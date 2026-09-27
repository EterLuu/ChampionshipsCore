package ink.ziip.championshipscore.worker;

import ink.ziip.championshipscore.protocol.MatchState;
import org.junit.jupiter.api.Test;
import java.util.ArrayDeque;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class WorkerAdminStopTest {
    @Test
    void defersWorldMutationsAndWaitsForSettlementPublication() {
        var global = new ArrayDeque<Runnable>();
        var publication = new CompletableFuture<Boolean>();
        var called = new AtomicReference<String>();
        var result = WorkerAdminStop.request(global::add, () -> MatchState.RUNNING,
                () -> { called.set("finish"); return publication; },
                () -> { fail("Must settle a running match"); return null; }).toCompletableFuture();
        assertNull(called.get());
        assertFalse(result.isDone());
        global.remove().run();
        assertEquals("finish", called.get());
        assertFalse(result.isDone());
        publication.complete(true);
        assertTrue(result.join());
    }

    @Test
    void cancelsPreparationWithoutInventingASettlement() {
        for (MatchState state : new MatchState[]{MatchState.CREATED, MatchState.PREPARING,
                MatchState.READY, MatchState.ROUTING, MatchState.COUNTDOWN, MatchState.SUSPENDED}) {
            var result = WorkerAdminStop.request(Runnable::run, () -> state,
                    () -> { fail("Unstarted match cannot settle"); return null; },
                    () -> CompletableFuture.completedFuture(true));
            assertTrue(result.toCompletableFuture().join());
        }
    }

    @Test
    void rechecksStateOnGlobalThreadAfterAnotherStopHasWon() {
        var global = new ArrayDeque<Runnable>();
        var state = new AtomicReference<>(MatchState.RUNNING);
        var result = WorkerAdminStop.request(global::add, state::get,
                () -> { fail("Already stopping"); return null; },
                () -> { fail("Already stopping"); return null; }).toCompletableFuture();
        state.set(MatchState.SETTLING);
        global.remove().run();
        assertFalse(result.join());
    }

    @Test
    void reportsExceptionsThroughCompletionInsteadOfThrowingFromCommand() {
        var result = WorkerAdminStop.request(Runnable::run, () -> MatchState.RUNNING,
                () -> { throw new IllegalStateException("world failure"); },
                () -> CompletableFuture.completedFuture(true));
        assertThrows(CompletionException.class, () -> result.toCompletableFuture().join());
    }
}
