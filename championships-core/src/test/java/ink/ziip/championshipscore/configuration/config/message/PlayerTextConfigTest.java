package ink.ziip.championshipscore.configuration.config.message;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.configuration.ConfigurationStateExtension;
import ink.ziip.championshipscore.shared.presentation.PlayerTextFormat;
import ink.ziip.championshipscore.support.TestObjects;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.nio.file.Path;

@ExtendWith(ConfigurationStateExtension.class)
class PlayerTextConfigTest {
    @Test
    void currentDefaultsPreserveLegacyStylesAndReloadKeepsExplicitEmptyValues() throws Exception {
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
        assertThrows(IllegalArgumentException.class, () -> config.loadFromConfiguration(document));
        assertSame(prior, MessageConfig.playerTextFormat());
    }
}
