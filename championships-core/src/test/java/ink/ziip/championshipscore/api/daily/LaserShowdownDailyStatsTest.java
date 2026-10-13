package ink.ziip.championshipscore.api.daily;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.daily.entry.DailyMapStatEntry;
import ink.ziip.championshipscore.api.daily.entry.DailyMatchResultEntry;
import ink.ziip.championshipscore.api.game.laser.model.LaserGun;
import ink.ziip.championshipscore.api.game.lasershowdown.runtime.LaserShowdownTeamArea;
import ink.ziip.championshipscore.api.game.model.GameTypeEnum;
import ink.ziip.championshipscore.api.player.PlayerManager;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.ConfigurationStateExtension;
import ink.ziip.championshipscore.database.daily.DailyStatsDao;
import ink.ziip.championshipscore.util.concurrent.LifecycleTasks;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@ExtendWith(ConfigurationStateExtension.class)
class LaserShowdownDailyStatsTest {
    @Test
    void unequalTeamsWinByKillsAndPersistIdempotentResultsAndRateBoards() throws Exception {
        UUID solo = UUID.randomUUID();
        Set<UUID> opponents = Set.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        var winner = new TestTeam(1, Set.of(solo));
        var loser = new TestTeam(2, opponents);
        var area = allocate(LaserShowdownTeamArea.class);
        set(area, "teamShootTimes", new HashMap<>(Map.of(winner, 10, loser, 9)));
        var plugin = allocate(ChampionshipsCore.class);
        set(plugin, "playerManager", allocate(TestPlayerNames.class));
        var manager = new DailyStatsManager(plugin);
        var writes = new AtomicInteger();
        var saved = new AtomicReference<List<DailyMatchResultEntry>>();
        var mapRows = new CopyOnWriteArrayList<DailyMapStatEntry>();
        set(area, "playerIndividualKills", new HashMap<>(Map.of(solo, 10)));
        set(area, "playerEnemyHits", new HashMap<>(Map.of(solo, 20L)));
        set(area, "playerHeadHits", new HashMap<>(Map.of(solo, 12L)));
        set(
                area,
                "playerWeaponLives",
                new HashMap<>(
                        Map.of(
                                solo,
                                new EnumMap<>(Map.of(LaserGun.MACHINE, 2L, LaserGun.RIFLE, 1L)))));
        set(
                manager,
                "statsDao",
                Proxy.newProxyInstance(
                        DailyStatsDao.class.getClassLoader(),
                        new Class<?>[] {DailyStatsDao.class},
                        (p, method, args) -> {
                            if (method.getName().equals("saveMatch")) {
                                writes.incrementAndGet();
                                saved.set(List.copyOf((List<DailyMatchResultEntry>) args[0]));
                            }
                            if (method.getName().equals("saveMapStats"))
                                mapRows.addAll((List<DailyMapStatEntry>) args[0]);
                            if (method.getName().startsWith("save")) return true;
                            if (method.getName().equals("getPlayerMapStats")) {
                                Map<UUID, DailyMapStatEntry> merged = new HashMap<>();
                                for (DailyMapStatEntry row : mapRows)
                                    merged.merge(
                                            row.uuid(),
                                            row,
                                            (a, b) ->
                                                    new DailyMapStatEntry(
                                                            a.uuid(),
                                                            a.username(),
                                                            a.game(),
                                                            a.map(),
                                                            a.gamesPlayed() + b.gamesPlayed(),
                                                            a.wins() + b.wins(),
                                                            0,
                                                            0,
                                                            0,
                                                            0,
                                                            0,
                                                            0,
                                                            0,
                                                            0,
                                                            0,
                                                            1,
                                                            a.laser().merge(b.laser())));
                                return List.copyOf(merged.values());
                            }
                            return List.of();
                        }));
        ((LifecycleTasks) field(DailyStatsManager.class, "databaseTasks").get(manager)).start();
        Set<UUID> players = new HashSet<>(opponents);
        players.add(solo);
        Map<UUID, Double> points = new HashMap<>();
        points.put(solo, 100D);
        opponents.forEach(id -> points.put(id, 150D));
        try {
            for (int match = 0; match < 5; match++) {
                var session =
                        new DailySession(
                                UUID.randomUUID(),
                                GameTypeEnum.LaserShowdown,
                                "arena",
                                area,
                                List.of(winner, loser),
                                players,
                                1L);
                manager.recordMatch(session, points);
                manager.recordMatch(session, points);
                assertEquals(match + 1, manager.stat(solo, GameTypeEnum.LaserShowdown).wins());
                for (UUID opponent : opponents)
                    assertEquals(0, manager.stat(opponent, GameTypeEnum.LaserShowdown).wins());
            }
            CountDownLatch drained = new CountDownLatch(1);
            manager.runAfterPendingWrites(drained::countDown);
            assertTrue(drained.await(3, TimeUnit.SECONDS));
            assertEquals(5, writes.get());
            assertEquals(4, saved.get().size());
            assertTrue(
                    saved.get().stream()
                            .filter(row -> row.uuid().equals(solo))
                            .allMatch(row -> row.won() && row.points() == 100D));
            assertTrue(
                    saved.get().stream()
                            .filter(row -> opponents.contains(row.uuid()))
                            .allMatch(row -> !row.won() && row.points() == 150D));
            assertEquals(5D, manager.metricValue(solo, "arena", DailyMetric.LASERSHOWDOWN_WINS));
            assertEquals(
                    10D, manager.metricValue(solo, null, DailyMetric.LASERSHOWDOWN_AVERAGE_KILLS));
            assertEquals(
                    60D,
                    manager.metricValue(solo, "arena", DailyMetric.LASERSHOWDOWN_HEADSHOT_RATE));
            assertEquals(
                    (double) LaserGun.MACHINE.ordinal(),
                    manager.metricValue(solo, null, DailyMetric.LASERSHOWDOWN_FAVORITE_GUN));
            assertTrue(
                    manager.leaderboard(DailyMetric.LASERSHOWDOWN_FAVORITE_GUN.boardId(null))
                            .isEmpty());
            assertTrue(
                    Double.isNaN(
                            manager.metricValue(
                                    opponents.iterator().next(),
                                    "arena",
                                    DailyMetric.LASERSHOWDOWN_HEADSHOT_RATE)));
            assertEquals(
                    solo,
                    manager.leaderboard(DailyMetric.LASERSHOWDOWN_WINS.boardId(null))
                            .getFirst()
                            .player());
            assertEquals(
                    50L,
                    manager.mapStat(solo, GameTypeEnum.LaserShowdown, "arena").laser().kills());
            manager.reloadFromDatabase().toCompletableFuture().get(3, TimeUnit.SECONDS);
            assertEquals(
                    10D, manager.metricValue(solo, null, DailyMetric.LASERSHOWDOWN_AVERAGE_KILLS));
            assertEquals(
                    60D, manager.metricValue(solo, null, DailyMetric.LASERSHOWDOWN_HEADSHOT_RATE));
            assertEquals(
                    (double) LaserGun.MACHINE.ordinal(),
                    manager.metricValue(solo, null, DailyMetric.LASERSHOWDOWN_FAVORITE_GUN));
            manager.renameMap(GameTypeEnum.LaserShowdown, "arena", "renamed");
            assertEquals(
                    50L,
                    manager.mapStat(solo, GameTypeEnum.LaserShowdown, "renamed").laser().kills());
            set(area, "teamShootTimes", Map.of(winner, 10, loser, 10));
            manager.recordMatch(
                    new DailySession(
                            UUID.randomUUID(),
                            GameTypeEnum.LaserShowdown,
                            "arena",
                            area,
                            List.of(winner, loser),
                            players,
                            1L),
                    points);
            assertEquals(1, manager.stat(solo, GameTypeEnum.LaserShowdown).wins());
            for (UUID opponent : opponents)
                assertEquals(1, manager.stat(opponent, GameTypeEnum.LaserShowdown).wins());
        } finally {
            manager.unload();
        }
    }

    private static final class TestTeam extends ChampionshipTeam {
        TestTeam(int id, Set<UUID> members) {
            super(id, "team" + id, "RED", "#ff5555", members, null);
        }
    }

    private static final class TestPlayerNames extends PlayerManager {
        private TestPlayerNames() {
            super(null);
        }

        @Override
        public String getPlayerName(UUID player) {
            return "Player";
        }
    }

    private static Field field(Class<?> type, String name) throws Exception {
        for (; type != null; type = type.getSuperclass()) {
            try {
                Field f = type.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static void set(Object object, String name, Object value) throws Exception {
        field(object.getClass(), name).set(object, value);
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        return type.cast(
                ((sun.misc.Unsafe) field(sun.misc.Unsafe.class, "theUnsafe").get(null))
                        .allocateInstance(type));
    }
}
