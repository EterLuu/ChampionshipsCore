package ink.ziip.championshipscore.redis;

import ink.ziip.championshipscore.protocol.transport.DeliveryDisposition;
import ink.ziip.championshipscore.protocol.transport.DeliveryHandler;
import ink.ziip.championshipscore.protocol.transport.InboundDelivery;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;

/** Generic at-least-once Stream consumer with pending-entry reclaim and dead-letter handling. */
public final class RedisStreamConsumer implements AutoCloseable {
    private final RedisConnectionConfig connectionConfig;
    private final RedisConsumerConfig consumerConfig;
    private final String stream;
    private final String groupStartOffset;
    private final String deadLetterStream;
    private final DeliveryHandler<Map<String, String>> handler;
    private final java.util.function.Consumer<Throwable> errorHandler;
    private final RedisClient client;
    private final StatefulRedisConnection<String, String> readConnection;
    private final StatefulRedisConnection<String, String> controlConnection;
    private final RedisAsyncCommands<String, String> reads;
    private final RedisAsyncCommands<String, String> control;
    private final Consumer<String> consumer;
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile String nextClaimId = "0-0";

    public RedisStreamConsumer(RedisConnectionConfig connectionConfig, RedisConsumerConfig consumerConfig,
                               String stream, String groupStartOffset,
                               DeliveryHandler<Map<String, String>> handler,
                               java.util.function.Consumer<Throwable> errorHandler) {
        this.connectionConfig = Objects.requireNonNull(connectionConfig, "connectionConfig");
        this.consumerConfig = Objects.requireNonNull(consumerConfig, "consumerConfig");
        this.stream = requireText(stream, "stream");
        this.groupStartOffset = requireText(groupStartOffset, "groupStartOffset");
        this.deadLetterStream = stream + ":dead";
        this.handler = Objects.requireNonNull(handler, "handler");
        this.errorHandler = Objects.requireNonNull(errorHandler, "errorHandler");
        this.client = RedisClient.create(io.lettuce.core.RedisURI.create(connectionConfig.uri()));
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
        control.xgroupCreate(XReadArgs.StreamOffset.from(stream, groupStartOffset), consumerConfig.group(),
                        new XGroupCreateArgs().mkstream(true))
                .whenComplete((ignored, error) -> {
                    Throwable cause = unwrap(error);
                    if (cause != null && !isBusyGroup(cause)) {
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
        XAutoClaimArgs<String> args = new XAutoClaimArgs<String>().consumer(consumer)
                .minIdleTime(consumerConfig.reclaimIdle()).startId(nextClaimId)
                .count(consumerConfig.batchSize());
        control.xautoclaim(stream, args).whenComplete((claimed, error) -> {
            if (!running()) return;
            if (error != null) {
                report(error);
                readNew();
                return;
            }
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
                        report(error);
                        reclaimThenRead();
                        return;
                    }
                    processSerially(messages).whenComplete((ignored, processingError) -> {
                        if (processingError != null) report(processingError);
                        reclaimThenRead();
                    });
                });
    }

    private CompletionStage<Void> processSerially(List<StreamMessage<String, String>> messages) {
        CompletionStage<Void> chain = CompletableFuture.completedFuture(null);
        if (messages == null) return chain;
        for (StreamMessage<String, String> message : messages)
            chain = chain.thenCompose(ignored -> processOne(message));
        return chain;
    }

    private CompletionStage<Void> processOne(StreamMessage<String, String> message) {
        long deliveries = message.getDeliveredCount() == null ? 1L : message.getDeliveredCount();
        if (deliveries >= consumerConfig.maxDeliveries())
            return deadLetter(message, "max-deliveries:" + deliveries).thenCompose(ignored -> acknowledge(message));
        InboundDelivery<Map<String, String>> delivery = new InboundDelivery<>(message.getStream(),
                message.getId(), deliveries, message.isClaimed(), Map.copyOf(message.getBody()));
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
        return result.handle((disposition, failure) -> failure == null && disposition != null
                        ? disposition : DeliveryDisposition.RETRY)
                .thenCompose(disposition -> switch (disposition) {
                    case ACK -> acknowledge(message);
                    case RETRY -> CompletableFuture.completedFuture(null);
                    case DEAD_LETTER -> deadLetter(message, "handler-requested").thenCompose(ignored -> acknowledge(message));
                });
    }

    private CompletionStage<Void> acknowledge(StreamMessage<String, String> message) {
        return control.xack(stream, consumerConfig.group(), message.getId()).thenApply(ignored -> null);
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
        XAddArgs args = new XAddArgs().maxlen(connectionConfig.approximateMaxStreamLength()).approximateTrimming();
        return control.xadd(deadLetterStream, args, fields).thenApply(ignored -> null);
    }

    private boolean running() { return started.get() && !closed.get(); }
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
        return error instanceof RedisCommandExecutionException && error.getMessage() != null
                && error.getMessage().contains("BUSYGROUP");
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
        readConnection.close();
        controlConnection.close();
        client.shutdown();
    }
}
