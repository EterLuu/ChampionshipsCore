package ink.ziip.championshipscore.api.game.area.prepare.step;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ink.ziip.championshipscore.configuration.ConfigurationStateExtension;
import ink.ziip.championshipscore.configuration.config.message.GuiConfig;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ConfigurationStateExtension.class)
class CountdownBlockDisappearanceStepTest {
    @Test
    void modeLabelReadsCurrentGuiWhileItsSavedValueAndLegacyAliasRemainStable() throws Exception {
        var mode = CountdownBlockDisappearanceStep.Mode.DOOR_EAST_WEST;
        mode.displayName();
        var config = new YamlConfiguration();
        config.set("map-editor.menus.countdown-blocks.items.east-west.title", "Reloaded mode");
        var field = GuiConfig.class.getDeclaredField("active");
        field.setAccessible(true);
        field.set(null, config);
        assertEquals("Reloaded mode", mode.displayName());
        assertEquals("DOOR_EAST_WEST", mode.value());
        assertEquals(mode, CountdownBlockDisappearanceStep.Mode.from("DOOR_HORIZONTAL"));
    }
}
