package ink.ziip.championshipscore.platform.bukkit.player;

import static org.junit.jupiter.api.Assertions.*;

import io.papermc.paper.threadedregions.scheduler.EntityScheduler;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

class PlayerTargetTeleportsTest {
    @Test
    void capturesAndRevalidatesOnTargetThreadThenPreheatsBeforeViewerTeleportAndFeedback() {
        var f = new Fixture();
        var result = f.request();
        assertEquals(0, f.teleports);
        f.run(f.target);
        assertEquals(1, f.chunks.size());
        assertFalse(result.isDone());
        assertEquals(0, f.tasks.size());
        f.chunks.getFirst().complete(null);
        assertEquals(f.target, f.tasks.getFirst().owner());
        f.run(f.target);
        assertEquals(0, f.teleports);
        f.run(f.viewer);
        assertEquals(1, f.teleports);
        assertEquals(0, f.feedback);
        f.nativeTeleport.complete(true);
        assertFalse(result.isDone());
        f.run(f.viewer);
        assertTrue(result.join());
        assertEquals(1, f.feedback);
    }

    @Test
    void newestSelectionSupersedesOldChunkCallbackWithoutCancellingReplacement() {
        var f = new Fixture();
        var old = f.request();
        f.run(f.target);
        var current = f.request();
        assertFalse(old.join());
        f.chunks.getFirst().complete(null);
        f.run(f.target);
        f.chunks.getLast().complete(null);
        f.run(f.target);
        f.run(f.viewer);
        f.nativeTeleport.complete(true);
        f.run(f.viewer);
        assertTrue(current.join());
        assertEquals(1, f.teleports);
        assertEquals(1, f.feedback);
    }

    @Test
    void sourceRoleSessionTeamConnectionAndLifecycleAreCheckedAgainBeforeApply() {
        for (String reason :
                List.of(
                        "role",
                        "session",
                        "team",
                        "reconnect",
                        "disconnect",
                        "disable",
                        "clear",
                        "close")) {
            var f = new Fixture();
            var result = f.ready();
            switch (reason) {
                case "role", "session", "team" -> f.viewerAllowed = false;
                case "reconnect" -> f.online.put(f.viewer, f.newPlayer(f.viewer));
                case "disconnect" -> f.online.remove(f.viewer);
                case "disable" -> f.enabled = false;
                case "clear" -> f.queue.clear();
                case "close" -> f.queue.close();
            }
            f.run(f.viewer);
            assertFalse(result.join(), reason);
            assertEquals(0, f.teleports, reason);
        }
    }

    @Test
    void targetIsCheckedAfterPreheatAndAgainByOwnerSnapshotBeforeViewerApply() {
        for (String phase : List.of("capture", "preheated", "viewer"))
            for (String reason : List.of("role", "connection", "snapshot")) {
                var f = new Fixture();
                var result = f.request();
                if (!phase.equals("capture")) {
                    f.run(f.target);
                    f.chunks.getFirst().complete(null);
                }
                if (phase.equals("viewer")) f.run(f.target);
                if (reason.equals("connection")) f.online.remove(f.target);
                else if (reason.equals("snapshot") || phase.equals("viewer"))
                    f.targetCurrent = false;
                else f.targetAllowed = false;
                f.run(phase.equals("viewer") ? f.viewer : f.target);
                assertFalse(result.join(), phase + reason);
                assertEquals(0, f.teleports);
            }
    }

    @Test
    void outgoingAndIncomingCancellationAndOldOwnerCleanupCannotAffectNewOwner() {
        for (boolean target : List.of(false, true)) {
            var f = new Fixture();
            var old = f.request();
            f.run(f.target);
            f.queue.cancelPlayer(target ? f.target : f.viewer);
            assertFalse(old.join());
            var replacement = new PlayerTargetTeleports(f.plugin);
            f.queue.close();
            f.queue = replacement;
            var current = f.request();
            f.chunks.getFirst().complete(null);
            f.run(f.target);
            f.chunks.getLast().complete(null);
            f.run(f.target);
            f.run(f.viewer);
            f.nativeTeleport.complete(true);
            f.run(f.viewer);
            assertTrue(current.join());
            assertEquals(1, f.teleports);
        }
    }

    @Test
    void rejectedRetiredAndThrowingSchedulersAlwaysCompleteAndReleaseTheRequest() {
        for (String phase : List.of("capture", "recheck", "apply", "feedback"))
            for (String failure : List.of("reject", "retire", "throw")) {
                var f = new Fixture();
                if (phase.equals("capture")) f.fault = failure;
                var result = f.request();
                if (!phase.equals("capture")) {
                    f.run(f.target);
                    if (phase.equals("recheck")) f.fault = failure;
                    f.chunks.getFirst().complete(null);
                    if (!phase.equals("recheck")) {
                        if (phase.equals("apply")) f.fault = failure;
                        f.run(f.target);
                    }
                    if (phase.equals("feedback")) {
                        f.run(f.viewer);
                        f.fault = failure;
                        f.nativeTeleport.complete(true);
                    }
                }
                if (failure.equals("retire")) f.tasks.removeLast().retired().run();
                assertTrue(result.isDone(), phase + failure);
                assertTrue(result.isCompletedExceptionally() || !result.join());
                assertEquals(0, f.feedback);
            }
    }

