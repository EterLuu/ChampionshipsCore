package ink.ziip.championshipscore.api.game.bingo.util;

import ink.ziip.championshipscore.platform.bukkit.text.LegacyText;
import net.kyori.adventure.text.format.TextColor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MessageServiceTest {
    @Test
    void formatsColoursInsertedByArgumentsAsWellAsTheTemplate() {
        String message = MessageService.format("&e获胜者：{0}", "&#123abcAlice");
        assertEquals("获胜者：Alice", LegacyText.plainText(message));
        assertEquals("§e获胜者：§x§1§2§3§a§b§cAlice", message);
    }

    @Test
    void nestedLocalisationArgumentsKeepTheirColour() {
        String family = MessageService.format("&#123abc羊毛");
        String name = MessageService.format("{0}", family);
        assertEquals(TextColor.color(0x123abc), LegacyText.component(name).color());
        assertEquals("羊毛", LegacyText.plainText(name));
    }
}
