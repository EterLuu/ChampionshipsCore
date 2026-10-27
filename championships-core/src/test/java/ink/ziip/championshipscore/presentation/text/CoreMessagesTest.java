package ink.ziip.championshipscore.presentation.text;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.ConfigurationStateExtension;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.platform.bukkit.text.LegacyText;
import ink.ziip.championshipscore.support.MessageConfigurations;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ConfigurationStateExtension.class)
class CoreMessagesTest {
    @Test
    void configuredWrappersPreserveOriginalColorsAndAllowRemovingDefaultBold() throws Exception {
        var config = MessageConfigurations.defaults();
        var styles =
                java.util.Map.of(
                        "bingo-area.unreachable", "&e",
                        "bingo-area.sent-to", "&b",
                        "area-list-gui.delete-confirmation", "&c",
                        "parkour-tag-chaser-button-step.sign-click", "&e&l",
                        "parkour-tag-chaser-button-step.sign-become", "&d&l");
        for (var style : styles.entrySet()) {
            assertEquals(
                    LegacyText.serialize(LegacyText.component(style.getValue() + "plain")),
                    LegacyText.serialize(CoreMessages.styled(style.getKey(), "plain")));
            var custom = new YamlConfiguration();
            custom.set("code-text." + style.getKey(), "&b[%message%]");
            config.loadFromConfiguration(custom);
            assertEquals(
                    LegacyText.serialize(LegacyText.component("&b[&aValue %message%&b]")),
                    LegacyText.serialize(CoreMessages.styled(style.getKey(), "&aValue %message%")));
            custom.set("code-text." + style.getKey(), "");
            config.loadFromConfiguration(custom);
            assertEquals("", LegacyText.serialize(CoreMessages.styled(style.getKey(), "plain")));
            config.loadFromConfiguration(new YamlConfiguration());
        }
    }

    @Test
    void neutralIdentitiesRetainDefaultColorsAndReadCompleteCustomTemplates() throws Exception {
        var config = MessageConfigurations.defaults();
        var document = new YamlConfiguration();
        config.loadFromConfiguration(document);
        var team = new ChampionshipTeam(1, "Team%arg1%", "red", "&c", null) {};
        assertEquals(
                LegacyText.translateColorCodes("&fAlice"),
                CoreMessages.formatPlayerName("Alice", null));
        assertEquals(
                LegacyText.translateColorCodes("&fAlice &7<&cTeam%arg1%&7>"),
                CoreMessages.formatPlayerName("Alice", team));
        document.set("code-text.core-messages.player-name", "&a[%arg1%]");
        document.set("code-text.core-messages.player-with-team", "%arg2% / &b%arg1%");
        config.loadFromConfiguration(document);
        assertEquals(
                LegacyText.translateColorCodes("&a[Alice]"),
                CoreMessages.formatPlayerNameOnly("Alice"));
        assertEquals(
                LegacyText.translateColorCodes("&cTeam%arg1% / &bAlice%arg2%"),
                CoreMessages.formatPlayerName("Alice%arg2%", team));
        document.set("code-text.core-messages.player-with-team", "");
        config.loadFromConfiguration(document);
        assertEquals("", CoreMessages.formatPlayerName("Alice", team));
        config.loadFromConfiguration(new YamlConfiguration());
        assertEquals(
                LegacyText.translateColorCodes("&fAlice"),
                CoreMessages.formatPlayerNameOnly("Alice"));
    }

    @Test
    void dailyWrapperPreservesExplicitNestedPrefixCompatibilityAndEmptyOverrides()
            throws Exception {
        var config = MessageConfigurations.defaults();
        config.loadFromConfiguration(new YamlConfiguration());
        assertEquals(MessageConfig.DAILY_PREFIX + "hello", CoreMessages.dailyMessage("hello"));
        var document = new YamlConfiguration();
        document.set("daily.prefix", "prefix %message%");
        document.set("daily.prefixed", "%message% / %prefix%");
        config.loadFromConfiguration(document);
        assertEquals(
                "body prefix %message% / prefix %message%",
                CoreMessages.dailyMessage("body %prefix%"));
        document.set("daily.prefixed", "");
        config.loadFromConfiguration(document);
        assertEquals("", CoreMessages.dailyMessage("body"));
    }
}
