package ink.ziip.championshipscore.worker;

import static ink.ziip.championshipscore.worker.support.TestObjects.allocate;
import static ink.ziip.championshipscore.worker.support.TestObjects.field;
import static ink.ziip.championshipscore.worker.support.TestObjects.proxy;
import static ink.ziip.championshipscore.worker.support.TestObjects.set;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.platform.bukkit.scheduler.PlayerProgressChecks;
import ink.ziip.championshipscore.protocol.*;
import ink.ziip.championshipscore.protocol.transport.DeliveryReceipt;

import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

import org.bukkit.GameMode;
import org.bukkit.Server;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

class WorkerMatchRegistryTest {
    @Nested
    class TestModeAdmission {
        @Test
        void disabledTestRejectsWithoutTakingOverFormalSessionOrPresentation(
                @TempDir Path directory) throws Exception {
            var config = config();
            var registry = allocate(WorkerMatchRegistry.class);
            set(registry, "config", WorkerConfig.load(config));
            set(registry, "manifests", new HashMap<>());
            var formal = allocate(WorkerMatchSession.class);
            set(registry, "active", formal);
            var original = new BingoPresentation(Map.of("game.name", "formal"));
            set(registry, "latestPresentation", original);
            var emitted = new CopyOnWriteArrayList<MatchEvent>();
            var outbox =
                    new DurableEventOutbox(
                            event -> {
                                emitted.add(event);
                                return CompletableFuture.completedFuture(
                                        new DeliveryReceipt(event.messageId(), "events", "1-0", 1));
                            },
                            directory);
            outbox.initialize();
            set(registry, "events", outbox);
            for (var mode : List.of(MatchRunMode.TEST, MatchRunMode.TEST_EVENT)) {
                var manifest = manifest(mode);
                assertEquals(true, call(registry, "acceptManifest", MatchManifest.class, manifest));
                assertSame(original, read(registry, "latestPresentation"));
                var prepare =
                        MatchMessages.command(manifest.matchId(), 1, MatchCommandType.PREPARE);
                assertTrue(
                        ((CompletionStage<Boolean>)
                                        call(registry, "prepare", MatchCommand.class, prepare))
                                .toCompletableFuture()
                                .get(5, TimeUnit.SECONDS));
                var abort = MatchMessages.command(manifest.matchId(), 1, MatchCommandType.ABORT);
                assertTrue(
                        ((CompletionStage<Boolean>)
                                        call(registry, "dispatch", MatchCommand.class, abort))
                                .toCompletableFuture()
                                .get(5, TimeUnit.SECONDS));
                var events =
                        emitted.stream()
                                .filter(e -> e.matchId().equals(manifest.matchId()))
                                .toList();
                assertEquals(
                        List.of(
                                MatchEventType.PREPARE_FAILED,
                                MatchEventType.RELEASED,
                                MatchEventType.PREPARE_FAILED,
                                MatchEventType.RELEASED),
                        events.stream().map(MatchEvent::type).toList());
                assertEquals("test-disabled", events.get(0).attributes().get("reason"));
                assertEquals("unclaimed", events.get(1).attributes().get("worlds"));
                assertEquals(events.get(0).messageId(), events.get(2).messageId());
                assertEquals(events.get(1).messageId(), events.get(3).messageId());
                assertSame(formal, read(registry, "active"));
                assertSame(original, read(registry, "latestPresentation"));
                assertFalse((boolean) read(registry, "worldSlotConsumed"));
                // Worlds, schedulers, team service, router and progress checks are deliberately
                // null: touching a real match/world lifecycle before rejection fails this test.
            }
            for (var mode : List.of(MatchRunMode.GAME, MatchRunMode.EVENT, MatchRunMode.DAILY)) {
                var manifest = manifest(mode);
                assertEquals(true, call(registry, "acceptManifest", MatchManifest.class, manifest));
                assertSame(
                        manifest.runtimeRules().presentation(),
                        read(registry, "latestPresentation"));
            }
        }
    }

    @Nested
    class ProgressObservation {
        @Test
        void oldSessionCannotObserveTheNewMatchOrRemoveItsPendingRequest() throws Exception {
            var f = new ProgressFixture();
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
                var f = new ProgressFixture();
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
    }

    private static YamlConfiguration config() throws Exception {
        try (var input = WorkerMatchRegistryTest.class.getResourceAsStream("/config.yml")) {
            var config =
                    YamlConfiguration.loadConfiguration(
                            new InputStreamReader(
                                    Objects.requireNonNull(input), StandardCharsets.UTF_8));
            config.set("enabled", true);
            return config;
        }
    }

    private static MatchManifest manifest(MatchRunMode mode) {
        return new MatchManifest(
                ProtocolVersion.CURRENT,
                UUID.randomUUID(),
                1,
                1,
                "bingo-1",
                mode,
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
                List.of(new BingoTaskSpec(0, "stone", "item", Map.of("material", "STONE"))),
                List.of(),
                List.of());
    }

    private static Object read(Object target, String name) throws Exception {
        return field(target.getClass(), name).get(target);
    }

    private static Object call(Object target, String name, Class<?> type, Object value)
            throws Exception {
        var m = target.getClass().getDeclaredMethod(name, type);
        m.setAccessible(true);
        return m.invoke(target, value);
    }

    private static final class ProgressFixture {
        final WorkerMatchRegistry registry = allocate(WorkerMatchRegistry.class);
        final UUID id = UUID.randomUUID();
        final List<Runnable> tasks = new ArrayList<>();
        final Player player;
        final WorkerMatchSession active;
        int observations;

        ProgressFixture() throws Exception {
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
}
