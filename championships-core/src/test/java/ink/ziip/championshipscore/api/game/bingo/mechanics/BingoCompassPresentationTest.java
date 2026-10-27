package ink.ziip.championshipscore.api.game.bingo.mechanics;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.configuration.ConfigurationStateExtension;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.platform.bukkit.text.LegacyText;
import ink.ziip.championshipscore.support.MessageConfigurations;

import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ConfigurationStateExtension.class)
class BingoCompassPresentationTest {
    @Test
    void feedbackPreservesConfiguredTextColorsAndLiteralArgumentsWhileAllowingStyleOverrides()
            throws Exception {
        var config = MessageConfigurations.defaults();
        assertEquals(
                LegacyText.serialize(
                        LegacyText.component(
                                MessageConfig.MAP_EDITOR_BINGO_NO_TEAMMATES,
                                NamedTextColor.YELLOW)),
                LegacyText.serialize(BingoCompassListener.noTeammatesMessage()));

        var custom = new YamlConfiguration();
        custom.set("map-editor-bingo-teleport.no-teammates", "&aCustom %message%");
        custom.set("code-text.bingo-compass-listener.no-teammates", "&b[%message%]");
        config.loadFromConfiguration(custom);
        String rendered = LegacyText.serialize(BingoCompassListener.noTeammatesMessage());
        assertEquals("[Custom %message%]", LegacyText.plainText(rendered));
        assertTrue(rendered.contains("§aCustom %message%"));
        assertTrue(rendered.startsWith("§b["));

        custom.set("code-text.bingo-compass-listener.no-teammates", "");
        config.loadFromConfiguration(custom);
        assertEquals("", LegacyText.serialize(BingoCompassListener.noTeammatesMessage()));
        config.loadFromConfiguration(new YamlConfiguration());
        assertEquals(
                LegacyText.serialize(
                        LegacyText.component(
                                MessageConfig.MAP_EDITOR_BINGO_NO_TEAMMATES,
                                NamedTextColor.YELLOW)),
                LegacyText.serialize(BingoCompassListener.noTeammatesMessage()));
    }
}
