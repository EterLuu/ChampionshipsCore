package ink.ziip.championshipscore.api.daily;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.api.daily.entry.DailyStatEntry;
import ink.ziip.championshipscore.api.game.model.GameTypeEnum;
import ink.ziip.championshipscore.database.daily.DailyStatsDao;
import ink.ziip.championshipscore.util.concurrent.LifecycleTasks;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

class DailyStatsLifecycleTest {
    private static final UUID PLAYER = UUID.randomUUID();

    @Test
    void failedReloadPreservesPreviouslyPublishedCache() throws Exception {
        AtomicBoolean fail = new AtomicBoolean();
        DailyStatsManager manager =
                manager(
                        () -> {
                            if (fail.get()) throw new IllegalStateException("database unavailable");
                            return List.of(entry(3));
                        });
        try {
            manager.reloadFromDatabase().toCompletableFuture().get(2, TimeUnit.SECONDS);
            assertEquals(3, manager.stat(PLAYER, GameTypeEnum.Bingo).gamesPlayed());
            fail.set(true);
            assertThrows(
                    ExecutionException.class,
                    () ->
                            manager.reloadFromDatabase()
                                    .toCompletableFuture()
                                    .get(2, TimeUnit.SECONDS));
            assertEquals(3, manager.stat(PLAYER, GameTypeEnum.Bingo).gamesPlayed());
        } finally {
            manager.unload();
        }
    }

    @Test
    void stoppedQueryCannotRepopulateCachesOrCompleteQueuedReloadSuccessfully() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        var queryThread = new java.util.concurrent.atomic.AtomicReference<Thread>();
        DailyStatsManager manager =
                manager(
                        () -> {
                            queryThread.set(Thread.currentThread());
                            entered.countDown();
                            boolean done = false;
                            while (!done) {
                                try {
                                    release.await();
                                    done = true;
                                } catch (InterruptedException ignored) {
                                    /* JDBC may ignore interruption. */
                                }
                            }
                            return List.of(entry(9));
                        });
        try {
            var old = manager.reloadFromDatabase().toCompletableFuture();
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            var waiting = manager.reloadFromDatabase().toCompletableFuture();
            manager.unload();
            assertTrue(old.isCancelled());
            assertTrue(waiting.isCancelled());
            release.countDown();
            queryThread.get().join(2000);
            assertFalse(queryThread.get().isAlive());
            assertEquals(0, manager.stat(PLAYER, GameTypeEnum.Bingo).gamesPlayed());
            assertTrue(
                    manager.reloadFromDatabase().toCompletableFuture().isCompletedExceptionally());
        } finally {
            release.countDown();
            manager.unload();
        }
    }

    private static DailyStatsManager manager(Supplier<List<DailyStatEntry>> stats)
            throws Exception {
        DailyStatsManager manager = new DailyStatsManager(null);
        var field = DailyStatsManager.class.getDeclaredField("statsDao");
        field.setAccessible(true);
        field.set(
                manager,
                Proxy.newProxyInstance(
                        DailyStatsDao.class.getClassLoader(),
                        new Class<?>[] {DailyStatsDao.class},
                        (proxy, method, args) ->
                                method.getName().equals("getPlayerStats")
                                        ? stats.get()
                                        : List.of()));
        field = DailyStatsManager.class.getDeclaredField("databaseTasks");
        field.setAccessible(true);
        ((LifecycleTasks) field.get(manager)).start();
        return manager;
    }

    private static DailyStatEntry entry(long games) {
        return new DailyStatEntry(PLAYER, "Player", GameTypeEnum.Bingo, games, 0, 0, 0, 0, 1);
    }
}
