package ink.ziip.championshipscore.protocol.transport;

import java.util.concurrent.CompletionStage;

/** Asynchronous durable-message handler shared by Core, workers and proxy adapters. */
@FunctionalInterface
public interface DeliveryHandler<T> {
    CompletionStage<DeliveryDisposition> handle(InboundDelivery<T> delivery);
}
