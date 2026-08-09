package ink.ziip.championshipscore.protocol.transport;

import java.util.Objects;

/** Transport-neutral metadata supplied to a durable message handler. */
public record InboundDelivery<T>(
        String stream,
        String streamEntryId,
        long deliveryCount,
        boolean reclaimed,
        T payload
) {
    public InboundDelivery {
        Objects.requireNonNull(stream, "stream");
        Objects.requireNonNull(streamEntryId, "streamEntryId");
        Objects.requireNonNull(payload, "payload");
        if (stream.isBlank() || streamEntryId.isBlank()) {
            throw new IllegalArgumentException("stream and streamEntryId must not be blank");
        }
        if (deliveryCount < 1) deliveryCount = 1;
    }
}
