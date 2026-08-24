package ink.ziip.championshipscore.redis;

import ink.ziip.championshipscore.protocol.transport.DeliveryDisposition;
import ink.ziip.championshipscore.protocol.transport.DeliveryHandler;
import ink.ziip.championshipscore.protocol.transport.InboundDelivery;
import ink.ziip.championshipscore.protocol.transport.MatchInboundMessage;
import io.lettuce.core.Consumer;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisCommandExecutionException;
import io.lettuce.core.StreamMessage;
import io.lettuce.core.XAddArgs;
import io.lettuce.core.XAutoClaimArgs;
import io.lettuce.core.XGroupCreateArgs;
import io.lettuce.core.XReadArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;

import java.time.Instant;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * At-least-once Redis Streams consumer. Deliveries are processed serially per consumer so match
 * event order is preserved. Handler failure or RETRY intentionally leaves the entry pending;
 * abandoned entries are reclaimed after the configured idle period.
 */
public final class RedisMatchConsumer implements AutoCloseable {
    private final RedisTransportConfig transportConfig;
    private final RedisConsumerConfig consumerConfig;
    private final String stream;
    private final String deadLetterStream;
    private final DeliveryHandler<MatchInboundMessage> handler;
    private final java.util.function.Consumer<Throwable> errorHandler;
    private final RedisMatchMessageCodec codec;
    private final RedisClient client;
    private final StatefulRedisConnection<String, String> readConnection;
    private final StatefulRedisConnection<String, String> controlConnection;
    private final RedisAsyncCommands<String, String> reads;
    private final RedisAsyncCommands<String, String> control;
    private final Consumer<String> consumer;
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private volatile String nextClaimId = "0-0";

    public RedisMatchConsumer(RedisTransportConfig transportConfig, RedisConsumerConfig consumerConfig,
                              String stream, DeliveryHandler<MatchInboundMessage> handler,
                              java.util.function.Consumer<Throwable> errorHandler) {
        this.transportConfig = Objects.requireNonNull(transportConfig, "transportConfig");
        this.consumerConfig = Objects.requireNonNull(consumerConfig, "consumerConfig");
        this.stream = requireText(stream, "stream");
        this.deadLetterStream = stream + ":dead";
        this.handler = Objects.requireNonNull(handler, "handler");
        this.errorHandler = Objects.requireNonNull(errorHandler, "errorHandler");
        this.codec = new RedisMatchMessageCodec();
        this.client = RedisClient.create(io.lettuce.core.RedisURI.create(transportConfig.uri()));
        this.readConnection = client.connect();
        this.controlConnection = client.connect();
        this.reads = readConnection.async();
        this.control = controlConnection.async();
        this.consumer = Consumer.from(consumerConfig.group(), consumerConfig.consumer());
    }

    public CompletionStage<Void> start() {
        if (closed.get()) return CompletableFuture.failedFuture(new IllegalStateException("Consumer is closed"));
        if (!started.compareAndSet(false, true)) return CompletableFuture.completedFuture(null);
        CompletableFuture<Void> ready = new CompletableFuture<>();
        ensureGroup()
                .whenComplete((ignored, error) -> {
                    Throwable cause = unwrap(error);
                    if (cause != null) {
                        started.set(false);
                        ready.completeExceptionally(cause);
                        return;
                    }
                    ready.complete(null);
                    reclaimThenRead();
                });
        return ready;
    }

    private void reclaimThenRead() {
        if (!running()) return;
        XAutoClaimArgs<String> args = new XAutoClaimArgs<String>()
                .consumer(consumer)
                .minIdleTime(consumerConfig.reclaimIdle())
                .startId(nextClaimId)
                .count(consumerConfig.batchSize());
        control.xautoclaim(stream, args).whenComplete((claimed, error) -> {
            if (!running()) return;
            if (error != null) {
                recover(error, this::readNew);
                return;
            }
            resetFailures();
            nextClaimId = claimed.getId();
            processSerially(claimed.getMessages()).whenComplete((ignored, processingError) -> {
                if (processingError != null) report(processingError);
                readNew();
            });
        });
    }

    private void readNew() {
        if (!running()) return;
        XReadArgs args = new XReadArgs().count(consumerConfig.batchSize()).block(consumerConfig.blockTimeout());
        reads.xreadgroup(consumer, args, streamOffsets(XReadArgs.StreamOffset.lastConsumed(stream)))
                .whenComplete((messages, error) -> {
                    if (!running()) return;
                    if (error != null) {
                        recover(error, this::reclaimThenRead);
                        return;
                    }
                    resetFailures();
                    processSerially(messages).whenComplete((ignored, processingError) -> {
                        if (processingError != null) report(processingError);
                        reclaimThenRead();
                    });
                });
    }

    private CompletionStage<Void> processSerially(List<StreamMessage<String, String>> messages) {
        CompletionStage<Void> chain = CompletableFuture.completedFuture(null);
        if (messages == null) return chain;
        for (StreamMessage<String, String> message : messages) {
            chain = chain.thenCompose(ignored -> processOne(message));
        }
        return chain;
    }

