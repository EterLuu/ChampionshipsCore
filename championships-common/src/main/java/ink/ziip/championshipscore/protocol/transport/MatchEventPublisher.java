package ink.ziip.championshipscore.protocol.transport;

import ink.ziip.championshipscore.protocol.MatchEvent;

import java.util.concurrent.CompletionStage;

/** Worker-to-SCC durable event transport. */
public interface MatchEventPublisher extends AutoCloseable {
    CompletionStage<DeliveryReceipt> publishEvent(MatchEvent event);

    @Override
    default void close() {
    }
}
