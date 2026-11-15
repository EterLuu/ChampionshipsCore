package ink.ziip.championshipscore.worker;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BooleanSupplier;

class WorkerPreparationTest {
    @Test
    void phasesWaitForBothPreparationAndScatterBeforeFinalCountdown() {
        Fixture f = new Fixture();
        f.flow.start(true);
        assertTrue(f.flow.introducing());
        f.tick();
        f.tick();
        assertFalse(f.flow.introducing());
        f.tick();
        f.tick();
        assertEquals(
                List.of(
                        "intro",
                        "intro:0",
                        "intro:1",
                        "intro:2",
                        "hide",
                        "prepare",
                        "prepare:2",
                        "prepare:1",
                        "hide",
                        "resources"),
                f.events);
        assertTrue(f.delayed.isEmpty());
        f.prepared.complete(null);
        f.flush();
        assertEquals("scatter", f.events.getLast());
        f.scattered.complete(null);
        f.flush();
        assertEquals("countdown:2", f.events.getLast());
        f.tick();
        f.tick();
        assertEquals(
                List.of("countdown:2", "countdown:1", "start"),
                f.events.subList(f.events.size() - 3, f.events.size()));
    }

    @Test
    void duplicateStartDoesNotQueueASecondIntroduction() {
        Fixture f = new Fixture();
        f.flow.start(true);
        f.flow.start(true);
        assertEquals(List.of("intro", "intro:0"), f.events);
        assertEquals(1, f.delayed.size());
    }

    @Test
    void cancelledTickerCannotContinueEvenWhenHostStillReportsCountdown() {
        Fixture f = new Fixture();
        f.flow.start(true);
        Task old = f.delayed.remove();
        f.flow.close();
        old.action.run();
        f.flow.start(false);
        assertTrue(old.cancelled());
        assertEquals(List.of("intro", "intro:0"), f.events);
        assertFalse(f.flow.introducing());
    }

    @Test
    void closedFlowIgnoresPendingPreparationAndQueuedContinuation() {
        for (boolean queued : List.of(false, true)) {
            Fixture f = new Fixture();
            f.zero();
            f.flow.start(false);
            if (queued) f.prepared.complete(null);
            f.flow.close();
            f.prepared.complete(null);
            f.flush();
            assertFalse(f.events.contains("scatter"));
        }
    }

    @Test
    void playerActionsFromPreviousPhaseOrClosedRunAreRejected() {
        Fixture f = new Fixture();
        f.flow.start(true);
        BooleanSupplier intro = f.guards.getFirst();
        f.tick();
        f.tick();
        assertFalse(intro.getAsBoolean());
        BooleanSupplier prepare = f.guards.getLast();
        assertTrue(prepare.getAsBoolean());
        f.flow.close();
        assertFalse(prepare.getAsBoolean());
    }

    @Test
    void asynchronousFailuresDoNotAdvanceToRunning() {
        for (boolean scatter : List.of(false, true)) {
            Fixture f = new Fixture();
            f.zero();
            f.flow.start(false);
            if (scatter) {
                f.prepared.complete(null);
                f.flush();
            }
            (scatter ? f.scattered : f.prepared)
                    .completeExceptionally(new IllegalStateException("failed"));
            f.flush();
            assertEquals(
                    List.of(
                            scatter
                                    ? "team-scatter-teleport-failed"
                                    : "team-scatter-prepare-failed"),
                    f.failures);
            assertFalse(f.events.contains("start"));
        }
    }

    @Test
    void synchronousPreparationFailureIsReported() {
        Fixture f = new Fixture();
        f.zero();
        f.throwPrepare = true;
        f.flow.start(false);
        assertEquals(List.of("team-scatter-prepare-failed"), f.failures);
    }

    @Test
    void schedulerRejectionDoesNotLeaveAPendingFlow() {
        for (boolean dispatch : List.of(false, true)) {
            Fixture f = new Fixture();
            if (dispatch) {
                f.zero();
                f.flow.start(false);
                f.reject = true;
                f.prepared.complete(null);
            } else {
                f.reject = true;
                f.flow.start(true);
            }
            assertEquals(1, f.failures.size());
            assertFalse(f.flow.introducing());
        }
    }

