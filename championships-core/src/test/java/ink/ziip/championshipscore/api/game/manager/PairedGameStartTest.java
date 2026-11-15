package ink.ziip.championshipscore.api.game.manager;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

class PairedGameStartTest {
    @Test
    void successfulBatchWaitsForEveryPreloadAndDispatchesGateRelease() {
        Slot a = new Slot(), b = new Slot();
        List<Runnable> queued = new ArrayList<>();
        assertTrue(PairedGameStart.execute(List.of(a, b), queued::add));
        assertSame(a.gate, b.gate);
        assertFalse(a.gate.isDone());
        a.ready.complete(null);
        assertTrue(queued.isEmpty());
        b.ready.complete(null);
        assertEquals(1, queued.size());
        assertFalse(a.gate.isDone());
        queued.getFirst().run();
        assertTrue(a.gate.isDone());
        assertTrue(a.claimed && b.claimed);
    }

    @Test
    void rejectedStartRollsBackAttemptedSlotsAndClearsUnstartedGates() {
        Slot a = new Slot(), b = new Slot(), c = new Slot();
        b.accept = false;
        assertFalse(PairedGameStart.execute(List.of(a, b, c), Runnable::run));
        assertEquals(1, a.rollbacks);
        assertEquals(1, b.rollbacks);
        assertEquals(0, c.rollbacks);
        assertNull(a.gate);
        assertNull(b.gate);
        assertNull(c.gate);
        assertFalse(a.claimed);
    }

    @Test
    void rollbackFailureDoesNotPreventLaterCleanupOrHideOriginalError() {
        Slot a = new Slot(), b = new Slot();
        a.failRollback = true;
        b.failStart = true;
        RuntimeException failure =
                assertThrows(
                        RuntimeException.class,
                        () -> PairedGameStart.execute(List.of(a, b), Runnable::run));
        assertEquals("start", failure.getMessage());
        assertEquals("rollback", failure.getSuppressed()[0].getMessage());
        assertEquals(1, a.rollbacks);
        assertEquals(1, b.rollbacks);
        assertNull(a.gate);
        assertNull(b.gate);
    }

    @Test
    void refusalWithFailedRollbackStillCleansEverySlotExactlyOnce() {
        Slot a = new Slot(), b = new Slot();
        a.failRollback = true;
        b.accept = false;
        assertThrows(
                RuntimeException.class,
                () -> PairedGameStart.execute(List.of(a, b), Runnable::run));
        assertEquals(1, a.rollbacks);
        assertEquals(1, b.rollbacks);
        assertNull(a.gate);
        assertNull(b.gate);
    }

    @Test
    void armFailureReleasesEveryAlreadyArmedGateWithoutStarting() {
        Slot a = new Slot(), b = new Slot();
        b.failArm = true;
        assertThrows(
                RuntimeException.class,
                () -> PairedGameStart.execute(List.of(a, b), Runnable::run));
        assertEquals(0, a.starts);
        assertEquals(0, b.starts);
        assertNull(a.gate);
        assertNull(b.gate);
    }

    @Test
    void registrationFailureRollsBackPartialClaims() {
        Slot a = new Slot(), b = new Slot();
        b.failClaim = true;
        assertThrows(
                RuntimeException.class,
                () -> PairedGameStart.execute(List.of(a, b), Runnable::run));
        assertFalse(a.claimed);
        assertFalse(b.claimed);
        assertNull(a.gate);
        assertNull(b.gate);
    }

    @Test
    void rejectedCompletionDispatchCannotLeaveGatePending() {
        Slot a = new Slot();
        assertTrue(
                PairedGameStart.execute(
                        List.of(a),
                        r -> {
                            throw new IllegalStateException("closed");
                        }));
        a.ready.complete(null);
        assertTrue(a.gate.isCompletedExceptionally());
    }

    private static class Slot implements PairedGameStart.Slot {
        CompletableFuture<Void> gate;
        final CompletableFuture<Void> ready = new CompletableFuture<>();
        int starts, rollbacks;
        boolean accept = true, claimed, failArm, failStart, failClaim, failRollback;

        @Override
        public void arm(CompletableFuture<Void> gate) {
            this.gate = gate;
            if (gate != null && failArm) throw new IllegalStateException("arm");
        }

        @Override
        public boolean start() {
            starts++;
            if (failStart) throw new IllegalStateException("start");
            return accept;
        }

        @Override
        public void claim() {
            claimed = true;
            if (failClaim) throw new IllegalStateException("claim");
        }

        @Override
        public void rollback() {
            rollbacks++;
            claimed = false;
            if (failRollback) throw new IllegalStateException("rollback");
        }

        @Override
        public CompletableFuture<Void> ready() {
            return ready;
        }
    }
}
