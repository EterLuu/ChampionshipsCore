package ink.ziip.championshipscore.worker;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.protocol.*;
import ink.ziip.championshipscore.protocol.transport.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.*;

class WorkerTestReleaseJournalTest {
    @TempDir Path directory;

    private Map<String, UUID> worlds() {
        return Map.of(
                "bingo",
                UUID.randomUUID(),
                "bingo_nether",
                UUID.randomUUID(),
                "bingo_the_end",
                UUID.randomUUID());
    }

    private WorkerTestReleaseJournal.Pending pending() {
        return new WorkerTestReleaseJournal.Pending(
                MatchMessages.event(
                        UUID.randomUUID(),
                        1,
                        5,
                        MatchEventType.RELEASED,
                        Map.of("worlds", "reset"),
                        Clock.systemUTC()),
                worlds());
    }

    @Test
    void replacementMustObserveAllThreeChangedWorldUuidsBeforePublication() throws Exception {
        var journal = new WorkerTestReleaseJournal(directory);
        var value = pending();
        journal.stage(value);
        var restarted = new WorkerTestReleaseJournal(directory);
        var published = new ArrayList<MatchEvent>();
        MatchEventPublisher publisher =
                event -> {
                    published.add(event);
                    return CompletableFuture.completedFuture(
                            new DeliveryReceipt(event.messageId(), "events", "1-0", 1));
                };
        assertEquals(
                0,
                restarted.resume(value.previousWorlds(), publisher).toCompletableFuture().join());
        var partial = new HashMap<>(value.previousWorlds());
        partial.put("bingo", UUID.randomUUID());
        assertEquals(0, restarted.resume(partial, publisher).toCompletableFuture().join());
        assertTrue(published.isEmpty());
        assertEquals(1, restarted.pending().size());
        assertEquals(1, restarted.resume(worlds(), publisher).toCompletableFuture().join());
        assertEquals(List.of(value.event()), published);
        assertTrue(restarted.pending().isEmpty());
    }

    @Test
    void publicationFailureKeepsExactEventIdentityForRetry() throws Exception {
        var journal = new WorkerTestReleaseJournal(directory);
        var value = pending();
        journal.stage(value);
        assertThrows(
                CompletionException.class,
                () ->
                        journal.resume(
                                        worlds(),
                                        event ->
                                                CompletableFuture.failedFuture(
                                                        new IllegalStateException("redis offline")))
                                .toCompletableFuture()
                                .join());
        assertEquals(List.of(value), new WorkerTestReleaseJournal(directory).pending());
        var receipt = new CompletableFuture<DeliveryReceipt>();
        var resume = journal.resume(worlds(), event -> receipt).toCompletableFuture();
        assertFalse(resume.isDone());
        assertEquals(1, journal.pending().size());
        receipt.complete(new DeliveryReceipt(value.event().messageId(), "events", "2-0", 1));
        assertEquals(1, resume.join());
        assertTrue(journal.pending().isEmpty());
    }

    @Test
    void duplicateStageIsIdempotentButChangedEventCannotOverwriteAHandoff() throws Exception {
        var journal = new WorkerTestReleaseJournal(directory);
        var value = pending();
        journal.stage(value);
        journal.stage(value);
        var altered =
                new WorkerTestReleaseJournal.Pending(
                        new MatchEvent(
                                ProtocolVersion.CURRENT,
                                UUID.randomUUID(),
                                value.event().matchId(),
                                1,
                                6,
                                1,
                                MatchEventType.RELEASED,
                                Map.of("worlds", "reset")),
                        value.previousWorlds());
        assertThrows(java.io.IOException.class, () -> journal.stage(altered));
        assertEquals(List.of(value), journal.pending());
    }

    @Test
    void corruptJournalFailsBeforeWorkerCommandAdmission() throws Exception {
        Files.writeString(directory.resolve("invalid.release"), "corrupt");
        assertThrows(java.io.IOException.class, () -> new WorkerTestReleaseJournal(directory));
    }
}
