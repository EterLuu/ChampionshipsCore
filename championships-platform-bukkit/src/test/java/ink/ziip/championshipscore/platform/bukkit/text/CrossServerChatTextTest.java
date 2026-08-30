package ink.ziip.championshipscore.platform.bukkit.text;

import ink.ziip.championshipscore.protocol.CrossServerChatMessage;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CrossServerChatTextTest {
    @Test
    void encodesAndRendersTheSharedPlayerPresentation() {
        PlayerPresentation presentation = new PlayerPresentation("&#ff5555红队", "&#ff5555", true);
        CrossServerChatMessage message = CrossServerChatText.message("core-a", UUID.randomUUID(), "Alice",
                presentation, Component.text("hello"), 123L);

        assertEquals("core-a", message.sourceInstance());
        assertEquals(presentation.label(), message.label());
        assertEquals(presentation.teamColorCode(), message.teamColorCode());
        assertEquals(presentation.activePlayer(), message.activePlayer());
        assertEquals(presentation.chatLine("Alice", Component.text("hello")), CrossServerChatText.render(message));
    }

    @Test
    void rejectsMalformedAdventurePayloads() {
        CrossServerChatMessage message = new CrossServerChatMessage(UUID.randomUUID(), "worker-a",
                UUID.randomUUID(), "Alice", "&a大厅", null, false, "{", 123L);

        assertThrows(RuntimeException.class, () -> CrossServerChatText.render(message));
    }
}
