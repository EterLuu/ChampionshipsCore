package ink.ziip.championshipscore.api.game.acerace;

import io.papermc.paper.registry.keys.EnchantmentKeys;
import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.event.SingleGameEndEvent;
import ink.ziip.championshipscore.api.daily.DailyRecordType;
import ink.ziip.championshipscore.api.game.instance.multiteam.BaseMultiTeamGameInstance;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.util.Enchants;
import ink.ziip.championshipscore.util.Utils;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Display;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRiptideEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.Vector;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** A lap race with ordered progress gates and course-bound proximity respawn points. */
public class AceRaceArea extends BaseMultiTeamGameInstance {
    private static final long LAUNCH_PAD_DELAY_TICKS = 0L;
    /**
     * Vanilla multiplies a player's horizontal motion by {@code slipperiness * 0.91} at the end of a tick
     * spent on the ground and by {@code 0.91} while airborne, and it picks the branch from the ground state
     * the tick <em>started</em> with. A pad impulse is only a velocity packet, so a racer who leaves the wool
     * before the packet lands keeps 0.91 instead of 0.546 and flies about 1.667x as far. These constants model
     * the intended grounded curve so the server can measure and undo that difference.
     */
    private static final double LAUNCH_PAD_SLIPPERINESS = 0.6D;
    private static final double AIR_DRAG = 0.91D;
    private static final double LAUNCH_GROUND_DRAG = LAUNCH_PAD_SLIPPERINESS * AIR_DRAG;
    /** Highest per-tick airborne gain a sprinting racer can legitimately add while holding forward. */
    private static final double SPRINT_AIR_ACCELERATION = 0.026D;
    private static final int LAUNCH_ENFORCEMENT_TICKS = 20;
    private static final int LAUNCH_DEBT_REPAY_TICKS = 6;
    /**
     * Share of the pad impulse a single tick of travel must show before the flight budget starts. Keep it
     * clear of the fastest legitimate ground speed, which the red station's Speed IX pushes well above a
     * normal sprint, yet far below the impulse's own first tick.
     */
    private static final double LAUNCH_DETECTION_FRACTION = 0.5D;
    /** How long to wait for the impulse to show up in the racer's movement before dropping the budget. */
    private static final int LAUNCH_IMPULSE_WAIT_TICKS = 40;
    private static final int LAUNCH_ENFORCEMENT_EXPIRED = Integer.MAX_VALUE;
    /** Absorbs movement-packet jitter so an honest flight never trips the correction. */
    private static final double LAUNCH_DISTANCE_TOLERANCE = 0.5D;
    private static final int JUMP_BOOST_DURATION_TICKS = 14;
    private static final int SPEED_BOOST_DURATION_TICKS = 80;
    private static final int RED_SPEED_DURATION_TICKS = 6;
    private static final int ENVIRONMENTAL_EFFECT_DURATION_TICKS = 40;
    private static final int BASE_SPEED_AMPLIFIER = 0;
    private static final int YELLOW_SPEED_AMPLIFIER = 2;
    private static final int RED_SPEED_AMPLIFIER = 8;
    private static final int DEPTH_STRIDER_LEVEL = 3;
    private static final int DOLPHINS_GRACE_DEPTH_STRIDER_LEVEL = 1;
    private static final double MIN_LAUNCH_VERTICAL_MULTIPLIER = 0.9D;
    private static final double MAX_LAUNCH_VERTICAL_MULTIPLIER = 1.2D;
    private static final double RIPTIDE_EXTRA_MULTIPLIER = 0.01D;
    private static final int SPEED_STATION_RADIUS = 2;
    private static final int WATER_SPEED_STATION_RADIUS = 4;
    private static final double SPEED_STATION_RADIUS_SQUARED = SPEED_STATION_RADIUS * SPEED_STATION_RADIUS;
    private static final double WATER_SPEED_STATION_RADIUS_SQUARED =
            WATER_SPEED_STATION_RADIUS * WATER_SPEED_STATION_RADIUS;
    private static final long RACER_VISIBILITY_HIDDEN_AFTER_START_TICKS = 60L * 20L;
    private static final double RACER_VISIBILITY_DISTANCE_SQUARED = 8D * 8D;
    private static final double RESPAWN_GATE_BIND_DISTANCE_SQUARED = 6D * 6D;
    private static final double RESPAWN_ROUTE_CORRIDOR_MARGIN = 28D;
    private static final long RACER_VISIBILITY_UPDATE_TICKS = 1L;
    private static final long LAUNCH_ENFORCEMENT_UPDATE_TICKS = 1L;
    private static final long MAP_EDIT_PREVIEW_UPDATE_TICKS = 10L;
    private static final String COLLISION_TEAM_PREFIX = "cc_ar_";
    private static final String TIMER_BOSS_BAR_PREFIX = "acerace-game-timer:";

    @Getter
    private final List<AceRaceProgressPoint> progressPoints = new ArrayList<>();
    private final List<AceRaceRespawnPoint> respawnPoints = new ArrayList<>();
    /** Zero-based progress point for each respawn marker, or -1 for the start segment. */
    private final List<Integer> respawnProgressPointBindings = new ArrayList<>();
    /** Raw configuration index for each loaded marker (invalid serialized rows are skipped). */
    private final List<Integer> respawnPointConfigIndexes = new ArrayList<>();
    private final List<EnderCrystal> mapEditPreviewCrystals = new ArrayList<>();
    private final NamespacedKey mapEditPreviewMarkerKey;
    private final NamespacedKey mapEditPreviewAreaKey;
    @Getter
    private final List<UUID> finishedPlayers = new ArrayList<>();
    private final Map<UUID, Integer> nextProgressPoint = new HashMap<>();
    private final Map<UUID, Integer> completedLaps = new HashMap<>();
    private final Map<UUID, Long> lapStartedNanos = new HashMap<>();
    /** Completed lap durations in milliseconds, retained for the entire match for the sidebar. */
    private final Map<UUID, List<Long>> lapDurationsMillis = new HashMap<>();
    private final Map<UUID, Long> raceStartedNanos = new HashMap<>();
    private final Map<UUID, Location> latestRespawnLocations = new HashMap<>();
    private final Map<UUID, Set<Integer>> capturedRespawnPoints = new HashMap<>();
    private final Map<UUID, Integer> activeFallHeights = new HashMap<>();
    private final Map<UUID, Location> lastMoveLocations = new HashMap<>();
    /** A player must leave the start line before a finish-line crossing can count for the race. */
    private final Set<UUID> startLineArmed = new HashSet<>();
    private final Map<UUID, TrackFeatureContact> featureContacts = new HashMap<>();
    private final Map<UUID, BukkitTask> pendingLaunchPadTasks = new HashMap<>();
    private final Map<UUID, LaunchEnforcement> enforcedLaunches = new HashMap<>();
    private final Set<RacerPair> visibleRacerPairs = new HashSet<>();
    private final String visibilityOwner = "game:acerace:" + UUID.randomUUID();
    private final Set<RacerView> riptideHiddenViews = new HashSet<>();
    private final Map<UUID, Integer> riptideViewerGraceTicks = new HashMap<>();
    private final Map<UUID, TextDisplay> racerNameDisplays = new HashMap<>();
    private final Map<UUID, String> originalScoreboardTeams = new HashMap<>();
    private final int copyIndex;
    @Getter
    private int timer;
    private BukkitTask progressTask;
    private BukkitTask racerVisibilityTask;
    private BukkitTask launchEnforcementTask;
    private BukkitTask racerVisibilityUnlockTask;
    private BukkitTask mapEditPreviewTask;
    private UUID mapEditPreviewViewer;
    private boolean racerVisibilityUnlocked;

    public AceRaceArea(ChampionshipsCore plugin, AceRaceConfig config) {
        this(plugin, config, 0, true);
    }

    AceRaceArea(ChampionshipsCore plugin, AceRaceConfig config, int copyIndex, boolean initializeConfig) {
        super(plugin, GameTypeEnum.AceRace, new AceRaceHandler(plugin), config);
        if (initializeConfig) getGameConfig().initializeConfiguration(plugin.getFolder());
        this.copyIndex = copyIndex;
        this.mapEditPreviewMarkerKey = new NamespacedKey(plugin, "acerace_preview_respawn");
        this.mapEditPreviewAreaKey = new NamespacedKey(plugin, "acerace_preview_area");
        getGameHandler().setAceRaceArea(this);
        getGameHandler().register();
        loadCoursePoints();
        setGameStageEnum(GameStageEnum.WAITING);
    }

    @Override
    public int getCopyIndex() {
        return copyIndex;
    }

    public void loadCoursePoints() {
        progressPoints.clear();
        ConfigurationSection root = getGameConfig().getProgressPoints();
        if (root != null) {
            for (String key : root.getKeys(false)) {
                ConfigurationSection section = root.getConfigurationSection(key);
                if (section == null) continue;
                Vector pos1 = section.getVector("pos1");
                Vector pos2 = section.getVector("pos2");
                if (pos1 == null || pos2 == null) {
                    logGame(java.util.logging.Level.WARNING, "进度点", "跳过不完整进度点=" + key);
                    continue;
                }
                progressPoints.add(new AceRaceProgressPoint(
                        section.getInt("order", progressPoints.size() + 1), pos1, pos2,
                        section.getInt("fall-y", getWorldFallHeight()),
                        AceRaceEquipment.fromConfig(section.getString("equipment"))));
            }
            progressPoints.sort(Comparator.comparingInt(AceRaceProgressPoint::order));
        }

        respawnPoints.clear();
        respawnPointConfigIndexes.clear();
        List<String> configuredRespawnPoints = getGameConfig().ensureRespawnPoints();
        for (int configIndex = 0; configIndex < configuredRespawnPoints.size(); configIndex++) {
            String serialized = configuredRespawnPoints.get(configIndex);
            try {
                Location location = Utils.getLocation(serialized);
                if (location.getWorld() == null || !getWorldName().equals(location.getWorld().getName())) {
                    logGame(java.util.logging.Level.WARNING, "重生点", "跳过世界无效的重生点=" + serialized);
                    continue;
                }
                respawnPoints.add(new AceRaceRespawnPoint(location));
                respawnPointConfigIndexes.add(configIndex);
            } catch (Exception exception) {
                logGame(java.util.logging.Level.WARNING, "重生点", "跳过格式无效的重生点=" + serialized);
            }
        }
        rebuildRespawnProgressPointBindings();
        if (mapEditPreviewViewer != null) refreshMapEditPreviewEntities();
    }

    /**
     * Binds each marker from its position relative to the ordered gate geometry. The route corridor
     * keeps bends and large launch jumps in the correct segment without depending on marker list order.
     */
    private void rebuildRespawnProgressPointBindings() {
        respawnProgressPointBindings.clear();
        if (respawnPoints.isEmpty()) return;

        World world = respawnPoints.getFirst().destination().getWorld();
        AceRaceLine finishLine = getFinishLine();
        Location startApproach = getGameConfig().getStartSpawnPoint();
        Location finishApproach = world == null || finishLine == null ? null : finishLine.center(world);
        respawnProgressPointBindings.addAll(bindRespawnPoints(
                progressPoints, respawnPoints, startApproach, finishApproach));

        for (int index = 0; index < respawnProgressPointBindings.size(); index++) {
            int configIndex = index < respawnPointConfigIndexes.size()
                    ? respawnPointConfigIndexes.get(index) : -1;
            Integer configured = getGameConfig().getRespawnProgressPointBinding(
                    configIndex, progressPoints.size());
            if (configured != null) respawnProgressPointBindings.set(index, configured);
        }

        Set<Integer> boundProgressPoints = new HashSet<>(respawnProgressPointBindings);
        for (int index = 0; index < progressPoints.size(); index++) {
            if (!boundProgressPoints.contains(index)) {
                logGame(java.util.logging.Level.WARNING, "重生点",
                        "未能按坐标为进度点 #" + (index + 1) + " 找到绑定重生点");
            }
        }
    }

