package ink.ziip.championshipscore.api.game.tntrun;

import io.papermc.paper.registry.keys.EnchantmentKeys;
import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.event.SingleGameEndEvent;
import ink.ziip.championshipscore.api.game.arena.ArenaPreparer;
import ink.ziip.championshipscore.api.game.instance.multiteam.BaseMultiTeamGameInstance;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.CCConfig;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.util.Enchants;
import ink.ziip.championshipscore.util.Utils;
import lombok.Getter;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.NumberConversions;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.io.File;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

public class TNTRunTeamArea extends BaseMultiTeamGameInstance {
    private static final int EVENT_WARNING_SECONDS = 10;
    private static final int TNT_RAIN_DURATION_SECONDS = 10;
    private static final int[] TNT_RAIN_START_TIMES = {120, 60, 20};
    @Getter
    private final Map<UUID, Location> playerSpawnLocations = new ConcurrentHashMap<>();
    @Getter
    private final List<UUID> deathPlayer = new CopyOnWriteArrayList<>();
    private final Map<Block, Integer> pendingBlockRemovals = new ConcurrentHashMap<>();
    @Getter
    private int timer;
    private BukkitTask startGameProgressTask;
    private BukkitTask handlePlayerMoveTask;
    private BukkitTask tntGeneratorTask;
    private int tntTimer;
    private volatile int terrainGeneration;

