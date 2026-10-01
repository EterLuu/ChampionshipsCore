package ink.ziip.championshipscore.api.schedule.laserbox;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseManager;
import ink.ziip.championshipscore.api.game.laserbox.LaserBoxArea;
import ink.ziip.championshipscore.api.object.game.GameRunMode;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.object.schedule.TwoVTwoVector;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import ink.ziip.championshipscore.api.schedule.FormalEventMapResolver;
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
        if (teams.size() < 2 || teams.size() % 2 != 0 || new HashSet<>(teams).size() != teams.size())
            return List.of();
        List<ChampionshipTeam> ring = new ArrayList<>(teams);
        List<List<TwoVTwoVector>> rounds = new ArrayList<>();
        for (int round = 0; round < Math.min(9, teams.size() - 1); round++) {
            List<TwoVTwoVector> pairs = new ArrayList<>();
            for (int pair = 0; pair < ring.size() / 2; pair++)
                pairs.add(new TwoVTwoVector(ring.get(pair), ring.get(ring.size() - 1 - pair)));
            rounds.add(List.copyOf(pairs));
            ring.add(1, ring.removeLast());
        }
        return List.copyOf(rounds);
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
        rounds = roundPairs(teams);
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
        handler.register();
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
        var started = plugin.getGameManager().joinLaserBoxInstances(map, rounds.get(subRound),
                subRound == 0, GameRunMode.EVENT);
        if (started == null) {
            plugin.getLogger().warning(Utils.formatGameLog(GAME, map, "调度", "中止", "本轮对阵启动失败"));
            endSchedule();
            return;
        }
        subRound++;
        activeRoundInstances.addAll(started);
    }

    public synchronized void onInstanceComplete(LaserBoxArea instance) {
        if (!enabled || !activeRoundInstances.remove(instance) || !activeRoundInstances.isEmpty()) return;
        boolean next = hasNextRound();
        plugin.getScheduleManager().settleEventRound(GAME, next, () -> {
            if (!enabled) return;
            if (!next) { endSchedule(); return; }
            Utils.playSoundToAllPlayers(Sound.ENTITY_PLAYER_LEVELUP, 1, 1F);
            countdown(false);
        });
    }

    public boolean hasNextRound() { return enabled && subRound < rounds.size(); }

    public void endSchedule() {
        enabled = false;
        if (countdownTask != null) countdownTask.cancel();
        countdownTask = null;
        handler.unRegister();
        activeRoundInstances.clear();
        rounds = List.of();
        map = null;
        plugin.getScheduleManager().clearRoundPreparationCountdown();
        plugin.getGameManager().releaseEventSpectatorsForGame(GAME);
        plugin.getGameManager().releaseRoundTransitionHolds(GAME);
    }
}
