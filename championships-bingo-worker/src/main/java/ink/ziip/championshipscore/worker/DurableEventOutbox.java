package ink.ziip.championshipscore.worker;

import ink.ziip.championshipscore.protocol.BinaryProtocolCodec;
import ink.ziip.championshipscore.protocol.MatchEvent;
import ink.ziip.championshipscore.protocol.transport.DeliveryReceipt;
import ink.ziip.championshipscore.protocol.transport.MatchEventPublisher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/** File-backed worker outbox: an event is durable before Redis publication and deleted after XADD. */
final class DurableEventOutbox implements MatchEventPublisher {
    private final MatchEventPublisher delegate;
    private final Path directory;
    private final BinaryProtocolCodec codec = new BinaryProtocolCodec();
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

    CompletionStage<Integer> replay() {
        return CompletableFuture.supplyAsync(() -> {
            try (var files = Files.list(directory)) {
                List<Path> pending = files.filter(path -> path.getFileName().toString().endsWith(".event"))
                        .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                        .toList();
                List<MatchEvent> events = new ArrayList<>(pending.size());
                for (Path path : pending) events.add(codec.decodeEvent(Files.readAllBytes(path)));
                return events;
            } catch (IOException failure) {
                throw new java.io.UncheckedIOException(failure);
            }
        }).thenCompose(events -> {
            CompletionStage<Integer> chain = CompletableFuture.completedFuture(0);
            for (MatchEvent event : events) {
                chain = chain.thenCompose(count -> publishWithRetry(event).thenApply(ignored -> count + 1));
            }
            return chain;
        });
    }

    @Override
    public synchronized CompletionStage<DeliveryReceipt> publishEvent(MatchEvent event) {
        CompletableFuture<DeliveryReceipt> result = new CompletableFuture<>();
        CompletableFuture<Void> staged = CompletableFuture.runAsync(() -> stage(event));
        publicationTail = publicationTail.handle((ignored, previousFailure) -> null)
                .thenCompose(ignored -> staged)
                .thenCompose(ignored -> publishWithRetry(event))
                .handle((receipt, failure) -> {
                    if (failure == null) result.complete(receipt);
                    else result.completeExceptionally(failure);
                    return null;
                });
        return result;
    }

    private CompletionStage<DeliveryReceipt> publishStaged(MatchEvent event) {
        return delegate.publishEvent(event).thenApply(receipt -> {
            try {
                Files.deleteIfExists(path(event));
            } catch (IOException failure) {
                throw new java.io.UncheckedIOException(failure);
            }
            return receipt;
        });
    }

    private CompletionStage<DeliveryReceipt> publishWithRetry(MatchEvent event) {
        if (closed.get()) return CompletableFuture.failedFuture(new IllegalStateException("Event outbox is closed"));
        return publishStaged(event).handle((receipt, failure) -> {
            if (failure == null) return CompletableFuture.completedFuture(receipt);
            if (closed.get()) return CompletableFuture.<DeliveryReceipt>failedFuture(failure);
            return CompletableFuture.runAsync(() -> { },
                            CompletableFuture.delayedExecutor(1, TimeUnit.SECONDS))
                    .thenCompose(ignored -> publishWithRetry(event));
        }).thenCompose(stage -> stage);
    }

    private void stage(MatchEvent event) {
        try {
            Path target = path(event);
            if (Files.exists(target)) return;
            Path temporary = temporaryPath(event);
            Files.write(temporary, codec.encodeEvent(event), StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE, StandardOpenOption.SYNC);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException failure) {
            throw new java.io.UncheckedIOException(failure);
        }
    }

    private Path path(MatchEvent event) {
        return directory.resolve(String.format("%020d-%020d-%s.event",
                event.createdAtEpochMilli(), event.seq(), event.messageId()));
    }

    private Path temporaryPath(MatchEvent event) {
        return directory.resolve(event.messageId() + ".tmp");
    }

    private void recoverTemporaryFiles() throws IOException {
        try (var files = Files.list(directory)) {
            for (Path temporary : files.filter(path -> path.getFileName().toString().endsWith(".tmp")).toList()) {
                MatchEvent event = codec.decodeEvent(Files.readAllBytes(temporary));
                Path target = path(event);
                if (Files.exists(target)) {
                    Files.delete(temporary);
                    continue;
                }
                try {
                    Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
                } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                    Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    void closeGracefully(Duration timeout) {
        CompletionStage<Void> tail;
        synchronized (this) {
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
        closed.set(true);
        delegate.close();
    }
}
