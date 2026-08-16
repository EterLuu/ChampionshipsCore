package ink.ziip.championshipscore.api.game.manager;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.ChampionshipPermissions;
import ink.ziip.championshipscore.api.BaseManager;
import ink.ziip.championshipscore.api.event.SingleGameEndEvent;
import ink.ziip.championshipscore.api.event.TeamGameEndEvent;
import ink.ziip.championshipscore.api.game.instance.BaseGameInstance;
import ink.ziip.championshipscore.api.game.instance.multiteam.BaseMultiTeamGameInstance;
import ink.ziip.championshipscore.api.game.instance.paired.BasePairedGameInstance;
import ink.ziip.championshipscore.api.game.spectate.SpectateMenu;
import ink.ziip.championshipscore.api.game.spectate.SpectatorManager;
import ink.ziip.championshipscore.api.game.battlebox.BattleBoxArea;
import ink.ziip.championshipscore.api.game.battlebox.BattleBoxManager;
import ink.ziip.championshipscore.api.game.bingo.BingoManager;
import ink.ziip.championshipscore.api.game.bingo.execution.BingoExecutionRouter;
import ink.ziip.championshipscore.api.game.bingo.execution.BingoExecutionMode;
import ink.ziip.championshipscore.api.game.bingo.execution.BingoStartRequest;
import ink.ziip.championshipscore.api.game.bingo.execution.LocalBingoExecutionGateway;
import ink.ziip.championshipscore.api.game.bingo.execution.RemoteBingoInstance;
import ink.ziip.championshipscore.api.game.buildmart.BuildMartManager;
import ink.ziip.championshipscore.api.game.decarnival.DragonEggCarnivalManager;
import ink.ziip.championshipscore.api.game.dodgebolt.DodgeboltManager;
import ink.ziip.championshipscore.api.game.dodgebolt.DodgeboltArea;
import ink.ziip.championshipscore.api.game.acerace.AceRaceManager;
import ink.ziip.championshipscore.api.game.hotycodydusky.HotyCodyDuskyManager;
import ink.ziip.championshipscore.api.game.parkourtag.ParkourTagArea;
import ink.ziip.championshipscore.api.game.parkourtag.ParkourTagManager;
import ink.ziip.championshipscore.api.game.parkourwarrior.ParkourWarriorManager;
import ink.ziip.championshipscore.api.game.skywars.SkyWarsManager;
import ink.ziip.championshipscore.api.game.snowball.SnowballShowdownManager;
import ink.ziip.championshipscore.api.game.tgttos.TGTTOSManager;
import ink.ziip.championshipscore.api.game.tntrun.TNTRunManager;
import ink.ziip.championshipscore.api.object.game.GameRunMode;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.object.schedule.TwoVTwoVector;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.command.MainCommand;
import ink.ziip.championshipscore.configuration.config.CCConfig;
import ink.ziip.championshipscore.util.Utils;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class GameManager extends BaseManager {
    public enum GameStopResult {
        SETTLEMENT_STARTED,
        PRE_START_ABORTED,
        NOT_ACTIVE,
        NOT_REGISTERED,
        FAILED
    }

    public record ReloadReport(int reusedInstances, int resetInstances, int failedResets,
                               int reloadedConfigurations, int failedConfigurations,
                               int enabledManagers, int disabledManagers,
                               int remoteMatchesStopped) {
    }

    private final Map<UUID, BaseGameInstance> playerSpectatorStatus = new ConcurrentHashMap<>();
    private final Map<ChampionshipTeam, BaseGameInstance> teamStatus = new ConcurrentHashMap<>();
    private final Map<UUID, BaseGameInstance> playerStatus = new ConcurrentHashMap<>();
    private final Map<UUID, RoundTransitionHold> roundTransitionHolds = new ConcurrentHashMap<>();
    private final Map<UUID, SpectatorTransitionHold> spectatorTransitionHolds = new ConcurrentHashMap<>();
    private final Map<UUID, RemoteBingoInstance> remoteBingoInstances = new ConcurrentHashMap<>();
    private final Map<BaseGameInstance, Set<ChampionshipTeam>> pendingFinaleAudience = new ConcurrentHashMap<>();
    private final GameManagerHandler gameManagerHandler;
    private final SpectateMenu spectateMenu;
    @Getter
    private final SpectatorManager spectatorManager;
    @Getter
    private final BattleBoxManager battleBoxManager;
    @Getter
    private final ParkourTagManager parkourTagManager;
    @Getter
    private final SkyWarsManager skyWarsManager;
    @Getter
    private final TGTTOSManager tgttosManager;
    @Getter
    private final TNTRunManager tntRunManager;
    @Getter
    private final DragonEggCarnivalManager dragonEggCarnivalManager;
    @Getter
    private final SnowballShowdownManager snowballShowdownManager;
    @Getter
    private final ParkourWarriorManager parkourWarriorManager;
    @Getter
    private final HotyCodyDuskyManager hotyCodyDuskyManager;
    @Getter
    private final BingoManager bingoManager;
    @Getter
    private final BingoExecutionRouter bingoExecutionRouter;
    @Getter
    private final BuildMartManager buildMartManager;
    @Getter
    private final DodgeboltManager dodgeboltManager;
    @Getter
    private final AceRaceManager aceRaceManager;
    /**
     * Registry mapping each game type to its area manager. Drives the generic
     * {@code join*} dispatch so adding a game only requires registering it here.
     */
    private final Map<GameTypeEnum, BaseGameInstanceManager<? extends BaseGameInstance>> areaManagers = new EnumMap<>(GameTypeEnum.class);
    /** Lazily parsed from {@link CCConfig#ENABLED_GAMES}; see {@link #getEnabledGames()}. */
    private Set<GameTypeEnum> enabledGames;
    /** Representative instance used for automatic audience routing, including gaps between formal rounds. */
    private volatile BaseGameInstance spectatorFocus;
    /** Managers that have actually been loaded, including disabled games opened through map editing. */
    private final Set<GameTypeEnum> loadedGameManagers = EnumSet.noneOf(GameTypeEnum.class);

    public GameManager(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
        gameManagerHandler = new GameManagerHandler(championshipsCore);
        spectateMenu = new SpectateMenu(championshipsCore, this);
        spectatorManager = new SpectatorManager(championshipsCore, this);
        battleBoxManager = new BattleBoxManager(plugin);
        parkourTagManager = new ParkourTagManager(plugin);
        skyWarsManager = new SkyWarsManager(plugin);
        tgttosManager = new TGTTOSManager(plugin);
        tntRunManager = new TNTRunManager(plugin);
        dragonEggCarnivalManager = new DragonEggCarnivalManager(plugin);
        snowballShowdownManager = new SnowballShowdownManager(plugin);
        parkourWarriorManager = new ParkourWarriorManager(plugin);
        hotyCodyDuskyManager = new HotyCodyDuskyManager(plugin);
        bingoManager = new BingoManager(plugin);
        buildMartManager = new BuildMartManager(plugin);
        dodgeboltManager = new DodgeboltManager(plugin);
        aceRaceManager = new AceRaceManager(plugin);

        areaManagers.put(GameTypeEnum.Bingo, bingoManager);
        areaManagers.put(GameTypeEnum.BuildMart, buildMartManager);
        areaManagers.put(GameTypeEnum.BattleBox, battleBoxManager);
        areaManagers.put(GameTypeEnum.ParkourTag, parkourTagManager);
        areaManagers.put(GameTypeEnum.SkyWars, skyWarsManager);
        areaManagers.put(GameTypeEnum.TGTTOS, tgttosManager);
        areaManagers.put(GameTypeEnum.TNTRun, tntRunManager);
        areaManagers.put(GameTypeEnum.DragonEggCarnival, dragonEggCarnivalManager);
        areaManagers.put(GameTypeEnum.SnowballShowdown, snowballShowdownManager);
        areaManagers.put(GameTypeEnum.ParkourWarrior, parkourWarriorManager);
        areaManagers.put(GameTypeEnum.HotyCodyDusky, hotyCodyDuskyManager);
        areaManagers.put(GameTypeEnum.Dodgebolt, dodgeboltManager);
        areaManagers.put(GameTypeEnum.AceRace, aceRaceManager);

        bingoExecutionRouter = new BingoExecutionRouter(new LocalBingoExecutionGateway(request ->
                request.teams().isEmpty()
                        ? joinSingleTeamAreaForAllTeamsLocal(GameTypeEnum.Bingo, request.area(),
                                request.showIntroduction(), request.runMode())
                        : joinSingleTeamAreaForTeams(GameTypeEnum.Bingo, request.area(),
                                request.showIntroduction(), request.runMode(),
                                request.teams().toArray(ChampionshipTeam[]::new)),
                ignored -> forceEndLocalAreas(GameTypeEnum.Bingo)));
    }

    /**
     * @return the area manager registered for {@code gameTypeEnum}, or {@code null} if none.
     */
    @Nullable
    public BaseGameInstanceManager<? extends BaseGameInstance> getAreaManager(GameTypeEnum gameTypeEnum) {
        return areaManagers.get(gameTypeEnum);
    }

    public boolean isGameManagerLoaded(@NotNull GameTypeEnum gameTypeEnum) {
        return loadedGameManagers.contains(gameTypeEnum);
    }

    /** Returns a bound map's admin teleport anchor, or {@code null} when the world is unbound. */
    @Nullable
    public Location getMapTeleportLocation(@NotNull String worldName) {
        for (GameTypeEnum gameType : GameTypeEnum.values()) {
            BaseGameInstanceManager<? extends BaseGameInstance> manager = areaManagers.get(gameType);
            if (manager == null) continue;
            Location target = manager.getWorldTeleportLocation(worldName);
            if (target != null) return target;
        }
        return null;
    }

    /**
     * Loads an otherwise disabled game's manager for map preparation. The operation is idempotent so
     * reopening its map UI cannot register worlds, listeners, or runtime instances twice.
     *
     * @return true when a load was started; false when the manager was already available or absent.
     */
    public boolean loadGameForEditing(@NotNull GameTypeEnum gameTypeEnum) {
        return loadGameManager(gameTypeEnum);
    }

    private boolean loadGameManager(@NotNull GameTypeEnum gameTypeEnum) {
        BaseGameInstanceManager<? extends BaseGameInstance> manager = areaManagers.get(gameTypeEnum);
        if (manager == null || !loadedGameManagers.add(gameTypeEnum)) return false;
        manager.load();
        plugin.getLogger().log(Level.INFO, Utils.formatGameLog(gameTypeEnum, "-", "加载", "完成",
                "地图管理器已加载"));
        return true;
    }

    /**
     * @return true if {@code gameTypeEnum} is listed in the {@code enabled-games} config option.
     * Only enabled games load their area worlds and can be started or operated.
     */
    public boolean isGameEnabled(@NotNull GameTypeEnum gameTypeEnum) {
        return getEnabledGames().contains(gameTypeEnum);
    }

    /**
     * @return the games enabled via the {@code enabled-games} config option. Parsed lazily on first
     * use (the configuration file is loaded after this manager is constructed), case-insensitively;
     * unknown names are logged once and ignored. An empty list means no game is enabled.
     */
    public Set<GameTypeEnum> getEnabledGames() {
        if (enabledGames == null) {
            Set<GameTypeEnum> parsed = EnumSet.noneOf(GameTypeEnum.class);
            List<String> configured = CCConfig.ENABLED_GAMES;
            if (configured != null) {
                for (String name : configured) {
                    if (name == null)
                        continue;
                    String trimmed = name.trim();
                    boolean matched = false;
                    for (GameTypeEnum type : GameTypeEnum.values()) {
                        if (type.name().equalsIgnoreCase(trimmed)) {
                            parsed.add(type);
                            matched = true;
                            break;
                        }
                    }
                    if (!matched) {
                        plugin.getLogger().log(Level.WARNING, Utils.formatModuleLog("GameManager", "配置",
                                "enabled-games 包含未知游戏=" + trimmed + "，已忽略"));
                    }
                }
            }
            enabledGames = parsed;
            plugin.getLogger().log(Level.INFO, Utils.formatModuleLog("GameManager", "加载",
                    "已启用游戏=" + (parsed.isEmpty() ? "无" : parsed)));
        }
        return enabledGames;
    }

    /** Immutable snapshot taken before ConfigurationManager replaces the global config values. */
    public Set<GameTypeEnum> enabledGamesSnapshot() {
        return Set.copyOf(getEnabledGames());
    }

    /**
     * Reconciles global game enablement without tearing down unchanged maps. Idle instances retain
     * their worlds, listeners and loaded templates; only non-WAITING instances are force-reset.
     * Managers are loaded/unloaded solely when enabled-games actually changes.
     */
    public CompletionStage<ReloadReport> hotReload(@NotNull Set<GameTypeEnum> previouslyEnabled) {
        if (!Bukkit.isPrimaryThread())
            throw new IllegalStateException("GameManager hot reload must run on the server thread");

        enabledGames = null;
        Set<GameTypeEnum> currentlyEnabled = Set.copyOf(getEnabledGames());
        Set<GameTypeEnum> managersToDisable = EnumSet.noneOf(GameTypeEnum.class);
        for (GameTypeEnum gameType : previouslyEnabled) {
            if (!currentlyEnabled.contains(gameType) && loadedGameManagers.contains(gameType))
                managersToDisable.add(gameType);
        }
        Set<GameTypeEnum> managersToEnable = EnumSet.noneOf(GameTypeEnum.class);
        for (GameTypeEnum gameType : currentlyEnabled) {
            if (!loadedGameManagers.contains(gameType)) managersToEnable.add(gameType);
        }

        int resetInstances = 0;
        int remoteMatchesStopped = 0;

        Set<ink.ziip.championshipscore.api.game.config.BaseGameConfig> activeConfigurations =
                Collections.newSetFromMap(new IdentityHashMap<>());
        for (GameTypeEnum gameType : Set.copyOf(loadedGameManagers)) {
            if (managersToDisable.contains(gameType)) continue;
            BaseGameInstanceManager<? extends BaseGameInstance> manager = areaManagers.get(gameType);
            if (manager == null) continue;
            for (BaseGameInstance instance : manager.getRuntimeInstances()) {
                if (instance.getGameStageEnum() != GameStageEnum.WAITING)
                    activeConfigurations.add(instance.getGameConfig());
            }
        }

        int reloadedConfigurations = 0;
        int failedConfigurations = 0;
        int reusedInstances = 0;
        Set<ink.ziip.championshipscore.api.game.config.BaseGameConfig> visitedConfigurations =
                Collections.newSetFromMap(new IdentityHashMap<>());
        List<CompletableFuture<Boolean>> resets = new ArrayList<>();
        for (GameTypeEnum gameType : Set.copyOf(loadedGameManagers)) {
            BaseGameInstanceManager<? extends BaseGameInstance> manager = areaManagers.get(gameType);
            if (manager == null) continue;
            boolean disabling = managersToDisable.contains(gameType);
            for (BaseGameInstance instance : manager.getRuntimeInstances()) {
                if (instance.getGameStageEnum() == GameStageEnum.WAITING) {
                    if (!disabling && !activeConfigurations.contains(instance.getGameConfig())
                            && visitedConfigurations.add(instance.getGameConfig())) {
                        if (instance.getGameConfig().reloadConfigurationChecked(plugin.getFolder()))
                            reloadedConfigurations++;
                        else
                            failedConfigurations++;
                    }
                    if (!disabling) reusedInstances++;
                    continue;
                }
                if (plugin.getDailyManager() != null) plugin.getDailyManager().abort(instance);
                resets.add(instance.abortAndReset());
                resetInstances++;
            }
        }

        for (RemoteBingoInstance instance : remoteBingoInstances.values()) {
            if (instance.getGameStageEnum() != GameStageEnum.WAITING) remoteMatchesStopped++;
        }
        CompletableFuture<Void> remoteStop = remoteMatchesStopped > 0
                ? bingoExecutionRouter.forceEnd("configuration-reload").toCompletableFuture()
                : CompletableFuture.completedFuture(null);

        int finalReusedInstances = reusedInstances;
        int finalResetInstances = resetInstances;
        int finalRemoteMatchesStopped = remoteMatchesStopped;
        int finalReloadedConfigurations = reloadedConfigurations;
        int finalFailedConfigurations = failedConfigurations;
        CompletableFuture<?>[] operations = new CompletableFuture<?>[resets.size() + 1];
        for (int index = 0; index < resets.size(); index++) operations[index] = resets.get(index);
        operations[operations.length - 1] = remoteStop;
        CompletableFuture<ReloadReport> result = new CompletableFuture<>();
        CompletableFuture.allOf(operations).whenComplete((ignored, operationFailure) -> {
            Runnable finish = () -> {
                try {
                    int failedResets = (int) resets.stream()
                            .filter(reset -> reset.isCompletedExceptionally() || !Boolean.TRUE.equals(reset.getNow(false)))
                            .count();
                    int disabledManagers = 0;
                    if (managersToDisable.contains(GameTypeEnum.Bingo)
                            && plugin.getRemoteBingoManager() != null) {
                        plugin.getRemoteBingoManager().unload();
                    }
                    for (GameTypeEnum gameType : managersToDisable) {
                        if (!loadedGameManagers.remove(gameType)) continue;
                        BaseGameInstanceManager<? extends BaseGameInstance> manager = areaManagers.get(gameType);
                        if (manager != null) manager.unload();
                        disabledManagers++;
                    }
                    int enabledManagers = 0;
                    for (GameTypeEnum gameType : managersToEnable) {
                        if (loadGameManager(gameType)) enabledManagers++;
                    }
                    if (managersToEnable.contains(GameTypeEnum.Bingo)
                            && loadedGameManagers.contains(GameTypeEnum.Bingo)
                            && plugin.getRemoteBingoManager() != null) {
                        plugin.getRemoteBingoManager().load();
                    }
                    if (operationFailure != null) {
                        result.completeExceptionally(operationFailure);
                        return;
                    }
                    result.complete(new ReloadReport(finalReusedInstances, finalResetInstances, failedResets,
                            finalReloadedConfigurations, finalFailedConfigurations,
                            enabledManagers, disabledManagers, finalRemoteMatchesStopped));
                } catch (Throwable failure) {
                    result.completeExceptionally(failure);
                }
            };
            if (Bukkit.isPrimaryThread()) finish.run();
            else {
                try {
                    Bukkit.getScheduler().runTask(plugin, finish);
                } catch (RuntimeException schedulingFailure) {
                    result.completeExceptionally(schedulingFailure);
                }
            }
        });
        return result;
    }

    @Override
    public void load() {
        for (GameTypeEnum gameType : areaManagers.keySet()) {
            if (isGameEnabled(gameType)) {
                loadGameManager(gameType);
            } else {
                plugin.getLogger().log(Level.INFO, Utils.formatGameLog(gameType, "-", "加载", "跳过",
                        "游戏未启用，不加载场地与世界"));
            }
        }

        gameManagerHandler.register();
        spectateMenu.start();
        spectatorManager.load();
    }

    @Override
    public void unload() {
        spectateMenu.stop();
        spectatorManager.unload();
        playerSpectatorStatus.clear();
        for (GameTypeEnum gameType : EnumSet.copyOf(loadedGameManagers)) {
            BaseGameInstanceManager<? extends BaseGameInstance> manager = areaManagers.get(gameType);
            if (manager != null) manager.unload();
        }
        loadedGameManagers.clear();
        enabledGames = null;
        spectatorFocus = null;
        pendingFinaleAudience.clear();
        roundTransitionHolds.clear();
        spectatorTransitionHolds.clear();
        remoteBingoInstances.values().forEach(RemoteBingoInstance::dispose);
        remoteBingoInstances.clear();

        gameManagerHandler.unRegister();
    }

    /**
     * Force-ends every currently-running area of the given game (any area not in WAITING). Used by the
     * schedule "delete current game" flow to scrap a broken/in-progress game before clearing its records.
     * Calls {@link BaseGameInstance#endGameFinally()}, which removes players and resets the instance.
     */
    public void forceEndAreas(@NotNull GameTypeEnum gameTypeEnum) {
        if (gameTypeEnum == GameTypeEnum.Bingo) {
            bingoExecutionRouter.forceEnd("formal-event-force-end");
            return;
        }
        forceEndLocalAreas(gameTypeEnum);
    }

    /** Emergency-stops only formal EVENT ownership, leaving concurrent DAILY/GAME copies untouched. */
    public void forceEndEventAreas(@NotNull GameTypeEnum gameTypeEnum) {
        if (gameTypeEnum == GameTypeEnum.Bingo) {
            boolean hasEventRun = remoteBingoInstances.values().stream()
                    .anyMatch(instance -> instance.getRunMode() == GameRunMode.EVENT
                            && instance.getGameStageEnum() != GameStageEnum.WAITING);
            BaseGameInstanceManager<?> manager = areaManagers.get(gameTypeEnum);
            if (manager != null) hasEventRun |= manager.getRuntimeInstances().stream()
                    .anyMatch(instance -> instance.getRunMode() == GameRunMode.EVENT
                            && instance.getGameStageEnum() != GameStageEnum.WAITING);
            if (hasEventRun) bingoExecutionRouter.forceEnd("formal-event-force-end");
            return;
        }
        BaseGameInstanceManager<?> manager = areaManagers.get(gameTypeEnum);
        if (manager == null) return;
        for (BaseGameInstance instance : manager.getRuntimeInstances()) {
            if (instance.getRunMode() == GameRunMode.EVENT
                    && instance.getGameStageEnum() != GameStageEnum.WAITING) {
                instance.endGameFinally();
            }
        }
    }

    public boolean hasActiveEventAreas(@NotNull GameTypeEnum gameTypeEnum) {
        BaseGameInstanceManager<?> manager = areaManagers.get(gameTypeEnum);
        if (manager == null) return false;
        return manager.getRuntimeInstances().stream()
                .anyMatch(instance -> instance.isEventRun()
                        && instance.getGameStageEnum() != GameStageEnum.WAITING);
    }

    private void forceEndLocalAreas(@NotNull GameTypeEnum gameTypeEnum) {
        BaseGameInstanceManager<?> manager = areaManagers.get(gameTypeEnum);
        if (manager == null) return;
        for (BaseGameInstance instance : manager.getRuntimeInstances()) {
            if (instance.getGameStageEnum() != GameStageEnum.WAITING) {
                instance.endGameFinally();
            }
        }
    }

    /**
     * Every exact runtime target which an administrator can stop. This deliberately includes
     * pre-start stages so a stuck preload/countdown can be reset without touching sibling copies.
     */
    public @NotNull List<BaseGameInstance> getStoppableInstances() {
        List<BaseGameInstance> instances = new ArrayList<>();
        for (Map.Entry<GameTypeEnum, BaseGameInstanceManager<? extends BaseGameInstance>> entry
                : areaManagers.entrySet()) {
            if (!loadedGameManagers.contains(entry.getKey())) continue;
            entry.getValue().getRuntimeInstances().stream()
                    .filter(instance -> isStoppableStage(instance.getGameStageEnum()))
                    .forEach(instances::add);
        }
        remoteBingoInstances.values().stream()
                .filter(instance -> isStoppableStage(instance.getGameStageEnum()))
                .forEach(instances::add);
        instances.sort(Comparator
                .comparingInt((BaseGameInstance instance) -> instance.getGameTypeEnum().ordinal())
                .thenComparing(this::canonicalMapName, String.CASE_INSENSITIVE_ORDER)
                .thenComparingInt(BaseGameInstance::getCopyIndex)
                .thenComparing(this::stableInstanceKey));
        return List.copyOf(instances);
    }

    /** Active copies matching one configured map, including one exact remote Bingo match. */
    public @NotNull List<BaseGameInstance> getStoppableMapInstances(
            @NotNull GameTypeEnum gameType, @NotNull String mapName) {
        return getStoppableInstances().stream()
                .filter(instance -> instance.getGameTypeEnum() == gameType)
                .filter(instance -> mapMatches(instance, mapName))
                .sorted(Comparator.comparingInt(BaseGameInstance::getCopyIndex)
                        .thenComparing(this::stableInstanceKey))
                .toList();
    }

    /**
     * Stops one still-registered target only. A played game uses its ordinary end path so scoring,
     * end events and result presentation remain intact; a pre-start run is aborted without points.
     */
    public CompletionStage<GameStopResult> stopGameInstance(@NotNull BaseGameInstance target,
                                                             @NotNull String reason) {
        if (!Bukkit.isPrimaryThread()) {
            CompletableFuture<GameStopResult> result = new CompletableFuture<>();
            Bukkit.getScheduler().runTask(plugin, () -> stopGameInstance(target, reason)
                    .whenComplete((value, failure) -> {
                        if (failure == null) result.complete(value);
                        else result.completeExceptionally(failure);
                    }));
            return result;
        }
        if (!isRegisteredRuntimeInstance(target))
            return CompletableFuture.completedFuture(GameStopResult.NOT_REGISTERED);

        GameStageEnum stage = target.getGameStageEnum();
        if (!isStoppableStage(stage))
            return CompletableFuture.completedFuture(GameStopResult.NOT_ACTIVE);

        boolean settle = settlesOnAdministrativeStop(stage);
        if (target instanceof RemoteBingoInstance remote) {
            return plugin.getRemoteBingoManager().stopMatch(remote.matchId(), reason, settle)
                    .thenApply(stopped -> stopped
                            ? (settle ? GameStopResult.SETTLEMENT_STARTED : GameStopResult.PRE_START_ABORTED)
                            : GameStopResult.FAILED);
        }

        if (!settle) {
            return target.abortAndReset().thenApply(reset -> reset
                    ? GameStopResult.PRE_START_ABORTED : GameStopResult.FAILED);
        }

        try {
            // This is intentionally the normal end entry, not endGameFinally(): the latter suppresses
            // the visible result phase and is reserved for emergency lifecycle teardown.
            target.endGame();
            return CompletableFuture.completedFuture(
                    isStoppableStage(target.getGameStageEnum())
                            ? GameStopResult.FAILED : GameStopResult.SETTLEMENT_STARTED);
        } catch (RuntimeException failure) {
            plugin.getLogger().log(Level.SEVERE, Utils.formatGameLog(target.getGameTypeEnum(),
                    canonicalMapName(target), stage.name(), "管理员停止", "正常结算入口异常"), failure);
            return CompletableFuture.completedFuture(GameStopResult.FAILED);
        }
    }

    static boolean isStoppableStage(@NotNull GameStageEnum stage) {
        return stage == GameStageEnum.LOADING || stage == GameStageEnum.PREPARATION
                || stage == GameStageEnum.COUNTDOWN || stage == GameStageEnum.PROGRESS
                || stage == GameStageEnum.STOPPING;
    }

    static boolean settlesOnAdministrativeStop(@NotNull GameStageEnum stage) {
        return stage == GameStageEnum.PROGRESS || stage == GameStageEnum.STOPPING;
    }

    private boolean isRegisteredRuntimeInstance(@NotNull BaseGameInstance target) {
        if (target instanceof RemoteBingoInstance remote)
            return remoteBingoInstances.get(remote.matchId()) == remote;
        BaseGameInstanceManager<? extends BaseGameInstance> manager = areaManagers.get(target.getGameTypeEnum());
        return manager != null && manager.getRuntimeInstances().stream().anyMatch(instance -> instance == target);
    }

    private @NotNull String canonicalMapName(@NotNull BaseGameInstance instance) {
        String configName = instance.getGameConfig().getConfigName();
        if (configName != null && !configName.isBlank()) return configName;
        String areaName = instance.getGameConfig().getAreaName();
        return areaName == null ? "" : areaName;
    }

    public boolean joinTeamArea(@NotNull GameTypeEnum gameTypeEnum, @NotNull String area, @NotNull ChampionshipTeam rightChampionshipTeam, @NotNull ChampionshipTeam leftChampionshipTeam) {
        return joinTeamArea(gameTypeEnum, area, rightChampionshipTeam, leftChampionshipTeam, false);
    }

    /** Starts the non-scoring final and records which finalist owns both opening arrows. */
    public boolean joinDodgeboltArea(@NotNull String area, @NotNull ChampionshipTeam rightTeam,
                                     @NotNull ChampionshipTeam leftTeam,
                                     @NotNull ChampionshipTeam higherSeed, boolean showIntroduction) {
        return joinDodgeboltArea(area, rightTeam, leftTeam, higherSeed, showIntroduction, false);
    }

    /** Forced starts admit each team's currently-online subset while normal finals still require full rosters. */
    public boolean joinDodgeboltArea(@NotNull String area, @NotNull ChampionshipTeam rightTeam,
                                     @NotNull ChampionshipTeam leftTeam,
                                     @NotNull ChampionshipTeam higherSeed, boolean showIntroduction,
                                     boolean forcePartialRoster) {
        return joinDodgeboltArea(area, rightTeam, leftTeam, higherSeed, showIntroduction,
                forcePartialRoster, GameRunMode.GAME);
    }

    public boolean joinDodgeboltArea(@NotNull String area, @NotNull ChampionshipTeam rightTeam,
                                     @NotNull ChampionshipTeam leftTeam,
                                     @NotNull ChampionshipTeam higherSeed, boolean showIntroduction,
                                     boolean forcePartialRoster, @NotNull GameRunMode runMode) {
        DodgeboltArea instance = dodgeboltManager.getArea(area);
        if (instance == null || (!higherSeed.equals(rightTeam) && !higherSeed.equals(leftTeam))) return false;
        instance.setFirstRoundArrowTeam(higherSeed);
        boolean started = joinTeamArea(GameTypeEnum.Dodgebolt, area, rightTeam, leftTeam,
                showIntroduction, forcePartialRoster, runMode);
        if (!started) instance.setFirstRoundArrowTeam(null);
        return started;
    }

    /** Moves every online non-finalist into a registered final's spectator set. */
    public synchronized void spectateFinale(@NotNull BaseGameInstance area,
                                            @NotNull ChampionshipTeam rightTeam,
                                            @NotNull ChampionshipTeam leftTeam) {
        if (!area.isEventRun()) return;
        pendingFinaleAudience.put(area, Set.of(rightTeam, leftTeam));
        spectatorFocus = area;
        if (!isRegularSpectatingStage(area.getGameStageEnum())) return;
        activateFinaleAudience(area);
    }

    /** Compatibility API for integrations using the legacy Dodgebolt-specific name. */
    public synchronized void spectateDodgeboltFinal(@NotNull DodgeboltArea area,
                                                     @NotNull ChampionshipTeam rightTeam,
                                                     @NotNull ChampionshipTeam leftTeam) {
        spectateFinale(area, rightTeam, leftTeam);
    }

    private void activateFinaleAudience(@NotNull BaseGameInstance area) {
        Set<ChampionshipTeam> finalists = pendingFinaleAudience.remove(area);
        if (finalists == null) return;
        for (Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
            if (finalists.stream().anyMatch(team -> team.isTeamMember(player))) continue;
            moveSpectatorTo(player, area);
        }
        spectatorFocus = area;
    }

    public boolean joinTeamArea(@NotNull GameTypeEnum gameTypeEnum, @NotNull String area,
                                @NotNull ChampionshipTeam rightChampionshipTeam,
                                @NotNull ChampionshipTeam leftChampionshipTeam, boolean showIntroduction) {
        return joinTeamArea(gameTypeEnum, area, rightChampionshipTeam, leftChampionshipTeam,
                showIntroduction, false, GameRunMode.GAME);
    }

    public boolean joinTeamArea(@NotNull GameTypeEnum gameTypeEnum, @NotNull String area,
                                @NotNull ChampionshipTeam rightChampionshipTeam,
                                @NotNull ChampionshipTeam leftChampionshipTeam, boolean showIntroduction,
                                @NotNull GameRunMode runMode) {
        return joinTeamArea(gameTypeEnum, area, rightChampionshipTeam, leftChampionshipTeam,
                showIntroduction, false, runMode);
    }

    private boolean joinTeamArea(@NotNull GameTypeEnum gameTypeEnum, @NotNull String area,
                                 @NotNull ChampionshipTeam rightChampionshipTeam,
                                 @NotNull ChampionshipTeam leftChampionshipTeam, boolean showIntroduction,
                                 boolean forcePartialDodgeboltRoster, @NotNull GameRunMode runMode) {
        if (!isGameEnabled(gameTypeEnum))
            return false;
        if (!plugin.getPrepareSessionManager().canStart(gameTypeEnum, area))
            return false;
        Collection<UUID> rightParticipants = forcePartialDodgeboltRoster
                ? rightChampionshipTeam.getOnlinePlayers().stream().map(Player::getUniqueId).toList()
                : rightChampionshipTeam.getMembers();
        Collection<UUID> leftParticipants = forcePartialDodgeboltRoster
                ? leftChampionshipTeam.getOnlinePlayers().stream().map(Player::getUniqueId).toList()
                : leftChampionshipTeam.getMembers();
        if (rightParticipants.isEmpty() || leftParticipants.isEmpty())
            return false;
        for (UUID uuid : rightParticipants) {
            if (isPlayerUnavailableForStart(uuid, gameTypeEnum, showIntroduction, runMode))
                return false;
            if (playerSpectatorStatus.containsKey(uuid))
                removeSpectator(uuid);
        }
        for (UUID uuid : leftParticipants) {
            if (isPlayerUnavailableForStart(uuid, gameTypeEnum, showIntroduction, runMode))
                return false;
            if (playerSpectatorStatus.containsKey(uuid))
                removeSpectator(uuid);
        }
        if (teamStatus.containsKey(rightChampionshipTeam))
            return false;
        if (teamStatus.containsKey(leftChampionshipTeam))
            return false;

        BaseGameInstanceManager<? extends BaseGameInstance> manager = areaManagers.get(gameTypeEnum);
        if (manager == null)
            return false;
        if (!(manager.getArea(area) instanceof BasePairedGameInstance teamArea))
            return false;

        teamArea.prepareRunMode(runMode);
        teamArea.setIntroductionEnabledForNextStart(showIntroduction);
        boolean started = teamArea instanceof DodgeboltArea dodgeboltArea
                ? dodgeboltArea.tryStartGame(rightChampionshipTeam, leftChampionshipTeam,
                        forcePartialDodgeboltRoster)
                : teamArea.tryStartGame(rightChampionshipTeam, leftChampionshipTeam);
        if (started) {
            teamStatus.put(rightChampionshipTeam, teamArea);
            teamStatus.put(leftChampionshipTeam, teamArea);
            if (forcePartialDodgeboltRoster) {
                for (UUID uuid : teamArea.getParticipantUniqueIds()) {
                    playerStatus.put(uuid, teamArea);
                    roundTransitionHolds.remove(uuid);
                    plugin.getVisibilityManager().reconcilePlayer(uuid);
                }
            } else {
                addPlayerStatusByTeam(rightChampionshipTeam, teamArea);
                addPlayerStatusByTeam(leftChampionshipTeam, teamArea);
            }
            focusSpectatorsOn(teamArea);
            return true;
        }
        teamArea.prepareRunMode(GameRunMode.GAME);
        teamArea.setIntroductionEnabledForNextStart(false);
        return false;
    }

    public synchronized boolean joinSingleTeamAreaForTeams(@NotNull GameTypeEnum gameTypeEnum, @NotNull String area, @NotNull ChampionshipTeam... championshipTeams) {
        return joinSingleTeamAreaForTeams(gameTypeEnum, area, false, championshipTeams);
    }

    public synchronized boolean joinSingleTeamAreaForTeams(@NotNull GameTypeEnum gameTypeEnum, @NotNull String area,
                                                            boolean showIntroduction,
                                                            @NotNull ChampionshipTeam... championshipTeams) {
        return joinSingleTeamAreaForTeams(gameTypeEnum, area, showIntroduction,
                GameRunMode.GAME, championshipTeams);
    }

    public synchronized boolean joinSingleTeamAreaForTeams(@NotNull GameTypeEnum gameTypeEnum, @NotNull String area,
                                                            boolean showIntroduction, @NotNull GameRunMode runMode,
                                                            @NotNull ChampionshipTeam... championshipTeams) {
        if (!isGameEnabled(gameTypeEnum))
            return false;
        if (!plugin.getPrepareSessionManager().canStart(gameTypeEnum, area))
            return false;
        for (ChampionshipTeam championshipTeam : championshipTeams) {
            if (teamStatus.containsKey(championshipTeam))
                return false;
            for (UUID uuid : championshipTeam.getMembers()) {
                if (isPlayerUnavailableForStart(uuid, gameTypeEnum, showIntroduction, runMode))
                    return false;
            }
        }

        BaseMultiTeamGameInstance singleTeamArea = findAvailableMultiTeamInstance(gameTypeEnum, area);
        if (singleTeamArea == null) return false;

        return joinMultiTeamInstanceForTeams(gameTypeEnum, singleTeamArea, showIntroduction,
                runMode, List.of(championshipTeams));
    }

    /** Starts an explicitly selected runtime slot, used by same-map DAILY replicas such as Ace Race. */
    public synchronized boolean joinMultiTeamInstanceForTeams(
            @NotNull GameTypeEnum gameTypeEnum, @NotNull BaseMultiTeamGameInstance singleTeamArea,
            boolean showIntroduction, @NotNull GameRunMode runMode,
            @NotNull List<ChampionshipTeam> championshipTeams) {
        if (!isGameEnabled(gameTypeEnum) || singleTeamArea.getGameTypeEnum() != gameTypeEnum)
            return false;
        String mapName = singleTeamArea.getGameConfig().getConfigName();
        if (!plugin.getPrepareSessionManager().canStart(gameTypeEnum, mapName))
            return false;
        for (ChampionshipTeam championshipTeam : championshipTeams) {
            if (teamStatus.containsKey(championshipTeam)) return false;
            for (UUID uuid : championshipTeam.getMembers()) {
                if (isPlayerUnavailableForStart(uuid, gameTypeEnum, showIntroduction, runMode)) return false;
            }
        }
        for (ChampionshipTeam championshipTeam : championshipTeams)
            for (UUID uuid : championshipTeam.getMembers()) removeSpectator(uuid);

        singleTeamArea.prepareRunMode(runMode);
        singleTeamArea.setIntroductionEnabledForNextStart(showIntroduction);
        if (singleTeamArea.tryStartGame(championshipTeams)) {
            for (ChampionshipTeam championshipTeam : championshipTeams) {
                teamStatus.put(championshipTeam, singleTeamArea);
                addPlayerStatusByTeam(championshipTeam, singleTeamArea);
            }
            focusSpectatorsOn(singleTeamArea);
            return true;
        }

        singleTeamArea.prepareRunMode(GameRunMode.GAME);
        singleTeamArea.setIntroductionEnabledForNextStart(false);
        return false;
    }

    /** Public-play Bingo entry that carries an explicit transient roster through local or remote execution. */
    public CompletionStage<Boolean> joinBingoForTeams(@NotNull String area, boolean showIntroduction,
                                                      @NotNull GameRunMode runMode,
                                                      @NotNull List<ChampionshipTeam> teams) {
        if (teams.isEmpty()) return CompletableFuture.completedFuture(false);
        return bingoExecutionRouter.start(new BingoStartRequest(area, showIntroduction, runMode, teams));
    }

    /** Async-safe start surface used by schedules and commands; remote mode waits for its manifest row. */
    public CompletionStage<Boolean> joinSingleTeamAreaForAllTeamsAsync(
            @NotNull GameTypeEnum gameTypeEnum, @NotNull String area,
            boolean showIntroduction, @NotNull GameRunMode runMode) {
        if (gameTypeEnum == GameTypeEnum.Bingo)
            return bingoExecutionRouter.start(new BingoStartRequest(area, showIntroduction, runMode));
        return CompletableFuture.completedFuture(joinSingleTeamAreaForAllTeamsLocal(
                gameTypeEnum, area, showIntroduction, runMode));
    }

    public synchronized boolean joinSingleTeamAreaForPlayers(@NotNull GameTypeEnum gameTypeEnum, @NotNull String area, List<UUID> players) {
        return joinSingleTeamAreaForPlayers(gameTypeEnum, area, players, false);
    }

    public synchronized boolean joinSingleTeamAreaForPlayers(@NotNull GameTypeEnum gameTypeEnum, @NotNull String area,
                                                              List<UUID> players, boolean showIntroduction) {
        return joinSingleTeamAreaForPlayers(gameTypeEnum, area, players, showIntroduction, GameRunMode.GAME);
    }

    public synchronized boolean joinSingleTeamAreaForPlayers(@NotNull GameTypeEnum gameTypeEnum, @NotNull String area,
                                                              List<UUID> players, boolean showIntroduction,
                                                              @NotNull GameRunMode runMode) {
        if (!isGameEnabled(gameTypeEnum))
            return false;
        if (!plugin.getPrepareSessionManager().canStart(gameTypeEnum, area))
            return false;
        for (UUID playerUUID : players) {
            if (isPlayerUnavailableForStart(playerUUID, gameTypeEnum, showIntroduction, runMode))
                return false;
        }

        Set<ChampionshipTeam> championshipTeams = new HashSet<>();
        for (UUID playerUUID : players) {
            ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(playerUUID);
            if (championshipTeam == null)
                return false;

            championshipTeams.add(championshipTeam);
        }

        BaseMultiTeamGameInstance singleTeamArea = findAvailableMultiTeamInstance(gameTypeEnum, area);
        if (singleTeamArea == null) return false;

        for (UUID playerUUID : players) {
            removeSpectator(playerUUID);
        }

        singleTeamArea.prepareRunMode(runMode);
        singleTeamArea.setIntroductionEnabledForNextStart(showIntroduction);
        if (singleTeamArea.tryStartGame(championshipTeams.stream().toList(), players)) {
            for (UUID playerUUID : players) {
                playerStatus.put(playerUUID, singleTeamArea);
                roundTransitionHolds.remove(playerUUID);
                plugin.getVisibilityManager().reconcilePlayer(playerUUID);
            }
            focusSpectatorsOn(singleTeamArea);
            return true;
        }

        singleTeamArea.prepareRunMode(GameRunMode.GAME);
        singleTeamArea.setIntroductionEnabledForNextStart(false);
        return false;
    }

    public boolean joinSingleTeamAreaForAllTeams(@NotNull GameTypeEnum gameTypeEnum, @NotNull String area) {
        return joinSingleTeamAreaForAllTeams(gameTypeEnum, area, false);
    }

    /**
     * Direct-command variant that also admits explicit players, such as an administrator testing a
     * map without a formal championship team. Explicit players remain unscored in GAME mode and are
     * released through the same instance lifecycle as team members.
     */
    public boolean joinSingleTeamAreaForAllTeams(@NotNull GameTypeEnum gameTypeEnum, @NotNull String area,
                                                  @NotNull Collection<UUID> additionalPlayers) {
        if (gameTypeEnum == GameTypeEnum.Bingo) {
            return additionalPlayers.isEmpty() && bingoExecutionRouter.mode() == BingoExecutionMode.LOCAL
                    && bingoExecutionRouter.start(new BingoStartRequest(area, false, GameRunMode.GAME))
                    .toCompletableFuture().getNow(false);
        }
        return joinSingleTeamAreaForAllTeamsLocal(
                gameTypeEnum, area, false, GameRunMode.GAME, additionalPlayers);
    }

    public boolean joinSingleTeamAreaForAllTeams(@NotNull GameTypeEnum gameTypeEnum, @NotNull String area,
                                                  boolean showIntroduction) {
        return joinSingleTeamAreaForAllTeams(gameTypeEnum, area, showIntroduction, GameRunMode.GAME);
    }

    public boolean joinSingleTeamAreaForAllTeams(@NotNull GameTypeEnum gameTypeEnum, @NotNull String area,
                                                  boolean showIntroduction, @NotNull GameRunMode runMode) {
        if (gameTypeEnum == GameTypeEnum.Bingo) {
            return bingoExecutionRouter.mode() == BingoExecutionMode.LOCAL
                    && bingoExecutionRouter.start(new BingoStartRequest(area, showIntroduction, runMode))
                    .toCompletableFuture().getNow(false);
        }
        return joinSingleTeamAreaForAllTeamsLocal(gameTypeEnum, area, showIntroduction, runMode);
    }

    /** Non-mutating execution-plane readiness check used before committing a Bingo event round. */
    public boolean canStartBingo(@NotNull String area, boolean showIntroduction,
                                 @NotNull GameRunMode runMode) {
        return bingoExecutionRouter.canStart(new BingoStartRequest(area, showIntroduction, runMode));
    }

    private boolean joinSingleTeamAreaForAllTeamsLocal(
            @NotNull GameTypeEnum gameTypeEnum, @NotNull String area,
            boolean showIntroduction, @NotNull GameRunMode runMode) {
        if (!isGameEnabled(gameTypeEnum))
            return false;
        if (!plugin.getPrepareSessionManager().canStart(gameTypeEnum, area))
            return false;
        for (ChampionshipTeam championshipTeam : plugin.getTeamManager().getTeamList()) {
            if (teamStatus.containsKey(championshipTeam))
                return false;
            for (UUID uuid : championshipTeam.getMembers()) {
                if (isPlayerUnavailableForStart(uuid, gameTypeEnum, showIntroduction, runMode))
                    return false;
            }
        }

        BaseMultiTeamGameInstance singleTeamArea = findAvailableMultiTeamInstance(gameTypeEnum, area);
        if (singleTeamArea == null) return false;

        for (ChampionshipTeam championshipTeam : plugin.getTeamManager().getTeamList()) {
            for (UUID uuid : championshipTeam.getMembers()) removeSpectator(uuid);
        }

        singleTeamArea.prepareRunMode(runMode);
        singleTeamArea.setIntroductionEnabledForNextStart(showIntroduction);
        if (singleTeamArea.tryStartGame(plugin.getTeamManager().getTeamList())) {
            for (ChampionshipTeam championshipTeam : plugin.getTeamManager().getTeamList()) {
                teamStatus.put(championshipTeam, singleTeamArea);
                addPlayerStatusByTeam(championshipTeam, singleTeamArea);
            }
            focusSpectatorsOn(singleTeamArea);
            return true;
        }

        singleTeamArea.prepareRunMode(GameRunMode.GAME);
        singleTeamArea.setIntroductionEnabledForNextStart(false);
        return false;
    }

    private boolean joinSingleTeamAreaForAllTeamsLocal(
            @NotNull GameTypeEnum gameTypeEnum, @NotNull String area,
            boolean showIntroduction, @NotNull GameRunMode runMode,
            @NotNull Collection<UUID> additionalPlayers) {
        if (!isGameEnabled(gameTypeEnum))
            return false;
        if (!plugin.getPrepareSessionManager().canStart(gameTypeEnum, area))
            return false;

        List<ChampionshipTeam> teams = plugin.getTeamManager().getTeamList();
        LinkedHashSet<UUID> participants = new LinkedHashSet<>();
        for (ChampionshipTeam championshipTeam : teams) {
            participants.addAll(championshipTeam.getMembers());
            if (teamStatus.containsKey(championshipTeam))
                return false;
        }
        participants.addAll(additionalPlayers);
        if (participants.isEmpty()) return false;
        for (UUID uuid : participants)
            if (isPlayerUnavailableForStart(uuid, gameTypeEnum, showIntroduction, runMode)) return false;

        BaseMultiTeamGameInstance singleTeamArea = findAvailableMultiTeamInstance(gameTypeEnum, area);
        if (singleTeamArea == null) return false;

        for (UUID uuid : participants) removeSpectator(uuid);

        singleTeamArea.prepareRunMode(runMode);
        singleTeamArea.setIntroductionEnabledForNextStart(showIntroduction);
        if (singleTeamArea.tryStartGame(teams, List.copyOf(participants))) {
            for (ChampionshipTeam championshipTeam : teams) {
                teamStatus.put(championshipTeam, singleTeamArea);
            }
            for (UUID uuid : participants) {
                playerStatus.put(uuid, singleTeamArea);
                roundTransitionHolds.remove(uuid);
                plugin.getVisibilityManager().reconcilePlayer(uuid);
                Player player = Bukkit.getPlayer(uuid);
                if (player != null && plugin.getSidebarManager() != null)
                    plugin.getSidebarManager().invalidate(player);
            }
            focusSpectatorsOn(singleTeamArea);
            return true;
        }

        singleTeamArea.prepareRunMode(GameRunMode.GAME);
        singleTeamArea.setIntroductionEnabledForNextStart(false);
        return false;
    }

    /** Resolves one idle runtime copy for a configured multi-team map, rather than always using copy zero. */
    @Nullable
    private BaseMultiTeamGameInstance findAvailableMultiTeamInstance(
            @NotNull GameTypeEnum gameType, @NotNull String mapName) {
        BaseGameInstanceManager<? extends BaseGameInstance> manager = areaManagers.get(gameType);
        if (manager == null) return null;
        BaseGameInstance representative = manager.getArea(mapName);
        if (!(representative instanceof BaseMultiTeamGameInstance)) return null;
        return manager.getRuntimeInstances().stream()
                .filter(instance -> instance instanceof BaseMultiTeamGameInstance)
                .filter(instance -> instance.getGameConfig() == representative.getGameConfig())
                .filter(instance -> instance.getGameStageEnum() == GameStageEnum.WAITING)
                .filter(instance -> plugin.getDailyManager() == null
                        || plugin.getDailyManager().session(instance) == null)
                .sorted(Comparator.comparingInt(BaseGameInstance::getCopyIndex))
                .map(instance -> (BaseMultiTeamGameInstance) instance)
                .findFirst().orElse(null);
    }

    /** Atomically reserves the normal team/player ownership maps for one remote Bingo execution. */
    public synchronized boolean reserveRemoteBingo(@NotNull RemoteBingoInstance instance,
                                                   @NotNull GameRunMode runMode,
                                                   boolean showIntroduction) {
        return reserveRemoteBingo(instance, runMode, showIntroduction, plugin.getTeamManager().getTeamList());
    }

    public synchronized boolean reserveRemoteBingo(@NotNull RemoteBingoInstance instance,
                                                   @NotNull GameRunMode runMode,
                                                   boolean showIntroduction,
                                                   @NotNull List<ChampionshipTeam> teams) {
        if (!canReserveRemoteBingo(runMode, showIntroduction, teams)) return false;
        if (!instance.reserve(teams, runMode)) return false;

        for (ChampionshipTeam team : teams) {
            teamStatus.put(team, instance);
            for (UUID playerId : team.getMembers()) {
                removeSpectator(playerId);
                playerStatus.put(playerId, instance);
                roundTransitionHolds.remove(playerId);
                plugin.getVisibilityManager().reconcilePlayer(playerId);
            }
        }
        remoteBingoInstances.put(instance.matchId(), instance);
        if (runMode == GameRunMode.EVENT) spectatorFocus = instance;
        return true;
    }

    /** Checks the same ownership constraints as {@link #reserveRemoteBingo} without changing them. */
    public synchronized boolean canReserveRemoteBingo(@NotNull GameRunMode runMode,
                                                       boolean showIntroduction) {
        return canReserveRemoteBingo(runMode, showIntroduction, plugin.getTeamManager().getTeamList());
    }

    public synchronized boolean canReserveRemoteBingo(@NotNull GameRunMode runMode,
                                                       boolean showIntroduction,
                                                       @NotNull List<ChampionshipTeam> teams) {
        if (!isGameEnabled(GameTypeEnum.Bingo)) return false;
        if (teams.stream().flatMap(team -> team.getOnlinePlayers().stream()).findAny().isEmpty()) return false;
        for (ChampionshipTeam team : teams) {
            if (teamStatus.containsKey(team)) return false;
            for (UUID playerId : team.getMembers()) {
                if (isPlayerUnavailableForStart(playerId, GameTypeEnum.Bingo,
                        showIntroduction, runMode)) return false;
            }
        }
        return true;
    }

    /** Unteamed online viewers frozen into the remote manifest and owned until settlement. */
    public synchronized Set<UUID> reserveRemoteBingoSpectators(@NotNull RemoteBingoInstance instance) {
        if (!instance.isEventRun()) return Set.of();
        Set<UUID> result = new LinkedHashSet<>();
        for (Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
            if (plugin.getTeamManager().getTeamByPlayer(player) != null) continue;
            UUID playerId = player.getUniqueId();
            BaseGameInstance previous = playerSpectatorStatus.get(playerId);
            if (previous != null && previous != instance) previous.onlyRemoveSpectatorFromList(playerId);
            playerSpectatorStatus.put(playerId, instance);
            instance.addSpectatorWithoutTeleport(playerId);
            plugin.getVisibilityManager().reconcilePlayer(playerId);
            result.add(playerId);
        }
        return Set.copyOf(result);
    }

    public synchronized void abortRemoteBingo(@NotNull RemoteBingoInstance instance) {
        boolean interruptedEvent = instance.isEventRun();
        releaseInstanceParticipants(instance);
        List<UUID> spectatorIds = playerSpectatorStatus.entrySet().stream()
                .filter(entry -> entry.getValue() == instance).map(Map.Entry::getKey).toList();
        for (UUID spectatorId : spectatorIds) {
            playerSpectatorStatus.remove(spectatorId, instance);
            instance.onlyRemoveSpectatorFromList(spectatorId);
            plugin.getVisibilityManager().reconcilePlayer(spectatorId);
        }
        remoteBingoInstances.remove(instance.matchId(), instance);
        instance.abortFromRemote();
        if (plugin.getDailyManager() != null) plugin.getDailyManager().abort(instance);
        instance.dispose();
        if (interruptedEvent) {
            plugin.getScheduleManager().abortFormalEvent(GameTypeEnum.Bingo,
                    "远端执行中止，已释放 Core 侧队伍与玩家占用");
        }
    }

    /** Starts one or more independent Battle Box instances from a shared map definition. */
    public synchronized boolean joinBattleBoxArea(@NotNull String area, @NotNull List<TwoVTwoVector> pairs) {
        return joinBattleBoxArea(area, pairs, false);
    }

    public synchronized boolean joinBattleBoxArea(@NotNull String area, @NotNull List<TwoVTwoVector> pairs,
                                                   boolean showIntroduction) {
        return joinBattleBoxInstances(area, pairs, showIntroduction) != null;
    }

    /** Returns the exact instances started for round-completion tracking, or {@code null} on failure. */
    public synchronized @Nullable List<BattleBoxArea> joinBattleBoxInstances(
            @NotNull String area, @NotNull List<TwoVTwoVector> pairs, boolean showIntroduction) {
        return joinBattleBoxInstances(area, pairs, showIntroduction, GameRunMode.GAME);
    }

    public synchronized @Nullable List<BattleBoxArea> joinBattleBoxInstances(
            @NotNull String area, @NotNull List<TwoVTwoVector> pairs, boolean showIntroduction,
            @NotNull GameRunMode runMode) {
        if (!isGameEnabled(GameTypeEnum.BattleBox))
            return null;
        if (!plugin.getPrepareSessionManager().canStart(GameTypeEnum.BattleBox, area))
            return null;
        if (pairs.isEmpty())
            return null;
        Set<ChampionshipTeam> teams = new LinkedHashSet<>();
        for (TwoVTwoVector pair : pairs) {
            teams.add(pair.getTeamOne());
            teams.add(pair.getTeamTwo());
        }
        if (teams.size() != pairs.size() * 2)
            return null;
        for (ChampionshipTeam team : teams) {
            if (teamStatus.containsKey(team))
                return null;
            for (UUID uuid : team.getMembers()) {
                if (isPlayerUnavailableForStart(uuid, GameTypeEnum.BattleBox, showIntroduction, runMode))
                    return null;
            }
        }
        List<BattleBoxArea> pool = battleBoxManager.getMapInstances(area);
        List<BattleBoxArea> selected = pool.stream()
                .filter(instance -> instance.getGameStageEnum() == GameStageEnum.WAITING)
                .limit(pairs.size())
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        if (selected.size() < pairs.size())
            return null;

        java.util.concurrent.CompletableFuture<Void> startGate = new java.util.concurrent.CompletableFuture<>();
        selected.forEach(instance -> instance.coordinateStartWith(startGate));

        for (ChampionshipTeam team : teams) {
            for (UUID uuid : team.getMembers()) {
                removeSpectator(uuid);
            }
        }

        for (int i = 0; i < pairs.size(); i++) {
            BattleBoxArea instance = selected.get(i);
            TwoVTwoVector pair = pairs.get(i);
            instance.prepareRunMode(runMode);
            instance.setIntroductionEnabledForNextStart(showIntroduction);
            if (!instance.tryStartGame(pair.getTeamOne(), pair.getTeamTwo())) {
                startGate.complete(null);
                instance.setIntroductionEnabledForNextStart(false);
                return null;
            }
            teamStatus.put(pair.getTeamOne(), instance);
            teamStatus.put(pair.getTeamTwo(), instance);
            addPlayerStatusByTeam(pair.getTeamOne(), instance);
            addPlayerStatusByTeam(pair.getTeamTwo(), instance);
        }
        java.util.concurrent.CompletableFuture.allOf(selected.stream()
                .map(BattleBoxArea::getStartPreloadFuture)
                .toArray(java.util.concurrent.CompletableFuture[]::new))
                .whenComplete((unused, error) -> plugin.getServer().getScheduler()
                        .runTask(plugin, () -> startGate.complete(null)));
        focusSpectatorsOn(selected.getFirst());
        return List.copyOf(selected);
    }

    /** Battle-Box-style parallel start for Parkour Tag: each pairing runs in its own stamped arena copy. */
    public synchronized boolean joinParkourTagArea(@NotNull String area, @NotNull List<TwoVTwoVector> pairs) {
        return joinParkourTagArea(area, pairs, false);
    }

    public synchronized boolean joinParkourTagArea(@NotNull String area, @NotNull List<TwoVTwoVector> pairs,
                                                    boolean showIntroduction) {
        return joinParkourTagInstances(area, pairs, showIntroduction) != null;
    }

    /** Returns the exact instances started for round-completion tracking, or {@code null} on failure. */
    public synchronized @Nullable List<ParkourTagArea> joinParkourTagInstances(
            @NotNull String area, @NotNull List<TwoVTwoVector> pairs, boolean showIntroduction) {
        return joinParkourTagInstances(area, pairs, showIntroduction, GameRunMode.GAME);
    }

    public synchronized @Nullable List<ParkourTagArea> joinParkourTagInstances(
            @NotNull String area, @NotNull List<TwoVTwoVector> pairs, boolean showIntroduction,
            @NotNull GameRunMode runMode) {
        if (!isGameEnabled(GameTypeEnum.ParkourTag))
            return null;
        if (!plugin.getPrepareSessionManager().canStart(GameTypeEnum.ParkourTag, area))
            return null;
        if (pairs.isEmpty())
            return null;
        Set<ChampionshipTeam> teams = new LinkedHashSet<>();
        for (TwoVTwoVector pair : pairs) {
            teams.add(pair.getTeamOne());
            teams.add(pair.getTeamTwo());
        }
        if (teams.size() != pairs.size() * 2)
            return null;
        for (ChampionshipTeam team : teams) {
            if (teamStatus.containsKey(team))
                return null;
            for (UUID uuid : team.getMembers()) {
                if (isPlayerUnavailableForStart(uuid, GameTypeEnum.ParkourTag, showIntroduction, runMode))
                    return null;
            }
        }
        List<ParkourTagArea> pool = parkourTagManager.getMapInstances(area);
        List<ParkourTagArea> selected = pool.stream()
                .filter(instance -> instance.getGameStageEnum() == GameStageEnum.WAITING)
                .limit(pairs.size())
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        if (selected.size() < pairs.size())
            return null;

        java.util.concurrent.CompletableFuture<Void> startGate = new java.util.concurrent.CompletableFuture<>();
        selected.forEach(instance -> instance.coordinateStartWith(startGate));

        for (ChampionshipTeam team : teams) {
            for (UUID uuid : team.getMembers()) {
                removeSpectator(uuid);
            }
        }

        for (int i = 0; i < pairs.size(); i++) {
            ParkourTagArea instance = selected.get(i);
            TwoVTwoVector pair = pairs.get(i);
            instance.prepareRunMode(runMode);
            instance.setIntroductionEnabledForNextStart(showIntroduction);
            if (!instance.tryStartGame(pair.getTeamOne(), pair.getTeamTwo())) {
                startGate.complete(null);
                instance.setIntroductionEnabledForNextStart(false);
                return null;
            }
            teamStatus.put(pair.getTeamOne(), instance);
            teamStatus.put(pair.getTeamTwo(), instance);
            addPlayerStatusByTeam(pair.getTeamOne(), instance);
            addPlayerStatusByTeam(pair.getTeamTwo(), instance);
        }
        java.util.concurrent.CompletableFuture.allOf(selected.stream()
                .map(ParkourTagArea::getStartPreloadFuture)
                .toArray(java.util.concurrent.CompletableFuture[]::new))
                .whenComplete((unused, error) -> plugin.getServer().getScheduler()
                        .runTask(plugin, () -> startGate.complete(null)));
        focusSpectatorsOn(selected.getFirst());
        return List.copyOf(selected);
    }

    public BaseGameInstance getTeamCurrenArea(ChampionshipTeam championshipTeam) {
        BaseGameInstance active = teamStatus.get(championshipTeam);
        if (active != null) return active;
        for (UUID uuid : championshipTeam.getMembers()) {
            RoundTransitionHold hold = roundTransitionHolds.get(uuid);
            if (hold != null) return hold.instance();
        }
        return null;
    }

    private void addPlayerStatusByTeam(ChampionshipTeam championshipTeam, BaseGameInstance baseArea) {
        for (UUID uuid : championshipTeam.getMembers()) {
            playerStatus.put(uuid, baseArea);
            roundTransitionHolds.remove(uuid);
            plugin.getVisibilityManager().reconcilePlayer(uuid);
            Player player = org.bukkit.Bukkit.getPlayer(uuid);
            if (player != null && plugin.getSidebarManager() != null) plugin.getSidebarManager().invalidate(player);
        }
    }

    private boolean isPlayerUnavailableForStart(UUID uuid, GameTypeEnum gameType, boolean showIntroduction,
                                                GameRunMode requestedMode) {
        if (playerStatus.containsKey(uuid)) return true;
        ChampionshipTeam formalTeam = plugin.getTeamManager().getFormalTeamByPlayer(uuid);
        if (formalTeam != null && plugin.getTeamManager().isMutationPending(formalTeam)) return true;
        RoundTransitionHold hold = roundTransitionHolds.get(uuid);
        if (hold == null) return false;
        return requestedMode != GameRunMode.EVENT || showIntroduction
                || hold.mode() != GameRunMode.EVENT
               || hold.instance().getGameTypeEnum() != gameType;
    }

    /** Marks participants as waiting while leaving them at their round-end locations. */
    public void holdParticipantsForNextRound(@NotNull BaseGameInstance instance,
                                             @NotNull Collection<UUID> participants) {
        for (UUID uuid : participants) {
            roundTransitionHolds.put(uuid, new RoundTransitionHold(instance, instance.getRunMode()));
            Player player = org.bukkit.Bukkit.getPlayer(uuid);
            if (player == null) continue;
            instance.sanitizeParticipantForLobby(player, false);
        }
    }

    /** Keeps spectators attached to a completed event instance until the next round can adopt them. */
    public void holdSpectatorsForNextRound(@NotNull BaseGameInstance instance) {
        for (UUID uuid : instance.getSpectatorUniqueIds())
            spectatorTransitionHolds.put(uuid, new SpectatorTransitionHold(instance));
    }

    public boolean isWaitingForNextRound(@NotNull UUID uuid) {
        return roundTransitionHolds.containsKey(uuid);
    }

    /** Restores a reconnected participant's waiting state without moving them. */
    public boolean restoreNextRoundHold(@NotNull Player player) {
        RoundTransitionHold hold = roundTransitionHolds.get(player.getUniqueId());
        if (hold == null) return false;
        hold.instance().sanitizeParticipantForLobby(player, false);
        return true;
    }

    public void teamGameEndHandler(TeamGameEndEvent event) {
        // Participant ownership is retained through the visible settlement phase.
    }

    public void singleTeamGameEndHandler(SingleGameEndEvent event) {
        // Participant ownership is retained through the visible settlement phase.
    }

    /** Clears only mappings owned by the instance being finalized. */
    public void releaseInstanceParticipants(@NotNull BaseGameInstance instance) {
        spectatorManager.onAreaReleased(instance);
        pendingFinaleAudience.remove(instance);
        teamStatus.entrySet().removeIf(entry -> entry.getValue() == instance);
        List<UUID> released = playerStatus.entrySet().stream()
                .filter(entry -> entry.getValue() == instance).map(Map.Entry::getKey).toList();
        playerStatus.entrySet().removeIf(entry -> entry.getValue() == instance);
        for (UUID playerId : released) plugin.getVisibilityManager().reconcilePlayer(playerId);
    }

    public void releaseInstancePlayers(@NotNull BaseGameInstance instance, @NotNull Set<UUID> players) {
        for (UUID player : players) {
            if (playerStatus.remove(player, instance)) plugin.getVisibilityManager().reconcilePlayer(player);
        }
    }

    @Nullable
    public BaseGameInstance getBasePlayerArea(UUID uuid) {
        return playerStatus.get(uuid);
    }

    @Nullable
    public BaseGameInstance getPlayerSpectatorStatus(UUID uuid) {
        return playerSpectatorStatus.get(uuid);
    }

    public synchronized boolean spectateArea(@NotNull Player player, @NotNull BaseGameInstance baseArea) {
        UUID uuid = player.getUniqueId();
        if (!canJoinSpectatorArea(player, baseArea)) {
            return false;
        }
        if (playerSpectatorStatus.containsKey(uuid)) {
            return false;
        }
        if (playerStatus.containsKey(uuid)) {
            return false;
        }

        playerSpectatorStatus.put(uuid, baseArea);
        spectatorManager.prepareExternal(player);
        baseArea.addSpectator(player);
        plugin.getVisibilityManager().reconcilePlayer(uuid);
        if (plugin.getSidebarManager() != null) plugin.getSidebarManager().invalidate(player);
        return true;
    }

    /** Opens the live arena selector used by the player-facing spectate command. */
    public void openSpectateMenu(@NotNull Player player) {
        spectateMenu.open(player);
    }

    /** Opens the nine-slot spectator quick-controls menu. */
    public void openSpectatorControls(@NotNull Player player) {
        spectatorManager.openControls(player);
    }

    /** Applies the same roster restriction as the explicit spectate command. Automatic routing bypasses it. */
    public boolean canManuallySpectate(@NotNull Player player) {
        if (player.hasPermission(MainCommand.ADMIN_PERMISSION)) return true;
        if (!CCConfig.STRICT_SPECTATOR_RULE) return true;
        ChampionshipTeam team = plugin.getTeamManager().getTeamByPlayer(player);
        if (plugin.getRankManager().getRound() != 7 && team != null
                && !player.hasPermission(ChampionshipPermissions.REFEREE)) {
            player.sendMessage(ink.ziip.championshipscore.configuration.config.message.MessageConfig.SPECTATOR_IS_PLAYER);
            return false;
        }
        return true;
    }

    /** Lifecycle-active instances used by internal maintenance checks such as map rename protection. */
    public List<BaseGameInstance> getSpectatableInstances() {
        return getSpectatableInstances(null);
    }

    /** Instances this viewer may enter, including pre-game arenas for administrators. */
    public List<BaseGameInstance> getSpectatableInstances(@Nullable Player viewer) {
        List<BaseGameInstance> instances = new ArrayList<>(areaManagers.entrySet().stream()
                .filter(entry -> isGameEnabled(entry.getKey()) && loadedGameManagers.contains(entry.getKey()))
                .flatMap(entry -> entry.getValue().getRuntimeInstances().stream()
                        .map(instance -> (BaseGameInstance) instance))
                .filter(instance -> viewer == null
                        ? isInstanceActivelyRunning(instance)
                        : canJoinSpectatorArea(viewer, instance))
                .toList());
        remoteBingoInstances.values().stream()
                .filter(instance -> viewer == null
                        ? isInstanceActivelyRunning(instance)
                        : canJoinSpectatorArea(viewer, instance))
                .forEach(instances::add);
        instances.sort(Comparator
                .comparingInt((BaseGameInstance instance) -> instance.getGameTypeEnum().ordinal())
                .thenComparing(instance -> instance.getGameConfig().getAreaName(),
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                .thenComparingInt(BaseGameInstance::getCopyIndex)
                .thenComparing(this::stableInstanceKey));
        return List.copyOf(instances);
    }

    /** Active copies for one configured map, including Core-side handles for remote Bingo matches. */
    public @NotNull List<BaseGameInstance> getSpectatableMapInstances(
            @NotNull GameTypeEnum gameType, @NotNull String mapName) {
        return getSpectatableInstances().stream()
                .filter(instance -> instance.getGameTypeEnum() == gameType)
                .filter(instance -> mapMatches(instance, mapName))
                .sorted(Comparator.comparingInt(BaseGameInstance::getCopyIndex)
                        .thenComparing(this::stableInstanceKey))
                .toList();
    }

    /** Copies for one map that the given viewer is currently allowed to enter. */
    public @NotNull List<BaseGameInstance> getSpectatableMapInstances(
            @NotNull Player viewer, @NotNull GameTypeEnum gameType, @NotNull String mapName) {
        return getSpectatableInstances(viewer).stream()
                .filter(instance -> instance.getGameTypeEnum() == gameType)
                .filter(instance -> mapMatches(instance, mapName))
                .sorted(Comparator.comparingInt(BaseGameInstance::getCopyIndex)
                        .thenComparing(this::stableInstanceKey))
                .toList();
    }

    /** Human-readable identity shared by the GUI, direct command and status messages. */
    public @NotNull String getSpectatorDisplayName(@NotNull BaseGameInstance instance) {
        String name = instance.getGameConfig().getAreaName();
        if (name == null || name.isBlank()) name = instance.getGameConfig().getConfigName();
        if (instance instanceof RemoteBingoInstance remote)
            return name + " · 对局 " + remote.matchId().toString().substring(0, 8);
        if (instance instanceof ParkourTagArea)
            return name + " · 分区 " + (instance.getCopyIndex() + 1);
        if (instance instanceof BattleBoxArea)
            return name + " · 分区 " + (instance.getCopyIndex() + 1);
        if (instance instanceof ink.ziip.championshipscore.api.game.acerace.AceRaceArea)
            return name + " · 实例 " + (instance.getCopyIndex() + 1);
        return name;
    }

    /** Command token for selecting one copy. Local replicas use their one-based index; remote runs use match ID. */
    public @NotNull String getSpectatorInstanceToken(@NotNull BaseGameInstance instance) {
        if (instance instanceof RemoteBingoInstance remote)
            return remote.matchId().toString().substring(0, 8);
        return Integer.toString(instance.getCopyIndex() + 1);
    }

    /** Selects or switches to one live arena without an intermediate lobby teleport. */
    public synchronized boolean selectSpectatorArea(@NotNull Player player, @NotNull BaseGameInstance target) {
        if (!canJoinSpectatorArea(player, target) || playerStatus.containsKey(player.getUniqueId())) return false;
        if (playerSpectatorStatus.get(player.getUniqueId()) == target) return true;
        moveSpectatorTo(player, target);
        return true;
    }

    /** Selects a live arena and teleports directly to a destination inside that same spectator instance. */
    public synchronized boolean selectSpectatorArea(@NotNull Player player, @NotNull BaseGameInstance target,
                                                    @NotNull Location destination) {
        if (!canJoinSpectatorArea(player, target) || playerStatus.containsKey(player.getUniqueId())) return false;
        if (playerSpectatorStatus.get(player.getUniqueId()) == target) {
            target.teleportSpectatorAsync(player, destination);
            return true;
        }
        moveSpectatorTo(player, target, destination);
        return true;
    }

    private boolean mapMatches(@NotNull BaseGameInstance instance, @NotNull String mapName) {
        String configName = instance.getGameConfig().getConfigName();
        String areaName = instance.getGameConfig().getAreaName();
        return configName != null && configName.equalsIgnoreCase(mapName)
                || areaName != null && areaName.equalsIgnoreCase(mapName);
    }

    private @NotNull String stableInstanceKey(@NotNull BaseGameInstance instance) {
        return instance instanceof RemoteBingoInstance remote ? remote.matchId().toString() : "";
    }

    /** Routes an unteamed player joining mid-game to the current spectator focus. */
    public synchronized boolean spectateCurrentGame(@NotNull Player player) {
        if (plugin.getTeamManager().getTeamByPlayer(player) != null || playerStatus.containsKey(player.getUniqueId()))
            return false;
        BaseGameInstance active = getCurrentSpectatorFocus();
        return active != null && spectateArea(player, active);
    }

    /** Releases spectators attached to event-owned instances only; standalone games are untouched. */
    public synchronized void releaseEventSpectatorsForGame(@NotNull GameTypeEnum gameType) {
        List<Map.Entry<UUID, BaseGameInstance>> entries = playerSpectatorStatus.entrySet().stream()
                .filter(entry -> entry.getValue().getGameTypeEnum() == gameType
                        && (entry.getValue().isEventRun()
                        || isHeldSpectator(entry.getKey(), entry.getValue())))
                .toList();
        for (Map.Entry<UUID, BaseGameInstance> entry : entries) {
            Player player = org.bukkit.Bukkit.getPlayer(entry.getKey());
            if (player != null) entry.getValue().removeSpectator(player);
            else entry.getValue().onlyRemoveSpectatorFromList(entry.getKey());
            playerSpectatorStatus.remove(entry.getKey(), entry.getValue());
            plugin.getVisibilityManager().reconcilePlayer(entry.getKey());
        }
        spectatorTransitionHolds.entrySet().removeIf(entry ->
                entry.getValue().instance().getGameTypeEnum() == gameType);
    }

    public void clearSpectatorStatus(@NotNull UUID uuid, @NotNull BaseGameInstance expected) {
        boolean removed = playerSpectatorStatus.remove(uuid, expected);
        spectatorTransitionHolds.computeIfPresent(uuid, (ignored, hold) ->
                hold.instance() == expected ? null : hold);
        if (spectatorFocus == expected && expected.getOnlineSpectators().isEmpty()
                && !isInstanceAvailableForSpectating(expected)) {
            spectatorFocus = null;
        }
        if (removed) {
            Player online = Bukkit.getPlayer(uuid);
            if (online == null) {
                spectatorManager.forget(uuid);
            } else {
                // The area normally performs this during removeSpectator. Also cover rejected remote
                // admission and failed async teleports, while not stealing a fast transfer to a new area.
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (playerSpectatorStatus.get(uuid) == null && spectatorManager.areaOf(uuid) == expected)
                        spectatorManager.leavePresentation(online);
                });
            }
            plugin.getVisibilityManager().reconcilePlayer(uuid);
        }
    }

    private synchronized void focusSpectatorsOn(@NotNull BaseGameInstance startedInstance) {
        if (!startedInstance.isEventRun()) return;
        spectatorFocus = startedInstance;
        if (!isRegularSpectatingStage(startedInstance.getGameStageEnum())) return;
        BaseGameInstance current = getCurrentSpectatorFocus();
        BaseGameInstance target = current != null && current.getGameTypeEnum() == startedInstance.getGameTypeEnum()
                && isRegularSpectatingStage(current.getGameStageEnum()) ? current : startedInstance;
        spectatorFocus = target;
        transferHeldSpectatorsTo(target);

        for (Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
            if (plugin.getTeamManager().getTeamByPlayer(player) != null) continue;
            UUID uuid = player.getUniqueId();
            BaseGameInstance previous = playerSpectatorStatus.get(uuid);
            if (previous == target) continue;
            moveSpectatorTo(player, target);
        }
    }

    private boolean isHeldSpectator(@NotNull UUID uuid, @NotNull BaseGameInstance instance) {
        SpectatorTransitionHold hold = spectatorTransitionHolds.get(uuid);
        return hold != null && hold.instance() == instance;
    }

    /** Transfers both online and offline spectators without a lobby hop once the next round is live. */
    private void transferHeldSpectatorsTo(@NotNull BaseGameInstance target) {
        List<Map.Entry<UUID, SpectatorTransitionHold>> holds = spectatorTransitionHolds.entrySet().stream()
                .filter(entry -> entry.getValue().instance().getGameTypeEnum() == target.getGameTypeEnum())
                .toList();
        for (Map.Entry<UUID, SpectatorTransitionHold> entry : holds) {
            UUID uuid = entry.getKey();
            SpectatorTransitionHold hold = entry.getValue();
            if (!spectatorTransitionHolds.remove(uuid, hold)) continue;

            BaseGameInstance previous = playerSpectatorStatus.get(uuid);
            if (previous != hold.instance()) continue;

            Player player = org.bukkit.Bukkit.getPlayer(uuid);
            if (previous == target) {
                if (player != null && player.isOnline())
                    target.teleportSpectatorAsync(player, target.getSpectatorSpawnLocation());
                continue;
            }
            if (player != null && player.isOnline()) {
                moveSpectatorTo(player, target);
                continue;
            }
            if (playerSpectatorStatus.replace(uuid, previous, target)) {
                previous.onlyRemoveSpectatorFromList(uuid);
                target.addSpectatorWithoutTeleport(uuid);
                plugin.getVisibilityManager().reconcilePlayer(uuid);
            }
        }
    }

    private void moveSpectatorTo(@NotNull Player player, @NotNull BaseGameInstance target) {
        moveSpectatorTo(player, target, target.getSpectatorSpawnLocation());
    }

    private void moveSpectatorTo(@NotNull Player player, @NotNull BaseGameInstance target,
                                 @NotNull Location destination) {
        UUID uuid = player.getUniqueId();
        BaseGameInstance previous = playerSpectatorStatus.get(uuid);
        if (previous == target) {
            target.teleportSpectatorAsync(player, destination);
            return;
        }
        spectatorTransitionHolds.remove(uuid);
        if (previous != null) previous.detachSpectator(player);
        if (plugin.getDailyManager() != null) plugin.getDailyManager().detachSpectator(uuid);
        playerSpectatorStatus.put(uuid, target);
        spectatorManager.prepareExternal(player);
        target.addSpectator(player, destination);
        plugin.getVisibilityManager().reconcilePlayer(uuid);
        if (plugin.getDailyManager() != null) plugin.getDailyManager().attachSpectator(target, uuid);
    }


    @Nullable
    private BaseGameInstance getCurrentSpectatorFocus() {
        BaseGameInstance focus = spectatorFocus;
        if (focus != null && isRegularSpectatingStage(focus.getGameStageEnum())) return focus;

        for (GameTypeEnum gameType : GameTypeEnum.values()) {
            BaseGameInstanceManager<? extends BaseGameInstance> manager = areaManagers.get(gameType);
            if (manager == null) continue;
            BaseGameInstance active = manager.getRuntimeInstances().stream()
                    .filter(BaseGameInstance::isEventRun)
                    .filter(instance -> isRegularSpectatingStage(instance.getGameStageEnum()))
                    .sorted(Comparator.comparing(instance -> instance.getGameConfig().getConfigName(),
                            String.CASE_INSENSITIVE_ORDER))
                    .findFirst().orElse(null);
            if (active != null) {
                spectatorFocus = active;
                return active;
            }
        }
        return null;
    }

    private boolean isInstanceAvailableForSpectating(@Nullable BaseGameInstance instance) {
        if (instance == null) return false;
        if (!instance.isEventRun()) return false;
        if (isInstanceActivelyRunning(instance)) return true;
        return instance.isEventRun() && instance.getGameStageEnum() == GameStageEnum.END;
    }

    public boolean isInstanceActivelyRunning(@NotNull BaseGameInstance instance) {
        return switch (instance.getGameStageEnum()) {
            case LOADING, PREPARATION, COUNTDOWN, PROGRESS -> true;
            default -> false;
        };
    }

    /** Called by an instance once rule introduction/preparation is actually available to spectators. */
    public void onInstancePreparationStarted(@NotNull BaseGameInstance instance) {
        focusSpectatorsOn(instance);
        activateFinaleAudience(instance);
    }

    /** Shared command, menu and area-admission policy. */
    public boolean canJoinSpectatorArea(@NotNull Player player, @NotNull BaseGameInstance instance) {
        boolean administrator = player.hasPermission(MainCommand.ADMIN_PERMISSION);
        if (!isSpectatingStageAllowed(instance.getGameStageEnum(), administrator)) return false;
        try {
            Location destination = instance.getSpectatorSpawnLocation();
            return destination != null && destination.getWorld() != null;
        } catch (RuntimeException ignored) {
            // Partially configured map-edit drafts must not break the whole selector/completer.
            return false;
        }
    }

    static boolean isSpectatingStageAllowed(@NotNull GameStageEnum stage, boolean administrator) {
        if (isRegularSpectatingStage(stage)) return true;
        return administrator && (stage == GameStageEnum.WAITING || stage == GameStageEnum.LOADING);
    }

    private static boolean isRegularSpectatingStage(@NotNull GameStageEnum stage) {
        return stage == GameStageEnum.PREPARATION
                || stage == GameStageEnum.COUNTDOWN
                || stage == GameStageEnum.PROGRESS;
    }

    public boolean leaveSpectating(@NotNull Player player) {
        UUID uuid = player.getUniqueId();
        if (playerSpectatorStatus.containsKey(uuid)) {
            BaseGameInstance baseArea = playerSpectatorStatus.get(uuid);
            baseArea.removeSpectator(player);
            playerSpectatorStatus.remove(uuid);
            if (plugin.getDailyManager() != null) plugin.getDailyManager().detachSpectator(uuid);
            spectatorTransitionHolds.remove(uuid);
            spectatorManager.leavePresentation(player);
            plugin.getVisibilityManager().reconcilePlayer(uuid);
            if (plugin.getSidebarManager() != null) plugin.getSidebarManager().invalidate(player);
            return true;
        }

        return false;
    }

    public void removeSpectator(@NotNull UUID uuid) {
        if (playerSpectatorStatus.containsKey(uuid)) {
            BaseGameInstance baseArea = playerSpectatorStatus.get(uuid);
            baseArea.removeSpectator(uuid);
            playerSpectatorStatus.remove(uuid);
            if (plugin.getDailyManager() != null) plugin.getDailyManager().detachSpectator(uuid);
            Player online = Bukkit.getPlayer(uuid);
            if (online != null) spectatorManager.leavePresentation(online);
            plugin.getVisibilityManager().reconcilePlayer(uuid);
        }
        spectatorTransitionHolds.remove(uuid);
    }

    public void removeSpectatingPlayerFromList(@NotNull UUID uuid) {
        if (playerSpectatorStatus.containsKey(uuid)) {
            BaseGameInstance baseArea = playerSpectatorStatus.get(uuid);
            baseArea.onlyRemoveSpectatorFromList(uuid);
            playerSpectatorStatus.remove(uuid);
            if (plugin.getDailyManager() != null) plugin.getDailyManager().detachSpectator(uuid);
            Player online = Bukkit.getPlayer(uuid);
            if (online != null) spectatorManager.leavePresentation(online);
            plugin.getVisibilityManager().reconcilePlayer(uuid);
        }
        spectatorTransitionHolds.remove(uuid);
    }

    private record RoundTransitionHold(BaseGameInstance instance, GameRunMode mode) {
   }

    private record SpectatorTransitionHold(BaseGameInstance instance) {
    }
}
