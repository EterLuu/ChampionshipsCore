package ink.ziip.championshipscore.api.game.bingo.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ink.ziip.championshipscore.platform.bukkit.text.LegacyText;

import net.kyori.adventure.text.format.TextColor;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

class MessageServiceTest {
    @TempDir Path directory;

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

    @Test
    void argumentsContainingOtherTokensRemainLiteral() {
        assertEquals(
                "{1} $1\\name / ready",
                MessageService.format("{0} / {1}", "{1} $1\\name", "ready"));
    }

    @Test
    void partialLocaleKeepsSameLanguageDefaultsAndEmptyOverridesAcrossReloads() throws Exception {
        Files.createDirectories(directory.resolve("bingo/lang"));
        var bundled =
                YamlConfiguration.loadConfiguration(
                        Path.of("src/main/resources/bingo/lang/en_US.yml").toFile());
        var partial = new YamlConfiguration();
        partial.set("dont-edit-this.version", bundled.getInt("dont-edit-this.version"));
        partial.set("card.title", "");
        partial.save(directory.resolve("bingo/lang/en_US.yml").toFile());
        var service = ink.ziip.championshipscore.support.BingoMessages.open(directory, "en_US");
        try {
            assertEquals("", service.tr("card.title"));
            assertEquals(
                    MessageService.color(bundled.getString("compass.menu_title")),
                    service.tr("compass.menu_title"));
            partial.set("card.title", null);
            partial.save(directory.resolve("bingo/lang/en_US.yml").toFile());
            service.reload("", "en_US");
            assertEquals(
                    MessageService.color(bundled.getString("card.title")),
                    service.tr("card.title"));
        } finally {
            service.close();
        }
    }
}
