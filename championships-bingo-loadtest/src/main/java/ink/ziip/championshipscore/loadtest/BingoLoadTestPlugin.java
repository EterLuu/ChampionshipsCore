package ink.ziip.championshipscore.loadtest;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Chunk;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

public final class BingoLoadTestPlugin extends JavaPlugin {
    private ChunkStressController controller;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        StressSettings settings;
        try {
            settings = StressSettings.load(getConfig());
        } catch (RuntimeException invalid) {
            getLogger().log(Level.SEVERE, "Invalid chunk stress configuration", invalid);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        controller = new ChunkStressController(this, settings);
        getServer().getPluginManager().registerEvents(controller, this);
        if (getCommand("chunkstress") != null) getCommand("chunkstress").setExecutor(this::executeCommand);

        if (getConfig().getBoolean("auto-start", false)) {
            // One shot: a later restart must never destroy more map unless explicitly armed again.
            getConfig().set("auto-start", false);
            saveConfig();
            long delay = Math.max(1L, getConfig().getLong("start-delay-seconds", 20L) * 20L);
            getServer().getGlobalRegionScheduler().runDelayed(this, ignored -> {
                if (!controller.start()) getLogger().severe("Automatic chunk stress test could not start");
            }, delay);
            getLogger().warning("One-shot chunk stress test armed; it will start in " + (delay / 20L) + "s");
        } else {
            getLogger().info("Chunk stress test is idle; use /chunkstress start or arm auto-start in config.yml");
        }
    }

    @Override
    public void onDisable() {
        if (controller != null) controller.stop("plugin-disable", false);
    }

    private boolean executeCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1) return false;
        return switch (args[0].toLowerCase(java.util.Locale.ROOT)) {
            case "start" -> {
                sender.sendMessage(controller.start() ? "Chunk stress test started" : "Chunk stress test is busy");
                yield true;
            }
            case "stop" -> {
                controller.stop("command", true);
                sender.sendMessage("Chunk stress test stopping");
                yield true;
            }
            case "status" -> {
                sender.sendMessage(controller.status());
                yield true;
            }
            default -> false;
        };
    }
}

final class ChunkStressController implements Listener {
    private static final int TEAM_COUNT = 8;
    static final List<EntityType> MONSTER_TYPES = List.of(
            EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.CREEPER);
    static final List<EntityType> CREATURE_TYPES = List.of(
            EntityType.COW, EntityType.SHEEP, EntityType.PIG, EntityType.CHICKEN,
            EntityType.RABBIT, EntityType.GOAT);

    private final BingoLoadTestPlugin plugin;
    private final StressSettings settings;
    private final ConcurrentLinkedQueue<ChunkPos> queue = new ConcurrentLinkedQueue<>();
    private final Set<ChunkPos> pending = ConcurrentHashMap.newKeySet();
    private final Set<ChunkPos> loading = ConcurrentHashMap.newKeySet();
    private final Map<ChunkPos, Chunk> tickets = new ConcurrentHashMap<>();
    private final Map<ChunkPos, Integer> references = new ConcurrentHashMap<>();
    private final Map<UUID, Entity> managedEntities = new ConcurrentHashMap<>();
    private final List<VirtualWalker> walkers = new ArrayList<>(); // global scheduler only
    private final LatencyHistogram loadLatency = new LatencyHistogram(); // global scheduler only
    private final AtomicBoolean diskCheckPending = new AtomicBoolean();
    private final AtomicInteger entitySpawnsInflight = new AtomicInteger();
    private final AtomicInteger entitySpawnFailures = new AtomicInteger();

    private volatile State state = State.IDLE;
    private volatile long latestFreeDiskBytes = Long.MAX_VALUE;
    private World world;
    private ScheduledTask tickTask;
    private long runStartedMillis;
    private long stageStartedMillis;
    private volatile int stageIndex;
    private long logicalTicks;
    private int inflight;
    private int maximumInflight;
    private int maximumQueue;
    private int maximumRawQueue;
    private long completedLoads;
    private long failedLoads;
    private long staleRequests;
    private long lastTickNanos;
    private long maximumTickGapMillis;
    private long lastRateSampleNanos;
    private long lastRateSampleTicks;
    private double schedulerTps;
    private double schedulerMspt;
    private double minimumSchedulerTps;
    private double maximumSchedulerMspt;
    private long lastSafetySampleNanos;
    private long lastSafetySampleTicks;
    private double safetySchedulerTps;
    private int consecutiveLowTpsSamples;
    private long runGeneration;
    private long entitySpawnSequence;
    private int latestWorldEntities;
    private int maximumWorldEntities;
    private int maximumManagedEntities;
    private Layout layout;
    private long layoutStartedMillis;
    private int layoutSwitches;

