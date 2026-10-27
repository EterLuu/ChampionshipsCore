package ink.ziip.championshipscore.presentation.display;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.presentation.display.WorldTextStyles.Profile;

import org.bukkit.Color;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

class WorldTextStylesTest {
    private static YamlConfiguration bundled() {
        return YamlConfiguration.loadConfiguration(
                Path.of("src/main/resources/message.yml").toFile());
    }

    @Test
    void profilesInheritBaseAndKeepSpecificDefaultsWithoutRewritingOtherFields() {
        var defaults = bundled();
        var document = new YamlConfiguration();
        var initial = WorldTextStyles.read(document, defaults);
        assertEquals(4, initial.size());
        assertEquals(4096, initial.get(Profile.BUILD_MART_MATERIALS).lineWidth());
        assertEquals(250, initial.get(Profile.RIPTIDE_INSTRUCTION).lineWidth());
        assertNull(initial.get(Profile.RIPTIDE_COLOR_TARGET).background());
        assertEquals(Color.fromARGB(0), initial.get(Profile.ACE_RACE_NAME).background());
        document.set(WorldTextStyles.PATH + ".defaults.line-width", 300);
        document.set(WorldTextStyles.PATH + ".defaults.shadowed", true);
        document.set(Profile.RIPTIDE_INSTRUCTION.path() + ".billboard", "vertical");
        var changed = WorldTextStyles.read(document, defaults);
        assertEquals(300, changed.get(Profile.ACE_RACE_NAME).lineWidth());
        assertEquals(300, changed.get(Profile.RIPTIDE_COLOR_TARGET).lineWidth());
        assertEquals(4096, changed.get(Profile.BUILD_MART_MATERIALS).lineWidth());
        assertEquals(250, changed.get(Profile.RIPTIDE_INSTRUCTION).lineWidth());
        assertEquals(
                Display.Billboard.VERTICAL, changed.get(Profile.RIPTIDE_INSTRUCTION).billboard());
        assertTrue(changed.get(Profile.RIPTIDE_COLOR_TARGET).shadowed());
        assertEquals(200, initial.get(Profile.ACE_RACE_NAME).lineWidth());
        assertThrows(UnsupportedOperationException.class, changed::clear);
    }

    @Test
    void explicitNativeBackgroundWorldLightZeroOpacityAndRemovedOverridesRemainDistinct() {
        var defaults = bundled();
        var document = new YamlConfiguration();
        String key = Profile.BUILD_MART_MATERIALS.path();
        document.set(key + ".background", "native");
        document.set(key + ".block-light", -1);
        document.set(key + ".sky-light", -1);
        document.set(key + ".text-opacity", 0);
        document.set(key + ".shadowed", false);
        var style = WorldTextStyles.read(document, defaults).get(Profile.BUILD_MART_MATERIALS);
        assertNull(style.background());
        assertEquals(-1, style.blockLight());
        assertEquals(0, style.textOpacity());
        assertFalse(style.shadowed());
        document.set(key, null);
        style = WorldTextStyles.read(document, defaults).get(Profile.BUILD_MART_MATERIALS);
        assertEquals(Color.fromARGB(96, 0, 0, 0), style.background());
        assertEquals(15, style.blockLight());
        assertEquals(255, style.textOpacity());
    }
}
