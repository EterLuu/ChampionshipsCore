package ink.ziip.championshipscore.worker;

import ink.ziip.championshipscore.protocol.BinaryProtocolCodec;
import ink.ziip.championshipscore.protocol.MatchEvent;
import ink.ziip.championshipscore.protocol.transport.DeliveryReceipt;
import ink.ziip.championshipscore.protocol.transport.MatchEventPublisher;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * File-backed worker outbox: an event is durable before Redis publication and deleted after XADD.
 */
final class DurableEventOutbox implements MatchEventPublisher {
    private static final Logger LOG = Logger.getLogger(DurableEventOutbox.class.getName());
    private final Set<Path> acknowledgedFiles = new HashSet<>();
    private final MatchEventPublisher delegate;
    private final Path directory;
    private final BinaryProtocolCodec codec = new BinaryProtocolCodec();
    private CompletionStage<Void> stagingTail = CompletableFuture.completedFuture(null);
    private boolean accepting = true;
    private CompletionStage<Void> publicationTail = CompletableFuture.completedFuture(null);
    private final AtomicBoolean closed = new AtomicBoolean();

    DurableEventOutbox(MatchEventPublisher delegate, Path directory) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.directory = Objects.requireNonNull(directory, "directory");
    }

    void initialize() throws IOException {
        Files.createDirectories(directory);
        recoverTemporaryFiles();
    }

    synchronized CompletionStage<Integer> replay() {
        var snapshot = publicationTail.thenApplyAsync(ignored -> readPending());
        // Live writes can resume once the snapshot is captured, even if Redis is unavailable.
        stagingTail = snapshot.handle((ignored, failure) -> null);
        var replayed =
                snapshot.thenCompose(
                        events -> {
                            CompletionStage<Integer> chain = CompletableFuture.completedFuture(0);
                            for (MatchEvent event : events) {
                                chain =
                                        chain.thenCompose(
                                                count ->
                                                        publishWithRetry(event)
                                                                .thenApply(receipt -> count + 1));
                            }
                            return chain;
                        });
        publicationTail = replayed.handle((ignored, failure) -> null);
        return replayed.toCompletableFuture().minimalCompletionStage();
    }

    private List<MatchEvent> readPending() {
        try (var files = Files.list(directory)) {
            var paths =
                    files.filter(path -> path.getFileName().toString().endsWith(".event"))
                            .sorted()
                            .toList();
            List<MatchEvent> events = new ArrayList<>(paths.size());
            // Keep the arrival slots of independent matches, but respect a match's protocol order
            // even when its wall clock moved backwards before shutdown.
            var order =
                    Comparator.comparingLong(MatchEvent::epoch).thenComparingLong(MatchEvent::seq);
            Map<UUID, PriorityQueue<MatchEvent>> byMatch = new HashMap<>();
            for (Path path : paths) {
                MatchEvent event = codec.decodeEvent(Files.readAllBytes(path));
                events.add(event);
                byMatch.computeIfAbsent(event.matchId(), ignored -> new PriorityQueue<>(order))
                        .add(event);
            }
            events.replaceAll(event -> byMatch.get(event.matchId()).remove());
            return events;
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    @Override
    public synchronized CompletionStage<DeliveryReceipt> publishEvent(MatchEvent event) {
        if (!accepting || closed.get())
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Event outbox is closed"));
        // A blocked XADD must not prevent later events from reaching disk. Serialize file writes
        // independently so two submissions for the same message cannot truncate the same .tmp.
        CompletionStage<Void> staged = stagingTail.thenRunAsync(() -> stage(event));
        stagingTail = staged.handle((ignored, failure) -> null);
        CompletionStage<DeliveryReceipt> publication =
                publicationTail
                        .thenCompose(ignored -> staged)
                        .thenCompose(ignored -> publishWithRetry(event));
        publicationTail = publication.handle((ignored, failure) -> null);
        return publication.toCompletableFuture().minimalCompletionStage();
    }

    private CompletionStage<DeliveryReceipt> publishWithRetry(MatchEvent event) {
        CompletableFuture<DeliveryReceipt> result = new CompletableFuture<>();
        attempt(event, result, 0);
        return result;
    }

    private static long retryDelayMillis(int failures) {
        long ceiling = Math.min(60_000L, 1000L << Math.min(failures, 6));
        return ThreadLocalRandom.current().nextLong(ceiling / 2, ceiling + 1);
    }

    private void attempt(
            MatchEvent event, CompletableFuture<DeliveryReceipt> result, int failures) {
        if (closed.get()) {
            result.completeExceptionally(new IllegalStateException("Event outbox is closed"));
            return;
        }
        CompletionStage<DeliveryReceipt> publication;
        try {
            publication = delegate.publishEvent(event);
        } catch (RuntimeException failure) {
            publication = CompletableFuture.failedFuture(failure);
        }
        publication.whenCompleteAsync(
                (receipt, failure) -> {
                    if (failure == null) {
                        acknowledge(path(event));
                        result.complete(receipt);
                    } else if (closed.get()) {
                        result.completeExceptionally(failure);
                    } else {
                        if (failures == 0 || failures % 10 == 0)
                            LOG.log(
                                    Level.WARNING,
                                    "Retrying Worker event " + event.messageId(),
                                    failure);
                        CompletableFuture.delayedExecutor(
                                        retryDelayMillis(failures), TimeUnit.MILLISECONDS)
                                .execute(() -> attempt(event, result, failures + 1));
                    }
                });
    }

    private synchronized void acknowledge(Path file) {
        if (closed.get()) return;
        try {
            Files.deleteIfExists(file);
        } catch (IOException failure) {
            boolean startCleanup = acknowledgedFiles.isEmpty();
            acknowledgedFiles.add(file);
            if (startCleanup) {
                LOG.log(
                        Level.WARNING,
                        "Published Worker event awaiting local cleanup: " + file,
                        failure);
                scheduleCleanup(0);
            }
        }
    }

    private void scheduleCleanup(int failures) {
        CompletableFuture.delayedExecutor(retryDelayMillis(failures), TimeUnit.MILLISECONDS)
                .execute(() -> retryCleanup(failures + 1));
    }

    private synchronized void retryCleanup(int failures) {
        if (closed.get()) return;
        IOException lastFailure = null;
        for (var it = acknowledgedFiles.iterator(); it.hasNext(); ) {
            try {
                Files.deleteIfExists(it.next());
                it.remove();
            } catch (IOException failure) {
                lastFailure = failure;
            }
        }
        if (!acknowledgedFiles.isEmpty()) {
            if (failures % 10 == 0)
                LOG.log(
                        Level.WARNING,
                        "Worker outbox cleanup pending for " + acknowledgedFiles.size() + " files",
                        lastFailure);
            scheduleCleanup(failures);
        }
    }

    private void stage(MatchEvent event) {
        try {
            Path target = path(event);
            if (Files.exists(target)) return;
            Path temporary = temporaryPath(event);
            Files.write(
                    temporary,
                    codec.encodeEvent(event),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.SYNC);
            commitTemporaryFile(temporary, target);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private Path path(MatchEvent event) {
        return directory.resolve(
                String.format(
                        "%020d-%020d-%s.event",
                        event.createdAtEpochMilli(), event.seq(), event.messageId()));
    }

    private Path temporaryPath(MatchEvent event) {
        return directory.resolve(event.messageId() + ".tmp");
    }

    private void recoverTemporaryFiles() throws IOException {
        try (var files = Files.list(directory)) {
            for (Path temporary :
                    files.filter(path -> path.getFileName().toString().endsWith(".tmp")).toList()) {
                byte[] staged = Files.readAllBytes(temporary);
                MatchEvent event;
                try {
                    event = codec.decodeEvent(staged);
                } catch (IllegalArgumentException malformed) {
                    Path quarantine =
                            temporary.resolveSibling(
                                    temporary.getFileName() + "." + UUID.randomUUID() + ".corrupt");
                    Files.move(temporary, quarantine);
                    LOG.log(
                            Level.WARNING,
                            "Quarantined incomplete Worker outbox file " + quarantine,
                            malformed);
                    continue;
                }
                Path target = path(event);
                if (Files.exists(target)) {
                    Files.delete(temporary);
                    continue;
                }
                commitTemporaryFile(temporary, target);
            }
        }
    }

    private static void commitTemporaryFile(Path temporary, Path target) throws IOException {
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    void closeGracefully(Duration timeout) {
        CompletionStage<Void> tail;
        synchronized (this) {
            accepting = false;
            tail = publicationTail;
        }
        try {
            tail.toCompletableFuture().get(Math.max(1L, timeout.toMillis()), TimeUnit.MILLISECONDS);
        } catch (TimeoutException timeoutFailure) {
            // Files already staged remain available for replay on the next worker start.
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (java.util.concurrent.ExecutionException ignored) {
            // A failed publication remains staged and is replayed on the next start.
        } finally {
            close();
        }
    }

    @Override
    public void close() {
        synchronized (this) {
            if (!closed.compareAndSet(false, true)) return;
            acknowledgedFiles.clear();
        }
        delegate.close();
    }
}