    ChunkStressController(BingoLoadTestPlugin plugin, StressSettings settings) {
        this.plugin = plugin;
        this.settings = settings;
    }

    boolean start() {
        if (state == State.RUNNING || state == State.STOPPING) return false;
        World selected = plugin.getServer().getWorld(settings.worldName());
        if (selected == null) {
            plugin.getLogger().severe("Chunk stress world is not loaded: " + settings.worldName());
            return false;
        }
        runGeneration++;
        reset(selected);
        StressSettings.Stage first = settings.stages().getFirst();
        activate(first.walkers());
        state = State.RUNNING;
        runStartedMillis = System.currentTimeMillis();
        stageStartedMillis = runStartedMillis;
        layoutStartedMillis = runStartedMillis;
        lastTickNanos = System.nanoTime();
        lastRateSampleNanos = lastTickNanos;
        lastRateSampleTicks = 0L;
        lastSafetySampleNanos = lastTickNanos;
        lastSafetySampleTicks = 0L;
        tickTask = plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(
                plugin, ignored -> tick(), 1L, 1L);
        plugin.getLogger().warning("CHUNK_STRESS START world=" + world.getName()
                + " stages=" + settings.stages() + " view=" + settings.viewDistance()
                + " maxConcurrent=" + settings.maxConcurrentLoads()
                + " stationarySeparation=" + settings.stationaryPlayerSeparationBlocks()
                + " entitySpawnBand=" + settings.entityMinimumSpawnDistanceBlocks()
                + "-" + settings.entityMaximumSpawnDistanceBlocks()
                + " layoutSwitchInterval=" + settings.layoutSwitchIntervalSeconds() + "s"
                + " stationaryDispersalSpeed="
                + settings.stationaryDispersalSpeedBlocksPerSecond()
                + " entitySpawnRate=" + settings.entitySpawnsPerTick() + "/tick");
        return true;
    }

    void stop(String reason, boolean writeReport) {
        if (state != State.RUNNING) return;
        state = State.STOPPING;
        if (tickTask != null) tickTask.cancel();
        tickTask = null;
        queue.clear();
        pending.clear();
        loading.clear();
        references.clear();
        sampleSchedulerRate(System.nanoTime());
        String summary = summary(reason);
        plugin.getLogger().warning("CHUNK_STRESS STOP " + summary);
        if (writeReport) {
            writeReport(summary);
            beginBatchedCleanup();
        } else {
            // During plugin/server shutdown the scheduler can no longer safely accept cleanup work.
            // Entities are non-persistent and Paper removes this plugin's chunk tickets on disable.
            managedEntities.clear();
            tickets.clear();
            state = State.FINISHED;
        }
    }

    String status() {
        return "ChunkStress state=" + state + " " + liveMetrics();
    }

