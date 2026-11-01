package ink.ziip.championshipscore.worker;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.protocol.*;
import ink.ziip.championshipscore.protocol.transport.DeliveryReceipt;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStreamReader;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

class WorkerTestAdmissionTest {
    @Test
    void optInDefaultsOffAndOnlyExplicitBooleanTrueEnablesTests() throws Exception {
        var config = config();
        assertFalse(WorkerConfig.load(config).testEnabled());
        config.set("test", null);
        assertFalse(WorkerConfig.load(config).testEnabled());
        config.set("test.enabled", "true");
        assertFalse(WorkerConfig.load(config).testEnabled());
        config.set("test.enabled", true);
        assertTrue(WorkerConfig.load(config).testEnabled());
    }

    @Test
    void disabledTestRejectsWithoutTakingOverFormalSessionOrPresentation(@TempDir Path directory)
            throws Exception {
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
            assertSame(original, field(registry, "latestPresentation"));
            var prepare = MatchMessages.command(manifest.matchId(), 1, MatchCommandType.PREPARE);
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
                    emitted.stream().filter(e -> e.matchId().equals(manifest.matchId())).toList();
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
            assertSame(formal, field(registry, "active"));
            assertSame(original, field(registry, "latestPresentation"));
            assertFalse((boolean) field(registry, "worldSlotConsumed"));
            // Worlds, schedulers, team service, router and progress checks are deliberately null:
            // touching a real match/world lifecycle before rejection fails this test.
        }
        for (var mode : List.of(MatchRunMode.GAME, MatchRunMode.EVENT, MatchRunMode.DAILY)) {
            var manifest = manifest(mode);
            assertEquals(true, call(registry, "acceptManifest", MatchManifest.class, manifest));
            assertSame(
                    manifest.runtimeRules().presentation(), field(registry, "latestPresentation"));
        }
    }

    private static YamlConfiguration config() throws Exception {
        try (var input = WorkerTestAdmissionTest.class.getResourceAsStream("/config.yml")) {
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

    private static <T> T allocate(Class<T> type) throws Exception {
        var f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        return type.cast(((sun.misc.Unsafe) f.get(null)).allocateInstance(type));
    }

    private static Object field(Object target, String name) throws Exception {
        var f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }

    private static void set(Object target, String name, Object value) throws Exception {
        var f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    private static Object call(Object target, String name, Class<?> type, Object value)
            throws Exception {
        var m = target.getClass().getDeclaredMethod(name, type);
        m.setAccessible(true);
        return m.invoke(target, value);
    }
}
