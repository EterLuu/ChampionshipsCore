package ink.ziip.championshipscore.redis;

import ink.ziip.championshipscore.protocol.CrossServerChatMessage;
import ink.ziip.championshipscore.protocol.transport.DeliveryDisposition;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

/** Redis Stream fan-out for short-lived global chat messages. */
public final class RedisChatTransport implements AutoCloseable {
    private static final long MAX_MESSAGE_AGE_MILLIS = Duration.ofSeconds(30).toMillis();
    private static final int DEDUPLICATION_LIMIT = 4_096;

    private final RedisConnectionConfig config;
    private final RedisStreamPublisher publisher;
    private final RedisStreamConsumer consumer;
    private final Consumer<CrossServerChatMessage> handler;
    private final Consumer<Throwable> errorHandler;
    private final Map<UUID, Boolean> processed = java.util.Collections.synchronizedMap(
            new LinkedHashMap<>(DEDUPLICATION_LIMIT, .75F, true) {
                @Override protected boolean removeEldestEntry(Map.Entry<UUID, Boolean> eldest) {
                    return size() > DEDUPLICATION_LIMIT;
                }
            });

    public RedisChatTransport(RedisConnectionConfig config, RedisConsumerConfig consumerConfig,
                              Consumer<CrossServerChatMessage> handler,
                              Consumer<Throwable> errorHandler) {
        this.config = Objects.requireNonNull(config, "config");
        this.handler = Objects.requireNonNull(handler, "handler");
        this.errorHandler = Objects.requireNonNull(errorHandler, "errorHandler");
        this.publisher = new RedisStreamPublisher(config);
        this.consumer = new RedisStreamConsumer(config, Objects.requireNonNull(consumerConfig, "consumerConfig"),
                stream(config), "$", this::consume, this.errorHandler);
    }

    public CompletionStage<Void> start() {
        return publisher.ping().thenCompose(ignored -> consumer.start());
    }

    public CompletionStage<String> publish(CrossServerChatMessage message) {
        Objects.requireNonNull(message, "message");
        if (!config.instanceId().equals(message.sourceInstance())) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("Chat source does not match this Redis instance"));
        }
        return publisher.append(stream(config), message.fields());
    }

    private CompletionStage<DeliveryDisposition> consume(
            ink.ziip.championshipscore.protocol.transport.InboundDelivery<Map<String, String>> delivery) {
        CrossServerChatMessage message;
        try {
            message = CrossServerChatMessage.parse(delivery.payload());
        } catch (RuntimeException malformed) {
            return CompletableFuture.completedFuture(DeliveryDisposition.DEAD_LETTER);
        }
        long age = System.currentTimeMillis() - message.createdAt();
        if (config.instanceId().equals(message.sourceInstance()) || age > MAX_MESSAGE_AGE_MILLIS
                || age < -MAX_MESSAGE_AGE_MILLIS || processed.containsKey(message.messageId())) {
            return CompletableFuture.completedFuture(DeliveryDisposition.ACK);
        }
        try {
            handler.accept(message);
            processed.put(message.messageId(), Boolean.TRUE);
            return CompletableFuture.completedFuture(DeliveryDisposition.ACK);
        } catch (RuntimeException failure) {
            errorHandler.accept(failure);
            return CompletableFuture.completedFuture(DeliveryDisposition.RETRY);
        }
    }

    public static String stream(RedisConnectionConfig config) {
        return Objects.requireNonNull(config, "config").key("chat:global");
    }

    @Override
    public void close() {
        consumer.close();
        publisher.close();
    }
}
