package ink.ziip.championshipscore.worker;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.platform.bukkit.scheduler.PlatformScheduler;
import ink.ziip.championshipscore.protocol.*;
import ink.ziip.championshipscore.protocol.transport.DeliveryReceipt;

import io.papermc.paper.threadedregions.scheduler.*;

import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

class WorkerTestReleaseTest {
    @TempDir Path directory;

    @Test
    void disconnectInstructionCannotTargetAnUnknownParticipant() throws Exception {
        try (var f = new Fixture(directory)) {
            var rejected = f.session.abort("test", Set.of(UUID.randomUUID())).toCompletableFuture();
            assertThrows(CompletionException.class, rejected::join);
            assertNull(f.retired);
            assertEquals(1L, f.published.getCount());
        }
    }

    @Test
    void retiredEntityTaskCannotReportCleanWhileItsConnectionStillExists() throws Exception {
        try (var f = new Fixture(directory)) {
            var released = f.release();
            assertFalse(released.isDone());
            f.retired.run();
            assertThrows(CompletionException.class, released::join);
            assertFalse(f.session.testResourcesReleased());
            assertEquals(1L, f.published.getCount());
        }
    }

    @Test
    void departureAndDurablePublicationBothPrecedeTheReleaseAcknowledgement() throws Exception {
        try (var f = new Fixture(directory)) {
            var released = f.release();
            f.present = false;
            f.retired.run();
            assertFalse(released.isDone());
            f.retry();
            assertTrue(f.published.await(3, TimeUnit.SECONDS));
            assertFalse(released.isDone());
            assertFalse(f.session.testResourcesReleased());
            f.receipt.complete(new DeliveryReceipt(f.event.messageId(), "events", "1-0", 1));
            released.get(3, TimeUnit.SECONDS);
            assertEquals(MatchEventType.RELEASED, f.event.type());
            assertTrue(f.session.testResourcesReleased());
            assertSame(released, f.release());
        }
    }

    @Test
    void cancelReturnRequestIsNotProofThatThePlayerLeftTheBackend() throws Exception {
        try (var f = new Fixture(directory)) {
            var left = f.router.requestAndAwaitDeparture(f.id).toCompletableFuture();
            f.router.cancel(f.id);
            f.retry();
            assertFalse(left.isDone());
            f.present = false;
            f.retry();
            assertDoesNotThrow(left::join);
        }
    }

    static final class Fixture implements AutoCloseable {
        final UUID id = UUID.randomUUID();
        final CompletableFuture<DeliveryReceipt> receipt = new CompletableFuture<>();
        final CountDownLatch published = new CountDownLatch(1);
        MatchEvent event;
        Runnable retired;
        boolean present = true;
        final WorkerMatchSession session = allocate(WorkerMatchSession.class);
        final WorkerReturnRouter router = allocate(WorkerReturnRouter.class);
        final DurableEventOutbox outbox;

        Fixture(Path directory) throws Exception {
            var entity =
                    proxy(
                            EntityScheduler.class,
                            (p, m, a) -> {
                                if (m.getName().equals("execute")) {
                                    retired = (Runnable) a[2];
                                    return true;
                                }
                                throw new AssertionError(m.getName());
                            });
            var player =
                    proxy(
                            Player.class,
                            (p, m, a) ->
                                    switch (m.getName()) {
                                        case "getUniqueId" -> id;
                                        case "getScheduler" -> entity;
                                        case "isOnline" -> false;
                                        default -> throw new AssertionError(m.getName());
                                    });
            var global =
                    proxy(
                            GlobalRegionScheduler.class,
                            (p, m, a) -> {
                                if (m.getName().equals("execute")) {
                                    ((Runnable) a[1]).run();
                                    return null;
                                }
                                throw new AssertionError(m.getName());
                            });
            var server =
                    proxy(
                            Server.class,
                            (p, m, a) ->
                                    switch (m.getName()) {
                                        case "getPlayer" -> present ? player : null;
                                        case "getGlobalRegionScheduler" -> global;
                                        default -> throw new AssertionError(m.getName());
                                    });
            var plugin =
                    proxy(
                            Plugin.class,
                            (p, m, a) ->
                                    switch (m.getName()) {
                                        case "getServer" -> server;
                                        case "getLogger" ->
                                                java.util.logging.Logger.getAnonymousLogger();
                                        default -> throw new AssertionError(m.getName());
                                    });
            set(router, "plugin", plugin);
            set(router, "pending", ConcurrentHashMap.<UUID>newKeySet());
            set(router, "departures", new ConcurrentHashMap<UUID, CompletableFuture<Void>>());
            outbox =
                    new DurableEventOutbox(
                            e -> {
                                event = e;
                                published.countDown();
                                return receipt;
                            },
                            directory);
            outbox.initialize();
            var manifest =
                    new MatchManifest(
                            ProtocolVersion.CURRENT,
                            UUID.randomUUID(),
                            1,
                            1,
                            "worker",
                            MatchRunMode.TEST,
                            60,
                            1,
                            "test",
                            new BingoScoringRules(1, List.of(1), 0, 0, 0),
                            new BingoRuntimeRules(
                                    90,
                                    100,
                                    10,
                                    130,
                                    List.of(),
                                    false,
                                    45,
                                    List.of(),
                                    new BingoPresentation(Map.of())),
                            List.of(
                                    new BingoTaskSpec(
                                            0, "stone", "item", Map.of("material", "STONE"))),
                            List.of(),
                            List.of());
            var lifecycle = new MatchStateMachine();
            lifecycle.transitionTo(MatchState.ABORTED);
            set(session, "plugin", plugin);
            set(session, "manifest", manifest);
            set(session, "lifecycle", lifecycle);
            set(
                    session,
                    "participants",
                    Map.of(id, new PlayerSnapshot(id, "testbot", ParticipantRole.SPECTATOR, null)));
            set(session, "nativeScoreboardTeams", new HashMap<>());
            set(session, "scheduler", new PlatformScheduler(plugin));
            set(
                    session,
                    "config",
                    new WorkerConfig(
                            true,
                            "worker",
                            null,
                            null,
                            "BungeeCord",
                            "cc",
                            "bingo",
                            "bingo_nether",
                            "bingo_the_end",
                            true,
                            null,
                            false));
            set(session, "returnRouter", router);
            set(session, "events", outbox);
        }

        CompletableFuture<Void> release() throws Exception {
            var method = WorkerMatchSession.class.getDeclaredMethod("releaseTestResources");
            method.setAccessible(true);
            return ((CompletionStage<Void>) method.invoke(session)).toCompletableFuture();
        }

        void retry() throws Exception {
            var m = WorkerReturnRouter.class.getDeclaredMethod("retryPending");
            m.setAccessible(true);
            m.invoke(router);
        }

        public void close() {
            outbox.close();
        }
    }

    static Field field(Class<?> c, String n) throws Exception {
        var f = c.getDeclaredField(n);
        f.setAccessible(true);
        return f;
    }

    static void set(Object o, String n, Object v) throws Exception {
        field(o.getClass(), n).set(o, v);
    }

    static <T> T allocate(Class<T> c) throws Exception {
        var u = (sun.misc.Unsafe) field(sun.misc.Unsafe.class, "theUnsafe").get(null);
        return c.cast(u.allocateInstance(c));
    }

    static <T> T proxy(Class<T> c, InvocationHandler h) {
        return c.cast(Proxy.newProxyInstance(c.getClassLoader(), new Class<?>[] {c}, h));
    }
}
