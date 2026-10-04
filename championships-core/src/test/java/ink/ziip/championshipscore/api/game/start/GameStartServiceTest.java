package ink.ziip.championshipscore.api.game.start;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareSessionManager;
import ink.ziip.championshipscore.api.game.config.BaseGameConfig;
import ink.ziip.championshipscore.api.game.frostbite.runtime.FrostbiteArea;
import ink.ziip.championshipscore.api.game.hotycodydusky.config.HotyCodyDuskyConfig;
import ink.ziip.championshipscore.api.game.hotycodydusky.runtime.HotyCodyDuskyTeamArea;
import ink.ziip.championshipscore.api.game.instance.BaseGameInstance;
import ink.ziip.championshipscore.api.game.instance.multiteam.BaseMultiTeamGameInstance;
import ink.ziip.championshipscore.api.game.instance.paired.BasePairedGameInstance;
import ink.ziip.championshipscore.api.game.manager.*;
import ink.ziip.championshipscore.api.game.model.*;
import ink.ziip.championshipscore.api.game.riptiderush.config.RiptideRushConfig;
import ink.ziip.championshipscore.api.game.riptiderush.runtime.RiptideRushArea;
import ink.ziip.championshipscore.api.game.snowball.runtime.SnowballShowdownTeamArea;
import ink.ziip.championshipscore.api.game.tntrun.config.TNTRunConfig;
import ink.ziip.championshipscore.api.game.tntrun.runtime.TNTRunTeamArea;
import ink.ziip.championshipscore.api.schedule.ScheduleManager;
import ink.ziip.championshipscore.api.schedule.model.TwoVTwoVector;
import ink.ziip.championshipscore.api.team.*;

import org.bukkit.*;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;

class GameStartServiceTest {
    @ParameterizedTest
    @EnumSource(GameTypeEnum.class)
    void everyGameCarriesTheChosenMapAndOnlyTheExplicitTeams(GameTypeEnum game) throws Exception {
        withFixture(
                game,
                f -> {
                    String arena = GameStartRules.internalArenas(game) ? "3" : "1";
                    var arguments =
                            GameStartArguments.parse(
                                    new String[] {
                                        game.commandName(),
                                        "CHOSEN",
                                        "#2",
                                        "team-1",
                                        "--arena",
                                        arena
                                    },
                                    false);
                    var result =
                            new GameStartService(f.plugin)
                                    .startManual(arguments)
                                    .toCompletableFuture()
                                    .join();
                    assertTrue(result.started(), result.detail());
                    assertEquals("Chosen", f.manager.map);
                    assertEquals(List.of(f.teams.get(1), f.teams.get(0)), f.manager.requested);
                    assertFalse(f.manager.requested.contains(f.teams.get(2)));
                    assertEquals(GameRunMode.GAME, f.manager.mode);
                    if (GameStartRules.internalArenas(game))
                        assertEquals(List.of(2), f.area.getSelectedArenaIndices(4));
                });
    }

