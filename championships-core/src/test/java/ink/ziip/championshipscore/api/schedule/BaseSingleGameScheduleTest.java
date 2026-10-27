package ink.ziip.championshipscore.api.schedule;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseListener;
import ink.ziip.championshipscore.api.game.manager.GameManager;
import ink.ziip.championshipscore.api.game.model.*;
import ink.ziip.championshipscore.configuration.config.message.ScheduleMessageConfig;

import org.bukkit.*;
import org.bukkit.scheduler.*;
import org.junit.jupiter.api.Test;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;

class BaseSingleGameScheduleTest {
    @Test
    void stoppedRoundSettlementCannotFinishOrPrepareANewSchedule() throws Exception {
        for (int round : new int[] {1, 3}) {
            try (var fixture = new PreparationFixture()) {
                Owner owner = (Owner) fixture.schedule.scheduleManager;
                owner.deferSettlement = true;
                fixture.schedule.subRound = round;
                fixture.schedule.nextRound();
                Runnable previous = owner.afterSettlement;
                assertNotNull(previous);
                fixture.schedule.endSchedule();
                fixture.schedule.enabled = true;
                fixture.schedule.scheduleGeneration++;
                previous.run();
                assertTrue(fixture.schedule.isEnabled(), "round " + round);
                assertNull(fixture.clock, "Old settlement prepared the new schedule");
                assertTrue(fixture.game.starts.isEmpty());
            }
        }
    }

    @Test
    void readyCourseStillWaitsForTheFullTenSecondClock() throws Exception {
        try (var fixture = new PreparationFixture()) {
            fixture.schedule.nextRound();
            fixture.schedule.prepared.complete(true);
            fixture.tick(10);
            assertTrue(fixture.game.starts.isEmpty());
            fixture.tick(1);
            assertEquals(1, fixture.game.starts.size());
            assertEquals(2, fixture.schedule.getSubRound());
        }
    }

    @Test
    void expiredClockWaitsForCourseReadinessAndStartsOnlyOnce() throws Exception {
        try (var fixture = new PreparationFixture()) {
            fixture.schedule.nextRound();
            fixture.tick(11);
            assertTrue(fixture.game.starts.isEmpty());
            fixture.schedule.prepared.complete(true);
            assertEquals(1, fixture.game.starts.size());
            assertFalse(fixture.schedule.prepared.complete(true));
            assertEquals(1, fixture.game.starts.size());
        }
    }

    @Test
    void stoppedScheduleIgnoresItsOldPreparationAfterANewGenerationBegins() throws Exception {
        try (var fixture = new PreparationFixture()) {
            fixture.schedule.nextRound();
            fixture.tick(11);
            fixture.schedule.endSchedule();
            fixture.schedule.enabled = true;
            fixture.schedule.scheduleGeneration++;
            fixture.schedule.prepared.complete(true);
            assertTrue(fixture.game.starts.isEmpty());
            assertTrue(fixture.schedule.isEnabled());
        }
    }

    @Test
    void failedPreparationEndsTheScheduleAndDoesNotStartAnUnbuiltCourse() throws Exception {
        try (var fixture = new PreparationFixture()) {
            fixture.schedule.nextRound();
            fixture.schedule.prepared.completeExceptionally(
                    new IllegalStateException("build failed"));
            fixture.tick(11);
            assertFalse(fixture.schedule.isEnabled());
            assertTrue(fixture.game.starts.isEmpty());
        }
    }

    @Test
    void synchronousPreparationFailureAlsoReleasesTheSchedule() throws Exception {
        try (var fixture = new PreparationFixture()) {
            fixture.schedule.failSynchronously = true;
            assertDoesNotThrow(fixture.schedule::nextRound);
            assertFalse(fixture.schedule.isEnabled());
            assertTrue(fixture.game.starts.isEmpty());
        }
    }

    @Test
    void anOldExecutionResultCannotStopTheNewScheduleAndCurrentFailureEndsIt() throws Exception {
        Field sf = field(Bukkit.class, "server");
        Object previous = sf.get(null);
        try {
            var plugin = allocate(ChampionshipsCore.class);
            set(plugin, "logger", Logger.getAnonymousLogger());
            BukkitTask task = proxy(BukkitTask.class, (p, m, a) -> null);
            BukkitScheduler scheduler = proxy(BukkitScheduler.class, (p, m, a) -> task);
            Server server =
                    proxy(
                            Server.class,
                            (p, m, a) -> m.getName().equals("getScheduler") ? scheduler : null);
            sf.set(null, server);
            set(plugin, "server", server);
            var game = allocate(AsyncGame.class);
            game.starts = new ArrayList<>();
            set(plugin, "gameManager", game);
            var owner = new Owner(plugin);
            set(plugin, "scheduleManager", owner);
            var schedule = new Single(plugin, new NoEvents(plugin));
            schedule.startGame();
            schedule.startRound();
            var old = game.starts.getFirst();
            schedule.endSchedule();
            schedule.startGame();
            schedule.startRound();
            old.complete(false);
            assertTrue(schedule.isEnabled());
            game.starts
                    .getLast()
                    .completeExceptionally(new IllegalStateException("worker refused"));
            assertFalse(schedule.isEnabled());
            assertEquals(0, game.forcedStops);
        } finally {
            sf.set(null, previous);
        }
    }