    private void reset(World selected) {
        world = selected;
        queue.clear();
        pending.clear();
        loading.clear();
        references.clear();
        walkers.clear();
        tickets.clear();
        managedEntities.clear();
        entitySpawnsInflight.set(0);
        entitySpawnFailures.set(0);
        stageIndex = 0;
        logicalTicks = 0;
        inflight = 0;
        maximumInflight = 0;
        maximumQueue = 0;
        maximumRawQueue = 0;
        completedLoads = 0;
        failedLoads = 0;
        staleRequests = 0;
        maximumTickGapMillis = 0;
        schedulerTps = 20.0;
        schedulerMspt = 50.0;
        minimumSchedulerTps = 20.0;
        maximumSchedulerMspt = 50.0;
        safetySchedulerTps = 20.0;
        consecutiveLowTpsSamples = 0;
        entitySpawnSequence = 0L;
        latestWorldEntities = selected.getEntityCount();
        maximumWorldEntities = latestWorldEntities;
        maximumManagedEntities = 0;
        layout = settings.layoutSwitchIntervalSeconds() > 0 ? Layout.CONCENTRATED : Layout.FIXED;
        layoutSwitches = 0;

        Location spawn = selected.getSpawnLocation();
        int maximumWalkers = settings.stages().getLast().walkers();
        int maximumStationary = settings.stages().stream()
                .mapToInt(StressSettings.Stage::stationaryWalkers).max().orElse(0);
        for (int index = 0; index < maximumWalkers; index++) {
            int team = index % TEAM_COUNT;
            int member = index / TEAM_COUNT;
            double anchorAngle = team * Math.PI * 2.0 / TEAM_COUNT;
            double anchorX = spawn.getX() + Math.cos(anchorAngle) * settings.anchorRadiusBlocks();
            double anchorZ = spawn.getZ() + Math.sin(anchorAngle) * settings.anchorRadiusBlocks();
            double x = anchorX;
            double z = anchorZ;
            double dispersedX = anchorX;
            double dispersedZ = anchorZ;
            if (index < maximumStationary) {
                StationaryLayout.Point dispersed = StationaryLayout.dispersed(
                        anchorX, anchorZ, anchorAngle, member,
                        settings.stationaryPlayerSeparationBlocks());
                dispersedX = dispersed.x();
                dispersedZ = dispersed.z();
                if (layout == Layout.CONCENTRATED) {
                    StationaryLayout.Point rendezvous = StationaryLayout.dispersed(
                            anchorX, anchorZ, anchorAngle, 0,
                            settings.stationaryPlayerSeparationBlocks());
                    x = rendezvous.x();
                    z = rendezvous.z();
                } else {
                    x = dispersedX;
                    z = dispersedZ;
                }
            }
            double fanOffset = (member - 3.5) * 0.055;
            double direction = anchorAngle + fanOffset;
            walkers.add(new VirtualWalker(team, x, z, dispersedX, dispersedZ,
                    Math.cos(direction), Math.sin(direction)));
        }
    }

    private void tick() {
        if (state != State.RUNNING) return;
        long nowNanos = System.nanoTime();
        maximumTickGapMillis = Math.max(maximumTickGapMillis, (nowNanos - lastTickNanos) / 1_000_000L);
        lastTickNanos = nowNanos;
        logicalTicks++;

        latestWorldEntities = world.getEntityCount();
        maximumWorldEntities = Math.max(maximumWorldEntities, latestWorldEntities);
        maximumManagedEntities = Math.max(maximumManagedEntities, managedEntities.size());
        drainQueue();
        advanceLayoutIfDue();
        if (logicalTicks % settings.movementPeriodTicks() == 0) moveWalkers();
        spawnEntitiesForStage();
        if (logicalTicks % 20L == 0L) sampleSafetySchedulerRate(nowNanos);
        if (logicalTicks % 100L == 0) checkDiskAsync();
        if (logicalTicks % settings.statusIntervalTicks() == 0) {
            sampleSchedulerRate(nowNanos);
            plugin.getLogger().info("CHUNK_STRESS STATUS " + liveMetrics());
        }
        advanceStageIfDue();
        maximumQueue = Math.max(maximumQueue, pending.size());
        maximumRawQueue = Math.max(maximumRawQueue, queue.size());
        enforceLimits();
    }

    private void moveWalkers() {
        StressSettings.Stage stage = settings.stages().get(stageIndex);
        if (layout == Layout.DISPERSING) moveStationaryWalkers(stage);
        if (stage.flyingWalkers() == 0) return;
        double distance = stage.speedBlocksPerSecond() * settings.movementPeriodTicks() / 20.0;
        int active = stage.walkers();
        for (int index = stage.stationaryWalkers(); index < active; index++) {
            VirtualWalker walker = walkers.get(index);
            walker.x += walker.dx * distance;
            walker.z += walker.dz * distance;
            updateWindow(walker);
        }
    }

