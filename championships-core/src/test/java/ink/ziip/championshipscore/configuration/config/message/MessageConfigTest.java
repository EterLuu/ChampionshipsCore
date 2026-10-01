package ink.ziip.championshipscore.configuration.config.message;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.configuration.ConfigurationStateExtension;
import ink.ziip.championshipscore.configuration.config.BaseConfigurationFile;
import ink.ziip.championshipscore.util.Utils;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.*;

class MessageConfigTest {
    @ExtendWith(ConfigurationStateExtension.class)
    @Nested
    class EventStartMessageConfigCases {
        @Test
        void replacesTheOldBingoDefaultInExistingFilesAndPreservesCustomFeedback() throws Exception {
            var unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            var plugin = (ChampionshipsCore) ((sun.misc.Unsafe) unsafeField.get(null)).allocateInstance(ChampionshipsCore.class);
            var logger = org.bukkit.plugin.java.JavaPlugin.class.getDeclaredField("logger");
            logger.setAccessible(true);
            logger.set(plugin, Logger.getAnonymousLogger());
            var config = new MessageConfig(plugin);
            YamlConfiguration yaml;
            try (var reader = new InputStreamReader(getClass().getResourceAsStream("/message.yml"), StandardCharsets.UTF_8)) {
                yaml = YamlConfiguration.loadConfiguration(reader);
            }
            config.loadFromConfiguration(yaml);
            config.loadCustomDefaultOptions();
            String template = MessageConfig.EVENT_START_UNAVAILABLE;
            assertTrue(template.contains("%game%"));
            assertTrue(template.contains("%detail%"));
            assertFalse(template.contains("宾果"));
            var document = BaseConfigurationFile.class.getDeclaredField("configuration");
            document.setAccessible(true);
            document.set(config, yaml);
            yaml.set("event.start.unavailable", "宾果执行端尚未就绪、已有比赛运行，或参赛者当前不可用。");
            config.loadFileOptions();
            assertEquals(template, MessageConfig.EVENT_START_UNAVAILABLE);
            assertEquals(template, yaml.getString("event.start.unavailable"));

            yaml.set("event.start.unavailable", "&cCustom %game%: %detail%");
            config.loadFileOptions();
            assertEquals(Utils.translateColorCodes("&cCustom %game%: %detail%"), MessageConfig.EVENT_START_UNAVAILABLE);
        }
    }

    @ExtendWith(ConfigurationStateExtension.class)
    @Nested
    class LaserBoxScheduleMessageConfigCases {
        @Test void existingV14ConfigurationUsesNewGameDefaultsAndPreservesExplicitCustomizations() throws Exception {
            var unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); unsafeField.setAccessible(true);
            var plugin = (ChampionshipsCore) ((sun.misc.Unsafe) unsafeField.get(null)).allocateInstance(ChampionshipsCore.class);
            var config = new ScheduleMessageConfig(plugin);
            YamlConfiguration yaml;
            try (var reader = new InputStreamReader(getClass().getResourceAsStream("/schedule-message.yml"), StandardCharsets.UTF_8)) {
                yaml = YamlConfiguration.loadConfiguration(reader);
            }
            assertEquals(14, yaml.getInt("dont-edit-this.version"));
            assertEquals(14, config.getLatestVersion());
            config.loadFromConfiguration(yaml);
            config.loadCustomDefaultOptions();
            var rules = List.copyOf(ScheduleMessageConfig.LASER_BOX);
            var points = List.copyOf(ScheduleMessageConfig.LASER_BOX_POINTS);
            assertFalse(rules.isEmpty()); assertFalse(points.isEmpty());
            yaml.set("laser-box", null); yaml.set("laser-box-points", null);
            var field = ink.ziip.championshipscore.configuration.config.BaseConfigurationFile.class.getDeclaredField("configuration");
            field.setAccessible(true); field.set(config, yaml);
            config.loadFileOptions();
            assertEquals(rules, ScheduleMessageConfig.LASER_BOX);
            assertEquals(points, ScheduleMessageConfig.LASER_BOX_POINTS);
            yaml.set("laser-box", List.of()); yaml.set("laser-box-points", List.of("custom points"));
            config.loadFileOptions();
            assertEquals(List.of(), ScheduleMessageConfig.LASER_BOX);
            assertEquals(List.of("custom points"), ScheduleMessageConfig.LASER_BOX_POINTS);
        }
    }
}
