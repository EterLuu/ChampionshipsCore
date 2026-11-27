package ink.ziip.championshipscore.worker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ink.ziip.championshipscore.protocol.BinaryProtocolCodec;
import ink.ziip.championshipscore.protocol.MatchEvent;
import ink.ziip.championshipscore.protocol.MatchEventType;
import ink.ziip.championshipscore.protocol.MatchMessages;
import ink.ziip.championshipscore.protocol.transport.DeliveryReceipt;
import ink.ziip.championshipscore.protocol.transport.MatchEventPublisher;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

class DurableEventOutboxTest {
    private static final UUID MATCH_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final Clock CLOCK =
            Clock.fixed(Instant.ofEpochMilli(1_800_000_000_000L), ZoneOffset.UTC);

    @Test
    void laterEventsAreStagedWhileFirstRedisPublicationIsBlocked(@TempDir Path directory)
            throws Exception {
        BlockingPublisher publisher = new BlockingPublisher();
        try (DurableEventOutbox outbox = new DurableEventOutbox(publisher, directory)) {
            outbox.initialize();
            MatchEvent first =
                    MatchMessages.event(MATCH_ID, 1, 1, MatchEventType.READY, Map.of(), CLOCK);
            MatchEvent second =
                    MatchMessages.event(MATCH_ID, 1, 2, MatchEventType.STARTED, Map.of(), CLOCK);

            CompletionStage<DeliveryReceipt> firstResult = outbox.publishEvent(first);
            CompletionStage<DeliveryReceipt> secondResult = outbox.publishEvent(second);

            await(() -> eventFileCount(directory) == 2);
            assertEquals(2, eventFileCount(directory));
            assertEquals(first, publisher.firstEntered.get(2, TimeUnit.SECONDS));
            assertEquals(1, publisher.calls.get());
            publisher.first.complete(receipt(first));
            assertEquals(second, publisher.secondEntered.get(2, TimeUnit.SECONDS));
            publisher.second.complete(receipt(second));
            firstResult.toCompletableFuture().get(1, TimeUnit.SECONDS);
            secondResult.toCompletableFuture().get(1, TimeUnit.SECONDS);
            assertEquals(0, eventFileCount(directory));
        }
    }

    @Test
    void initializationRecoversAtomicMoveTemporaryFile(@TempDir Path directory) throws Exception {
        MatchEvent event =
                MatchMessages.event(MATCH_ID, 2, 1, MatchEventType.HEARTBEAT, Map.of(), CLOCK);
        Files.write(
                directory.resolve(event.messageId() + ".tmp"),
                new BinaryProtocolCodec().encodeEvent(event));
        ImmediatePublisher publisher = new ImmediatePublisher();
        try (DurableEventOutbox outbox = new DurableEventOutbox(publisher, directory)) {

            outbox.initialize();
            assertEquals(1, eventFileCount(directory));
            assertEquals(1, outbox.replay().toCompletableFuture().get(1, TimeUnit.SECONDS));
            assertEquals(1, publisher.calls.get());
            assertEquals(0, eventFileCount(directory));
        }
    }

    @Test
    void incompleteTemporaryFileDoesNotBlockOtherEvents(@TempDir Path directory) throws Exception {
        var event = MatchMessages.event(MATCH_ID, 1, 1, MatchEventType.READY, Map.of(), CLOCK);
        Files.write(directory.resolve("broken.tmp"), new byte[] {0});
        Files.write(
                directory.resolve(event.messageId() + ".tmp"),
                new BinaryProtocolCodec().encodeEvent(event));
        try (var outbox = new DurableEventOutbox(new ImmediatePublisher(), directory)) {
            outbox.initialize();
            assertEquals(1, outbox.replay().toCompletableFuture().get(2, TimeUnit.SECONDS));
            outbox.initialize();
            try (var files = Files.list(directory)) {
                assertEquals(1, files.filter(p -> p.toString().endsWith(".corrupt")).count());
            }
        }
    }

    @Test
    void acknowledgedEventCleanupNeverRepublishesOrBlocksLaterEvents(@TempDir Path directory)
            throws Exception {
        var first = MatchMessages.event(MATCH_ID, 1, 1, MatchEventType.READY, Map.of(), CLOCK);
        var second = MatchMessages.event(MATCH_ID, 1, 2, MatchEventType.STARTED, Map.of(), CLOCK);
        var publisher = new BlockingPublisher();
        try (var outbox = new DurableEventOutbox(publisher, directory)) {
            outbox.initialize();
            var firstResult = outbox.publishEvent(first);
            publisher.firstEntered.get(2, TimeUnit.SECONDS);
            Path path;
            try (var files = Files.list(directory)) {
                path = files.filter(p -> p.toString().endsWith(".event")).findFirst().orElseThrow();
            }
            Files.delete(path);
            Files.createDirectory(path);
            Path sentinel = Files.createFile(path.resolve("hold"));
            publisher.first.complete(receipt(first));
            firstResult.toCompletableFuture().get(2, TimeUnit.SECONDS);
            var secondResult = outbox.publishEvent(second);
            assertEquals(second, publisher.secondEntered.get(2, TimeUnit.SECONDS));
            publisher.second.complete(receipt(second));
            secondResult.toCompletableFuture().get(2, TimeUnit.SECONDS);
            Files.delete(sentinel);
            await(() -> !Files.exists(path));
            assertEquals(2, publisher.calls.get());
        }
    }

