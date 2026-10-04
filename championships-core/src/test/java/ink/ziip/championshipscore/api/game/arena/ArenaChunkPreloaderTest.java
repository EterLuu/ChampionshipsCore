package ink.ziip.championshipscore.api.game.arena;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.ChampionshipsCore;

import org.bukkit.*;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

class ArenaChunkPreloaderTest {
    @Test
    void overlappingMatchesKeepTicketUntilLastOwnerReleases() throws Exception {
        withFixture(
                f -> {
                    Set<ArenaChunkPreloader.ChunkTicket> first = new HashSet<>(),
                            second = new HashSet<>();
                    var point = List.of(new Location(f.world, 1, 2, 1));
                    var a = ArenaChunkPreloader.preload(f.plugin, point, 0, first);
                    var b = ArenaChunkPreloader.preload(f.plugin, point, 0, second);
                    f.queued.forEach(Runnable::run);
                    assertTrue(a.isDone());
                    assertTrue(b.isDone());
                    assertEquals(1, f.adds[0]);
                    ArenaChunkPreloader.release(f.plugin, first);
                    assertEquals(0, f.removes[0]);
                    ArenaChunkPreloader.release(f.plugin, second);
                    assertEquals(1, f.removes[0]);
                    ArenaChunkPreloader.release(f.plugin, second);
                    assertEquals(1, f.removes[0]);
                });
    }

    @Test
    void canceledGenerationCannotAcquireTicketFromAlreadyQueuedCallback() throws Exception {
        withFixture(
                f -> {
                    var current = new AtomicBoolean(true);
                    Set<ArenaChunkPreloader.ChunkTicket> tickets = new HashSet<>();
                    var ready =
                            ArenaChunkPreloader.preload(
                                    f.plugin,
                                    List.of(new Location(f.world, 1, 2, 1)),
                                    0,
                                    tickets,
                                    current::get);
                    current.set(false);
                    f.queued.forEach(Runnable::run);
                    assertTrue(ready.isDone());
                    assertTrue(tickets.isEmpty());
                    assertEquals(0, f.adds[0]);
                    assertEquals(0, f.removes[0]);
                });
    }

    @Test
    void ticketFromAnotherPluginSubsystemIsNotReleasedByMatch() throws Exception {
        withFixture(
                f -> {
                    f.ticketAlreadyPresent.set(true);
                    Set<ArenaChunkPreloader.ChunkTicket> tickets = new HashSet<>();
                    ArenaChunkPreloader.preload(
                            f.plugin, List.of(new Location(f.world, 1, 2, 1)), 0, tickets);
                    f.queued.forEach(Runnable::run);
                    ArenaChunkPreloader.release(f.plugin, tickets);
                    assertEquals(1, f.adds[0]);
                    assertEquals(0, f.removes[0]);
                });
    }

    private record Fixture(
            ChampionshipsCore plugin,
            World world,
            List<Runnable> queued,
            int[] adds,
            int[] removes,
            AtomicBoolean ticketAlreadyPresent) {}

    private interface Action {
        void run(Fixture fixture) throws Exception;
    }

    private static void withFixture(Action action) throws Exception {
        var serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        Object previous = serverField.get(null);
        try {
            var unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            var plugin =
                    (ChampionshipsCore)
                            ((sun.misc.Unsafe) unsafeField.get(null))
                                    .allocateInstance(ChampionshipsCore.class);
            var loaded = ChampionshipsCore.class.getDeclaredField("loaded");
            loaded.setAccessible(true);
            loaded.set(plugin, true);
            List<Runnable> queued = new ArrayList<>();
            int[] adds = {0}, removes = {0};
            var present = new AtomicBoolean(false);
            Chunk chunk =
                    proxy(
                            Chunk.class,
                            (p, m, a) -> {
                                if (m.getName().equals("addPluginChunkTicket")) {
                                    adds[0]++;
                                    return !present.get();
                                }
                                throw new AssertionError(m.getName());
                            });
            World world =
                    proxy(
                            World.class,
                            (p, m, a) ->
                                    switch (m.getName()) {
                                        case "getName" -> "preload-test";
                                        case "getChunkAtAsync" ->
                                                CompletableFuture.completedFuture(chunk);
                                        case "removePluginChunkTicket" -> {
                                            removes[0]++;
                                            yield true;
                                        }
                                        default -> throw new AssertionError(m.getName());
                                    });
            BukkitTask task = proxy(BukkitTask.class, (p, m, a) -> null);
            BukkitScheduler scheduler =
                    proxy(
                            BukkitScheduler.class,
                            (p, m, a) -> {
                                if (m.getName().equals("runTask")) {
                                    queued.add((Runnable) a[1]);
                                    return task;
                                }
                                throw new AssertionError(m.getName());
                            });
            Server server =
                    proxy(
                            Server.class,
                            (p, m, a) ->
                                    switch (m.getName()) {
                                        case "getWorld" -> world;
                                        case "getScheduler" -> scheduler;
                                        default -> throw new AssertionError(m.getName());
                                    });
            var pluginServer = org.bukkit.plugin.java.JavaPlugin.class.getDeclaredField("server");
            pluginServer.setAccessible(true);
            pluginServer.set(plugin, server);
            serverField.set(null, server);
            action.run(new Fixture(plugin, world, queued, adds, removes, present));
        } finally {
            serverField.set(null, previous);
        }
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(
                Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler));
    }
}
