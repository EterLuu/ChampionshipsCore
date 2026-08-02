package ink.ziip.championshipscore.api.schedule;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseManager;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.schedule.battlebox.BattleBoxScheduleManager;
import ink.ziip.championshipscore.api.schedule.bingo.BingoScheduleHandler;
import ink.ziip.championshipscore.api.schedule.bingo.BingoScheduleManager;
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
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.message.ScheduleMessageConfig;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.util.Utils;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.command.CommandSender;
import ink.ziip.championshipscore.api.game.dodgebolt.DodgeboltArea;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

public class ScheduleManager extends BaseManager {
    public enum EventAction {
        STARTED,
        STOPPED,
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
    private int timer;
    private BukkitTask dodgeboltTransitionTask;
    private BukkitTask dragonEggCarnivalTransitionTask;

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

        snowballScheduleManager.load();
        skyWarsScheduleManager.load();
        tntRunScheduleManager.load();
        tgttosScheduleManager.load();
        battleBoxScheduleManager.load();
        parkourTagScheduleManager.load();
        parkourWarriorScheduleManager.load();
        hotyCodyDuskyScheduleManager.load();
        bingoScheduleManager.load();
    }

    @Override
    public void unload() {
        if (dodgeboltTransitionTask != null) dodgeboltTransitionTask.cancel();
        if (dragonEggCarnivalTransitionTask != null) dragonEggCarnivalTransitionTask.cancel();
        dodgeboltTransitionTask = null;
        dragonEggCarnivalTransitionTask = null;
        snowballScheduleManager.unload();
        skyWarsScheduleManager.unload();
        tntRunScheduleManager.unload();
        tgttosScheduleManager.unload();
        battleBoxScheduleManager.unload();
        parkourTagScheduleManager.unload();
        parkourWarriorScheduleManager.unload();
        hotyCodyDuskyScheduleManager.unload();
        bingoScheduleManager.unload();
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
                    ParkourTag, HotyCodyDusky, Bingo, DragonEggCarnival, Dodgebolt -> true;
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
            endGameSchedule(gameTypeEnum);
            return EventAction.STOPPED;
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
            default -> {
                return EventAction.UNSUPPORTED;
            }
        }
        return EventAction.STARTED;
    }

    public EventAction startOrStopDragonEggCarnival(@NotNull ChampionshipTeam team,
                                                      @NotNull ChampionshipTeam rival) {
        if (isFormalEventRunning(GameTypeEnum.DragonEggCarnival)) {
            endGameSchedule(GameTypeEnum.DragonEggCarnival);
            return EventAction.STOPPED;
        }
        startDragonEggCarnival(team, rival);
        return EventAction.STARTED;
    }

    /** Stops the formal schedule and leaves any actively running game instance under referee control. */
    public boolean stopFormalEvent(@NotNull GameTypeEnum gameTypeEnum) {
        if (!supportsFormalEvent(gameTypeEnum) || !isFormalEventRunning(gameTypeEnum)) return false;
        endGameSchedule(gameTypeEnum);
        return true;
    }

    private boolean isFormalEventRunning(@NotNull GameTypeEnum gameTypeEnum) {
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
            case DragonEggCarnival -> dragonEggCarnivalTransitionTask != null;
            case Dodgebolt -> dodgeboltTransitionTask != null;
            default -> false;
        };
    }

    /** Shows a lobby/round transition in the action bar only. */
    public void showRoundPreparationCountdown(GameTypeEnum gameType, int round, int seconds) {
        String roundValue = String.valueOf(Math.max(1, round));
        String secondsValue = String.valueOf(Math.max(0, seconds));
        Utils.sendActionBarToAllPlayers(MessageConfig.GAME_ROUND_PREPARATION_ACTION_BAR
                .replace("%game%", gameType.toString())
                .replace("%round%", roundValue)
                .replace("%time%", secondsValue));
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
            case Dodgebolt -> {
                if (dodgeboltTransitionTask != null) dodgeboltTransitionTask.cancel();
                dodgeboltTransitionTask = null;
            }
            case DragonEggCarnival -> {
                if (dragonEggCarnivalTransitionTask != null) dragonEggCarnivalTransitionTask.cancel();
                dragonEggCarnivalTransitionTask = null;
            }
            default -> { } // BuildMart has no formal schedule manager
        }
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
        addAllSpectatorsToArea();
        dragonEggCarnivalTransitionTask = scheduler.runTaskTimer(plugin, () -> {

            Utils.changeLevelForAllPlayers(timer);
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
                Utils.changeLevelForAllPlayers(0);
                plugin.getGameManager().joinTeamArea(GameTypeEnum.DragonEggCarnival, "area1", team, rival);
                if (dragonEggCarnivalTransitionTask != null)
                    dragonEggCarnivalTransitionTask.cancel();
                dragonEggCarnivalTransitionTask = null;
            }
            timer--;
        }, 0, 20L);
    }

    public void addAllSpectatorsToArea() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(player);
            if (championshipTeam == null) {
                player.performCommand("cc spectate leave");
                player.performCommand("cc spectate dragoneggcarnival area1");
            }
        }
    }

    /** Queues finalist selection after all pending score writes and starts a non-scoring final. */
    public void requestDodgeboltFinal(String requestedArea, ChampionshipTeam requestedRight,
                                      ChampionshipTeam requestedLeft, CommandSender requester) {
        if (!plugin.getGameManager().isGameEnabled(GameTypeEnum.Dodgebolt)) {
            Utils.sendAdminError(requester, "躲避箭未在 enabled-games 中启用");
            return;
        }
        if (dodgeboltTransitionTask != null) {
            Utils.sendAdminError(requester, "已有躲避箭决赛正在进入场地");
            return;
        }
        plugin.getRankManager().withFreshTeamLeaderboard(leaderboard -> {
            String area = requestedArea;
            if (area == null || area.isBlank()) {
                List<String> names = plugin.getGameManager().getDodgeboltManager().getAreaNameList();
                names.sort(String.CASE_INSENSITIVE_ORDER);
                if (names.isEmpty()) {
                    Utils.sendAdminError(requester, "没有已配置的躲避箭地图");
                    return;
                }
                area = names.getFirst();
            }
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
            startDodgeboltTransition(area, instance, right, left, higherSeed, requester);
        });
    }

    private void startDodgeboltTransition(String area, DodgeboltArea instance,
                                          ChampionshipTeam right, ChampionshipTeam left,
                                          ChampionshipTeam higherSeed, CommandSender requester) {
        final int[] remaining = {10};
        Utils.sendAdminSuccess(requester, "躲避箭决赛已排定 &#bababa• " + right.getColoredName()
                + " &#edededvs " + left.getColoredName() + " &#bababa• &#ededed地图 " + area);
        Utils.sendMessageToAllPlayers("&#bababa━━━━━━━━ &#fff566&l躲避箭决赛 &#bababa━━━━━━━━\n"
                + right.getColoredName() + " &#edededvs " + left.getColoredName()
                + "\n&#ededed第一局两箭：" + higherSeed.getColoredName());
        dodgeboltTransitionTask = scheduler.runTaskTimer(plugin, () -> {
            showRoundPreparationCountdown(GameTypeEnum.Dodgebolt, 1, remaining[0]);
            Utils.changeLevelForAllPlayers(remaining[0]);
            if (remaining[0] == 0) {
                dodgeboltTransitionTask.cancel();
                dodgeboltTransitionTask = null;
                Utils.changeLevelForAllPlayers(0);
                if (plugin.getGameManager().joinDodgeboltArea(area, right, left, higherSeed, true)) {
                    plugin.getGameManager().spectateDodgeboltFinal(instance, right, left);
                } else {
                    Utils.sendAdminError(requester, "躲避箭决赛启动失败，请检查队员在线状态和场地占用");
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

        return "";
    }
}
