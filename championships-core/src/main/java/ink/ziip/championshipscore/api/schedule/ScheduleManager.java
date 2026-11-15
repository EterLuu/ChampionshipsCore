package ink.ziip.championshipscore.api.schedule;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseManager;
import ink.ziip.championshipscore.api.finale.FinaleGameDefinition;
import ink.ziip.championshipscore.api.finale.FinaleGameRegistry;
import ink.ziip.championshipscore.api.game.decarnival.runtime.DragonEggCarnivalArea;
import ink.ziip.championshipscore.api.game.dodgebolt.runtime.DodgeboltArea;
import ink.ziip.championshipscore.api.game.instance.BaseGameInstance;
import ink.ziip.championshipscore.api.game.model.GameRunMode;
import ink.ziip.championshipscore.api.game.model.GameTypeEnum;
import ink.ziip.championshipscore.api.schedule.acerace.AceRaceScheduleManager;
import ink.ziip.championshipscore.api.schedule.battlebox.BattleBoxScheduleManager;
import ink.ziip.championshipscore.api.schedule.bingo.BingoScheduleManager;
import ink.ziip.championshipscore.api.schedule.buildmart.BuildMartScheduleManager;
import ink.ziip.championshipscore.api.schedule.frostbite.FrostbiteScheduleManager;
import ink.ziip.championshipscore.api.schedule.hotycodydusky.HotyCodyDuskyScheduleManager;
import ink.ziip.championshipscore.api.schedule.laserbox.LaserBoxScheduleManager;
import ink.ziip.championshipscore.api.schedule.lasershowdown.LaserShowdownScheduleManager;
import ink.ziip.championshipscore.api.schedule.model.TwoVTwoVector;
import ink.ziip.championshipscore.api.schedule.parkourtag.ParkourTagScheduleManager;
import ink.ziip.championshipscore.api.schedule.parkourwarrior.ParkourWarriorScheduleManager;
import ink.ziip.championshipscore.api.schedule.riptiderush.RiptideRushScheduleManager;
import ink.ziip.championshipscore.api.schedule.skywars.SkyWarsScheduleManager;
import ink.ziip.championshipscore.api.schedule.tgttos.TGTTOSScheduleManager;
import ink.ziip.championshipscore.api.schedule.tntrun.TNTRunScheduleManager;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.message.EventMessages;
import ink.ziip.championshipscore.configuration.config.message.GameMessages;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.configuration.config.message.ScheduleMessageConfig;
import ink.ziip.championshipscore.configuration.config.message.SulfurSoccerMessages;
import ink.ziip.championshipscore.logging.LogText;
import ink.ziip.championshipscore.platform.bukkit.text.LegacyText;
import ink.ziip.championshipscore.presentation.text.CoreMessages;
import ink.ziip.championshipscore.shared.presentation.MessageTemplate;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class ScheduleManager extends BaseManager {
    /** Announces the actual pairings, including rounds generated from the latest standings. */
    public void announceRoundMatchups(GameTypeEnum game, int round, List<TwoVTwoVector> pairs) {
        CoreMessages.sendMessageToAllPlayers(roundMatchups(game, round, pairs));
    }

    static String roundMatchups(GameTypeEnum game, int round, List<TwoVTwoVector> pairs) {
        List<String> lines = new ArrayList<>();
        lines.add(MessageConfig.codeText("schedule-manager.round-matchups-border"));
        lines.add(MessageConfig.codeText("schedule-manager.round-matchups-header", game, round));
        for (int i = 0; i < pairs.size(); i++) {
            var pair = pairs.get(i);
            lines.add(
                    MessageConfig.codeText(
                            "schedule-manager.round-matchups-pair",
                            i + 1,
                            pair.getTeamOne().getColoredName(),
                            pair.getTeamTwo().getColoredName()));
        }
        lines.add(MessageConfig.codeText("schedule-manager.round-matchups-border"));
        return CoreMessages.getMessage(lines);
    }

    private final java.util.Map<
                    GameTypeEnum, ink.ziip.championshipscore.api.game.start.EventStartSelection>
            startSelections = new java.util.EnumMap<>(GameTypeEnum.class);

    public java.util.List<ChampionshipTeam> participatingTeams(GameTypeEnum game) {
        var selected = startSelections.get(game);
        return selected == null
                ? java.util.List.copyOf(plugin.getTeamManager().getTeamList())
                : selected.teams();
    }

    public ink.ziip.championshipscore.api.game.start.ArenaSelection selectedArenas(
            GameTypeEnum game) {
        var selected = startSelections.get(game);
        return selected == null
                ? ink.ziip.championshipscore.api.game.start.ArenaSelection.all()
                : selected.arenas();
    }

    public @Nullable String selectedMap(GameTypeEnum game) {
        var selected = startSelections.get(game);
        return selected == null ? null : selected.map();
    }

    public void clearStartSelection(GameTypeEnum game) {
        startSelections.remove(game);
    }

    /** Releases every schedule-owned hold after its listeners and clocks have stopped. */
    public void releaseSchedule(GameTypeEnum game) {
        cancelRoundSettlement(game);
        if (game == GameTypeEnum.Bingo && plugin.getRemoteBingoManager() != null)
            plugin.getRemoteBingoManager().releaseFinishedEventMatches();
        clearStartSelection(game);
        clearRoundPreparationCountdown();
        plugin.getGameManager().releaseEventSpectatorsForGame(game);
        plugin.getGameManager().releaseRoundTransitionHolds(game);
    }

    private void cancelRoundSettlement(GameTypeEnum game) {
        EventRoundSettlement settlement = roundSettlements.remove(game);
        if (settlement != null) settlement.cancel();
    }

    public EventAction startOrStopFormalEvent(
            ink.ziip.championshipscore.api.game.start.GameStartArguments arguments) {
        GameTypeEnum game = arguments.game();
        if (!supportsFormalEvent(game) || FinaleGameRegistry.isRegistered(game))
            return EventAction.UNSUPPORTED;
        if (isFormalEventRunning(game)) return startOrStopFormalEvent(game);
        var teams =
                new ink.ziip.championshipscore.api.game.start.GameStartService(plugin)
                        .resolveTeams(arguments, true, GameRunMode.EVENT);
        String map = arguments.map();
        if (map != null) {
            var manager = plugin.getGameManager().getAreaManager(game);
            map =
                    manager.getAreaNameList().stream()
                            .filter(name -> name.equalsIgnoreCase(arguments.map()))
                            .findFirst()
                            .orElseThrow(
                                    () ->
                                            new IllegalArgumentException(
                                                    MessageConfig.codeText(
                                                            "schedule-manager.start-or-stop-formal-event-1",
                                                            arguments.map())));
            if (!plugin.getPrepareSessionManager().canStart(game, map))
                throw new IllegalArgumentException(
                        MessageConfig.codeText(
                                "schedule-manager.start-or-stop-formal-event-2", map));
        }
        startSelections.put(
                game,
                new ink.ziip.championshipscore.api.game.start.EventStartSelection(
                        map, teams, arguments.arenas()));
        EventAction action;
        try {
            var service = new ink.ziip.championshipscore.api.game.start.GameStartService(plugin);
            var maps = FormalEventMapResolver.maps(plugin, game);
            if (maps.isEmpty())
                throw new IllegalArgumentException(
                        MessageConfig.codeText("schedule-manager.start-or-stop-formal-event-3"));
            for (String selected : new java.util.LinkedHashSet<>(maps))
                service.validateMap(game, selected, teams, arguments.arenas());
            action = startOrStopFormalEvent(game);
            if (action == EventAction.STARTED && !isFormalEventRunning(game))
                action = EventAction.UNAVAILABLE;
        } catch (RuntimeException failure) {
            clearStartSelection(game);
            throw failure;
        }
        if (action != EventAction.STARTED) clearStartSelection(game);
        return action;
    }

    private static final long RESULT_DISPLAY_TICKS = 200L;
    private static final int FIRST_ROUND_PREPARATION_SECONDS = 10;
    private static final int ROUND_TRANSITION_SECONDS = 10;

    public enum EventAction {
        STARTED,
        STOPPED,
        UNAVAILABLE,
        UNSUPPORTED
    }

    private final BukkitScheduler scheduler;
    private Map<GameTypeEnum, FormalSchedule> schedules = Map.of();
    private BukkitTask dodgeboltTransitionTask;
    private BukkitTask dragonEggCarnivalTransitionTask;
    private BukkitTask sulfurSoccerTransitionTask;
    private GameTypeEnum pendingFinaleRequest;
    private long finaleRequestGeneration;
    private BossBar roundPreparationBar;
    private final Map<GameTypeEnum, Set<BaseGameInstance>> pendingEventInstances =
            new EnumMap<>(GameTypeEnum.class);
    private final Map<GameTypeEnum, EventRoundSettlement> roundSettlements =
            new EnumMap<>(GameTypeEnum.class);

    public ScheduleManager(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
        scheduler = championshipsCore.getServer().getScheduler();
    }

    public LaserShowdownScheduleManager getLaserShowdownScheduleManager() {
        return (LaserShowdownScheduleManager) schedules.get(GameTypeEnum.LaserShowdown);
    }

    public SkyWarsScheduleManager getSkyWarsScheduleManager() {
        return (SkyWarsScheduleManager) schedules.get(GameTypeEnum.SkyWars);
    }

    public TNTRunScheduleManager getTntRunScheduleManager() {
        return (TNTRunScheduleManager) schedules.get(GameTypeEnum.TNTRun);
    }

    public TGTTOSScheduleManager getTgttosScheduleManager() {
        return (TGTTOSScheduleManager) schedules.get(GameTypeEnum.TGTTOS);
    }

    public BattleBoxScheduleManager getBattleBoxScheduleManager() {
        return (BattleBoxScheduleManager) schedules.get(GameTypeEnum.BattleBox);
    }

    public ParkourTagScheduleManager getParkourTagScheduleManager() {
        return (ParkourTagScheduleManager) schedules.get(GameTypeEnum.ParkourTag);
    }

    public ParkourWarriorScheduleManager getParkourWarriorScheduleManager() {
        return (ParkourWarriorScheduleManager) schedules.get(GameTypeEnum.ParkourWarrior);
    }

    public HotyCodyDuskyScheduleManager getHotyCodyDuskyScheduleManager() {
        return (HotyCodyDuskyScheduleManager) schedules.get(GameTypeEnum.HotyCodyDusky);
    }

    public BingoScheduleManager getBingoScheduleManager() {
        return (BingoScheduleManager) schedules.get(GameTypeEnum.Bingo);
    }

    public AceRaceScheduleManager getAceRaceScheduleManager() {
        return (AceRaceScheduleManager) schedules.get(GameTypeEnum.AceRace);
    }

    public BuildMartScheduleManager getBuildMartScheduleManager() {
        return (BuildMartScheduleManager) schedules.get(GameTypeEnum.BuildMart);
    }

    public RiptideRushScheduleManager getRiptideRushScheduleManager() {
        return (RiptideRushScheduleManager) schedules.get(GameTypeEnum.RiptideRush);
    }

    public FrostbiteScheduleManager getFrostbiteScheduleManager() {
        return (FrostbiteScheduleManager) schedules.get(GameTypeEnum.FrostbiteFrenzy);
    }

    public LaserBoxScheduleManager getLaserBoxScheduleManager() {
        return (LaserBoxScheduleManager) schedules.get(GameTypeEnum.LaserBox);
    }

    @Override
    public void load() {
        schedules = FormalScheduleRegistry.create(plugin);
        schedules.values().forEach(FormalSchedule::load);
    }

    @Override
    public void unload() {
        roundSettlements.values().forEach(EventRoundSettlement::cancel);
        roundSettlements.clear();
        if (dodgeboltTransitionTask != null) dodgeboltTransitionTask.cancel();
        if (dragonEggCarnivalTransitionTask != null) dragonEggCarnivalTransitionTask.cancel();
        if (sulfurSoccerTransitionTask != null) sulfurSoccerTransitionTask.cancel();
        sulfurSoccerTransitionTask = null;
        dodgeboltTransitionTask = null;
        dragonEggCarnivalTransitionTask = null;
        pendingFinaleRequest = null;
        startSelections.clear();
        finaleRequestGeneration++;
        clearRoundPreparationCountdown();
        schedules.values().forEach(FormalSchedule::unload);
        schedules = Map.of();
    }

    public void addRound(GameTypeEnum gameTypeEnum) {
        plugin.getRankManager().addGameOrder(gameTypeEnum, plugin.getRankManager().getRound() + 1);
    }

    public void resetRound() {
        plugin.getRankManager().resetGameOrder();
    }

    /**
     * @return whether this game has an implementation for the formal-event command surface.
     */
    public boolean supportsFormalEvent(@NotNull GameTypeEnum gameTypeEnum) {
        return FormalScheduleRegistry.supportedGames().contains(gameTypeEnum)
                || FinaleGameRegistry.isRegistered(gameTypeEnum);
    }

    /**
     * Starts an ordinary formal event or stops it when it is already running, for emergency
     * operation.
     */
    public EventAction startOrStopFormalEvent(@NotNull GameTypeEnum gameTypeEnum) {
        if (!supportsFormalEvent(gameTypeEnum) || FinaleGameRegistry.isRegistered(gameTypeEnum))
            return EventAction.UNSUPPORTED;
        if (isFormalEventRunning(gameTypeEnum)) {
            // A repeated start is the emergency-stop form of this command.  Stop the
            // scheduler and release every instance, just like /cc event stop; leaving
            // instances in PREPARATION/PROGRESS keeps their participants unavailable.
            stopFormalEvent(gameTypeEnum);
            return EventAction.STOPPED;
        }
        FormalSchedule schedule = schedules.get(gameTypeEnum);
        if (schedule == null) return EventAction.UNSUPPORTED;
        return schedule.startGame() ? EventAction.STARTED : EventAction.UNAVAILABLE;
    }

    /** Returns the concrete preflight rejection when a game exposes one. */
    public @Nullable String getFormalEventStartFailure(GameTypeEnum game) {
        FormalSchedule schedule = schedules.get(game);
        return schedule == null ? null : schedule.startFailureReason();
    }

    /** Stops the formal schedule and force-ends any actively running game instance. */
    public boolean stopFormalEvent(@NotNull GameTypeEnum gameTypeEnum) {
        if (FinaleGameRegistry.isRegistered(gameTypeEnum)) return stopFinale(gameTypeEnum);
        if (!supportsFormalEvent(gameTypeEnum) || !isFormalEventRunning(gameTypeEnum)) return false;
        endGameSchedule(gameTypeEnum);
        plugin.getGameManager().forceEndEventAreas(gameTypeEnum);
        return true;
    }

    /**
     * Closes a formal schedule when its external execution plane aborts before producing a result.
     */
    public boolean abortFormalEvent(@NotNull GameTypeEnum gameTypeEnum, @NotNull String reason) {
        if (!supportsFormalEvent(gameTypeEnum) || !isFormalEventRunning(gameTypeEnum)) return false;
        endGameSchedule(gameTypeEnum);
        plugin.getLogger()
                .warning(
                        LogText.formatGameLog(
                                gameTypeEnum,
                                "-",
                                MessageConfig.codeText("schedule-manager.abort-formal-event-1"),
                                MessageConfig.codeText("schedule-manager.abort-formal-event-2"),
                                reason));
        return true;
    }

    public boolean isFormalEventRunning(@NotNull GameTypeEnum gameTypeEnum) {
        if (FinaleGameRegistry.isRegistered(gameTypeEnum)) return isFinaleRunning(gameTypeEnum);
        FormalSchedule schedule = schedules.get(gameTypeEnum);
        return schedule != null && schedule.isEnabled();
    }

    public boolean hasRunningFormalEvent() {
        for (GameTypeEnum game : GameTypeEnum.values()) {
            if (supportsFormalEvent(game) && isFormalEventRunning(game)) return true;
        }
        return false;
    }

    public synchronized boolean isFinaleRunning(@NotNull GameTypeEnum gameType) {
        if (!FinaleGameRegistry.isRegistered(gameType)) return false;
        if (pendingFinaleRequest == gameType) return true;
        boolean transitioning =
                switch (gameType) {
                    case Dodgebolt -> dodgeboltTransitionTask != null;
                    case DragonEggCarnival -> dragonEggCarnivalTransitionTask != null;
                    case SulfurSoccer -> sulfurSoccerTransitionTask != null;
                    default -> false;
                };
        return transitioning || plugin.getGameManager().hasActiveEventAreas(gameType);
    }

    /** Cancels finalist selection/countdown and force-ends a running finale instance. */
    public synchronized boolean stopFinale(@NotNull GameTypeEnum gameType) {
        if (!FinaleGameRegistry.isRegistered(gameType)) return false;
        boolean pending = pendingFinaleRequest == gameType;
        boolean running = isFinaleRunning(gameType);
        if (!pending && !running) return false;
        if (pending) {
            pendingFinaleRequest = null;
            finaleRequestGeneration++;
        }
        endGameSchedule(gameType);
        if (plugin.getGameManager().hasActiveEventAreas(gameType))
            plugin.getGameManager().forceEndEventAreas(gameType);
        return true;
    }

    private boolean isAnyFinaleRunning() {
        for (FinaleGameDefinition definition : FinaleGameRegistry.definitions()) {
            if (isFinaleRunning(definition.gameType())) return true;
        }
        return false;
    }

    /** Whether a completed instance belongs to a formal schedule that will assign another round. */
    public boolean hasNextRound(@NotNull GameTypeEnum gameTypeEnum) {
        FormalSchedule schedule = schedules.get(gameTypeEnum);
        return schedule != null && schedule.hasNextRound();
    }

    /**
     * Registers an EVENT instance before its synchronous end event reaches the schedule handler.
     */
    public synchronized void registerPendingEventInstance(@NotNull BaseGameInstance instance) {
        pendingEventInstances
                .computeIfAbsent(
                        instance.getGameTypeEnum(),
                        ignored -> Collections.newSetFromMap(new IdentityHashMap<>()))
                .add(instance);
    }

    /**
     * Removes an instance from the settlement queue when it is finalized directly by a force-stop.
     */
    public synchronized void unregisterPendingEventInstance(@NotNull BaseGameInstance instance) {
        Set<BaseGameInstance> pending = pendingEventInstances.get(instance.getGameTypeEnum());
        if (pending == null || !pending.remove(instance)) return;
        if (pending.isEmpty()) pendingEventInstances.remove(instance.getGameTypeEnum());
    }

    /** Special one-off events have no per-game schedule handler to close their settlement phase. */
    public void onEventInstanceReady(@NotNull BaseGameInstance instance) {
        GameTypeEnum gameType = instance.getGameTypeEnum();
        if (FinaleGameRegistry.isRegistered(gameType) || !isFormalEventRunning(gameType)) {
            settleEventRound(gameType, false, () -> {});
        }
    }

    /**
     * Keeps every completed instance in its arena until the local round result, and for a final
     * round the queued event leaderboard, has been displayed.
     */
    public void settleEventRound(
            @NotNull GameTypeEnum gameType,
            boolean hasNextRound,
            @NotNull Runnable afterSettlement) {
        settleEventRound(gameType, hasNextRound, afterSettlement, RESULT_DISPLAY_TICKS);
    }

    public void settleEventRound(
            @NotNull GameTypeEnum gameType,
            boolean hasNextRound,
            @NotNull Runnable afterSettlement,
            long resultDisplayTicks) {
        List<BaseGameInstance> instances;
        synchronized (this) {
            Set<BaseGameInstance> pending = pendingEventInstances.remove(gameType);
            instances = pending == null ? List.of() : List.copyOf(pending);
        }
        if (instances.isEmpty()) {
            plugin.getLogger()
                    .warning(
                            LogText.formatGameLog(
                                    gameType,
                                    "-",
                                    MessageConfig.codeText("schedule-manager.abort-formal-event-1"),
                                    MessageConfig.codeText("schedule-manager.settle-event-round-1"),
                                    MessageConfig.codeText(
                                            "schedule-manager.settle-event-round-2")));
            afterSettlement.run();
            return;
        }

        // The arena lifecycle must not depend on the rank database. Score writes and the final
        // leaderboard may be slow, but leaving participantStatus owned by an END instance would
        // reject every subsequent game start. Reserve the result-display window immediately;
        // ranking output is scheduled independently below.
        cancelRoundSettlement(gameType);
        EventRoundSettlement settlement = new EventRoundSettlement(instances);
        roundSettlements.put(gameType, settlement);
        settlement.attach(
                scheduler.runTaskLater(
                        plugin,
                        () -> {
                            if (!roundSettlements.remove(gameType, settlement)) return;
                            settlement.complete(
                                    hasNextRound && isFormalEventRunning(gameType),
                                    afterSettlement);
                        },
                        Math.max(0L, resultDisplayTicks)));

        if (hasNextRound) return;

        CoreMessages.playSoundToAllPlayers(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 1F);
        CoreMessages.sendTitleToAllPlayers(
                GameMessages.GAME_ROUND_END_TITLE.replace("%game%", gameType.toString()),
                GameMessages.GAME_ROUND_END_SUBTITLE,
                60);
        plugin.getRankManager().broadcastFinalRankings(gameType, () -> {});
    }

    /** Shows a global round-transition countdown in one persistent boss bar. */
    public void showRoundPreparationCountdown(GameTypeEnum gameType, int round, int seconds) {
        if (seconds <= 0) {
            clearRoundPreparationCountdown();
            return;
        }
        String roundValue = String.valueOf(Math.max(1, round));
        String secondsValue = CoreMessages.bossBarTime(seconds);
        String title =
                LegacyText.translateColorCodes(
                        MessageTemplate.render(
                                GameMessages.GAME_ROUND_PREPARATION_ACTION_BAR,
                                Map.of(
                                        "%game%",
                                        gameType.toString(),
                                        "%round%",
                                        roundValue,
                                        "%time%",
                                        secondsValue)));
        if (roundPreparationBar == null)
            roundPreparationBar = Bukkit.createBossBar(title, BarColor.YELLOW, BarStyle.SOLID);
        roundPreparationBar.setTitle(title);
        int duration = round <= 1 ? FIRST_ROUND_PREPARATION_SECONDS : ROUND_TRANSITION_SECONDS;
        roundPreparationBar.setProgress(Math.max(0D, Math.min(1D, seconds / (double) duration)));
        Set<Player> online = new HashSet<>(Bukkit.getOnlinePlayers());
        for (Player current : new ArrayList<>(roundPreparationBar.getPlayers())) {
            if (!online.contains(current)) roundPreparationBar.removePlayer(current);
        }
        for (Player player : online) roundPreparationBar.addPlayer(player);
    }

    public void clearRoundPreparationCountdown() {
        if (roundPreparationBar == null) return;
        roundPreparationBar.removeAll();
        roundPreparationBar = null;
    }

    /**
     * Ends the schedule manager of the given game (cancels its countdown/round tasks and
     * unregisters its handler). Called before force-ending the area so the SingleGameEndEvent fired
     * by endGame doesn't advance the schedule via nextRound().
     */
    public void endGameSchedule(GameTypeEnum gameTypeEnum) {
        cancelRoundSettlement(gameTypeEnum);
        FormalSchedule schedule = schedules.get(gameTypeEnum);
        if (schedule != null && schedule.isEnabled()) schedule.endSchedule();
        switch (gameTypeEnum) {
            case Dodgebolt -> {
                if (dodgeboltTransitionTask != null) dodgeboltTransitionTask.cancel();
                dodgeboltTransitionTask = null;
            }
            case SulfurSoccer -> {
                if (sulfurSoccerTransitionTask != null) sulfurSoccerTransitionTask.cancel();
                sulfurSoccerTransitionTask = null;
            }
            case DragonEggCarnival -> {
                if (dragonEggCarnivalTransitionTask != null)
                    dragonEggCarnivalTransitionTask.cancel();
                dragonEggCarnivalTransitionTask = null;
            }
            default -> {}
        }
        clearRoundPreparationCountdown();
    }

    /**
     * Undo the most recently started game: stop its schedule, force-end its running areas, then
     * (after a short delay so the area's async score recording lands first) clear its status entry
     * + point records.
     *
     * @return the game that was undone, or null if no round exists.
     */
    public GameTypeEnum deleteLatestGame() {
        GameTypeEnum latest = plugin.getRankManager().getLatestGame();
        if (latest == null) return null;
        endGameSchedule(latest);
        plugin.getGameManager().forceEndAreas(latest);
        // force-end -> endGame -> addPlayerPoints (async). Delay the soft-delete so those INSERTs
        // land
        // before UPDATE ... SET valid=0, otherwise late inserts survive with valid=1.
        scheduler.runTaskLaterAsynchronously(
                plugin, () -> plugin.getRankManager().deleteGameRecords(latest), 60L);
        return latest;
    }

    /**
     * Selects finalists from a fresh leaderboard and dispatches to the registered finale
     * implementation.
     */
    public synchronized void requestFinale(
            @NotNull GameTypeEnum gameType,
            String requestedArea,
            ChampionshipTeam requestedRight,
            ChampionshipTeam requestedLeft,
            @NotNull CommandSender requester,
            boolean forcePartialRoster) {
        FinaleGameDefinition definition = FinaleGameRegistry.definition(gameType);
        if (definition == null) {
            CoreMessages.sendAdminError(
                    requester, EventMessages.SCHEDULE_FINALE_GAME_NOT_REGISTERED);
            return;
        }
        if (!plugin.getGameManager().isGameEnabled(gameType)) {
            CoreMessages.sendAdminError(
                    requester,
                    EventMessages.SCHEDULE_FINALE_GAME_DISABLED.replace("%game%", gameType.name()));
            return;
        }
        if (forcePartialRoster && !definition.supportsPartialRoster()) {
            CoreMessages.sendAdminError(
                    requester, EventMessages.SCHEDULE_FINALE_PARTIAL_ROSTER_UNSUPPORTED);
            return;
        }
        if (isFinaleRunning(gameType)) {
            stopFinale(gameType);
            CoreMessages.sendAdminInfo(
                    requester,
                    EventMessages.SCHEDULE_FINALE_EMERGENCY_STOPPED.replace(
                            "%game%", gameType.name()));
            return;
        }
        if (isAnyFinaleRunning()) {
            CoreMessages.sendAdminError(requester, EventMessages.SCHEDULE_FINALE_OTHER_RUNNING);
            return;
        }

        long requestId = ++finaleRequestGeneration;
        pendingFinaleRequest = gameType;
        plugin.getRankManager()
                .withFreshTeamLeaderboard(
                        leaderboard -> {
                            synchronized (ScheduleManager.this) {
                                if (requestId != finaleRequestGeneration
                                        || pendingFinaleRequest != gameType) return;
                                pendingFinaleRequest = null;
                            }

                            String configuredArea = FormalEventMapResolver.map(plugin, gameType, 1);
                            String area =
                                    requestedArea == null || requestedArea.isBlank()
                                            ? (configuredArea == null
                                                    ? definition.defaultArea()
                                                    : configuredArea)
                                            : requestedArea;
                            BaseGameInstance instance = finaleArea(gameType, area);
                            if (instance == null
                                    || !plugin.getPrepareSessionManager()
                                            .canStart(gameType, area)) {
                                CoreMessages.sendAdminError(
                                        requester,
                                        MessageTemplate.render(
                                                EventMessages.SCHEDULE_FINALE_MAP_UNAVAILABLE,
                                                Map.of("%game%", gameType.name(), "%map%", area)));
                                return;
                            }

                            Finalists finalists =
                                    resolveFinalists(
                                            leaderboard, requestedRight, requestedLeft, requester);
                            if (finalists == null) return;

                            switch (gameType) {
                                case Dodgebolt -> {
                                    double rightPoints = pointsOf(leaderboard, finalists.right());
                                    double leftPoints = pointsOf(leaderboard, finalists.left());
                                    ChampionshipTeam higherSeed =
                                            rightPoints >= leftPoints
                                                    ? finalists.right()
                                                    : finalists.left();
                                    if (Double.compare(rightPoints, leftPoints) == 0) {
                                        CoreMessages.sendAdminInfo(
                                                requester,
                                                EventMessages.SCHEDULE_FINALE_DODGEBOLT_TIE_SEED);
                                    }
                                    startDodgeboltTransition(
                                            area,
                                            (DodgeboltArea) instance,
                                            finalists.right(),
                                            finalists.left(),
                                            higherSeed,
                                            requester,
                                            forcePartialRoster);
                                }
                                case SulfurSoccer ->
                                        startSulfurSoccerTransition(
                                                area,
                                                instance,
                                                finalists.right(),
                                                finalists.left(),
                                                requester);
                                case DragonEggCarnival ->
                                        startDragonEggCarnivalTransition(
                                                area,
                                                (DragonEggCarnivalArea) instance,
                                                finalists.right(),
                                                finalists.left(),
                                                requester);
                                default ->
                                        CoreMessages.sendAdminError(
                                                requester,
                                                EventMessages
                                                        .SCHEDULE_FINALE_START_NOT_IMPLEMENTED);
                            }
                        });
    }

    /**
     * Compatibility API for integrations; the canonical entry point is the /cc finale dodgebolt
     * start command.
     */
    public void requestDodgeboltFinal(
            String requestedArea,
            ChampionshipTeam requestedRight,
            ChampionshipTeam requestedLeft,
            CommandSender requester) {
        requestFinale(
                GameTypeEnum.Dodgebolt,
                requestedArea,
                requestedRight,
                requestedLeft,
                requester,
                false);
    }

    /** Compatibility API for integrations; supports Dodgebolt's optional online-subset start. */
    public void requestDodgeboltFinal(
            String requestedArea,
            ChampionshipTeam requestedRight,
            ChampionshipTeam requestedLeft,
            CommandSender requester,
            boolean forcePartialRoster) {
        requestFinale(
                GameTypeEnum.Dodgebolt,
                requestedArea,
                requestedRight,
                requestedLeft,
                requester,
                forcePartialRoster);
    }

    @Nullable
    private BaseGameInstance finaleArea(@NotNull GameTypeEnum gameType, @NotNull String area) {
        var manager = plugin.getGameManager().getAreaManager(gameType);
        return manager == null ? null : manager.getArea(area);
    }

    @Nullable
    private Finalists resolveFinalists(
            @NotNull List<Map.Entry<ChampionshipTeam, Double>> leaderboard,
            ChampionshipTeam requestedRight,
            ChampionshipTeam requestedLeft,
            @NotNull CommandSender requester) {
        ChampionshipTeam right = requestedRight;
        ChampionshipTeam left = requestedLeft;
        if (right == null || left == null) {
            if (leaderboard.size() < 2) {
                CoreMessages.sendAdminError(
                        requester, EventMessages.SCHEDULE_FINALE_AUTO_FINALISTS_UNAVAILABLE);
                return null;
            }
            if (leaderboard.size() > 2
                    && Double.compare(leaderboard.get(1).getValue(), leaderboard.get(2).getValue())
                            == 0) {
                CoreMessages.sendAdminError(
                        requester, EventMessages.SCHEDULE_FINALE_TIE_BREAK_REQUIRED);
                return null;
            }
            right = leaderboard.get(0).getKey();
            left = leaderboard.get(1).getKey();
        }
        if (right.equals(left)) {
            CoreMessages.sendAdminError(requester, EventMessages.SCHEDULE_FINALE_TEAMS_MUST_DIFFER);
            return null;
        }
        return new Finalists(right, left);
    }

    private void startDragonEggCarnivalTransition(
            @NotNull String area,
            @NotNull DragonEggCarnivalArea instance,
            @NotNull ChampionshipTeam right,
            @NotNull ChampionshipTeam left,
            @NotNull CommandSender requester) {
        final int[] remaining = {10};
        CoreMessages.sendAdminSuccess(
                requester,
                MessageTemplate.render(
                        EventMessages.SCHEDULE_FINALE_DRAGON_EGG_SCHEDULED,
                        Map.of(
                                "%right%",
                                right.getColoredName(),
                                "%left%",
                                left.getColoredName(),
                                "%map%",
                                area)));
        dragonEggCarnivalTransitionTask =
                scheduler.runTaskTimer(
                        plugin,
                        () -> {
                            showRoundPreparationCountdown(
                                    GameTypeEnum.DragonEggCarnival, 1, remaining[0]);
                            if (remaining[0] == 10) {
                                CoreMessages.sendMessageToAllPlayers(
                                        CoreMessages.getMessage(
                                                        ScheduleMessageConfig.DRAGON_EGG_CARNIVAL)
                                                .replace("%team%", right.getColoredName())
                                                .replace("%rival%", left.getColoredName()));
                            }
                            if (remaining[0] == 5)
                                CoreMessages.sendMessageToAllPlayers(
                                        CoreMessages.getMessage(
                                                ScheduleMessageConfig.DRAGON_EGG_CARNIVAL_POINTS));
                            if (remaining[0] == 0) {
                                dragonEggCarnivalTransitionTask.cancel();
                                dragonEggCarnivalTransitionTask = null;
                                if (plugin.getGameManager()
                                        .joinTeamArea(
                                                GameTypeEnum.DragonEggCarnival,
                                                area,
                                                right,
                                                left,
                                                true,
                                                GameRunMode.EVENT)) {
                                    plugin.getGameManager().spectateFinale(instance, right, left);
                                } else {
                                    CoreMessages.sendAdminError(
                                            requester,
                                            EventMessages.SCHEDULE_FINALE_DRAGON_EGG_START_FAILED);
                                }
                                return;
                            }
                            remaining[0]--;
                        },
                        0L,
                        20L);
    }

    private void startDodgeboltTransition(
            String area,
            DodgeboltArea instance,
            ChampionshipTeam right,
            ChampionshipTeam left,
            ChampionshipTeam higherSeed,
            CommandSender requester,
            boolean forcePartialRoster) {
        final int[] remaining = {10};
        CoreMessages.sendAdminSuccess(
                requester,
                (forcePartialRoster
                                ? EventMessages.SCHEDULE_FINALE_DODGEBOLT_SCHEDULED_FORCED
                                : EventMessages.SCHEDULE_FINALE_DODGEBOLT_SCHEDULED)
                        .replace("%right%", right.getColoredName())
                        .replace("%left%", left.getColoredName())
                        .replace("%map%", area));
        CoreMessages.sendMessageToAllPlayers(
                MessageTemplate.render(
                        EventMessages.SCHEDULE_FINALE_DODGEBOLT_ANNOUNCEMENT,
                        Map.of(
                                "%right%",
                                right.getColoredName(),
                                "%left%",
                                left.getColoredName(),
                                "%first%",
                                higherSeed.getColoredName())));
        dodgeboltTransitionTask =
                scheduler.runTaskTimer(
                        plugin,
                        () -> {
                            showRoundPreparationCountdown(GameTypeEnum.Dodgebolt, 1, remaining[0]);
                            if (remaining[0] == 0) {
                                dodgeboltTransitionTask.cancel();
                                dodgeboltTransitionTask = null;
                                if (plugin.getGameManager()
                                        .joinDodgeboltArea(
                                                area,
                                                right,
                                                left,
                                                higherSeed,
                                                true,
                                                forcePartialRoster,
                                                GameRunMode.EVENT)) {
                                    plugin.getGameManager().spectateFinale(instance, right, left);
                                } else {
                                    CoreMessages.sendAdminError(
                                            requester,
                                            forcePartialRoster
                                                    ? EventMessages
                                                            .SCHEDULE_FINALE_DODGEBOLT_FORCED_START_FAILED
                                                    : EventMessages
                                                            .SCHEDULE_FINALE_DODGEBOLT_START_FAILED);
                                }
                                return;
                            }
                            remaining[0]--;
                        },
                        0L,
                        20L);
    }

    private void startSulfurSoccerTransition(
            String area,
            BaseGameInstance instance,
            ChampionshipTeam right,
            ChampionshipTeam left,
            CommandSender requester) {
        final int[] remaining = {10};
        CoreMessages.sendAdminSuccess(
                requester,
                MessageTemplate.render(
                        SulfurSoccerMessages.SULFUR_SOCCER_SCHEDULED,
                        Map.of(
                                "%right%",
                                right.getColoredName(),
                                "%left%",
                                left.getColoredName(),
                                "%map%",
                                area)));
        CoreMessages.sendMessageToAllPlayers(
                MessageTemplate.render(
                        SulfurSoccerMessages.SULFUR_SOCCER_FINALISTS,
                        Map.of(
                                "%right%",
                                right.getColoredName(), "%left%", left.getColoredName())));
        sulfurSoccerTransitionTask =
                scheduler.runTaskTimer(
                        plugin,
                        () -> {
                            showRoundPreparationCountdown(
                                    GameTypeEnum.SulfurSoccer, 1, remaining[0]);
                            if (remaining[0] == 0) {
                                sulfurSoccerTransitionTask.cancel();
                                sulfurSoccerTransitionTask = null;
                                if (plugin.getGameManager()
                                        .joinTeamArea(
                                                GameTypeEnum.SulfurSoccer,
                                                area,
                                                right,
                                                left,
                                                true,
                                                GameRunMode.EVENT)) {
                                    plugin.getGameManager().spectateFinale(instance, right, left);
                                } else {
                                    clearRoundPreparationCountdown();
                                    CoreMessages.sendAdminError(
                                            requester,
                                            SulfurSoccerMessages.SULFUR_SOCCER_START_FAILED);
                                }
                                return;
                            }
                            remaining[0]--;
                        },
                        0L,
                        20L);
    }

    private static double pointsOf(
            List<Map.Entry<ChampionshipTeam, Double>> leaderboard, ChampionshipTeam team) {
        for (Map.Entry<ChampionshipTeam, Double> entry : leaderboard) {
            if (entry.getKey().equals(team)) return entry.getValue();
        }
        return 0D;
    }

    private record Finalists(@NotNull ChampionshipTeam right, @NotNull ChampionshipTeam left) {}

    public String getScheduleStrings(GameTypeEnum gameTypeEnum) {
        if (gameTypeEnum == GameTypeEnum.TNTRun)
            return CoreMessages.getMessage(ScheduleMessageConfig.TNT_RUN);
        if (gameTypeEnum == GameTypeEnum.TGTTOS)
            return CoreMessages.getMessage(ScheduleMessageConfig.TGTTOS);
        if (gameTypeEnum == GameTypeEnum.LaserShowdown)
            return CoreMessages.getMessage(ScheduleMessageConfig.LASER_SHOWDOWN);
        if (gameTypeEnum == GameTypeEnum.SkyWars)
            return CoreMessages.getMessage(ScheduleMessageConfig.SKY_WARS);
        if (gameTypeEnum == GameTypeEnum.ParkourWarrior)
            return CoreMessages.getMessage(ScheduleMessageConfig.PARKOUR_WARRIOR);
        if (gameTypeEnum == GameTypeEnum.Bingo)
            return CoreMessages.getMessage(ScheduleMessageConfig.BINGO);
        if (gameTypeEnum == GameTypeEnum.AceRace)
            return CoreMessages.getMessage(ScheduleMessageConfig.ACE_RACE);
        if (gameTypeEnum == GameTypeEnum.BuildMart)
            return CoreMessages.getMessage(ScheduleMessageConfig.BUILD_MART);
        if (gameTypeEnum == GameTypeEnum.RiptideRush)
            return CoreMessages.getMessage(ScheduleMessageConfig.RIPTIDE_RUSH);

        if (gameTypeEnum == GameTypeEnum.FrostbiteFrenzy)
            return CoreMessages.getMessage(ScheduleMessageConfig.FROSTBITE);
        if (gameTypeEnum == GameTypeEnum.LaserBox)
            return CoreMessages.getMessage(ScheduleMessageConfig.LASER_BOX);
        return "";
    }

    public String getSchedulePointsStrings(GameTypeEnum gameTypeEnum) {
        if (gameTypeEnum == GameTypeEnum.TNTRun)
            return CoreMessages.getMessage(ScheduleMessageConfig.TNT_RUN_POINTS);
        if (gameTypeEnum == GameTypeEnum.TGTTOS)
            return CoreMessages.getMessage(ScheduleMessageConfig.TGTTOS_POINTS);
        if (gameTypeEnum == GameTypeEnum.LaserShowdown)
            return CoreMessages.getMessage(ScheduleMessageConfig.LASER_SHOWDOWN_POINTS);
        if (gameTypeEnum == GameTypeEnum.SkyWars)
            return CoreMessages.getMessage(ScheduleMessageConfig.SKY_WARS_POINTS);
        if (gameTypeEnum == GameTypeEnum.ParkourWarrior)
            return CoreMessages.getMessage(ScheduleMessageConfig.PARKOUR_WARRIOR_POINTS);
        if (gameTypeEnum == GameTypeEnum.Bingo)
            return CoreMessages.getMessage(ScheduleMessageConfig.BINGO_POINTS);
        if (gameTypeEnum == GameTypeEnum.AceRace)
            return CoreMessages.getMessage(ScheduleMessageConfig.ACE_RACE_POINTS);
        if (gameTypeEnum == GameTypeEnum.BuildMart)
            return CoreMessages.getMessage(ScheduleMessageConfig.BUILD_MART_POINTS);
        if (gameTypeEnum == GameTypeEnum.RiptideRush)
            return CoreMessages.getMessage(ScheduleMessageConfig.RIPTIDE_RUSH_POINTS);

        if (gameTypeEnum == GameTypeEnum.FrostbiteFrenzy)
            return CoreMessages.getMessage(ScheduleMessageConfig.FROSTBITE_POINTS);
        if (gameTypeEnum == GameTypeEnum.LaserBox)
            return CoreMessages.getMessage(ScheduleMessageConfig.LASER_BOX_POINTS);
        return "";
    }
}
