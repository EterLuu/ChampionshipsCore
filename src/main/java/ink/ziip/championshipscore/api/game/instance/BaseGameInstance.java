package ink.ziip.championshipscore.api.game.instance;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseListener;
import ink.ziip.championshipscore.api.game.config.BaseGameConfig;
import ink.ziip.championshipscore.api.game.arena.ArenaChunkPreloader;
import ink.ziip.championshipscore.api.game.manager.BaseGameInstanceManager;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import ink.ziip.championshipscore.api.player.ChampionshipPlayer;
import ink.ziip.championshipscore.api.player.PlayerManager;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.CCConfig;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.util.Utils;
import ink.ziip.championshipscore.util.scheduler.FoliaScheduler;
import org.bukkit.*;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Item;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.logging.Level;

public abstract class BaseGameInstance {
    private static final String SPECTATOR_TIMER_BOSS_BAR = "spectator-game-timer";
    protected final Set<UUID> spectators = ConcurrentHashMap.newKeySet();
    protected final Map<UUID, Double> playerPoints = new ConcurrentHashMap<>();
    protected final ChampionshipsCore plugin;
    protected final FoliaScheduler scheduler;
    protected final GameInstanceHandler gameInstanceHandler;
    protected final PlayerManager playerManager;
    private final Map<String, BossBarState> bossBars = new ConcurrentHashMap<>();
    private final Set<ArenaChunkPreloader.ChunkTicket> startChunkTickets = ConcurrentHashMap.newKeySet();
    private volatile CompletableFuture<Void> startPreloadFuture = CompletableFuture.completedFuture(null);
    private volatile CompletableFuture<Void> coordinatedStartGate;
    private volatile CompletableFuture<?> worldTransition = CompletableFuture.completedFuture(null);

    /** Duration (seconds) of the optional rule-introduction phase preceding the normal preparation. */
    protected static final int INTRODUCTION_DURATION = 45;

    /** True while players are gathered at the introduction spawn point for the rules broadcast. */
    protected volatile boolean introductionPhase = false;
    protected volatile ScheduledTask introductionTask;
    private volatile boolean introductionEnabledForNextStart = true;

    /** Final five-second countdown, isolated from every game's live timer. */
    protected volatile ScheduledTask finalCountdownTask;

    private static final Note BIT_C4 = Note.natural(0, Note.Tone.C);
    private static final Note BIT_C5 = Note.natural(1, Note.Tone.C);

    protected BaseListener gameHandler;
    protected BaseGameConfig gameConfig;

    protected volatile GameStageEnum gameStageEnum;
    protected GameTypeEnum gameTypeEnum;

    public BaseGameInstance(ChampionshipsCore plugin, GameTypeEnum gameTypeEnum, BaseListener gameHandler,
                            BaseGameConfig gameConfig) {
        this.playerManager = plugin.getPlayerManager();

        this.gameStageEnum = GameStageEnum.END;
        this.plugin = plugin;
        this.scheduler = FoliaScheduler.region(plugin, this::getSpectatorSpawnLocation);
        this.gameTypeEnum = gameTypeEnum;

        this.gameHandler = gameHandler;
        this.gameConfig = gameConfig;

        gameInstanceHandler = new GameInstanceHandler(plugin, this);
        gameInstanceHandler.register();
    }

    public void resetGame() {
        releaseStartChunks();
        cancelIntroduction();
        cancelFinalCountdown();
        resetBaseArea();
        playerPoints.clear();
        clearBossBars();

        // Template-backed games begin an asynchronous unload/copy/reload in resetArea(). That
        // transition publishes WAITING itself only after the replacement world and its Location-valued
        // config are ready. Static-world games keep the already-completed transition and can wait now.
        if (worldTransition.isDone()) {
            setGameStageEnum(GameStageEnum.WAITING);
            logGame(Level.INFO, "流程", "场地已重置，等待下一场");
        } else {
            logGame(Level.INFO, "流程", "场地已重置，正在异步恢复地图");
        }
    }

    /** Permanently releases listeners and UI owned by this instance when its manager unloads it. */
    public void dispose() {
        releaseStartChunks();
        cancelIntroduction();
        cancelFinalCountdown();
        clearBossBars();
        getGameHandler().unRegister();
        gameInstanceHandler.unRegister();
    }

    /** Landing points that must be warm before preparation starts. Games with replicas override this. */
    protected Collection<Location> getStartPreloadLocations() {
        return List.of();
    }

    /** Starts preparation only after all landing chunks are loaded and ticketed. Must be called on main. */
    protected final void startGamePreparationAfterPreload() {
        releaseStartChunks();
        List<Location> locations = new ArrayList<>(getStartPreloadLocations());
        if (gameConfig.getIntroductionSpawnPoint() != null)
            locations.add(gameConfig.getIntroductionSpawnPoint());
        AtomicReference<Throwable> preloadError = new AtomicReference<>();
        CompletableFuture<Void> preload;
        if (locations.isEmpty()) {
            preload = CompletableFuture.completedFuture(null);
        } else {
            logGame(Level.INFO, "区块", "开始异步预热落地区域，目标点=" + locations.size());
            preload = ArenaChunkPreloader.preload(plugin, locations, 1, startChunkTickets)
                    .exceptionally(error -> {
                        preloadError.set(error);
                        return null;
                    });
        }
        startPreloadFuture = preload;
        CompletableFuture<Void> gate = coordinatedStartGate;
        CompletableFuture<Void> ready = gate == null ? preload : preload.thenCompose(unused -> gate);
        ready.whenComplete((unused, ignored) ->
                scheduler.runTask(() -> {
                    if (!plugin.isLoaded() || getGameStageEnum() != GameStageEnum.LOADING) {
                        releaseStartChunks();
                        return;
                    }
                    Throwable error = preloadError.get();
                    if (error != null)
                        logGame(Level.WARNING, "区块", "预热未完全成功，将使用已加载区块 | " + error.getMessage());
                    else if (!locations.isEmpty())
                        logGame(Level.INFO, "区块", "落地区域预热完成，区块票=" + startChunkTickets.size());
                    coordinatedStartGate = null;
                    startGamePreparation();
                }));
    }