    private CompletionStage<Void> processOne(StreamMessage<String, String> message) {
        MatchInboundMessage payload;
        try {
            payload = codec.decode(message.getBody());
        } catch (RuntimeException malformed) {
            return deadLetter(message, "decode:" + concise(malformed))
                    .thenCompose(ignored -> acknowledge(message));
        }

        long deliveries = message.getDeliveredCount() == null ? 1L : message.getDeliveredCount();
        if (deliveries >= consumerConfig.maxDeliveries()) {
            return deadLetter(message, "max-deliveries:" + deliveries)
                    .thenCompose(ignored -> acknowledge(message));
        }
        InboundDelivery<MatchInboundMessage> delivery = new InboundDelivery<>(
                message.getStream(), message.getId(), deliveries, message.isClaimed(), payload);
        CompletionStage<DeliveryDisposition> result;
        try {
            result = handler.handle(delivery);
        } catch (RuntimeException failure) {
            report(failure);
            return CompletableFuture.completedFuture(null);
        }
        if (result == null) {
            report(new IllegalStateException("Delivery handler returned null stage"));
            return CompletableFuture.completedFuture(null);
        }
        return result.handle((disposition, failure) -> {
                    if (failure != null) {
                        report(failure);
                        return DeliveryDisposition.RETRY;
                    }
                    return disposition == null ? DeliveryDisposition.RETRY : disposition;
                })
                .thenCompose(disposition -> switch (disposition) {
                    case ACK -> acknowledge(message);
                    case RETRY -> CompletableFuture.completedFuture(null);
                    case DEAD_LETTER -> deadLetter(message, "handler-requested")
                            .thenCompose(ignored -> acknowledge(message));
                });
    }

    private CompletionStage<Void> acknowledge(StreamMessage<String, String> message) {
        return control.xack(stream, consumerConfig.group(), message.getId())
                .handle((ignored, error) -> {
                    Throwable cause = unwrap(error);
                    if (cause == null) return CompletableFuture.<Void>completedFuture(null);
                    if (isNoGroup(cause)) return ensureGroup();
                    return CompletableFuture.<Void>failedFuture(cause);
                })
                .thenCompose(stage -> stage);
    }

    private CompletionStage<Void> deadLetter(StreamMessage<String, String> message, String reason) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("sourceStream", stream);
        fields.put("sourceEntryId", message.getId());
        fields.put("consumerGroup", consumerConfig.group());
        fields.put("consumer", consumerConfig.consumer());
        fields.put("failedAt", Long.toString(Instant.now().toEpochMilli()));
        fields.put("reason", reason);
        message.getBody().forEach((key, value) -> fields.put("original." + key, value));
        XAddArgs args = new XAddArgs().maxlen(transportConfig.approximateMaxStreamLength())
                .approximateTrimming();
        return control.xadd(deadLetterStream, args, fields).thenApply(ignored -> null);
    }

    private boolean running() {
        return started.get() && !closed.get();
    }

    private CompletionStage<Void> ensureGroup() {
        return control.xgroupCreate(XReadArgs.StreamOffset.from(stream, "0-0"), consumerConfig.group(),
                        new XGroupCreateArgs().mkstream(true))
                .handle((ignored, error) -> {
                    Throwable cause = unwrap(error);
                    if (cause != null && !isBusyGroup(cause)) throw new CompletionException(cause);
                    return null;
                });
    }

    private void recover(Throwable error, Runnable retry) {
        Throwable cause = unwrap(error);
        if (isNoGroup(cause)) {
            ensureGroup().whenComplete((ignored, recreationError) -> {
                if (recreationError != null) {
                    report(recreationError);
                    retryLater(retry);
                } else {
                    resetFailures();
                    retry.run();
                }
            });
            return;
        }
        report(cause);
        retryLater(retry);
    }

    private void retryLater(Runnable retry) {
        if (!running()) return;
        int failures = Math.min(consecutiveFailures.getAndIncrement(), 6);
        long delayMillis = Math.min(5_000L, 100L << failures);
        CompletableFuture.delayedExecutor(delayMillis, java.util.concurrent.TimeUnit.MILLISECONDS)
                .execute(() -> {
                    if (running()) retry.run();
                });
    }

    private void resetFailures() {
        consecutiveFailures.set(0);
    }

    private void report(Throwable error) {
        Throwable cause = unwrap(error);
        if (cause != null && running()) errorHandler.accept(cause);
    }

    private static Throwable unwrap(Throwable error) {
        Throwable current = error;
        while ((current instanceof CompletionException || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) current = current.getCause();
        return current;
    }

    private static boolean isBusyGroup(Throwable error) {
        return error instanceof RedisCommandExecutionException
                && error.getMessage() != null && error.getMessage().contains("BUSYGROUP");
    }

    private static boolean isNoGroup(Throwable error) {
        return error instanceof RedisCommandExecutionException
                && error.getMessage() != null && error.getMessage().contains("NOGROUP");
    }

    private static String concise(Throwable error) {
        String message = error.getMessage();
        String text = error.getClass().getSimpleName() + (message == null ? "" : ":" + message);
        return text.length() <= 512 ? text : text.substring(0, 512);
    }

    @SafeVarargs
    private static <K> XReadArgs.StreamOffset<K>[] streamOffsets(XReadArgs.StreamOffset<K>... offsets) {
        return offsets;
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        started.set(false);
        client.shutdown(Duration.ofMillis(100), Duration.ofSeconds(5));
    }
}