    @Test
    void hostStateInvalidationSuppressesQueuedTick() {
        Fixture f = new Fixture();
        f.flow.start(true);
        f.valid = false;
        f.tick();
        assertEquals(List.of("intro", "intro:0"), f.events);
    }

    @Test
    void zeroDurationsStillWaitForScatterAndStartOnlyOnce() {
        Fixture f = new Fixture();
        f.zero();
        f.flow.start(false);
        f.prepared.complete(null);
        Runnable continuation = f.queued.remove();
        continuation.run();
        continuation.run();
        assertEquals(1, f.events.stream().filter("scatter"::equals).count());
        f.scattered.complete(null);
        f.flush();
        assertEquals(1, f.events.stream().filter("start"::equals).count());
    }

    @Test
    void startFailureIsReportedAfterCountdown() {
        Fixture f = new Fixture();
        f.zero();
        f.throwStart = true;
        f.flow.start(false);
        f.prepared.complete(null);
        f.flush();
        f.scattered.complete(null);
        f.flush();
        assertEquals(List.of("preparation-failed"), f.failures);
    }

    private record Task(Runnable action, boolean[] cancellation) {
        Task(Runnable action) {
            this(action, new boolean[1]);
        }

        boolean cancelled() {
            return cancellation[0];
        }
    }

    private static class Fixture implements WorkerPreparation.Host {
        WorkerPreparation flow = new WorkerPreparation(this, 2, 2, 2);
        final List<String> events = new ArrayList<>(), failures = new ArrayList<>();
        final List<BooleanSupplier> guards = new ArrayList<>();
        final ArrayDeque<Task> delayed = new ArrayDeque<>();
        final ArrayDeque<Runnable> queued = new ArrayDeque<>();
        final CompletableFuture<Void> prepared = new CompletableFuture<>(),
                scattered = new CompletableFuture<>();
        boolean valid = true, reject, throwPrepare, throwStart;

        void zero() {
            flow = new WorkerPreparation(this, 0, 0, 0);
        }

        void tick() {
            delayed.remove().action.run();
        }

        void flush() {
            while (!queued.isEmpty()) queued.remove().run();
        }

        @Override
        public boolean valid() {
            return valid;
        }

        @Override
        public Runnable later(Runnable action, long ticks) {
            assertEquals(20, ticks);
            if (reject) throw new IllegalStateException("rejected");
            Task task = new Task(action);
            delayed.add(task);
            return () -> task.cancellation[0] = true;
        }

        @Override
        public void execute(Runnable action) {
            if (reject) throw new IllegalStateException("rejected");
            queued.add(action);
        }

        @Override
        public void introduction(BooleanSupplier current) {
            guards.add(current);
            events.add("intro");
        }

        @Override
        public void introductionTick(int elapsed, BooleanSupplier current) {
            events.add("intro:" + elapsed);
        }

        @Override
        public void preparation(BooleanSupplier current) {
            guards.add(current);
            events.add("prepare");
        }

        @Override
        public void preparationTick(int remaining, BooleanSupplier current) {
            events.add("prepare:" + remaining);
        }

        @Override
        public CompletionStage<Void> prepare(BooleanSupplier current) {
            if (throwPrepare) throw new IllegalStateException("prepare");
            events.add("resources");
            return prepared;
        }

        @Override
        public CompletionStage<Void> scatter(BooleanSupplier current) {
            events.add("scatter");
            return scattered;
        }

        @Override
        public void countdown(int remaining, BooleanSupplier current) {
            events.add("countdown:" + remaining);
        }

        @Override
        public void hideTimer() {
            events.add("hide");
        }

        @Override
        public void start() {
            if (throwStart) throw new IllegalStateException("start");
            events.add("start");
            valid = false;
        }

        @Override
        public void failed(String reason, Throwable failure) {
            failures.add(reason);
            valid = false;
        }
    }
}