    static @NotNull List<Integer> bindRespawnPoints(@NotNull List<AceRaceProgressPoint> progressPoints,
                                                     @NotNull List<AceRaceRespawnPoint> respawnPoints,
                                                     Location startApproach, Location finishApproach) {
        if (progressPoints.isEmpty()) return new ArrayList<>();
        List<AceRaceLine> gates = progressPoints.stream()
                .map(point -> new AceRaceLine(point.pos1(), point.pos2())).toList();
        World world = startApproach == null ? null : startApproach.getWorld();
        if (world == null && finishApproach != null) world = finishApproach.getWorld();
        if (world == null && !respawnPoints.isEmpty()) world = respawnPoints.getFirst().destination().getWorld();

        List<Location> gateCenters = new ArrayList<>();
        for (AceRaceLine gate : gates) gateCenters.add(gate.center(world));
        List<Location> routeNodes = new ArrayList<>();
        routeNodes.add(startApproach != null ? startApproach.clone() : gateCenters.getFirst().clone());
        routeNodes.addAll(gateCenters.stream().map(Location::clone).toList());
        routeNodes.add(finishApproach != null ? finishApproach.clone() : gateCenters.getLast().clone());

        List<Integer> bindings = new ArrayList<>(respawnPoints.size());
        for (AceRaceRespawnPoint respawnPoint : respawnPoints) {
            Location location = respawnPoint.destination();
            int nearGate = nearestGateWithinBindingRadius(location, gates);
            if (nearGate >= 0) {
                // A marker close to a gate is deliberately considered part of the segment after it;
                // its capture radius is the same safety margin used when recovering a missed gate.
                bindings.add(nearGate);
                continue;
            }

            List<Integer> orientedCandidates = new ArrayList<>();
            Location firstGateNext = gates.size() > 1 ? gateCenters.get(1) : routeNodes.getLast();
            if (isBeforeGate(location, gates.getFirst(), firstGateNext)) {
                orientedCandidates.add(0);
            }
            for (int segment = 1; segment < gates.size(); segment++) {
                AceRaceLine previousGate = gates.get(segment - 1);
                AceRaceLine nextGate = gates.get(segment);
                Location nextGateNext = segment + 1 < gates.size()
                        ? gateCenters.get(segment + 1) : routeNodes.getLast();
                if (isAfterGate(location, previousGate, gateCenters.get(segment))
                        && isBeforeGate(location, nextGate, nextGateNext)) {
                    orientedCandidates.add(segment);
                }
            }
            if (isAfterGate(location, gates.getLast(), routeNodes.getLast())) {
                orientedCandidates.add(gates.size());
            }

            List<Integer> corridorCandidates = new ArrayList<>();
            for (int segment = 0; segment < routeNodes.size() - 1; segment++) {
                if (withinRouteCorridor(location, routeNodes.get(segment), routeNodes.get(segment + 1))) {
                    corridorCandidates.add(segment);
                }
            }
            List<Integer> candidates = corridorCandidates.isEmpty()
                    ? orientedCandidates : corridorCandidates;
            if (corridorCandidates.size() > 1 && !orientedCandidates.isEmpty()) {
                List<Integer> intersection = corridorCandidates.stream()
                        .filter(orientedCandidates::contains).toList();
                if (!intersection.isEmpty()) candidates = intersection;
            }

            int binding = candidates.stream()
                    .min(Comparator.comparingDouble(segment -> distanceSquaredToSegment(
                            location, routeNodes.get(segment), routeNodes.get(segment + 1))))
                    .orElseGet(() -> nearestRouteSegment(location, routeNodes));
            // Route segment 0 is before the first gate and therefore has binding -1;
            // segment N is after gate N and therefore has binding N-1.
            bindings.add(binding - 1);
        }

        // Some maps place the final recovery marker before the last gate and go straight into the
        // finish line. Keep that marker as a final-gate safety net, selected by distance rather than
        // by whichever marker happened to be saved last.
        int finalProgressPoint = progressPoints.size() - 1;
        if (!bindings.contains(finalProgressPoint) && !respawnPoints.isEmpty()) {
            int fallback = -1;
            double nearestDistance = Double.POSITIVE_INFINITY;
            for (int index = 0; index < respawnPoints.size(); index++) {
                if (bindings.get(index) != finalProgressPoint - 1 && finalProgressPoint > 0) continue;
                double distance = gates.getLast().distanceSquared(respawnPoints.get(index).destination());
                if (distance >= nearestDistance) continue;
                nearestDistance = distance;
                fallback = index;
            }
            if (fallback >= 0) bindings.set(fallback, finalProgressPoint);
        }
        return bindings;
    }

    private static boolean withinRouteCorridor(@NotNull Location location,
                                               @NotNull Location from, @NotNull Location to) {
        if (location.getWorld() != from.getWorld() || from.getWorld() != to.getWorld()) return false;
        double minX = Math.min(from.getX(), to.getX()) - RESPAWN_ROUTE_CORRIDOR_MARGIN;
        double maxX = Math.max(from.getX(), to.getX()) + RESPAWN_ROUTE_CORRIDOR_MARGIN;
        double minZ = Math.min(from.getZ(), to.getZ()) - RESPAWN_ROUTE_CORRIDOR_MARGIN;
        double maxZ = Math.max(from.getZ(), to.getZ()) + RESPAWN_ROUTE_CORRIDOR_MARGIN;
        return location.getX() >= minX && location.getX() <= maxX
                && location.getZ() >= minZ && location.getZ() <= maxZ;
    }

    private static int nearestGateWithinBindingRadius(@NotNull Location location,
                                                       @NotNull List<AceRaceLine> gates) {
        int nearest = -1;
        double nearestDistance = RESPAWN_GATE_BIND_DISTANCE_SQUARED;
        for (int index = 0; index < gates.size(); index++) {
            double distance = gates.get(index).distanceSquared(location);
            if (distance >= nearestDistance) continue;
            nearestDistance = distance;
            nearest = index;
        }
        return nearest;
    }

    private static boolean isAfterGate(@NotNull Location location, @NotNull AceRaceLine gate,
                                       @NotNull Location nextGateCenter) {
        int expectedSide = gate.side(nextGateCenter);
        int actualSide = gate.side(location);
        return expectedSide != 0 && (actualSide == expectedSide || actualSide == 0);
    }

    private static boolean isBeforeGate(@NotNull Location location, @NotNull AceRaceLine gate,
                                        @NotNull Location nextGateCenter) {
        int expectedSide = gate.side(nextGateCenter);
        int actualSide = gate.side(location);
        return expectedSide != 0 && (actualSide == -expectedSide || actualSide == 0);
    }

    private static int nearestRouteSegment(@NotNull Location location, @NotNull List<Location> routeNodes) {
        int nearest = 0;
        double nearestDistance = Double.POSITIVE_INFINITY;
        for (int segment = 0; segment < routeNodes.size() - 1; segment++) {
            double distance = distanceSquaredToSegment(location, routeNodes.get(segment), routeNodes.get(segment + 1));
            if (distance >= nearestDistance) continue;
            nearestDistance = distance;
            nearest = segment;
        }
        return nearest;
    }

    private static double distanceSquaredToSegment(@NotNull Location location,
                                                    @NotNull Location from, @NotNull Location to) {
        if (location.getWorld() != from.getWorld() || from.getWorld() != to.getWorld())
            return Double.POSITIVE_INFINITY;
        double x = to.getX() - from.getX();
        double y = to.getY() - from.getY();
        double z = to.getZ() - from.getZ();
        double lengthSquared = x * x + y * y + z * z;
        double progress = lengthSquared <= 0.0001D ? 0D
                : ((location.getX() - from.getX()) * x + (location.getY() - from.getY()) * y
                + (location.getZ() - from.getZ()) * z) / lengthSquared;
        progress = Math.max(0D, Math.min(1D, progress));
        double nearestX = from.getX() + x * progress;
        double nearestY = from.getY() + y * progress;
        double nearestZ = from.getZ() + z * progress;
        double dx = location.getX() - nearestX;
        double dy = location.getY() - nearestY;
        double dz = location.getZ() - nearestZ;
        return dx * dx + dy * dy + dz * dz;
    }

    private int getWorldFallHeight() {
        World world = Bukkit.getWorld(getWorldName());
        return world == null ? -64 : world.getMinHeight();
    }

    private AceRaceLine getStartLine() {
        AceRaceConfig config = getGameConfig();
        return config.hasStartLine() ? new AceRaceLine(config.getStartLinePos1(), config.getStartLinePos2()) : null;
    }

    private AceRaceLine getFinishLine() {
        AceRaceConfig config = getGameConfig();
        return config.hasFinishLine() ? new AceRaceLine(config.getFinishLinePos1(), config.getFinishLinePos2()) : null;
    }

    public int getRespawnPointBinding(int index) {
        int loadedIndex = getRespawnPointIndexForConfig(index);
        return loadedIndex >= 0 && loadedIndex < respawnProgressPointBindings.size()
                ? respawnProgressPointBindings.get(loadedIndex) : -1;
    }

    public int getRespawnPointCount() {
        return respawnPoints.size();
    }

    public int getRespawnPointIndexForConfig(int configIndex) {
        return respawnPointConfigIndexes.indexOf(configIndex);
    }

    public int getRespawnPointConfigIndex(int loadedIndex) {
        return loadedIndex >= 0 && loadedIndex < respawnPointConfigIndexes.size()
                ? respawnPointConfigIndexes.get(loadedIndex) : -1;
    }

    public boolean setRespawnPointBinding(int index, int binding) {
        int loadedIndex = getRespawnPointIndexForConfig(index);
        if (loadedIndex < 0 || index < 0 || index >= getGameConfig().ensureRespawnPoints().size()
                || binding < -1 || binding >= progressPoints.size()) return false;
        getGameConfig().setRespawnProgressPointBinding(index, binding);
        loadCoursePoints();
        return true;
    }

    /** Toggles the editor-only course preview for the current prepare session. */
    public boolean toggleMapEditPreview(@NotNull Player viewer) {
        if (mapEditPreviewViewer != null && mapEditPreviewViewer.equals(viewer.getUniqueId())) {
            disableMapEditPreview();
            return false;
        }
        disableMapEditPreview();
        mapEditPreviewViewer = viewer.getUniqueId();
        refreshMapEditPreviewEntities();
        mapEditPreviewTask = scheduler.runTaskTimer(plugin, () -> {
            Player player = Bukkit.getPlayer(mapEditPreviewViewer);
            var session = player == null ? null : plugin.getPrepareSessionManager().getSession(player);
            String areaName = getGameConfig().getAreaName();
            if (player == null || session == null
                    || session.getGameType() != GameTypeEnum.AceRace
                    || areaName == null || !areaName.equals(session.getAreaName())) {
                disableMapEditPreview();
                return;
            }
            spawnMapEditPreviewParticles(player);
        }, MAP_EDIT_PREVIEW_UPDATE_TICKS, MAP_EDIT_PREVIEW_UPDATE_TICKS);
        return true;
    }

    public boolean isMapEditPreviewEnabled() {
        return mapEditPreviewViewer != null;
    }

    public void disableMapEditPreview() {
        if (mapEditPreviewTask != null) {
            mapEditPreviewTask.cancel();
            mapEditPreviewTask = null;
        }
        for (EnderCrystal crystal : mapEditPreviewCrystals) {
            if (crystal != null && !crystal.isDead()) crystal.remove();
        }
        mapEditPreviewCrystals.clear();
        mapEditPreviewViewer = null;
    }

