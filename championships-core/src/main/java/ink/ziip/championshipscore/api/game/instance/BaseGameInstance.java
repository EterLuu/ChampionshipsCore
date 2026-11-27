package ink.ziip.championshipscore.api.game.instance;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseListener;
import ink.ziip.championshipscore.api.game.arena.ArenaChunkPreloader;
import ink.ziip.championshipscore.api.game.config.BaseGameConfig;
import ink.ziip.championshipscore.api.game.manager.BaseGameInstanceManager;
import ink.ziip.championshipscore.api.game.model.GameRunMode;
import ink.ziip.championshipscore.api.game.model.GameStageEnum;
import ink.ziip.championshipscore.api.game.model.GameTypeEnum;
import ink.ziip.championshipscore.api.game.spatial.LobbyReturnLocations;
import ink.ziip.championshipscore.api.game.start.ArenaSelection;
import ink.ziip.championshipscore.api.game.start.GameExecutionContext;
import ink.ziip.championshipscore.api.player.ChampionshipPlayer;
import ink.ziip.championshipscore.api.player.PlayerManager;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.CCConfig;
import ink.ziip.championshipscore.configuration.config.message.GameMessages;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.logging.LogText;
import ink.ziip.championshipscore.platform.bukkit.player.PlayerStateService;
import ink.ziip.championshipscore.platform.bukkit.text.LegacyText;

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
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
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
    private final Set<ArenaChunkPreloader.ChunkTicket> startChunkTickets =
            ConcurrentHashMap.newKeySet();
    private volatile CompletableFuture<Void> startPreloadFuture =
            CompletableFuture.completedFuture(null);
    private boolean roundTransitionPending;
    private boolean forceTerminalEnd;
    private boolean settlementSuppressed;
    private GameRunMode runMode = GameRunMode.GAME;
    private GameExecutionContext executionContext =
            GameExecutionContext.forRunMode(GameRunMode.GAME);
    private Consumer<BaseGameInstance> testCompletionListener;
    private ArenaSelection arenaSelection = ArenaSelection.all();
    private volatile long startGeneration;
    private boolean postGamePending;
    private boolean postGameFinalizing;
    private volatile CompletableFuture<Void> coordinatedStartGate;
    private final GameMapLifecycle mapLifecycle;
    private volatile boolean disposed;
    private ParticipantCallbacks participantCallbacks;

    /**
     * Duration (seconds) of the optional rule-introduction phase preceding the normal preparation.
     */
    protected static final int INTRODUCTION_DURATION = 90;

    /** Allocated when this instance first arms or starts preparation. */
    private volatile GamePreparation preparation;

    final CountdownBlockDisappearance countdownBlockDisappearance;
    private static final long POST_GAME_RESULT_DISPLAY_TICKS = 200L;
    private BukkitTask postGameRoutingTask;

    protected BaseListener gameHandler;
    protected BaseGameConfig gameConfig;

    protected GameStageEnum gameStageEnum;
    protected GameTypeEnum gameTypeEnum;

    public BaseGameInstance(
            ChampionshipsCore plugin,
            GameTypeEnum gameTypeEnum,
            BaseListener gameHandler,
            BaseGameConfig gameConfig) {
        this.playerManager = plugin.getPlayerManager();

        this.gameStageEnum = GameStageEnum.END;
        this.plugin = plugin;
        this.scheduler = plugin.getServer().getScheduler();
        this.gameTypeEnum = gameTypeEnum;

        this.gameHandler = gameHandler;
        gameHandler.bindGameInstance(this);
        this.gameConfig = gameConfig;
        this.countdownBlockDisappearance = new CountdownBlockDisappearance(plugin, this);
        this.mapLifecycle = new GameMapLifecycle(this);

        gameInstanceHandler = new GameInstanceHandler(plugin, this);
        gameInstanceHandler.register();
    }

    public void resetGame() {
        invalidateParticipantCallbacks();
        if (startPreloadFuture != null && !startPreloadFuture.isDone())
            startPreloadFuture.complete(null);
        coordinatedStartGate = null;
        arenaSelection = ArenaSelection.all();
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
        logGame(
                Level.INFO,
                MessageConfig.codeText("base-game-instance.reset-game-1"),
                MessageConfig.codeText("base-game-instance.reset-game-2"));
    }

    public final void routePlayerMoveLow(@NotNull PlayerMoveEvent event) {
        gameInstanceHandler.handleRoutedPlayerMoveLow(event);
        if (!plugin.getGameManager()
                .getSpectatorManager()
                .isSpectatorLike(event.getPlayer().getUniqueId()))
            gameHandler.handleRoutedPlayerMoveLow(event);
    }

    public final void routePlayerMoveNormal(@NotNull PlayerMoveEvent event) {
        if (!plugin.getGameManager()
                .getSpectatorManager()
                .isSpectatorLike(event.getPlayer().getUniqueId()))
            gameHandler.handleRoutedPlayerMoveNormal(event);
    }

    public final void routePlayerMoveHigh(@NotNull PlayerMoveEvent event) {
        if (!plugin.getGameManager()
                .getSpectatorManager()
                .isSpectatorLike(event.getPlayer().getUniqueId()))
            gameHandler.handleRoutedPlayerMoveHigh(event);
    }

    /** Permanently releases listeners and UI owned by this instance when its manager unloads it. */
    public void dispose() {
        invalidateParticipantCallbacks();
        if (startPreloadFuture != null && !startPreloadFuture.isDone())
            startPreloadFuture.complete(null);
        arenaSelection = ArenaSelection.all();
        disposed = true;
        mapLifecycle.dispose();
        releaseStartChunks();
        cancelIntroduction();
        cancelFinalCountdown();
        countdownBlockDisappearance.restore();
        cancelPostGameRouting();
        clearBossBars();
        getGameHandler().unRegister();
        gameInstanceHandler.unRegister();
    }

    /**
     * Landing points that must be warm before preparation starts. Games with replicas override
     * this.
     */
    protected Collection<Location> getStartPreloadLocations() {
        return List.of();
    }

    /**
     * Starts preparation only after all landing chunks are loaded and ticketed. Must be called on
     * main.
     */
    protected final void startGamePreparationAfterPreload() {
        long generation = ++startGeneration;
        releaseStartChunks();
        List<Location> locations = new ArrayList<>(getStartPreloadLocations());
        Location introductionSpawnPoint = resolveIntroductionSpawnPoint();
        if (introductionSpawnPoint != null) locations.add(introductionSpawnPoint);
        AtomicReference<Throwable> preloadError = new AtomicReference<>();
        CompletableFuture<Void> preload;
        if (locations.isEmpty()) {
            preload = CompletableFuture.completedFuture(null);
        } else {
            logGame(
                    Level.INFO,
                    MessageConfig.codeText(
                            "base-game-instance.start-game-preparation-after-preload-1"),
                    MessageConfig.codeText(
                            "base-game-instance.start-game-preparation-after-preload-2",
                            locations.size()));
            preload =
                    ArenaChunkPreloader.preload(
                                    plugin,
                                    locations,
                                    1,
                                    startChunkTickets,
                                    () ->
                                            generation == startGeneration
                                                    && !disposed
                                                    && getGameStageEnum() == GameStageEnum.LOADING)
                            .exceptionally(
                                    error -> {
                                        preloadError.set(error);
                                        return null;
                                    });
        }
        startPreloadFuture = preload;
        CompletableFuture<Void> gate = coordinatedStartGate;
        CompletableFuture<Void> ready =
                gate == null ? preload : preload.thenCompose(unused -> gate);
        ready.whenComplete(
                (unused, ignored) ->
                        scheduler.runTask(
                                plugin,
                                () -> {
                                    if (generation != startGeneration || disposed) return;
                                    if (!plugin.isLoaded()
                                            || getGameStageEnum() != GameStageEnum.LOADING) {
                                        releaseStartChunks();
                                        return;
                                    }
                                    Throwable error = preloadError.get();
                                    if (error != null)
                                        logGame(
                                                Level.WARNING,
                                                MessageConfig.codeText(
                                                        "base-game-instance.start-game-preparation-after-preload-1"),
                                                MessageConfig.codeText(
                                                        "base-game-instance.start-game-preparation-after-preload-4",
                                                        error.getMessage()));
                                    else if (!locations.isEmpty())
                                        logGame(
                                                Level.INFO,
                                                MessageConfig.codeText(
                                                        "base-game-instance.start-game-preparation-after-preload-1"),
                                                MessageConfig.codeText(
                                                        "base-game-instance.start-game-preparation-after-preload-3",
                                                        startChunkTickets.size()));
                                    coordinatedStartGate = null;
                                    // Every game enters preparation through this common gate. Reset
                                    // the actual
                                    // participants here so an event cannot carry inventory,
                                    // experience, effects,
                                    // or hazardous player state from the lobby or a previous game
                                    // into its start.
                                    resetPlayerHealthFoodEffectLevelInventory();
                                    startGamePreparation();
                                }));
    }

    public final void coordinateStartWith(@Nullable CompletableFuture<Void> gate) {
        coordinatedStartGate = gate;
    }

    /** Selects physical sub-arenas for the next run without changing its shared map definition. */
    public final void prepareArenaSelection(@NotNull ArenaSelection selection) {
        if (getGameStageEnum() != GameStageEnum.WAITING) {
            throw new IllegalStateException(
                    MessageConfig.codeText("base-game-instance.prepare-arena-selection-1"));
        }
        arenaSelection = Objects.requireNonNull(selection);
    }

    public final @NotNull List<Integer> getSelectedArenaIndices(int arenaCount) {
        return (arenaSelection == null ? ArenaSelection.all() : arenaSelection).resolve(arenaCount);
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
        for (UUID uuid : getParticipantUniqueIds()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) continue;
            // The bulk methods above reset the common fields. These direct operations complete
            // partial team starts and clear the exp bar, total exp, and transient hazards they do
            // not cover.
            PlayerStateService.resetExperience(player);
            PlayerStateService.resetVitals(player);
        }
    }

    public void addPlayerPoints(UUID uuid, double points) {
        if (uuid == null) {
            logGame(
                    Level.WARNING,
                    MessageConfig.codeText("base-game-instance.add-player-points-1"),
                    MessageConfig.codeText(
                            "base-game-instance.add-player-points-2", formatPointChange(points)));
            return;
        }
        playerPoints.merge(uuid, points, Double::sum);
        logGame(
                Level.INFO,
                MessageConfig.codeText("base-game-instance.add-player-points-1"),
                MessageConfig.codeText(
                        "base-game-instance.add-player-points-3",
                        plugin.getPlayerManager().getPlayerName(uuid),
                        uuid,
                        formatPointChange(points)));
        ChampionshipPlayer championshipPlayer = plugin.getPlayerManager().getPlayer(uuid);
        if (championshipPlayer != null)
            championshipPlayer.sendActionBar(
                    MessageConfig.codeText("base-game-instance.points-actionbar", points));
        Player online = Bukkit.getPlayer(uuid);
        if (online != null && plugin.getSidebarManager() != null)
            plugin.getSidebarManager().invalidate(online);
    }

    public void addPlayerPointsToAllTeamMembers(ChampionshipTeam championshipTeam, int points) {
        for (UUID uuid : championshipTeam.getMembers()) {
            playerPoints.merge(uuid, (double) points, Double::sum);
            logGame(
                    Level.INFO,
                    MessageConfig.codeText("base-game-instance.add-player-points-1"),
                    MessageConfig.codeText(
                            "base-game-instance.add-player-points-3",
                            plugin.getPlayerManager().getPlayerName(uuid),
                            uuid,
                            formatPointChange(points)));
            Player online = Bukkit.getPlayer(uuid);
            if (online != null && plugin.getSidebarManager() != null)
                plugin.getSidebarManager().invalidate(online);
        }
    }

    /** Immutable result snapshot for non-formal settlement layers such as DAILY. */
    public Map<UUID, Double> getPlayerPointsSnapshot() {
        return Map.copyOf(playerPoints);
    }

    protected void logGame(Level level, String event, String message) {
        String area =
                gameConfig == null || gameConfig.getAreaName() == null
                        ? "-"
                        : gameConfig.getAreaName();
        String stage = gameStageEnum == null ? "-" : gameStageEnum.name();
        String formatted = LogText.formatGameLog(gameTypeEnum, area, stage, event, message);
        boolean importantFlow =
                Level.INFO.equals(level)
                        && MessageConfig.codeText("base-game-instance.reset-game-1").equals(event)
                        && (message.startsWith(
                                        MessageConfig.codeText("base-game-instance.log-game-1"))
                                || message.startsWith(
                                        MessageConfig.codeText("base-game-instance.log-game-2")));
        if (importantFlow && plugin.getLogManager() != null)
            plugin.getLogManager().important(formatted);
        else plugin.getLogger().log(level, formatted);
    }

    private String formatPointChange(double points) {
        return (points >= 0 ? "+" : "") + LegacyText.formatPoints(points);
    }

    public void addPlayerPointsToDatabase() {
        if (settlementSuppressed || !executionContext.writesSettlement()) return;
        List<ink.ziip.championshipscore.api.rank.RankManager.PointSubmission> submissions =
                new ArrayList<>();
        for (Map.Entry<UUID, Double> playerPointEntry : playerPoints.entrySet()) {
            if (playerPointEntry.getValue() != 0)
                submissions.add(
                        new ink.ziip.championshipscore.api.rank.RankManager.PointSubmission(
                                UUID.randomUUID(),
                                playerPointEntry.getKey(),
                                null,
                                gameTypeEnum,
                                gameConfig.getAreaName(),
                                "scc",
                                playerPointEntry.getValue()));
        }
        plugin.getRankManager()
                .addPlayerPointsBatch(submissions, executionContext.test())
                .whenComplete(
                        (accepted, failure) -> {
                            if (failure != null || !Boolean.TRUE.equals(accepted))
                                logGame(
                                        Level.SEVERE,
                                        MessageConfig.codeText(
                                                "base-game-instance.add-player-points-1"),
                                        MessageConfig.codeText(
                                                "base-game-instance.add-player-points-to-database-1",
                                                failure,
                                                submissions));
                        });
        if (isFormalEventRun()) plugin.getRankManager().refreshAfterPendingPointWrites();
    }

    public double getTeamPoints(ChampionshipTeam championshipTeam) {
        double points = 0D;
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

    public GameExecutionContext getExecutionContext() {
        return executionContext;
    }

    /** Applies the owner/generation after a start surface has selected the concrete instance. */
    public void applyExecutionContext(@NotNull GameExecutionContext context) {
        this.executionContext = context;
    }

    /** Internal Core test hook for advancing a test-owned round after normal completion. */
    public void setTestCompletionListener(
            @org.jetbrains.annotations.Nullable Consumer<BaseGameInstance> listener) {
        this.testCompletionListener = listener;
    }

    public boolean isEventRun() {
        return runMode == GameRunMode.EVENT || runMode == GameRunMode.TEST_EVENT;
    }

    /** True only for the production schedule-owned event lifecycle. */
    public boolean isFormalEventRun() {
        return runMode == GameRunMode.EVENT && executionContext.formalEvent();
    }

    public boolean isTestRun() {
        return runMode == GameRunMode.TEST || runMode == GameRunMode.TEST_EVENT;
    }

    /** Assigned by GameManager immediately before tryStartGame; ordinary game commands use GAME. */
    public void prepareRunMode(@NotNull GameRunMode runMode) {
        if (getGameStageEnum() != GameStageEnum.WAITING)
            throw new IllegalStateException("Cannot change run mode while an instance is active");
        this.runMode = runMode;
        this.executionContext = GameExecutionContext.forRunMode(runMode);
        this.settlementSuppressed = false;
    }

    public void setGameStageEnum(GameStageEnum gameStageEnum) {
        GameStageEnum previous;
        synchronized (this) {
            previous = this.gameStageEnum;
            if (previous != gameStageEnum && !allowedStageTransition(previous, gameStageEnum)) {
                logGame(
                        Level.WARNING,
                        MessageConfig.codeText("base-game-instance.reset-game-1"),
                        MessageConfig.codeText(
                                "base-game-instance.set-game-stage-enum-1",
                                previous,
                                gameStageEnum));
            }
            this.gameStageEnum = gameStageEnum;
        }
        if (plugin.getSidebarManager() != null) plugin.getSidebarManager().invalidateAll();
        if (gameStageEnum == GameStageEnum.PREPARATION
                && previous != GameStageEnum.PREPARATION
                && plugin.getGameManager() != null) {
            plugin.getGameManager().onInstancePreparationStarted(this);
        }
    }

    private static boolean allowedStageTransition(GameStageEnum from, GameStageEnum to) {
        if (from == to) return true;
        return switch (from) {
            case WAITING -> to == GameStageEnum.LOADING || to == GameStageEnum.PREPARATION;
            case LOADING ->
                    to == GameStageEnum.PREPARATION
                            || to == GameStageEnum.END
                            || to == GameStageEnum.WAITING;
            case PREPARATION -> to == GameStageEnum.COUNTDOWN || to == GameStageEnum.END;
            case COUNTDOWN -> to == GameStageEnum.PROGRESS || to == GameStageEnum.END;
            case PROGRESS -> to == GameStageEnum.STOPPING || to == GameStageEnum.END;
            case STOPPING -> to == GameStageEnum.COUNTDOWN || to == GameStageEnum.END;
            case END -> to == GameStageEnum.LOADING || to == GameStageEnum.WAITING;
        };
    }

    public CompletableFuture<Boolean> loadMap(World.Environment environment) {
        return mapLifecycle.loadMap(environment);
    }

    /** Loads the published template, or preserves the editable world of an unpublished map. */
    public final CompletableFuture<Boolean> loadPublishedMapOrDraft(World.Environment environment) {
        return mapLifecycle.loadPublishedMapOrDraft(environment);
    }

    /**
     * True only when every instance backed by this same world is idle and the map can be reloaded
     * safely.
     */
    public boolean canSaveMap() {
        if (getGameStageEnum() != GameStageEnum.WAITING) return false;
        BaseGameInstanceManager<? extends BaseGameInstance> manager =
                plugin.getGameManager().getAreaManager(gameTypeEnum);
        if (manager == null) return true;
        return manager.getRuntimeInstances().stream()
                .filter(instance -> getWorldName().equals(instance.getWorldName()))
                .allMatch(instance -> instance.getGameStageEnum() == GameStageEnum.WAITING);
    }

    public CompletableFuture<Boolean> saveMap(World.Environment environment) {
        return mapLifecycle.saveMap(environment);
    }

    private BossBar createBossBar(String title, BarColor color, BarStyle style) {
        return Bukkit.createBossBar(LegacyText.translateColorCodes(title), color, style);
    }

    public BossBar createBossBar(String name, String title, BarColor color, BarStyle style) {
        BossBar bossBar = createBossBar(title, color, style);
        if (bossBars.containsKey(name)) removeBossBar(name);

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
        for (BossBar bossBar : new ArrayList<>(bossBars.values())) bossBar.removeAll();
        bossBars.clear();
        if (preparation != null) preparation.clearCountdownDuration();
    }

    protected final void removePlayerFromBossBars(Player player) {
        for (BossBar bossBar : bossBars.values()) bossBar.removePlayer(player);
    }

    /**
     * Updates the shared timer bar and synchronizes it to every participant and instance spectator.
     */
    protected void updateGameTimerBossBar(String title, int remainingSeconds, int durationSeconds) {
        double progress = durationSeconds <= 0 ? 0D : remainingSeconds / (double) durationSeconds;
        updateGameTimerBossBar(title, progress);
    }

    protected void updateGameTimerBossBar(
            String title, int remainingSeconds, int durationSeconds, BarColor color) {
        double progress = durationSeconds <= 0 ? 0D : remainingSeconds / (double) durationSeconds;
        updateGameTimerBossBar(title, progress, color);
    }

    protected void updateGameTimerBossBar(String title, double progress, BarColor color) {
        BossBar bossBar =
                bossBars.computeIfAbsent(
                        GAME_TIMER_BOSS_BAR,
                        ignored ->
                                createBossBar(
                                        title,
                                        color == null ? BarColor.YELLOW : color,
                                        BarStyle.SOLID));
        if (color != null && bossBar.getColor() != color) bossBar.setColor(color);
        updateGameTimerBossBar(title, progress);
    }

    /**
     * Variant for non-countdown clocks, where the caller supplies the semantic progress directly.
     */
    protected void updateGameTimerBossBar(String title, double progress) {
        BossBar bossBar =
                bossBars.computeIfAbsent(
                        GAME_TIMER_BOSS_BAR,
                        ignored -> createBossBar(title, BarColor.YELLOW, BarStyle.SOLID));
        bossBar.setTitle(LegacyText.translateColorCodes(title));
        bossBar.setProgress(Math.max(0D, Math.min(1D, progress)));

        Set<Player> viewers = new LinkedHashSet<>();
        for (UUID uuid : getParticipantUniqueIds()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) viewers.add(player);
        }
        viewers.addAll(getOnlineSpectators());
        for (Map.Entry<String, BossBar> entry : bossBars.entrySet()) {
            if (GAME_TIMER_BOSS_BAR.equals(entry.getKey())) continue;
            for (Player viewer : viewers) entry.getValue().removePlayer(viewer);
        }
        for (Player current : new ArrayList<>(bossBar.getPlayers())) {
            if (!viewers.contains(current)) bossBar.removePlayer(current);
        }
        for (Player viewer : viewers) bossBar.addPlayer(viewer);
    }

    protected void clearGameTimerBossBar() {
        removeBossBar(GAME_TIMER_BOSS_BAR);
        if (preparation != null) preparation.clearCountdownDuration();
    }

    public void setBossBar(String name, String title) {
        BossBar bossBar = bossBars.get(name);
        if (bossBar != null) {
            bossBar.setTitle(LegacyText.translateColorCodes(title));
        } else {
            logGame(
                    Level.WARNING,
                    "BossBar",
                    MessageConfig.codeText("base-game-instance.set-boss-bar-1", name));
        }
    }

    public void addBossBarPlayer(String name, Player player) {
        if (player == null) return;

        BossBar bossBar = bossBars.get(name);
        if (bossBar != null) {
            bossBar.addPlayer(player);
        } else {
            logGame(
                    Level.WARNING,
                    "BossBar",
                    MessageConfig.codeText("base-game-instance.set-boss-bar-1", name));
        }
    }

    public void removeBossBarPlayer(String name, Player player) {
        BossBar bossBar = bossBars.get(name);
        if (bossBar != null) {
            bossBar.removePlayer(player);
        } else {
            logGame(
                    Level.WARNING,
                    "BossBar",
                    MessageConfig.codeText("base-game-instance.set-boss-bar-1", name));
        }
    }

    public void setBossBarProgress(String name, double progress) {
        BossBar bossBar = bossBars.get(name);
        if (bossBar != null) {
            bossBar.setProgress(progress);
        } else {
            logGame(
                    Level.WARNING,
                    "BossBar",
                    MessageConfig.codeText("base-game-instance.set-boss-bar-1", name));
        }
    }

    public Location getLobbyLocation() {
        return CCConfig.LOBBY_LOCATION;
    }

    /**
     * Returns a player to their last lobby position, retaining the spawn fallback for new players.
     */
    protected final void teleportToLobby(@NotNull Player player) {
        Location destination = LobbyReturnLocations.resolve(player, getLobbyLocation());
        if (destination != null && destination.getWorld() != null) player.teleport(destination);
    }

    /** True while the visible result phase still owns the participant roster. */
    public boolean isPostGamePending() {
        return postGamePending;
    }

    /**
     * Reconnects during the result window still belong to the arena until settlement releases it.
     */
    public final boolean restorePostGameParticipant(@NotNull Player player) {
        if (!postGamePending
                || getGameStageEnum() != GameStageEnum.END
                || disposed
                || !plugin.isLoaded()
                || Bukkit.getPlayer(player.getUniqueId()) != player
                || plugin.getGameManager().getBasePlayerArea(player.getUniqueId()) != this)
            return false;
        sanitizeParticipantForLobby(player, false);
        restoreArenaWaitingLocation(player);
        return true;
    }

    protected void restoreArenaWaitingLocation(Player player) {
        Location destination = getSpectatorSpawnLocation();
        if (destination != null
                && destination.getWorld() != null
                && !destination.getWorld().equals(player.getWorld())) player.teleport(destination);
    }

    public final boolean isRoundTransitionPending() {
        return roundTransitionPending;
    }

    /** Removes all game state that could leak into the lobby and applies its authoritative mode. */
    public void sanitizeParticipantForLobby(@NotNull Player player, boolean teleport) {
        plugin.getGameManager().getSpectatorManager().resumeParticipant(player, this);
        player.getInventory().clear();
        PlayerStateService.clearEffects(player);
        PlayerStateService.disableFlight(player);
        PlayerStateService.clearHazards(player);
        player.setLevel(0);
        if (teleport) teleportToLobby(player);
        player.setGameMode(GameMode.ADVENTURE);
    }

    /** Opens the result-display phase without releasing players or rebuilding the arena. */
    protected final void beginPostGameSettlement() {
        cancelParticipantCallbacks();
        cancelPostGameRouting();
        postGamePending = true;
        roundTransitionPending = false;
        if (isFormalEventRun() && plugin.getScheduleManager() != null)
            plugin.getScheduleManager().registerPendingEventInstance(this);
    }

    /**
     * Called after the synchronous end event has given an event coordinator a chance to take
     * ownership.
     */
    protected final void finishPostGameAfterEndEvent() {
        if (forceTerminalEnd) {
            completePostGame(false);
            return;
        }
        if (isFormalEventRun()) {
            plugin.getScheduleManager().onEventInstanceReady(this);
            return;
        }
        postGameRoutingTask =
                scheduler.runTaskLater(
                        plugin, () -> completePostGame(false), POST_GAME_RESULT_DISPLAY_TICKS);
    }

    /** Releases one finished run only after its visible result phase. */
    public final void completePostGame(boolean nextEventRound) {
        if (!postGamePending || postGameFinalizing) return;
        postGameFinalizing = true;
        // Normal event settlement removes the queue entry before this callback; force-stop
        // finalization reaches here directly, so make both paths clean up identically.
        if (plugin.getScheduleManager() != null)
            plugin.getScheduleManager().unregisterPendingEventInstance(this);
        cancelPostGameRouting();
        roundTransitionPending = nextEventRound;
        onPostGameSettlement(nextEventRound);

        Consumer<BaseGameInstance> testCompletion = testCompletionListener;
        if (testCompletion != null) {
            try {
                testCompletion.accept(this);
            } catch (RuntimeException failure) {
                logGame(
                        Level.SEVERE,
                        MessageConfig.codeText("base-game-instance.reset-game-1"),
                        "TEST completion callback failed: " + failure.getMessage());
            }
        }

        List<UUID> participantIds = List.copyOf(getParticipantUniqueIds());
        if (nextEventRound) {
            plugin.getGameManager().holdParticipantsForNextRound(this, participantIds);
            plugin.getGameManager().holdSpectatorsForNextRound(this);
        } else {
            for (UUID uuid : participantIds) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null && player.isOnline()) sanitizeParticipantForLobby(player, true);
            }
        }

        if (!nextEventRound) releaseAllSpectators();
        plugin.getGameManager().releaseInstanceParticipants(this);
        try {
            countdownBlockDisappearance.restore();
            resetGame();
            // A DAILY session remains reserved through the result phase. Only after the instance
            // has
            // released its player status and reset can its players receive the lobby entry item
            // again.
            plugin.getDailyManager().onInstanceReturnedToLobby(this);
        } finally {
            roundTransitionPending = false;
            postGamePending = false;
            postGameFinalizing = false;
            testCompletionListener = null;
            runMode = GameRunMode.GAME;
            executionContext = GameExecutionContext.forRunMode(GameRunMode.GAME);
            settlementSuppressed = false;
        }
    }

    /** Execution backends release their players only when Core settles the complete flow. */
    protected void onPostGameSettlement(boolean nextEventRound) {}

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
        return preparation != null && preparation.isIntroductionActive();
    }

    /** Whether participants should be held in place during the final five-second countdown. */
    public boolean freezeMovementDuringCountdown() {
        return true;
    }

    public void setIntroductionEnabledForNextStart(boolean enabled) {
        preparation().setIntroductionEnabled(enabled);
    }

    /**
     * Runs the optional rule-introduction phase. When the area config provides at least one rule
     * section, every player is teleported to its introduction spawn, falling back to the spectator
     * spawn when no dedicated point is configured. Players remain in PREPARATION and use adventure
     * mode while the rule sections are broadcast one at a time in chat over {@link
     * #getIntroductionDurationSeconds()} seconds; afterwards {@code onComplete} (the normal
     * preparation: spawn assignment + countdown) runs. Without rules the introduction is skipped
     * and {@code onComplete} runs immediately.
     */
    protected void startGameIntroduction(@NotNull Runnable onComplete) {
        preparation().startIntroduction(onComplete);
    }

    private GamePreparation preparation() {
        if (preparation == null) preparation = new GamePreparation(this);
        return preparation;
    }

    /**
     * Variant-aware games override this without coupling the base lifecycle to a concrete config
     * model.
     */
    protected List<List<String>> getIntroductionRules() {
        return gameConfig.getRules();
    }

    /** Duration is captured by the shared preparation request, including interactive lessons. */
    protected int getIntroductionDurationSeconds() {
        return INTRODUCTION_DURATION;
    }

    /** Interactive lessons own mechanic cues; lobby rules need not be repeated at the arena. */
    protected boolean isInteractiveIntroduction() {
        return false;
    }

    /** Games may keep their participants in managed spectator mode during an event hand-off. */
    public boolean retainsParticipantSpectatorsForNextRound() {
        return false;
    }

    /**
     * Background preparation also reserves a waiting instance for lifecycle and reload handling.
     */
    public boolean hasPendingPreparation() {
        return false;
    }

    public void prepareParticipantForNextRound(@NotNull Player player) {
        sanitizeParticipantForLobby(player, false);
        restoreArenaWaitingLocation(player);
    }

    /** A dedicated viewpoint is optional; every playable map already defines a spectator spawn. */
    protected Location resolveIntroductionSpawnPoint() {
        Location configured = gameConfig.getIntroductionSpawnPoint();
        if (configured == null) configured = gameConfig.getSpectatorSpawnPoint();
        if (configured == null || configured.getWorld() != null) return configured;

        // Map configuration can be parsed before its physical world is loaded (LaserBox and
        // other managers intentionally do this during startup). Bind the raw coordinates at the
        // last possible point before chunk preloading or teleportation so Bukkit never receives a
        // Location whose target world is null.
        World world = Bukkit.getWorld(getWorldName());
        if (world == null) return null;
        Location rebound = configured.clone();
        rebound.setWorld(world);
        return rebound;
    }

    /**
     * True while participant deaths/reconnects must be restored by the shared pre-game lifecycle.
     */
    public boolean isSharedPreGameRecoveryPhase() {
        return getGameStageEnum() == GameStageEnum.LOADING
                || (getGameStageEnum() == GameStageEnum.PREPARATION && isIntroductionPhase());
    }

    /**
     * Restores a participant who joins or respawns before game-specific preparation takes
     * ownership.
     */
    public boolean restoreSharedPreGameParticipant(@NotNull Player player) {
        GameStageEnum stage = getGameStageEnum();
        if (stage == GameStageEnum.LOADING) {
            applyIntroductionGameMode(player);
            player.setFallDistance(0f);
            player.setFireTicks(0);
            // Loading may be the next round of an event. Keep the saved location until the
            // prepared arena takes over instead of taking a detour through the lobby.
            return true;
        }
        if (stage == GameStageEnum.PREPARATION && isIntroductionPhase()) {
            Location introductionSpawnPoint = resolveIntroductionSpawnPoint();
            if (introductionSpawnPoint == null) return false;
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

    void applyIntroductionGameModeToAllParticipants() {
        for (UUID uuid : getParticipantUniqueIds()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) applyIntroductionGameMode(player);
        }
    }

    private void applyIntroductionGameMode(@NotNull Player player) {
        GameMode mode = gameConfig.getIntroductionGameMode();
        if (mode == GameMode.SPECTATOR) {
            plugin.getGameManager().getSpectatorManager().enterParticipantSpectator(player, this);
            return;
        }
        player.setGameMode(mode);
        if (mode == GameMode.ADVENTURE) {
            player.setFlying(false);
            player.setAllowFlight(false);
        }
    }

    void broadcastRuleSection(@NotNull List<String> lines) {
        for (String line : lines) sendMessageToAllGamePlayers(LegacyText.translateColorCodes(line));
        playSoundToAllGamePlayers(Sound.BLOCK_NOTE_BLOCK_PLING, 1, 1F);
    }

    /**
     * Sends one title to this instance's participants and spectators with an exact stay duration.
     */
    void sendTimedTitleToAllGamePlayers(String title, String subtitle, int stayTicks) {
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
        logGame(
                Level.INFO,
                MessageConfig.codeText("base-game-instance.reset-game-1"),
                MessageConfig.codeText("base-game-instance.announce-game-preparation-1"));
    }

    protected void showPreparationCountdown(int seconds) {
        preparation().showCountdown(seconds);
    }

    /** Runs the default five-second final countdown. */
    protected void startFinalCountdown(
            String gameTitle, String startTitle, String startSubtitle, @NotNull Runnable onStart) {
        startFinalCountdown(5, gameTitle, startTitle, startSubtitle, onStart);
    }

    /**
     * Runs the authoritative final countdown. The supplied callback starts live game systems at T0;
     * the stage transition, start title and high cue all happen in that same server tick.
     */
    protected void startFinalCountdown(
            int countdownSeconds,
            String gameTitle,
            String startTitle,
            String startSubtitle,
            @NotNull Runnable onStart) {
        startFinalCountdown(countdownSeconds, gameTitle, startTitle, startSubtitle, true, onStart);
    }

    /** Runs a final countdown with optional per-second and start cues. */
    protected void startFinalCountdown(
            int countdownSeconds,
            String gameTitle,
            String startTitle,
            String startSubtitle,
            boolean playCountdownSound,
            @NotNull Runnable onStart) {
        startFinalCountdown(
                countdownSeconds,
                gameTitle,
                startTitle,
                startSubtitle,
                playCountdownSound,
                true,
                onStart);
    }

    /**
     * Runs a countdown that can hand off to another countdown without entering live progress.
     * Selection windows use this so the formal start countdown remains the only transition into
     * {@link GameStageEnum#PROGRESS}.
     */
    protected void startFinalCountdown(
            int countdownSeconds,
            String gameTitle,
            String startTitle,
            String startSubtitle,
            boolean playCountdownSound,
            boolean transitionToProgress,
            @NotNull Runnable onStart) {
        preparation()
                .startCountdown(
                        countdownSeconds,
                        gameTitle,
                        startTitle,
                        startSubtitle,
                        playCountdownSound,
                        transitionToProgress,
                        onStart);
    }

    /**
     * Optional block-disappearance selection for this instance, translated by replica subclasses.
     */
    protected Vector[] getCountdownBlockDisappearanceBounds() {
        if (gameTypeEnum != GameTypeEnum.TGTTOS) return null;
        if (!gameConfig.hasCountdownBlockDisappearance()) return null;
        return new Vector[] {
            gameConfig.getCountdownBlockDisappearancePos1().clone(),
            gameConfig.getCountdownBlockDisappearancePos2().clone()
        };
    }

    /**
     * A live remaining-time clock with an exact endpoint: duration is rendered at T0, the first
     * decrement occurs at T0+20 ticks, and zero/onEnd occur at T0+duration*20 ticks.
     */
    protected BukkitTask startRemainingTimer(
            int durationSeconds, @NotNull IntConsumer onTick, @NotNull Runnable onEnd) {
        final int[] remaining = {Math.max(0, durationSeconds)};
        onTick.accept(remaining[0]);
        if (remaining[0] == 0) {
            onEnd.run();
            return null;
        }

        BukkitTask[] taskHolder = new BukkitTask[1];
        taskHolder[0] =
                scheduler.runTaskTimer(
                        plugin,
                        () -> {
                            remaining[0]--;
                            onTick.accept(remaining[0]);
                            if (remaining[0] == 0) {
                                taskHolder[0].cancel();
                                onEnd.run();
                            }
                        },
                        20L,
                        20L);
        return taskHolder[0];
    }

    public void cancelFinalCountdown() {
        if (preparation != null) preparation.cancelCountdown();
        countdownBlockDisappearance.cancel();
    }

    protected String getFinalCountdownSubtitle(String gameTitle) {
        return GameMessages.GAME_START_COUNT_DOWN_SUBTITLE.replace("%game%", gameTitle);
    }

    protected String getFinalCountdownTitle(int seconds) {
        return GameMessages.GAME_START_COUNT_DOWN_TITLE.replace("%time%", String.valueOf(seconds));
    }

    protected String getFinalCountdownSubtitle(String gameTitle, int seconds) {
        return getFinalCountdownSubtitle(gameTitle);
    }

    void playCountdownBit(Note note) {
        playNoteToAllGamePlayers(Instrument.BIT, note);
        for (Player spectator : getOnlineSpectators()) {
            spectator.playNote(spectator.getLocation(), Instrument.BIT, note);
        }
    }

    protected void announceGameStart(String title, String subtitle) {
        sendActionBarToAllGamePlayers(
                GameMessages.GAME_START_ACTION_BAR.replace("%game%", gameTypeEnum.toString()));
        sendTitleToAllGamePlayers(title, subtitle);
        logGame(
                Level.INFO,
                MessageConfig.codeText("base-game-instance.reset-game-1"),
                MessageConfig.codeText("base-game-instance.log-game-1"));
    }

    protected void announceGameEnd(String title, String subtitle) {
        clearBossBars();
        if (settlementSuppressed) {
            logGame(
                    Level.INFO,
                    MessageConfig.codeText("base-game-instance.reset-game-1"),
                    MessageConfig.codeText("base-game-instance.announce-game-end-1"));
            return;
        }
        sendActionBarToAllGamePlayers(
                GameMessages.GAME_END_ACTION_BAR.replace("%game%", gameTypeEnum.toString()));
        boolean hasNextRound =
                isFormalEventRun()
                        && plugin.getScheduleManager() != null
                        && plugin.getScheduleManager().hasNextRound(gameTypeEnum);
        String completionTitle =
                hasNextRound
                        ? GameMessages.GAME_ROUND_COMPLETE_TITLE
                        : GameMessages.GAME_ROUND_END_TITLE;
        sendTitleToAllGamePlayers(completionTitle, subtitle);
        logGame(
                Level.INFO,
                MessageConfig.codeText("base-game-instance.reset-game-1"),
                MessageConfig.codeText("base-game-instance.announce-game-end-2"));
    }

    /**
     * Emits the normal completion event for every run mode. Schedule listeners decide whether they
     * own the event; TEST/TEST_EVENT therefore exercise the same Bukkit event path while formal
     * schedule handlers continue to require {@link #isFormalEventRun()}.
     */
    protected final void publishGameEndEvent(@NotNull Event event) {
        if (!settlementSuppressed) Bukkit.getPluginManager().callEvent(event);
    }

    /** Allows in-memory scoring for normal finishes, including TEST; aborts suppress scoring. */
    protected final boolean isSettlementAllowed() {
        return !settlementSuppressed;
    }

    /** Cancels a running rule-introduction phase (task + flag); safe to call at any time. */
    public void cancelIntroduction() {
        if (preparation != null) preparation.cancelIntroduction();
    }

    /**
     * Whether an interactive introduction lets this participant use the world (break, interact,
     * pick up and drop) under the game's own rules. Damage protection is never lifted.
     */
    public boolean allowsIntroductionWorldInteraction(@NotNull Player player) {
        return false;
    }

    /** Optional game-owned activity; the shared introduction still owns timing and recovery. */
    protected void onIntroductionStarted() {}

    /** Called once for completion, cancellation, reset or disposal, before normal preparation. */
    protected void onIntroductionStopped() {}

    /**
     * Where a player (re)joining or being pulled back during PREPARATION should land: the
     * introduction spawn point while the introduction phase runs, otherwise the given
     * normal-preparation fallback.
     */
    public Location getPreparationTeleportLocation(@NotNull Location fallback) {
        Location introductionSpawnPoint = resolveIntroductionSpawnPoint();
        if (isIntroductionPhase() && introductionSpawnPoint != null) return introductionSpawnPoint;
        return fallback;
    }

    public boolean isSpectator(@NotNull Player player) {
        return spectators.contains(player.getUniqueId());
    }

    /** Returns whether this runtime instance has been released and may no longer own players. */
    public final boolean isDisposed() {
        return disposed;
    }

    /**
     * Gameplay admission is separate from roster membership, which still owns results and recovery.
     */
    public final boolean isGameplayParticipant(@NotNull Player player) {
        return !notAreaPlayer(player)
                && !plugin.getGameManager()
                        .getSpectatorManager()
                        .isSpectatorLike(player.getUniqueId());
    }

    public final boolean isGameplayParticipant(@NotNull UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        return player != null && isGameplayParticipant(player);
    }

    /** Recovery callbacks may restore an eliminated participant, but only in their original run. */
    public final long captureGameGeneration() {
        return startGeneration;
    }

    /** Recovery may restore an eliminated roster member while the same run still owns them. */
    public final BukkitTask scheduleParticipantRecovery(
            Player player, Runnable action, long ticks) {
        return participantCallbacks().schedule(player, action, ticks, false);
    }

    /** Respawn can synchronously invoke listeners, so validate ownership again before recovery. */
    public final BukkitTask scheduleParticipantRespawn(Player player, Runnable afterRespawn) {
        long generation = captureGameGeneration();
        return scheduleParticipantRecovery(
                player,
                () -> {
                    player.spigot().respawn();
                    if (isCurrentParticipantConnection(generation, player)) afterRespawn.run();
                },
                1L);
    }

    /** Delayed gameplay also rechecks the shared spectator admission rule before changing state. */
    public final BukkitTask scheduleGameplayAction(Player player, Runnable action, long ticks) {
        return participantCallbacks().schedule(player, action, ticks, true);
    }

    private ParticipantCallbacks participantCallbacks() {
        if (participantCallbacks == null)
            participantCallbacks = new ParticipantCallbacks(this, plugin, scheduler);
        return participantCallbacks;
    }

    protected final void invalidateParticipantCallbacks() {
        cancelParticipantCallbacks();
        startGeneration++;
    }

    private void cancelParticipantCallbacks() {
        if (participantCallbacks != null) participantCallbacks.cancel();
    }

    public final boolean isCurrentParticipantConnection(long generation, Player player) {
        return generation == startGeneration
                && !disposed
                && plugin.isLoaded()
                && getGameStageEnum() != GameStageEnum.WAITING
                && getGameStageEnum() != GameStageEnum.END
                && Bukkit.getPlayer(player.getUniqueId()) == player
                && !notAreaPlayer(player)
                && plugin.getGameManager().getBasePlayerArea(player.getUniqueId()) == this;
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
            scheduler.runTask(
                    plugin,
                    () -> {
                        event.getEntity().spigot().respawn();
                        removeSpectator(player);
                    });
        }
    }

    public void handleSpectatorJoin(@NotNull PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (isSpectator(player)) {
            teleportSpectatorAsync(
                    player,
                    plugin.getGameManager().getSpectatorManager().defaultDestination(player, this));
        }
    }

    public void teleportAllSpectators(@NotNull Location location) {
        for (Player player : getOnlineSpectators()) {
            teleportSpectatorAsync(player, location);
        }
    }

    public void addSpectator(@NotNull Player player) {
        addSpectator(
                player,
                plugin.getGameManager().getSpectatorManager().defaultDestination(player, this));
    }

    /**
     * Adds a spectator and sends them directly to an explicitly selected location in this instance.
     */
    public void addSpectator(@NotNull Player player, @NotNull Location location) {
        spectators.add(player.getUniqueId());
        teleportSpectatorAsync(player, location);
    }

    /** Retains an offline spectator for a direct hand-off to a later event-round instance. */
    public void addSpectatorWithoutTeleport(@NotNull UUID uuid) {
        spectators.add(uuid);
    }

    /**
     * Snapshot used by the schedule coordinator to carry spectators through an event-round
     * transition.
     */
    public Set<UUID> getSpectatorUniqueIds() {
        return Set.copyOf(spectators);
    }

    /**
     * Loads the destination chunk without blocking the server thread, then applies spectator state.
     */
    public void teleportSpectatorAsync(@NotNull Player player, @NotNull Location location) {
        plugin.getGameManager()
                .getSpectatorManager()
                .teleportManaged(
                        player,
                        this,
                        location,
                        success -> {
                            if (success) return;
                            plugin.getGameManager().leaveSpectating(player);
                            logGame(
                                    Level.WARNING,
                                    MessageConfig.codeText(
                                            "base-game-instance.teleport-spectator-async-1"),
                                    MessageConfig.codeText(
                                            "base-game-instance.teleport-spectator-async-2",
                                            player.getName()));
                        });
    }

    /**
     * Applies the common spectator presentation; area subclasses may add game-specific overlays.
     */
    protected void applySpectatorGameMode(@NotNull Player player) {
        // Optional game overlay only. SpectatorManager owns identity, mode and passive protection.
    }

    /**
     * Spectator-mode entry point invoked by the spectator manager on behalf of area-level requests.
     */
    public final void applyManagedSpectatorPresentation(@NotNull Player player) {
        applySpectatorGameMode(player);
    }

    /** Restores any per-game spectator state before the player leaves this game. */
    protected void clearSpectatorGameMode(@NotNull Player player) {
        // Optional game overlay cleanup only.
    }

    public void removeAllSpectator() {
        releaseAllSpectators();
    }

    /**
     * Whether spectators of this area survive a disconnect and are restored on reconnect
     * (teleported back to the spectator spawn by {@link #handleSpectatorJoin}). Default {@code
     * false}: a spectator who quits is dropped, because {@code GameManagerHandler.onPlayerQuit}
     * calls {@code leaveSpectating}. Areas that opt in must release their spectators on game end
     * via {@link #releaseAllSpectators()}, otherwise a reconnecting spectator would land in a
     * finished game.
     */
    public boolean keepSpectatorAcrossReconnect() {
        return false;
    }

    /**
     * Releases every spectator - online ones are teleported to the lobby and set to ADVENTURE,
     * offline ones are just dropped - and clears both this area's spectator set and the
     * GameManager's spectator-status map for them. Used on game end by areas that keep spectators
     * across reconnect.
     */
    public void releaseAllSpectators() {
        Set<UUID> ids = new HashSet<>(spectators);
        for (UUID uuid : ids) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                removeSpectator(player); // teleport to lobby + ADVENTURE; drop from set
            } else {
                onlyRemoveSpectatorFromList(uuid); // drop from set
            }
        }
        for (UUID uuid : ids) plugin.getGameManager().clearSpectatorStatus(uuid, this);
    }

    /**
     * Detaches a spectator for a transfer to another live game without an intermediate lobby
     * teleport.
     */
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
        if (!Bukkit.isPrimaryThread()) return mapLifecycle.runOnMain(this::abortAndReset);
        if (disposed) return CompletableFuture.completedFuture(false);
        if (!mapLifecycle.activeOperation().isDone())
            return mapLifecycle.activeOperation().thenApply(Boolean.TRUE::equals);
        if (getGameStageEnum() == GameStageEnum.WAITING)
            return CompletableFuture.completedFuture(true);

        settlementSuppressed = true;
        try {
            endGameFinally();
            // A few implementations legitimately ignore END. If there is no reset already in
            // flight,
            // force the common reset so an interrupted transition cannot remain stuck in END.
            if (getGameStageEnum() != GameStageEnum.WAITING
                    && mapLifecycle.activeOperation().isDone()) resetGame();
        } finally {
            settlementSuppressed = false;
        }

        CompletableFuture<Boolean> completion = mapLifecycle.activeOperation();
        return completion.thenApply(
                success ->
                        Boolean.TRUE.equals(success)
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
            teleportToLobby(player);
            clearSpectatorGameMode(player);
            plugin.getGameManager().getSpectatorManager().leavePresentation(player);
            player.setGameMode(GameMode.ADVENTURE);
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
        Location spectatorSpawn = getSpectatorSpawnLocation();
        World world = Bukkit.getWorld(getWorldName());
        if (world == null && spectatorSpawn != null) world = spectatorSpawn.getWorld();
        if (world != null && pos1 != null && pos2 != null) {
            world.getNearbyEntities(
                            new BoundingBox(
                                    pos1.getX(),
                                    pos1.getY(),
                                    pos1.getZ(),
                                    pos2.getX(),
                                    pos2.getY(),
                                    pos2.getZ()))
                    .forEach(
                            entity -> {
                                if (entity instanceof Item) {
                                    entity.remove();
                                }
                            });
        }
    }

    public boolean notInArea(Location location) {
        if (location == null) return true;
        Vector pos1 = getGameConfig().getAreaPos1();
        Vector pos2 = getGameConfig().getAreaPos2();
        Location spectatorSpawn = getSpectatorSpawnLocation();
        World areaWorld = Bukkit.getWorld(getWorldName());
        if (areaWorld == null && spectatorSpawn != null) areaWorld = spectatorSpawn.getWorld();
        return location.getWorld() == null
                || areaWorld == null
                || pos1 == null
                || pos2 == null
                || !location.getWorld().getName().equals(areaWorld.getName())
                || !location.toVector().isInAABB(pos1, pos2);
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
