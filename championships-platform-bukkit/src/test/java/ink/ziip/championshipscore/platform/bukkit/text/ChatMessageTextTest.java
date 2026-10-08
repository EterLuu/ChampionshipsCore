package ink.ziip.championshipscore.platform.bukkit.text;

import static ink.ziip.championshipscore.platform.bukkit.text.support.TextTestFixtures.sender;

import static org.junit.jupiter.api.Assertions.*;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.junit.jupiter.api.Test;

import java.util.Set;

class ChatMessageTextTest {
    @Test
    void administratorsAndRefereesCanUseColorsAndDecorations() {
        for (String permission : Set.of("cc.admin", "cc.refuge")) {
            Component formatted =
                    ChatMessageText.format(
                            sender(Set.of(permission)),
                            Component.text("&c&l&nhello&r plain &#123456&o&m&khex"));
            assertEquals(
                    "hello plain hex",
                    PlainTextComponentSerializer.plainText().serialize(formatted));
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

    private static Component textNode(Component component, String content) {
        if (component instanceof TextComponent text && text.content().equals(content))
            return component;
        for (Component child : component.children()) {
            Component match =
                    textNode(
                            child.style(
                                    child.style()
                                            .merge(
                                                    component.style(),
                                                    net.kyori.adventure.text.format.Style.Merge
                                                            .Strategy.IF_ABSENT_ON_TARGET)),
                            content);
            if (match != null) return match;
        }
        return null;
    }
}
