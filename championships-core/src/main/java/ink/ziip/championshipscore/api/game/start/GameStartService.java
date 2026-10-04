package ink.ziip.championshipscore.api.game.start;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.instance.BaseGameInstance;
import ink.ziip.championshipscore.api.game.instance.multiteam.BaseMultiTeamGameInstance;
import ink.ziip.championshipscore.api.game.model.GameRunMode;
import ink.ziip.championshipscore.api.game.model.GameStageEnum;
import ink.ziip.championshipscore.api.game.model.GameTypeEnum;
import ink.ziip.championshipscore.api.schedule.model.TwoVTwoVector;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Resolves the complete command request before invoking the game's existing start lifecycle. */
public final class GameStartService {
    public record Result(boolean started, String detail) {}

    private final ChampionshipsCore plugin;

    public GameStartService(ChampionshipsCore plugin) {
        this.plugin = plugin;
    }

    public List<ChampionshipTeam> resolveTeams(GameStartArguments arguments) {
        return resolveTeams(arguments, false, GameRunMode.GAME);
    }

    public List<ChampionshipTeam> resolveTeams(
            GameStartArguments arguments, boolean introduction, GameRunMode mode) {
        List<ChampionshipTeam> teams = new ArrayList<>();
        if (arguments.allTeams()) {
            teams.addAll(plugin.getTeamManager().getTeamList());
            teams.sort(Comparator.comparingInt(ChampionshipTeam::getId));
        } else {
            for (String name : arguments.teams()) {
                var team =
                        plugin.getTeamManager()
                                .getTeam(name.startsWith("#") ? name.substring(1) : name);
                if (team == null) throw new IllegalArgumentException("找不到参赛队伍：" + name);
                teams.add(team);
            }
        }
        GameStartRules.validateTeamCount(arguments.game(), teams.size());
        String failure =
                plugin.getGameManager()
                        .validateStartTeams(arguments.game(), teams, introduction, mode);
        if (failure != null) throw new IllegalArgumentException(failure);
        return List.copyOf(teams);
    }

