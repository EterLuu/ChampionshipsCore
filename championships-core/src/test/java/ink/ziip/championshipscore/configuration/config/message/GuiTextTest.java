package ink.ziip.championshipscore.configuration.config.message;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.configuration.ConfigurationStateExtension;
import ink.ziip.championshipscore.support.MessageConfigurations;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Locale;

@ExtendWith(ConfigurationStateExtension.class)
class GuiTextTest {
    @Test
    void dynamicLoreRowsUseGuiTemplatesAndPreserveLiteralTokensAndExplicitColors()
            throws Exception {
        var defaults =
                YamlConfiguration.loadConfiguration(
                        java.nio.file.Path.of("src/main/resources/gui.yml").toFile());
        var field = GuiConfig.class.getDeclaredField("active");
        field.setAccessible(true);
        var document = new YamlConfiguration();
        document.setDefaults(defaults);
        field.set(null, document);
        for (String path :
                java.util.List.of(
                        "map-editor.menus.list-editor.presentation.detail-row",
                        "daily.menus.bingo-vote.presentation.description-row")) {
            assertEquals(
                    "§7plain",
                    ink.ziip.championshipscore.platform.bukkit.text.LegacyText.serialize(
                            GuiText.row(path, "plain")));
            document.set(path, "&b[%message%]");
            assertEquals(
                    "§b[§aValue %message%§b]",
                    ink.ziip.championshipscore.platform.bukkit.text.LegacyText.serialize(
                            GuiText.row(path, "&aValue %message%")));
            document.set(path, "");
            assertEquals(
                    "",
                    ink.ziip.championshipscore.platform.bukkit.text.LegacyText.serialize(
                            GuiText.row(path, "plain")));
            document.set(path, null);
            assertEquals(
                    "§7plain",
                    ink.ziip.championshipscore.platform.bukkit.text.LegacyText.serialize(
                            GuiText.row(path, "plain")));
            document.set(path, 12);
            assertTrue(
                    GuiConfig.validate(document).stream()
                            .anyMatch(error -> error.startsWith(path)));
            document.set(path, null);
        }
    }

    @Test
    void coordinateFormatUsesIntegerValuesAndReloadableTemplate() throws Exception {
        var config = MessageConfigurations.defaults();
        var document = new YamlConfiguration();
        config.loadFromConfiguration(document);
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.FRANCE);
            assertEquals("-12, 64, 3000", GuiText.coordinate(-12, 64, 3000));
            document.set("code-text.gui-text.coordinate", "Z=%arg3% Y=%arg2% X=%arg1%");
            config.loadFromConfiguration(document);
            assertEquals("Z=3000 Y=64 X=-12", GuiText.coordinate(-12, 64, 3000));
            document.set("code-text.gui-text.coordinate", "");
            config.loadFromConfiguration(document);
            assertEquals("", GuiText.coordinate(1, 2, 3));
            document.set("code-text.gui-text.coordinate", null);
            config.loadFromConfiguration(document);
            assertEquals("1, 2, 3", GuiText.coordinate(1, 2, 3));
        } finally {
            Locale.setDefault(previous);
        }
    }
}
