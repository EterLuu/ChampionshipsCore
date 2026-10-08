package ink.ziip.championshipscore.platform.bukkit.text;

import static ink.ziip.championshipscore.platform.bukkit.text.support.TextTestFixtures.sender;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.protocol.CrossServerChatMessage;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.junit.jupiter.api.Test;

import java.util.*;

class CrossServerChatTextTest {

    @Test
    void roundTripKeepsPresentationSenderModeAndFormattedComponents() {
        Component message =
                ChatMessageText.format(sender(Set.of("cc.admin")), Component.text("&c&l&nhello"));
        for (boolean daily : new boolean[] {false, true}) {
            var presentation = new PlayerPresentation("&c红队", "&c", true, daily);
            var sent =
                    CrossServerChatText.message(
                            "core-a", UUID.randomUUID(), "Alice", presentation, message, 123L);
            var received = CrossServerChatMessage.parse(sent.fields());
            assertEquals("core-a", received.sourceInstance());
            assertEquals(presentation.label(), received.label());
            assertEquals(presentation.teamColorCode(), received.teamColorCode());
            assertEquals(presentation.activePlayer(), received.activePlayer());
            assertEquals(daily, received.daily());
            Component rendered = CrossServerChatText.render(received);
            assertEquals(presentation.chatLine("Alice", message), rendered, "daily=" + daily);
            assertEquals(
                    daily ? "[红队] Alice » hello" : "Alice <红队> » hello",
                    PlainTextComponentSerializer.plainText().serialize(rendered),
                    "daily=" + daily);
        }
    }

    @Test
    void rejectsMalformedAdventurePayloads() {
        CrossServerChatMessage message =
                new CrossServerChatMessage(
                        UUID.randomUUID(),
                        "worker-a",
                        UUID.randomUUID(),
                        "Alice",
                        "&a大厅",
                        null,
                        false,
                        "{",
                        123L);

        assertThrows(RuntimeException.class, () -> CrossServerChatText.render(message));
    }
}