    private void advanceLayoutIfDue() {
        int intervalSeconds = settings.layoutSwitchIntervalSeconds();
        if (layout == Layout.FIXED || intervalSeconds <= 0) return;
        long now = System.currentTimeMillis();
        if (now - layoutStartedMillis < intervalSeconds * 1000L) return;
        StressSettings.Stage stage = settings.stages().get(stageIndex);
        if (now - stageStartedMillis >= stage.durationSeconds() * 1000L) return;
        layoutStartedMillis = now;
        layoutSwitches++;
        if (layout == Layout.CONCENTRATED) {
            layout = Layout.DISPERSING;
            plugin.getLogger().warning("CHUNK_STRESS LAYOUT layout=" + layout
                    + " switch=" + layoutSwitches + " stationary=" + stage.stationaryWalkers()
                    + " speed=" + settings.stationaryDispersalSpeedBlocksPerSecond());
            return;
        }

        layout = Layout.CONCENTRATED;
        int rendezvousMember = (layoutSwitches / 2) % 4;
        concentrateStationaryWalkers(stage, rendezvousMember);
        plugin.getLogger().warning("CHUNK_STRESS LAYOUT layout=" + layout
                + " switch=" + layoutSwitches + " stationary=" + stage.stationaryWalkers()
                + " rendezvousMember=" + (rendezvousMember + 1));
    }

    private void concentrateStationaryWalkers(StressSettings.Stage stage, int rendezvousMember) {
        int stationary = stage.stationaryWalkers();
        for (int index = 0; index < stationary; index++) {
            VirtualWalker walker = walkers.get(index);
            int rendezvousIndex = rendezvousMember * TEAM_COUNT + walker.team;
            if (rendezvousIndex >= stationary) rendezvousIndex = walker.team;
            if (rendezvousIndex >= stationary) rendezvousIndex = index;
            VirtualWalker rendezvous = walkers.get(rendezvousIndex);
            walker.x = rendezvous.dispersedX;
            walker.z = rendezvous.dispersedZ;
            updateWindow(walker);
        }
    }

    private void moveStationaryWalkers(StressSettings.Stage stage) {
        double distance = settings.stationaryDispersalSpeedBlocksPerSecond()
                * settings.movementPeriodTicks() / 20.0;
        for (int index = 0; index < stage.stationaryWalkers(); index++) {
            VirtualWalker walker = walkers.get(index);
            double deltaX = walker.dispersedX - walker.x;
            double deltaZ = walker.dispersedZ - walker.z;
            double remaining = Math.hypot(deltaX, deltaZ);
            if (remaining < 0.001) continue;
            double step = Math.min(distance, remaining);
            walker.x += deltaX / remaining * step;
            walker.z += deltaZ / remaining * step;
            updateWindow(walker);
        }
    }

    private void activate(int count) {
        for (int index = 0; index < count; index++) {
            VirtualWalker walker = walkers.get(index);
            if (!walker.active) {
                walker.active = true;
                updateWindow(walker);
            }
        }
    }

    private void updateWindow(VirtualWalker walker) {
        Set<ChunkPos> next = ChunkWindow.around(walker.x, walker.z, settings.viewDistance());
        if (next.equals(walker.window)) return;
        for (ChunkPos entered : next) {
            if (walker.window.contains(entered)) continue;
            int count = references.merge(entered, 1, Integer::sum);
            if (count == 1 && !tickets.containsKey(entered) && pending.add(entered)) queue.add(entered);
        }
        for (ChunkPos exited : walker.window) {
            if (next.contains(exited)) continue;
            Integer remaining = references.computeIfPresent(exited, (ignored, count) -> count <= 1 ? null : count - 1);
            if (remaining == null) {
                // Real player chunk loaders cancel obsolete requests as their view window moves.
                // Keep submitted loads deduplicated, but make queued stale entries cheap to skip.
                if (!loading.contains(exited)) pending.remove(exited);
                scheduleTicketRelease(exited);
            }
        }
        walker.window = next;
    }

