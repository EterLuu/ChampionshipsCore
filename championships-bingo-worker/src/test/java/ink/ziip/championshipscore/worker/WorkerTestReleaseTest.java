package ink.ziip.championshipscore.worker;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.protocol.MatchEvent;
import ink.ziip.championshipscore.protocol.MatchEventType;
import ink.ziip.championshipscore.protocol.MatchMessages;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Clock;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

class WorkerTestReleaseTest {
    enum Step {
        GLOBAL,
        CLEAR,
        DEPART,
        WORLDS,
        STAGE,
        RESET,
        PUBLISH
    }

    @Test
    void cleanupDepartureAndPublicationMustAllCompleteBeforeAcknowledgement() {
        var f = new Fixture(true);
        var result = f.release();
        assertSame(result, f.release());
        assertEquals(List.of("clear"), f.calls);
        f.cleaned.complete(null);
        assertEquals(List.of("clear", "depart"), f.calls);
        assertFalse(result.isDone());
        f.departed.complete(null);
        assertEquals(List.of("clear", "depart", "publish"), f.calls);
        assertFalse(f.flow.released());
        f.published.complete(null);
        assertDoesNotThrow(result::join);
        assertTrue(f.flow.released());
        assertSame(result, f.release());
        assertEquals(1, f.events.size());
    }

    @Test
    void activeMatchIsRejectedAndCanReleaseAfterBecomingTerminal() {
        var f = new Fixture(true);
        f.terminal = false;
        assertThrows(CompletionException.class, f.release()::join);
        assertTrue(f.calls.isEmpty());
        f.terminal = true;
        f.completeAll();
        assertDoesNotThrow(f.release()::join);
    }

    @ParameterizedTest
    @EnumSource(Step.class)
    void synchronousFailureCompletesTheAttemptAndAllowsRetry(Step step) {
        var f = new Fixture(step == Step.PUBLISH);
        f.completeAll();
        f.fail = step;
        var failed = f.release();
        assertThrows(CompletionException.class, failed::join);
        assertFalse(f.flow.released());
        f.fail = null;
        var retry = f.release();
        assertNotSame(failed, retry);
        assertFalse(retry.isCompletedExceptionally());
        if (f.reuse) assertDoesNotThrow(retry::join);
        else assertFalse(retry.isDone());
    }

    @Test
    void resetWaitsForDeparturesAndJournalAndNeverAcknowledgesInOldProcess() {
        var f = new Fixture(false);
        var result = f.release();
        f.cleaned.complete(null);
        assertTrue(f.staged.isEmpty());
        f.departed.complete(null);
        assertEquals(List.of("clear", "depart", "worlds", "stage", "reset"), f.calls);
        assertFalse(result.isDone());
        assertFalse(f.flow.released());
        assertTrue(f.events.isEmpty());
        assertEquals(f.worlds, f.staged.getFirst().previousWorlds());
        assertSame(result, f.release());
    }

    @Test
    void failedResetRetriesTheExactPersistedEventAndOriginalWorldUuids() {
        var f = new Fixture(false);
        f.completeAll();
        f.fail = Step.RESET;
        assertThrows(CompletionException.class, f.release()::join);
        var pending = f.staged.getFirst();
        f.worlds = worlds();
        f.fail = null;
        assertFalse(f.release().isDone());
        assertSame(pending, f.staged.getLast());
        assertEquals(1, f.reserved);
        assertEquals(1, Collections.frequency(f.calls, "worlds"));
    }

    @Test
    void failedPublicationRetriesTheSameEventIdentity() {
        var f = new Fixture(true);
        f.cleaned.complete(null);
        f.departed.complete(null);
        var failed = f.release();
        f.published.completeExceptionally(new IllegalStateException("redis unavailable"));
        assertThrows(CompletionException.class, failed::join);
        f.published = CompletableFuture.completedFuture(null);
        assertDoesNotThrow(f.release()::join);
        assertSame(f.events.getFirst(), f.events.getLast());
        assertEquals(1, f.reserved);
    }

    @Test
    void failedCleanupCannotRoutePlayersOrPublish() {
        var f = new Fixture(true);
        var failed = f.release();
        f.cleaned.completeExceptionally(new IllegalStateException("entity cleanup"));
        assertThrows(CompletionException.class, failed::join);
        assertEquals(List.of("clear"), f.calls);
        f.cleaned = CompletableFuture.completedFuture(null);
        f.departed.complete(null);
        f.published.complete(null);
        assertDoesNotThrow(f.release()::join);
    }

    @Test
    void failedDepartureCannotResetWorlds() {
        var f = new Fixture(false);
        var failed = f.release();
        f.cleaned.complete(null);
        f.departed.completeExceptionally(new IllegalStateException("router closed"));
        assertThrows(CompletionException.class, failed::join);
        assertTrue(f.staged.isEmpty());
    }

    @Test
    void closeDuringCleanupInvalidatesEntityCallbackAndBlocksDeparture() {
        var f = new Fixture(true);
        var result = f.release();
        f.flow.close();
        assertThrows(IllegalStateException.class, f.entityGuard::run);
        f.completeAll();
        assertThrows(CompletionException.class, result::join);
        assertThrows(CompletionException.class, f.release()::join);
        assertEquals(List.of("clear"), f.calls);
    }

