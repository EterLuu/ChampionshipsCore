package ink.ziip.championshipscore.protocol.transport;

/**
 * Durable consumer outcome. ACK removes the delivery from the pending list, RETRY leaves it pending
 * for a later reclaim, and DEAD_LETTER copies the original message to the dead-letter stream before
 * acknowledging it.
 */
public enum DeliveryDisposition {
    ACK,
    RETRY,
    DEAD_LETTER
}
