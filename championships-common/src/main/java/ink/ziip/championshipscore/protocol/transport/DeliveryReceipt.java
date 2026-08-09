package ink.ziip.championshipscore.protocol.transport;

import java.util.Objects;
import java.util.UUID;

/** Durable transport acceptance. Redis implementations use the stream entry ID as position. */
public record DeliveryReceipt(
        UUID messageId,
        String destination,
        String position,
        long acceptedAtEpochMilli
) {
    public DeliveryReceipt {
        Objects.requireNonNull(messageId, "messageId");
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(position, "position");
        if (destination.isBlank() || position.isBlank()) {
            throw new IllegalArgumentException("destination and position must not be blank");
        }
        if (acceptedAtEpochMilli < 1) throw new IllegalArgumentException("acceptedAtEpochMilli must be positive");
    }
}
