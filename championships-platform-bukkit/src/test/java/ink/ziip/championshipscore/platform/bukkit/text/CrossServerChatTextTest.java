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
    void senderFreezesCustomRichFormatAndLegacyMessagesStillRender() {
        var format =
                new ink.ziip.championshipscore.shared.presentation.PlayerTextFormat(
                        "",
                        "",
                        "&7",
                        "%player_name%",
                        "custom %message% from %player_name%",
                        "%message%");
        var rich =
                Component.translatable("item.minecraft.diamond")
                        .color(net.kyori.adventure.text.format.NamedTextColor.GREEN)
                        .clickEvent(
                                net.kyori.adventure.text.event.ClickEvent.suggestCommand("/help"))
                        .hoverEvent(Component.text("%player_name%"));
        var presentation = new PlayerPresentation("", null, false, false, format);
        var sent =
                CrossServerChatText.message(
                        "core", UUID.randomUUID(), "Alice", presentation, rich, 123L);
        var received = CrossServerChatMessage.parse(sent.fields());
        assertNotNull(received.renderedJson());
        var json = net.kyori.adventure.text.serializer.gson.GsonComponentSerializer.gson();
        assertEquals(
                json.serialize(presentation.chatLine("Alice", rich)),
                json.serialize(CrossServerChatText.render(received)));
        var legacy = new java.util.HashMap<>(sent.fields());
        legacy.remove("renderedJson");
        legacy.put("label", "Viewers");
        assertNull(CrossServerChatMessage.parse(legacy).renderedJson());
        assertTrue(
                PlainTextComponentSerializer.plainText()
                        .serialize(CrossServerChatText.render(CrossServerChatMessage.parse(legacy)))
                        .startsWith("Alice <Viewers> » "));
        var invalid = new java.util.HashMap<>(sent.fields());
        invalid.put("renderedJson", "{");
        assertThrows(
                RuntimeException.class,
                () -> CrossServerChatText.render(CrossServerChatMessage.parse(invalid)));
        invalid.put("renderedJson", " ");
        assertThrows(IllegalArgumentException.class, () -> CrossServerChatMessage.parse(invalid));
    }

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
            assertEquals(
                    net.kyori.adventure.text.serializer.gson.GsonComponentSerializer.gson()
                            .serialize(presentation.chatLine("Alice", message)),
                    net.kyori.adventure.text.serializer.gson.GsonComponentSerializer.gson()
                            .serialize(rendered),
                    "daily=" + daily);
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
