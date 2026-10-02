package ink.ziip.championshipscore.api.schedule.laserbox;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseManager;
import ink.ziip.championshipscore.api.game.laserbox.LaserBoxArea;
import ink.ziip.championshipscore.api.object.game.GameRunMode;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.object.schedule.TwoVTwoVector;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import ink.ziip.championshipscore.api.schedule.FormalEventMapResolver;
import ink.ziip.championshipscore.api.schedule.FormalPairingScheduler;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.message.ScheduleMessageConfig;
import ink.ziip.championshipscore.util.Utils;
import lombok.Getter;
import org.bukkit.Sound;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/** BattleBox-style round robin: all pairings finish and settle before the next round starts. */
public final class LaserBoxScheduleManager extends BaseManager {
    private static final GameTypeEnum GAME = GameTypeEnum.LaserBox;
    private final BukkitScheduler scheduler;
    private final LaserBoxScheduleHandler handler;
    private final Set<LaserBoxArea> activeRoundInstances = Collections.newSetFromMap(new IdentityHashMap<>());
    private List<List<TwoVTwoVector>> rounds = List.of();
    private List<ChampionshipTeam> teams = List.of();
    private final Map<ChampionshipTeam, Double> standings = new HashMap<>();
    private final Set<String> previousOpponents = new HashSet<>();
    private int totalRounds;
    private String map;
    private BukkitTask countdownTask;
    @Getter private boolean enabled;
    @Getter private int subRound;
    @Getter private String lastStartFailureReason;

    public LaserBoxScheduleManager(ChampionshipsCore plugin) {
        super(plugin);
        scheduler = plugin.getServer().getScheduler();
        handler = new LaserBoxScheduleHandler(plugin, this);
    }

    static List<List<TwoVTwoVector>> roundPairs(List<ChampionshipTeam> teams) {
        return FormalPairingScheduler.roundRobin(teams, FormalPairingScheduler.totalRounds(teams.size()));
    }

    @Override public void load() { }
    @Override public void unload() { if (enabled) endSchedule(); }

    public boolean startGame() {
        lastStartFailureReason = null;
        if (enabled) {
            lastStartFailureReason = "激光方盒赛程正在运行";
            return false;
        }
        List<ChampionshipTeam> teams = new ArrayList<>(plugin.getTeamManager().getTeamList());
        Collections.shuffle(teams);
        this.teams = List.copyOf(teams);
        totalRounds = FormalPairingScheduler.totalRounds(teams.size());
        standings.clear();
        teams.forEach(team -> standings.put(team, 0D));
        previousOpponents.clear();
        rounds = FormalPairingScheduler.roundRobin(teams, FormalPairingScheduler.seededRounds(teams.size()));
        rounds.forEach(round -> FormalPairingScheduler.rememberOpponents(round, previousOpponents));
        if (rounds.isEmpty()) return reject("至少需要两支队伍，队伍数必须为偶数");
        List<String> maps = FormalEventMapResolver.maps(plugin, GAME);
        if (maps.isEmpty()) return reject("没有已加载的激光方盒地图");
        map = maps.getFirst();
        if (plugin.getConfigurationManager().getCCConfig().formalEventMaps(GAME).isEmpty()) {
            // Automatic selection uses real registered maps and prefers one ready to start.
            map = maps.stream().filter(name -> plugin.getPrepareSessionManager().canStart(GAME, name))
                    .findFirst().orElse(map);
        }
        String mapFailure = plugin.getPrepareSessionManager().getStartFailureReason(GAME, map);
        if (mapFailure != null) return reject("地图“" + map + "”" + mapFailure);
        var copies = plugin.getGameManager().getLaserBoxManager().getMapInstances(map);
        long available = copies.stream().filter(copy -> copy.getGameStageEnum() == GameStageEnum.WAITING).count();
        if (available < teams.size() / 2)
            return reject("需要 " + teams.size() / 2 + " 个空闲场地副本，当前只有 " + available + " 个");
        try {
            for (var copy : copies) copy.getGameConfig().validate();
        } catch (RuntimeException failure) {
            return reject(failure.getMessage());
        }
        plugin.getScheduleManager().addRound(GAME);
        enabled = true;
        subRound = 0;
        countdown(true);
        return true;
    }

    private boolean reject(String reason) {
        lastStartFailureReason = reason;
        plugin.getLogger().warning(Utils.formatGameLog(GAME, map == null ? "-" : map, "调度", "启动", reason));
        rounds = List.of();
        map = null;
        return false;
    }

