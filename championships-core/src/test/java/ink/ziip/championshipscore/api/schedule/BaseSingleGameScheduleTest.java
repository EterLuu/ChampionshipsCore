package ink.ziip.championshipscore.api.schedule;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseListener;
import ink.ziip.championshipscore.api.game.manager.GameManager;
import ink.ziip.championshipscore.api.game.model.*;

import org.bukkit.*;
import org.bukkit.scheduler.*;
import org.junit.jupiter.api.Test;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;

class BaseSingleGameScheduleTest {
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
        Owner(ChampionshipsCore p) {
            super(p);
        }

        @Override
        public void addRound(GameTypeEnum g) {}

        @Override
        public void clearRoundPreparationCountdown() {}
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