    private static final class AsyncGame extends GameManager {
        List<CompletableFuture<Boolean>> starts;
        int forcedStops;

        private AsyncGame() {
            super(null);
        }

        @Override
        public CompletionStage<Boolean> joinSingleTeamAreaForAllTeamsAsync(
                GameTypeEnum g, String map, boolean intro, GameRunMode mode) {
            var result = new CompletableFuture<Boolean>();
            starts.add(result);
            return result;
        }

        @Override
        public void releaseEventSpectatorsForGame(GameTypeEnum g) {}

        @Override
        public void releaseRoundTransitionHolds(GameTypeEnum g) {}

        @Override
        public void forceEndAreas(GameTypeEnum g) {
            forcedStops++;
        }
    }

    private static final class Owner extends ScheduleManager {
        boolean deferSettlement;
        Runnable afterSettlement;

        Owner(ChampionshipsCore p) {
            super(p);
        }

        @Override
        public void addRound(GameTypeEnum g) {}

        @Override
        public void clearRoundPreparationCountdown() {}

        @Override
        public void showRoundPreparationCountdown(GameTypeEnum g, int round, int seconds) {}

        @Override
        public void settleEventRound(GameTypeEnum g, boolean next, Runnable after, long ticks) {
            if (deferSettlement) afterSettlement = after;
            else after.run();
        }
    }

    private static final class Preparing extends BaseSingleGameSchedule {
        final CompletableFuture<Boolean> prepared = new CompletableFuture<>();
        boolean failSynchronously;

        Preparing(ChampionshipsCore plugin) {
            super(plugin, new NoEvents(plugin), GameTypeEnum.RiptideRush);
            enabled = true;
            subRound = 1;
        }

        @Override
        protected CompletionStage<Boolean> prepareNextRound() {
            if (failSynchronously) throw new IllegalStateException("preparation refused");
            return prepared;
        }

        @Override
        protected void playNextRoundSound() {}

        @Override
        public String getArea() {
            return "Chosen";
        }

        @Override
        public int getTotalRounds() {
            return 3;
        }
    }

    private static final class PreparationFixture implements AutoCloseable {
        final Field serverField = field(Bukkit.class, "server");
        final Object previousServer = serverField.get(null);
        final List<String> previousMessage = ScheduleMessageConfig.NEXT_ROUND_SOON;
        final AsyncGame game;
        final Preparing schedule;
        Runnable clock;

        PreparationFixture() throws Exception {
            var plugin = allocate(ChampionshipsCore.class);
            set(plugin, "logger", Logger.getAnonymousLogger());
            BukkitTask task = proxy(BukkitTask.class, (p, m, a) -> null);
            BukkitScheduler scheduler =
                    proxy(
                            BukkitScheduler.class,
                            (p, m, a) -> {
                                if (m.getName().equals("runTaskTimer")) clock = (Runnable) a[1];
                                return task;
                            });
            Server server =
                    proxy(
                            Server.class,
                            (p, m, a) ->
                                    switch (m.getName()) {
                                        case "getScheduler" -> scheduler;
                                        case "getOnlinePlayers" -> List.of();
                                        default -> null;
                                    });
            serverField.set(null, server);
            set(plugin, "server", server);
            game = allocate(AsyncGame.class);
            game.starts = new ArrayList<>();
            set(plugin, "gameManager", game);
            set(plugin, "scheduleManager", new Owner(plugin));
            ScheduleMessageConfig.NEXT_ROUND_SOON = List.of("Next round");
            schedule = new Preparing(plugin);
        }

        void tick(int seconds) {
            for (int i = 0; i < seconds; i++) clock.run();
        }

        @Override
        public void close() throws Exception {
            ScheduleMessageConfig.NEXT_ROUND_SOON = previousMessage;
            serverField.set(null, previousServer);
        }
    }

    private static final class NoEvents extends BaseListener {
        NoEvents(ChampionshipsCore p) {
            super(p);
        }

        @Override
        public void register() {}

        @Override
        public void unRegister() {}
    }

    private static final class Single extends BaseSingleGameSchedule {
        Single(ChampionshipsCore p, BaseListener h) {
            super(p, h, GameTypeEnum.Bingo);
        }

        @Override
        public String getArea() {
            return "Chosen";
        }

        @Override
        public int getTotalRounds() {
            return 1;
        }
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
