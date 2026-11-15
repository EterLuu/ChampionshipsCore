package ink.ziip.championshipscore.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.UUID;

class CrossServerChatMessageTest {
    @Test
    void roundTripsRedisFields() {
        CrossServerChatMessage message =
                new CrossServerChatMessage(
                        UUID.randomUUID(),
                        "core-a",
                        UUID.randomUUID(),
                        "Alice",
                        "&cRed",
                        "#ff5555",
                        true,
                        "{\"text\":\"hello\"}",
                        123L);
        assertEquals(message, CrossServerChatMessage.parse(message.fields()));
    }

    @Test
    void rejectsMissingAndInvalidFields() {
        CrossServerChatMessage message =
                new CrossServerChatMessage(
                        UUID.randomUUID(),
                        "core-a",
                        UUID.randomUUID(),
                        "Alice",
                        "&cRed",
                        null,
                        false,
                        "{\"text\":\"hello\"}",
                        123L);
        var missing = new HashMap<>(message.fields());
        missing.remove("senderName");
        assertThrows(IllegalArgumentException.class, () -> CrossServerChatMessage.parse(missing));
        var invalid = new HashMap<>(message.fields());
        invalid.put("daily", "invalid");
        assertThrows(IllegalArgumentException.class, () -> CrossServerChatMessage.parse(invalid));
    }
}