    @Test
    void synchronousPublisherFailureRetriesBeforeTheNextEvent(@TempDir Path directory)
            throws Exception {
        var attempts = new CopyOnWriteArrayList<Long>();
        MatchEventPublisher publisher =
                event -> {
                    attempts.add(event.seq());
                    if (attempts.size() == 1)
                        throw new IllegalStateException("transport temporarily unavailable");
                    return CompletableFuture.completedFuture(receipt(event));
                };
        try (var outbox = new DurableEventOutbox(publisher, directory)) {
            outbox.initialize();
            var first = MatchMessages.event(MATCH_ID, 1, 1, MatchEventType.READY, Map.of(), CLOCK);
            var second =
                    MatchMessages.event(MATCH_ID, 1, 2, MatchEventType.STARTED, Map.of(), CLOCK);
            var firstResult = outbox.publishEvent(first);
            var secondResult = outbox.publishEvent(second);
            assertEquals(
                    receipt(first), firstResult.toCompletableFuture().get(5, TimeUnit.SECONDS));
            assertEquals(
                    receipt(second), secondResult.toCompletableFuture().get(5, TimeUnit.SECONDS));
            assertEquals(List.of(1L, 1L, 2L), attempts);
            assertEquals(0, eventFileCount(directory));
            outbox.closeGracefully(Duration.ofSeconds(1));
            assertTrue(outbox.publishEvent(first).toCompletableFuture().isCompletedExceptionally());
            assertEquals(0, eventFileCount(directory));
        }
    }

    @Test
    void replayOrdersOldSequencesBeforeLiveEventsAcrossAClockCorrection(@TempDir Path directory)
            throws Exception {
        MatchEvent first =
                MatchMessages.event(MATCH_ID, 1, 1, MatchEventType.READY, Map.of(), CLOCK);
        MatchEvent second =
                MatchMessages.event(
                        MATCH_ID,
                        1,
                        2,
                        MatchEventType.STARTED,
                        Map.of(),
                        Clock.offset(CLOCK, Duration.ofMinutes(-1)));
        MatchEvent live =
                MatchMessages.event(MATCH_ID, 1, 3, MatchEventType.HEARTBEAT, Map.of(), CLOCK);
        var codec = new BinaryProtocolCodec();
        for (var event : List.of(first, second))
            Files.write(directory.resolve(event.messageId() + ".tmp"), codec.encodeEvent(event));
        var gate = new CompletableFuture<DeliveryReceipt>();
        var sent = new CopyOnWriteArrayList<Long>();
        try (var outbox =
                new DurableEventOutbox(
                        event -> {
                            sent.add(event.seq());
                            return event.equals(first)
                                    ? gate
                                    : CompletableFuture.completedFuture(receipt(event));
                        },
                        directory)) {
            outbox.initialize();
            var replay = outbox.replay();
            var latest = outbox.publishEvent(live);
            await(() -> eventFileCount(directory) == 3 && !sent.isEmpty());
            assertEquals(List.of(1L), sent);
            gate.complete(receipt(first));
            assertEquals(2, replay.toCompletableFuture().get(3, TimeUnit.SECONDS));
            assertEquals(receipt(live), latest.toCompletableFuture().get(3, TimeUnit.SECONDS));
            assertEquals(List.of(1L, 2L, 3L), sent);
        }
    }

    private static DeliveryReceipt receipt(MatchEvent event) {
        return new DeliveryReceipt(event.messageId(), "events", "1-0", CLOCK.millis());
    }

    private static long eventFileCount(Path directory) {
        try (var files = Files.list(directory)) {
            return files.filter(path -> path.getFileName().toString().endsWith(".event")).count();
        } catch (java.io.IOException failure) {
            throw new java.io.UncheckedIOException(failure);
        }
    }

    private static void await(java.util.function.BooleanSupplier condition)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(10L);
        assertTrue(condition.getAsBoolean(), "Timed out waiting for both events to be staged");
    }

    private static final class BlockingPublisher implements MatchEventPublisher {
        private final AtomicInteger calls = new AtomicInteger();
        private final CompletableFuture<DeliveryReceipt> first = new CompletableFuture<>();
        private final CompletableFuture<DeliveryReceipt> second = new CompletableFuture<>();
        private final CompletableFuture<MatchEvent> firstEntered = new CompletableFuture<>();
        private final CompletableFuture<MatchEvent> secondEntered = new CompletableFuture<>();

        @Override
        public CompletionStage<DeliveryReceipt> publishEvent(MatchEvent event) {
            if (calls.incrementAndGet() == 1) {
                firstEntered.complete(event);
                return first;
            }
            secondEntered.complete(event);
            return second;
        }
    }

    private static final class ImmediatePublisher implements MatchEventPublisher {
        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public CompletionStage<DeliveryReceipt> publishEvent(MatchEvent event) {
            calls.incrementAndGet();
            return CompletableFuture.completedFuture(receipt(event));
        }
    }
}
