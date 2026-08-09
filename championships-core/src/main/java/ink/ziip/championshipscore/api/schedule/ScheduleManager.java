package ink.ziip.championshipscore.api.schedule;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseManager;
import ink.ziip.championshipscore.api.game.instance.BaseGameInstance;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.object.game.GameRunMode;
import ink.ziip.championshipscore.api.schedule.battlebox.BattleBoxScheduleManager;
import ink.ziip.championshipscore.api.schedule.bingo.BingoScheduleHandler;
import ink.ziip.championshipscore.api.schedule.bingo.BingoScheduleManager;
import ink.ziip.championshipscore.api.schedule.buildmart.BuildMartScheduleHandler;
import ink.ziip.championshipscore.api.schedule.buildmart.BuildMartScheduleManager;
import ink.ziip.championshipscore.api.schedule.hotycodydusky.HotyCodyDuskyScheduleManager;
import ink.ziip.championshipscore.api.schedule.parkourtag.ParkourTagScheduleManager;
import ink.ziip.championshipscore.api.schedule.parkourwarrior.ParkourWarriorScheduleHandler;
import ink.ziip.championshipscore.api.schedule.parkourwarrior.ParkourWarriorScheduleManager;
import ink.ziip.championshipscore.api.schedule.skywars.SkyWarsScheduleHandler;
import ink.ziip.championshipscore.api.schedule.skywars.SkyWarsScheduleManager;
import ink.ziip.championshipscore.api.schedule.snowball.SnowballScheduleHandler;
import ink.ziip.championshipscore.api.schedule.snowball.SnowballScheduleManager;
import ink.ziip.championshipscore.api.schedule.tgttos.TGTTOSScheduleHandler;
import ink.ziip.championshipscore.api.schedule.tgttos.TGTTOSScheduleManager;
import ink.ziip.championshipscore.api.schedule.tntrun.TNTRunScheduleHandler;
import ink.ziip.championshipscore.api.schedule.tntrun.TNTRunScheduleManager;
import ink.ziip.championshipscore.api.schedule.acerace.AceRaceScheduleHandler;
import ink.ziip.championshipscore.api.schedule.acerace.AceRaceScheduleManager;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.message.ScheduleMessageConfig;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.util.Utils;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.command.CommandSender;
import ink.ziip.championshipscore.api.game.dodgebolt.DodgeboltArea;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class ScheduleManager extends BaseManager {
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
    @Getter
    private SnowballScheduleManager snowballScheduleManager;
    @Getter
    private SkyWarsScheduleManager skyWarsScheduleManager;
    @Getter
    private TNTRunScheduleManager tntRunScheduleManager;
    @Getter
    private TGTTOSScheduleManager tgttosScheduleManager;
    @Getter
    private BattleBoxScheduleManager battleBoxScheduleManager;
    @Getter
    private ParkourTagScheduleManager parkourTagScheduleManager;
    @Getter
    private ParkourWarriorScheduleManager parkourWarriorScheduleManager;
    @Getter
    private HotyCodyDuskyScheduleManager hotyCodyDuskyScheduleManager;
    @Getter
    private BingoScheduleManager bingoScheduleManager;
    @Getter
    private AceRaceScheduleManager aceRaceScheduleManager;
    @Getter
    private BuildMartScheduleManager buildMartScheduleManager;
    private int timer;
    private BukkitTask dodgeboltTransitionTask;
    private BukkitTask dragonEggCarnivalTransitionTask;
    private BossBar roundPreparationBar;
    private final Map<GameTypeEnum, Set<BaseGameInstance>> pendingEventInstances =
            new EnumMap<>(GameTypeEnum.class);

    public ScheduleManager(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
        scheduler = championshipsCore.getServer().getScheduler();
    }

    @Override
    public void load() {
        snowballScheduleManager = new SnowballScheduleManager(plugin, new SnowballScheduleHandler(plugin));
        skyWarsScheduleManager = new SkyWarsScheduleManager(plugin, new SkyWarsScheduleHandler(plugin));
        tntRunScheduleManager = new TNTRunScheduleManager(plugin, new TNTRunScheduleHandler(plugin));
        tgttosScheduleManager = new TGTTOSScheduleManager(plugin, new TGTTOSScheduleHandler(plugin));
        battleBoxScheduleManager = new BattleBoxScheduleManager(plugin);
        parkourTagScheduleManager = new ParkourTagScheduleManager(plugin);
        parkourWarriorScheduleManager = new ParkourWarriorScheduleManager(plugin, new ParkourWarriorScheduleHandler(plugin));
        hotyCodyDuskyScheduleManager = new HotyCodyDuskyScheduleManager(plugin);
        bingoScheduleManager = new BingoScheduleManager(plugin, new BingoScheduleHandler(plugin));
        aceRaceScheduleManager = new AceRaceScheduleManager(plugin, new AceRaceScheduleHandler(plugin));
        buildMartScheduleManager = new BuildMartScheduleManager(plugin, new BuildMartScheduleHandler(plugin));

        snowballScheduleManager.load();
        skyWarsScheduleManager.load();
        tntRunScheduleManager.load();
        tgttosScheduleManager.load();
        battleBoxScheduleManager.load();
        parkourTagScheduleManager.load();
        parkourWarriorScheduleManager.load();
        hotyCodyDuskyScheduleManager.load();
        bingoScheduleManager.load();
        aceRaceScheduleManager.load();
        buildMartScheduleManager.load();
    }

    @Override
    public void unload() {
        if (dodgeboltTransitionTask != null) dodgeboltTransitionTask.cancel();
        if (dragonEggCarnivalTransitionTask != null) dragonEggCarnivalTransitionTask.cancel();
        dodgeboltTransitionTask = null;
        dragonEggCarnivalTransitionTask = null;
        clearRoundPreparationCountdown();
        snowballScheduleManager.unload();
        skyWarsScheduleManager.unload();
        tntRunScheduleManager.unload();
        tgttosScheduleManager.unload();
        battleBoxScheduleManager.unload();
        parkourTagScheduleManager.unload();
        parkourWarriorScheduleManager.unload();
        hotyCodyDuskyScheduleManager.unload();
        bingoScheduleManager.unload();
        aceRaceScheduleManager.unload();
        buildMartScheduleManager.unload();
    }

    public void addRound(GameTypeEnum gameTypeEnum) {
        plugin.getRankManager().addGameOrder(gameTypeEnum, plugin.getRankManager().getRound() + 1);
    }

    public void resetRound() {
        plugin.getRankManager().resetGameOrder();
    }

    /** @return whether this game has an implementation for the formal-event command surface. */
    public boolean supportsFormalEvent(@NotNull GameTypeEnum gameTypeEnum) {
        return switch (gameTypeEnum) {
            case SnowballShowdown, SkyWars, TNTRun, TGTTOS, ParkourWarrior, BattleBox,
                    ParkourTag, HotyCodyDusky, Bingo, DragonEggCarnival, Dodgebolt, AceRace, BuildMart -> true;
            default -> false;
        };
    }

    /** Starts an ordinary formal event or stops it when it is already running, for emergency operation. */
    public EventAction startOrStopFormalEvent(@NotNull GameTypeEnum gameTypeEnum) {
        if (!supportsFormalEvent(gameTypeEnum)
                || gameTypeEnum == GameTypeEnum.DragonEggCarnival
                || gameTypeEnum == GameTypeEnum.Dodgebolt)
            return EventAction.UNSUPPORTED;
        if (isFormalEventRunning(gameTypeEnum)) {
            // A repeated start is the emergency-stop form of this command.  Stop the
            // scheduler and release every instance, just like /cc event stop; leaving
            // instances in PREPARATION/PROGRESS keeps their participants unavailable.
            stopFormalEvent(gameTypeEnum);
            return EventAction.STOPPED;
        }
        if (gameTypeEnum == GameTypeEnum.Bingo
                && !plugin.getGameManager().canStartBingo("bingo", true, GameRunMode.EVENT)) {
            return EventAction.UNAVAILABLE;
        }
        switch (gameTypeEnum) {
            case SnowballShowdown -> snowballScheduleManager.startGame();
            case SkyWars -> skyWarsScheduleManager.startGame();
            case TNTRun -> tntRunScheduleManager.startGame();
            case TGTTOS -> tgttosScheduleManager.startGame();
            case ParkourWarrior -> parkourWarriorScheduleManager.startGame();
            case BattleBox -> battleBoxScheduleManager.startBattleBox();
            case ParkourTag -> parkourTagScheduleManager.startParkourTag();
            case HotyCodyDusky -> hotyCodyDuskyScheduleManager.startHotyCodyDusky();
            case Bingo -> bingoScheduleManager.startGame();
            case AceRace -> aceRaceScheduleManager.startGame();
            case BuildMart -> buildMartScheduleManager.startGame();
            default -> {
                return EventAction.UNSUPPORTED;
            }
        }
        return EventAction.STARTED;
    }

    public EventAction startOrStopDragonEggCarnival(@NotNull ChampionshipTeam team,
                                                      @NotNull ChampionshipTeam rival) {
        if (isFormalEventRunning(GameTypeEnum.DragonEggCarnival)) {
            stopFormalEvent(GameTypeEnum.DragonEggCarnival);
            return EventAction.STOPPED;
        }
        startDragonEggCarnival(team, rival);
        return EventAction.STARTED;
    }

    /** Stops the formal schedule and force-ends any actively running game instance. */
    public boolean stopFormalEvent(@NotNull GameTypeEnum gameTypeEnum) {
        if (!supportsFormalEvent(gameTypeEnum) || !isFormalEventRunning(gameTypeEnum)) return false;
        endGameSchedule(gameTypeEnum);
        plugin.getGameManager().forceEndAreas(gameTypeEnum);
        return true;
    }

    /** Closes a formal schedule when its external execution plane aborts before producing a result. */
    public boolean abortFormalEvent(@NotNull GameTypeEnum gameTypeEnum, @NotNull String reason) {
        if (!supportsFormalEvent(gameTypeEnum) || !isFormalEventRunning(gameTypeEnum)) return false;
        endGameSchedule(gameTypeEnum);
        plugin.getLogger().warning(Utils.formatGameLog(gameTypeEnum, "-", "调度", "中止", reason));
        return true;
    }

    public boolean isFormalEventRunning(@NotNull GameTypeEnum gameTypeEnum) {
        return switch (gameTypeEnum) {
            case SnowballShowdown -> snowballScheduleManager.isEnabled();
            case SkyWars -> skyWarsScheduleManager.isEnabled();
            case TNTRun -> tntRunScheduleManager.isEnabled();
            case TGTTOS -> tgttosScheduleManager.isEnabled();
            case ParkourWarrior -> parkourWarriorScheduleManager.isEnabled();
            case BattleBox -> battleBoxScheduleManager.isEnabled();
            case ParkourTag -> parkourTagScheduleManager.isEnabled();
            case HotyCodyDusky -> hotyCodyDuskyScheduleManager.isEnabled();
            case Bingo -> bingoScheduleManager.isEnabled();
            case AceRace -> aceRaceScheduleManager.isEnabled();
            case BuildMart -> buildMartScheduleManager.isEnabled();
            case DragonEggCarnival -> dragonEggCarnivalTransitionTask != null;
            case Dodgebolt -> dodgeboltTransitionTask != null;
            default -> false;
        };
    }

    /** Whether a completed instance belongs to a formal schedule that will assign another round. */
    public boolean hasNextRound(@NotNull GameTypeEnum gameTypeEnum) {
        return switch (gameTypeEnum) {
            case SnowballShowdown -> snowballScheduleManager.hasNextRound();
            case TNTRun -> tntRunScheduleManager.hasNextRound();
            case TGTTOS -> tgttosScheduleManager.hasNextRound();
            case BattleBox -> battleBoxScheduleManager.hasNextRound();
            case ParkourTag -> parkourTagScheduleManager.hasNextRound();
            case HotyCodyDusky -> hotyCodyDuskyScheduleManager.hasNextRound();
            case BuildMart -> buildMartScheduleManager.hasNextRound();
            default -> false;
        };
    }

    /** Registers an EVENT instance before its synchronous end event reaches the schedule handler. */
    public synchronized void registerPendingEventInstance(@NotNull BaseGameInstance instance) {
        pendingEventInstances.computeIfAbsent(instance.getGameTypeEnum(), ignored ->
                Collections.newSetFromMap(new IdentityHashMap<>())).add(instance);
    }

    /** Removes an instance from the settlement queue when it is finalized directly by a force-stop. */
    public synchronized void unregisterPendingEventInstance(@NotNull BaseGameInstance instance) {
        Set<BaseGameInstance> pending = pendingEventInstances.get(instance.getGameTypeEnum());
        if (pending == null || !pending.remove(instance)) return;
        if (pending.isEmpty()) pendingEventInstances.remove(instance.getGameTypeEnum());
    }

    /** Special one-off events have no per-game schedule handler to close their settlement phase. */
    public void onEventInstanceReady(@NotNull BaseGameInstance instance) {
        GameTypeEnum gameType = instance.getGameTypeEnum();
        if (gameType == GameTypeEnum.DragonEggCarnival || gameType == GameTypeEnum.Dodgebolt
                || !isFormalEventRunning(gameType)) {
            settleEventRound(gameType, false, () -> { });
        }
    }

    /**
     * Keeps every completed instance in its arena until the local round result, and for a final round
     * the queued event leaderboard, has been displayed.
     */
    public void settleEventRound(@NotNull GameTypeEnum gameType, boolean hasNextRound,
                                 @NotNull Runnable afterSettlement) {
        List<BaseGameInstance> instances;
        synchronized (this) {
            Set<BaseGameInstance> pending = pendingEventInstances.remove(gameType);
            instances = pending == null ? List.of() : List.copyOf(pending);
        }
        if (instances.isEmpty()) {
            plugin.getLogger().warning(Utils.formatGameLog(gameType, "-", "调度", "结算",
                    "未找到等待释放的 EVENT 实例"));
            afterSettlement.run();
            return;
        }

        // The arena lifecycle must not depend on the rank database. Score writes and the final
        // leaderboard may be slow, but leaving participantStatus owned by an END instance would
        // reject every subsequent game start. Reserve the result-display window immediately;
        // ranking output is scheduled independently below.
        Runnable release = () -> scheduler.runTaskLater(plugin, () -> {
            for (BaseGameInstance instance : instances)
                instance.completePostGame(hasNextRound);
            afterSettlement.run();
        }, RESULT_DISPLAY_TICKS);

        if (hasNextRound) {
            release.run();
            return;
        }

        Utils.playSoundToAllPlayers(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 1F);
        Utils.sendTitleToAllPlayers(MessageConfig.GAME_ROUND_END_TITLE.replace("%game%", gameType.toString()),
                MessageConfig.GAME_ROUND_END_SUBTITLE, 60);
        plugin.getRankManager().broadcastFinalRankings(gameType, () -> { });
        release.run();
    }

    /** Shows a global round-transition countdown in one persistent boss bar. */
    public void showRoundPreparationCountdown(GameTypeEnum gameType, int round, int seconds) {
        if (seconds <= 0) {
            clearRoundPreparationCountdown();
            return;
        }
        String roundValue = String.valueOf(Math.max(1, round));
        String secondsValue = String.valueOf(seconds);
        String title = Utils.translateColorCodes(MessageConfig.GAME_ROUND_PREPARATION_ACTION_BAR
                .replace("%game%", gameType.toString())
                .replace("%round%", roundValue)
                .replace("%time%", secondsValue));
        if (roundPreparationBar == null)
            roundPreparationBar = Bukkit.createBossBar(title, BarColor.YELLOW, BarStyle.SOLID);
        roundPreparationBar.setTitle(title);
        int duration = round <= 1 ? FIRST_ROUND_PREPARATION_SECONDS : ROUND_TRANSITION_SECONDS;
        roundPreparationBar.setProgress(Math.max(0D, Math.min(1D, seconds / (double) duration)));
        Set<Player> online = new HashSet<>(Bukkit.getOnlinePlayers());
        for (Player current : new ArrayList<>(roundPreparationBar.getPlayers())) {
            if (!online.contains(current))
                roundPreparationBar.removePlayer(current);
        }
        for (Player player : online)
            roundPreparationBar.addPlayer(player);
    }

    public void clearRoundPreparationCountdown() {
        if (roundPreparationBar == null)
            return;
        roundPreparationBar.removeAll();
        roundPreparationBar = null;
    }

    /**
     * Ends the schedule manager of the given game (cancels its countdown/round tasks and unregisters its
     * handler). Called before force-ending the area so the SingleGameEndEvent fired by endGame doesn't
     * advance the schedule via nextRound().
     */
    public void endGameSchedule(GameTypeEnum gameTypeEnum) {
        switch (gameTypeEnum) {
            case SnowballShowdown -> { if (snowballScheduleManager.isEnabled()) snowballScheduleManager.endSchedule(); }
            case SkyWars -> { if (skyWarsScheduleManager.isEnabled()) skyWarsScheduleManager.endSchedule(); }
            case TNTRun -> { if (tntRunScheduleManager.isEnabled()) tntRunScheduleManager.endSchedule(); }
            case TGTTOS -> { if (tgttosScheduleManager.isEnabled()) tgttosScheduleManager.endSchedule(); }
            case ParkourWarrior -> { if (parkourWarriorScheduleManager.isEnabled()) parkourWarriorScheduleManager.endSchedule(); }
            case BattleBox -> { if (battleBoxScheduleManager.isEnabled()) battleBoxScheduleManager.endSchedule(); }
            case ParkourTag -> { if (parkourTagScheduleManager.isEnabled()) parkourTagScheduleManager.endSchedule(); }
            case HotyCodyDusky -> { if (hotyCodyDuskyScheduleManager.isEnabled()) hotyCodyDuskyScheduleManager.endSchedule(); }
            case Bingo -> { if (bingoScheduleManager.isEnabled()) bingoScheduleManager.endSchedule(); }
            case AceRace -> { if (aceRaceScheduleManager.isEnabled()) aceRaceScheduleManager.endSchedule(); }
            case BuildMart -> { if (buildMartScheduleManager.isEnabled()) buildMartScheduleManager.endSchedule(); }
            case Dodgebolt -> {
                if (dodgeboltTransitionTask != null) dodgeboltTransitionTask.cancel();
                dodgeboltTransitionTask = null;
            }
            case DragonEggCarnival -> {
                if (dragonEggCarnivalTransitionTask != null) dragonEggCarnivalTransitionTask.cancel();
                dragonEggCarnivalTransitionTask = null;
            }
            default -> { }
        }
        clearRoundPreparationCountdown();
    }

    /**
     * Undo the most recently started game: stop its schedule, force-end its running areas, then (after a
     * short delay so the area's async score recording lands first) clear its status entry + point records.
     * @return the game that was undone, or null if no round exists.
     */
    public GameTypeEnum deleteLatestGame() {
        GameTypeEnum latest = plugin.getRankManager().getLatestGame();
        if (latest == null) return null;
        endGameSchedule(latest);
        plugin.getGameManager().forceEndAreas(latest);
        // force-end -> endGame -> addPlayerPoints (async). Delay the soft-delete so those INSERTs land
        // before UPDATE ... SET valid=0, otherwise late inserts survive with valid=1.
        scheduler.runTaskLaterAsynchronously(plugin, () -> plugin.getRankManager().deleteGameRecords(latest), 60L);
        return latest;
    }

    private void startDragonEggCarnival(ChampionshipTeam team, ChampionshipTeam rival) {
        plugin.getScheduleManager().addRound(GameTypeEnum.DragonEggCarnival);
        timer = 10;
        dragonEggCarnivalTransitionTask = scheduler.runTaskTimer(plugin, () -> {

            showRoundPreparationCountdown(GameTypeEnum.DragonEggCarnival, 1, timer);

            if (timer == 10) {
                Utils.sendMessageToAllPlayers(Utils.getMessage(ScheduleMessageConfig.DRAGON_EGG_CARNIVAL)
                        .replace("%team%", team.getColoredName())
                        .replace("%rival%", rival.getColoredName()));
            }

            if (timer == 5) {
                Utils.sendMessageToAllPlayers(Utils.getMessage(ScheduleMessageConfig.DRAGON_EGG_CARNIVAL_POINTS));
            }
            if (timer == 0) {
                plugin.getGameManager().joinTeamArea(GameTypeEnum.DragonEggCarnival, "area1", team, rival,
                        true, GameRunMode.EVENT);
                if (dragonEggCarnivalTransitionTask != null)
                    dragonEggCarnivalTransitionTask.cancel();
                dragonEggCarnivalTransitionTask = null;
            }
            timer--;
        }, 0, 20L);
    }

    /** Queues finalist selection after all pending score writes and starts a non-scoring final. */
    public void requestDodgeboltFinal(String requestedArea, ChampionshipTeam requestedRight,
                                      ChampionshipTeam requestedLeft, CommandSender requester) {
        requestDodgeboltFinal(requestedArea, requestedRight, requestedLeft, requester, false);
    }

    /** A forced final uses each finalist's online subset, but otherwise follows the formal final lifecycle. */
    public void requestDodgeboltFinal(String requestedArea, ChampionshipTeam requestedRight,
                                      ChampionshipTeam requestedLeft, CommandSender requester,
                                      boolean forcePartialRoster) {
        if (!plugin.getGameManager().isGameEnabled(GameTypeEnum.Dodgebolt)) {
            Utils.sendAdminError(requester, "躲避箭未在 enabled-games 中启用");
            return;
        }
        if (dodgeboltTransitionTask != null) {
            Utils.sendAdminError(requester, "已有躲避箭决赛正在进入场地");
            return;
        }
        plugin.getRankManager().withFreshTeamLeaderboard(leaderboard -> {
            String area = requestedArea == null || requestedArea.isBlank() ? "dodgebolt" : requestedArea;
            DodgeboltArea instance = plugin.getGameManager().getDodgeboltManager().getArea(area);
            if (instance == null || !plugin.getPrepareSessionManager().canStart(GameTypeEnum.Dodgebolt, area)) {
                Utils.sendAdminError(requester, "躲避箭地图不存在或尚未发布：&#fff566" + area);
                return;
            }

            ChampionshipTeam right = requestedRight;
            ChampionshipTeam left = requestedLeft;
            if (right == null || left == null) {
                if (leaderboard.size() < 2) {
                    Utils.sendAdminError(requester, "队伍总榜不足两支队伍，无法自动选出决赛队伍");
                    return;
                }
                if (leaderboard.size() > 2
                        && Double.compare(leaderboard.get(1).getValue(), leaderboard.get(2).getValue()) == 0) {
                    Utils.sendAdminError(requester, "第二名与第三名同分，请显式指定两支决赛队伍");
                    return;
                }
                right = leaderboard.get(0).getKey();
                left = leaderboard.get(1).getKey();
            }
            if (right.equals(left)) {
                Utils.sendAdminError(requester, "决赛必须指定两支不同队伍");
                return;
            }

            double rightPoints = pointsOf(leaderboard, right);
            double leftPoints = pointsOf(leaderboard, left);
            ChampionshipTeam higherSeed = rightPoints >= leftPoints ? right : left;
            if (Double.compare(rightPoints, leftPoints) == 0) {
                Utils.sendAdminInfo(requester, "两队积分相同，第一参数队伍将作为第一局两箭队伍");
            }
            startDodgeboltTransition(area, instance, right, left, higherSeed, requester, forcePartialRoster);
        });
    }

    private void startDodgeboltTransition(String area, DodgeboltArea instance,
                                          ChampionshipTeam right, ChampionshipTeam left,
                                          ChampionshipTeam higherSeed, CommandSender requester,
                                          boolean forcePartialRoster) {
        final int[] remaining = {10};
        Utils.sendAdminSuccess(requester, "躲避箭决赛已排定 &#bababa• " + right.getColoredName()
                + " &#edededvs " + left.getColoredName() + " &#bababa• &#ededed地图 " + area
                + (forcePartialRoster ? " &#bababa• &#ff6b26强制阵容" : ""));
        Utils.sendMessageToAllPlayers("&#bababa━━━━━━━━ &#fff566&l躲避箭决赛 &#bababa━━━━━━━━\n"
                + right.getColoredName() + " &#edededvs " + left.getColoredName()
                + "\n&#ededed第一局两箭：" + higherSeed.getColoredName());
        dodgeboltTransitionTask = scheduler.runTaskTimer(plugin, () -> {
            showRoundPreparationCountdown(GameTypeEnum.Dodgebolt, 1, remaining[0]);
            if (remaining[0] == 0) {
                dodgeboltTransitionTask.cancel();
                dodgeboltTransitionTask = null;
                if (plugin.getGameManager().joinDodgeboltArea(area, right, left, higherSeed,
                        true, forcePartialRoster, GameRunMode.EVENT)) {
                    plugin.getGameManager().spectateDodgeboltFinal(instance, right, left);
                } else {
                    Utils.sendAdminError(requester, forcePartialRoster
                            ? "躲避箭决赛强制启动失败，请确保每队至少有 1 名在线玩家并检查场地占用"
                            : "躲避箭决赛启动失败，请检查队员在线状态和场地占用");
                }
                return;
            }
            remaining[0]--;
        }, 0L, 20L);
    }

    private static double pointsOf(List<Map.Entry<ChampionshipTeam, Double>> leaderboard,
                                   ChampionshipTeam team) {
        for (Map.Entry<ChampionshipTeam, Double> entry : leaderboard) {
            if (entry.getKey().equals(team)) return entry.getValue();
        }
        return 0D;
    }

    public String getScheduleStrings(GameTypeEnum gameTypeEnum) {
        if (gameTypeEnum == GameTypeEnum.TNTRun)
            return Utils.getMessage(ScheduleMessageConfig.TNT_RUN);
        if (gameTypeEnum == GameTypeEnum.TGTTOS)
            return Utils.getMessage(ScheduleMessageConfig.TGTTOS);
        if (gameTypeEnum == GameTypeEnum.SnowballShowdown)
            return Utils.getMessage(ScheduleMessageConfig.SNOWBALL);
        if (gameTypeEnum == GameTypeEnum.SkyWars)
            return Utils.getMessage(ScheduleMessageConfig.SKY_WARS);
        if (gameTypeEnum == GameTypeEnum.ParkourWarrior)
            return Utils.getMessage(ScheduleMessageConfig.PARKOUR_WARRIOR);
        if (gameTypeEnum == GameTypeEnum.Bingo)
            return Utils.getMessage(ScheduleMessageConfig.BINGO);
        if (gameTypeEnum == GameTypeEnum.AceRace)
            return Utils.getMessage(ScheduleMessageConfig.ACE_RACE);
        if (gameTypeEnum == GameTypeEnum.BuildMart)
            return Utils.getMessage(ScheduleMessageConfig.BUILD_MART);

        return "";
    }

    public String getSchedulePointsStrings(GameTypeEnum gameTypeEnum) {
        if (gameTypeEnum == GameTypeEnum.TNTRun)
            return Utils.getMessage(ScheduleMessageConfig.TNT_RUN_POINTS);
        if (gameTypeEnum == GameTypeEnum.TGTTOS)
            return Utils.getMessage(ScheduleMessageConfig.TGTTOS_POINTS);
        if (gameTypeEnum == GameTypeEnum.SnowballShowdown)
            return Utils.getMessage(ScheduleMessageConfig.SNOWBALL_POINTS);
        if (gameTypeEnum == GameTypeEnum.SkyWars)
            return Utils.getMessage(ScheduleMessageConfig.SKY_WARS_POINTS);
        if (gameTypeEnum == GameTypeEnum.ParkourWarrior)
            return Utils.getMessage(ScheduleMessageConfig.PARKOUR_WARRIOR_POINTS);
        if (gameTypeEnum == GameTypeEnum.Bingo)
            return Utils.getMessage(ScheduleMessageConfig.BINGO_POINTS);
        if (gameTypeEnum == GameTypeEnum.AceRace)
            return Utils.getMessage(ScheduleMessageConfig.ACE_RACE_POINTS);
        if (gameTypeEnum == GameTypeEnum.BuildMart)
            return Utils.getMessage(ScheduleMessageConfig.BUILD_MART_POINTS);

        return "";
    }
}
