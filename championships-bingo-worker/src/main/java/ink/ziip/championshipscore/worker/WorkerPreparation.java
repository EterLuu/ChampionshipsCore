package ink.ziip.championshipscore.worker;

import java.util.concurrent.CompletionStage;
import java.util.function.BooleanSupplier;

/**
 * Owns preparation callbacks, while the session remains the authority for match state and roles.
 */
final class WorkerPreparation implements AutoCloseable {
    interface Host {
        boolean valid();

        Runnable later(Runnable action, long ticks);

        void execute(Runnable action);

        void introduction(BooleanSupplier current);

        void introductionTick(int elapsed, BooleanSupplier current);

        void preparation(BooleanSupplier current);

        void preparationTick(int remaining, BooleanSupplier current);

        CompletionStage<Void> prepare(BooleanSupplier current);

        CompletionStage<Void> scatter(BooleanSupplier current);

        void countdown(int remaining, BooleanSupplier current);

        void hideTimer();

        void start();

        void failed(String reason, Throwable failure);
    }

    private enum Phase {
        INTRODUCTION,
        PREPARATION,
        SCATTER,
        COUNTDOWN
    }

    private final Host host;
    private volatile Run active;
    private volatile boolean closed;
    private final int introductionSeconds;
    private final int preparationSeconds;
    private final int countdownSeconds;

    WorkerPreparation(
            Host host, int introductionSeconds, int preparationSeconds, int countdownSeconds) {
        this.host = host;
        this.introductionSeconds = Math.max(0, introductionSeconds);
        this.preparationSeconds = Math.max(0, preparationSeconds);
        this.countdownSeconds = Math.max(0, countdownSeconds);
    }

    void start(boolean introduction) {
        if (closed || active != null || !host.valid()) return;
        Run run = new Run();
        active = run;
        perform(
                run,
                () -> {
                    if (introduction) {
                        run.phase = Phase.INTRODUCTION;
                        host.introduction(current(run));
                        if (valid(run)) introductionTick(run, 0);
                    } else prepare(run);
                });
    }

    boolean introducing() {
        Run run = active;
        return run != null && run.phase == Phase.INTRODUCTION;
    }

    private boolean valid(Run run) {
        return active == run && !closed && host.valid();
    }

    private BooleanSupplier current(Run run) {
        Phase phase = run.phase;
        return () -> valid(run) && run.phase == phase;
    }

    private void introductionTick(Run run, int elapsed) {
        host.introductionTick(elapsed, current(run));
        if (!valid(run)) return;
        if (elapsed >= introductionSeconds) {
            host.hideTimer();
            prepare(run);
        } else later(run, () -> introductionTick(run, elapsed + 1));
    }

    private void prepare(Run run) {
        if (!valid(run)) return;
        run.phase = Phase.PREPARATION;
        host.preparation(current(run));
        if (valid(run)) preparationTick(run, preparationSeconds);
    }

    private void preparationTick(Run run, int remaining) {
        if (remaining > 0) {
            host.preparationTick(remaining, current(run));
            if (valid(run)) later(run, () -> preparationTick(run, remaining - 1));
            return;
        }
        host.hideTimer();
        run.phase = Phase.SCATTER;
        await(
                run,
                "team-scatter-prepare-failed",
                () -> host.prepare(current(run)),
                () ->
                        await(
                                run,
                                "team-scatter-teleport-failed",
                                () -> host.scatter(current(run)),
                                () -> {
                                    run.phase = Phase.COUNTDOWN;
                                    countdown(run, countdownSeconds);
                                }));
    }

    private void countdown(Run run, int remaining) {
        if (remaining <= 0) {
            host.start();
            if (active == run) active = null;
        } else {
            host.countdown(remaining, current(run));
            if (valid(run)) later(run, () -> countdown(run, remaining - 1));
        }
    }

    private void later(Run run, Runnable action) {
        long sequence = ++run.sequence;
        Runnable cancel =
                host.later(
                        () -> {
                            if (!valid(run) || run.sequence != sequence) return;
                            run.cancel = null;
                            perform(run, action);
                        },
                        20L);
        if (valid(run) && run.sequence == sequence) run.cancel = cancel;
        else cancel.run();
    }

    private void await(
            Run run,
            String reason,
            java.util.function.Supplier<CompletionStage<Void>> operation,
            Runnable next) {
        if (!valid(run)) return;
        long sequence = ++run.sequence;
        try {
            operation
                    .get()
                    .whenComplete(
                            (unused, failure) -> {
                                if (!valid(run) || run.sequence != sequence) return;
                                try {
                                    host.execute(
                                            () -> {
                                                if (!valid(run) || run.sequence != sequence) return;
                                                // Duplicate callbacks cannot advance the same stage
                                                // twice.
                                                run.sequence++;
                                                if (failure != null) fail(run, reason, failure);
                                                else perform(run, next);
                                            });
                                } catch (RuntimeException failureToSchedule) {
                                    fail(run, reason, failureToSchedule);
                                }
                            });
        } catch (RuntimeException failure) {
            fail(run, reason, failure);
        }
    }

    private void perform(Run run, Runnable action) {
        if (!valid(run)) return;
        try {
            action.run();
        } catch (RuntimeException failure) {
            fail(run, "preparation-failed", failure);
        }
    }

    private void fail(Run run, String reason, Throwable failure) {
        if (active != run) return;
        active = null;
        if (run.cancel != null) run.cancel.run();
        host.failed(reason, failure);
    }

    @Override
    public void close() {
        closed = true;
        Run run = active;
        active = null;
        if (run != null && run.cancel != null) run.cancel.run();
    }

    private static final class Run {
        private volatile Phase phase;
        private volatile Runnable cancel;
        private volatile long sequence;
    }
}
