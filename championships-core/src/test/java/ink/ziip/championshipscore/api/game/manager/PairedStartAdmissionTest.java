package ink.ziip.championshipscore.api.game.manager;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareSessionManager;
import ink.ziip.championshipscore.api.game.model.*;
import ink.ziip.championshipscore.api.game.parkourtag.ParkourTagManager;
import ink.ziip.championshipscore.api.game.parkourtag.config.ParkourTagConfig;
import ink.ziip.championshipscore.api.game.parkourtag.runtime.ParkourTagArea;
import ink.ziip.championshipscore.api.game.start.ArenaSelection;
import ink.ziip.championshipscore.api.schedule.model.TwoVTwoVector;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.api.team.TeamManager;
import ink.ziip.championshipscore.api.visibility.PlayerVisibilityManager;

import org.bukkit.*;
import org.bukkit.scheduler.*;
import org.junit.jupiter.api.Test;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;

class PairedStartAdmissionTest {
    @Test
    void sixTeamsFillThreeCopiesAndRetainUnrelatedRunningMatch() throws Exception {
        withFixture(
                f -> {
                    f.slots.getFirst().setGameStageEnum(GameStageEnum.PROGRESS);
                    var unrelated = new TestTeam(99);
                    bindings(f.manager, "teamStatus").put(unrelated, f.slots.getFirst());
                    bindings(f.manager, "playerStatus")
                            .put(unrelated.getMembers().iterator().next(), f.slots.getFirst());
                    var result = start(f, f.pairs, ArenaSelection.all());
                    assertEquals(f.slots.subList(1, 4), result);
                    assertSame(
                            f.slots.getFirst(), bindings(f.manager, "teamStatus").get(unrelated));
                    for (int i = 0; i < 3; i++) {
                        var pair = f.pairs.get(i);
                        var slot = f.slots.get(i + 1);
                        assertSame(slot, bindings(f.manager, "teamStatus").get(pair.getTeamOne()));
                        assertSame(slot, bindings(f.manager, "teamStatus").get(pair.getTeamTwo()));
                    }
                });
    }

    @Test
    void refusalAndExceptionReleaseEveryAttemptedSlotButKeepUnrelatedOwnership() throws Exception {
        for (boolean throwing : List.of(false, true))
            withFixture(
                    f -> {
                        var unrelated = new TestTeam(99);
                        var busy = f.slots.getLast();
                        busy.setGameStageEnum(GameStageEnum.PROGRESS);
                        bindings(f.manager, "teamStatus").put(unrelated, busy);
                        f.slots.get(1).reject = true;
                        f.slots.get(1).throwing = throwing;
                        if (throwing)
                            assertThrows(
                                    IllegalStateException.class,
                                    () -> start(f, f.pairs, ArenaSelection.all()));
                        else assertNull(start(f, f.pairs, ArenaSelection.all()));
                        assertEquals(1, f.slots.get(0).aborts);
                        assertEquals(1, f.slots.get(1).aborts);
                        assertEquals(0, f.slots.get(2).attempts);
                        assertEquals(0, busy.aborts);
                        assertEquals(Map.of(unrelated, busy), bindings(f.manager, "teamStatus"));
                        assertTrue(bindings(f.manager, "playerStatus").isEmpty());
                        for (var slot : f.slots)
                            assertNull(field(slot.getClass(), "coordinatedStartGate").get(slot));
                    });
    }

    @Test
    void explicitBusyCopyAndOverlappingRostersAreRejectedBeforeMutation() throws Exception {
        withFixture(
                f -> {
                    f.slots.get(1).setGameStageEnum(GameStageEnum.PROGRESS);
                    assertNull(start(f, f.pairs.subList(0, 2), ArenaSelection.parse("1,2")));
                    var samePlayer = new TestTeam(91, f.pairs.getFirst().getTeamOne().getMembers());
                    assertNull(
                            start(
                                    f,
                                    List.of(
                                            f.pairs.getFirst(),
                                            new TwoVTwoVector(samePlayer, new TestTeam(92))),
                                    ArenaSelection.all()));
                    assertNull(
                            start(
                                    f,
                                    List.of(f.pairs.getFirst(), f.pairs.getFirst()),
                                    ArenaSelection.all()));
                    assertTrue(f.slots.stream().allMatch(s -> s.attempts == 0));
                    assertTrue(bindings(f.manager, "teamStatus").isEmpty());
                });
    }

    private static Object start(Fixture f, List<TwoVTwoVector> pairs, ArenaSelection selection) {
        return f.manager.joinPairedInstances(
                GameTypeEnum.ParkourTag, "map", pairs, selection, true, GameRunMode.EVENT);
    }

