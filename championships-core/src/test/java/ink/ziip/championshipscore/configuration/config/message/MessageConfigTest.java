package ink.ziip.championshipscore.configuration.config.message;

import static ink.ziip.championshipscore.support.TestObjects.allocate;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.configuration.ConfigurationStateExtension;
import ink.ziip.championshipscore.shared.presentation.PlayerTextFormat;
import ink.ziip.championshipscore.support.MessageConfigurations;
import ink.ziip.championshipscore.support.TestObjects;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;

class MessageConfigTest {
    @ExtendWith(ConfigurationStateExtension.class)
    @Nested
    class EventStartMessageConfigCases {
        @Test
        void loadsTheCurrentMessageTemplate() throws Exception {

            var plugin = allocate(ChampionshipsCore.class);
            var logger = org.bukkit.plugin.java.JavaPlugin.class.getDeclaredField("logger");
            logger.setAccessible(true);
            logger.set(plugin, Logger.getAnonymousLogger());
            var config = new MessageConfig(plugin);
            YamlConfiguration yaml;
            try (var reader =
                    new InputStreamReader(
                            getClass().getResourceAsStream("/message.yml"),
                            StandardCharsets.UTF_8)) {
                yaml = YamlConfiguration.loadConfiguration(reader);
            }
            config.loadFromConfiguration(yaml);
            assertTrue(EventMessages.EVENT_START_UNAVAILABLE.contains("%game%"));
            assertTrue(EventMessages.EVENT_START_UNAVAILABLE.contains("%detail%"));
            assertFalse(EventMessages.EVENT_START_UNAVAILABLE.contains("宾果"));
        }
    }

    @ExtendWith(ConfigurationStateExtension.class)
    @Nested
    class LaserBoxScheduleMessageConfigCases {
        @Test
        void loadsCurrentV14ScheduleMessagesAndPreservesExplicitValues() throws Exception {

            var plugin = allocate(ChampionshipsCore.class);
            var config = new ScheduleMessageConfig(plugin);
            YamlConfiguration yaml;
            try (var reader =
                    new InputStreamReader(
                            getClass().getResourceAsStream("/schedule-message.yml"),
                            StandardCharsets.UTF_8)) {
                yaml = YamlConfiguration.loadConfiguration(reader);
            }
            assertEquals(14, yaml.getInt("dont-edit-this.version"));
            assertEquals(14, config.getLatestVersion());
            config.loadFromConfiguration(yaml);
            var rules = List.copyOf(ScheduleMessageConfig.LASER_BOX);
            var points = List.copyOf(ScheduleMessageConfig.LASER_BOX_POINTS);
            assertFalse(rules.isEmpty());
            assertFalse(points.isEmpty());
            yaml.set("laser-box", List.of());
            yaml.set("laser-box-points", List.of("custom points"));
            config.loadFromConfiguration(yaml);
            assertEquals(List.of(), ScheduleMessageConfig.LASER_BOX);
            assertEquals(List.of("custom points"), ScheduleMessageConfig.LASER_BOX_POINTS);
        }
    }

    @ExtendWith(ConfigurationStateExtension.class)
    @Nested
    class PlayerTextFormatCases {
        @Test
        void currentDefaultsPreserveLegacyStylesAndReloadKeepsExplicitEmptyValues()
                throws Exception {
            var config = new MessageConfig(TestObjects.allocate(ChampionshipsCore.class));
            var document =
                    YamlConfiguration.loadConfiguration(
                            Path.of("src/main/resources/message.yml").toFile());
            config.loadFromConfiguration(document);
            assertEquals(PlayerTextFormat.legacy(), MessageConfig.playerTextFormat());
            var prior = MessageConfig.playerTextFormat();
            document.set("presentation.player.prefix", "");
            document.set("presentation.player.chat", "%message% / %player%");
            config.loadFromConfiguration(document);
            assertEquals("", MessageConfig.playerTextFormat().prefix());
            assertEquals("%message% / %player%", MessageConfig.playerTextFormat().chat());
            assertEquals(PlayerTextFormat.legacy(), prior);
            document.set("presentation.player", null);
            config.loadFromConfiguration(document);
            assertEquals(PlayerTextFormat.legacy(), MessageConfig.playerTextFormat());
        }

