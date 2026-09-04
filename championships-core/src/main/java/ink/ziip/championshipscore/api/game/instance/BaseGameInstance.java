package ink.ziip.championshipscore.api.game.instance;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseListener;
import ink.ziip.championshipscore.api.game.config.BaseGameConfig;
import ink.ziip.championshipscore.api.game.arena.ArenaChunkPreloader;
import ink.ziip.championshipscore.api.game.manager.BaseGameInstanceManager;
import ink.ziip.championshipscore.api.object.game.GameRunMode;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import ink.ziip.championshipscore.api.player.ChampionshipPlayer;
import ink.ziip.championshipscore.api.player.PlayerManager;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.CCConfig;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.platform.bukkit.player.PlayerStateService;
import ink.ziip.championshipscore.shared.presentation.RuleIntroductionTimeline;
import ink.ziip.championshipscore.util.Utils;
import org.bukkit.*;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.Supplier;
import java.util.logging.Level;

public abstract class BaseGameInstance {
    private static final String GAME_TIMER_BOSS_BAR = "game-timer";
    protected final HashSet<UUID> spectators = new HashSet<>();
    protected final Map<UUID, Double> playerPoints = new ConcurrentHashMap<>();
    protected final ChampionshipsCore plugin;
    protected final BukkitScheduler scheduler;
    protected final GameInstanceHandler gameInstanceHandler;
    protected final PlayerManager playerManager;
    protected final Map<String, BossBar> bossBars = new ConcurrentHashMap<>();
    private final Set<ArenaChunkPreloader.ChunkTicket> startChunkTickets = ConcurrentHashMap.newKeySet();
    private volatile CompletableFuture<Void> startPreloadFuture = CompletableFuture.completedFuture(null);
    private boolean roundTransitionPending;
    private boolean forceTerminalEnd;
    private boolean settlementSuppressed;
    private GameRunMode runMode = GameRunMode.GAME;
    private boolean postGamePending;
    private boolean postGameFinalizing;
    private volatile CompletableFuture<Void> coordinatedStartGate;
    private CompletableFuture<Boolean> activeMapLoad = CompletableFuture.completedFuture(true);
    private long mapLoadGeneration;
    private volatile boolean disposed;

    /** Duration (seconds) of the optional rule-introduction phase preceding the normal preparation. */
    protected static final int INTRODUCTION_DURATION = 90;
    private static final int INTRODUCTION_TITLE_DURATION_SECONDS = 5;

    /** True while players are gathered at the introduction spawn point for the rules broadcast. */
    protected volatile boolean introductionPhase = false;
    protected BukkitTask introductionTask;
    private boolean introductionEnabledForNextStart;
    private int preparationCountdownDuration;

    /** Final five-second countdown, isolated from every game's live timer. */
    protected BukkitTask finalCountdownTask;
    private final CountdownBlockDisappearance countdownBlockDisappearance;
    private static final long POST_GAME_RESULT_DISPLAY_TICKS = 200L;
    private BukkitTask postGameRoutingTask;

    private static final Note BIT_C4 = Note.natural(0, Note.Tone.C);
    private static final Note BIT_C5 = Note.natural(1, Note.Tone.C);

    protected BaseListener gameHandler;
    protected BaseGameConfig gameConfig;

    protected GameStageEnum gameStageEnum;
    protected GameTypeEnum gameTypeEnum;

    public BaseGameInstance(ChampionshipsCore plugin, GameTypeEnum gameTypeEnum, BaseListener gameHandler,
                            BaseGameConfig gameConfig) {
        this.playerManager = plugin.getPlayerManager();

        this.gameStageEnum = GameStageEnum.END;
        this.plugin = plugin;
        this.scheduler = plugin.getServer().getScheduler();
        this.gameTypeEnum = gameTypeEnum;

        this.gameHandler = gameHandler;
        this.gameConfig = gameConfig;
        this.countdownBlockDisappearance = new CountdownBlockDisappearance(plugin, this);

        gameInstanceHandler = new GameInstanceHandler(plugin, this);
        gameInstanceHandler.register();
    }

    public void resetGame() {
        releaseStartChunks();
        cancelIntroduction();
        cancelFinalCountdown();
        countdownBlockDisappearance.restore();
        resetBaseArea();
        roundTransitionPending = false;
        postGamePending = false;
        playerPoints.clear();
        clearBossBars();

        setGameStageEnum(GameStageEnum.WAITING);
        logGame(Level.INFO, "流程", "场地已重置，等待下一场");
    }

    public final void routePlayerMoveLow(@NotNull PlayerMoveEvent event) {
        gameInstanceHandler.handleRoutedPlayerMoveLow(event);
        gameHandler.handleRoutedPlayerMoveLow(event);
    }

    public final void routePlayerMoveNormal(@NotNull PlayerMoveEvent event) {
        gameHandler.handleRoutedPlayerMoveNormal(event);
    }

    public final void routePlayerMoveHigh(@NotNull PlayerMoveEvent event) {
        gameHandler.handleRoutedPlayerMoveHigh(event);
    }

