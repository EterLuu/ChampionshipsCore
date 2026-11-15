package ink.ziip.championshipscore.worker;

import ink.ziip.championshipscore.protocol.MatchEvent;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/** Owns one test match's release, including retries and its durable acknowledgement identity. */
final class WorkerTestRelease implements AutoCloseable {
    interface Effects {
        boolean terminal();

        List<UUID> participants();

        CompletionStage<Void> global(Supplier<CompletionStage<Void>> action);

        CompletionStage<Void> clear(List<UUID> ids, Runnable checkActive);

        CompletionStage<Void> depart(List<UUID> ids);

        MatchEvent reserveEvent(boolean reuse);

        Map<String, UUID> worldIds();

        void stage(WorkerTestReleaseJournal.Pending pending);

        void resetWorlds();

        CompletionStage<Void> publish(MatchEvent event);

        void failed(Throwable failure);
    }

    private final Effects effects;
    private final boolean reuseWorlds;
    private CompletableFuture<Void> attempt;
    private boolean closed;
    private MatchEvent releaseEvent;
    private WorkerTestReleaseJournal.Pending pendingReset;

    WorkerTestRelease(Effects effects, boolean reuseWorlds) {
        this.effects = effects;
        this.reuseWorlds = reuseWorlds;
    }

    CompletionStage<Void> release() {
        CompletableFuture<Void> result;
        synchronized (this) {
            if (closed)
                return CompletableFuture.failedFuture(
                        new IllegalStateException("test release closed"));
            if (attempt != null && !attempt.isCompletedExceptionally()) return attempt;
            result = new CompletableFuture<>();
            attempt = result;
        }
        // Start through a completed stage so synchronous failures also complete the shared attempt.
        CompletableFuture.completedFuture(null)
                .thenCompose(
                        ignored ->
                                global(
                                        result,
                                        () -> {
                                            if (!effects.terminal())
                                                throw new IllegalStateException(
                                                        "test match is not terminal");
                                            List<UUID> ids = List.copyOf(effects.participants());
                                            return effects.clear(ids, () -> checkActive(result))
                                                    .thenCompose(
                                                            cleared ->
                                                                    global(
                                                                            result,
                                                                            () ->
                                                                                    effects.depart(
                                                                                            ids)));
                                        }))
                .thenCompose(ignored -> global(result, () -> acknowledge(result)))
                .whenComplete(
                        (ignored, failure) -> {
                            if (failure != null) {
                                if (result.completeExceptionally(failure)) effects.failed(failure);
                            } else {
                                boolean active;
                                synchronized (this) {
                                    active = !closed && attempt == result;
                                }
                                if (active) result.complete(null);
                            }
                        });
        return result;
    }

    private CompletionStage<Void> global(
            CompletableFuture<Void> result, Supplier<CompletionStage<Void>> action) {
        checkActive(result);
        return effects.global(
                () -> {
                    checkActive(result);
                    return action.get();
                });
    }

    private CompletionStage<Void> acknowledge(CompletableFuture<Void> result) {
        if (reuseWorlds) {
            if (releaseEvent == null) releaseEvent = effects.reserveEvent(true);
            return effects.publish(releaseEvent);
        }
        if (pendingReset == null) {
            Map<String, UUID> worlds = effects.worldIds();
            if (releaseEvent == null) releaseEvent = effects.reserveEvent(false);
            pendingReset = new WorkerTestReleaseJournal.Pending(releaseEvent, worlds);
        }
        effects.stage(pendingReset);
        checkActive(result);
        effects.resetWorlds();
        // A reset request is not proof of fresh worlds. Only the replacement process may publish
        // this journaled event after observing all three changed world UUIDs.
        return new CompletableFuture<>();
    }

    private synchronized void checkActive(CompletableFuture<Void> result) {
        if (closed || attempt != result || result.isDone())
            throw new IllegalStateException("test release is no longer active");
    }

    synchronized boolean released() {
        return attempt != null && attempt.isDone() && !attempt.isCompletedExceptionally();
    }

    @Override
    public void close() {
        CompletableFuture<Void> pending;
        synchronized (this) {
            closed = true;
            pending = attempt;
        }
        if (pending != null && !pending.isDone())
            pending.completeExceptionally(new IllegalStateException("test release closed"));
    }
}
