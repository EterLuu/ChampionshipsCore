package ink.ziip.championshipscore.api.game.bingo.config;

import static org.junit.jupiter.api.Assertions.*;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

class DailyVoteSettingsTest {
    @Test
    void bundledDefaultsAndMissingOverridesShareTheSameRules() {
        var defaults =
                YamlConfiguration.loadConfiguration(
                        Path.of("src/main/resources/bingo/config.yml").toFile());
        assertEquals(
                new DailyVoteSettings(20, 30, 9, true, 0.05D), DailyVoteSettings.from(defaults));
        assertEquals(
                DailyVoteSettings.from(defaults), DailyVoteSettings.from(new YamlConfiguration()));
    }

    @Test
    void reloadCreatesASeparateSnapshotAndClampsInvalidMinimums() {
        var config = new YamlConfiguration();
        var before = DailyVoteSettings.from(config);
        config.set("daily-vote.seconds", 47);
        config.set("daily-vote.genesis-seconds", 61);
        config.set("daily-vote.genesis-options", 72);
        config.set("remix.enabled", false);
        config.set("remix.chance", 2);
        assertEquals(new DailyVoteSettings(47, 61, 72, false, 1), DailyVoteSettings.from(config));
        assertEquals(new DailyVoteSettings(20, 30, 9, true, 0.05D), before);
        config.set("daily-vote.seconds", -1);
        config.set("daily-vote.genesis-seconds", 0);
        config.set("daily-vote.genesis-options", 0);
        config.set("remix.chance", -1);
        assertEquals(new DailyVoteSettings(5, 5, 1, false, 0), DailyVoteSettings.from(config));
    }
}
