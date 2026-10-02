package ink.ziip.championshipscore.configuration.config.message;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.configuration.ConfigurationStateExtension;
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
        void loadsTheCurrentMessageTemplate() throws Exception {
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
            assertTrue(MessageConfig.EVENT_START_UNAVAILABLE.contains("%game%"));
            assertTrue(MessageConfig.EVENT_START_UNAVAILABLE.contains("%detail%"));
            assertFalse(MessageConfig.EVENT_START_UNAVAILABLE.contains("宾果"));
        }
    }

    @ExtendWith(ConfigurationStateExtension.class)
    @Nested
    class LaserBoxScheduleMessageConfigCases {
        @Test void loadsCurrentV14ScheduleMessagesAndPreservesExplicitValues() throws Exception {
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
            var rules = List.copyOf(ScheduleMessageConfig.LASER_BOX);
            var points = List.copyOf(ScheduleMessageConfig.LASER_BOX_POINTS);
            assertFalse(rules.isEmpty()); assertFalse(points.isEmpty());
            yaml.set("laser-box", List.of()); yaml.set("laser-box-points", List.of("custom points"));
            config.loadFromConfiguration(yaml);
            assertEquals(List.of(), ScheduleMessageConfig.LASER_BOX);
            assertEquals(List.of("custom points"), ScheduleMessageConfig.LASER_BOX_POINTS);
        }
    }
}
