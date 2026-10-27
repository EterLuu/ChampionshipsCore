package ink.ziip.championshipscore.configuration.config.message;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.configuration.ConfigurationStateExtension;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

@ExtendWith(ConfigurationStateExtension.class)
class GameplayTextConfigTest {
    @Test
    void partialOverridesEmptyListsAndRemovedKeysUseOneEffectiveSnapshot() throws Exception {
        var config = ink.ziip.championshipscore.support.MessageConfigurations.defaults();
        var document = new YamlConfiguration();
        document.set("buildmart.material-display.separator", "");
        document.set("frostbite.item-presentation.lore", List.of("%title%", "", "%description%"));
        config.loadFromConfiguration(document);
        assertEquals(2, MessageConfig.materialDisplayFormat().columns());
        assertEquals("%material% × %count%", MessageConfig.materialDisplayFormat().entry());
        assertEquals("", MessageConfig.materialDisplayFormat().separator());
        assertEquals(List.of("%title%", "", "%description%"), MessageConfig.FROSTBITE_ITEM_LORE);
        document.set("frostbite.item-presentation.lore", List.of());
        config.loadFromConfiguration(document);
        assertEquals(List.of(), MessageConfig.FROSTBITE_ITEM_LORE);
        config.loadFromConfiguration(new YamlConfiguration());
        assertEquals(List.of("&7%description%"), MessageConfig.FROSTBITE_ITEM_LORE);
        assertEquals("  ", MessageConfig.materialDisplayFormat().separator());
    }

    @Test
    void invalidColumnsAndTemplateTypesCannotPublishAnyNewText() throws Exception {
        var config = ink.ziip.championshipscore.support.MessageConfigurations.defaults();
        config.loadFromConfiguration(new YamlConfiguration());
        var format = MessageConfig.materialDisplayFormat();
        var header = MessageConfig.codeText("team-manager.info-header");
        for (Object invalid : List.of(0, -1, 2.5, "2", Long.MAX_VALUE)) {
            var document = new YamlConfiguration();
            document.set("buildmart.material-display.columns", invalid);
            document.set("code-text.team-manager.info-header", "new");
            assertThrows(
                    IllegalArgumentException.class, () -> config.loadFromConfiguration(document));
            assertSame(format, MessageConfig.materialDisplayFormat());
            assertEquals(header, MessageConfig.codeText("team-manager.info-header"));
        }
        for (String key : List.of("style", "entry", "separator")) {
            var document = new YamlConfiguration();
            document.set("buildmart.material-display." + key, false);
            assertThrows(
                    IllegalArgumentException.class, () -> config.loadFromConfiguration(document));
            assertSame(format, MessageConfig.materialDisplayFormat());
        }
        var document = new YamlConfiguration();
        document.set("frostbite.item-presentation.lore", List.of(42));
        assertThrows(IllegalArgumentException.class, () -> config.loadFromConfiguration(document));
        assertEquals(List.of("&7%description%"), MessageConfig.FROSTBITE_ITEM_LORE);
    }
}
