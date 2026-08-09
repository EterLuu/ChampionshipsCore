package ink.ziip.championshipscore.worker;

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
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DurableEventOutboxTest {
    private static final UUID MATCH_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final Clock CLOCK = Clock.fixed(Instant.ofEpochMilli(1_800_000_000_000L), ZoneOffset.UTC);

    @Test
    void laterEventsAreStagedWhileFirstRedisPublicationIsBlocked(@TempDir Path directory) throws Exception {
        BlockingPublisher publisher = new BlockingPublisher();
        DurableEventOutbox outbox = new DurableEventOutbox(publisher, directory);
        outbox.initialize();
        MatchEvent first = MatchMessages.event(MATCH_ID, 1, 1, MatchEventType.READY, Map.of(), CLOCK);
        MatchEvent second = MatchMessages.event(MATCH_ID, 1, 2, MatchEventType.STARTED, Map.of(), CLOCK);

        CompletionStage<DeliveryReceipt> firstResult = outbox.publishEvent(first);
        CompletionStage<DeliveryReceipt> secondResult = outbox.publishEvent(second);

        await(() -> eventFileCount(directory) == 2);
        assertEquals(2, eventFileCount(directory));
        assertEquals(1, publisher.calls.get());
        publisher.first.complete(receipt(first));
        await(() -> publisher.calls.get() == 2);
        publisher.second.complete(receipt(second));
        firstResult.toCompletableFuture().get(1, TimeUnit.SECONDS);
        secondResult.toCompletableFuture().get(1, TimeUnit.SECONDS);
        assertEquals(0, eventFileCount(directory));
        outbox.close();
    }

    @Test
    void initializationRecoversAtomicMoveTemporaryFile(@TempDir Path directory) throws Exception {
        MatchEvent event = MatchMessages.event(MATCH_ID, 2, 1, MatchEventType.HEARTBEAT, Map.of(), CLOCK);
        Files.write(directory.resolve(event.messageId() + ".tmp"), new BinaryProtocolCodec().encodeEvent(event));
        ImmediatePublisher publisher = new ImmediatePublisher();
        DurableEventOutbox outbox = new DurableEventOutbox(publisher, directory);

        outbox.initialize();
        assertEquals(1, eventFileCount(directory));
        assertEquals(1, outbox.replay().toCompletableFuture().get(1, TimeUnit.SECONDS));
        assertEquals(1, publisher.calls.get());
        assertEquals(0, eventFileCount(directory));
        outbox.close();
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

    private static void await(java.util.function.BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(10L);
    }

    private static final class BlockingPublisher implements MatchEventPublisher {
        private final AtomicInteger calls = new AtomicInteger();
        private final CompletableFuture<DeliveryReceipt> first = new CompletableFuture<>();
        private final CompletableFuture<DeliveryReceipt> second = new CompletableFuture<>();

        @Override
        public CompletionStage<DeliveryReceipt> publishEvent(MatchEvent event) {
            return calls.incrementAndGet() == 1 ? first : second;
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