    private void drainQueue() {
        int submitted = 0;
        while (inflight < settings.maxConcurrentLoads() && submitted < settings.maxSubmissionsPerTick()) {
            ChunkPos position = queue.poll();
            if (position == null) return;
            if (!pending.contains(position) || !references.containsKey(position)) {
                pending.remove(position);
                staleRequests++;
                continue;
            }
            submitted++;
            inflight++;
            loading.add(position);
            maximumInflight = Math.max(maximumInflight, inflight);
            long started = System.nanoTime();
            long generation = runGeneration;
            world.getChunkAtAsync(position.x(), position.z(), true).whenComplete((chunk, error) ->
                    plugin.getServer().getGlobalRegionScheduler().execute(plugin,
                            () -> completeLoad(generation, position, chunk, error, started)));
        }
    }

    private void completeLoad(long generation, ChunkPos position, Chunk chunk, Throwable error,
                              long startedNanos) {
        if (generation != runGeneration) return;
        inflight = Math.max(0, inflight - 1);
        loading.remove(position);
        pending.remove(position);
        loadLatency.record((System.nanoTime() - startedNanos) / 1_000_000L);
        if (error != null || chunk == null) {
            failedLoads++;
            if (error != null) plugin.getLogger().log(Level.WARNING,
                    "Chunk stress load failed at " + position, error);
            return;
        }
        completedLoads++;
        if (state != State.RUNNING || !references.containsKey(position)) return;
        Location owner = ownerLocation(position);
        plugin.getServer().getRegionScheduler().execute(plugin, owner, () -> {
            if (state != State.RUNNING || !references.containsKey(position)) return;
            chunk.addPluginChunkTicket(plugin);
            tickets.put(position, chunk);
        });
    }

    private void scheduleTicketRelease(ChunkPos position) {
        plugin.getServer().getGlobalRegionScheduler().runDelayed(plugin, ignored -> {
            if (references.containsKey(position)) return;
            Chunk chunk = tickets.remove(position);
            if (chunk != null) removeTicket(position, chunk);
        }, Math.max(1, settings.releaseDelayTicks()));
    }

    private void removeTicket(ChunkPos position, Chunk chunk) {
        plugin.getServer().getRegionScheduler().execute(plugin, ownerLocation(position),
                () -> chunk.removePluginChunkTicket(plugin));
    }

    private Location ownerLocation(ChunkPos position) {
        return new Location(world, position.x() * 16.0 + 8.0, world.getMinHeight(),
                position.z() * 16.0 + 8.0);
    }

    private void advanceStageIfDue() {
        StressSettings.Stage stage = settings.stages().get(stageIndex);
        if (System.currentTimeMillis() - stageStartedMillis < stage.durationSeconds() * 1000L) return;
        if (stageIndex + 1 >= settings.stages().size()) {
            stop("completed", true);
            return;
        }
        stageIndex++;
        stageStartedMillis = System.currentTimeMillis();
        StressSettings.Stage next = settings.stages().get(stageIndex);
        activate(next.walkers());
        plugin.getLogger().warning("CHUNK_STRESS STAGE walkers=" + next.walkers()
                + " stationary=" + next.stationaryWalkers() + " flying=" + next.flyingWalkers()
                + " mode=" + next.mode() + " speed=" + next.speedBlocksPerSecond()
                + " targetWorldEntities=" + next.targetWorldEntities()
                + " duration=" + next.durationSeconds() + "s");
    }

    private void enforceLimits() {
        if (completedLoads >= settings.maxCompletedLoads()) stop("max-completed-loads", true);
        else if (pending.size() > settings.maxPendingRequests()) stop("max-pending-requests", true);
        else if (failedLoads + entitySpawnFailures.get() >= settings.maxFailures()) stop("max-failures", true);
        else if (consecutiveLowTpsSamples >= settings.lowTpsGraceSamples()) {
            stop("minimum-scheduler-tps", true);
        }
        else if (latestFreeDiskBytes < settings.minimumFreeDiskBytes()) stop("minimum-free-disk", true);
    }

    private void spawnEntitiesForStage() {
        StressSettings.Stage stage = settings.stages().get(stageIndex);
        if (stage.targetWorldEntities() <= 0 || stage.stationaryWalkers() <= 0) return;
        int missing = stage.targetWorldEntities() - latestWorldEntities - entitySpawnsInflight.get();
        int budget = Math.min(settings.entitySpawnsPerTick(), Math.max(0, missing));
        for (int index = 0; index < budget; index++) {
            if (!scheduleEntitySpawn()) break;
        }
    }