    @ParameterizedTest
    @EnumSource(
            value = GameTypeEnum.class,
            names = {"Dodgebolt", "DragonEggCarnival", "SulfurSoccer"},
            mode = EnumSource.Mode.EXCLUDE)
    void formalSelectionPersistsAndInvalidSubarenaNeverDispatches(GameTypeEnum game)
            throws Exception {
        withFixture(
                game,
                f -> {
                    var schedule = new RecordingSchedule(f.plugin);
                    set(f.plugin, "scheduleManager", schedule);
                    var arguments =
                            GameStartArguments.parse(
                                    new String[] {game.commandName(), "Chosen", "#2", "team-1"},
                                    true);
                    assertEquals(
                            ScheduleManager.EventAction.STARTED,
                            schedule.startOrStopFormalEvent(arguments));
                    assertEquals(
                            List.of(f.teams.get(1), f.teams.get(0)),
                            schedule.participatingTeams(game));
                    assertEquals("Chosen", schedule.selectedMap(game));
                    assertEquals(
                            List.of("Chosen", "Chosen"),
                            List.of(
                                    ink.ziip.championshipscore.api.schedule.FormalEventMapResolver
                                            .map(f.plugin, game, 1),
                                    ink.ziip.championshipscore.api.schedule.FormalEventMapResolver
                                            .map(f.plugin, game, 3)));
                    schedule.clearStartSelection(game);
                    schedule.running = false;
                    var invalid =
                            GameStartArguments.parse(
                                    new String[] {
                                        game.commandName(),
                                        "Chosen",
                                        "#2",
                                        "team-1",
                                        "--arena",
                                        "99"
                                    },
                                    true);
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> schedule.startOrStopFormalEvent(invalid));
                    assertNull(schedule.selectedMap(game));
                    assertEquals(1, schedule.dispatches);
                });
    }

    @Test
    void bingoExecutionFailureReturnsACompletedRejection() throws Exception {
        withFixture(
                GameTypeEnum.Bingo,
                f -> {
                    f.manager.failBingo = true;
                    var result =
                            new GameStartService(f.plugin)
                                    .startManual(
                                            GameStartArguments.parse(
                                                    new String[] {"bingo", "Chosen", "#1", "#2"},
                                                    false))
                                    .toCompletableFuture()
                                    .join();
                    assertFalse(result.started());
                    assertTrue(result.detail().contains("失败"));
                });
    }

    private record Fixture(
            ChampionshipsCore plugin,
            RecordingManager manager,
            BaseGameInstance area,
            List<ChampionshipTeam> teams) {}

    private interface Action {
        void run(Fixture f) throws Exception;
    }

    private static void withFixture(GameTypeEnum game, Action action) throws Exception {
        var sf = field(Bukkit.class, "server");
        Object previous = sf.get(null);
        try {
            Server server =
                    (Server)
                            Proxy.newProxyInstance(
                                    Server.class.getClassLoader(),
                                    new Class<?>[] {Server.class},
                                    (p, m, a) ->
                                            switch (m.getName()) {
                                                case "getPlayer" -> null;
                                                case "getScheduler" ->
                                                        Proxy.newProxyInstance(
                                                                BukkitScheduler.class
                                                                        .getClassLoader(),
                                                                new Class<?>[] {
                                                                    BukkitScheduler.class
                                                                },
                                                                (p2, m2, a2) -> null);
                                                default -> throw new AssertionError(m.getName());
                                            });
            sf.set(null, server);
            var plugin = allocate(ChampionshipsCore.class);
            set(plugin, "server", server);
            var manager = allocate(RecordingManager.class);
            set(manager, "plugin", plugin);
            set(manager, "enabledGames", Set.of(game));
            for (String name :
                    List.of(
                            "teamStatus",
                            "playerStatus",
                            "playerSpectatorStatus",
                            "roundTransitionHolds",
                            "spectatorTransitionHolds"))
                set(manager, name, new ConcurrentHashMap<>());
            set(plugin, "gameManager", manager);
            List<ChampionshipTeam> teams =
                    List.of(new TestTeam(1), new TestTeam(2), new TestTeam(3));
            var teamManager = allocate(TeamManager.class);
            set(
                    teamManager,
                    "cachedTeams",
                    new ConcurrentHashMap<>(
                            Map.of(
                                    "team-1",
                                    teams.get(0),
                                    "team-2",
                                    teams.get(1),
                                    "team-3",
                                    teams.get(2))));
            set(teamManager, "pendingMemberTeamIds", Set.of());
            set(teamManager, "pendingTeamDeletions", Set.of());
            set(plugin, "teamManager", teamManager);
            BaseGameInstance area;
            BaseGameConfig config;
            switch (game) {
                case TNTRun -> {
                    area = allocate(TNTRunTeamArea.class);
                    var c = new TNTRunConfig(plugin, "Chosen");
                    set(c, "spawnPoints", List.of("1", "2", "3", "4"));
                    config = c;
                }
                case HotyCodyDusky -> {
                    area = allocate(HotyCodyDuskyTeamArea.class);
                    var c = new HotyCodyDuskyConfig(plugin, "Chosen");
                    c.setCopies(4);
                    config = c;
                }
                case SnowballShowdown -> {
                    area = allocate(SnowballShowdownTeamArea.class);
                    set(area, "areaLocations", List.of(List.of(), List.of(), List.of(), List.of()));
                    config =
                            new ink.ziip.championshipscore.api.game.snowball.config
                                    .SnowballShowdownConfig(plugin, "Chosen");
                }
                case FrostbiteFrenzy -> {
                    area = allocate(FrostbiteArea.class);
                    config =
                            new ink.ziip.championshipscore.api.game.frostbite.config
                                    .FrostbiteConfig(plugin, "Chosen");
                }
                default -> {
                    area = allocate(RiptideRushArea.class);
                    config = new RiptideRushConfig(plugin, "Chosen");
                }
            }
            set(area, "gameConfig", config);
            set(area, "gameStageEnum", GameStageEnum.WAITING);
            set(area, "gameTypeEnum", game);
            var maps = new Maps(plugin);
            maps.register(area);
            set(manager, "areaManagers", Map.of(game, maps));
            set(plugin, "prepareSessionManager", allocate(ReadyPrepare.class));
            action.run(new Fixture(plugin, manager, area, teams));
        } finally {
            sf.set(null, previous);
        }
    }

    private static final class RecordingSchedule extends ScheduleManager {
        boolean running;
        int dispatches;

        RecordingSchedule(ChampionshipsCore p) {
            super(p);
        }

        @Override
        public boolean isFormalEventRunning(GameTypeEnum g) {
            return running;
        }

        @Override
        public EventAction startOrStopFormalEvent(GameTypeEnum g) {
            dispatches++;
            running = !running;
            return running ? EventAction.STARTED : EventAction.STOPPED;
        }
    }

    private static final class RecordingManager extends GameManager {
        String map;
        List<ChampionshipTeam> requested;
        GameRunMode mode;
        boolean failBingo;

        private RecordingManager() {
            super(null);
        }

        private boolean record(String map, List<ChampionshipTeam> teams, GameRunMode mode) {
            this.map = map;
            this.requested = List.copyOf(teams);
            this.mode = mode;
            return true;
        }

        @Override
        public boolean joinMultiTeamInstanceForTeams(
                GameTypeEnum g,
                BaseMultiTeamGameInstance area,
                boolean intro,
                GameRunMode mode,
                List<ChampionshipTeam> teams) {
            return record(area.getGameConfig().getConfigName(), teams, mode);
        }

        @Override
        public List<BasePairedGameInstance> joinPairedInstances(
                GameTypeEnum g,
                String map,
                List<TwoVTwoVector> pairs,
                ArenaSelection selection,
                boolean intro,
                GameRunMode mode) {
            record(
                    map,
                    pairs.stream()
                            .flatMap(
                                    p -> java.util.stream.Stream.of(p.getTeamOne(), p.getTeamTwo()))
                            .toList(),
                    mode);
            return List.of();
        }

        @Override
        public boolean joinDodgeboltArea(
                String map,
                ChampionshipTeam right,
                ChampionshipTeam left,
                ChampionshipTeam seed,
                boolean intro,
                boolean force,
                GameRunMode mode) {
            return record(map, List.of(right, left), mode);
        }

        @Override
        public boolean joinTeamArea(
                GameTypeEnum g,
                String map,
                ChampionshipTeam right,
                ChampionshipTeam left,
                boolean intro,
                GameRunMode mode) {
            return record(map, List.of(right, left), mode);
        }

        @Override
        public CompletionStage<Boolean> joinBingoForTeams(
                String map, boolean intro, GameRunMode mode, List<ChampionshipTeam> teams) {
            record(map, teams, mode);
            return failBingo
                    ? CompletableFuture.failedFuture(new IllegalStateException("worker down"))
                    : CompletableFuture.completedFuture(true);
        }

        @Override
        public boolean canStartBingoForTeams(
                String map, boolean intro, GameRunMode mode, List<ChampionshipTeam> teams) {
            return true;
        }
    }

    private static final class ReadyPrepare extends PrepareSessionManager {
        ReadyPrepare(ChampionshipsCore p) {
            super(p);
        }

        @Override
        public boolean canStart(GameTypeEnum g, String map) {
            return map.equals("Chosen");
        }
    }

    private static final class Maps extends BaseGameInstanceManager<BaseGameInstance> {
        Maps(ChampionshipsCore p) {
            super(p);
        }

        void register(BaseGameInstance area) {
            registerMapInstances("Chosen", List.of(area));
        }

        @Override
        public void load() {}

        @Override
        public boolean addArea(String name) {
            return false;
        }
    }

    private static final class TestTeam extends ChampionshipTeam {
        TestTeam(int id) {
            super(id, "team-" + id, "red", "#FFFFFF", Set.of(new UUID(id, 1)), null);
        }
    }

    private static Field field(Class<?> t, String n) throws Exception {
        for (; t != null; t = t.getSuperclass())
            try {
                var f = t.getDeclaredField(n);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {
            }
        throw new NoSuchFieldException(n);
    }

    private static void set(Object o, String n, Object v) throws Exception {
        field(o.getClass(), n).set(o, v);
    }

    private static <T> T allocate(Class<T> t) throws Exception {
        return t.cast(
                ((sun.misc.Unsafe) field(sun.misc.Unsafe.class, "theUnsafe").get(null))
                        .allocateInstance(t));
    }
}
