package ink.ziip.championshipscore.platform.bukkit.text;

import ink.ziip.championshipscore.protocol.CrossServerChatMessage;
import java.lang.reflect.Proxy;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.permissions.Permissible;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChatTextTest {
    @Test
    void administratorsAndRefereesCanUseColorsAndDecorations() {
        for (String permission : Set.of("cc.admin", "cc.refuge")) {
            Component formatted = ChatMessageText.format(sender(Set.of(permission)),
                    Component.text("&c&l&nhello&r plain &#123456&o&m&khex"));
            assertEquals("hello plain hex", PlainTextComponentSerializer.plainText().serialize(formatted));
            Component hello = textNode(formatted, "hello");
            assertEquals(NamedTextColor.RED, hello.color());
            assertEquals(TextDecoration.State.TRUE, hello.decoration(TextDecoration.BOLD));
            assertEquals(TextDecoration.State.TRUE, hello.decoration(TextDecoration.UNDERLINED));
            Component plain = textNode(formatted, " plain ");
            assertNotEquals(NamedTextColor.RED, plain.color());
            assertNotEquals(TextDecoration.State.TRUE, plain.decoration(TextDecoration.BOLD));
            assertNotEquals(TextDecoration.State.TRUE, plain.decoration(TextDecoration.UNDERLINED));
            Component hex = textNode(formatted, "hex");
            assertEquals(TextColor.color(0x123456), hex.color());
            assertEquals(TextDecoration.State.TRUE, hex.decoration(TextDecoration.ITALIC));
            assertEquals(TextDecoration.State.TRUE, hex.decoration(TextDecoration.STRIKETHROUGH));
            assertEquals(TextDecoration.State.TRUE, hex.decoration(TextDecoration.OBFUSCATED));
        }
    }

    @Test
    void ordinaryPlayersKeepTheirMessageAndLiteralCodes() {
        Component message = Component.text("&c&l&nhello", NamedTextColor.GREEN);
        assertSame(message, ChatMessageText.format(sender(Set.of("cc.player")), message));
    }

    @Test
    void formattingSurvivesCrossServerChatInBothModes() {
        Component formatted = ChatMessageText.format(sender(Set.of("cc.admin")), Component.text("&c&l&nhello"));
        for (boolean daily : new boolean[]{false, true}) {
            PlayerPresentation presentation = new PlayerPresentation("&b青队", "&b", true, daily);
            CrossServerChatMessage sent = CrossServerChatText.message("core", UUID.randomUUID(), "Admin",
                    presentation, formatted, 123L);
            Component received = CrossServerChatText.render(CrossServerChatMessage.parse(sent.fields()));
            assertEquals(presentation.chatLine("Admin", formatted), received);
        }
    }

    private static Permissible sender(Set<String> permissions) {
        return (Permissible) Proxy.newProxyInstance(Permissible.class.getClassLoader(),
                new Class<?>[]{Permissible.class}, (proxy, method, args) -> {
                    if (method.getName().equals("hasPermission")) return permissions.contains(args[0]);
                    throw new AssertionError(method.getName());
                });
    }

    private static Component textNode(Component component, String content) {
        if (component instanceof TextComponent text && text.content().equals(content)) return component;
        for (Component child : component.children()) {
            Component match = textNode(child.style(child.style().merge(component.style(),
                    net.kyori.adventure.text.format.Style.Merge.Strategy.IF_ABSENT_ON_TARGET)), content);
            if (match != null) return match;
        }
        return null;
    }

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

    @Test
    void preservesSenderModeAcrossRedis() {
        for (boolean daily : new boolean[]{false, true}) {
            PlayerPresentation presentation = new PlayerPresentation("&c红队", "&c", true, daily);
            CrossServerChatMessage sent = CrossServerChatText.message("core-a", UUID.randomUUID(), "Alice",
                    presentation, Component.text("hello"), 123L);
            CrossServerChatMessage received = CrossServerChatMessage.parse(sent.fields());
            assertEquals(daily, received.daily());
            assertEquals(daily ? "[红队] Alice » hello" : "Alice <红队> » hello",
                    net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                            .serialize(CrossServerChatText.render(received)));
        }
    }

    @Test
    void acceptsVanillaNamesAndAliases() {
        assertEquals("hello team", TeamChatCommandParser.messageBody("/teammsg hello team"));
        assertEquals("hello", TeamChatCommandParser.messageBody("/TM   hello  "));
        assertEquals("hello", TeamChatCommandParser.messageBody("/minecraft:teammsg hello"));
        assertEquals("hello", TeamChatCommandParser.messageBody("/minecraft:tm hello"));
    }

    @Test
    void distinguishesMissingMessagesAndOtherCommands() {
        assertEquals("", TeamChatCommandParser.messageBody("/tm"));
        assertNull(TeamChatCommandParser.messageBody("/msg Alice hello"));
        assertNull(TeamChatCommandParser.messageBody("hello"));
    }
}