    public TNTRunTeamArea(ChampionshipsCore plugin, TNTRunConfig tntRunConfig, boolean firstTime, String areaName) {
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

    @Override
    protected Collection<Location> getStartPreloadLocations() {
        List<Location> locations = new ArrayList<>();
        List<String> configured = getGameConfig().getPlayerSpawnPoints();
        if (configured != null) {
            for (String raw : configured) {
                Location location = Utils.getLocation(raw);
                if (location != null) locations.add(location);
            }
        }
        return locations;
    }

    @Override
    public void resetArea() {
        terrainGeneration++;
        playerSpawnLocations.clear();
        deathPlayer.clear();
        pendingBlockRemovals.clear();

        startGameProgressTask = null;
        handlePlayerMoveTask = null;
        tntGeneratorTask = null;

        // Restore only this map's copies. Unloading the physical world would interrupt other maps that
        // deliberately occupy independent regions in the same TNTRun world.
        World world = Bukkit.getWorld(getWorldName());
        File schematic = new File(new File(new File(plugin.getDataFolder(), "tntrun/schematics"),
                getGameConfig().getConfigName()), "arena.schem");
        if (world == null || !schematic.isFile() || getGameConfig().getCopies() < 1) {
            if (canUseExclusiveLegacyReload()) {
                logGame(Level.WARNING, "重置", "旧版单地图缺少完整盖章元数据，回退为独占世界模板重载");
                loadPublishedMapOrDraft(World.Environment.NORMAL);
                return;
            }
            logGame(Level.SEVERE, "重置", "共享世界地图无法局部恢复：世界、arena.schem 或副本数量无效");
            setGameStageEnum(GameStageEnum.END);
            return;
        }
        try {
            ArenaPreparer.restoreCopies(plugin, world, schematic, getGameConfig().getCopyGrid(),
                    getGameConfig().getCopies());
            setGameStageEnum(GameStageEnum.WAITING);
        } catch (Exception exception) {
            logGame(Level.SEVERE, "重置", "局部恢复失败，地图保持禁用 | " + exception.getMessage());
            setGameStageEnum(GameStageEnum.END);
        }
    }

    private boolean canUseExclusiveLegacyReload() {
        File template = new File(new File(plugin.getDataFolder(), "maps"), getWorldName());
        ink.ziip.championshipscore.api.game.manager.BaseGameInstanceManager<?> manager =
                plugin.getGameManager().getTntRunManager();
        long mapsInWorld = manager.getRuntimeInstances().stream()
                .filter(instance -> getWorldName().equals(instance.getWorldName()))
                .map(instance -> instance.getGameConfig())
                .distinct()
                .count();
        return mapsInWorld <= 1 && getGameConfig().isPrepareReady() && template.isDirectory();
    }

    @Override
    public void resetGame() {
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
        List<String> configuredSpawns = getGameConfig().getPlayerSpawnPoints();
        if (configuredSpawns != null) {
            for (String configuredSpawn : configuredSpawns) {
                try {
                    Location location = Utils.getLocation(configuredSpawn);
                    if (location != null && location.getWorld() != null) validSpawns.add(location);
                } catch (RuntimeException ignored) {
                    // A malformed entry must not prevent the remaining entries or safe fallbacks from working.
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

        throw new IllegalStateException("TNTRun has no valid spectator spawn for map "
                + getGameConfig().getConfigName());
    }

    @Override
    public Location getAdminTeleportLocation() {
        List<String> configured = getGameConfig().getPlayerSpawnPoints();
        if (configured != null && !configured.isEmpty()) {
            Location copyZero = Utils.getLocation(configured.getFirst());
            if (copyZero != null && copyZero.getWorld() != null)
                return copyZero;
        }
        return getSpectatorSpawnLocation();
    }

    @Override
    public void startGamePreparation() {
        setGameStageEnum(GameStageEnum.PREPARATION);

        // Rule-introduction phase (if configured): gather players at the introduction spawn point and
        // broadcast the rule sections in chat over 45s, then run the normal preparation below.
        startGameIntroduction(this::startFormalPreparation);
    }

    /** Normal preparation: spawn assignment + countdown, runs after the rule-introduction phase. */
    private void startFormalPreparation() {

        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);

        List<Location> locations = new ArrayList<>();
        for (String stringLocation : getGameConfig().getPlayerSpawnPoints()) {
            locations.add(Utils.getLocation(stringLocation));
        }

        for (ChampionshipTeam championshipTeam : gameTeams) {
            Collections.shuffle(locations);

            Iterator<Location> locationI = locations.iterator();

            for (UUID uuid : championshipTeam.getMembers()) {
                if (!locationI.hasNext())
                    locationI = locations.iterator();

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

        announceGamePreparation(MessageConfig.TNT_RUN_START_PREPARATION,
                MessageConfig.TNT_RUN_START_PREPARATION_TITLE, MessageConfig.TNT_RUN_START_PREPARATION_SUBTITLE);

        startGameProgress();
    }

    public void startGameProgress() {
        int offlinePlayers = 0;
        for (UUID uuid : gamePlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) {
                deathPlayer.add(uuid);
                offlinePlayers++;
                logGame(Level.INFO, "玩家", "玩家=" + playerManager.getPlayerName(uuid) + " uuid=" + uuid
                        + " 状态=离线，计入淘汰");
            }
        }
        addPointsToAllSurvivePlayers(offlinePlayers * 4);

        startFinalCountdown(MessageConfig.TNT_RUN_START_PREPARATION_TITLE,
                MessageConfig.TNT_RUN_GAME_START_TITLE, MessageConfig.TNT_RUN_GAME_START_SUBTITLE,
                this::beginGameProgress);
    }

    private void beginGameProgress() {
        startGameProgressTask = startRemainingTimer(getGameConfig().getTimer(), seconds -> {
            timer = seconds;
            updateGameTimerBossBar(tntRunBossBarTitle(), timer, getGameConfig().getTimer());

            int tntRainWarning = tntRainWarningSeconds();
            if (tntRainWarning > 0) {
                sendActionBarToAllGamePlayers(MessageConfig.TNT_RUN_TNT_RAIN_COUNT_DOWN
                        .replace("%time%", String.valueOf(tntRainWarning)));
            }

            if (isTntRainStart(timer)) {
                sendActionBarToAllGamePlayers(MessageConfig.TNT_RUN_TNT_RAIN);

                tntTimer = 9;

                // Candidate selection stays off the game thread. Entity creation and tracking are
                // dispatched back below, matching the path proven under 64-player formal events.
                final int tntRainGeneration = terrainGeneration;
                tntGeneratorTask = scheduler.runTaskTimerAsynchronously(plugin, () -> {

                    int i = 0;
                    Iterator<String> locationIterator = getGameConfig().getPlayerSpawnPoints().iterator();

                    while (i < 12) {
                        if (!locationIterator.hasNext())
                            locationIterator = getGameConfig().getPlayerSpawnPoints().iterator();

                        Location location = Utils.getLocation(locationIterator.next());
                        Location tntLocation = location.clone();
                        tntLocation.add(ThreadLocalRandom.current().nextInt(-30, 30), 15, ThreadLocalRandom.current().nextInt(-30, 30));

                        while (notInArea(tntLocation)) {
                            tntLocation = location.clone();
                            tntLocation.add(ThreadLocalRandom.current().nextInt(-30, 30), 15, ThreadLocalRandom.current().nextInt(-30, 30));
                        }

                        final Location finalTntLocation = tntLocation;
                        scheduler.runTaskLater(plugin, () -> {
                            if (tntRainGeneration != terrainGeneration
                                    || getGameStageEnum() != GameStageEnum.PROGRESS) return;
                            World world = finalTntLocation.getWorld();
                            if (world != null) {
                                TNTPrimed tntPrimed = (TNTPrimed) world.spawnEntity(finalTntLocation, EntityType.TNT);
                                tntPrimed.setFuseTicks(200);
                                scheduler.runTaskTimer(plugin, (task) -> {
                                    if (!tntPrimed.isValid() || tntPrimed.getFuseTicks() <= 0) {
                                        task.cancel();
                                        return;
                                    }

                                    Location tntTraceLocation = tntPrimed.getLocation();
                                    if (getBlockUnderLocation(tntTraceLocation, 0.8) != null
                                            || notInArea(tntTraceLocation)) {
                                        tntPrimed.setFuseTicks(0);
                                    }
                                }, 0, 1L);
                            }
                        }, 0L);
                        i++;
                    }

                    if (tntTimer == 0) {
                        if (tntGeneratorTask != null)
                            tntGeneratorTask.cancel();
                    }

                    tntTimer--;
                }, 0, 20L);

            }
        }, this::endGame);

        // Intentional performance exception: this proven 64-player path keeps repeated foot-block
        // probing away from the game thread. Delayed world mutation remains on the server thread and
        // pendingBlockRemovals is concurrent so duplicate probes cannot create duplicate tasks.
        final List<UUID> gamePlayersCopy = new ArrayList<>(gamePlayers);
        handlePlayerMoveTask = scheduler.runTaskTimerAsynchronously(plugin, () -> gamePlayersCopy.forEach(uuid -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && !deathPlayer.contains(uuid)) {
                handlePlayerMove(player);
            }
        }), 0, 1L);
    }

    private String tntRunBossBarTitle() {
        int activeSeconds = activeTntRainSeconds();
        if (activeSeconds > 0) {
            return MessageConfig.TNT_RUN_BOSS_BAR_TNT_RAIN
                    .replace("%time%", String.valueOf(timer))
                    .replace("%rain-time%", String.valueOf(activeSeconds));
        }
        return MessageConfig.TNT_RUN_BOSS_BAR.replace("%time%", String.valueOf(timer));
    }

    private int tntRainWarningSeconds() {
        for (int start : TNT_RAIN_START_TIMES) {
            int until = timer - start;
            if (until >= 1 && until <= EVENT_WARNING_SECONDS)
                return until;
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
            if (remainingSeconds == start)
                return true;
        }
        return false;
    }

    void handlePlayerMove(@NotNull Player player) {
        destroyBlock(player.getLocation());
    }

    private void giveElytraToAllPlayers() {
        for (UUID uuid : gamePlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && !deathPlayer.contains(uuid)) {
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
        World world = location.getWorld();
        double x = location.getX();
        double y = location.getY();
        double z = location.getZ();
        if (world == null)
            return null;

        for (int i = 0; i <= 1; i++) {
            Block block1 = world.getBlockAt(
                    NumberConversions.floor(x + bias),
                    NumberConversions.floor(y - i),
                    NumberConversions.floor(z - bias)
            );
            Material block1Type = block1.getType();
            if (block1Type != Material.AIR && block1Type != Material.LIGHT)
                return block1;
            Block block2 = world.getBlockAt(
                    NumberConversions.floor(x - bias),
                    NumberConversions.floor(y - i),
                    NumberConversions.floor(z + bias)
            );
            Material block2Type = block2.getType();
            if (block2Type != Material.AIR && block2Type != Material.LIGHT)
                return block2;
            Block block3 = world.getBlockAt(
                    NumberConversions.floor(x + bias),
                    NumberConversions.floor(y - i),
                    NumberConversions.floor(z + bias)
            );
            Material block3Type = block3.getType();
            if (block3Type != Material.AIR && block3Type != Material.LIGHT)
                return block3;
            Block block4 = world.getBlockAt(
                    NumberConversions.floor(x - bias),
                    NumberConversions.floor(y - i),
                    NumberConversions.floor(z - bias)
            );
            Material block4Type = block4.getType();
            if (block4Type != Material.AIR && block4Type != Material.LIGHT)
                return block4;
        }

        return null;
    }

    private void destroyBlock(Location location) {
        World world = location.getWorld();
        if (world == null)
            return;

        Block block = getBlockUnderLocation(location, 0.3);

        final int generation = terrainGeneration;
        if (block != null && pendingBlockRemovals.putIfAbsent(block, generation) == null) {
            scheduler.runTaskLater(plugin, () -> {
                if (pendingBlockRemovals.remove(block, generation)
                        && generation == terrainGeneration && getGameStageEnum() == GameStageEnum.PROGRESS) {
                    world.playSound(location, Sound.BLOCK_SAND_BREAK, 3, 1);
                    block.setType(Material.AIR);
                    block.getRelative(BlockFace.DOWN).setType(Material.AIR);
                }
            }, 8);
        }
    }

    @Override
    public void endGame() {
        if (getGameStageEnum() == GameStageEnum.WAITING)
            return;

        if (startGameProgressTask != null)
            startGameProgressTask.cancel();
        if (handlePlayerMoveTask != null)
            handlePlayerMoveTask.cancel();
        if (tntGeneratorTask != null)
            tntGeneratorTask.cancel();

        if (isSettlementAllowed()) calculatePoints();

        setGameStageEnum(GameStageEnum.END);

        announceGameEnd(MessageConfig.TNT_RUN_GAME_END_TITLE, MessageConfig.TNT_RUN_GAME_END_SUBTITLE);

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
            sendMessageToAllGamePlayers(MessageConfig.TNT_RUN_FALL_INTO_VOID
                    .replace("%player%", Utils.formatPlayerName(player)));
        }
    }

    public synchronized boolean addDeathPlayer(UUID uuid) {
        if (deathPlayer.contains(uuid))
            return false;

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
        boolean preGameDeath = stage == GameStageEnum.PREPARATION || stage == GameStageEnum.COUNTDOWN;
        event.setKeepInventory(true);
        event.setKeepLevel(true);
        event.setDroppedExp(0);
        event.getDrops().clear();

        if (!preGameDeath && stage == GameStageEnum.PROGRESS) {
            addDeathPlayer(player);
        }

        scheduler.runTask(plugin, () -> {
            player.spigot().respawn();
            player.setFallDistance(0f);
            if (preGameDeath) {
                teleportPlayerToSpawnPoint(player);
                player.setGameMode(GameMode.ADVENTURE);
            } else if (stage == GameStageEnum.PROGRESS) {
                player.teleport(getSpectatorSpawnLocation());
                player.setGameMode(GameMode.SPECTATOR);
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
            ChampionshipsCore championshipsCore = ChampionshipsCore.getInstance();
            championshipsCore.getServer().getScheduler().runTask(championshipsCore, () -> {
                player.setGameMode(GameMode.SPECTATOR);
            });
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
            ChampionshipsCore championshipsCore = ChampionshipsCore.getInstance();
            championshipsCore.getServer().getScheduler().runTask(championshipsCore, () -> {
                player.setGameMode(GameMode.SPECTATOR);
            });
            return;
        }
        if (getGameStageEnum() == GameStageEnum.PREPARATION
                || getGameStageEnum() == GameStageEnum.COUNTDOWN) {
            teleportPlayerToSpawnPoint(player);
            player.setGameMode(GameMode.ADVENTURE);
            return;
        }
        player.teleport(CCConfig.LOBBY_LOCATION);
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
     * In-bounds means inside some copy's own box. With the prepare/template model each stamped copy is a
     * self-contained sub-arena, so the play area is the set of per-copy boxes (not one box spanning the
     * void gaps between them). Maps without per-copy boxes use their configured aggregate area boundary.
     */
    @Override
    public boolean notInArea(Location location) {
        List<BoundingBox> boxes = getGameConfig().getCopyBoxes();
        if (boxes.isEmpty()) return super.notInArea(location);
        if (location == null || location.getWorld() == null
                || !location.getWorld().getName().equals(getWorldName())) {
            return true;
        }
        Vector point = location.toVector();
        for (BoundingBox box : boxes) {
            if (box.contains(point)) return false;
        }
        return true;
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
