package ink.ziip.championshipscore.redis;

import ink.ziip.championshipscore.protocol.transport.DeliveryHandler;

import java.util.Map;
import java.util.function.Consumer;

/** Consumes immutable Redis field maps from the configured group offset. */
public final class RedisStreamConsumer extends RedisConsumerLoop<Map<String, String>> {
    public RedisStreamConsumer(
            RedisConnectionConfig config,
            RedisConsumerConfig consumerConfig,
            String stream,
            String groupStartOffset,
            DeliveryHandler<Map<String, String>> handler,
            Consumer<Throwable> errorHandler) {
        super(
                config.uri(),
                config.approximateMaxStreamLength(),
                groupStartOffset,
                Map::copyOf,
                consumerConfig,
                stream,
                handler,
                errorHandler);
    }
}
