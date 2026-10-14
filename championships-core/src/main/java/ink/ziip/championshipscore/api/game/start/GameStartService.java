package ink.ziip.championshipscore.api.game.start;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.instance.BaseGameInstance;
import ink.ziip.championshipscore.api.game.instance.multiteam.BaseMultiTeamGameInstance;
import ink.ziip.championshipscore.api.game.model.GameRunMode;
import ink.ziip.championshipscore.api.game.model.GameStageEnum;
import ink.ziip.championshipscore.api.game.model.GameTypeEnum;
import ink.ziip.championshipscore.api.schedule.model.TwoVTwoVector;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;

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
                if (team == null)
                    throw new IllegalArgumentException(
                            MessageConfig.codeText("game-start-service.resolve-teams-1", name));
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
                throw new IllegalArgumentException(
                        MessageConfig.codeText(
                                "game-start-service.start-manual-1", game.commandName()));
            List<ChampionshipTeam> teams = resolveTeams(arguments);
            var manager = plugin.getGameManager().getAreaManager(game);
            if (manager == null)
                throw new IllegalArgumentException(
                        MessageConfig.codeText("game-start-service.start-manual-2"));
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
                                .orElseThrow(
                                        () ->
                                                new IllegalArgumentException(
                                                        MessageConfig.codeText(
                                                                "game-start-service.start-manual-4")));
            } else {
                map =
                        manager.getAreaNameList().stream()
                                .filter(name -> name.equalsIgnoreCase(arguments.map()))
                                .findFirst()
                                .orElseThrow(
                                        () ->
                                                new IllegalArgumentException(
                                                        MessageConfig.codeText(
                                                                "game-start-service.start-manual-3",
                                                                arguments.map())));
            }
            if (!plugin.getPrepareSessionManager().canStart(game, map)) {
                throw new IllegalArgumentException(
                        MessageConfig.codeText("game-start-service.start-manual-5", map));
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
                                                        ? MessageConfig.codeText(
                                                                "game-start-service.start-manual-8")
                                                        : Boolean.TRUE.equals(started)
                                                                ? MessageConfig.codeText(
                                                                        "game-start-service.start-manual-7",
                                                                        map)
                                                                : MessageConfig.codeText(
                                                                        "game-start-service.start-manual-6")));
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
                    throw new IllegalArgumentException(
                            MessageConfig.codeText("game-start-service.start-manual-9"));
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
                                    ? MessageConfig.codeText(
                                            "game-start-service.start-manual-11", map, teams.size())
                                    : MessageConfig.codeText(
                                            "game-start-service.start-manual-10")));
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
            throw new IllegalArgumentException(
                    MessageConfig.codeText("game-start-service.start-manual-5", map));
        if (game == GameTypeEnum.Bingo) {
            arenas.resolve(1);
            if (!plugin.getGameManager().canStartBingoForTeams(map, true, GameRunMode.EVENT, teams))
                throw new IllegalArgumentException(
                        MessageConfig.codeText("game-start-service.validate-map-1"));
        } else {
            StartTargets.forGame(game, manager.getMapInstances(map), teams.size(), arenas);
        }
    }
}
