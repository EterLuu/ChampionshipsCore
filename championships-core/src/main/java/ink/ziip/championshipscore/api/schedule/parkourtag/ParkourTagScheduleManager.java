package ink.ziip.championshipscore.api.schedule.parkourtag;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseManager;
import ink.ziip.championshipscore.api.game.parkourtag.ParkourTagArea;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.object.game.GameRunMode;
import ink.ziip.championshipscore.api.object.schedule.TwoVTwoVector;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.message.ScheduleMessageConfig;
import ink.ziip.championshipscore.api.schedule.FormalEventMapResolver;
import ink.ziip.championshipscore.util.Utils;
import lombok.Getter;
import org.bukkit.Sound;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class ParkourTagScheduleManager extends BaseManager {
    private static final int ROUND_TRANSITION_SECONDS = 10;
    private final BukkitScheduler scheduler;
    private final ParkourTagScheduleHandler handler;
    private final List<Set<TwoVTwoVector>> rounds = new ArrayList<>();
    @Getter
    private int subRound;
    private int timer;
    @Getter
    private boolean enabled;
    private BukkitTask firstStartTask;
    private BukkitTask startTask;
    private String scheduledMapName;
    private final Set<ParkourTagArea> activeRoundInstances =
            Collections.newSetFromMap(new IdentityHashMap<>());

    public ParkourTagScheduleManager(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
        handler = new ParkourTagScheduleHandler(championshipsCore, this);
        scheduler = championshipsCore.getServer().getScheduler();
        subRound = 0;
    }

    private boolean cycleGeneratePairs() {
        this.rounds.clear();

        List<ChampionshipTeam> teams = new ArrayList<>(plugin.getTeamManager().getTeamList());

        if (teams.size() < 2 || teams.size() % 2 != 0) {
            plugin.getLogger().warning(Utils.formatGameLog(GameTypeEnum.ParkourTag, "-", "调度", "对阵",
                    "队伍数=" + teams.size() + "，至少需要两支且必须为偶数"));
            return false;
        }

        int rounds = Math.min(9, teams.size() - 1); // >=10 teams capped at 9 rounds; fewer -> full N-1 round-robin
        int pairs = teams.size() / 2;

        Collections.shuffle(teams);

        ChampionshipTeam firstTeam = teams.getFirst();
        teams.remove(firstTeam);

        int teamsSize = teams.size();

        for (int i = 0; i < rounds; i++) {
            int teamIdx = i % teamsSize;

            Set<TwoVTwoVector> set = new HashSet<>();

            set.add(new TwoVTwoVector(firstTeam, teams.get(teamIdx)));

            for (int j = 1; j < pairs; j++) {
                int firstTeamNum = (i + j) % teamsSize;
                int secondTeamNum = (i + teamsSize - j) % teamsSize;
                TwoVTwoVector tv = new TwoVTwoVector(teams.get(firstTeamNum), teams.get(secondTeamNum));
                set.add(tv);
            }
            this.rounds.add(set);
        }
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

    public void startParkourTag() {
        if (enabled) {
            endSchedule();
            return;
        }

        if (!cycleGeneratePairs()) return;
        scheduledMapName = FormalEventMapResolver.map(plugin, GameTypeEnum.ParkourTag, 1);
        if (scheduledMapName == null
                || plugin.getGameManager().getParkourTagManager().getArea(scheduledMapName) == null) {
            plugin.getLogger().warning(Utils.formatGameLog(GameTypeEnum.ParkourTag, "-", "调度", "启动",
                    "无法开始：缺少 formal-events 地图 " + scheduledMapName));
            return;
        }
        int requiredInstances = rounds.getFirst().size();
        long availableInstances = plugin.getGameManager().getParkourTagManager()
                .getMapInstances(scheduledMapName).stream()
                .filter(instance -> instance.getGameStageEnum() == ink.ziip.championshipscore.api.object.stage.GameStageEnum.WAITING)
                .count();
        if (availableInstances < requiredInstances) {
            plugin.getLogger().warning(Utils.formatGameLog(GameTypeEnum.ParkourTag, scheduledMapName, "调度", "启动",
                    "无法开始：需要实例=" + requiredInstances + "，空闲实例=" + availableInstances));
            scheduledMapName = null;
            return;
        }

        plugin.getGameManager().getParkourTagManager().resetEventState();
        plugin.getScheduleManager().addRound(GameTypeEnum.ParkourTag);
        enabled = true;
        timer = 10;
        subRound = 0;

        firstStartTask = scheduler.runTaskTimer(plugin, () -> {

            plugin.getScheduleManager().showRoundPreparationCountdown(GameTypeEnum.ParkourTag, 1, timer);

            if (timer == 10) {
                Utils.sendMessageToAllPlayers(Utils.getMessage(ScheduleMessageConfig.PARKOUR_TAG));
            }

            if (timer == 5) {
                Utils.sendMessageToAllPlayers(Utils.getMessage(ScheduleMessageConfig.PARKOUR_TAG_POINTS));
            }

            if (timer == 0) {
                subRound = 0;
                startParkourTagRound();
                if (firstStartTask != null)
                    firstStartTask.cancel();
            }
            timer--;
        }, 0, 20L);
    }

    public void startParkourTagRound() {
        if (!enabled)
            return;

        subRound++;
        if (subRound > rounds.size()) {
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

        List<TwoVTwoVector> pairs = new ArrayList<>(rounds.get(subRound - 1));

        List<ParkourTagArea> started = plugin.getGameManager()
                .joinParkourTagInstances(areaName, pairs, subRound == 1, GameRunMode.EVENT);
        if (started != null) {
            activeRoundInstances.clear();
            activeRoundInstances.addAll(started);
            plugin.getLogger().info(Utils.formatGameLog(GameTypeEnum.ParkourTag, areaName, "调度", "轮次",
                    "第 " + subRound + " 轮开始，对局数=" + pairs.size()));
        } else {
            abortSchedule("第 " + subRound + " 轮启动失败");
        }
    }

    private void abortSchedule(String reason) {
        plugin.getLogger().warning(Utils.formatGameLog(GameTypeEnum.ParkourTag,
                scheduledMapName == null ? "-" : scheduledMapName, "调度", "中止", reason));
        if (firstStartTask != null) firstStartTask.cancel();
        if (startTask != null) startTask.cancel();
        enabled = false;
        activeRoundInstances.clear();
        scheduledMapName = null;
        handler.unRegister();
        plugin.getGameManager().releaseEventSpectatorsForGame(GameTypeEnum.ParkourTag);
        plugin.getScheduleManager().clearRoundPreparationCountdown();
    }

    public void endSchedule() {
        if (firstStartTask != null)
            firstStartTask.cancel();
        if (startTask != null)
            startTask.cancel();

        enabled = false;
        activeRoundInstances.clear();
        scheduledMapName = null;

        handler.unRegister();
        plugin.getScheduleManager().clearRoundPreparationCountdown();
        plugin.getGameManager().releaseEventSpectatorsForGame(GameTypeEnum.ParkourTag);
        rounds.clear();
    }

    public void nextParkourTagRound() {
        if (!enabled)
            return;

        subRound++;
        if (subRound > rounds.size()) {
            endSchedule();
            return;
        }
        Utils.playSoundToAllPlayers(Sound.ENTITY_PLAYER_LEVELUP, 1, 1F);

        timer = ROUND_TRANSITION_SECONDS;
        startTask = scheduler.runTaskTimer(plugin, () -> {

            plugin.getScheduleManager().showRoundPreparationCountdown(GameTypeEnum.ParkourTag, subRound, timer);

            if (timer == ROUND_TRANSITION_SECONDS) {
                Utils.sendMessageToAllPlayers(Utils.getMessage(ScheduleMessageConfig.NEXT_ROUND_SOON));
            }

            if (timer == 0) {
                startRoundBattle();
                if (startTask != null)
                    startTask.cancel();
            }
            timer--;
        }, 0, 20L);
    }

    /** Called when the Parkour Tag area finishes a whole round (all its parallel matches done). */
    public synchronized void onInstanceComplete(ParkourTagArea instance) {
        if (!activeRoundInstances.remove(instance)) return;
        if (!activeRoundInstances.isEmpty()) return;

        boolean hasNextRound = hasNextRound();
        plugin.getScheduleManager().settleEventRound(GameTypeEnum.ParkourTag, hasNextRound, () -> {
            if (!enabled) return;
            if (hasNextRound) nextParkourTagRound();
            else endSchedule();
        });
    }

    public boolean hasNextRound() {
        return enabled && subRound < rounds.size();
    }

}