    public final void coordinateStartWith(@NotNull CompletableFuture<Void> gate) {
        coordinatedStartGate = gate;
    }

    public final @NotNull CompletableFuture<Void> getStartPreloadFuture() {
        return startPreloadFuture;
    }

    protected final void releaseStartChunks() {
        ArenaChunkPreloader.release(plugin, startChunkTickets);
    }

    public void resetPlayerHealthFoodEffectLevelInventory() {
        setHealthForAllGamePlayers(20);
        setFoodLevelForAllGamePlayers(20);
        clearEffectsForAllGamePlayers();
        cleanInventoryForAllGamePlayers();
        changeLevelForAllGamePlayers(0);
    }

    public void addPlayerPoints(UUID uuid, double points) {
        playerPoints.merge(uuid, points, Double::sum);
        logGame(Level.INFO, "积分", "玩家=" + plugin.getPlayerManager().getPlayerName(uuid)
                + " uuid=" + uuid + " 变更=" + formatPointChange(points));
        ChampionshipPlayer championshipPlayer = plugin.getPlayerManager().getPlayer(uuid);
        if (championshipPlayer != null)
            championshipPlayer.sendActionBar("&e[+] " + points);
    }

    public void addPlayerPointsToAllTeamMembers(ChampionshipTeam championshipTeam, int points) {
        for (UUID uuid : championshipTeam.getMembers()) {
            playerPoints.merge(uuid, (double) points, Double::sum);
            logGame(Level.INFO, "积分", "玩家=" + plugin.getPlayerManager().getPlayerName(uuid)
                    + " uuid=" + uuid + " 变更=" + formatPointChange(points));
        }
    }

    protected void logGame(Level level, String event, String message) {
        String area = gameConfig == null || gameConfig.getAreaName() == null ? "-" : gameConfig.getAreaName();
        String stage = gameStageEnum == null ? "-" : gameStageEnum.name();
        plugin.getLogger().log(level, Utils.formatGameLog(gameTypeEnum, area, stage, event, message));
    }

    private String formatPointChange(double points) {
        return (points >= 0 ? "+" : "") + Utils.formatPoints(points);
    }

    public void addPlayerPointsToDatabase() {
        for (Map.Entry<UUID, Double> playerPointEntry : playerPoints.entrySet()) {
            if (playerPointEntry.getValue() != 0)
                plugin.getRankManager().addPlayerPoints(playerPointEntry.getKey(), null, gameTypeEnum, gameConfig.getAreaName(), playerPointEntry.getValue());
        }
        plugin.getRankManager().refreshAfterPendingPointWrites();
    }

    public int getTeamPoints(ChampionshipTeam championshipTeam) {
        int points = 0;
        for (UUID uuid : championshipTeam.getMembers()) {
            points += playerPoints.getOrDefault(uuid, 0d);
        }

        return points;
    }

    public String getPlayerPointsRank() {
        ArrayList<Map.Entry<UUID, Double>> list;
        list = new ArrayList<>(playerPoints.entrySet());
        list.sort(Map.Entry.comparingByValue());

        Collections.reverse(list);

        StringBuilder stringBuilder = new StringBuilder();

        stringBuilder.append(MessageConfig.GAME_BOARD_BAR
                        .replace("%game%", gameTypeEnum.toString()))
                .append("\n");

        int i = 1;
        for (Map.Entry<UUID, Double> entry : list) {
            String row = MessageConfig.RANK_PLAYER_BOARD_ROW
                    .replace("%player_rank%", String.valueOf(i))
                    .replace("%player%", Utils.formatPlayerName(entry.getKey()))
                    .replace("%player_point%", Utils.formatPoints(entry.getValue()));

            stringBuilder.append(row).append("\n");
            i++;
        }

        return stringBuilder.toString();
    }

    public GameStageEnum getGameStageEnum() {
        synchronized (this) {
            return this.gameStageEnum;
        }
    }

    public void setGameStageEnum(GameStageEnum gameStageEnum) {
        synchronized (this) {
            this.gameStageEnum = gameStageEnum;
        }
    }

    public void loadMap(World.Environment environment) {
        loadMapAsync(environment);
    }

    public synchronized CompletableFuture<Boolean> loadMapAsync(World.Environment environment) {
        if (!plugin.isLoaded())
            return CompletableFuture.completedFuture(false);
        if (!worldTransition.isDone())
            return worldTransition.thenApply(ignored -> getGameStageEnum() == GameStageEnum.WAITING);

        clearBossBars();
        teleportAllSpectators(getLobbyLocation());
        setGameStageEnum(GameStageEnum.END);
        logGame(Level.INFO, "世界", "开始加载 " + getWorldName());

        File target = plugin.getWorldManager().getWorldFolder(getWorldName());
        File source = new File(new File(plugin.getDataFolder(), "maps"), getWorldName());
        FoliaScheduler global = FoliaScheduler.global(plugin);

        CompletableFuture<Boolean> transition = global.runGlobalFuture(getGameHandler()::unRegister)
                .thenCompose(ignored -> plugin.getWorldManager().deleteWorldAsync(getWorldName(), true))
                .thenCompose(ignored -> global.supplyAsync(source::isDirectory))
                .thenCompose(templateExists -> {
                    if (!templateExists)
                        return CompletableFuture.failedFuture(
                                new IllegalStateException("地图模板不存在 " + source));
                    return global.runAsyncFuture(() -> {
                        if (!plugin.getWorldManager().copyWorldFiles(source, target))
                            throw new IllegalStateException("无法从地图模板复制 " + getWorldName());
                    });
                })
                .thenCompose(ignored -> plugin.getWorldManager().loadWorldAsync(
                        getWorldName(), environment, false))
                .thenCompose(loaded -> {
                    if (!loaded)
                        return CompletableFuture.failedFuture(
                                new IllegalStateException("Bukkit 无法加载 " + getWorldName()));
                    return global.runGlobalFuture(() -> {
                        getGameConfig().initializeConfiguration(plugin.getFolder());
                        getGameHandler().register();
                        setGameStageEnum(GameStageEnum.WAITING);
                        logGame(Level.INFO, "世界", "加载完成 " + getWorldName());
                        teleportAllSpectators(getSpectatorSpawnLocation());
                    }).thenApply(done -> true);
                });
        worldTransition = transition.whenComplete((loaded, throwable) -> {
            if (throwable != null)
                plugin.getLogger().log(Level.SEVERE, "Failed to load arena world " + getWorldName(), throwable);
        });
        return transition;
    }

