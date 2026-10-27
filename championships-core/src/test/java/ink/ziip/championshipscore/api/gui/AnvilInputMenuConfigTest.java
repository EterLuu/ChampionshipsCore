package ink.ziip.championshipscore.api.gui;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.configuration.ConfigurationStateExtension;
import ink.ziip.championshipscore.configuration.config.message.GuiConfig;
import ink.ziip.championshipscore.support.TestObjects;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.nio.file.Path;
import java.util.List;

@ExtendWith(ConfigurationStateExtension.class)
class AnvilInputMenuConfigTest {
    @Test
    void bothDomainsInheritCompleteInputLoreAndPreserveExplicitOverrides() throws Exception {
        var document =
                YamlConfiguration.loadConfiguration(Path.of("src/main/resources/gui.yml").toFile());
        TestObjects.field(GuiConfig.class, "active").set(null, document);
        for (var path :
                List.of(
                        MenuId.TEAMS_INPUT.item("team-name"),
                        MenuId.TEAMS_INPUT.item("player-name"),
                        MenuId.MAP_EDITOR_INPUT.item("name"),
                        MenuId.MAP_EDITOR_INPUT.item("number"))) {
            var original = GuiConfig.itemTemplate(path, null);
            assertEquals("PAPER", original.material());
            assertEquals(List.of("%hint%"), original.lore());
            document.set(path + ".material", "DIAMOND");
            document.set(path + ".lore", List.of("before", "%value%", "%hint%", "after"));
            document.set(path + ".glint", true);
            var custom = GuiConfig.itemTemplate(path, null);
            assertEquals("DIAMOND", custom.material());
            assertEquals(4, custom.lore().size());
            assertTrue(custom.glint());
            document.set(path + ".lore", List.of());
            assertTrue(GuiConfig.itemTemplate(path, null).lore().isEmpty());
        }
    }
}