    private boolean scheduleEntitySpawn() {
        StressSettings.Stage stage = settings.stages().get(stageIndex);
        for (int attempts = 0; attempts < stage.stationaryWalkers() * 8; attempts++) {
            long sequence = entitySpawnSequence++;
            NaturalSpawnPlanner.Offset offset = NaturalSpawnPlanner.offset(sequence,
                    stage.stationaryWalkers(), settings.entityMinimumSpawnDistanceBlocks() + 1.0,
                    settings.entityMaximumSpawnDistanceBlocks() - 1.0);
            VirtualWalker stationaryWalker = walkers.get(offset.ownerIndex());
            int blockX = (int) Math.floor(stationaryWalker.x + offset.x());
            int blockZ = (int) Math.floor(stationaryWalker.z + offset.z());
            ChunkPos position = new ChunkPos(blockX >> 4, blockZ >> 4);
            if (!tickets.containsKey(position) || !references.containsKey(position)) continue;
            return scheduleNaturalEntitySpawn(sequence, offset.ownerIndex(), blockX, blockZ);
        }
        return false;
    }

    private boolean scheduleNaturalEntitySpawn(long sequence, int ownerIndex, int blockX, int blockZ) {
        Location owner = new Location(world, blockX + 0.5, world.getMinHeight(), blockZ + 0.5);
        EntityType type = entityTypeFor(sequence);
        long generation = runGeneration;
        entitySpawnsInflight.incrementAndGet();
        plugin.getServer().getRegionScheduler().execute(plugin, owner, () -> {
            try {
                StressSettings.Stage current = settings.stages().get(stageIndex);
                if (generation != runGeneration || state != State.RUNNING
                        || current.targetWorldEntities() <= 0
                        || ownerIndex >= current.stationaryWalkers()
                        || !isWithinNaturalSpawnBand(blockX, blockZ, walkers.get(ownerIndex))) {
                    return;
                }
                int chunkX = blockX >> 4;
                int chunkZ = blockZ >> 4;
                if (!world.isChunkLoaded(chunkX, chunkZ)) return;
                int blockY = world.getHighestBlockYAt(blockX, blockZ,
                        HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1;
                Location spawn = new Location(world, blockX + 0.5, blockY, blockZ + 0.5);
                Entity entity = world.spawnEntity(spawn, type, CreatureSpawnEvent.SpawnReason.NATURAL);
                entity.setPersistent(false);
                entity.setInvulnerable(true);
                if (entity instanceof LivingEntity living) {
                    living.setRemoveWhenFarAway(false);
                    living.setAI(true);
                }
                managedEntities.put(entity.getUniqueId(), entity);
            } catch (RuntimeException error) {
                int failures = entitySpawnFailures.incrementAndGet();
                if (failures <= 5 || failures % 100 == 0) {
                    plugin.getLogger().log(Level.WARNING, "Entity stress spawn failed", error);
                }
            } finally {
                if (generation == runGeneration) entitySpawnsInflight.decrementAndGet();
            }
        });
        return true;
    }

    static EntityType entityTypeFor(long sequence) {
        int vanillaLandCapSlot = (int) Math.floorMod(sequence, 80L);
        if (vanillaLandCapSlot < 70) {
            return MONSTER_TYPES.get(vanillaLandCapSlot % MONSTER_TYPES.size());
        }
        return CREATURE_TYPES.get((vanillaLandCapSlot - 70) % CREATURE_TYPES.size());
    }

    private boolean isWithinNaturalSpawnBand(int blockX, int blockZ, VirtualWalker walker) {
        double deltaX = blockX + 0.5 - walker.x;
        double deltaZ = blockZ + 0.5 - walker.z;
        double distanceSquared = deltaX * deltaX + deltaZ * deltaZ;
        double minimum = settings.entityMinimumSpawnDistanceBlocks();
        double maximum = settings.entityMaximumSpawnDistanceBlocks();
        return distanceSquared >= minimum * minimum && distanceSquared <= maximum * maximum;
    }

    private void beginBatchedCleanup() {
        Iterator<Map.Entry<UUID, Entity>> entityIterator = managedEntities.entrySet().iterator();
        Iterator<Map.Entry<ChunkPos, Chunk>> ticketIterator = tickets.entrySet().iterator();
        scheduleCleanupBatch(entityIterator, ticketIterator);
    }

    private void scheduleCleanupBatch(Iterator<Map.Entry<UUID, Entity>> entityIterator,
                                      Iterator<Map.Entry<ChunkPos, Chunk>> ticketIterator) {
        plugin.getServer().getAsyncScheduler().runDelayed(plugin, ignored -> {
            int entityBudget = 256;
            while (entityBudget-- > 0 && entityIterator.hasNext()) {
                Map.Entry<UUID, Entity> entry = entityIterator.next();
                UUID uuid = entry.getKey();
                Entity entity = entry.getValue();
                boolean scheduled = entity.getScheduler().execute(plugin, entity::remove,
                        () -> managedEntities.remove(uuid, entity), 1L);
                if (!scheduled) managedEntities.remove(uuid, entity);
            }

            int ticketBudget = 256;
            while (ticketBudget-- > 0 && ticketIterator.hasNext()) {
                Map.Entry<ChunkPos, Chunk> entry = ticketIterator.next();
                if (tickets.remove(entry.getKey(), entry.getValue())) {
                    removeTicket(entry.getKey(), entry.getValue());
                }
            }

            if (entityIterator.hasNext() || ticketIterator.hasNext()) {
                scheduleCleanupBatch(entityIterator, ticketIterator);
                return;
            }
            plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> {
                state = State.FINISHED;
                plugin.getServer().getGlobalRegionScheduler().runDelayed(plugin, task ->
                        plugin.getLogger().info("CHUNK_STRESS CLEANUP managedRemaining="
                                + managedEntities.size() + " ticketsRemaining=" + tickets.size()
                                + " worldEntities=" + world.getEntityCount()), 40L);
            });
        }, 50L, TimeUnit.MILLISECONDS);
    }

