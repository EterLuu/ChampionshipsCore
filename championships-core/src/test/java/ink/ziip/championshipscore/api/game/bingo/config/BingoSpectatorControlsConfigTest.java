package ink.ziip.championshipscore.api.game.bingo.config;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.configuration.ConfigurationStateExtension;
import ink.ziip.championshipscore.configuration.config.message.GuiConfig;
import ink.ziip.championshipscore.protocol.BingoPresentation;
import ink.ziip.championshipscore.shared.presentation.SpectatorControlsPresentation;
import ink.ziip.championshipscore.support.BingoMessages;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

@ExtendWith(ConfigurationStateExtension.class)
class BingoSpectatorControlsConfigTest {
    @TempDir Path directory;

    @Test
    void selectedLanguagesAndCustomGuiFreezeWithoutChangingEarlierMatches() throws Exception {
        var document =
                YamlConfiguration.loadConfiguration(Path.of("src/main/resources/gui.yml").toFile());
        var active = GuiConfig.class.getDeclaredField("active");
        active.setAccessible(true);
        active.set(null, document);
        var language = BingoMessages.open(directory, "zh_CN");
        try {
            for (String locale : List.of("zh_CN", "en_US")) {
                language.reload("", locale);
                var original = BingoSpectatorControlsConfig.snapshot();
                assertEquals(
                        "&a&l" + language.tr("spectator.teleport.name"),
                        original.items().get("teleport").title());
                assertEquals(
                        "&e&l" + language.tr("spectator.speed.feedback"), original.speedFeedback());
                assertTrue(original.items().get("speed").title().contains("%speed%"));
                assertEquals(
                        original,
                        SpectatorControlsPresentation.from(
                                new BingoPresentation(original.attributes())));
            }
            var original = BingoSpectatorControlsConfig.snapshot();
            String root = BingoSpectatorControlsConfig.PATH;
            document.set(root + ".teleport.slot", 6);
            document.set(root + ".card.slot", 4);
            document.set(root + ".teleport.material", "PAPER");
            document.set(root + ".teleport.title", "&d%name%");
            document.set(root + ".teleport.lore", List.of("first", "%hint%", "%speed%", "last"));
            document.set(root + ".teleport.glint", true);
            document.set(root + ".speed.feedback", "");
            var custom = BingoSpectatorControlsConfig.snapshot();
            assertEquals(4, custom.cardSlot());
            assertEquals(6, custom.slots().get("teleport"));
            assertEquals("PAPER", custom.items().get("teleport").material());
            assertTrue(custom.items().get("teleport").glint());
            assertEquals(4, custom.items().get("teleport").lore().size());
            assertEquals("%speed%", custom.items().get("teleport").lore().get(2));
            assertEquals("", custom.speedFeedback());
            assertEquals(0, original.slots().get("teleport"));
            assertEquals(1, original.items().get("teleport").lore().size());
            document.set(root + ".teleport.lore", List.of());
            assertTrue(
                    BingoSpectatorControlsConfig.snapshot()
                            .items()
                            .get("teleport")
                            .lore()
                            .isEmpty());
            document.set(root + ".speed.slot", 4);
            assertThrows(IllegalArgumentException.class, BingoSpectatorControlsConfig::snapshot);
        } finally {
            language.close();
        }
    }
}
