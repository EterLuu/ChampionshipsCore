package ink.ziip.championshipscore.protocol.transport;

import ink.ziip.championshipscore.protocol.MatchCommand;
import ink.ziip.championshipscore.protocol.MatchManifest;

import java.util.concurrent.CompletionStage;

/** SCC-to-worker durable command transport. */
public interface MatchCommandPublisher extends AutoCloseable {
    CompletionStage<DeliveryReceipt> publishManifest(MatchManifest manifest);

    CompletionStage<DeliveryReceipt> publishCommand(MatchCommand command);

    @Override
    default void close() {
    }
}