    @EventHandler
    public void onEntityRemoved(EntityRemoveEvent event) {
        managedEntities.remove(event.getEntity().getUniqueId());
    }

    private void checkDiskAsync() {
        if (!diskCheckPending.compareAndSet(false, true)) return;
        plugin.getServer().getAsyncScheduler().runNow(plugin, ignored -> {
            long usable = 0L;
            try {
                Path path = plugin.getDataFolder().toPath();
                Files.createDirectories(path);
                FileStore store = Files.getFileStore(path);
                usable = store.getUsableSpace();
            } catch (IOException error) {
                plugin.getLogger().log(Level.WARNING, "Unable to read free disk space", error);
            }
            long result = usable;
            plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> {
                if (result > 0L) latestFreeDiskBytes = result;
                diskCheckPending.set(false);
            });
        });
    }

    private void sampleSchedulerRate(long nowNanos) {
        long tickDelta = logicalTicks - lastRateSampleTicks;
        long timeDelta = nowNanos - lastRateSampleNanos;
        if (tickDelta < 20L || timeDelta <= 0L) return;
        schedulerTps = Math.min(20.0, tickDelta * 1_000_000_000.0 / timeDelta);
        schedulerMspt = timeDelta / 1_000_000.0 / tickDelta;
        minimumSchedulerTps = Math.min(minimumSchedulerTps, schedulerTps);
        maximumSchedulerMspt = Math.max(maximumSchedulerMspt, schedulerMspt);
        lastRateSampleTicks = logicalTicks;
        lastRateSampleNanos = nowNanos;
    }

    private void sampleSafetySchedulerRate(long nowNanos) {
        long tickDelta = logicalTicks - lastSafetySampleTicks;
        long timeDelta = nowNanos - lastSafetySampleNanos;
        if (tickDelta <= 0L || timeDelta <= 0L) return;
        safetySchedulerTps = Math.min(20.0, tickDelta * 1_000_000_000.0 / timeDelta);
        if (safetySchedulerTps < settings.minimumSchedulerTps()) consecutiveLowTpsSamples++;
        else consecutiveLowTpsSamples = 0;
        lastSafetySampleTicks = logicalTicks;
        lastSafetySampleNanos = nowNanos;
    }

    private static double twoDecimals(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private String liveMetrics() {
        LatencyHistogram.Snapshot latency = loadLatency.snapshot();
        StressSettings.Stage stage = settings.stages().get(stageIndex);
        int active = state == State.RUNNING ? stage.walkers() : 0;
        return "stage=" + (stageIndex + 1) + "/" + settings.stages().size()
                + " mode=" + stage.mode() + " speed=" + stage.speedBlocksPerSecond()
                + " layout=" + layout + " layoutSwitches=" + layoutSwitches
                + " walkers=" + active + " stationary="
                + (state == State.RUNNING ? stage.stationaryWalkers() : 0)
                + " flying=" + (state == State.RUNNING ? stage.flyingWalkers() : 0)
                + " schedulerTps=" + twoDecimals(schedulerTps)
                + " schedulerMspt=" + twoDecimals(schedulerMspt)
                + " safetyTps=" + twoDecimals(safetySchedulerTps)
                + " lowTpsSamples=" + consecutiveLowTpsSamples
                + " completed=" + completedLoads + " failed=" + failedLoads
                + " pending=" + pending.size() + " rawQueue=" + queue.size()
                + " inflight=" + inflight + " tickets=" + tickets.size()
                + " worldEntities=" + latestWorldEntities + " managedEntities=" + managedEntities.size()
                + " targetPerStationary=" + (stage.stationaryWalkers() == 0 ? 0
                : (stage.targetWorldEntities() + stage.stationaryWalkers() - 1)
                / stage.stationaryWalkers())
                + " entityInflight=" + entitySpawnsInflight.get()
                + " entityFailed=" + entitySpawnFailures.get()
                + " latencyAvgMs=" + latency.averageMillis() + " p95Ms=" + latency.p95Millis()
                + " p99Ms=" + latency.p99Millis() + " maxMs=" + latency.maximumMillis()
                + " maxTickGapMs=" + maximumTickGapMillis
                + " freeGiB=" + (latestFreeDiskBytes == Long.MAX_VALUE ? "unknown"
                : latestFreeDiskBytes / 1024L / 1024L / 1024L);
    }

    private String summary(String reason) {
        long duration = Math.max(0L, System.currentTimeMillis() - runStartedMillis);
        return "reason=" + reason + " durationMs=" + duration + " completed=" + completedLoads
                + " failed=" + failedLoads + " stale=" + staleRequests + " maxQueue=" + maximumQueue
                + " maxRawQueue=" + maximumRawQueue + " maxInflight=" + maximumInflight
                + " minSchedulerTps=" + twoDecimals(minimumSchedulerTps)
                + " maxSchedulerMspt=" + twoDecimals(maximumSchedulerMspt)
                + " maxWorldEntities=" + maximumWorldEntities
                + " maxManagedEntities=" + maximumManagedEntities + " " + liveMetrics();
    }

    private void writeReport(String summary) {
        plugin.getServer().getAsyncScheduler().runNow(plugin, ignored -> {
            try {
                Path directory = plugin.getDataFolder().toPath();
                Files.createDirectories(directory);
                String line = "{\"timestamp\":\"" + Instant.now() + "\",\"summary\":\""
                        + summary.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}\n";
                Files.writeString(directory.resolve("results.jsonl"), line, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException error) {
                plugin.getLogger().log(Level.SEVERE, "Unable to write chunk stress result", error);
            }
        });
    }

    private enum State { IDLE, RUNNING, STOPPING, FINISHED }

    private enum Layout { FIXED, CONCENTRATED, DISPERSING }

    private static final class VirtualWalker {
        private final int team;
        private double x;
        private double z;
        private final double dispersedX;
        private final double dispersedZ;
        private final double dx;
        private final double dz;
        private boolean active;
        private Set<ChunkPos> window = Set.of();

        private VirtualWalker(int team, double x, double z, double dispersedX, double dispersedZ,
                              double dx, double dz) {
            this.team = team;
            this.x = x;
            this.z = z;
            this.dispersedX = dispersedX;
            this.dispersedZ = dispersedZ;
            this.dx = dx;
            this.dz = dz;
        }
    }
}
