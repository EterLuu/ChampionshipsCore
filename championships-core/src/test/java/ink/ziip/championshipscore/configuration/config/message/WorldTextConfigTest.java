package ink.ziip.championshipscore.configuration.config.message;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.configuration.ConfigurationStateExtension;
import ink.ziip.championshipscore.presentation.display.WorldTextStyles;
import ink.ziip.championshipscore.presentation.display.WorldTextStyles.Profile;
import ink.ziip.championshipscore.support.MessageConfigurations;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.Map;

@ExtendWith(ConfigurationStateExtension.class)
class WorldTextConfigTest {
    @Test
    void badStylesCannotPublishNewProfilesOrText() throws Exception {
        var config = MessageConfigurations.defaults();
        config.loadFromConfiguration(new YamlConfiguration());
        var original = MessageConfig.worldTextStyle(Profile.ACE_RACE_NAME);
        var originalName = MessageConfig.codeText("ace-race-area.racer-name");
        var invalid =
                Map.<String, Object>of(
                        "line-width",
                        0,
                        "shadowed",
                        "yes",
                        "background",
                        "#ffff",
                        "alignment",
                        "TOP",
                        "billboard",
                        "unknown",
                        "block-light",
                        16,
                        "text-opacity",
                        256,
                        "sky-light",
                        5);
        for (var field : invalid.entrySet()) {
            var document = new YamlConfiguration();
            document.set("code-text.ace-race-area.racer-name", "new");
            document.set(Profile.ACE_RACE_NAME.path() + "." + field.getKey(), field.getValue());
            assertThrows(
                    IllegalArgumentException.class,
                    () -> config.loadFromConfiguration(document),
                    field.getKey());
            assertSame(original, MessageConfig.worldTextStyle(Profile.ACE_RACE_NAME));
            assertEquals(originalName, MessageConfig.codeText("ace-race-area.racer-name"));
        }
        for (String root :
                List.of(
                        WorldTextStyles.PATH,
                        WorldTextStyles.PATH + ".defaults",
                        WorldTextStyles.PATH + ".profiles",
                        Profile.RIPTIDE_INSTRUCTION.path())) {
            var document = new YamlConfiguration();
            document.set(root, false);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> config.loadFromConfiguration(document),
                    root);
            assertSame(original, MessageConfig.worldTextStyle(Profile.ACE_RACE_NAME));
        }
        for (String key :
                List.of(
                        WorldTextStyles.PATH + ".unknown",
                        WorldTextStyles.PATH + ".profiles.unknown",
                        Profile.ACE_RACE_NAME.path() + ".shadwoed")) {
            var document = new YamlConfiguration();
            document.set(key, true);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> config.loadFromConfiguration(document),
                    key);
        }
        for (Object value : List.of("255", 255.5, -1, Long.MAX_VALUE)) {
            var document = new YamlConfiguration();
            document.set(Profile.ACE_RACE_NAME.path() + ".text-opacity", value);
            assertThrows(
                    IllegalArgumentException.class, () -> config.loadFromConfiguration(document));
        }
    }

    @Test
    void validReloadPublishesNewImmutableStyleWhileEarlierSnapshotKeepsItsValues()
            throws Exception {
        var config = MessageConfigurations.defaults();
        config.loadFromConfiguration(new YamlConfiguration());
        var original = MessageConfig.worldTextStyle(Profile.RIPTIDE_COLOR_TARGET);
        var custom = new YamlConfiguration();
        custom.set(Profile.RIPTIDE_COLOR_TARGET.path() + ".line-width", 120);
        custom.set("code-text.riptide-introduction-run.color-target", "&b%block% %prompt%");
        config.loadFromConfiguration(custom);
        assertEquals(200, original.lineWidth());
        assertEquals(120, MessageConfig.worldTextStyle(Profile.RIPTIDE_COLOR_TARGET).lineWidth());
        assertEquals(
                "&b%block% %prompt%",
                MessageConfig.codeText("riptide-introduction-run.color-target"));
        config.loadFromConfiguration(new YamlConfiguration());
        assertEquals(original, MessageConfig.worldTextStyle(Profile.RIPTIDE_COLOR_TARGET));
    }
}