    @Test
    void failedNativeTeleportAndLateFeedbackCannotSendAFalseSuccessMessage() {
        for (String reason : List.of("false", "exception", "role", "disconnect", "new-selection")) {
            var f = new Fixture();
            var result = f.ready();
            f.run(f.viewer);
            if (reason.equals("exception"))
                f.nativeTeleport.completeExceptionally(new IllegalStateException());
            else if (reason.equals("false")) f.nativeTeleport.complete(false);
            else {
                f.nativeTeleport.complete(true);
                if (reason.equals("role")) f.viewerAllowed = false;
                if (reason.equals("disconnect")) f.online.remove(f.viewer);
                if (reason.equals("new-selection")) f.request();
                f.run(f.viewer);
            }
            assertTrue(result.isDone());
            assertEquals(0, f.feedback, reason);
        }
    }

    private record Task(UUID owner, Runnable action, Runnable retired) {}

    private static final class Fixture {
        final UUID viewer = UUID.randomUUID(), target = UUID.randomUUID();
        final Map<UUID, Player> online = new HashMap<>();
        final List<Task> tasks = new ArrayList<>();
        final List<CompletableFuture<Chunk>> chunks = new ArrayList<>();
        final CompletableFuture<Boolean> nativeTeleport = new CompletableFuture<>();
        final World world;
        final Plugin plugin;
        PlayerTargetTeleports queue;
        UUID thread;
        String fault;
        boolean enabled = true, viewerAllowed = true, targetAllowed = true, targetCurrent = true;
        int teleports, feedback;

        Fixture() {
            world =
                    proxy(
                            World.class,
                            (p, m, a) -> {
                                if (m.getName().equals("getChunkAtAsync")) {
                                    assertEquals(target, thread);
                                    var future = new CompletableFuture<Chunk>();
                                    chunks.add(future);
                                    return future;
                                }
                                if (m.getName().equals("getName")) return "world";
                                throw new AssertionError(m.getName());
                            });
            online.put(viewer, newPlayer(viewer));
            online.put(target, newPlayer(target));
            Server server =
                    proxy(
                            Server.class,
                            (p, m, a) -> {
                                assertEquals("getPlayer", m.getName());
                                return online.get(a[0]);
                            });
            plugin =
                    proxy(
                            Plugin.class,
                            (p, m, a) ->
                                    switch (m.getName()) {
                                        case "getServer" -> server;
                                        case "isEnabled" -> enabled;
                                        default -> throw new AssertionError(m.getName());
                                    });
            queue = new PlayerTargetTeleports(plugin);
        }

        Player newPlayer(UUID id) {
            var scheduler =
                    proxy(
                            EntityScheduler.class,
                            (p, m, a) -> {
                                assertEquals("execute", m.getName());
                                var task = new Task(id, (Runnable) a[1], (Runnable) a[2]);
                                String failure = fault;
                                fault = null;
                                if ("reject".equals(failure)) return false;
                                if ("throw".equals(failure)) throw new IllegalStateException();
                                tasks.add(task);
                                return true;
                            });
            return proxy(
                    Player.class,
                    (p, m, a) ->
                            switch (m.getName()) {
                                case "getUniqueId" -> id;
                                case "isOnline" -> true;
                                case "getScheduler" -> scheduler;
                                case "getLocation" -> {
                                    assertEquals(id, thread);
                                    yield new Location(world, 1024, 80, 2048);
                                }
                                case "teleportAsync" -> {
                                    assertEquals(viewer, thread);
                                    assertTrue(chunks.getLast().isDone());
                                    teleports++;
                                    yield nativeTeleport;
                                }
                                default -> throw new AssertionError(m.getName());
                            });
        }

        CompletableFuture<Boolean> request() {
            thread = viewer;
            try {
                return queue.request(
                        online.get(viewer),
                        target,
                        () -> {
                            assertEquals(viewer, thread);
                            return viewerAllowed;
                        },
                        p -> {
                            assertEquals(target, thread);
                            return targetAllowed;
                        },
                        () -> targetCurrent,
                        () -> {
                            assertEquals(viewer, thread);
                            feedback++;
                        });
            } finally {
                thread = null;
            }
        }

        CompletableFuture<Boolean> ready() {
            var result = request();
            run(target);
            chunks.getLast().complete(null);
            run(target);
            return result;
        }

        void run(UUID owner) {
            var task =
                    tasks.stream().filter(t -> t.owner().equals(owner)).findFirst().orElseThrow();
            tasks.remove(task);
            thread = owner;
            try {
                task.action().run();
            } finally {
                thread = null;
            }
        }
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(
                Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler));
    }
}
