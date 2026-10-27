package ink.ziip.championshipscore.platform.bukkit.scheduler;

import static org.junit.jupiter.api.Assertions.*;

import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import java.lang.reflect.*;
import java.util.*;
import java.util.function.Consumer;

class PlayerProgressChecksTest {
    @Test
    void duplicateEventsKeepFirstSchedulingOrderAndNextTickCanQueueAgain() {
        var f = new Fixture();
        f.request();
        f.request();
        f.request();
        assertEquals(1, f.tasks.size());
        f.tasks.getFirst().run();
        f.tasks.getFirst().run();
        assertEquals(1, f.checks);
        f.request();
        assertEquals(2, f.tasks.size());
        f.tasks.getLast().run();
        assertEquals(2, f.checks);
    }

    @Test
    void oldConnectionAndSessionCannotConsumeTheirReplacements() {
        for (boolean reconnect : List.of(false, true)) {
            var f = new Fixture();
            f.request();
            Player oldPlayer = f.player;
            if (reconnect) {
                f.player = f.newPlayer();
                f.online = f.player;
            } else f.session = new Object();
            f.request();
            if (reconnect) f.queue.cancel(oldPlayer);
            f.tasks.getFirst().run();
            f.retired.getFirst().run();
            f.request();
            assertEquals(2, f.tasks.size());
            f.tasks.getLast().run();
            assertEquals(1, f.checks);
        }
    }

    @Test
    void roleConnectionDisableAndCancelledRunAreRecheckedAtExecution() {
        for (String reason : List.of("role", "connection", "disable", "clear", "close")) {
            var f = new Fixture();
            f.request();
            switch (reason) {
                case "role" -> f.current = false;
                case "connection" -> f.online = null;
                case "disable" -> f.enabled = false;
                case "clear" -> f.queue.clear();
                case "close" -> f.queue.close();
            }
            f.tasks.getFirst().run();
            assertEquals(0, f.checks, reason);
        }
    }

    @Test
    void retiredRejectedAndFailedSchedulingReleaseTheirPendingSlot() {
        for (String reason : List.of("retired", "rejected", "exception")) {
            var f = new Fixture();
            if (reason.equals("rejected")) f.reject = true;
            if (reason.equals("exception")) f.throwOnSchedule = true;
            if (f.throwOnSchedule) assertThrows(IllegalStateException.class, f::request);
            else f.request();
            if (reason.equals("retired")) f.retired.getFirst().run();
            f.reject = false;
            f.throwOnSchedule = false;
            f.request();
            assertEquals(2, f.tasks.size(), reason);
            f.tasks.getLast().run();
            assertEquals(1, f.checks, reason);
        }
    }

    @Test
    void checkFailureAndClearDoNotSuppressTheNextRequest() {
        var f = new Fixture();
        f.queue.request(
                f.player,
                f.session,
                () -> true,
                () -> {
                    throw new IllegalStateException();
                });
        assertThrows(IllegalStateException.class, () -> f.tasks.getFirst().run());
        f.request();
        f.queue.clear();
        f.request();
        f.tasks.get(1).run();
        f.tasks.get(2).run();
        assertEquals(1, f.checks);
        f.queue.close();
        f.request();
        assertEquals(3, f.tasks.size());
    }

    private static final class Fixture {
        final UUID id = UUID.randomUUID();
        final List<Runnable> tasks = new ArrayList<>(), retired = new ArrayList<>();
        final PlayerProgressChecks queue;
        final EntityScheduler scheduler;
        Player player, online;
        Object session = new Object();
        boolean current = true, enabled = true, reject, throwOnSchedule;
        int checks;

        Fixture() {
            scheduler =
                    proxy(
                            EntityScheduler.class,
                            (p, m, a) -> {
                                assertEquals("runDelayed", m.getName());
                                @SuppressWarnings("unchecked")
                                var callback = (Consumer<ScheduledTask>) a[1];
                                tasks.add(() -> callback.accept(null));
                                retired.add((Runnable) a[2]);
                                assertEquals(1L, a[3]);
                                if (throwOnSchedule) throw new IllegalStateException();
                                return reject
                                        ? null
                                        : proxy(ScheduledTask.class, (t, method, args) -> null);
                            });
            player = newPlayer();
            online = player;
            Server server =
                    proxy(
                            Server.class,
                            (p, m, a) -> {
                                assertEquals("getPlayer", m.getName());
                                return online;
                            });
            Plugin plugin =
                    proxy(
                            Plugin.class,
                            (p, m, a) ->
                                    switch (m.getName()) {
                                        case "isEnabled" -> enabled;
                                        case "getServer" -> server;
                                        default -> throw new AssertionError(m.getName());
                                    });
            queue = new PlayerProgressChecks(plugin);
        }

        Player newPlayer() {
            return proxy(
                    Player.class,
                    (p, m, a) ->
                            switch (m.getName()) {
                                case "getUniqueId" -> id;
                                case "getScheduler" -> scheduler;
                                case "isOnline" -> true;
                                default -> throw new AssertionError(m.getName());
                            });
        }

        void request() {
            queue.request(player, session, () -> current, () -> checks++);
        }
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(
                Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler));
    }
}