    private record Fixture(GameManager manager, List<Slot> slots, List<TwoVTwoVector> pairs) {}

    private interface Action {
        void run(Fixture f) throws Exception;
    }

    private static void withFixture(Action action) throws Exception {
        Field serverField = field(Bukkit.class, "server");
        Object previous = serverField.get(null);
        try {
            var plugin = allocate(ChampionshipsCore.class);
            set(plugin, "logger", java.util.logging.Logger.getAnonymousLogger());
            BukkitTask task = proxy(BukkitTask.class, (p, m, a) -> null);
            BukkitScheduler scheduler = proxy(BukkitScheduler.class, (p, m, a) -> task);
            Server server =
                    proxy(
                            Server.class,
                            (p, m, a) ->
                                    switch (m.getName()) {
                                        case "getScheduler" -> scheduler;
                                        case "getPlayer" -> null;
                                        case "isPrimaryThread" -> false;
                                        default -> throw new AssertionError(m.getName());
                                    });
            serverField.set(null, server);
            set(plugin, "server", server);
            var manager = allocate(GameManager.class);
            set(manager, "plugin", plugin);
            set(manager, "enabledGames", Set.of(GameTypeEnum.ParkourTag));
            for (String name :
                    List.of(
                            "teamStatus",
                            "playerStatus",
                            "playerSpectatorStatus",
                            "roundTransitionHolds",
                            "spectatorTransitionHolds"))
                set(manager, name, new ConcurrentHashMap<>());
            set(plugin, "gameManager", manager);
            set(plugin, "visibilityManager", new PlayerVisibilityManager(plugin));
            var teams = allocate(TeamManager.class);
            set(teams, "cachedTeams", new ConcurrentHashMap<>());
            set(plugin, "teamManager", teams);
            var prepare = allocate(PrepareSessionManager.class);
            set(prepare, "plugin", plugin);
            set(prepare, "mapLocks", Map.of());
            set(plugin, "prepareSessionManager", prepare);
            var config = new ParkourTagConfig(plugin, "map");
            set(config, "preparePublished", true);
            set(config, "prepareDirty", false);
            List<Slot> slots = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                var slot = allocate(Slot.class);
                set(slot, "plugin", plugin);
                set(slot, "gameConfig", config);
                set(slot, "gameTypeEnum", GameTypeEnum.ParkourTag);
                set(slot, "gameStageEnum", GameStageEnum.WAITING);
                set(slot, "copyIndex", i);
                set(slot, "startPreloadFuture", CompletableFuture.completedFuture(null));
                slots.add(slot);
            }
            var maps = new ParkourTagManager(plugin);
            set(maps, "areas", new ConcurrentHashMap<>(Map.of("map", slots.getFirst())));
            set(maps, "instancesByMap", new ConcurrentHashMap<>(Map.of("map", slots)));
            set(manager, "areaManagers", Map.of(GameTypeEnum.ParkourTag, maps));
            List<TwoVTwoVector> pairs =
                    List.of(
                            new TwoVTwoVector(new TestTeam(1), new TestTeam(2)),
                            new TwoVTwoVector(new TestTeam(3), new TestTeam(4)),
                            new TwoVTwoVector(new TestTeam(5), new TestTeam(6)));
            action.run(new Fixture(manager, slots, pairs));
        } finally {
            serverField.set(null, previous);
        }
    }

    private static final class Slot extends ParkourTagArea {
        private boolean reject, throwing;
        private int attempts, aborts;

        private Slot() {
            super(null, null);
        }

        @Override
        public boolean tryStartGame(ChampionshipTeam right, ChampionshipTeam left) {
            attempts++;
            setGameStageEnum(GameStageEnum.LOADING);
            if (throwing) throw new IllegalStateException("test refusal");
            if (reject) return false;
            rightChampionshipTeam = right;
            leftChampionshipTeam = left;
            return true;
        }

        @Override
        public CompletableFuture<Boolean> abortAndReset() {
            aborts++;
            setGameStageEnum(GameStageEnum.WAITING);
            rightChampionshipTeam = null;
            leftChampionshipTeam = null;
            return CompletableFuture.completedFuture(true);
        }
    }

    private static final class TestTeam extends ChampionshipTeam {
        TestTeam(int id) {
            this(id, Set.of(new UUID(id, 1)));
        }

        TestTeam(int id, Set<UUID> roster) {
            super(id, "team-" + id, "red", "#ffffff", roster, null);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<Object, Object> bindings(Object target, String name) throws Exception {
        return (Map<Object, Object>) field(target.getClass(), name).get(target);
    }

    private static <T> T proxy(Class<T> t, InvocationHandler h) {
        return t.cast(Proxy.newProxyInstance(t.getClassLoader(), new Class<?>[] {t}, h));
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