        @Test
        void invalidTemplateDoesNotReplaceLastAcceptedFormat() throws Exception {
            var config = new MessageConfig(TestObjects.allocate(ChampionshipsCore.class));
            var document =
                    YamlConfiguration.loadConfiguration(
                            Path.of("src/main/resources/message.yml").toFile());
            document.set("presentation.player.identity", "%player_name%");
            config.loadFromConfiguration(document);
            var prior = MessageConfig.playerTextFormat();
            document.set("presentation.player.identity", 3);
            assertThrows(
                    IllegalArgumentException.class, () -> config.loadFromConfiguration(document));
            assertSame(prior, MessageConfig.playerTextFormat());
        }
    }

    @ExtendWith(ConfigurationStateExtension.class)
    @Nested
    class GameplayTextCases {
        @Test
        void partialOverridesEmptyListsAndRemovedKeysUseOneEffectiveSnapshot() throws Exception {
            var config = MessageConfigurations.defaults();
            var document = new YamlConfiguration();
            document.set("buildmart.material-display.separator", "");
            document.set(
                    "frostbite.item-presentation.lore", List.of("%title%", "", "%description%"));
            config.loadFromConfiguration(document);
            assertEquals(2, MessageConfig.materialDisplayFormat().columns());
            assertEquals("%material% × %count%", MessageConfig.materialDisplayFormat().entry());
            assertEquals("", MessageConfig.materialDisplayFormat().separator());
            assertEquals(
                    List.of("%title%", "", "%description%"), FrostbiteMessages.FROSTBITE_ITEM_LORE);
            document.set("frostbite.item-presentation.lore", List.of());
            config.loadFromConfiguration(document);
            assertEquals(List.of(), FrostbiteMessages.FROSTBITE_ITEM_LORE);
            config.loadFromConfiguration(new YamlConfiguration());
            assertEquals(List.of("&7%description%"), FrostbiteMessages.FROSTBITE_ITEM_LORE);
            assertEquals("  ", MessageConfig.materialDisplayFormat().separator());
        }

        @Test
        void invalidColumnsAndTemplateTypesCannotPublishAnyNewText() throws Exception {
            var config = MessageConfigurations.defaults();
            config.loadFromConfiguration(new YamlConfiguration());
            var format = MessageConfig.materialDisplayFormat();
            var header = MessageConfig.codeText("team-manager.info-header");
            for (Object invalid : List.of(0, -1, 2.5, "2", Long.MAX_VALUE)) {
                var document = new YamlConfiguration();
                document.set("buildmart.material-display.columns", invalid);
                document.set("code-text.team-manager.info-header", "new");
                assertThrows(
                        IllegalArgumentException.class,
                        () -> config.loadFromConfiguration(document),
                        String.valueOf(invalid));
                assertSame(format, MessageConfig.materialDisplayFormat());
                assertEquals(header, MessageConfig.codeText("team-manager.info-header"));
            }
            for (String key : List.of("style", "entry", "separator")) {
                var document = new YamlConfiguration();
                document.set("buildmart.material-display." + key, false);
                assertThrows(
                        IllegalArgumentException.class,
                        () -> config.loadFromConfiguration(document),
                        key);
                assertSame(format, MessageConfig.materialDisplayFormat());
            }
            var document = new YamlConfiguration();
            document.set("frostbite.item-presentation.lore", List.of(42));
            assertThrows(
                    IllegalArgumentException.class, () -> config.loadFromConfiguration(document));
            assertEquals(List.of("&7%description%"), FrostbiteMessages.FROSTBITE_ITEM_LORE);
        }
    }
}