    /**
     * Restores a published map template when one exists. New maps deliberately have no template until
     * prepare publishes their first revision, so reopening the server must keep their editable draft
     * world instead of deleting it and then failing a template copy.
     */
    public final CompletableFuture<Boolean> loadPublishedMapOrDraft(World.Environment environment) {
        getGameConfig().initializeConfiguration(plugin.getFolder());
        File template = new File(new File(plugin.getDataFolder(), "maps"), getWorldName());
        if (getGameConfig().isPrepareReady() && template.isDirectory()) {
            return loadMapAsync(environment);
        }

        if (getGameConfig().isPrepareReady()) {
            getGameConfig().beginPrepareDraft();
            logGame(Level.SEVERE, "世界", "已发布地图缺少模板，已降为草稿并禁止开赛：" + template.getPath());
        }
        return loadDraftWorldAsync(environment);
    }

    private synchronized CompletableFuture<Boolean> loadDraftWorldAsync(World.Environment environment) {
        if (!worldTransition.isDone())
            return worldTransition.thenApply(ignored -> getGameStageEnum() == GameStageEnum.WAITING);
        clearBossBars();
        teleportAllSpectators(getLobbyLocation());
        setGameStageEnum(GameStageEnum.END);
        logGame(Level.INFO, "世界", "加载 prepare 草稿 " + getWorldName());
        FoliaScheduler global = FoliaScheduler.global(plugin);
        CompletableFuture<Boolean> transition = global.runGlobalFuture(getGameHandler()::unRegister)
                .thenCompose(ignored -> plugin.getWorldManager().loadWorldAsync(getWorldName(), environment, false))
                .thenCompose(loaded -> {
                    if (!loaded)
                        return CompletableFuture.failedFuture(
                                new IllegalStateException("草稿世界加载失败：" + getWorldName()));
                    return global.runGlobalFuture(() -> {
                        // Location-valued config entries may have been parsed before this custom world
                        // existed. Re-read them now so every cached Location references the live world.
                        getGameConfig().initializeConfiguration(plugin.getFolder());
                        getGameHandler().register();
                        setGameStageEnum(GameStageEnum.WAITING);
                        logGame(Level.INFO, "世界", "草稿世界已就绪，等待 prepare 发布");
                    }).thenApply(done -> true);
                });
        worldTransition = transition.whenComplete((loaded, throwable) -> {
            if (throwable != null)
                plugin.getLogger().log(Level.SEVERE, "Failed to load draft world " + getWorldName(), throwable);
        });
        return transition;
    }

    /** True only when every instance backed by this same world is idle and the map can be reloaded safely. */
    public boolean canSaveMap() {
        if (getGameStageEnum() != GameStageEnum.WAITING)
            return false;
        BaseGameInstanceManager<? extends BaseGameInstance> manager =
                plugin.getGameManager().getAreaManager(gameTypeEnum);
        if (manager == null)
            return true;
        return manager.getRuntimeInstances().stream()
                .filter(instance -> getWorldName().equals(instance.getWorldName()))
                .allMatch(instance -> instance.getGameStageEnum() == GameStageEnum.WAITING);
    }

    public boolean saveMap(World.Environment environment) {
        if (!canSaveMap())
            return false;
        saveMapAsync(environment);
        return true;
    }

