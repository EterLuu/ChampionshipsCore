package ink.ziip.championshipscore.api.daily;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.configuration.ConfigurationStateExtension;
import ink.ziip.championshipscore.platform.bukkit.text.LegacyText;
import ink.ziip.championshipscore.support.BingoMessages;
import ink.ziip.championshipscore.support.MessageConfigurations;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

@ExtendWith(ConfigurationStateExtension.class)
class DailyBingoVotePresentationTest {
    @TempDir Path directory;

    @Test
    void completeTemplatesReorderTranslatedArgumentsWithoutExpandingTheirLiteralTokens()
            throws Exception {
        var config = MessageConfigurations.defaults();
        var custom = new YamlConfiguration();
        custom.set("code-text.daily-bingo-vote-controller.remix-title", "&a[%title%]");
        custom.set(
                "code-text.daily-bingo-vote-controller.remix-subtitle", "&7%description% / %name%");
        config.loadFromConfiguration(custom);
        var messages = BingoMessages.open(directory, "zh_CN");
        try {
            for (String locale : List.of("zh_CN", "en_US")) {
                messages.reload("", locale);
                var title =
                        DailyBingoVoteController.remixTitle(
                                messages, "&aName %description%", "&bDescription");
                assertEquals(
                        "[" + LegacyText.plainText(messages.tr("genesis.title")) + "]",
                        LegacyText.plainText(LegacyText.serialize(title.title())));
                assertEquals(
                        "Description / Name %description%",
                        LegacyText.plainText(LegacyText.serialize(title.subtitle())));
                assertTrue(LegacyText.serialize(title.subtitle()).contains("§bDescription"));
                assertTrue(LegacyText.serialize(title.subtitle()).contains("§aName"));
            }
            custom.set("code-text.daily-bingo-vote-controller.remix-title", "");
            custom.set("code-text.daily-bingo-vote-controller.remix-subtitle", "");
            config.loadFromConfiguration(custom);
            var empty = DailyBingoVoteController.remixTitle(messages, "name", "description");
            assertEquals("", LegacyText.serialize(empty.title()));
            assertEquals("", LegacyText.serialize(empty.subtitle()));
        } finally {
            messages.close();
        }
    }
}
