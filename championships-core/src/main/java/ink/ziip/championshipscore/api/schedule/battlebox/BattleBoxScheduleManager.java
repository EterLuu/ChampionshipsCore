package ink.ziip.championshipscore.api.schedule.battlebox;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseManager;
import ink.ziip.championshipscore.api.game.battlebox.runtime.BattleBoxArea;
import ink.ziip.championshipscore.api.game.model.GameRunMode;
import ink.ziip.championshipscore.api.game.model.GameTypeEnum;
import ink.ziip.championshipscore.api.schedule.FormalEventMapResolver;
import ink.ziip.championshipscore.api.schedule.FormalPairingScheduler;
import ink.ziip.championshipscore.api.schedule.model.TwoVTwoVector;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.message.ScheduleMessageConfig;
import ink.ziip.championshipscore.logging.LogText;
import ink.ziip.championshipscore.presentation.text.CoreMessages;

import lombok.Getter;

import org.bukkit.Sound;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class BattleBoxScheduleManager extends BaseManager {
    private static final int ROUND_TRANSITION_SECONDS = 10;
    private final BukkitScheduler scheduler;
    private final BattleBoxScheduleHandler handler;
    private final List<List<TwoVTwoVector>> rounds = new ArrayList<>();
    private List<ChampionshipTeam> teams = List.of();
    private final Map<ChampionshipTeam, Double> standings = new HashMap<>();
    private final Set<String> previousOpponents = new HashSet<>();
    private int totalRounds;
    private int seededRounds;
    @Getter private int subRound;
    private int timer;
    @Getter private boolean enabled;
    private BukkitTask firstStartTask;
    private BukkitTask startTask;
    private String scheduledMapName;
    private final Set<BattleBoxArea> activeRoundInstances =
            Collections.newSetFromMap(new IdentityHashMap<>());

    public BattleBoxScheduleManager(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
        handler = new BattleBoxScheduleHandler(championshipsCore, this);
        scheduler = championshipsCore.getServer().getScheduler();
        subRound = 0;
    }

    private boolean cycleGeneratePairs() {
        this.rounds.clear();

        List<ChampionshipTeam> selectedTeams =
                new ArrayList<>(
                        plugin.getScheduleManager().participatingTeams(GameTypeEnum.BattleBox));

        if (selectedTeams.size() < 2 || selectedTeams.size() % 2 != 0) {
            plugin.getLogger()
                    .warning(
                            LogText.formatGameLog(
                                    GameTypeEnum.BattleBox,
                                    "-",
                                    "调度",
                                    "对阵",
                                    "队伍数=" + selectedTeams.size() + "，至少需要两支且必须为偶数"));
            return false;
        }

        Collections.shuffle(selectedTeams);
        teams = List.copyOf(selectedTeams);
        totalRounds = FormalPairingScheduler.totalRounds(teams.size());
        seededRounds = FormalPairingScheduler.seededRounds(teams.size());
        standings.clear();
        teams.forEach(team -> standings.put(team, 0D));
        previousOpponents.clear();
        rounds.addAll(FormalPairingScheduler.roundRobin(teams, seededRounds));
        rounds.forEach(round -> FormalPairingScheduler.rememberOpponents(round, previousOpponents));
        return !this.rounds.isEmpty();
    }

    @Override
    public void load() {}

    @Override
    public void unload() {
        if (enabled) {
            endSchedule();
        }
    }

    public void startBattleBox() {
        if (enabled) {
            endSchedule();
            return;
        }

        if (!cycleGeneratePairs()) return;
        scheduledMapName = FormalEventMapResolver.map(plugin, GameTypeEnum.BattleBox, 1);
        if (scheduledMapName == null) {
            plugin.getLogger()
                    .warning(
                            LogText.formatGameLog(
                                    GameTypeEnum.BattleBox, "-", "调度", "启动", "无法开始：未配置地图"));
            return;
        }
        int requiredInstances = rounds.getFirst().size();
        long availableInstances =
                plugin
                        .getGameManager()
                        .getBattleBoxManager()
                        .getMapInstances(scheduledMapName)
                        .stream()
                        .filter(
                                instance ->
                                        instance.getGameStageEnum()
                                                == ink.ziip.championshipscore.api.game.model
                                                        .GameStageEnum.WAITING)
                        .count();
        if (availableInstances < requiredInstances) {
            plugin.getLogger()
                    .warning(
                            LogText.formatGameLog(
                                    GameTypeEnum.BattleBox,
                                    scheduledMapName,
                                    "调度",
                                    "启动",
                                    "无法开始：需要实例="
                                            + requiredInstances
                                            + "，空闲实例="
                                            + availableInstances));
            scheduledMapName = null;
            return;
        }

        plugin.getScheduleManager().addRound(GameTypeEnum.BattleBox);
        enabled = true;
        timer = 10;
        subRound = 0;

        firstStartTask =
                scheduler.runTaskTimer(
                        plugin,
                        () -> {
                            plugin.getScheduleManager()
                                    .showRoundPreparationCountdown(
                                            GameTypeEnum.BattleBox, 1, timer);

                            if (timer == 10) {
                                CoreMessages.sendMessageToAllPlayers(
                                        CoreMessages.getMessage(ScheduleMessageConfig.BATTLE_BOX));
                            }

                            if (timer == 5) {
                                CoreMessages.sendMessageToAllPlayers(
                                        CoreMessages.getMessage(
                                                ScheduleMessageConfig.BATTLE_BOX_POINTS));
                            }

                            if (timer == 0) {
                                subRound = 0;
                                startBattleBoxRound();
                                if (firstStartTask != null) firstStartTask.cancel();
                            }
                            timer--;
                        },
                        0,
                        20L);
    }

    public void startBattleBoxRound() {
        if (!enabled) return;

        subRound++;
        if (subRound > totalRounds) {
            endSchedule();
            return;
        }

        handler.register();

        startRoundBattle();
    }

    private void startRoundBattle() {
        String areaName = scheduledMapName;
        if (areaName == null) {
            abortSchedule("第 " + subRound + " 轮无法开始：地图不存在");
            return;
        }

        List<TwoVTwoVector> pairs = rounds.get(subRound - 1);

        List<BattleBoxArea> started =
                plugin.getGameManager()
                        .joinBattleBoxInstances(areaName, pairs, subRound == 1, GameRunMode.EVENT);
        if (started != null) {
            activeRoundInstances.clear();
            activeRoundInstances.addAll(started);
            plugin.getLogger()
                    .info(
                            LogText.formatGameLog(
                                    GameTypeEnum.BattleBox,
                                    areaName,
                                    "调度",
                                    "轮次",
                                    "第 " + subRound + " 轮开始，对局数=" + pairs.size()));
        } else {
            abortSchedule("第 " + subRound + " 轮启动失败");
        }
    }

    private void abortSchedule(String reason) {
        plugin.getLogger()
                .warning(
                        LogText.formatGameLog(
                                GameTypeEnum.BattleBox,
                                scheduledMapName == null ? "-" : scheduledMapName,
                                "调度",
                                "中止",
                                reason));
        if (firstStartTask != null) firstStartTask.cancel();
        if (startTask != null) startTask.cancel();
        enabled = false;
        activeRoundInstances.clear();
        scheduledMapName = null;
        handler.unRegister();
        plugin.getGameManager().releaseEventSpectatorsForGame(GameTypeEnum.BattleBox);
        plugin.getScheduleManager().clearRoundPreparationCountdown();
    }

    public void endSchedule() {
        plugin.getScheduleManager().clearStartSelection(GameTypeEnum.BattleBox);
        if (firstStartTask != null) firstStartTask.cancel();
        if (startTask != null) startTask.cancel();

        enabled = false;
        activeRoundInstances.clear();
        scheduledMapName = null;

        handler.unRegister();
        plugin.getScheduleManager().clearRoundPreparationCountdown();
        plugin.getGameManager().releaseEventSpectatorsForGame(GameTypeEnum.BattleBox);
        rounds.clear();
        teams = List.of();
        standings.clear();
        previousOpponents.clear();
        totalRounds = 0;
        seededRounds = 0;
    }

    public void nextBattleBoxRound() {
        if (!enabled) return;

        subRound++;
        if (subRound > totalRounds) {
            endSchedule();
            return;
        }
        CoreMessages.playSoundToAllPlayers(Sound.ENTITY_PLAYER_LEVELUP, 1, 1F);

        timer = ROUND_TRANSITION_SECONDS;
        startTask =
                scheduler.runTaskTimer(
                        plugin,
                        () -> {
                            plugin.getScheduleManager()
                                    .showRoundPreparationCountdown(
                                            GameTypeEnum.BattleBox, subRound, timer);

                            if (timer == ROUND_TRANSITION_SECONDS) {
                                CoreMessages.sendMessageToAllPlayers(
                                        CoreMessages.getMessage(
                                                ScheduleMessageConfig.NEXT_ROUND_SOON));
                            }

                            if (timer == 0) {
                                startRoundBattle();
                                if (startTask != null) startTask.cancel();
                            }
                            timer--;
                        },
                        0,
                        20L);
    }

    /** Advances only after every independently running instance in this round has ended. */
    public synchronized void onInstanceComplete(BattleBoxArea instance) {
        onInstanceComplete(instance, instance.getMatchWinner());
    }

    public synchronized void onInstanceComplete(BattleBoxArea instance, ChampionshipTeam winner) {
        if (!activeRoundInstances.remove(instance)) return;
        ChampionshipTeam right = instance.getRightChampionshipTeam();
        ChampionshipTeam left = instance.getLeftChampionshipTeam();
        if (right != null && left != null) {
            if (winner != null) standings.merge(winner, 1D, Double::sum);
            else {
                standings.merge(right, 0.5D, Double::sum);
                standings.merge(left, 0.5D, Double::sum);
            }
        }
        if (!activeRoundInstances.isEmpty()) return;

        boolean hasNextRound = prepareNextRound();
        plugin.getScheduleManager()
                .settleEventRound(
                        GameTypeEnum.BattleBox,
                        hasNextRound,
                        () -> {
                            if (!enabled) return;
                            if (hasNextRound) nextBattleBoxRound();
                            else endSchedule();
                        });
    }

    public boolean hasNextRound() {
        return enabled && subRound < totalRounds;
    }

    private boolean prepareNextRound() {
        if (!hasNextRound()) return false;
        if (rounds.size() > subRound) return true;
        List<TwoVTwoVector> next =
                FormalPairingScheduler.standingsRound(teams, standings, previousOpponents);
        if (next.isEmpty()) {
            plugin.getLogger()
                    .warning(
                            LogText.formatGameLog(
                                    GameTypeEnum.BattleBox,
                                    scheduledMapName,
                                    "调度",
                                    "对阵",
                                    "无法在不重复对手的前提下生成第 " + (subRound + 1) + " 轮"));
            return false;
        }
        rounds.add(next);
        FormalPairingScheduler.rememberOpponents(next, previousOpponents);
        return true;
    }
}