    @Test
    void closeDuringDeparturePreventsWorldReset() {
        var f = new Fixture(false);
        var result = f.release();
        f.cleaned.complete(null);
        f.flow.close();
        f.departed.complete(null);
        assertThrows(CompletionException.class, result::join);
        assertTrue(f.staged.isEmpty());
    }

    @Test
    void closeBeforeQueuedGlobalTaskPreventsCleanup() {
        var f = new Fixture(true);
        f.queueGlobals = true;
        var result = f.release();
        f.flow.close();
        f.globalQueue.remove().run();
        assertThrows(CompletionException.class, result::join);
        assertTrue(f.calls.isEmpty());
    }

    @Test
    void closeBeforeQueuedAcknowledgementPreventsPublication() {
        var f = new Fixture(true);
        var result = f.release();
        f.cleaned.complete(null);
        f.queueGlobals = true;
        f.departed.complete(null);
        f.flow.close();
        f.globalQueue.remove().run();
        assertThrows(CompletionException.class, result::join);
        assertTrue(f.events.isEmpty());
    }

    @Test
    void latePublicationCompletionCannotReopenClosedRelease() {
        var f = new Fixture(true);
        var result = f.release();
        f.cleaned.complete(null);
        f.departed.complete(null);
        f.flow.close();
        f.published.complete(null);
        assertThrows(CompletionException.class, result::join);
        assertFalse(f.flow.released());
    }

    @Test
    void participantListIsFrozenForTheAttempt() {
        var f = new Fixture(true);
        var expected = List.copyOf(f.ids);
        f.release();
        f.ids.clear();
        f.cleaned.complete(null);
        assertEquals(expected, f.routed);
    }

    private static Map<String, UUID> worlds() {
        return Map.of(
                "bingo", UUID.randomUUID(), "nether", UUID.randomUUID(), "end", UUID.randomUUID());
    }

    private static final class Fixture implements WorkerTestRelease.Effects {
        final boolean reuse;
        final WorkerTestRelease flow;
        final List<String> calls = new ArrayList<>();
        final List<UUID> ids = new ArrayList<>(List.of(UUID.randomUUID(), UUID.randomUUID()));
        List<UUID> routed;
        final List<MatchEvent> events = new ArrayList<>();
        final List<WorkerTestReleaseJournal.Pending> staged = new ArrayList<>();
        Map<String, UUID> worlds = worlds();
        CompletableFuture<Void> cleaned = new CompletableFuture<>();
        final CompletableFuture<Void> departed = new CompletableFuture<>();
        CompletableFuture<Void> published = new CompletableFuture<>();
        boolean terminal = true;
        boolean queueGlobals;
        final Queue<Runnable> globalQueue = new ArrayDeque<>();
        Runnable entityGuard;
        Step fail;
        int reserved;

        Fixture(boolean reuse) {
            this.reuse = reuse;
            flow = new WorkerTestRelease(this, reuse);
        }

        CompletableFuture<Void> release() {
            return flow.release().toCompletableFuture();
        }

        void completeAll() {
            cleaned.complete(null);
            departed.complete(null);
            published.complete(null);
        }

        void failAt(Step step) {
            if (fail == step) throw new IllegalStateException(step.name());
        }

        public boolean terminal() {
            return terminal;
        }

        public List<UUID> participants() {
            return ids;
        }

        public CompletionStage<Void> global(Supplier<CompletionStage<Void>> action) {
            failAt(Step.GLOBAL);
            if (!queueGlobals) return action.get();
            var result = new CompletableFuture<Void>();
            globalQueue.add(
                    () ->
                            CompletableFuture.completedFuture(null)
                                    .thenCompose(ignored -> action.get())
                                    .whenComplete(
                                            (ignored, failure) -> {
                                                if (failure == null) result.complete(null);
                                                else result.completeExceptionally(failure);
                                            }));
            return result;
        }

        public CompletionStage<Void> clear(List<UUID> ids, Runnable guard) {
            calls.add("clear");
            failAt(Step.CLEAR);
            entityGuard = guard;
            return cleaned;
        }

        public CompletionStage<Void> depart(List<UUID> ids) {
            calls.add("depart");
            failAt(Step.DEPART);
            routed = ids;
            return departed;
        }

        public MatchEvent reserveEvent(boolean reuse) {
            return MatchMessages.event(
                    UUID.randomUUID(),
                    1,
                    ++reserved,
                    MatchEventType.RELEASED,
                    Map.of("worlds", reuse ? "reused" : "reset"),
                    Clock.systemUTC());
        }

        public Map<String, UUID> worldIds() {
            calls.add("worlds");
            failAt(Step.WORLDS);
            return worlds;
        }

        public void stage(WorkerTestReleaseJournal.Pending pending) {
            calls.add("stage");
            failAt(Step.STAGE);
            staged.add(pending);
        }

        public void resetWorlds() {
            calls.add("reset");
            failAt(Step.RESET);
        }

        public CompletionStage<Void> publish(MatchEvent event) {
            calls.add("publish");
            failAt(Step.PUBLISH);
            events.add(event);
            return published;
        }

        public void failed(Throwable failure) {}
    }
}
