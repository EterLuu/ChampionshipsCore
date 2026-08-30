package ink.ziip.championshipscore.protocol;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CrossServerChatMessageTest {
    @Test
    void roundTripsRedisFields() {
        CrossServerChatMessage message = new CrossServerChatMessage(UUID.randomUUID(), "core-a",
                UUID.randomUUID(), "Alice", "&cRed", "#ff5555", true,
                "{\"text\":\"hello\"}", 123L);
        assertEquals(message, CrossServerChatMessage.parse(message.fields()));
    }

    @Test
    void rejectsMissingAndInvalidFields() {
        CrossServerChatMessage message = new CrossServerChatMessage(UUID.randomUUID(), "core-a",
                UUID.randomUUID(), "Alice", "&cRed", null, false,
                "{\"text\":\"hello\"}", 123L);
        java.util.Map<String, String> missing = new java.util.HashMap<>(message.fields());
        missing.remove("senderName");
        assertThrows(IllegalArgumentException.class, () -> CrossServerChatMessage.parse(missing));
    }
}