    public synchronized CompletableFuture<Boolean> saveMapAsync(World.Environment environment) {
        if (!canSaveMap()) {
            logGame(Level.WARNING, "世界", "保存被拒绝：同一地图仍有运行中的游戏实例");
            return CompletableFuture.completedFuture(false);
        }
        if (!worldTransition.isDone())
            return CompletableFuture.completedFuture(false);

        setGameStageEnum(GameStageEnum.END);
        logGame(Level.INFO, "世界", "开始保存 " + getWorldName());
        teleportAllSpectators(getLobbyLocation());

        File dataDirectory = new File(plugin.getDataFolder(), "maps");
        File target = new File(dataDirectory, getWorldName());
        File source = plugin.getWorldManager().getWorldFolder(getWorldName());
        String transaction = ".prepare-" + getWorldName() + "-" + UUID.randomUUID();
        File staging = new File(dataDirectory, transaction + "-staging");
        File backup = new File(dataDirectory, transaction + "-previous");
        FoliaScheduler global = FoliaScheduler.global(plugin);

        CompletableFuture<Boolean> transition = global.runGlobalFuture(getGameHandler()::unRegister)
                .thenCompose(ignored -> plugin.getWorldManager().unloadWorldAsync(getWorldName(), true))
                .thenCompose(unloaded -> {
                    if (!unloaded)
                        return CompletableFuture.failedFuture(
                                new IllegalStateException("世界未加载或无法卸载 " + getWorldName()));
                    return global.runAsyncFuture(() -> publishTemplate(source, staging, target, backup));
                })
                .thenCompose(ignored -> global.runAsyncFuture(() -> {
                    plugin.getWorldManager().deleteWorldFiles(source);
                    if (!plugin.getWorldManager().copyWorldFiles(target, source))
                        throw new IllegalStateException("无法恢复运行世界文件 " + getWorldName());
                }))
                .thenCompose(ignored -> plugin.getWorldManager().loadWorldAsync(
                        getWorldName(), environment, false))
                .thenCompose(loaded -> {
                    if (!loaded)
                        return CompletableFuture.failedFuture(
                                new IllegalStateException("新 revision 无法加载 " + getWorldName()));
                    return global.runGlobalFuture(() -> {
                        getGameConfig().initializeConfiguration(plugin.getFolder());
                        getGameHandler().register();
                        setGameStageEnum(GameStageEnum.WAITING);
                        teleportAllSpectators(getSpectatorSpawnLocation());
                        logGame(Level.INFO, "世界", "发布完成 " + getWorldName());
                    }).thenApply(done -> true);
                })
                .thenCompose(saved -> global.runAsyncFuture(() ->
                        plugin.getWorldManager().deleteWorldFiles(backup)).thenApply(done -> saved))
                .exceptionallyCompose(throwable -> rollbackPublishedTemplate(
                        environment, target, backup, staging, throwable));
        worldTransition = transition;
        return transition;
    }

    private void publishTemplate(File source, File staging, File target, File backup) {
        plugin.getWorldManager().deleteWorldFiles(staging);
        plugin.getWorldManager().deleteWorldFiles(backup);
        if (!plugin.getWorldManager().copyWorldFiles(source, staging))
            throw new IllegalStateException("无法写入暂存模板 " + getWorldName());
        try {
            if (target.exists())
                java.nio.file.Files.move(target.toPath(), backup.toPath());
            java.nio.file.Files.move(staging.toPath(), target.toPath());
        } catch (Exception exception) {
            try {
                if (!target.exists() && backup.exists())
                    java.nio.file.Files.move(backup.toPath(), target.toPath());
            } catch (Exception rollback) {
                exception.addSuppressed(rollback);
            }
            throw new IllegalStateException("模板切换失败 " + getWorldName(), exception);
        }
    }

    private CompletableFuture<Boolean> rollbackPublishedTemplate(
            World.Environment environment, File target, File backup, File staging, Throwable failure) {
        logGame(Level.SEVERE, "世界", "发布失败，开始回滚 | " + failure.getMessage());
        FoliaScheduler global = FoliaScheduler.global(plugin);
        return plugin.getWorldManager().deleteWorldAsync(getWorldName(), true)
                .thenCompose(ignored -> global.runAsyncFuture(() -> {
                    plugin.getWorldManager().deleteWorldFiles(staging);
                    if (backup.exists()) {
                        plugin.getWorldManager().deleteWorldFiles(target);
                        try {
                            java.nio.file.Files.move(backup.toPath(), target.toPath());
                        } catch (Exception exception) {
                            throw new IllegalStateException("发布回滚失败 " + getWorldName(), exception);
                        }
                    }
                    File runtime = plugin.getWorldManager().getWorldFolder(getWorldName());
                    if (target.exists() && !plugin.getWorldManager().copyWorldFiles(target, runtime))
                        throw new IllegalStateException("无法恢复编辑世界 " + getWorldName());
                }))
                .thenCompose(ignored -> plugin.getWorldManager().loadWorldAsync(
                        getWorldName(), environment, false))
                .thenCompose(loaded -> FoliaScheduler.global(plugin).runGlobalFuture(() -> {
                    if (loaded) {
                        getGameHandler().register();
                        setGameStageEnum(GameStageEnum.WAITING);
                    }
                }))
                .handle((ignored, rollbackFailure) -> {
                    if (rollbackFailure != null)
                        plugin.getLogger().log(Level.SEVERE,
                                "Failed to roll back map publication " + getWorldName(), rollbackFailure);
                    return false;
                });
    }

    public void createBossBar(String name, String title, BarColor color, BarStyle style) {
        removeBossBar(name);
        CompletableFuture<BossBar> created = FoliaScheduler.global(plugin).supplyGlobal(() ->
                Bukkit.createBossBar(Utils.translateColorCodes(title), color, style));
        BossBarState state = new BossBarState(created);
        bossBars.put(name, state);
        created.exceptionally(throwable -> {
            bossBars.remove(name, state);
            plugin.getLogger().log(Level.SEVERE, "Failed to create BossBar " + name, throwable);
            return null;
        });
    }

    public void removeBossBar(String name) {
        BossBarState state = bossBars.remove(name);
        if (state != null)
            state.execute(BossBar::removeAll);
    }

    /** Removes every area-owned bar and all of its viewers. Safe to call repeatedly. */
    public void clearBossBars() {
        for (String name : new ArrayList<>(bossBars.keySet()))
            removeBossBar(name);
    }

    private void removePlayerFromBossBars(Player player) {
        for (BossBarState state : bossBars.values())
            state.execute(bossBar -> bossBar.removePlayer(player));
    }

    /** Updates the shared live timer bar and synchronizes it to current in-game and external spectators. */
    protected void updateSpectatorTimerBossBar(String title, int remainingSeconds, int durationSeconds) {
        double progress = durationSeconds <= 0 ? 0D : remainingSeconds / (double) durationSeconds;
        updateSpectatorTimerBossBar(title, progress);
    }