    /** Returns the configured marker index represented by an editor preview crystal, or -1. */
    public int mapEditPreviewRespawnIndex(@NotNull Entity entity) {
        if (mapEditPreviewCrystals.stream().noneMatch(crystal -> crystal.getUniqueId().equals(entity.getUniqueId()))) return -1;
        String area = entity.getPersistentDataContainer().get(mapEditPreviewAreaKey, PersistentDataType.STRING);
        if (!java.util.Objects.equals(getGameConfig().getAreaName(), area)) return -1;
        Integer index = entity.getPersistentDataContainer().get(mapEditPreviewMarkerKey, PersistentDataType.INTEGER);
        return index == null || index < 0 || index >= respawnPoints.size()
                ? -1 : getRespawnPointConfigIndex(index);
    }

    private void refreshMapEditPreviewEntities() {
        for (EnderCrystal crystal : mapEditPreviewCrystals) {
            if (crystal != null && !crystal.isDead()) crystal.remove();
        }
        mapEditPreviewCrystals.clear();
        if (mapEditPreviewViewer == null) return;
        World world = Bukkit.getWorld(getWorldName());
        if (world == null) return;
        String areaName = getGameConfig().getAreaName();
        for (int index = 0; index < respawnPoints.size(); index++) {
            int markerIndex = index;
            Location location = respawnPoints.get(index).destination().clone();
            EnderCrystal crystal = world.spawn(location, EnderCrystal.class, spawned -> {
                spawned.setShowingBottom(false);
                spawned.setInvulnerable(true);
                spawned.setPersistent(false);
                int binding = markerIndex < respawnProgressPointBindings.size()
                        ? respawnProgressPointBindings.get(markerIndex) : -1;
                spawned.customName(net.kyori.adventure.text.Component.text(binding < 0
                        ? "起点后" : "进度点 #" + (binding + 1) + " 后"));
                spawned.setCustomNameVisible(true);
                spawned.getPersistentDataContainer().set(mapEditPreviewAreaKey,
                        PersistentDataType.STRING, areaName);
                spawned.getPersistentDataContainer().set(mapEditPreviewMarkerKey,
                        PersistentDataType.INTEGER, markerIndex);
            });
            mapEditPreviewCrystals.add(crystal);
        }
    }

    private void spawnMapEditPreviewParticles(@NotNull Player viewer) {
        World world = Bukkit.getWorld(getWorldName());
        if (world == null || viewer.getWorld() != world) return;
        for (AceRaceProgressPoint progressPoint : progressPoints) {
            Vector first = progressPoint.pos1();
            Vector second = progressPoint.pos2();
            double dx = second.getX() - first.getX();
            double dy = second.getY() - first.getY();
            double dz = second.getZ() - first.getZ();
            int samples = Math.max(1, (int) Math.ceil(Math.sqrt(dx * dx + dy * dy + dz * dz) / 2D));
            for (int sample = 0; sample <= samples; sample++) {
                double ratio = sample / (double) samples;
                Location particle = new Location(world, first.getX() + dx * ratio + 0.5D,
                        first.getY() + dy * ratio + 0.15D, first.getZ() + dz * ratio + 0.5D);
                viewer.spawnParticle(Particle.END_ROD, particle, 1, 0D, 0D, 0D, 0D);
            }
        }
    }

    @Override
    protected Collection<Location> getStartPreloadLocations() {
        List<Location> locations = new ArrayList<>();
        if (getGameConfig().getStartSpawnPoint() != null) locations.add(getGameConfig().getStartSpawnPoint());
        if (getGameConfig().getSpectatorSpawnPoint() != null) locations.add(getGameConfig().getSpectatorSpawnPoint());
        for (AceRaceRespawnPoint respawnPoint : respawnPoints) locations.add(respawnPoint.destination());
        return locations;
    }

    @Override
    public void resetArea() {
        stopRacerVisibilityUpdates();
        restoreAllRacerVisibility();
        cancelAllPendingLaunchPads();
        stopLaunchEnforcement();
        finishedPlayers.clear();
        nextProgressPoint.clear();
        completedLaps.clear();
        lapStartedNanos.clear();
        lapDurationsMillis.clear();
        raceStartedNanos.clear();
        latestRespawnLocations.clear();
        capturedRespawnPoints.clear();
        activeFallHeights.clear();
        lastMoveLocations.clear();
        startLineArmed.clear();
        featureContacts.clear();
        progressTask = null;
    }

    @Override
    public void startGamePreparation() {
        if (progressPoints.isEmpty() || respawnPoints.isEmpty() || getGameConfig().getStartSpawnPoint() == null
                || !getGameConfig().hasStartLine() || !getGameConfig().hasFinishLine()) {
            logGame(java.util.logging.Level.WARNING, "启动",
                    "赛道缺少进度点、重生点、起点出生点、起点线或终点线，已取消本局");
            endGameFinally();
            return;
        }
        setGameStageEnum(GameStageEnum.PREPARATION);
        hideAllRacersForPreparation();
        startGameIntroduction(this::startFormalPreparation);
    }