    /** Permanently releases listeners and UI owned by this instance when its manager unloads it. */
    public void dispose() {
        disposed = true;
        mapLoadGeneration++;
        if (!activeMapLoad.isDone())
            activeMapLoad.complete(false);
        releaseStartChunks();
        cancelIntroduction();
        cancelFinalCountdown();
        countdownBlockDisappearance.restore();
        cancelPostGameRouting();
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
        Location introductionSpawnPoint = resolveIntroductionSpawnPoint();
        if (introductionSpawnPoint != null)
            locations.add(introductionSpawnPoint);
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
                scheduler.runTask(plugin, () -> {
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
        if (uuid == null) {
            logGame(Level.WARNING, "积分", "忽略空玩家 UUID 的积分变更=" + formatPointChange(points));
            return;
        }
        playerPoints.put(uuid, playerPoints.getOrDefault(uuid, 0d) + points);
        logGame(Level.INFO, "积分", "玩家=" + plugin.getPlayerManager().getPlayerName(uuid)
                + " uuid=" + uuid + " 变更=" + formatPointChange(points));
        ChampionshipPlayer championshipPlayer = plugin.getPlayerManager().getPlayer(uuid);
        if (championshipPlayer != null)
            championshipPlayer.sendActionBar("&e[+] " + points);
        Player online = Bukkit.getPlayer(uuid);
        if (online != null && plugin.getSidebarManager() != null) plugin.getSidebarManager().invalidate(online);
    }

    public void addPlayerPointsToAllTeamMembers(ChampionshipTeam championshipTeam, int points) {
        for (UUID uuid : championshipTeam.getMembers()) {
            playerPoints.put(uuid, playerPoints.getOrDefault(uuid, 0d) + points);
            logGame(Level.INFO, "积分", "玩家=" + plugin.getPlayerManager().getPlayerName(uuid)
                    + " uuid=" + uuid + " 变更=" + formatPointChange(points));
            Player online = Bukkit.getPlayer(uuid);
            if (online != null && plugin.getSidebarManager() != null) plugin.getSidebarManager().invalidate(online);
        }
    }

    /** Immutable result snapshot for non-formal settlement layers such as DAILY. */
    public Map<UUID, Double> getPlayerPointsSnapshot() {
        return Map.copyOf(playerPoints);
    }

    protected void logGame(Level level, String event, String message) {
        String area = gameConfig == null || gameConfig.getAreaName() == null ? "-" : gameConfig.getAreaName();
        String stage = gameStageEnum == null ? "-" : gameStageEnum.name();
        String formatted = Utils.formatGameLog(gameTypeEnum, area, stage, event, message);
        boolean importantFlow = Level.INFO.equals(level) && "流程".equals(event)
                && (message.startsWith("游戏开始") || message.startsWith("游戏结束"));
        if (importantFlow && plugin.getLogManager() != null) plugin.getLogManager().important(formatted);
        else plugin.getLogger().log(level, formatted);
    }

    private String formatPointChange(double points) {
        return (points >= 0 ? "+" : "") + Utils.formatPoints(points);
    }

    public void addPlayerPointsToDatabase() {
        if (settlementSuppressed || runMode != GameRunMode.EVENT)
            return;
        List<ink.ziip.championshipscore.api.rank.RankManager.PointSubmission> submissions = new ArrayList<>();
        for (Map.Entry<UUID, Double> playerPointEntry : playerPoints.entrySet()) {
            if (playerPointEntry.getValue() != 0)
                submissions.add(new ink.ziip.championshipscore.api.rank.RankManager.PointSubmission(
                        UUID.randomUUID(), playerPointEntry.getKey(), null, gameTypeEnum,
                        gameConfig.getAreaName(), "scc", playerPointEntry.getValue()));
        }
        plugin.getRankManager().addPlayerPointsBatch(submissions).exceptionally(failure -> {
            logGame(Level.SEVERE, "积分", "批量积分提交失败 | " + failure.getMessage());
            return false;
        });
        plugin.getRankManager().refreshAfterPendingPointWrites();
    }

    public int getTeamPoints(ChampionshipTeam championshipTeam) {
        int points = 0;
        for (UUID uuid : championshipTeam.getMembers()) {
            points += playerPoints.getOrDefault(uuid, 0d);
        }

        return points;
    }

    public GameStageEnum getGameStageEnum() {
        synchronized (this) {
            return this.gameStageEnum;
        }
    }

    public GameTypeEnum getGameTypeEnum() {
        return gameTypeEnum;
    }

    public GameRunMode getRunMode() {
        return runMode;
    }

    public boolean isEventRun() {
        return runMode == GameRunMode.EVENT;
    }

    /** Assigned by GameManager immediately before tryStartGame; ordinary game commands use GAME. */
    public void prepareRunMode(@NotNull GameRunMode runMode) {
        if (getGameStageEnum() != GameStageEnum.WAITING)
            throw new IllegalStateException("Cannot change run mode while an instance is active");
        this.runMode = runMode;
    }

    public void setGameStageEnum(GameStageEnum gameStageEnum) {
        GameStageEnum previous;
        synchronized (this) {
            previous = this.gameStageEnum;
            this.gameStageEnum = gameStageEnum;
        }
        if (plugin.getSidebarManager() != null) plugin.getSidebarManager().invalidateAll();
        if (gameStageEnum == GameStageEnum.PREPARATION && previous != GameStageEnum.PREPARATION
                && plugin.getGameManager() != null) {
            plugin.getGameManager().onInstancePreparationStarted(this);
        }
    }

    public CompletableFuture<Boolean> loadMap(World.Environment environment) {
        if (!Bukkit.isPrimaryThread())
            return runOnMain(() -> loadMap(environment));
        if (disposed || !plugin.isLoaded() || !plugin.isEnabled())
            return CompletableFuture.completedFuture(false);
        if (!activeMapLoad.isDone())
            return activeMapLoad;

        CompletableFuture<Boolean> result = new CompletableFuture<>();
        activeMapLoad = result;
        long generation = ++mapLoadGeneration;
        long startedAt = System.nanoTime();

        clearBossBars();
        teleportAllSpectators(getLobbyLocation());

        setGameStageEnum(GameStageEnum.END);
        getGameHandler().unRegister();
        logGame(Level.INFO, "世界", "开始加载 " + getWorldName());

        // Repair the published template and preserve the current world before reload deletes either copy.
        if (!plugin.getWorldManager().prepareWorldForFirstLoad(getWorldName())) {
            logGame(Level.SEVERE, "世界", "加载失败：首次加载前世界修复未完成 " + getWorldName());
            result.complete(false);
            return result;
        }

        File target = plugin.getWorldManager().getWorldFolder(getWorldName());
        World loadedWorld = plugin.getServer().getWorld(getWorldName());
        if (loadedWorld != null && !plugin.getWorldManager().unloadWorld(getWorldName(), false)) {
            logGame(Level.SEVERE, "世界", "加载失败：Bukkit 无法卸载 " + getWorldName());
            result.complete(false);
            return result;
        }
        long unloadedAt = System.nanoTime();

        File maps = new File(plugin.getDataFolder(), "maps");
        File source = new File(maps, getWorldName());
        runAsyncFileOperation(() -> replaceWorldFiles(source, target))
                .thenCompose(filesReady -> runOnMain(() -> CompletableFuture.completedFuture(
                        finishMapLoad(environment, generation, startedAt, unloadedAt, filesReady))))
                .whenComplete((success, error) -> {
                    if (error != null)
                        logGame(Level.SEVERE, "世界", "加载任务异常 " + getWorldName() + " | " + error.getMessage());
                    result.complete(error == null && Boolean.TRUE.equals(success));
                });
        return result;
    }

    private boolean finishMapLoad(World.Environment environment, long generation, long startedAt,
                                  long unloadedAt, boolean filesReady) {
        if (disposed || generation != mapLoadGeneration || !plugin.isLoaded() || !plugin.isEnabled())
            return false;
        if (!filesReady) {
            logGame(Level.SEVERE, "世界", "加载失败：无法异步重建地图文件 " + getWorldName());
            return false;
        }
        long filesReadyAt = System.nanoTime();
        if (!plugin.getWorldManager().loadWorld(getWorldName(), environment, false)) {
            logGame(Level.SEVERE, "世界", "加载失败：Bukkit 无法加载 " + getWorldName());
            return false;
        }

        if (!getGameConfig().reloadConfigurationChecked(plugin.getFolder())) {
            logGame(Level.SEVERE, "配置", "地图配置重载失败，场地保持禁用 " + getWorldName());
            return false;
        }
        getGameHandler().register();
        setGameStageEnum(GameStageEnum.WAITING);
        long finishedAt = System.nanoTime();
        logGame(Level.INFO, "世界", "加载完成 " + getWorldName()
                + " | 主线程卸载=" + elapsedMillis(startedAt, unloadedAt) + "ms"
                + " 异步文件=" + elapsedMillis(unloadedAt, filesReadyAt) + "ms"
                + " 主线程加载=" + elapsedMillis(filesReadyAt, finishedAt) + "ms");
        teleportAllSpectators(getSpectatorSpawnLocation());
        return true;
    }

    private boolean replaceWorldFiles(File source, File target) {
        if (target.exists() && !plugin.getWorldManager().deleteWorldFiles(target))
            return false;
        if (plugin.getWorldManager().copyWorldFiles(source, target))
            return true;
        plugin.getWorldManager().deleteWorldFiles(target);
        return false;
    }

    private long elapsedMillis(long startedAt, long finishedAt) {
        return java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(finishedAt - startedAt);
    }

    /**
     * Restores a published map template when one exists. New maps deliberately have no template until
     * prepare publishes their first revision, so reopening the server must keep their editable draft
     * world instead of deleting it and then failing a template copy.
     */
    public final CompletableFuture<Boolean> loadPublishedMapOrDraft(World.Environment environment) {
        if (!Bukkit.isPrimaryThread())
            return runOnMain(() -> loadPublishedMapOrDraft(environment));
        if (!getGameConfig().reloadConfigurationChecked(plugin.getFolder()))
            return CompletableFuture.completedFuture(false);
        if (getGameConfig().isWorldBindingPending()) {
            getGameHandler().register();
            setGameStageEnum(GameStageEnum.WAITING);
            logGame(Level.INFO, "世界", "地图草稿尚未绑定世界，等待 prepare 设置");
            return CompletableFuture.completedFuture(true);
        }
        File template = new File(new File(plugin.getDataFolder(), "maps"), getWorldName());
        if (getGameConfig().isPrepareReady() && template.isDirectory())
            return loadMap(environment);

        if (getGameConfig().isPrepareReady()) {
            getGameConfig().beginPrepareDraft();
            logGame(Level.SEVERE, "世界", "已发布地图缺少模板，已降为草稿并禁止开赛：" + template.getPath());
        }
        return CompletableFuture.completedFuture(loadDraftWorld(environment));
    }

    private boolean loadDraftWorld(World.Environment environment) {
        clearBossBars();
        teleportAllSpectators(getLobbyLocation());
        setGameStageEnum(GameStageEnum.END);
        getGameHandler().unRegister();
        logGame(Level.INFO, "世界", "加载 prepare 草稿 " + getWorldName());

        if (!plugin.getWorldManager().loadWorld(getWorldName(), environment, false)) {
            logGame(Level.SEVERE, "世界", "草稿世界加载失败：" + getWorldName());
            return false;
        }

        getGameHandler().register();
        setGameStageEnum(GameStageEnum.WAITING);
        logGame(Level.INFO, "世界", "草稿世界已就绪，等待 prepare 发布");
        return true;
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

    public CompletableFuture<Boolean> saveMap(World.Environment environment) {
        if (!Bukkit.isPrimaryThread())
            return runOnMain(() -> saveMap(environment));
        if (!canSaveMap()) {
            logGame(Level.WARNING, "世界", "保存被拒绝：同一地图仍有运行中的游戏实例");
            return CompletableFuture.completedFuture(false);
        }

        setGameStageEnum(GameStageEnum.END);
        logGame(Level.INFO, "世界", "开始保存 " + getWorldName());
        teleportAllSpectators(getLobbyLocation());

        World editWorld = plugin.getServer().getWorld(getWorldName());
        if (editWorld == null) {
            setGameStageEnum(GameStageEnum.WAITING);
            logGame(Level.WARNING, "世界", "保存失败：世界未加载 " + getWorldName());
            return CompletableFuture.completedFuture(false);
        }
        for (Player player : editWorld.getPlayers()) {
            player.teleport(Utils.getScatteredLobbyLocation(CCConfig.LOBBY_LOCATION, player));
        }

        // Unload world but not remove files
        if (!plugin.getWorldManager().unloadWorld(getWorldName(), true)) {
            setGameStageEnum(GameStageEnum.WAITING);
            logGame(Level.SEVERE, "世界", "保存失败：Bukkit 无法卸载 " + getWorldName());
            return CompletableFuture.completedFuture(false);
        }

        File dataDirectory = new File(plugin.getDataFolder(), "maps");
        File target = new File(dataDirectory, getWorldName());
        File source = plugin.getWorldManager().getWorldFolder(getWorldName());
        String transaction = ".prepare-" + getWorldName() + "-" + UUID.randomUUID();
        File staging = new File(dataDirectory, transaction + "-staging");
        File backup = new File(dataDirectory, transaction + "-previous");

        return runAsyncFileOperation(() -> stagePublishedTemplate(source, target, staging, backup))
                .thenCompose(staged -> runOnMain(() -> {
                    if (!staged) {
                        boolean draftLoaded = loadDraftWorld(environment);
                        if (!draftLoaded)
                            logGame(Level.SEVERE, "世界", "发布失败后编辑世界也无法重新加载 " + getWorldName());
                        return CompletableFuture.completedFuture(false);
                    }
                    return loadMap(environment).thenCompose(loaded -> {
                        if (loaded)
                            return runAsyncFileOperation(() -> {
                                if (backup.exists() && !plugin.getWorldManager().deleteWorldFiles(backup))
                                    logGame(Level.WARNING, "世界", "发布成功，但旧 revision 清理失败 " + backup.getPath());
                                return true;
                            });
                        return rollbackPublishedTemplate(environment, source, target, backup);
                    });
                }));
    }

    private boolean stagePublishedTemplate(File source, File target, File staging, File backup) {
        if (!plugin.getWorldManager().copyWorldFiles(source, staging)) {
            plugin.getWorldManager().deleteWorldFiles(staging);
            logGame(Level.SEVERE, "世界", "发布失败：无法写入暂存模板，旧版本未改变 " + getWorldName());
            return false;
        }
        try {
            if (target.exists())
                java.nio.file.Files.move(target.toPath(), backup.toPath());
            java.nio.file.Files.move(staging.toPath(), target.toPath());
        } catch (Exception exception) {
            plugin.getWorldManager().deleteWorldFiles(staging);
            try {
                if (!target.exists() && backup.exists())
                    java.nio.file.Files.move(backup.toPath(), target.toPath());
            } catch (Exception rollback) {
                logGame(Level.SEVERE, "世界", "发布回滚失败：" + rollback.getMessage());
            }
            logGame(Level.SEVERE, "世界", "发布失败：模板切换失败，编辑世界已保留 | "
                    + exception.getMessage());
            return false;
        }
        if (!plugin.getWorldManager().deleteWorldFiles(source))
            logGame(Level.WARNING, "世界", "新模板已切换，但编辑世界目录清理不完整 " + source.getPath());
        return true;
    }

    private CompletableFuture<Boolean> rollbackPublishedTemplate(World.Environment environment,
                                                                  File source, File target, File backup) {
        if (!backup.exists())
            return CompletableFuture.completedFuture(false);
        World loadedWorld = plugin.getServer().getWorld(getWorldName());
        if (loadedWorld != null && !plugin.getWorldManager().unloadWorld(getWorldName(), false)) {
            logGame(Level.SEVERE, "世界", "新 revision 加载失败且无法卸载残留世界，未执行文件回滚");
            return CompletableFuture.completedFuture(false);
        }
        return runAsyncFileOperation(() -> {
            if (!plugin.getWorldManager().deleteWorldFiles(source)
                    || !plugin.getWorldManager().deleteWorldFiles(target))
                return false;
            try {
                java.nio.file.Files.move(backup.toPath(), target.toPath());
                return true;
            } catch (Exception exception) {
                logGame(Level.SEVERE, "世界", "新 revision 加载失败且回滚失败：" + exception.getMessage());
                return false;
            }
        }).thenCompose(restored -> runOnMain(() -> {
            if (!restored)
                return CompletableFuture.completedFuture(false);
            return loadMap(environment).thenApply(ignored -> {
                logGame(Level.SEVERE, "世界", "新 revision 加载失败，已回滚到上一发布版本");
                return false;
            });
        }));
    }

    private CompletableFuture<Boolean> runAsyncFileOperation(BooleanSupplier operation) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        if (disposed || !plugin.isLoaded() || !plugin.isEnabled()) {
            result.complete(false);
            return result;
        }
        try {
            scheduler.runTaskAsynchronously(plugin, () -> {
                try {
                    result.complete(operation.getAsBoolean());
                } catch (Throwable throwable) {
                    logGame(Level.SEVERE, "世界", "异步文件任务异常 | " + throwable.getMessage());
                    result.complete(false);
                }
            });
        } catch (RuntimeException exception) {
            logGame(Level.SEVERE, "世界", "无法提交异步文件任务 | " + exception.getMessage());
            result.complete(false);
        }
        return result;
    }

    private CompletableFuture<Boolean> runOnMain(Supplier<CompletableFuture<Boolean>> operation) {
        if (Bukkit.isPrimaryThread())
            return operation.get();
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        if (disposed || !plugin.isLoaded() || !plugin.isEnabled()) {
            result.complete(false);
            return result;
        }
        try {
            scheduler.runTask(plugin, () -> {
                try {
                    operation.get().whenComplete((success, error) -> {
                        if (error != null) result.completeExceptionally(error);
                        else result.complete(success);
                    });
                } catch (Throwable throwable) {
                    result.completeExceptionally(throwable);
                }
            });
        } catch (RuntimeException exception) {
            result.complete(false);
        }
        return result;
    }

    private BossBar createBossBar(String title, BarColor color, BarStyle style) {
        return Bukkit.createBossBar(Utils.translateColorCodes(title), color, style);
    }

    public BossBar createBossBar(String name, String title, BarColor color, BarStyle style) {
        BossBar bossBar = createBossBar(title, color, style);
        if (bossBars.containsKey(name))
            removeBossBar(name);

        bossBars.put(name, bossBar);
        return bossBar;
    }

    public void removeBossBar(String name) {
        BossBar bossBar = bossBars.remove(name);
        if (bossBar != null) {
            bossBar.removeAll();
        }
    }

    /** Removes every area-owned bar and all of its viewers. Safe to call repeatedly. */
    public void clearBossBars() {
        for (BossBar bossBar : new ArrayList<>(bossBars.values()))
            bossBar.removeAll();
        bossBars.clear();
        preparationCountdownDuration = 0;
    }

    protected final void removePlayerFromBossBars(Player player) {
        for (BossBar bossBar : bossBars.values())
            bossBar.removePlayer(player);
    }

    /** Updates the shared timer bar and synchronizes it to every participant and instance spectator. */
    protected void updateGameTimerBossBar(String title, int remainingSeconds, int durationSeconds) {
        double progress = durationSeconds <= 0 ? 0D : remainingSeconds / (double) durationSeconds;
        updateGameTimerBossBar(title, progress);
    }

    /** Variant for non-countdown clocks, where the caller supplies the semantic progress directly. */
    protected void updateGameTimerBossBar(String title, double progress) {
        BossBar bossBar = bossBars.computeIfAbsent(GAME_TIMER_BOSS_BAR,
                ignored -> createBossBar(title, BarColor.YELLOW, BarStyle.SOLID));
        bossBar.setTitle(Utils.translateColorCodes(title));
        bossBar.setProgress(Math.max(0D, Math.min(1D, progress)));

        Set<Player> viewers = new LinkedHashSet<>();
        for (UUID uuid : getParticipantUniqueIds()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null)
                viewers.add(player);
        }
        viewers.addAll(getOnlineSpectators());
        for (Map.Entry<String, BossBar> entry : bossBars.entrySet()) {
            if (GAME_TIMER_BOSS_BAR.equals(entry.getKey()))
                continue;
            for (Player viewer : viewers)
                entry.getValue().removePlayer(viewer);
        }
        for (Player current : new ArrayList<>(bossBar.getPlayers())) {
            if (!viewers.contains(current))
                bossBar.removePlayer(current);
        }
        for (Player viewer : viewers)
            bossBar.addPlayer(viewer);
    }

    protected void clearGameTimerBossBar() {
        removeBossBar(GAME_TIMER_BOSS_BAR);
        preparationCountdownDuration = 0;
    }

    public void setBossBar(String name, String title) {
        BossBar bossBar = bossBars.get(name);
        if (bossBar != null) {
            bossBar.setTitle(Utils.translateColorCodes(title));
        } else {
            logGame(Level.WARNING, "BossBar", "未找到 " + name);
        }
    }

    public void addBossBarPlayer(String name, Player player) {
        if (player == null)
            return;

        BossBar bossBar = bossBars.get(name);
        if (bossBar != null) {
            bossBar.addPlayer(player);
        } else {
            logGame(Level.WARNING, "BossBar", "未找到 " + name);
        }
    }

    public void removeBossBarPlayer(String name, Player player) {
        BossBar bossBar = bossBars.get(name);
        if (bossBar != null) {
            bossBar.removePlayer(player);
        } else {
            logGame(Level.WARNING, "BossBar", "未找到 " + name);
        }
    }

    public void setBossBarProgress(String name, double progress) {
        BossBar bossBar = bossBars.get(name);
        if (bossBar != null) {
            bossBar.setProgress(progress);
        } else {
            logGame(Level.WARNING, "BossBar", "未找到 " + name);
        }
    }

    public Location getLobbyLocation() {
        return CCConfig.LOBBY_LOCATION;
    }

    /**
     * Lobby spawn scattered horizontally around the configured centre for one player, shared with the
     * daily-mode lobby routing via {@link Utils#getScatteredLobbyLocation(Location, Player)}.
     */
    private Location getScatteredLobbyLocation(@NotNull Player player) {
        return Utils.getScatteredLobbyLocation(getLobbyLocation(), player);
    }

    /** True while the visible result phase still owns the participant roster. */
    public boolean isPostGamePending() {
        return postGamePending;
    }

    /** Removes all game state that could leak into the lobby and applies its authoritative mode. */
    public void sanitizeParticipantForLobby(@NotNull Player player, boolean teleport) {
        player.getInventory().clear();
        PlayerStateService.clearEffects(player);
        PlayerStateService.disableFlight(player);
        PlayerStateService.clearHazards(player);
        player.setLevel(0);
        if (teleport && getLobbyLocation() != null && getLobbyLocation().getWorld() != null)
            player.teleport(getScatteredLobbyLocation(player));
        player.setGameMode(GameMode.ADVENTURE);
    }

    /** Opens the result-display phase without releasing players or rebuilding the arena. */
    protected final void beginPostGameSettlement() {
        cancelPostGameRouting();
        postGamePending = true;
        roundTransitionPending = false;
        if (isEventRun() && plugin.getScheduleManager() != null)
            plugin.getScheduleManager().registerPendingEventInstance(this);
    }

    /** Called after the synchronous end event has given an event coordinator a chance to take ownership. */
    protected final void finishPostGameAfterEndEvent() {
        if (forceTerminalEnd) {
            completePostGame(false);
            return;
        }
        if (isEventRun()) {
            plugin.getScheduleManager().onEventInstanceReady(this);
            return;
        }
        postGameRoutingTask = scheduler.runTaskLater(plugin, () -> completePostGame(false),
                POST_GAME_RESULT_DISPLAY_TICKS);
    }

    /** Releases one finished run only after its visible result phase. */
    public final void completePostGame(boolean nextEventRound) {
        if (!postGamePending || postGameFinalizing)
            return;
        postGameFinalizing = true;
        // Normal event settlement removes the queue entry before this callback; force-stop
        // finalization reaches here directly, so make both paths clean up identically.
        if (plugin.getScheduleManager() != null)
            plugin.getScheduleManager().unregisterPendingEventInstance(this);
        cancelPostGameRouting();
        roundTransitionPending = nextEventRound;

        List<UUID> participantIds = List.copyOf(getParticipantUniqueIds());
        if (nextEventRound) {
            plugin.getGameManager().holdParticipantsForNextRound(this, participantIds);
            plugin.getGameManager().holdSpectatorsForNextRound(this);
        } else {
            for (UUID uuid : participantIds) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null && player.isOnline())
                    sanitizeParticipantForLobby(player, true);
            }
        }