    /** Variant for non-countdown clocks, where the caller supplies the semantic progress directly. */
    protected void updateSpectatorTimerBossBar(String title, double progress) {
        if (!bossBars.containsKey(SPECTATOR_TIMER_BOSS_BAR))
            createBossBar(SPECTATOR_TIMER_BOSS_BAR, title, BarColor.YELLOW, BarStyle.SOLID);

        Set<Player> viewers = new LinkedHashSet<>(getOnlineSpectators());
        Set<Player> participantCandidates = new LinkedHashSet<>(getOnlineParticipantSpectators());
        for (Player candidate : participantCandidates) {
            scheduler.runEntity(candidate, () -> {
                if (candidate.getGameMode() == GameMode.SPECTATOR)
                    syncSpectatorBossBarViewer(candidate);
                else
                    removeBossBarPlayer(SPECTATOR_TIMER_BOSS_BAR, candidate);
            });
        }
        withBossBar(SPECTATOR_TIMER_BOSS_BAR, bossBar -> {
            bossBar.setTitle(Utils.translateColorCodes(title));
            bossBar.setProgress(Math.max(0D, Math.min(1D, progress)));
            for (Player current : new ArrayList<>(bossBar.getPlayers())) {
                if (!viewers.contains(current) && !participantCandidates.contains(current))
                    bossBar.removePlayer(current);
            }
            viewers.forEach(bossBar::addPlayer);
        });
        viewers.forEach(this::removeViewerFromGameBossBars);
    }

    public void setBossBar(String name, String title) {
        withBossBar(name, bossBar -> bossBar.setTitle(Utils.translateColorCodes(title)));
    }

    public void addBossBarPlayer(String name, Player player) {
        if (player == null)
            return;

        withBossBar(name, bossBar -> bossBar.addPlayer(player));
    }

    public void removeBossBarPlayer(String name, Player player) {
        if (player != null)
            withBossBar(name, bossBar -> bossBar.removePlayer(player));
    }

    public void setBossBarProgress(String name, double progress) {
        withBossBar(name, bossBar -> bossBar.setProgress(Math.max(0D, Math.min(1D, progress))));
    }

    private void syncSpectatorBossBarViewer(Player viewer) {
        addBossBarPlayer(SPECTATOR_TIMER_BOSS_BAR, viewer);
        removeViewerFromGameBossBars(viewer);
    }

    private void removeViewerFromGameBossBars(Player viewer) {
        for (Map.Entry<String, BossBarState> entry : bossBars.entrySet()) {
            if (!SPECTATOR_TIMER_BOSS_BAR.equals(entry.getKey()))
                entry.getValue().execute(bossBar -> bossBar.removePlayer(viewer));
        }
    }

    private void withBossBar(String name, Consumer<BossBar> operation) {
        BossBarState state = bossBars.get(name);
        if (state == null) {
            logGame(Level.WARNING, "BossBar", "未找到 " + name);
            return;
        }
        state.execute(operation);
    }

    /** Serializes shared BossBar mutations on the global region for Paper and Folia. */
    private final class BossBarState {
        private final CompletableFuture<BossBar> bossBar;
        private CompletableFuture<Void> tail = CompletableFuture.completedFuture(null);

        private BossBarState(CompletableFuture<BossBar> bossBar) {
            this.bossBar = bossBar;
        }

        private synchronized void execute(Consumer<BossBar> operation) {
            if (!plugin.isEnabled()) {
                // onDisable is a global-region callback, but schedulers reject new work once the
                // plugin flag is down. Remove an already-created global BossBar directly.
                BossBar value = bossBar.isCompletedExceptionally() ? null : bossBar.getNow(null);
                if (value != null) operation.accept(value);
                return;
            }
            tail = tail.handle((ignored, previousFailure) -> null)
                    .thenCompose(ignored -> bossBar.thenCompose(value ->
                            FoliaScheduler.global(plugin).runGlobalFuture(() -> operation.accept(value))));
            tail.exceptionally(throwable -> {
                plugin.getLogger().log(Level.SEVERE, "Failed to update BossBar", throwable);
                return null;
            });
        }
    }

    public Location getLobbyLocation() {
        return CCConfig.LOBBY_LOCATION;
    }

    public boolean isIntroductionPhase() {
        return introductionPhase;
    }

    /** Whether participants should be held in place during the final five-second countdown. */
    public boolean freezeMovementDuringCountdown() {
        return true;
    }

    public void setIntroductionEnabledForNextStart(boolean enabled) {
        introductionEnabledForNextStart = enabled;
    }

    /**
     * Runs the optional rule-introduction phase. When the area config provides an introduction spawn
     * point and at least one rule section, every player is teleported there (still in PREPARATION
     * stage, free to walk around inside the area) while the rule sections are broadcast one at a time
     * in chat over {@link #INTRODUCTION_DURATION} seconds; afterwards {@code onComplete} (the normal
     * preparation: spawn assignment + countdown) runs. Without such config the introduction is skipped
     * and {@code onComplete} runs immediately.
     */
    protected void startGameIntroduction(@NotNull Runnable onComplete) {
        boolean showIntroduction = introductionEnabledForNextStart;
        introductionEnabledForNextStart = true;
        if (!showIntroduction) {
            onComplete.run();
            return;
        }

        List<List<String>> rules = getIntroductionRules();
        Location introductionSpawnPoint = gameConfig.getIntroductionSpawnPoint();
        if (rules == null || rules.isEmpty() || introductionSpawnPoint == null) {
            onComplete.run();
            return;
        }

        introductionPhase = true;
        teleportAllPlayers(introductionSpawnPoint);
        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);

        final int sectionCount = rules.size();
        // First broadcast at t=10s (players get a moment to look around after teleporting in), then one
        // section every 10s (with 3 sections: t=10s/20s/30s, the last one stays up for 15s). Section
        // counts that wouldn't fit fall back to a tighter even distribution.
        final int interval = Math.max(1, Math.min(10, INTRODUCTION_DURATION / (sectionCount + 1)));
        final int[] remain = {INTRODUCTION_DURATION};

