package ink.ziip.championshipscore.worker;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.platform.bukkit.scheduler.PlayerProgressChecks;
import ink.ziip.championshipscore.protocol.*;

import io.papermc.paper.threadedregions.scheduler.*;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import java.lang.reflect.*;
import java.util.*;
import java.util.function.Consumer;

class WorkerProgressCheckTest {
    @Test
    void oldSessionCannotObserveTheNewMatchOrRemoveItsPendingRequest() throws Exception {
        var f = new Fixture();
        f.registry.requestObserve(f.player);
        f.registry.requestObserve(f.player);
        assertEquals(1, f.tasks.size());
        var next = f.session();
        set(f.registry, "active", next);
        f.registry.requestObserve(f.player);
        f.tasks.getFirst().run();
        assertEquals(0, f.observations);
        f.registry.requestObserve(f.player);
        assertEquals(2, f.tasks.size());
        f.tasks.getLast().run();
        assertEquals(1, f.observations);
    }

    @Test
    void roleAndTerminalStateChangesPreventDeferredObservation() throws Exception {
        for (boolean terminal : List.of(false, true)) {
            var f = new Fixture();
            f.registry.requestObserve(f.player);
            if (terminal)
                ((MatchStateMachine) field(f.active.getClass(), "lifecycle").get(f.active))
                        .transitionTo(MatchState.ABORTED);
            else
                set(
                        f.active,
                        "participants",
                        Map.of(
                                f.id,
                                new PlayerSnapshot(
                                        f.id, "Tester", ParticipantRole.SPECTATOR, null)));
            f.tasks.getFirst().run();
            assertEquals(0, f.observations);
        }
    }

    private static final class Fixture {
        final WorkerMatchRegistry registry = allocate(WorkerMatchRegistry.class);
        final UUID id = UUID.randomUUID();
        final List<Runnable> tasks = new ArrayList<>();
        final Player player;
        final WorkerMatchSession active;
        int observations;

        Fixture() throws Exception {
            EntityScheduler scheduler =
                    proxy(
                            EntityScheduler.class,
                            (p, m, a) -> {
                                @SuppressWarnings("unchecked")
                                var callback = (Consumer<ScheduledTask>) a[1];
                                tasks.add(() -> callback.accept(null));
                                return proxy(ScheduledTask.class, (t, method, args) -> null);
                            });
            player =
                    proxy(
                            Player.class,
                            (p, m, a) ->
                                    switch (m.getName()) {
                                        case "getUniqueId" -> id;
                                        case "getScheduler" -> scheduler;
                                        case "isOnline" -> true;
                                        case "getGameMode" -> {
                                            observations++;
                                            yield GameMode.SURVIVAL;
                                        }
                                        default -> throw new AssertionError(m.getName());
                                    });
            Server server = proxy(Server.class, (p, m, a) -> player);
            Plugin plugin =
                    proxy(
                            Plugin.class,
                            (p, m, a) ->
                                    switch (m.getName()) {
                                        case "isEnabled" -> true;
                                        case "getServer" -> server;
                                        default -> throw new AssertionError(m.getName());
                                    });
            set(registry, "progressChecks", new PlayerProgressChecks(plugin));
            active = session();
            set(registry, "active", active);
        }

        WorkerMatchSession session() throws Exception {
            var session = allocate(WorkerMatchSession.class);
            var lifecycle = new MatchStateMachine();
            for (var state :
                    List.of(
                            MatchState.PREPARING,
                            MatchState.READY,
                            MatchState.ROUTING,
                            MatchState.COUNTDOWN,
                            MatchState.RUNNING)) lifecycle.transitionTo(state);
            set(session, "lifecycle", lifecycle);
            set(
                    session,
                    "participants",
                    Map.of(id, new PlayerSnapshot(id, "Tester", ParticipantRole.PLAYER, 1)));
            set(session, "permanentEffects", List.of());
            set(session, "differentialObjectives", Map.of());
            set(session, "objectives", new WorkerObjectives(List.of()));
            return session;
        }
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(
                Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler));
    }

    private static Field field(Class<?> type, String name) throws Exception {
        var f = type.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }

    private static void set(Object target, String name, Object value) throws Exception {
        field(target.getClass(), name).set(target, value);
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        return type.cast(
                ((sun.misc.Unsafe) field(sun.misc.Unsafe.class, "theUnsafe").get(null))
                        .allocateInstance(type));
    }
}