        if (!nextEventRound)
            releaseAllSpectators();
        plugin.getGameManager().releaseInstanceParticipants(this);
        try {
            countdownBlockDisappearance.restore();
            resetGame();
            // A DAILY session remains reserved through the result phase. Only after the instance has
            // released its player status and reset can its players receive the lobby entry item again.
            plugin.getDailyManager().onInstanceReturnedToLobby(this);
        } finally {
            roundTransitionPending = false;
            postGamePending = false;
            postGameFinalizing = false;
            runMode = GameRunMode.GAME;
        }
    }

    protected final void cancelPostGameRoutingBeforeStart() {
        cancelPostGameRouting();
        postGamePending = false;
        postGameFinalizing = false;
    }

    private void cancelPostGameRouting() {
        if (postGameRoutingTask != null) {
            postGameRoutingTask.cancel();
            postGameRoutingTask = null;
        }
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
     * Runs the optional rule-introduction phase. When the area config provides at least one rule
     * section, every player is teleported to its introduction spawn, falling back to the spectator
     * spawn when no dedicated point is configured. Players remain in PREPARATION and use adventure
     * mode while the rule sections are broadcast one at a time
     * in chat over {@link #INTRODUCTION_DURATION} seconds; afterwards {@code onComplete} (the normal
     * preparation: spawn assignment + countdown) runs. Without rules the introduction is skipped
     * and {@code onComplete} runs immediately.
     */
    protected void startGameIntroduction(@NotNull Runnable onComplete) {
        // Rule presentation belongs to the formal event lifecycle. Standalone game starts may
        // select their participants, but must never turn into an event-wide audience action.
        boolean showIntroduction = isEventRun() && introductionEnabledForNextStart;
        introductionEnabledForNextStart = false;
        if (!showIntroduction) {
            onComplete.run();
            return;
        }

        List<List<String>> rules = getIntroductionRules();
        Location introductionSpawnPoint = resolveIntroductionSpawnPoint();
        if (rules == null || rules.isEmpty() || introductionSpawnPoint == null) {
            onComplete.run();
            return;
        }

        introductionPhase = true;
        applyIntroductionGameModeToAllParticipants();
        teleportAllPlayers(introductionSpawnPoint);
        sendTimedTitleToAllGamePlayers(MessageConfig.GAME_INTRODUCTION_TITLE
                        .replace("%game%", gameTypeEnum.toString()), "",
                INTRODUCTION_TITLE_DURATION_SECONDS * 20);

        final int[] remain = {INTRODUCTION_DURATION};

        introductionTask = scheduler.runTaskTimer(plugin, () -> {
            int elapsed = INTRODUCTION_DURATION - remain[0];
            int section = RuleIntroductionTimeline.sectionAt(elapsed, INTRODUCTION_DURATION, rules.size());
            if (section >= 0) broadcastRuleSection(rules.get(section));

            showPreparationCountdown(remain[0]);

            if (remain[0] == 0) {
                cancelIntroduction();
                clearGameTimerBossBar();
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

    /** A dedicated viewpoint is optional; every playable map already defines a spectator spawn. */
    private Location resolveIntroductionSpawnPoint() {
        Location configured = gameConfig.getIntroductionSpawnPoint();
        return configured != null ? configured : gameConfig.getSpectatorSpawnPoint();
    }

    /** True while participant deaths/reconnects must be restored by the shared pre-game lifecycle. */
    public boolean isSharedPreGameRecoveryPhase() {
        return getGameStageEnum() == GameStageEnum.LOADING
                || (getGameStageEnum() == GameStageEnum.PREPARATION && introductionPhase);
    }

    /** Restores a participant who joins or respawns before game-specific preparation takes ownership. */
    public boolean restoreSharedPreGameParticipant(@NotNull Player player) {
        GameStageEnum stage = getGameStageEnum();
        if (stage == GameStageEnum.LOADING) {
            applyIntroductionGameMode(player);
            player.setFallDistance(0f);
            player.setFireTicks(0);
            Location lobby = getLobbyLocation();
            // A reconnecting participant normally already stands in the lobby where they left it; only
            // pull them to the lobby spawn when they are somewhere else entirely.
            if (lobby != null && lobby.getWorld() != null && !player.getWorld().equals(lobby.getWorld()))
                player.teleport(lobby);
            return true;
        }
        if (stage == GameStageEnum.PREPARATION && introductionPhase) {
            Location introductionSpawnPoint = resolveIntroductionSpawnPoint();
            if (introductionSpawnPoint == null)
                return false;
            player.setGameMode(GameMode.ADVENTURE);
            player.setFlying(false);
            player.setAllowFlight(false);
            player.setFallDistance(0f);
            player.setFireTicks(0);
            player.teleport(introductionSpawnPoint);
            return true;
        }
        return false;
    }

    private void applyIntroductionGameModeToAllParticipants() {
        GameMode mode = gameConfig.getIntroductionGameMode();
        changeGameModelForAllGamePlayers(mode);
        if (mode != GameMode.ADVENTURE) return;
        for (UUID uuid : getParticipantUniqueIds()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.setFlying(false);
                player.setAllowFlight(false);
            }
        }
    }

    private void applyIntroductionGameMode(@NotNull Player player) {
        GameMode mode = gameConfig.getIntroductionGameMode();
        player.setGameMode(mode);
        if (mode == GameMode.ADVENTURE) {
            player.setFlying(false);
            player.setAllowFlight(false);
        }
    }

    private void broadcastRuleSection(@NotNull List<String> lines) {
        for (String line : lines)
            sendMessageToAllGamePlayers(Utils.translateColorCodes(line));
        playSoundToAllGamePlayers(Sound.BLOCK_NOTE_BLOCK_PLING, 1, 1F);
    }

    /** Sends one title to this instance's participants and spectators with an exact stay duration. */
    private void sendTimedTitleToAllGamePlayers(String title, String subtitle, int stayTicks) {
        Set<UUID> viewers = new LinkedHashSet<>(getParticipantUniqueIds());
        viewers.addAll(spectators);
        for (UUID uuid : viewers) {
            ChampionshipPlayer championshipPlayer = playerManager.getPlayer(uuid);
            if (championshipPlayer != null)
                championshipPlayer.sendTitle(title, subtitle, stayTicks);
        }
    }

    /** One durable chat line at preparation; later phase changes stay out of chat. */
    protected void announceGamePreparation(String message, String title, String subtitle) {
        sendMessageToAllGamePlayers(message);
        sendTitleToAllGamePlayers(title, subtitle);
        logGame(Level.INFO, "流程", "进入场地准备");
    }

    protected void showPreparationCountdown(int seconds) {
        int remaining = Math.max(0, seconds);
        if (preparationCountdownDuration <= 0 || remaining > preparationCountdownDuration)
            preparationCountdownDuration = Math.max(1, remaining);
        updateGameTimerBossBar(MessageConfig.GAME_PREPARATION_COUNT_DOWN
                .replace("%game%", gameTypeEnum.toString())
                .replace("%time%", String.valueOf(remaining)), remaining, preparationCountdownDuration);
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
        clearGameTimerBossBar();
        changeLevelForAllGamePlayers(0);
        setGameStageEnum(GameStageEnum.COUNTDOWN);
        int duration = Math.max(0, countdownSeconds);
        countdownBlockDisappearance.start(duration);
        logGame(Level.INFO, "流程", "开始 " + duration + " 秒开赛倒计时");
        final int[] remaining = {duration};

        finalCountdownTask = scheduler.runTaskTimer(plugin, () -> {
            int seconds = remaining[0];
            if (seconds > 0) {
                String title = MessageConfig.GAME_START_COUNT_DOWN_TITLE
                        .replace("%time%", String.valueOf(seconds));
                String subtitle = getFinalCountdownSubtitle(gameTitle);
                sendTitleToAllGamePlayers(title, subtitle);
                playCountdownBit(BIT_C4);
                remaining[0]--;
                return;
            }

            if (finalCountdownTask != null)
                finalCountdownTask.cancel();
            finalCountdownTask = null;
            if (getGameStageEnum() != GameStageEnum.COUNTDOWN)
                return;

            setGameStageEnum(GameStageEnum.PROGRESS);
            onStart.run();
            if (getGameStageEnum() == GameStageEnum.PROGRESS) {
                announceGameStart(startTitle, startSubtitle);
                playCountdownBit(BIT_C5);
            }
        }, 0L, 20L);
    }

    /** Optional block-disappearance selection for this instance, translated by replica subclasses. */
    protected Vector[] getCountdownBlockDisappearanceBounds() {
        if (gameTypeEnum != GameTypeEnum.TGTTOS) return null;
        if (!gameConfig.hasCountdownBlockDisappearance()) return null;
        return new Vector[]{gameConfig.getCountdownBlockDisappearancePos1().clone(),
                gameConfig.getCountdownBlockDisappearancePos2().clone()};
    }

    /**
     * A live remaining-time clock with an exact endpoint: duration is rendered at T0, the first decrement
     * occurs at T0+20 ticks, and zero/onEnd occur at T0+duration*20 ticks.
     */
    protected BukkitTask startRemainingTimer(int durationSeconds, @NotNull IntConsumer onTick,
                                             @NotNull Runnable onEnd) {
        final int[] remaining = {Math.max(0, durationSeconds)};
        onTick.accept(remaining[0]);
        if (remaining[0] == 0) {
            onEnd.run();
            return null;
        }

        BukkitTask[] taskHolder = new BukkitTask[1];
        taskHolder[0] = scheduler.runTaskTimer(plugin, () -> {
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
        countdownBlockDisappearance.cancel();
    }

    protected String getFinalCountdownSubtitle(String gameTitle) {
        return MessageConfig.GAME_START_COUNT_DOWN_SUBTITLE.replace("%game%", gameTitle);
    }

    private void playCountdownBit(Note note) {
        playNoteToAllGamePlayers(Instrument.BIT, note);
        for (Player spectator : getOnlineSpectators()) {
            spectator.playNote(spectator.getLocation(), Instrument.BIT, note);
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
        if (settlementSuppressed) {
            logGame(Level.INFO, "流程", "本场已作废，不执行结算公告");
            return;
        }
        sendActionBarToAllGamePlayers(MessageConfig.GAME_END_ACTION_BAR
                .replace("%game%", gameTypeEnum.toString()));
        boolean hasNextRound = isEventRun() && plugin.getScheduleManager() != null
                && plugin.getScheduleManager().hasNextRound(gameTypeEnum);
        String completionTitle = hasNextRound
                ? MessageConfig.GAME_ROUND_COMPLETE_TITLE
                : MessageConfig.GAME_ROUND_END_TITLE;
        sendTitleToAllGamePlayers(completionTitle, subtitle);
        logGame(Level.INFO, "流程", "游戏结束，开始结算");
    }

    /** Emits a normal completion event only when this run is actually being settled. */
    protected final void publishGameEndEvent(@NotNull Event event) {
        if (!settlementSuppressed) Bukkit.getPluginManager().callEvent(event);
    }

    /** Shared guard for specialised settlement implementations such as paired-team scoring. */
    protected final boolean isSettlementAllowed() {
        return !settlementSuppressed;
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
        Location introductionSpawnPoint = resolveIntroductionSpawnPoint();
        if (introductionPhase && introductionSpawnPoint != null)
            return introductionSpawnPoint;
        return fallback;
    }

    public boolean isSpectator(@NotNull Player player) {
        return spectators.contains(player.getUniqueId());
    }

    /** Unified spectator identity for both arena spectators and eliminated participants. */
    public boolean isManagedSpectator(@NotNull Player player) {
        UUID uuid = player.getUniqueId();
        return plugin.getGameManager().getSpectatorManager().isSpectatorLike(uuid)
                && plugin.getGameManager().getSpectatorManager().areaOf(uuid) == this;
    }

    public void handleSpectatorDeath(@NotNull PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (isSpectator(player)) {
            event.setDroppedExp(0);
            event.getDrops().clear();
            scheduler.runTask(plugin, () -> {
                event.getEntity().spigot().respawn();
                removeSpectator(player);
            });
        }
    }

    public void handleSpectatorJoin(@NotNull PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (isSpectator(player)) {
            teleportSpectatorAsync(player, getSpectatorSpawnLocation());
        }
    }

    public void teleportAllSpectators(@NotNull Location location) {
        for (Player player : getOnlineSpectators()) {
            teleportSpectatorAsync(player, location);
        }
    }

    public void addSpectator(@NotNull Player player) {
        addSpectator(player, getSpectatorSpawnLocation());
    }

    /** Adds a spectator and sends them directly to an explicitly selected location in this instance. */
    public void addSpectator(@NotNull Player player, @NotNull Location location) {
        spectators.add(player.getUniqueId());
        teleportSpectatorAsync(player, location);
    }

    /** Retains an offline spectator for a direct hand-off to a later event-round instance. */
    public void addSpectatorWithoutTeleport(@NotNull UUID uuid) {
        spectators.add(uuid);
    }

    /** Snapshot used by the schedule coordinator to carry spectators through an event-round transition. */
    public Set<UUID> getSpectatorUniqueIds() {
        return Set.copyOf(spectators);
    }

    /** Loads the destination chunk without blocking the server thread, then applies spectator state. */
    public void teleportSpectatorAsync(@NotNull Player player, @NotNull Location location) {
        UUID uuid = player.getUniqueId();
        player.teleportAsync(location).whenComplete((success, error) -> scheduler.runTask(plugin, () -> {
            if (!plugin.isLoaded() || !player.isOnline() || !spectators.contains(uuid)) return;
            if (error != null || !Boolean.TRUE.equals(success)) {
                spectators.remove(uuid);
                plugin.getGameManager().clearSpectatorStatus(uuid, this);
                logGame(Level.WARNING, "观战", "异步传送失败，已清除观战状态 | 玩家=" + player.getName()
                        + (error == null ? "" : " | " + error.getMessage()));
                return;
            }
            applySpectatorGameMode(player);
        }));
    }

    /** Applies the common spectator presentation; area subclasses may add game-specific overlays. */
    protected void applySpectatorGameMode(@NotNull Player player) {
        player.setGameMode(GameMode.ADVENTURE);
        player.setAllowFlight(true);
        player.setFlying(true);
        player.setInvulnerable(true);
        player.setCollidable(false);
    }

    /** Spectator-mode entry point invoked by the spectator manager on behalf of area-level requests. */
    public final void applyManagedSpectatorPresentation(@NotNull Player player) {
        applySpectatorGameMode(player);
    }

    /** Restores any per-game spectator state before the player leaves this game. */
    protected void clearSpectatorGameMode(@NotNull Player player) {
        player.setFlying(false);
        player.setAllowFlight(false);
        player.setInvulnerable(false);
        player.setCollidable(true);
    }

    public void removeAllSpectator() {
        releaseAllSpectators();
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
        for (UUID uuid : ids)
            plugin.getGameManager().clearSpectatorStatus(uuid, this);
    }

    /** Detaches a spectator for a transfer to another live game without an intermediate lobby teleport. */
    public void detachSpectator(@NotNull Player player) {
        UUID uuid = player.getUniqueId();
        if (!spectators.remove(uuid)) return;
        removePlayerFromBossBars(player);
        clearSpectatorGameMode(player);
        player.setLevel(0);
    }

    public void endGameFinally() {
        forceTerminalEnd = true;
        try {
            if (getGameStageEnum() == GameStageEnum.END && postGamePending) {
                completePostGame(false);
                return;
            }
            cancelIntroduction();
            cancelFinalCountdown();
            clearBossBars();
            removeAllSpectator();
            removeAllPlayers();
            endGame();
        } finally {
            forceTerminalEnd = false;
        }
    }

    /**
     * Cancels a partial run and restores its arena without awarding points or publishing completion
     * events. The returned future completes only after an asynchronous template/world restore has
     * finished and the instance is ready again.
     */
    public CompletableFuture<Boolean> abortAndReset() {
        if (!Bukkit.isPrimaryThread()) return runOnMain(this::abortAndReset);
        if (disposed) return CompletableFuture.completedFuture(false);
        if (getGameStageEnum() == GameStageEnum.WAITING)
            return CompletableFuture.completedFuture(true);
        if (!activeMapLoad.isDone()) return activeMapLoad.thenApply(Boolean.TRUE::equals);

        settlementSuppressed = true;
        try {
            endGameFinally();
            // A few implementations legitimately ignore END. If there is no reset already in flight,
            // force the common reset so an interrupted transition cannot remain stuck in END.
            if (getGameStageEnum() != GameStageEnum.WAITING && activeMapLoad.isDone()) resetGame();
        } finally {
            settlementSuppressed = false;
        }

        CompletableFuture<Boolean> completion = activeMapLoad;
        return completion.thenApply(success -> Boolean.TRUE.equals(success)
                && getGameStageEnum() == GameStageEnum.WAITING);
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
            player.teleport(getScatteredLobbyLocation(player));
            ChampionshipsCore championshipsCore = ChampionshipsCore.getInstance();
            championshipsCore.getServer().getScheduler().runTask(championshipsCore, () -> {
                clearSpectatorGameMode(player);
                player.setGameMode(GameMode.ADVENTURE);
                championshipsCore.getGameManager().getSpectatorManager().leavePresentation(player);
            });
            player.setLevel(0);
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
            player.sendMessage(message);
        }
    }

    public void sendActionBarToAllSpectators(String message) {
        for (ChampionshipPlayer championshipPlayer : getOnlineCCSpectators()) {
            championshipPlayer.sendActionBar(message);
        }
    }

    public void changeLevelToAllSpectators(int level) {
        for (Player player : getOnlineSpectators()) {
            player.setLevel(Math.abs(level));
        }
    }

    public void sendTitleToAllSpectators(String title, String subTitle) {
        for (ChampionshipPlayer championshipPlayer : getOnlineCCSpectators()) {
            championshipPlayer.sendTitle(title, subTitle);
        }
    }

    public void cleanDroppedItems() {
        Vector pos1 = getGameConfig().getAreaPos1();
        Vector pos2 = getGameConfig().getAreaPos2();
        World world = getSpectatorSpawnLocation().getWorld();
        if (world != null) {
            world.getNearbyEntities(new BoundingBox(
                            pos1.getX(),
                            pos1.getY(),
                            pos1.getZ(),
                            pos2.getX(),
                            pos2.getY(),
                            pos2.getZ()))
                    .forEach(entity -> {
                        if (entity instanceof Item) {
                            entity.remove();
                        }
                    });
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

    /**
     * Location used by the admin world-teleport command when this instance owns the target world.
     * Replicated games can override this to expose their copy-0 anchor without changing the normal
     * spectator routing behavior.
     */
    public Location getAdminTeleportLocation() {
        Location configured = getGameConfig().getGameSpawnPoint();
        return configured != null ? configured : getSpectatorSpawnLocation();
    }

    /** Copy ordering used when a physical world contains several replicated instances. */
    public int getCopyIndex() {
        return 0;
    }

    public abstract int getTimer();

    public abstract void endGame();

    public abstract void resetBaseArea();

    public abstract void resetArea();

    public abstract BaseGameConfig getGameConfig();

    public abstract BaseListener getGameHandler();

    public abstract String getWorldName();

    public abstract void removeAllPlayers();

    /** Stable snapshot of every participant assigned to the current run. */
    public abstract Collection<UUID> getParticipantUniqueIds();

    public abstract void startGamePreparation();

    public abstract void sendMessageToAllGamePlayers(String message);

    public abstract void sendActionBarToAllGamePlayers(String message);

    protected abstract Collection<Player> getOnlineParticipantSpectators();

    public abstract void sendTitleToAllGamePlayers(String title, String subTitle);

    public abstract void changeLevelForAllGamePlayers(int level);

    public abstract void changeGameModelForAllGamePlayers(GameMode gameMode);

    public abstract void setHealthForAllGamePlayers(double health);

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