    public CompletionStage<Result> startManual(GameStartArguments arguments) {
        try {
            GameTypeEnum game = arguments.game();
            if (!plugin.getGameManager().isGameEnabled(game))
                throw new IllegalArgumentException("游戏未启用：" + game.commandName());
            List<ChampionshipTeam> teams = resolveTeams(arguments);
            var manager = plugin.getGameManager().getAreaManager(game);
            if (manager == null) throw new IllegalArgumentException("游戏没有地图管理器");
            String map;
            if (arguments.map() == null) {
                map =
                        manager.getAreaNameList().stream()
                                .sorted(String.CASE_INSENSITIVE_ORDER)
                                .filter(
                                        name ->
                                                plugin.getPrepareSessionManager()
                                                        .canStart(game, name))
                                .filter(
                                        name ->
                                                game == GameTypeEnum.Bingo
                                                        || canFit(
                                                                game,
                                                                manager.getMapInstances(name),
                                                                teams.size(),
                                                                arguments.arenas()))
                                .findFirst()
                                .orElseThrow(() -> new IllegalArgumentException("没有已发布且空闲的地图"));
            } else {
                map =
                        manager.getAreaNameList().stream()
                                .filter(name -> name.equalsIgnoreCase(arguments.map()))
                                .findFirst()
                                .orElseThrow(
                                        () ->
                                                new IllegalArgumentException(
                                                        "找不到地图：" + arguments.map()));
            }
            if (!plugin.getPrepareSessionManager().canStart(game, map)) {
                throw new IllegalArgumentException("地图未就绪：" + map);
            }
            if (game == GameTypeEnum.Bingo) {
                arguments.arenas().resolve(1);
                return plugin.getGameManager()
                        .joinBingoForTeams(map, false, GameRunMode.GAME, teams)
                        .handle(
                                (started, failure) ->
                                        new Result(
                                                failure == null && Boolean.TRUE.equals(started),
                                                failure != null
                                                        ? "Bingo 执行端启动失败"
                                                        : Boolean.TRUE.equals(started)
                                                                ? "已启动 " + map
                                                                : "Bingo 执行端拒绝启动"));
            }
            boolean started;
            if (GameStartRules.topology(game) == GameStartRules.Topology.PAIRED_COPIES) {
                var pairs =
                        StartAllocation.pair(teams).stream()
                                .map(pair -> new TwoVTwoVector(pair.first(), pair.second()))
                                .toList();
                started =
                        plugin.getGameManager()
                                        .joinPairedInstances(
                                                game,
                                                map,
                                                pairs,
                                                arguments.arenas(),
                                                false,
                                                GameRunMode.GAME)
                                != null;
            } else if (GameStartRules.topology(game) == GameStartRules.Topology.PAIRED_FINAL) {
                arguments.arenas().resolve(1);
                started =
                        game == GameTypeEnum.Dodgebolt
                                ? plugin.getGameManager()
                                        .joinDodgeboltArea(
                                                map,
                                                teams.get(0),
                                                teams.get(1),
                                                teams.get(0),
                                                false,
                                                false,
                                                GameRunMode.GAME)
                                : plugin.getGameManager()
                                        .joinTeamArea(
                                                game,
                                                map,
                                                teams.get(0),
                                                teams.get(1),
                                                false,
                                                GameRunMode.GAME);
            } else {
                List<? extends BaseGameInstance> pool = manager.getMapInstances(map);
                BaseGameInstance selected =
                        StartTargets.forGame(game, pool, teams.size(), arguments.arenas())
                                .getFirst();
                if (!(selected instanceof BaseMultiTeamGameInstance multi))
                    throw new IllegalArgumentException("地图不是多队比赛实例");
                ArenaSelection selection =
                        GameStartRules.internalArenas(game)
                                ? arguments.arenas()
                                : ArenaSelection.all();
                selection.resolve(SubArenaSupport.count(selected));
                selected.prepareArenaSelection(selection);
                started =
                        plugin.getGameManager()
                                .joinMultiTeamInstanceForTeams(
                                        game, multi, false, GameRunMode.GAME, teams);
                if (!started && selected.getGameStageEnum() == GameStageEnum.WAITING)
                    selected.prepareArenaSelection(ArenaSelection.all());
            }
            return CompletableFuture.completedFuture(
                    new Result(
                            started,
                            started
                                    ? "已启动 " + map + "，参赛队伍 " + teams.size() + " 支"
                                    : "启动失败：队伍、空闲副本数量或场地状态不满足要求"));
        } catch (IllegalArgumentException | IllegalStateException failure) {
            return CompletableFuture.completedFuture(new Result(false, failure.getMessage()));
        }
    }

    private static boolean canFit(
            GameTypeEnum game,
            List<? extends BaseGameInstance> pool,
            int teamCount,
            ArenaSelection arenas) {
        try {
            StartTargets.forGame(game, pool, teamCount, arenas);
            return true;
        } catch (IllegalArgumentException failure) {
            return false;
        }
    }

    public void validateMap(
            GameTypeEnum game, String map, List<ChampionshipTeam> teams, ArenaSelection arenas) {
        var manager = plugin.getGameManager().getAreaManager(game);
        if (manager == null || !plugin.getPrepareSessionManager().canStart(game, map))
            throw new IllegalArgumentException("地图未就绪：" + map);
        if (game == GameTypeEnum.Bingo) {
            arenas.resolve(1);
            if (!plugin.getGameManager().canStartBingoForTeams(map, true, GameRunMode.EVENT, teams))
                throw new IllegalArgumentException("Bingo 执行端或参赛名单不可用");
        } else {
            StartTargets.forGame(game, manager.getMapInstances(map), teams.size(), arenas);
        }
    }
}
