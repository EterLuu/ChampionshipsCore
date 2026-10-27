package ink.ziip.championshipscore.api.game.tntrun.runtime;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.event.SingleGameEndEvent;
import ink.ziip.championshipscore.api.game.arena.ArenaPreparer;
import ink.ziip.championshipscore.api.game.instance.multiteam.BaseMultiTeamGameInstance;
import ink.ziip.championshipscore.api.game.model.GameStageEnum;
import ink.ziip.championshipscore.api.game.model.GameTypeEnum;
import ink.ziip.championshipscore.api.game.tntrun.config.TNTRunConfig;
import ink.ziip.championshipscore.api.game.tntrun.geometry.TNTRunRainPattern;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.CCConfig;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.configuration.location.LocationConfig;
import ink.ziip.championshipscore.presentation.text.CoreMessages;
import ink.ziip.championshipscore.util.Enchants;

import io.papermc.paper.registry.keys.EnchantmentKeys;

import lombok.Getter;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

public class TNTRunTeamArea extends BaseMultiTeamGameInstance {
    private static final int EVENT_WARNING_SECONDS = 10;
    private static final int TNT_RAIN_DURATION_SECONDS = 10;
    private static final int[] TNT_RAIN_START_TIMES = {120, 60, 20};
    @Getter private final Map<UUID, Location> playerSpawnLocations = new ConcurrentHashMap<>();
    @Getter private final List<UUID> deathPlayer = new CopyOnWriteArrayList<>();
    @Getter private int timer;
    private BukkitTask startGameProgressTask;
    private TNTRunFloorTracker floorTracker;
    private BukkitTask tntGeneratorTask;
    private final Set<TNTPrimed> rainTnt = new HashSet<>();
    private BukkitTask rainTntTracker;
    private AtomicBoolean rainActive;
    private int terrainGeneration;

    public TNTRunTeamArea(
            ChampionshipsCore plugin,
            TNTRunConfig tntRunConfig,
            boolean firstTime,
            String areaName) {
        super(plugin, GameTypeEnum.TNTRun, new TNTRunHandler(plugin), tntRunConfig);

        getGameHandler().setTntRunTeamArea(this);
        tntRunConfig.setAreaName(areaName);

        if (firstTime) {
            getGameHandler().register();
            setGameStageEnum(GameStageEnum.WAITING);
        }
    }

    /** Registers this map against its already loaded shared world. */
    public void initializeInSharedWorld() {
        getGameHandler().register();
        setGameStageEnum(GameStageEnum.WAITING);
    }

    private List<String> selectedSpawnPoints() {
        List<String> configured = getGameConfig().getPlayerSpawnPoints();
        if (configured == null || configured.isEmpty()) return List.of();
        return getSelectedArenaIndices(configured.size()).stream().map(configured::get).toList();
    }