    private void startFormalPreparation() {
        Location start = getGameConfig().getStartSpawnPoint();
        teleportAllPlayers(start);
        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);
        resetPlayerHealthFoodEffectLevelInventory();
        announceGamePreparation(MessageConfig.ACE_RACE_START_PREPARATION,
                MessageConfig.ACE_RACE_START_PREPARATION_TITLE, MessageConfig.ACE_RACE_START_PREPARATION_SUBTITLE);
        startGameProgress();
    }

    private void startGameProgress() {
        Location start = getGameConfig().getStartSpawnPoint();
        for (UUID uuid : gamePlayers) {
            nextProgressPoint.put(uuid, 0);
            completedLaps.put(uuid, 0);
            lapStartedNanos.remove(uuid);
            lapDurationsMillis.put(uuid, new ArrayList<>());
            raceStartedNanos.remove(uuid);
            latestRespawnLocations.put(uuid, start.clone());
            capturedRespawnPoints.put(uuid, new HashSet<>());
            activeFallHeights.put(uuid, getGameConfig().getStartFallY());
            lastMoveLocations.put(uuid, start.clone());
        }
        startFinalCountdown(GameTypeEnum.AceRace.toString(), MessageConfig.ACE_RACE_GAME_START_TITLE,
                MessageConfig.ACE_RACE_GAME_START_SUBTITLE, this::beginGameProgress);
    }

    private void beginGameProgress() {
        scheduleRacerVisibilityUnlock();
        startLaunchEnforcement();
        giveTeamArmor();
        for (UUID uuid : gamePlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) handleEnvironmentalEffects(player);
        }
        // The preparation countdown owns the shared timer bar. Ace Race needs a personal lap
        // stopwatch in the same bar, so replace it with one bar per viewer before the live timer starts.
        clearGameTimerBossBar();
        progressTask = startRemainingTimer(getGameConfig().getTimer(), seconds -> {
            refreshEnvironmentalEffects();
            timer = seconds;
            updateAceRaceTimerBossBars(seconds);
        }, this::endGame);
    }

    /** Updates a countdown + personal current-lap stopwatch for every online instance viewer. */
    private void updateAceRaceTimerBossBars(int remainingSeconds) {
        Set<UUID> viewerIds = new HashSet<>(getParticipantUniqueIds());
        viewerIds.addAll(getSpectatorUniqueIds());
        for (String key : new ArrayList<>(bossBars.keySet())) {
            if (!key.startsWith(TIMER_BOSS_BAR_PREFIX)) continue;
            String uuidText = key.substring(TIMER_BOSS_BAR_PREFIX.length());
            try {
                if (!viewerIds.contains(UUID.fromString(uuidText))) removeBossBar(key);
            } catch (IllegalArgumentException ignored) {
                removeBossBar(key);
            }
        }

        double progress = getGameConfig().getTimer() <= 0 ? 0D
                : remainingSeconds / (double) getGameConfig().getTimer();
        long now = System.nanoTime();
        for (UUID uuid : viewerIds) {
            String key = TIMER_BOSS_BAR_PREFIX + uuid;
            BossBar bossBar = bossBars.computeIfAbsent(key, ignored ->
                    Bukkit.createBossBar("", BarColor.YELLOW, BarStyle.SOLID));
            bossBar.setTitle(Utils.translateColorCodes(MessageConfig.ACE_RACE_ACTION_BAR_COUNT_DOWN
                    .replace("%time%", Utils.formatMinutesSeconds(remainingSeconds))
                    .replace("%lap-time%", Utils.formatMinutesSeconds(currentLapElapsedSeconds(uuid, now)))
                    .replace("%lap%", String.valueOf(currentLapNumber(uuid)))));
            bossBar.setProgress(Math.max(0D, Math.min(1D, progress)));

            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                if (!bossBar.getPlayers().contains(player)) bossBar.addPlayer(player);
            } else {
                for (Player current : new ArrayList<>(bossBar.getPlayers())) bossBar.removePlayer(current);
            }
        }
    }

    private int currentLapNumber(@NotNull UUID uuid) {
        return Math.min(getGameConfig().getLaps(), completedLaps.getOrDefault(uuid, 0) + 1);
    }

    private long currentLapElapsedSeconds(@NotNull UUID uuid, long nowNanos) {
        List<Long> completed = lapDurationsMillis.get(uuid);
        if (finishedPlayers.contains(uuid) && completed != null && !completed.isEmpty())
            return completed.getLast() / 1_000L;
        Long started = lapStartedNanos.get(uuid);
        return started == null ? 0L : Math.max(0L, (nowNanos - started) / 1_000_000_000L);
    }

    public void handlePlayerMove(@NotNull PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (getGameStageEnum() != GameStageEnum.PROGRESS || finishedPlayers.contains(player.getUniqueId())) return;
        UUID uuid = player.getUniqueId();
        Location current = event.getTo();
        if (current == null) return;
        Location previous = lastMoveLocations.put(uuid, current.clone());
        if (previous == null) previous = current;
        handleEnvironmentalEffects(player);
        // Apply stations before fall recovery as well: a fast jump into a water ring may cross the
        // station before the next movement event that would otherwise refresh its short effect.
        handleRedSpeedStation(player, previous, current);
        if (hasReachedActiveFallHeight(player) || notInArea(current)) {
            returnToLatestRespawnPoint(player);
            return;
        }
        handleTrackBlockFeature(player);

        handleProgressPoint(player, previous, current);
        handleRespawnPoints(player, previous, current);
        handleStartAndFinishLines(player, previous, current);
    }

    private void handleProgressPoint(@NotNull Player player, @NotNull Location previous,
                                     @NotNull Location current) {
        int expected = nextProgressPoint.getOrDefault(player.getUniqueId(), 0);
        // A fast riptide/elytra movement event can cross several gates before Bukkit emits the next
        // movement event. Consume every consecutive gate intersected by this same trajectory instead
        // of leaving the later gates behind the player's new position forever.
        while (expected < progressPoints.size()
                && progressPoints.get(expected).crossed(previous, current)) {
            advanceProgressPoint(player, expected);
            expected++;
        }
    }

    private void advanceProgressPoint(@NotNull Player player, int progressIndex) {
        AceRaceProgressPoint progressPoint = progressPoints.get(progressIndex);
        activeFallHeights.put(player.getUniqueId(), progressPoint.fallY());
        nextProgressPoint.put(player.getUniqueId(), progressIndex + 1);
        applyProgressPointEquipment(player, progressPoint.equipment());
        announceProgressPointEquipment(player, progressPoint.equipment());
        player.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.7F, 1F);
    }

    private void handleRespawnPoints(@NotNull Player player, @NotNull Location previous,
                                     @NotNull Location current) {
        UUID uuid = player.getUniqueId();
        Set<Integer> captured = capturedRespawnPoints.computeIfAbsent(uuid, ignored -> new HashSet<>());
        List<Integer> crossed = new ArrayList<>();
        for (int index = 0; index < respawnPoints.size(); index++) {
            if (captured.contains(index)) continue;
            AceRaceRespawnPoint respawnPoint = respawnPoints.get(index);
            if (respawnPoint.crossingProgress(previous, current) < 0.0D) continue;
            crossed.add(index);
        }
        // A single high-speed movement can pass several markers. Their configured list order is not
        // the route order, so consume them in the order in which this trajectory actually reaches them.
        crossed.sort(Comparator.comparingDouble(index ->
                respawnPoints.get(index).crossingProgress(previous, current)));
        for (int index : crossed) {
            AceRaceRespawnPoint respawnPoint = respawnPoints.get(index);
            int boundProgressPoint = index < respawnProgressPointBindings.size()
                    ? respawnProgressPointBindings.get(index) : -1;
            int expected = nextProgressPoint.getOrDefault(uuid, 0);
            // A marker may repair exactly one missed gate. Later-course markers cannot be used to
            // shortcut several gates, and markers from an older segment cannot move respawn backward.
            if (boundProgressPoint == expected && expected < progressPoints.size()) {
                advanceProgressPoint(player, expected);
                expected++;
            }
            if (boundProgressPoint != expected - 1) continue;

            captured.add(index);
            latestRespawnLocations.put(uuid, respawnPoint.destination());
        }
    }

    private void handleStartAndFinishLines(@NotNull Player player, @NotNull Location previous,
                                           @NotNull Location current) {
        AceRaceLine startLine = getStartLine();
        AceRaceLine finishLine = getFinishLine();
        if (startLine == null || finishLine == null) return;
        UUID uuid = player.getUniqueId();
        if (!startLineArmed.contains(uuid)) {
            if (startLine.crossedAtOrAbove(previous, current)) {
                startLineArmed.add(uuid);
                long startedAt = System.nanoTime();
                lapStartedNanos.put(uuid, startedAt);
                raceStartedNanos.putIfAbsent(uuid, startedAt);
            }
            return;
        }
        if (nextProgressPoint.getOrDefault(uuid, 0) < progressPoints.size()
                || !crossedFinishForward(finishLine, previous, current, startLine)) return;

        int lap = completedLaps.getOrDefault(player.getUniqueId(), 0) + 1;
        completedLaps.put(player.getUniqueId(), lap);
        long completedAt = System.nanoTime();
        // The next lap starts only when the racer crosses the start line again. Do not carry the
        // just-completed timestamp through the reset, otherwise time spent turning around at the line
        // would be incorrectly added to the next lap.
        Long lapStarted = lapStartedNanos.remove(uuid);
        if (lapStarted != null) {
            long durationMillis = Math.max(0L, (completedAt - lapStarted) / 1_000_000L);
            lapDurationsMillis.computeIfAbsent(uuid, ignored -> new ArrayList<>()).add(durationMillis);
            if (getRunMode() == ink.ziip.championshipscore.api.object.game.GameRunMode.DAILY) {
                plugin.getDailyManager().statsManager().recordPlayerTime(
                        this, uuid, DailyRecordType.ACERACE_FASTEST_LAP, durationMillis);
            }
        }
        Long raceStarted = raceStartedNanos.get(uuid);
        if (lap == 3 && getGameConfig().getLaps() == 3 && raceStarted != null
                && getRunMode() == ink.ziip.championshipscore.api.object.game.GameRunMode.DAILY) {
            long durationMillis = Math.max(0L, (completedAt - raceStarted) / 1_000_000L);
            plugin.getDailyManager().statsManager().recordPlayerTime(
                    this, uuid, DailyRecordType.ACERACE_FASTEST_THREE_LAPS, durationMillis);
        }
        if (lap < getGameConfig().getLaps()) {
            resetLapProgress(player, current);
            updateAceRaceTimerBossBars(timer);
            String message = MessageConfig.ACE_RACE_LAP_COMPLETED
                    .replace("%player%", Utils.formatPlayerName(player))
                    .replace("%lap%", String.valueOf(lap))
                    .replace("%total%", String.valueOf(getGameConfig().getLaps()));
            sendMessageToAllGamePlayers(message);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1F, 1F);
            return;
        }
        finishPlayer(player);
    }

    /** Starts a new lap without carrying any ordered gate or respawn marker state across the line. */
    private void resetLapProgress(@NotNull Player player, @NotNull Location movementBaseline) {
        UUID uuid = player.getUniqueId();
        nextProgressPoint.put(uuid, 0);
        cancelPendingLaunchPad(uuid);
        player.removePotionEffect(PotionEffectType.SPEED);
        handleEnvironmentalEffects(player);
        activeFallHeights.put(uuid, getGameConfig().getStartFallY());
        applyProgressPointEquipment(player, AceRaceEquipment.NONE);
        Location start = getGameConfig().getStartSpawnPoint();
        if (start != null) latestRespawnLocations.put(uuid, start.clone());
        capturedRespawnPoints.computeIfAbsent(uuid, ignored -> new HashSet<>()).clear();
        startLineArmed.remove(uuid);
        lastMoveLocations.put(uuid, movementBaseline.clone());
    }

    private boolean crossedFinishForward(@NotNull AceRaceLine finishLine, @NotNull Location previous,
                                         @NotNull Location current, @NotNull AceRaceLine startLine) {
        if (!finishLine.crossedAtOrAbove(previous, current)) return false;
        World world = current.getWorld();
        if (world == null) return false;
        Location startCenter = startLine.center(world);
        if (finishLine.sameGeometry(startLine)) {
            Location startSpawn = getGameConfig().getStartSpawnPoint();
            return startSpawn != null && finishLine.crossedTowardReferenceSide(previous, current, startSpawn);
        }
        if (finishLine.side(startCenter) != 0)
            return finishLine.crossedTowardReferenceSide(previous, current, startCenter);
        Location finishCenter = finishLine.center(world);
        Vector towardStart = startCenter.toVector().subtract(finishCenter.toVector());
        towardStart.setY(0D);
        Vector movement = current.toVector().subtract(previous.toVector());
        movement.setY(0D);
        return towardStart.lengthSquared() > 0.0001D && movement.dot(towardStart) > 0D;
    }

    private void handleTrackBlockFeature(@NotNull Player player) {
        Block block = findTrackFeatureBlock(player.getLocation());
        if (block == null) {
            featureContacts.remove(player.getUniqueId());
            return;
        }
        Material material = block.getType();
        TrackFeatureContact featureContact = TrackFeatureContact.from(block);
        if (featureContact.equals(featureContacts.put(player.getUniqueId(), featureContact))) return;

        switch (material) {
            case YELLOW_GLAZED_TERRACOTTA -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,
                        SPEED_BOOST_DURATION_TICKS, YELLOW_SPEED_AMPLIFIER, true, false, false));
                player.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.6F, 1.5F);
                Utils.sendActionBar(player, MessageConfig.ACE_RACE_SPEED_BOOST);
            }
            case LIME_GLAZED_TERRACOTTA -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST,
                        JUMP_BOOST_DURATION_TICKS, 7, true, false, false));
                player.playSound(player.getLocation(), Sound.BLOCK_SLIME_BLOCK_FALL, 0.8F, 1.2F);
                Utils.sendActionBar(player, MessageConfig.ACE_RACE_JUMP_PAD);
            }
            case RED_WOOL -> scheduleLaunchPlayer(player, launchHorizontalVelocity(material),
                    launchBaseVerticalVelocity(material), 1.1F,
                    LAUNCH_PAD_DELAY_TICKS);
            case ORANGE_WOOL -> scheduleLaunchPlayer(player, launchHorizontalVelocity(material),
                    launchBaseVerticalVelocity(material), 1.35F,
                    LAUNCH_PAD_DELAY_TICKS);
            default -> {
            }
        }
    }

    private static boolean isTrackFeature(@NotNull Material material) {
        return material == Material.YELLOW_GLAZED_TERRACOTTA
                || material == Material.LIME_GLAZED_TERRACOTTA
                || material == Material.RED_WOOL
                || material == Material.ORANGE_WOOL;
    }

    private static boolean isLaunchPad(@NotNull Material material) {
        return material == Material.RED_WOOL || material == Material.ORANGE_WOOL;
    }

    static double launchHorizontalVelocity(@NotNull Material material) {
        return switch (material) {
            case RED_WOOL -> 2D;
            case ORANGE_WOOL -> 4D;
            default -> throw new IllegalArgumentException("Not an Ace Race launch pad: " + material);
        };
    }

    static double launchBaseVerticalVelocity(@NotNull Material material) {
        return switch (material) {
            case RED_WOOL -> 0.75D;
            case ORANGE_WOOL -> 1.5D;
            default -> throw new IllegalArgumentException("Not an Ace Race launch pad: " + material);
        };
    }

    private static @Nullable Block findTrackFeatureBlock(@NotNull Location location) {
        Block playerBlock = location.getBlock();
        Block directlyBelow = playerBlock.getRelative(0, -1, 0);
        if (isTrackFeature(directlyBelow.getType())) return directlyBelow;
        Block twoBlocksBelow = playerBlock.getRelative(0, -2, 0);
        if (twoBlocksBelow.getType() == Material.YELLOW_GLAZED_TERRACOTTA) return twoBlocksBelow;
        return null;
    }

    private void refreshEnvironmentalEffects() {
        for (UUID uuid : gamePlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && !isManagedSpectator(player)) handleEnvironmentalEffects(player);
        }
    }

    private void handleEnvironmentalEffects(@NotNull Player player) {
        if (player.isInWater()) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.CONDUIT_POWER,
                    ENVIRONMENTAL_EFFECT_DURATION_TICKS, 0, true, false, false));
            applyWaterEnvironmentSpeed(player);
            return;
        }
        applyBaseSpeed(player, false);
    }

    private void applyWaterEnvironmentSpeed(@NotNull Player player) {
        PotionEffect current = player.getPotionEffect(PotionEffectType.SPEED);
        if (current != null && current.getAmplifier() == YELLOW_SPEED_AMPLIFIER) {
            player.removePotionEffect(PotionEffectType.SPEED);
        }
        applyBaseSpeed(player, false);
    }

    private void handleRedSpeedStation(@NotNull Player player, @NotNull Location previous,
                                       @NotNull Location current) {
        int radius = player.isInWater() ? WATER_SPEED_STATION_RADIUS : SPEED_STATION_RADIUS;
        double radiusSquared = player.isInWater()
                ? WATER_SPEED_STATION_RADIUS_SQUARED : SPEED_STATION_RADIUS_SQUARED;
        boolean currentlyNear = isNearStation(current, Material.RED_GLAZED_TERRACOTTA,
                radius, radiusSquared);
        boolean crossedStation = currentlyNear || isNearStationAlongPath(previous, current,
                Material.RED_GLAZED_TERRACOTTA, radius, radiusSquared);
        // Refresh while the player remains inside the station, and still grant one full pulse when a
        // high-speed movement crosses the station between two movement packets.
        if (crossedStation) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,
                    RED_SPEED_DURATION_TICKS, RED_SPEED_AMPLIFIER, true, false, false));
            player.addPotionEffect(new PotionEffect(PotionEffectType.DOLPHINS_GRACE,
                    RED_SPEED_DURATION_TICKS, 0, true, false, false));
        }
    }

    private void applyBaseSpeed(@NotNull Player player, boolean replaceCurrent) {
        PotionEffect current = player.getPotionEffect(PotionEffectType.SPEED);
        if (!replaceCurrent && current != null) return;
        if (replaceCurrent) player.removePotionEffect(PotionEffectType.SPEED);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,
                PotionEffect.INFINITE_DURATION, BASE_SPEED_AMPLIFIER, true, false, false));
    }

    /** Each progress segment owns the threshold at which fall recovery becomes active. */
    private boolean hasReachedActiveFallHeight(@NotNull Player player) {
        return player.getLocation().getY() <= activeFallHeights.getOrDefault(
                player.getUniqueId(), getGameConfig().getStartFallY());
    }

    private void giveTeamArmor() {
        for (UUID uuid : gamePlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) giveTeamArmor(player);
        }
    }

    private void giveTeamArmor(@NotNull Player player) {
        ChampionshipTeam team = plugin.getTeamManager().getTeamByPlayer(player.getUniqueId());
        if (team == null) return;
        player.getInventory().setHelmet(unbreakable(team.getHelmet()));
        player.getInventory().setLeggings(unbreakableSwiftSneakLeggings(team.getLeggings()));
        player.getInventory().setBoots(unbreakableDepthStriderBoots(team.getBoots()));
    }

    private static @NotNull ItemStack unbreakable(@NotNull ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setUnbreakable(true);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static @NotNull ItemStack unbreakableSwiftSneakLeggings(@NotNull ItemStack leggings) {
        ItemMeta meta = leggings.getItemMeta();
        if (meta != null) {
            meta.setUnbreakable(true);
            meta.addEnchant(Enchants.get(EnchantmentKeys.SWIFT_SNEAK), 3, true);
            leggings.setItemMeta(meta);
        }
        return leggings;
    }

    private static @NotNull ItemStack unbreakableDepthStriderBoots(@NotNull ItemStack boots) {
        ItemMeta meta = boots.getItemMeta();
        if (meta != null) {
            meta.setUnbreakable(true);
            meta.addEnchant(Enchants.get(EnchantmentKeys.DEPTH_STRIDER), DEPTH_STRIDER_LEVEL, true);
            boots.setItemMeta(meta);
        }
        return boots;
    }

    private static void setDepthStriderLevel(@NotNull PlayerInventory inventory, int level) {
        ItemStack boots = inventory.getBoots();
        if (boots == null) return;
        ItemMeta meta = boots.getItemMeta();
        if (meta == null) return;
        meta.removeEnchant(Enchants.get(EnchantmentKeys.DEPTH_STRIDER));
        meta.addEnchant(Enchants.get(EnchantmentKeys.DEPTH_STRIDER), level, true);
        boots.setItemMeta(meta);
        inventory.setBoots(boots);
    }

    /** Returns whether at least one trident was removed from any player inventory slot. */
    private boolean removeAllTridents(@NotNull Player player) {
        boolean removed = false;
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item != null && item.getType() == Material.TRIDENT) {
                inventory.setItem(slot, null);
                removed = true;
            }
        }
        return removed;
    }

    /** Removes race-issued elytras that a player may have moved out of the chestplate slot. */
    private static void removeAllElytrasOutsideChestplate(@NotNull PlayerInventory inventory) {
        ItemStack[] storage = inventory.getStorageContents();
        for (int slot = 0; slot < storage.length; slot++) {
            ItemStack item = storage[slot];
            if (item != null && item.getType() == Material.ELYTRA) {
                inventory.setItem(slot, null);
            }
        }
        ItemStack offHand = inventory.getItemInOffHand();
        if (offHand.getType() == Material.ELYTRA) {
            inventory.setItemInOffHand(null);
        }
    }

    private static @NotNull ItemStack createRiptideTrident() {
        ItemStack trident = new ItemStack(Material.TRIDENT);
        trident.addEnchantment(Enchants.get(EnchantmentKeys.RIPTIDE), 1);
        ItemMeta meta = trident.getItemMeta();
        meta.setUnbreakable(true);
        trident.setItemMeta(meta);
        return trident;
    }

    public void applyIntermediateRiptideBoost(@NotNull PlayerRiptideEvent event) {
        Player player = event.getPlayer();
        if (getGameStageEnum() != GameStageEnum.PROGRESS || notAreaPlayer(player)
                || finishedPlayers.contains(player.getUniqueId())) return;
        if (event.getItem().getEnchantmentLevel(Enchants.get(EnchantmentKeys.RIPTIDE)) != 1) return;

        // Paper fires this event immediately before adding the vanilla riptide vector. Keep only a
        // small course-specific lift above vanilla Riptide I so the boost does not overshoot rings.
        Vector extraVelocity = event.getVelocity().multiply(RIPTIDE_EXTRA_MULTIPLIER);
        player.setVelocity(player.getVelocity().add(extraVelocity));
    }

    private static @NotNull ItemStack createCourseElytra() {
        ItemStack elytra = new ItemStack(Material.ELYTRA);
        ItemMeta meta = elytra.getItemMeta();
        meta.setUnbreakable(true);
        elytra.setItemMeta(meta);
        return elytra;
    }

    private void applyProgressPointEquipment(@NotNull Player player, @NotNull AceRaceEquipment equipment) {
        PlayerInventory inventory = player.getInventory();
        setDepthStriderLevel(inventory, equipment == AceRaceEquipment.DOLPHINS_GRACE
                ? DOLPHINS_GRACE_DEPTH_STRIDER_LEVEL : DEPTH_STRIDER_LEVEL);
        // Elytra can be manually unequipped into storage/off-hand. Clear those copies whenever
        // the stage changes so an old elytra cannot be carried into a different equipment stage.
        removeAllElytrasOutsideChestplate(inventory);
        ItemStack chestplate = inventory.getChestplate();
        if (equipment != AceRaceEquipment.ELYTRA
                && chestplate != null && chestplate.getType() == Material.ELYTRA) {
            inventory.setChestplate(null);
        }
        if (equipment != AceRaceEquipment.TRIDENT) removeAllTridents(player);
        if (equipment != AceRaceEquipment.DOLPHINS_GRACE) {
            player.removePotionEffect(PotionEffectType.DOLPHINS_GRACE);
        }

        if (equipment == AceRaceEquipment.ELYTRA) {
            if (chestplate == null || chestplate.getType() != Material.ELYTRA) {
                inventory.setChestplate(createCourseElytra());
                player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_ELYTRA, 1F, 1F);
            }
        } else if (equipment == AceRaceEquipment.TRIDENT && !hasTrident(player)) {
            inventory.addItem(createRiptideTrident());
        } else if (equipment == AceRaceEquipment.DOLPHINS_GRACE) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.DOLPHINS_GRACE,
                    PotionEffect.INFINITE_DURATION, 0, true, false, false));
            if (player.isInWater()) applyWaterEnvironmentSpeed(player);
        }
    }

    /** Rebuilds the deterministic race loadout for players who were offline when the race began. */
    private void restoreRacerEquipment(@NotNull Player player) {
        player.getInventory().clear();
        giveTeamArmor(player);
        int reached = nextProgressPoint.getOrDefault(player.getUniqueId(), 0) - 1;
        applyProgressPointEquipment(player, reached >= 0 && reached < progressPoints.size()
                ? progressPoints.get(reached).equipment() : AceRaceEquipment.NONE);
    }

    private void announceProgressPointEquipment(@NotNull Player player, @NotNull AceRaceEquipment equipment) {
        if (equipment == AceRaceEquipment.ELYTRA) {
            player.sendMessage(MessageConfig.ACE_RACE_RECEIVED_ELYTRA);
        } else if (equipment == AceRaceEquipment.TRIDENT) {
            player.sendMessage(MessageConfig.ACE_RACE_RECEIVED_TRIDENT);
        } else if (equipment == AceRaceEquipment.DOLPHINS_GRACE) {
            player.sendMessage(MessageConfig.ACE_RACE_RECEIVED_DOLPHINS_GRACE);
        }
    }

    private static boolean hasTrident(@NotNull Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == Material.TRIDENT) return true;
        }
        return false;
    }

    private boolean isNearStationAlongPath(@NotNull Location previous, @NotNull Location current,
                                           @NotNull Material stationMaterial, int radius,
                                           double radiusSquared) {
        if (previous.getWorld() == null || !previous.getWorld().equals(current.getWorld())) return false;
        Vector movement = current.toVector().subtract(previous.toVector());
        if (movement.lengthSquared() <= 1D) return false;
        int samples = Math.min(128, Math.max(1, (int) Math.ceil(movement.length())));
        // The current endpoint was already checked above. Only long movement packets need interior
        // samples; ordinary sub-block movement therefore keeps the same single station lookup cost.
        for (int sample = 1; sample < samples; sample++) {
            Location location = previous.clone().add(movement.clone().multiply(sample / (double) samples));
            if (isNearStation(location, stationMaterial, radius, radiusSquared)) return true;
        }
        return false;
    }

    private boolean isNearStation(@NotNull Location location, @NotNull Material stationMaterial,
                                  int radius, double radiusSquared) {
        if (location.getWorld() == null) return false;
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        for (int blockX = x - radius; blockX <= x + radius; blockX++) {
            for (int blockY = y - radius; blockY <= y + radius; blockY++) {
                for (int blockZ = z - radius; blockZ <= z + radius; blockZ++) {
                    if (location.getWorld().getBlockAt(blockX, blockY, blockZ).getType() != stationMaterial) continue;
                    double dx = location.getX() - (blockX + 0.5D);
                    double dy = location.getY() - (blockY + 0.5D);
                    double dz = location.getZ() - (blockZ + 0.5D);
                    if (dx * dx + dy * dy + dz * dz <= radiusSquared) return true;
                }
            }
        }
        return false;
    }

    private void launchPlayer(@NotNull Player player, double horizontalVelocity, double verticalVelocity, float pitch) {
        Vector direction = player.getLocation().getDirection();
        // Pitch still adjusts lift, but looking at the floor must retain a useful launch and looking
        // straight up must not double the pad height.
        double aimedVerticalVelocity = calculateAimedVerticalVelocity(verticalVelocity, direction.getY());
        direction.setY(0D);
        if (direction.lengthSquared() < 0.0001D) {
            double yawRadians = Math.toRadians(player.getLocation().getYaw());
            direction = new Vector(-Math.sin(yawRadians), 0D, Math.cos(yawRadians));
        }
        else direction.normalize();
        // setVelocity replaces the old motion with this fixed launch vector; existing momentum is not
        // carried into the pad's horizontal or vertical impulse.
        player.setVelocity(new Vector(direction.getX() * horizontalVelocity, aimedVerticalVelocity,
                direction.getZ() * horizontalVelocity));
        player.setFallDistance(0F);
        // The client, not the server, decides which drag branch this impulse lands in, so hold the flight
        // to the grounded curve for the next few ticks instead of trusting the packet to be applied fairly.
        enforcedLaunches.put(player.getUniqueId(),
                new LaunchEnforcement(horizontalVelocity, player.getLocation().clone()));
        player.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.8F, pitch);
        Utils.sendActionBar(player, MessageConfig.ACE_RACE_LAUNCH_PAD);
    }

    static double calculateAimedVerticalVelocity(double verticalVelocity, double directionY) {
        double aimedVelocity = verticalVelocity * (1D + directionY);
        return Math.max(verticalVelocity * MIN_LAUNCH_VERTICAL_MULTIPLIER,
                Math.min(verticalVelocity * MAX_LAUNCH_VERTICAL_MULTIPLIER, aimedVelocity));
    }

    /**
     * Horizontal speed the intended flight carries during the given tick, where tick zero is the launch
     * itself. The launch tick still travels the raw impulse and only afterwards pays the pad's grounded
     * drag; every later tick pays air drag and may add the sprint air acceleration a racer can reach by
     * holding forward, which keeps this an upper bound on honest movement rather than an exact replay.
     */
    static double launchEnvelopeSpeed(double horizontalVelocity, int ticksSinceLaunch) {
        double speed = horizontalVelocity;
        for (int tick = 1; tick <= ticksSinceLaunch; tick++) {
            speed = speed * (tick == 1 ? LAUNCH_GROUND_DRAG : AIR_DRAG) + SPRINT_AIR_ACCELERATION;
        }
        return speed;
    }

    /** Total horizontal distance the intended flight covers through the given tick, inclusive. */
    static double launchEnvelopeDistance(double horizontalVelocity, int ticksSinceLaunch) {
        double speed = horizontalVelocity;
        double distance = speed;
        for (int tick = 1; tick <= ticksSinceLaunch; tick++) {
            speed = speed * (tick == 1 ? LAUNCH_GROUND_DRAG : AIR_DRAG) + SPRINT_AIR_ACCELERATION;
            distance += speed;
        }
        return distance;
    }

    /**
     * Target speed for a racer already ahead of the envelope. The excess distance is repaid over the next
     * few ticks rather than merely capped, so the head start won during the round trip before the first
     * correction packet lands is given back instead of kept.
     */
    static double enforcedLaunchSpeed(double envelopeSpeed, double debt) {
        if (debt <= 0D) return envelopeSpeed;
        return Math.max(0D, envelopeSpeed - debt / LAUNCH_DEBT_REPAY_TICKS);
    }

    private void startLaunchEnforcement() {
        stopLaunchEnforcement();
        launchEnforcementTask = scheduler.runTaskTimer(plugin, this::enforceLaunchTrajectories,
                LAUNCH_ENFORCEMENT_UPDATE_TICKS, LAUNCH_ENFORCEMENT_UPDATE_TICKS);
    }

    private void stopLaunchEnforcement() {
        if (launchEnforcementTask != null) launchEnforcementTask.cancel();
        launchEnforcementTask = null;
        enforcedLaunches.clear();
    }

    /**
     * Compares the distance each launched racer actually covered against the intended flight and slows the
     * ones running ahead of it. Honest flights stay under the envelope and are never touched, so no extra
     * velocity packet reaches them.
     */
    private void enforceLaunchTrajectories() {
        if (enforcedLaunches.isEmpty()) return;
        if (getGameStageEnum() != GameStageEnum.PROGRESS) {
            enforcedLaunches.clear();
            return;
        }
        Iterator<Map.Entry<UUID, LaunchEnforcement>> entries = enforcedLaunches.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<UUID, LaunchEnforcement> entry = entries.next();
            Player player = Bukkit.getPlayer(entry.getKey());
            LaunchEnforcement launch = entry.getValue();
            // Riptide and elytra are sanctioned ways to outrun the pad's own curve; never fight them.
            if (player == null || !player.isOnline() || finishedPlayers.contains(entry.getKey())
                    || isManagedSpectator(player) || player.isRiptiding() || player.isGliding()) {
                entries.remove();
                continue;
            }
            if (launch.advance(player) >= LAUNCH_ENFORCEMENT_TICKS) entries.remove();
        }
    }

    /** Per-launch flight budget: measured horizontal travel checked against the intended grounded curve. */
    private static final class LaunchEnforcement {
        private final double horizontalVelocity;
        private Location lastSample;
        private double travelled;
        private int ticksSinceLaunch;
        private int ticksWaitingForImpulse;
        private boolean impulseObserved;

        private LaunchEnforcement(double horizontalVelocity, @NotNull Location launchLocation) {
            this.horizontalVelocity = horizontalVelocity;
            this.lastSample = launchLocation;
        }

        /**
         * Samples one tick of travel, corrects an over-budget racer, and reports how far through the
         * enforcement window this launch is. {@link AceRaceArea#LAUNCH_ENFORCEMENT_EXPIRED} ends it.
         */
        private int advance(@NotNull Player player) {
            Location current = player.getLocation();
            double stepX = 0D;
            double stepZ = 0D;
            if (current.getWorld() == lastSample.getWorld()) {
                stepX = current.getX() - lastSample.getX();
                stepZ = current.getZ() - lastSample.getZ();
            }
            lastSample = current;
            double step = Math.sqrt(stepX * stepX + stepZ * stepZ);

            // The impulse only takes effect once the packet reaches the client, so anchor the budget to the
            // tick the racer visibly accelerates rather than to the tick the server sent it. Counting from
            // the send would let a high-ping racer bank a large distance credit during the round trip and
            // then spend it on the very head start this is meant to remove.
            if (!impulseObserved) {
                if (step < horizontalVelocity * LAUNCH_DETECTION_FRACTION) {
                    return ++ticksWaitingForImpulse >= LAUNCH_IMPULSE_WAIT_TICKS ? LAUNCH_ENFORCEMENT_EXPIRED : 0;
                }
                impulseObserved = true;
                travelled = step;
                return 0;
            }

            travelled += step;
            ticksSinceLaunch++;
            double debt = travelled - launchEnvelopeDistance(horizontalVelocity, ticksSinceLaunch)
                    - LAUNCH_DISTANCE_TOLERANCE;
            if (debt > 0D) {
                double target = enforcedLaunchSpeed(
                        launchEnvelopeSpeed(horizontalVelocity, ticksSinceLaunch), debt);
                applyHorizontalSpeed(player, stepX, stepZ, step, target);
            }
            return ticksSinceLaunch;
        }

        /**
         * Rescales horizontal motion to {@code target}, keeping the racer's own heading and vertical
         * motion. Both are taken from the measured step because a player entity's server-side delta
         * movement is not a faithful record of client-driven motion.
         */
        private void applyHorizontalSpeed(@NotNull Player player, double stepX, double stepZ,
                                          double step, double target) {
            if (step <= target || step < 0.0001D) return;
            double scale = target / step;
            player.setVelocity(new Vector(stepX * scale, player.getVelocity().getY(), stepZ * scale));
        }
    }

    private void scheduleLaunchPlayer(@NotNull Player player, double horizontalVelocity,
                                      double verticalVelocity, float pitch,
                                      long delayTicks) {
        UUID uuid = player.getUniqueId();
        cancelPendingLaunchPad(uuid);
        // Any wait before the impulse is a predictable window in which a racer can leave the wool and have
        // the packet land on the airborne drag branch, so fire within the contact tick when possible.
        if (delayTicks <= 0L) {
            launchPlayer(player, horizontalVelocity, verticalVelocity, pitch);
            return;
        }
        BukkitTask task = scheduler.runTaskLater(plugin, () -> {
            pendingLaunchPadTasks.remove(uuid);
            if (getGameStageEnum() != GameStageEnum.PROGRESS || finishedPlayers.contains(uuid)
                    || !player.isOnline()) return;
            launchPlayer(player, horizontalVelocity, verticalVelocity, pitch);
        }, delayTicks);
        pendingLaunchPadTasks.put(uuid, task);
    }

    private void cancelPendingLaunchPad(@NotNull UUID uuid) {
        BukkitTask task = pendingLaunchPadTasks.remove(uuid);
        if (task != null) task.cancel();
        enforcedLaunches.remove(uuid);
    }

    private void cancelAllPendingLaunchPads() {
        for (BukkitTask task : pendingLaunchPadTasks.values()) task.cancel();
        pendingLaunchPadTasks.clear();
        enforcedLaunches.clear();
    }

    private record TrackFeatureContact(@NotNull UUID worldId, @NotNull Material material, int x, int y, int z) {
        private static @NotNull TrackFeatureContact from(@NotNull Block block) {
            Material material = block.getType();
            if (isLaunchPad(material)) {
                // Adjacent wool blocks form one pad. Keep it active until the player leaves the pad
                // so crossing a block boundary during takeoff cannot launch the player twice.
                return new TrackFeatureContact(block.getWorld().getUID(), material, 0, 0, 0);
            }
            return new TrackFeatureContact(
                    block.getWorld().getUID(), material, block.getX(), block.getY(), block.getZ());
        }
    }

    private void finishPlayer(@NotNull Player player) {
        UUID uuid = player.getUniqueId();
        if (finishedPlayers.contains(uuid)) return;
        cancelPendingLaunchPad(uuid);
        finishedPlayers.add(uuid);
        int place = finishedPlayers.size();
        int points = Math.max(getGameConfig().getMinimumFinishPoints(),
                getGameConfig().getFirstPlacePoints() - (place - 1) * getGameConfig().getPlacementDecrement())
                + getGameConfig().getPlacementBonus(place);
        addPlayerPoints(uuid, points);
        sendMessageToAllGamePlayers(MessageConfig.ACE_RACE_FINISHED
                .replace("%player%", Utils.formatPlayerName(player))
                .replace("%place%", String.valueOf(place)));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1F, 1.4F);
        player.removePotionEffect(PotionEffectType.SPEED);
        player.removePotionEffect(PotionEffectType.DOLPHINS_GRACE);
        applyProgressPointEquipment(player, AceRaceEquipment.NONE);
        restoreRacerVisibility(player);
        player.setGameMode(GameMode.SPECTATOR);
        // Formal matches retain the complete roster. DAILY runs treat a disconnected racer as unable
        // to finish, so that UUID must not keep the remaining racers in PROGRESS.
        Collection<UUID> requiredParticipants = getRunMode()
                == ink.ziip.championshipscore.api.object.game.GameRunMode.DAILY
                ? gamePlayers.stream().filter(participantId -> Bukkit.getPlayer(participantId) != null).toList()
                : gamePlayers;
        if (allParticipantsFinished(requiredParticipants, finishedPlayers)) endGame();
    }

    /** Uses roster membership rather than equal list sizes so duplicate or already-removed entries cannot delay the end. */
    static boolean allParticipantsFinished(@NotNull Collection<UUID> participants,
                                           @NotNull Collection<UUID> finished) {
        return !participants.isEmpty() && finished.containsAll(participants);
    }

    public void returnToLatestRespawnPoint(@NotNull Player player) {
        if (getGameStageEnum() != GameStageEnum.PROGRESS || finishedPlayers.contains(player.getUniqueId())) return;
        cancelPendingLaunchPad(player.getUniqueId());
        Location destination = latestRespawnLocations.getOrDefault(
                player.getUniqueId(), getGameConfig().getStartSpawnPoint());
        if (destination == null) return;
        player.teleport(destination);
        player.setFallDistance(0F);
        lastMoveLocations.put(player.getUniqueId(), destination.clone());
        Utils.sendActionBar(player, MessageConfig.ACE_RACE_RETURNED_TO_RESPAWN_POINT);
    }

    public int getPlayerPosition(@NotNull UUID uuid) {
        return getPlayerPlacementRange(uuid).first();
    }

    /**
     * Displays a shared placement interval when multiple unfinished racers have reached the same lap and
     * ordered progress point. Finished racers always retain their exact finish position.
     */
    public @NotNull String getPlayerPositionDisplay(@NotNull UUID uuid) {
        PlacementRange range = getPlayerPlacementRange(uuid);
        return range.first() == range.last()
                ? String.valueOf(range.first())
                : range.first() + "-" + range.last();
    }

    private @NotNull PlacementRange getPlayerPlacementRange(@NotNull UUID uuid) {
        if (!gamePlayers.contains(uuid)) return new PlacementRange(0, 0);
        int finishedIndex = finishedPlayers.indexOf(uuid);
        if (finishedIndex >= 0) {
            int place = finishedIndex + 1;
            return new PlacementRange(place, place);
        }

        int progress = playerProgress(uuid);
        int ahead = finishedPlayers.size();
        int tied = 0;
        for (UUID other : gamePlayers) {
            if (finishedPlayers.contains(other)) continue;
            int otherProgress = playerProgress(other);
            if (otherProgress > progress) ahead++;
            else if (otherProgress == progress) tied++;
        }
        int first = ahead + 1;
        return new PlacementRange(first, first + Math.max(1, tied) - 1);
    }

    public int getCurrentLap(@NotNull UUID uuid) {
        if (!gamePlayers.contains(uuid)) return 0;
        if (finishedPlayers.contains(uuid)) return getGameConfig().getLaps();
        return Math.min(getGameConfig().getLaps(), completedLaps.getOrDefault(uuid, 0) + 1);
    }

    /** Returns a completed lap duration in the same mm:ss format used by the live clocks. */
    public @NotNull String getLapDurationDisplay(@NotNull UUID uuid, int lap) {
        if (lap < 1) return MessageConfig.PLACEHOLDER_NONE;
        List<Long> durations = lapDurationsMillis.get(uuid);
        if (durations == null || lap > durations.size()) return MessageConfig.PLACEHOLDER_NONE;
        return Utils.formatMinutesSeconds(durations.get(lap - 1) / 1_000L);
    }

    /** Returns the three fixed sidebar slots, preserving a dash until each lap is completed. */
    public @NotNull String getLapDurationsDisplay(@NotNull UUID uuid) {
        List<Long> durations = lapDurationsMillis.get(uuid);
        List<String> slots = new ArrayList<>(3);
        for (int index = 0; index < 3; index++) {
            slots.add(durations != null && index < durations.size()
                    ? Utils.formatMinutesSeconds(durations.get(index) / 1_000L) : "-");
        }
        return String.join(" / ", slots);
    }

    public int getReachedProgressPoint(@NotNull UUID uuid) {
        return gamePlayers.contains(uuid) ? nextProgressPoint.getOrDefault(uuid, 0) : 0;
    }

    private int playerProgress(@NotNull UUID uuid) {
        return completedLaps.getOrDefault(uuid, 0) * progressPoints.size()
                + nextProgressPoint.getOrDefault(uuid, 0);
    }

    @Override
    public void endGame() {
        if (getGameStageEnum() == GameStageEnum.WAITING || getGameStageEnum() == GameStageEnum.END) return;
        if (progressTask != null) progressTask.cancel();
        cancelAllPendingLaunchPads();
        stopLaunchEnforcement();
        stopRacerVisibilityUpdates();
        restoreAllRacerVisibility();
        cleanInventoryForAllGamePlayers();
        announceGameEnd(MessageConfig.ACE_RACE_GAME_END_TITLE, MessageConfig.ACE_RACE_GAME_END_SUBTITLE);
        setGameStageEnum(GameStageEnum.END);
        beginPostGameSettlement();
        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);
        resetPlayerHealthFoodEffectLevelInventory();
        if (isSettlementAllowed()) sendMessageToAllGamePlayers(getTeamPointsRank());
        addPlayerPointsToDatabase();
        publishGameEndEvent(new SingleGameEndEvent(this, gameTeams));
        finishPostGameAfterEndEvent();
    }

    @Override
    public void handlePlayerDeath(@NotNull PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (notAreaPlayer(player)) return;
        event.setKeepInventory(true);
        event.getDrops().clear();
        scheduler.runTask(plugin, () -> {
            player.spigot().respawn();
            if (getGameStageEnum() == GameStageEnum.PROGRESS) {
                player.setGameMode(GameMode.ADVENTURE);
                returnToLatestRespawnPoint(player);
                restoreRacerEquipment(player);
                applyBaseSpeed(player, true);
            }
        });
    }

    @Override
    public void handlePlayerQuit(@NotNull PlayerQuitEvent event) {
        Player player = event.getPlayer();
        cancelPendingLaunchPad(player.getUniqueId());
        if (!notAreaPlayer(player)) {
            visibleRacerPairs.removeIf(pair -> pair.contains(player.getUniqueId()));
            releaseRacerVisibility(player);
        }
    }

    @Override
    public void handlePlayerJoin(@NotNull PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (notAreaPlayer(player)) return;
        if (!finishedPlayers.contains(player.getUniqueId())) {
            visibleRacerPairs.removeIf(pair -> pair.contains(player.getUniqueId()));
            hideRacer(player);
            refreshRacerVisibility();
        }
        if (getGameStageEnum() == GameStageEnum.PREPARATION) {
            player.teleport(getPreparationTeleportLocation(getGameConfig().getStartSpawnPoint()));
            player.setGameMode(GameMode.ADVENTURE);
        } else if (getGameStageEnum() == GameStageEnum.COUNTDOWN || getGameStageEnum() == GameStageEnum.PROGRESS) {
            if (finishedPlayers.contains(player.getUniqueId())) {
                player.teleport(getSpectatorSpawnLocation());
                player.setGameMode(GameMode.SPECTATOR);
            } else {
                player.setGameMode(GameMode.ADVENTURE);
                returnToLatestRespawnPoint(player);
                restoreRacerEquipment(player);
                applyBaseSpeed(player, true);
            }
        } else {
            releaseRacerVisibility(player);
            player.teleport(getSpectatorSpawnLocation());
            player.setGameMode(GameMode.ADVENTURE);
        }
    }

    private void stopRacerVisibilityUpdates() {
        if (racerVisibilityUnlockTask != null) {
            racerVisibilityUnlockTask.cancel();
            racerVisibilityUnlockTask = null;
        }
        if (racerVisibilityTask != null) {
            racerVisibilityTask.cancel();
            racerVisibilityTask = null;
        }
        racerVisibilityUnlocked = false;
        removeAllRacerNameDisplays();
        visibleRacerPairs.clear();
        riptideHiddenViews.clear();
        riptideViewerGraceTicks.clear();
    }

    /** Preparation keeps all racers fully hidden, regardless of distance. */
    private void hideAllRacersForPreparation() {
        stopRacerVisibilityUpdates();
        for (UUID uuid : gamePlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) hideRacer(player);
        }
        racerVisibilityTask = scheduler.runTaskTimer(
                plugin, this::refreshRacerVisibility,
                RACER_VISIBILITY_UPDATE_TICKS, RACER_VISIBILITY_UPDATE_TICKS);
    }

    /** Keeps active racers hidden until the first minute has elapsed, then enables proximity visibility. */
    private void scheduleRacerVisibilityUnlock() {
        if (racerVisibilityUnlockTask != null) racerVisibilityUnlockTask.cancel();
        racerVisibilityUnlocked = false;
        racerVisibilityUnlockTask = scheduler.runTaskLater(plugin, () -> {
            racerVisibilityUnlockTask = null;
            if (getGameStageEnum() != GameStageEnum.PROGRESS) return;
            racerVisibilityUnlocked = true;
            refreshRacerVisibility();
        }, RACER_VISIBILITY_HIDDEN_AFTER_START_TICKS);
    }

    /** Keeps active racers mutually visible only while they are within eight blocks after the grace period. */
    private void refreshRacerVisibility() {
        GameStageEnum stage = getGameStageEnum();
        if (stage == GameStageEnum.WAITING || stage == GameStageEnum.END) return;
        List<Player> racers = new ArrayList<>();
        for (UUID uuid : gamePlayers) {
            if (finishedPlayers.contains(uuid)) continue;
            Player racer = Bukkit.getPlayer(uuid);
            if (racer == null) continue;
            disableRacerCollisions(racer);
            racers.add(racer);
        }
        if (stage != GameStageEnum.PROGRESS || !racerVisibilityUnlocked) return;
        for (Player racer : racers) {
            TextDisplay nameDisplay = ensureRacerNameDisplay(racer);
            nameDisplay.teleport(racerNameDisplayLocation(racer));
        }

        Set<UUID> activeRacers = new HashSet<>();
        Map<UUID, Location> racerLocations = new HashMap<>();
        for (Player racer : racers) activeRacers.add(racer.getUniqueId());
        for (Player racer : racers) racerLocations.put(racer.getUniqueId(), racer.getLocation());
        visibleRacerPairs.removeIf(pair -> !pair.bothIn(activeRacers));
        riptideHiddenViews.removeIf(view -> !view.bothIn(activeRacers));
        updateRiptideViewerState(racers, activeRacers);

        for (int firstIndex = 0; firstIndex < racers.size(); firstIndex++) {
            Player first = racers.get(firstIndex);
            for (int secondIndex = firstIndex + 1; secondIndex < racers.size(); secondIndex++) {
                Player second = racers.get(secondIndex);
                RacerPair pair = RacerPair.of(first.getUniqueId(), second.getUniqueId());
                Location firstLocation = racerLocations.get(first.getUniqueId());
                Location secondLocation = racerLocations.get(second.getUniqueId());
                boolean nearby = firstLocation.getWorld().equals(secondLocation.getWorld())
                        && firstLocation.distanceSquared(secondLocation)
                        <= RACER_VISIBILITY_DISTANCE_SQUARED;
                boolean visible = visibleRacerPairs.contains(pair);
                if (nearby) {
                    if (!visible) {
                        showRacerNameDisplay(second, first);
                        showRacerNameDisplay(first, second);
                        visibleRacerPairs.add(pair);
                    }
                    updateDirectionalRacerView(second, first, !visible);
                    updateDirectionalRacerView(first, second, !visible);
                } else if (visible) {
                    clearDirectionalRacerView(second, first);
                    clearDirectionalRacerView(first, second);
                    hideRacerNameDisplay(second, first);
                    hideRacerNameDisplay(first, second);
                    visibleRacerPairs.remove(pair);
                }
            }
        }
    }

    private void hideRacer(@NotNull Player player) {
        disableRacerCollisions(player);
        plugin.getVisibilityManager().seeSelf(player.getUniqueId(), visibilityOwner,
                "Ace Race 开始后一分钟内隐藏其他玩家");
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(player)) continue;
            if (gamePlayers.contains(other.getUniqueId()) && !finishedPlayers.contains(other.getUniqueId())) {
                setRacerVisible(other, player, false);
                disableRacerCollisions(other);
            }
        }
    }

    private void restoreRacerVisibility(@NotNull Player player) {
        clearRacerOutlines(player);
        removeRacerNameDisplay(player.getUniqueId());
        visibleRacerPairs.removeIf(pair -> pair.contains(player.getUniqueId()));
        riptideHiddenViews.removeIf(view -> view.contains(player.getUniqueId()));
        riptideViewerGraceTicks.remove(player.getUniqueId());
        restoreRacerCollisions(player);
        plugin.getVisibilityManager().release(player.getUniqueId(), visibilityOwner);
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(player)) continue;
            if (gamePlayers.contains(other.getUniqueId()) && !finishedPlayers.contains(other.getUniqueId()))
                setRacerVisible(other, player, false);
        }
    }

    private void restoreAllRacerVisibility() {
        for (UUID uuid : gamePlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) clearRacerOutlines(player);
        }
        visibleRacerPairs.clear();
        riptideHiddenViews.clear();
        riptideViewerGraceTicks.clear();
        plugin.getVisibilityManager().releaseAll(gamePlayers, visibilityOwner);
        for (UUID uuid : gamePlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) continue;
            restoreRacerCollisions(player);
        }
    }

    /** Cleans transient visuals on disconnect; the UUID policy intentionally survives reconnects. */
    private void releaseRacerVisibility(@NotNull Player player) {
        clearRacerOutlines(player);
        removeRacerNameDisplay(player.getUniqueId());
        riptideHiddenViews.removeIf(view -> view.contains(player.getUniqueId()));
        riptideViewerGraceTicks.remove(player.getUniqueId());
        restoreRacerCollisions(player);
    }

    /** Keeps a newly joined observer hidden from active racers without hiding racers from observers. */
    public void handleVisibilityJoin(@NotNull Player joining) {
        if (getGameStageEnum() == GameStageEnum.WAITING || getGameStageEnum() == GameStageEnum.END) return;
        // GameManagerHandler separately restores reconnecting participants. Do not overwrite its
        // distance-based result if this MONITOR join hook happens to run afterwards.
        if (gamePlayers.contains(joining.getUniqueId()) && !finishedPlayers.contains(joining.getUniqueId())) return;
        for (UUID uuid : gamePlayers) {
            Player racer = Bukkit.getPlayer(uuid);
            if (racer != null && !racer.equals(joining) && !finishedPlayers.contains(uuid))
                setRacerVisible(racer, joining, false);
        }
    }

    /** Removes both directions of every Ace Race-only invisible glow involving this racer. */
    private void clearRacerOutlines(@NotNull Player player) {
        for (UUID uuid : gamePlayers) {
            Player other = Bukkit.getPlayer(uuid);
            if (other == null || other.equals(player)) continue;
            plugin.getGlowingEntities().unsetInvisibleGlowing(other, player);
            plugin.getGlowingEntities().unsetInvisibleGlowing(player, other);
        }
    }

    /** Removes real racer entities from a riptiding player's client before client-side spin contact can occur. */
    public void handleRiptideStart(@NotNull Player player) {
        if (getGameStageEnum() != GameStageEnum.PROGRESS || notAreaPlayer(player)) return;
        // A riptide is a sanctioned way to exceed the pad's own flight curve.
        enforcedLaunches.remove(player.getUniqueId());
        riptideViewerGraceTicks.put(player.getUniqueId(), 3);
        for (UUID uuid : gamePlayers) {
            if (uuid.equals(player.getUniqueId()) || finishedPlayers.contains(uuid)) continue;
            Player target = Bukkit.getPlayer(uuid);
            if (target == null) continue;
            RacerView view = new RacerView(player.getUniqueId(), target.getUniqueId());
            if (riptideHiddenViews.add(view)) {
                plugin.getGlowingEntities().unsetInvisibleGlowing(target, player);
                setRacerVisible(player, target, false);
            }
        }
        scheduler.runTask(plugin, () -> restoreAuthoritativeRiptideState(player));
    }

    private void restoreAuthoritativeRiptideState(@NotNull Player player) {
        if (getGameStageEnum() != GameStageEnum.PROGRESS || !player.isOnline() || !player.isRiptiding()) return;
        Vector velocity = player.getVelocity();
        // Mark the riptide metadata dirty again and resend the unchanged authoritative velocity. This repairs
        // the local player's state if it began the spin while already overlapping another client entity.
        player.setRiptiding(false);
        player.setRiptiding(true);
        player.setVelocity(velocity);
    }

    private void updateRiptideViewerState(@NotNull List<Player> racers, @NotNull Set<UUID> activeRacers) {
        riptideViewerGraceTicks.keySet().removeIf(uuid -> !activeRacers.contains(uuid));
        for (Player racer : racers) {
            UUID uuid = racer.getUniqueId();
            if (racer.isRiptiding()) {
                riptideViewerGraceTicks.put(uuid, 2);
                continue;
            }
            Integer grace = riptideViewerGraceTicks.get(uuid);
            if (grace == null) continue;
            if (grace <= 1) riptideViewerGraceTicks.remove(uuid);
            else riptideViewerGraceTicks.put(uuid, grace - 1);
        }
    }

    private void updateDirectionalRacerView(@NotNull Player target, @NotNull Player viewer, boolean newlyNearby) {
        RacerView view = new RacerView(viewer.getUniqueId(), target.getUniqueId());
        boolean suppressForRiptide = riptideViewerGraceTicks.containsKey(viewer.getUniqueId());
        if (suppressForRiptide) {
            if (riptideHiddenViews.add(view)) {
                plugin.getGlowingEntities().unsetInvisibleGlowing(target, viewer);
                setRacerVisible(viewer, target, false);
            }
            return;
        }
        if (riptideHiddenViews.remove(view) || newlyNearby) {
            setRacerVisible(viewer, target, true);
            plugin.getGlowingEntities().setInvisibleGlowing(target, viewer);
        }
    }

    private void clearDirectionalRacerView(@NotNull Player target, @NotNull Player viewer) {
        RacerView view = new RacerView(viewer.getUniqueId(), target.getUniqueId());
        riptideHiddenViews.remove(view);
        plugin.getGlowingEntities().unsetInvisibleGlowing(target, viewer);
        setRacerVisible(viewer, target, false);
    }

    /** Delegates the directional decision to the UUID-backed global lifecycle manager. */
    private void setRacerVisible(@NotNull Player viewer, @NotNull Player target, boolean visible) {
        plugin.getVisibilityManager().setPlayerVisible(viewer.getUniqueId(), target.getUniqueId(), visible,
                visibilityOwner, visible ? "Ace Race 距离内玩家可见" : "Ace Race 距离或冲刺规则隐藏");
    }

    private @NotNull TextDisplay ensureRacerNameDisplay(@NotNull Player racer) {
        TextDisplay existing = racerNameDisplays.get(racer.getUniqueId());
        if (existing != null && existing.isValid()) return existing;

        ChampionshipTeam team = plugin.getTeamManager().getTeamByPlayer(racer.getUniqueId());
        TextDisplay display = racer.getWorld().spawn(racerNameDisplayLocation(racer), TextDisplay.class, spawned -> {
            spawned.text(net.kyori.adventure.text.Component.text(racer.getName(),
                    team == null ? net.kyori.adventure.text.format.NamedTextColor.WHITE : team.getTeam().color()));
            spawned.setBillboard(Display.Billboard.CENTER);
            spawned.setShadowed(true);
            spawned.setSeeThrough(false);
            spawned.setDefaultBackground(false);
            spawned.setBackgroundColor(org.bukkit.Color.fromARGB(0, 0, 0, 0));
            spawned.setAlignment(TextDisplay.TextAlignment.CENTER);
            spawned.setTeleportDuration(1);
            spawned.setInterpolationDuration(1);
            spawned.setVisibleByDefault(false);
            spawned.setPersistent(false);
            spawned.setInvulnerable(true);
            spawned.setGravity(false);
        });
        racerNameDisplays.put(racer.getUniqueId(), display);
        return display;
    }

    private @NotNull Location racerNameDisplayLocation(@NotNull Player racer) {
        return racer.getLocation().add(0D, racer.getBoundingBox().getHeight() + 0.35D, 0D);
    }

    private void showRacerNameDisplay(@NotNull Player target, @NotNull Player viewer) {
        viewer.showEntity(plugin, ensureRacerNameDisplay(target));
    }

    private void hideRacerNameDisplay(@NotNull Player target, @NotNull Player viewer) {
        TextDisplay display = racerNameDisplays.get(target.getUniqueId());
        if (display != null) viewer.hideEntity(plugin, display);
    }

    private void removeRacerNameDisplay(@NotNull UUID uuid) {
        TextDisplay display = racerNameDisplays.remove(uuid);
        if (display != null) display.remove();
    }

    private void removeAllRacerNameDisplays() {
        for (TextDisplay display : racerNameDisplays.values()) display.remove();
        racerNameDisplays.clear();
    }

    /**
     * Disables both server-side entity pushing and the client-predicted collision driven by scoreboard teams.
     * A separate temporary team is kept for each original colour so glowing outlines retain their team colour.
     */
    private void disableRacerCollisions(@NotNull Player player) {
        player.setCollidable(false);
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        Team current = scoreboard.getEntryTeam(player.getName());
        if (current != null && !current.getName().startsWith(COLLISION_TEAM_PREFIX))
            originalScoreboardTeams.putIfAbsent(player.getUniqueId(), current.getName());

        ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(player.getUniqueId());
        String colorName = championshipTeam == null ? "white" : championshipTeam.getColorName().toLowerCase();
        String collisionTeamName = COLLISION_TEAM_PREFIX + colorName;
        Team collisionTeam = scoreboard.getTeam(collisionTeamName);
        if (collisionTeam == null) collisionTeam = scoreboard.registerNewTeam(collisionTeamName);
        var collisionColor = Utils.toNamedTextColor(colorName);
        if (!collisionTeam.hasColor() || !collisionColor.equals(collisionTeam.color()))
            collisionTeam.color(collisionColor);
        if (collisionTeam.getOption(Team.Option.COLLISION_RULE) != Team.OptionStatus.NEVER)
            collisionTeam.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
        if (!collisionTeam.hasEntry(player.getName())) collisionTeam.addEntry(player.getName());
    }

    /** Restores the exact scoreboard team occupied before Ace Race and removes empty temporary teams. */
    private void restoreRacerCollisions(@NotNull Player player) {
        player.setCollidable(true);
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        String originalName = originalScoreboardTeams.remove(player.getUniqueId());
        Team original = originalName == null ? null : scoreboard.getTeam(originalName);
        if (original == null) {
            ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(player.getUniqueId());
            if (championshipTeam != null) original = championshipTeam.getTeam();
        }
        if (original != null) {
            original.addEntry(player.getName());
        } else {
            Team current = scoreboard.getEntryTeam(player.getName());
            if (current != null && current.getName().startsWith(COLLISION_TEAM_PREFIX))
                current.removeEntry(player.getName());
        }
        removeEmptyCollisionTeams(scoreboard);
    }

    private void removeEmptyCollisionTeams(@NotNull Scoreboard scoreboard) {
        for (Team team : new ArrayList<>(scoreboard.getTeams())) {
            if (team.getName().startsWith(COLLISION_TEAM_PREFIX) && team.getEntries().isEmpty()) team.unregister();
        }
    }

    @Override
    public void dispose() {
        disableMapEditPreview();
        stopRacerVisibilityUpdates();
        restoreAllRacerVisibility();
        super.dispose();
    }

    private record PlacementRange(int first, int last) {
    }

    private record RacerPair(@NotNull UUID first, @NotNull UUID second) {
        private static @NotNull RacerPair of(@NotNull UUID first, @NotNull UUID second) {
            return first.compareTo(second) <= 0 ? new RacerPair(first, second) : new RacerPair(second, first);
        }

        private boolean contains(@NotNull UUID uuid) {
            return first.equals(uuid) || second.equals(uuid);
        }

        private boolean bothIn(@NotNull Set<UUID> uuids) {
            return uuids.contains(first) && uuids.contains(second);
        }
    }

    /** Directional view state: {@code viewer} may temporarily hide {@code target} while riptiding. */
    private record RacerView(@NotNull UUID viewer, @NotNull UUID target) {
        private boolean contains(@NotNull UUID uuid) {
            return viewer.equals(uuid) || target.equals(uuid);
        }

        private boolean bothIn(@NotNull Set<UUID> uuids) {
            return uuids.contains(viewer) && uuids.contains(target);
        }
    }

    @Override
    public Location getSpectatorSpawnLocation() {
        return getGameConfig().getSpectatorSpawnPoint();
    }

    /** Keeps racers and spectators inside this course's region in the shared Ace Race world. */
    @Override
    public boolean notInArea(Location location) {
        return location == null || getGameConfig().getAreaPos1() == null
                || getGameConfig().getAreaPos2() == null || super.notInArea(location);
    }

    @Override
    public AceRaceConfig getGameConfig() {
        return (AceRaceConfig) gameConfig;
    }

    @Override
    public AceRaceHandler getGameHandler() {
        return (AceRaceHandler) gameHandler;
    }

    @Override
    public String getWorldName() {
        return gameConfig.getConfiguredWorld();
    }
}