    private void countdown(boolean firstRound) {
        final int[] remaining = {10};
        countdownTask = scheduler.runTaskTimer(plugin, () -> {
            if (!enabled) return;
            plugin.getScheduleManager().showRoundPreparationCountdown(GAME, subRound + 1, remaining[0]);
            if (remaining[0] == 10)
                Utils.sendMessageToAllPlayers(firstRound ? plugin.getScheduleManager().getScheduleStrings(GAME)
                        : Utils.getMessage(ScheduleMessageConfig.NEXT_ROUND_SOON));
            if (firstRound && remaining[0] == 5)
                Utils.sendMessageToAllPlayers(plugin.getScheduleManager().getSchedulePointsStrings(GAME));
            if (remaining[0] == 0) {
                countdownTask.cancel();
                countdownTask = null;
                startRound();
                return;
            }
            remaining[0]--;
        }, 0L, 20L);
    }

    private void startRound() {
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
        List<TwoVTwoVector> pairs = rounds.get(subRound - 1);
        var started = plugin.getGameManager().joinLaserBoxInstances(map, pairs,
                subRound == 1, GameRunMode.EVENT);
        if (started == null) {
            plugin.getLogger().warning(Utils.formatGameLog(GAME, map, "调度", "中止", "本轮对阵启动失败"));
            endSchedule();
            return;
        }
        activeRoundInstances.clear();
        activeRoundInstances.addAll(started);
        plugin.getLogger().info(Utils.formatGameLog(GAME, map, "调度", "轮次",
                "第 " + subRound + " 轮开始，对局数=" + pairs.size()));
    }

    public synchronized void onInstanceComplete(LaserBoxArea instance) {
        onInstanceComplete(instance, instance.getMatchWinner());
    }

    public synchronized void onInstanceComplete(LaserBoxArea instance, ChampionshipTeam winner) {
        if (!enabled || !activeRoundInstances.remove(instance)) return;
        ChampionshipTeam right = instance.getRightChampionshipTeam();
        ChampionshipTeam left = instance.getLeftChampionshipTeam();
        if (right != null && left != null) {
            if (winner != null) standings.merge(winner, 1D, Double::sum);
            else {
                standings.merge(right, 0.5D, Double::sum);
                standings.merge(left, 0.5D, Double::sum);
            }
        }
        // Every parallel match contributes to the round standings. Only after all
        // instances have reported may the scheduler settle and advance the event.
        if (!activeRoundInstances.isEmpty()) return;
        boolean next = prepareNextRound();
        plugin.getScheduleManager().settleEventRound(GAME, next, () -> {
            if (!enabled) return;
            if (!next) { endSchedule(); return; }
            Utils.playSoundToAllPlayers(Sound.ENTITY_PLAYER_LEVELUP, 1, 1F);
            countdown(false);
        });
    }

    public boolean hasNextRound() {
        int configuredRounds = totalRounds > 0 ? totalRounds : rounds.size();
        return enabled && subRound < configuredRounds;
    }

    private boolean prepareNextRound() {
        if (!hasNextRound()) return false;
        if (rounds.size() > subRound) return true;
        List<TwoVTwoVector> next = FormalPairingScheduler.standingsRound(teams, standings, previousOpponents);
        if (next.isEmpty()) {
            plugin.getLogger().warning(Utils.formatGameLog(GAME, map == null ? "-" : map,
                    "调度", "对阵", "无法在不重复对手的前提下生成第 " + (subRound + 1) + " 轮"));
            return false;
        }
        List<List<TwoVTwoVector>> updated = new ArrayList<>(rounds);
        updated.add(next);
        rounds = List.copyOf(updated);
        FormalPairingScheduler.rememberOpponents(next, previousOpponents);
        return true;
    }

    public void endSchedule() {
        enabled = false;
        if (countdownTask != null) countdownTask.cancel();
        countdownTask = null;
        handler.unRegister();
        activeRoundInstances.clear();
        rounds = List.of();
        teams = List.of();
        standings.clear();
        previousOpponents.clear();
        totalRounds = 0;
        map = null;
        plugin.getScheduleManager().clearRoundPreparationCountdown();
        plugin.getGameManager().releaseEventSpectatorsForGame(GAME);
        plugin.getGameManager().releaseRoundTransitionHolds(GAME);
    }
}
