package ink.ziip.championshipscore.redis;

import ink.ziip.championshipscore.protocol.transport.DeliveryHandler;
import ink.ziip.championshipscore.protocol.transport.MatchInboundMessage;

import java.util.function.Consumer;

/** Replays durable match commands or events from the beginning of a new group. */
public final class RedisMatchConsumer extends RedisConsumerLoop<MatchInboundMessage> {
    public RedisMatchConsumer(
            RedisTransportConfig config,
            RedisConsumerConfig consumerConfig,
            String stream,
            DeliveryHandler<MatchInboundMessage> handler,
            Consumer<Throwable> errorHandler) {
        super(
                config.uri(),
                config.approximateMaxStreamLength(),
                "0-0",
                new RedisMatchMessageCodec()::decode,
                consumerConfig,
                stream,
                handler,
                errorHandler);
    }
}