    @Override
    protected Collection<Location> getStartPreloadLocations() {
        List<Location> locations = new ArrayList<>();
        List<String> configured = selectedSpawnPoints();
        if (configured != null) {
            for (String raw : configured) {
                Location location = LocationConfig.readLocation(raw);
                if (location != null) locations.add(location);
            }
        }
        World world = Bukkit.getWorld(getWorldName());
        List<BoundingBox> boxes = getGameConfig().getCopyBoxes();
        if (world != null && !boxes.isEmpty()) {
            // The base preloader owns and releases these tickets, including its edge margin.
            for (int index : getSelectedArenaIndices(boxes.size())) {
                BoundingBox box = boxes.get(index);
                int minX = (int) Math.floor(box.getMinX()) >> 4;
                int maxX = (int) Math.floor(Math.nextDown(box.getMaxX())) >> 4;
                int minZ = (int) Math.floor(box.getMinZ()) >> 4;
                int maxZ = (int) Math.floor(Math.nextDown(box.getMaxZ())) >> 4;
                for (int x = minX; x <= maxX; x++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        locations.add(new Location(world, x * 16 + 8, box.getMinY(), z * 16 + 8));
                    }
                }
            }
        }
        return locations;
    }

    @Override
    public void resetArea() {
        cancelTerrainTasks();
        playerSpawnLocations.clear();
        deathPlayer.clear();

        startGameProgressTask = null;
        tntGeneratorTask = null;

        // Restore only this map's copies. Unloading the physical world would interrupt other maps
        // that
        // deliberately occupy independent regions in the same TNTRun world.
        World world = Bukkit.getWorld(getWorldName());
        File schematic =
                new File(
                        new File(
                                new File(plugin.getDataFolder(), "tntrun/schematics"),
                                getGameConfig().getConfigName()),
                        "arena.schem");
        if (world == null || !schematic.isFile() || getGameConfig().getCopies() < 1) {
            logGame(
                    Level.SEVERE,
                    MessageConfig.codeText("tnt-run-team-area.reset-area-1"),
                    MessageConfig.codeText("tnt-run-team-area.reset-area-2"));
            setGameStageEnum(GameStageEnum.END);
            return;
        }
        try {
            ArenaPreparer.restoreCopies(
                    plugin,
                    world,
                    schematic,
                    getGameConfig().getCopyGrid(),
                    getGameConfig().getCopies());
            setGameStageEnum(GameStageEnum.WAITING);
        } catch (Exception exception) {
            logGame(
                    Level.SEVERE,
                    MessageConfig.codeText("tnt-run-team-area.reset-area-1"),
                    MessageConfig.codeText(
                            "tnt-run-team-area.reset-area-3", exception.getMessage()));
            setGameStageEnum(GameStageEnum.END);
        }
    }

    @Override
    public void resetGame() {
        invalidateParticipantCallbacks();
        cancelIntroduction();
        cancelFinalCountdown();
        playerPoints.clear();
        resetBaseArea();
    }

    @Override
    public boolean freezeMovementDuringCountdown() {
        return false;
    }

    @Override
    public Location getSpectatorSpawnLocation() {
        List<Location> validSpawns = new ArrayList<>();
        List<String> configuredSpawns = selectedSpawnPoints();
        if (configuredSpawns != null) {
            for (String configuredSpawn : configuredSpawns) {
                try {
                    Location location = LocationConfig.readLocation(configuredSpawn);
                    if (location != null && location.getWorld() != null) validSpawns.add(location);
                } catch (RuntimeException ignored) {
                    // A malformed entry must not prevent the remaining entries or safe fallbacks
                    // from working.
                }
            }
        }
        if (!validSpawns.isEmpty())
            return validSpawns.get(ThreadLocalRandom.current().nextInt(validSpawns.size()));

        Location configuredSpectatorSpawn = getGameConfig().getSpectatorSpawnPoint();
        if (configuredSpectatorSpawn != null && configuredSpectatorSpawn.getWorld() != null)
            return configuredSpectatorSpawn;

        World arenaWorld = Bukkit.getWorld(getWorldName());
        if (arenaWorld != null) return arenaWorld.getSpawnLocation();

        if (CCConfig.LOBBY_LOCATION != null && CCConfig.LOBBY_LOCATION.getWorld() != null)
            return CCConfig.LOBBY_LOCATION;

        throw new IllegalStateException(
                "TNTRun has no valid spectator spawn for map " + getGameConfig().getConfigName());
    }

    @Override
    public Location getAdminTeleportLocation() {
        List<String> configured = getGameConfig().getPlayerSpawnPoints();
        if (configured != null && !configured.isEmpty()) {
            Location copyZero = LocationConfig.readLocation(configured.getFirst());
            if (copyZero != null && copyZero.getWorld() != null) return copyZero;
        }
        return getSpectatorSpawnLocation();
    }

    @Override
    public void startGamePreparation() {
        setGameStageEnum(GameStageEnum.PREPARATION);

        // Rule-introduction phase (if configured): gather players at the introduction spawn point
        // and
        // broadcast the configured rule sections, then run the normal preparation below.
        startGameIntroduction(this::startFormalPreparation);
    }

    /** Normal preparation: spawn assignment + countdown, runs after the rule-introduction phase. */
    private void startFormalPreparation() {

        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);

        List<Location> locations = new ArrayList<>();
        for (String stringLocation : selectedSpawnPoints()) {
            locations.add(LocationConfig.readLocation(stringLocation));
        }

        for (ChampionshipTeam championshipTeam : gameTeams) {
            Collections.shuffle(locations);

            Iterator<Location> locationI = locations.iterator();

            for (UUID uuid : championshipTeam.getMembers()) {
                if (!locationI.hasNext()) locationI = locations.iterator();

                playerSpawnLocations.put(uuid, locationI.next());
            }
        }

        for (UUID uuid : playerSpawnLocations.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.teleport(playerSpawnLocations.get(uuid));
            }
        }

        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);

        resetPlayerHealthFoodEffectLevelInventory();

        giveElytraToAllPlayers();

        announceGamePreparation(
                MessageConfig.TNT_RUN_START_PREPARATION,
                MessageConfig.TNT_RUN_START_PREPARATION_TITLE,
                MessageConfig.TNT_RUN_START_PREPARATION_SUBTITLE);

        startGameProgress();
    }

    public void startGameProgress() {
        int offlinePlayers = 0;
        for (UUID uuid : gamePlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) {
                deathPlayer.add(uuid);
                offlinePlayers++;
                logGame(
                        Level.INFO,
                        MessageConfig.codeText("tnt-run-team-area.start-game-progress-1"),
                        MessageConfig.codeText(
                                "tnt-run-team-area.start-game-progress-2",
                                playerManager.getPlayerName(uuid),
                                uuid));
            }
        }
        addPointsToAllSurvivePlayers(offlinePlayers * 4);

        startFinalCountdown(
                MessageConfig.TNT_RUN_START_PREPARATION_TITLE,
                MessageConfig.TNT_RUN_GAME_START_TITLE,
                MessageConfig.TNT_RUN_GAME_START_SUBTITLE,
                this::beginGameProgress);
    }

    private void beginGameProgress() {
        startGameProgressTask =
                startRemainingTimer(
                        getGameConfig().getTimer(),
                        seconds -> {
                            timer = seconds;
                            updateGameTimerBossBar(
                                    tntRunBossBarTitle(), timer, getGameConfig().getTimer());

                            int tntRainWarning = tntRainWarningSeconds();
                            if (tntRainWarning > 0) {
                                sendActionBarToAllGamePlayers(
                                        MessageConfig.TNT_RUN_TNT_RAIN_COUNT_DOWN.replace(
                                                "%time%", String.valueOf(tntRainWarning)));
                            }

                            if (isTntRainStart(timer)) {
                                sendActionBarToAllGamePlayers(MessageConfig.TNT_RUN_TNT_RAIN);

                                startTntRain();
                            }
                        },
                        this::endGame);

        if (floorTracker != null) floorTracker.close();
        long generation = captureGameGeneration();
        int terrain = terrainGeneration;
        final List<UUID> participants = List.copyOf(gamePlayers);
        floorTracker =
                new TNTRunFloorTracker(
                        plugin,
                        () ->
                                participants.stream()
                                        .map(Bukkit::getPlayer)
                                        .filter(Objects::nonNull)
                                        .toList(),
                        player ->
                                !deathPlayer.contains(player.getUniqueId())
                                        && isGameplayParticipant(player),
                        () ->
                                generation == captureGameGeneration()
                                        && terrain == terrainGeneration
                                        && !isDisposed()
                                        && getGameStageEnum() == GameStageEnum.PROGRESS);
        floorTracker.start();
    }

    private void startTntRain() {
        String worldName = getWorldName();
        List<TNTRunRainPattern.Point> origins =
                selectedSpawnPoints().stream()
                        .map(LocationConfig::readLocation)
                        .filter(
                                location ->
                                        location != null
                                                && location.getWorld() != null
                                                && worldName.equals(location.getWorld().getName()))
                        .map(
                                location ->
                                        new TNTRunRainPattern.Point(
                                                location.getX(), location.getY(), location.getZ()))
                        .toList();
        List<BoundingBox> boxes = getGameConfig().getCopyBoxes();
        List<TNTRunRainPattern.Bounds> bounds =
                getSelectedArenaIndices(boxes.size()).stream()
                        .map(boxes::get)
                        .map(
                                box ->
                                        new TNTRunRainPattern.Bounds(
                                                box.getMinX(),
                                                box.getMinY(),
                                                box.getMinZ(),
                                                box.getMaxX(),
                                                box.getMaxY(),
                                                box.getMaxZ()))
                        .toList();
        if (origins.isEmpty() || bounds.isEmpty()) return;
        if (rainActive != null) rainActive.set(false);
        if (tntGeneratorTask != null) tntGeneratorTask.cancel();
        AtomicBoolean active = new AtomicBoolean(true);
        rainActive = active;
        int generation = terrainGeneration;
        tntGeneratorTask =
                new BukkitRunnable() {
                    private int remainingWaves = TNT_RAIN_DURATION_SECONDS;

                    @Override
                    public synchronized void run() {
                        if (!active.get()) {
                            cancel();
                            return;
                        }
                        // Only coordinate values cross this boundary; entities and worlds stay on
                        // Paper's tick thread.
                        List<TNTRunRainPattern.Point> wave =
                                TNTRunRainPattern.select(
                                        origins, bounds, ThreadLocalRandom.current());
                        try {
                            scheduler.runTask(
                                    plugin,
                                    () -> {
                                        if (!active.get()
                                                || generation != terrainGeneration
                                                || isDisposed()
                                                || getGameStageEnum() != GameStageEnum.PROGRESS)
                                            return;
                                        World world = Bukkit.getWorld(worldName);
                                        if (world == null) return;
                                        for (TNTRunRainPattern.Point point : wave) {
                                            spawnRainTnt(
                                                    new Location(
                                                            world, point.x(), point.y(), point.z()),
                                                    generation);
                                        }
                                    });
                        } catch (IllegalPluginAccessException stopping) {
                            active.set(false);
                            cancel();
                            return;
                        }
                        if (--remainingWaves == 0) cancel();
                    }
                }.runTaskTimerAsynchronously(plugin, 0L, 20L);
    }

    private void spawnRainTnt(Location location, int generation) {
        World world = location.getWorld();
        if (world == null) return;
        TNTPrimed tnt = (TNTPrimed) world.spawnEntity(location, EntityType.TNT);
        tnt.setFuseTicks(200);
        rainTnt.add(tnt);
        if (rainTntTracker != null) return;
        rainTntTracker =
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (generation != terrainGeneration
                                || isDisposed()
                                || getGameStageEnum() != GameStageEnum.PROGRESS) {
                            cancel();
                            return;
                        }
                        Iterator<TNTPrimed> iterator = rainTnt.iterator();
                        while (iterator.hasNext()) {
                            TNTPrimed currentTnt = iterator.next();
                            if (!currentTnt.isValid()) {
                                iterator.remove();
                                continue;
                            }
                            Location current = currentTnt.getLocation();
                            if (currentTnt.getFuseTicks() > 0
                                    && (getBlockUnderLocation(current, 0.8) != null
                                            || notInArea(current))) {
                                currentTnt.setFuseTicks(0);
                            }
                        }
                        if (rainTnt.isEmpty()) {
                            rainTntTracker = null;
                            cancel();
                        }
                    }
                }.runTaskTimer(plugin, 0L, 1L);
    }

    private void cancelTerrainTasks() {
        terrainGeneration++;
        if (startGameProgressTask != null) startGameProgressTask.cancel();
        if (floorTracker != null) floorTracker.close();
        floorTracker = null;
        if (tntGeneratorTask != null) tntGeneratorTask.cancel();
        if (rainActive != null) rainActive.set(false);
        if (rainTntTracker != null) rainTntTracker.cancel();
        rainTntTracker = null;
        rainTnt.forEach(TNTPrimed::remove);
        rainTnt.clear();
    }

    @Override
    public void dispose() {
        cancelTerrainTasks();
        super.dispose();
    }

    private String tntRunBossBarTitle() {
        int activeSeconds = activeTntRainSeconds();
        if (activeSeconds > 0) {
            return MessageConfig.TNT_RUN_BOSS_BAR_TNT_RAIN
                    .replace("%time%", CoreMessages.bossBarTime(timer))
                    .replace("%rain-time%", CoreMessages.bossBarTime(activeSeconds));
        }
        return MessageConfig.TNT_RUN_BOSS_BAR.replace("%time%", CoreMessages.bossBarTime(timer));
    }

    private int tntRainWarningSeconds() {
        for (int start : TNT_RAIN_START_TIMES) {
            int until = timer - start;
            if (until >= 1 && until <= EVENT_WARNING_SECONDS) return until;
        }
        return 0;
    }

    private int activeTntRainSeconds() {
        for (int start : TNT_RAIN_START_TIMES) {
            if (timer <= start && timer > start - TNT_RAIN_DURATION_SECONDS)
                return timer - (start - TNT_RAIN_DURATION_SECONDS);
        }
        return 0;
    }

    private boolean isTntRainStart(int remainingSeconds) {
        for (int start : TNT_RAIN_START_TIMES) {
            if (remainingSeconds == start) return true;
        }
        return false;
    }

    private void giveElytraToAllPlayers() {
        for (UUID uuid : gamePlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && !deathPlayer.contains(uuid) && isGameplayParticipant(player)) {
                ItemStack elytra = new ItemStack(Material.ELYTRA);
                elytra.addEnchantment(Enchants.get(EnchantmentKeys.UNBREAKING), 1);

                ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(player);
                if (championshipTeam != null) {
                    player.getInventory().setChestplate(championshipTeam.getChestPlate());
                    player.getInventory().setBoots(championshipTeam.getBoots());
                }

                player.getInventory().addItem(elytra.clone());
            }
        }
    }

    public Block getBlockUnderLocation(Location location, double bias) {
        TNTRunBlockProbe.Hit hit = TNTRunBlockProbe.find(location, bias);
        return hit == null ? null : hit.block();
    }

    @Override
    public void endGame() {
        if (getGameStageEnum() == GameStageEnum.WAITING || getGameStageEnum() == GameStageEnum.END)
            return;

        cancelTerrainTasks();

        if (isSettlementAllowed()) calculatePoints();

        setGameStageEnum(GameStageEnum.END);

        announceGameEnd(
                MessageConfig.TNT_RUN_GAME_END_TITLE, MessageConfig.TNT_RUN_GAME_END_SUBTITLE);

        beginPostGameSettlement();
        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);

        resetPlayerHealthFoodEffectLevelInventory();

        publishGameEndEvent(new SingleGameEndEvent(this, gameTeams));
        finishPostGameAfterEndEvent();
    }

    protected void calculatePoints() {
        int survivedPlayerNum = 0;

        for (UUID uuid : gamePlayers) {
            if (!deathPlayer.contains(uuid)) {
                addPlayerPoints(uuid, 100);
                survivedPlayerNum++;
            }
        }

        if (survivedPlayerNum == 0) {
            if (!deathPlayer.isEmpty())
                addPlayerPoints(deathPlayer.get(deathPlayer.size() - 1), 100);
            if (deathPlayer.size() >= 2)
                addPlayerPoints(deathPlayer.get(deathPlayer.size() - 2), 70);
            if (deathPlayer.size() >= 3)
                addPlayerPoints(deathPlayer.get(deathPlayer.size() - 3), 30);
        } else if (survivedPlayerNum <= 3) {
            if (!deathPlayer.isEmpty())
                addPlayerPoints(deathPlayer.get(deathPlayer.size() - 1), 70);
            if (deathPlayer.size() >= 2)
                addPlayerPoints(deathPlayer.get(deathPlayer.size() - 2), 30);
        }

        sendMessageToAllGamePlayers(getTeamPointsRank());

        addPlayerPointsToDatabase();
    }

    public void addDeathPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        if (addDeathPlayer(uuid)) {
            sendMessageToAllGamePlayers(
                    MessageConfig.TNT_RUN_FALL_INTO_VOID.replace(
                            "%player%", CoreMessages.formatPlayerName(player)));
        }
    }

    public synchronized boolean addDeathPlayer(UUID uuid) {
        if (deathPlayer.contains(uuid)) return false;

        deathPlayer.add(uuid);
        addPointsToAllSurvivePlayers();
        return true;
    }

    private void addPointsToAllSurvivePlayers() {
        for (UUID uuid : gamePlayers) {
            if (!deathPlayer.contains(uuid)) {
                addPlayerPoints(uuid, 4);
            }
        }
    }

    private void addPointsToAllSurvivePlayers(int points) {
        for (UUID uuid : gamePlayers) {
            if (!deathPlayer.contains(uuid)) {
                addPlayerPoints(uuid, points);
            }
        }
    }

    @Override
    public void handlePlayerDeath(@NotNull PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (notAreaPlayer(player)) return;

        GameStageEnum stage = getGameStageEnum();
        boolean preGameDeath =
                stage == GameStageEnum.PREPARATION || stage == GameStageEnum.COUNTDOWN;
        event.setKeepInventory(true);
        event.setKeepLevel(true);
        event.setDroppedExp(0);
        event.getDrops().clear();

        if (!preGameDeath && stage == GameStageEnum.PROGRESS) {
            addDeathPlayer(player);
        }

        long generation = captureGameGeneration();
        scheduler.runTask(
                plugin,
                () -> {
                    if (!isCurrentParticipantConnection(generation, player)) return;
                    player.spigot().respawn();
                    player.setFallDistance(0f);
                    if (preGameDeath) {
                        teleportPlayerToSpawnPoint(player);
                        player.setGameMode(GameMode.ADVENTURE);
                    } else if (stage == GameStageEnum.PROGRESS) {
                        plugin.getGameManager()
                                .getSpectatorManager()
                                .enterParticipantSpectator(
                                        player, this, getSpectatorSpawnLocation());
                    }
                });
    }

    @Override
    public void handlePlayerQuit(@NotNull PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (notAreaPlayer(player)) {
            return;
        }

        if (getGameStageEnum() == GameStageEnum.PROGRESS) {
            addDeathPlayer(player);
            plugin.getGameManager()
                    .getSpectatorManager()
                    .enterParticipantSpectator(player, this, getSpectatorSpawnLocation());
            return;
        }
    }

    @Override
    public void handlePlayerJoin(@NotNull PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (notAreaPlayer(player)) {
            return;
        }

        if (getGameStageEnum() == GameStageEnum.PROGRESS) {
            addDeathPlayer(player);
            plugin.getGameManager()
                    .getSpectatorManager()
                    .enterParticipantSpectator(player, this, getSpectatorSpawnLocation());
            return;
        }
        if (getGameStageEnum() == GameStageEnum.PREPARATION
                || getGameStageEnum() == GameStageEnum.COUNTDOWN) {
            teleportPlayerToSpawnPoint(player);
            player.setGameMode(GameMode.ADVENTURE);
            return;
        }
        teleportToLobby(player);
        player.setGameMode(GameMode.ADVENTURE);
    }

    public int getSurvivedPlayerNums() {
        return gamePlayers.size() - deathPlayer.size();
    }

    public void teleportPlayerToSpawnPoint(Player player) {
        // During the rule-introduction phase everyone roams from the introduction spawn point.
        if (isIntroductionPhase()) {
            player.teleport(getPreparationTeleportLocation(getSpectatorSpawnLocation()));
            return;
        }
        Location location = playerSpawnLocations.get(player.getUniqueId());

        if (location != null) {
            player.teleport(location);
            return;
        }

        for (Location spawnLocations : playerSpawnLocations.values()) {
            player.teleport(spawnLocations);
            return;
        }
    }

    /**
     * In-bounds means inside some copy's own box. With the prepare/template model each stamped copy
     * is a self-contained sub-arena, so the play area is the set of per-copy boxes (not one box
     * spanning the void gaps between them).
     */
    @Override
    public boolean notInArea(Location location) {
        List<BoundingBox> boxes = getGameConfig().getCopyBoxes();
        if (boxes.isEmpty()
                || location == null
                || location.getWorld() == null
                || !location.getWorld().getName().equals(getWorldName())) {
            return true;
        }
        Vector point = location.toVector();
        for (int index : getSelectedArenaIndices(boxes.size())) {
            if (boxes.get(index).contains(point)) return false;
        }
        return true;
    }

    /**
     * Participant fall checks may extend below the schematic without changing its terrain bounds.
     */
    public boolean notInPlayerArea(Location location) {
        return location == null
                || location.getWorld() == null
                || !location.getWorld().getName().equals(getWorldName())
                || getGameConfig().getCopyBoxes().isEmpty()
                || !getGameConfig()
                        .isInsidePlayerBounds(
                                location.toVector(),
                                getSelectedArenaIndices(getGameConfig().getCopyBoxes().size()));
    }

    @Override
    public TNTRunConfig getGameConfig() {
        return (TNTRunConfig) gameConfig;
    }

    @Override
    public TNTRunHandler getGameHandler() {
        return (TNTRunHandler) gameHandler;
    }

    public String getWorldName() {
        return gameConfig.getConfiguredWorld();
    }
}
