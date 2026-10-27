package ink.ziip.championshipscore.api.game.sulfursoccer.runtime;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.configuration.ConfigurationStateExtension;
import ink.ziip.championshipscore.support.MessageConfigurations;
import ink.ziip.championshipscore.support.TestObjects;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ConfigurationStateExtension.class)
class SulfurSoccerCountdownTextTest {
    @Test
    void configuredSubtitleOnlyAppearsForTheOpeningKickoff() throws Exception {
        var area = TestObjects.allocate(SulfurSoccerArea.class);
        var config = MessageConfigurations.defaults();
        config.loadFromConfiguration(new YamlConfiguration());
        TestObjects.set(area, "openingKickoff", true);
        assertEquals("&e3", area.getFinalCountdownSubtitle("ignored", 3));
        var document = new YamlConfiguration();
        document.set("code-text.sulfur-soccer-area.kickoff-countdown-subtitle", "&d[%arg1%]");
        config.loadFromConfiguration(document);
        assertEquals("&d[2]", area.getFinalCountdownSubtitle("ignored", 2));
        TestObjects.set(area, "openingKickoff", false);
        assertEquals("", area.getFinalCountdownSubtitle("ignored", 2));
        TestObjects.set(area, "openingKickoff", true);
        document.set("code-text.sulfur-soccer-area.kickoff-countdown-subtitle", "");
        config.loadFromConfiguration(document);
        assertEquals("", area.getFinalCountdownSubtitle("ignored", 2));
    }
}