        introductionTask = scheduler.runTaskTimer(() -> {
            int elapsed = INTRODUCTION_DURATION - remain[0];
            if (remain[0] > 0 && elapsed > 0 && elapsed % interval == 0) {
                int section = elapsed / interval - 1;
                if (section < sectionCount)
                    broadcastRuleSection(rules.get(section));
            }

            showPreparationCountdown(remain[0]);

            if (remain[0] == 0) {
                cancelIntroduction();
                // The game may have been ended during the introduction (stop command / force end).
                if (getGameStageEnum() == GameStageEnum.PREPARATION)
                    onComplete.run();
                return;
            }

            remain[0]--;
        }, 0, 20L);
    }

    /** Variant-aware games override this without coupling the base lifecycle to a concrete config model. */
    protected List<List<String>> getIntroductionRules() {
        return gameConfig.getRules();
    }

    private void broadcastRuleSection(@NotNull List<String> lines) {
        for (String line : lines)
            sendMessageToAllGamePlayers(Utils.translateColorCodes(line));
        playSoundToAllGamePlayers(Sound.BLOCK_NOTE_BLOCK_PLING, 1, 1F);
    }

    /** One durable chat line at preparation; later phase changes stay out of chat. */
    protected void announceGamePreparation(String message, String title, String subtitle) {
        sendMessageToAllGamePlayers(message);
        sendTitleToAllGamePlayers(title, subtitle);
        logGame(Level.INFO, "流程", "进入场地准备");
    }

    protected void showPreparationCountdown(int seconds) {
        sendActionBarToAllGamePlayers(MessageConfig.GAME_PREPARATION_COUNT_DOWN
                .replace("%game%", gameTypeEnum.toString())
                .replace("%time%", String.valueOf(Math.max(0, seconds))));
    }

    protected void announceGameStartSoon(String title, String subtitle) {
        sendActionBarToAllGamePlayers(subtitle);
        sendTitleToAllGamePlayers(title, subtitle);
    }

    /** Runs the default five-second final countdown. */
    protected void startFinalCountdown(String gameTitle, String startTitle, String startSubtitle,
                                       @NotNull Runnable onStart) {
        startFinalCountdown(5, gameTitle, startTitle, startSubtitle, onStart);
    }

    /**
     * Runs the authoritative final countdown. The supplied callback starts live game systems at T0;
     * the stage transition, start title and high cue all happen in that same server tick.
     */
    protected void startFinalCountdown(int countdownSeconds, String gameTitle, String startTitle,
                                       String startSubtitle, @NotNull Runnable onStart) {
        cancelFinalCountdown();
        setGameStageEnum(GameStageEnum.COUNTDOWN);
        int duration = Math.max(0, countdownSeconds);
        logGame(Level.INFO, "流程", "开始 " + duration + " 秒开赛倒计时");
        final int[] remaining = {duration};

        finalCountdownTask = scheduler.runTaskTimer(() -> {
            int seconds = remaining[0];
            if (seconds > 0) {
                String title = MessageConfig.GAME_START_COUNT_DOWN_TITLE
                        .replace("%time%", String.valueOf(seconds));
                String subtitle = getFinalCountdownSubtitle(gameTitle);
                String actionBar = MessageConfig.GAME_START_COUNT_DOWN_ACTION_BAR
                        .replace("%game%", gameTypeEnum.toString())
                        .replace("%time%", String.valueOf(seconds));
                sendTitleToAllGamePlayers(title, subtitle);
                sendActionBarToAllGamePlayers(actionBar);
                changeLevelForAllGamePlayers(seconds);
                playCountdownBit(BIT_C4);
                remaining[0]--;
                return;
            }

            if (finalCountdownTask != null)
                finalCountdownTask.cancel();
            finalCountdownTask = null;
            if (getGameStageEnum() != GameStageEnum.COUNTDOWN)
                return;

            changeLevelForAllGamePlayers(0);
            setGameStageEnum(GameStageEnum.PROGRESS);
            onStart.run();
            if (getGameStageEnum() == GameStageEnum.PROGRESS) {
                announceGameStart(startTitle, startSubtitle);
                playCountdownBit(BIT_C5);
            }
        }, 0L, 20L);
    }

    /**
     * A live remaining-time clock with an exact endpoint: duration is rendered at T0, the first decrement
     * occurs at T0+20 ticks, and zero/onEnd occur at T0+duration*20 ticks.
     */
    protected ScheduledTask startRemainingTimer(int durationSeconds, @NotNull IntConsumer onTick,
                                                @NotNull Runnable onEnd) {
        final int[] remaining = {Math.max(0, durationSeconds)};
        onTick.accept(remaining[0]);
        if (remaining[0] == 0) {
            onEnd.run();
            return null;
        }

        ScheduledTask[] taskHolder = new ScheduledTask[1];
        taskHolder[0] = scheduler.runTaskTimer(() -> {
            remaining[0]--;
            onTick.accept(remaining[0]);
            if (remaining[0] == 0) {
                taskHolder[0].cancel();
                onEnd.run();
            }
        }, 20L, 20L);
        return taskHolder[0];
    }

    public void cancelFinalCountdown() {
        if (finalCountdownTask != null) {
            finalCountdownTask.cancel();
            finalCountdownTask = null;
        }
    }

    protected String getFinalCountdownSubtitle(String gameTitle) {
        return MessageConfig.GAME_START_COUNT_DOWN_SUBTITLE.replace("%game%", gameTitle);
    }

    private void playCountdownBit(Note note) {
        playNoteToAllGamePlayers(Instrument.BIT, note);
        for (Player spectator : getOnlineSpectators()) {
            scheduler.runEntity(spectator,
                    () -> spectator.playNote(spectator.getLocation(), Instrument.BIT, note));
        }
    }

    protected void announceGameStart(String title, String subtitle) {
        sendActionBarToAllGamePlayers(MessageConfig.GAME_START_ACTION_BAR
                .replace("%game%", gameTypeEnum.toString()));
        sendTitleToAllGamePlayers(title, subtitle);
        logGame(Level.INFO, "流程", "游戏开始");
    }

    protected void announceGameEnd(String title, String subtitle) {
        clearBossBars();
        sendActionBarToAllGamePlayers(MessageConfig.GAME_END_ACTION_BAR
                .replace("%game%", gameTypeEnum.toString()));
        sendTitleToAllGamePlayers(title, subtitle);
        logGame(Level.INFO, "流程", "游戏结束，开始结算");
    }

    /** Cancels a running rule-introduction phase (task + flag); safe to call at any time. */
    public void cancelIntroduction() {
        if (introductionTask != null) {
            introductionTask.cancel();
            introductionTask = null;
        }
        introductionPhase = false;
    }

    /**
     * Where a player (re)joining or being pulled back during PREPARATION should land: the introduction
     * spawn point while the introduction phase runs, otherwise the given normal-preparation fallback.
     */
    public Location getPreparationTeleportLocation(@NotNull Location fallback) {
        Location introductionSpawnPoint = gameConfig.getIntroductionSpawnPoint();
        if (introductionPhase && introductionSpawnPoint != null)
            return introductionSpawnPoint;
        return fallback;
    }

    public boolean isSpectator(@NotNull Player player) {
        return spectators.contains(player.getUniqueId());
    }

    public void handleSpectatorDeath(@NotNull PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (isSpectator(player)) {
            event.setDroppedExp(0);
            event.getDrops().clear();
            scheduler.runEntity(player, () -> {
                event.getEntity().spigot().respawn();
                removeSpectator(player);
            });
        }
    }

    public void handleSpectatorJoin(@NotNull PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (isSpectator(player)) {
            player.teleportAsync(getSpectatorSpawnLocation()).thenAccept(success -> {
                if (success)
                    scheduler.runEntity(player, () -> applySpectatorGameMode(player));
            });
        }
    }

    public void teleportAllSpectators(@NotNull Location location) {
        for (Player player : getOnlineSpectators()) {
            player.teleportAsync(location).thenAccept(success -> {
                if (success)
                    scheduler.runEntity(player, () -> applySpectatorGameMode(player));
            });
        }
    }

    public void addSpectator(@NotNull Player player) {
        spectators.add(player.getUniqueId());
        player.teleportAsync(getSpectatorSpawnLocation()).thenAccept(success -> {
            if (success)
                scheduler.runEntity(player, () -> applySpectatorGameMode(player));
        });
    }

    /** Applies the mode used by external spectators of this game. Most games use vanilla spectator mode. */
    protected void applySpectatorGameMode(@NotNull Player player) {
        player.setGameMode(GameMode.SPECTATOR);
    }

    /** Restores any per-game spectator state before the player leaves this game. */
    protected void clearSpectatorGameMode(@NotNull Player player) {
    }

    public void removeAllSpectator() {
        for (Player player : getOnlineSpectators()) {
            removeSpectator(player);
        }
        spectators.clear();
    }

    /**
     * Whether spectators of this area survive a disconnect and are restored on reconnect (teleported
     * back to the spectator spawn by {@link #handleSpectatorJoin}). Default {@code false}: a spectator
     * who quits is dropped, because {@code GameManagerHandler.onPlayerQuit} calls {@code leaveSpectating}.
     * Areas that opt in must release their spectators on game end via {@link #releaseAllSpectators()},
     * otherwise a reconnecting spectator would land in a finished game.
     */
    public boolean keepSpectatorAcrossReconnect() {
        return false;
    }

    /**
     * Releases every spectator - online ones are teleported to the lobby and set to ADVENTURE, offline
     * ones are just dropped - and clears both this area's spectator set and the GameManager's
     * spectator-status map for them. Used on game end by areas that keep spectators across reconnect.
     */
    public void releaseAllSpectators() {
        Set<UUID> ids = new HashSet<>(spectators);
        for (UUID uuid : ids) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                removeSpectator(player);                 // teleport to lobby + ADVENTURE; drop from set
            } else {
                onlyRemoveSpectatorFromList(uuid);       // drop from set
            }
        }
        // Clear the GameManager's spectator-status entries for the released UUIDs (covers offline
        // spectators that leaveSpectating-on-quit would otherwise have cleared).
        for (UUID uuid : ids) {
            plugin.getGameManager().removeSpectator(uuid);
        }
    }

    public void endGameFinally() {
        cancelIntroduction();
        cancelFinalCountdown();
        clearBossBars();
        removeAllSpectator();
        removeAllPlayers();
        endGame();
    }

    public void removeSpectator(@NotNull UUID uuid) {
        if (spectators.contains(uuid)) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                removeSpectator(player);
            } else {
                onlyRemoveSpectatorFromList(uuid);
            }
        }
    }

    public void removeSpectator(@NotNull Player player) {
        UUID uuid = player.getUniqueId();
        if (spectators.contains(uuid)) {
            spectators.remove(player.getUniqueId());
            removePlayerFromBossBars(player);
            player.teleportAsync(getLobbyLocation()).thenAccept(success -> {
                if (!success)
                    return;
                scheduler.runEntity(player, () -> {
                    clearSpectatorGameMode(player);
                    player.setGameMode(GameMode.ADVENTURE);
                    player.setLevel(0);
                });
            });
        }
    }

    public void onlyRemoveSpectatorFromList(@NotNull UUID uuid) {
        spectators.remove(uuid);
    }

    public List<Player> getOnlineSpectators() {
        List<Player> list = new ArrayList<>();
        for (UUID uuid : spectators) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                list.add(player);
            }
        }

        return list;
    }

    public List<ChampionshipPlayer> getOnlineCCSpectators() {
        List<ChampionshipPlayer> list = new ArrayList<>();
        for (UUID uuid : spectators) {
            ChampionshipPlayer championshipPlayer = plugin.getPlayerManager().getPlayer(uuid);
            if (championshipPlayer != null) {
                list.add(championshipPlayer);
            }
        }

        return list;
    }

    public void sendMessageToAllSpectators(String message) {
        for (Player player : getOnlineSpectators()) {
            scheduler.runEntity(player, () -> player.sendMessage(message));
        }
    }

    public void sendActionBarToAllSpectators(String message) {
        for (ChampionshipPlayer championshipPlayer : getOnlineCCSpectators()) {
            championshipPlayer.sendActionBar(message);
        }
    }

    public void changeLevelToAllSpectators(int level) {
        for (Player player : getOnlineSpectators()) {
            scheduler.runEntity(player, () -> player.setLevel(Math.abs(level)));
        }
    }

    public void sendTitleToAllSpectators(String title, String subTitle) {
        for (ChampionshipPlayer championshipPlayer : getOnlineCCSpectators()) {
            championshipPlayer.sendTitle(title, subTitle);
        }
    }

    public void cleanDroppedItems() {
        cleanEntities(Item.class);
    }

    protected void cleanEntities(Class<? extends Entity> entityType) {
        Vector pos1 = getGameConfig().getAreaPos1();
        Vector pos2 = getGameConfig().getAreaPos2();
        World world = getSpectatorSpawnLocation().getWorld();
        if (world == null)
            return;
        double minX = Math.min(pos1.getX(), pos2.getX());
        double maxX = Math.max(pos1.getX(), pos2.getX());
        double minY = Math.min(pos1.getY(), pos2.getY());
        double maxY = Math.max(pos1.getY(), pos2.getY());
        double minZ = Math.min(pos1.getZ(), pos2.getZ());
        double maxZ = Math.max(pos1.getZ(), pos2.getZ());
        cleanEntities(world, new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ), entityType);
    }

    protected void cleanEntities(World world, BoundingBox bounds, Class<? extends Entity> entityType) {
        int minChunkX = ((int) Math.floor(bounds.getMinX())) >> 4;
        int maxChunkX = ((int) Math.floor(bounds.getMaxX())) >> 4;
        int minChunkZ = ((int) Math.floor(bounds.getMinZ())) >> 4;
        int maxChunkZ = ((int) Math.floor(bounds.getMaxZ())) >> 4;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                double chunkMinX = chunkX << 4;
                double chunkMinZ = chunkZ << 4;
                BoundingBox chunkBox = new BoundingBox(
                        Math.max(bounds.getMinX(), chunkMinX), bounds.getMinY(),
                        Math.max(bounds.getMinZ(), chunkMinZ),
                        Math.min(bounds.getMaxX(), chunkMinX + 15.999), bounds.getMaxY(),
                        Math.min(bounds.getMaxZ(), chunkMinZ + 15.999));
                Location owner = new Location(world, chunkMinX + 8, bounds.getMinY(), chunkMinZ + 8);
                scheduler.runAtLocation(owner, () -> world.getNearbyEntities(chunkBox, entityType::isInstance)
                        .forEach(Entity::remove));
            }
        }
    }

    public boolean notInArea(Location location) {
        if (location.getWorld() != null && getSpectatorSpawnLocation().getWorld() != null && location.getWorld().getName().equals(getSpectatorSpawnLocation().getWorld().getName())) {
            return !location.toVector().isInAABB(getGameConfig().getAreaPos1(), getGameConfig().getAreaPos2());
        }

        return true;
    }

    /**
     * The space an external spectator may explore. Usually this is the instance boundary, while a
     * shared map can override it to include all of its permanently allocated instance copies.
     */
    public boolean isSpectatorLocationAllowed(@NotNull Location location) {
        return !notInArea(location);
    }

    public abstract Location getSpectatorSpawnLocation();

    public abstract int getTimer();

    public abstract void endGame();

    public abstract void resetBaseArea();

    public abstract void resetArea();

    public abstract BaseGameConfig getGameConfig();

    public abstract BaseListener getGameHandler();

    public abstract String getWorldName();

    public abstract void removeAllPlayers();

    public abstract void startGamePreparation();

    public abstract void sendMessageToAllGamePlayers(String message);

    public abstract void sendActionBarToAllGamePlayers(String message);

    protected abstract Collection<Player> getOnlineParticipantSpectators();

    public abstract void sendTitleToAllGamePlayers(String title, String subTitle);

    public abstract void changeLevelForAllGamePlayers(int level);

    public abstract void changeGameModelForAllGamePlayers(GameMode gameMode);

    public abstract void setHealthForAllGamePlayers(double health);

    public abstract void revokeAllGamePlayersAdvancements();

    public abstract void setFoodLevelForAllGamePlayers(int level);

    public abstract void teleportAllPlayers(Location location);

    public abstract void clearEffectsForAllGamePlayers();

    public abstract void cleanInventoryForAllGamePlayers();

    public abstract void playSoundToAllGamePlayers(Sound sound, float volume, float pitch);

    public abstract void playNoteToAllGamePlayers(Instrument instrument, Note note);

    public abstract boolean notAreaPlayer(@NotNull Player player);

    public abstract void handlePlayerDeath(@NotNull PlayerDeathEvent event);

    public abstract void handlePlayerQuit(@NotNull PlayerQuitEvent event);

    public abstract void handlePlayerJoin(@NotNull PlayerJoinEvent event);
}
