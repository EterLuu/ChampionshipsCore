package ink.ziip.championshipscore.configuration.config;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.api.game.model.GameTypeEnum;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class DailyGameSettingsTest {
    @Test
    void reloadPreservesBundledDefaultsAndDoesNotMutateThePreviousSnapshot() {
        var config = new YamlConfiguration();
        var defaults = new YamlConfiguration();
        String path = "daily.games.AceRace.";
        defaults.set(path + "team-size", 2);
        defaults.set(path + "teams", 4);
        defaults.set(path + "max-players", 8);
        defaults.set(path + "concurrent-instances", 3);
        config.set(path + "min-players", 3);
        var first = DailyGameSettings.read(config, defaults);
        config.set(path + "min-players", 6);
        var second = DailyGameSettings.read(config, defaults);
        assertEquals(3, first.get(GameTypeEnum.AceRace).rules().minPlayers());
        assertEquals(6, second.get(GameTypeEnum.AceRace).rules().minPlayers());
        assertEquals(8, second.get(GameTypeEnum.AceRace).rules().maxPlayers());
        assertEquals(3, second.get(GameTypeEnum.AceRace).concurrentInstances());
        assertThrows(UnsupportedOperationException.class, first::clear);
    }

    @Test
    void laserShowdownEnforcesSoloTeamsAndPlayerLimits() {
        var yaml = new YamlConfiguration();
        String path = "daily.games.LaserShowdown.";
        yaml.set(path + "min-players", 1);
        yaml.set(path + "max-players", 100);
        yaml.set(path + "team-size", 4);
        yaml.set(path + "teams", 100);
        yaml.set(path + "countdown-seconds", 60);
        var rules = DailyGameSettings.read(yaml, null).get(GameTypeEnum.LaserShowdown).rules();
        assertEquals(2, rules.minPlayers());
        assertEquals(16, rules.maxPlayers());
        assertEquals(16, rules.teams());
        assertEquals(1, rules.teamSize());
        yaml.set(path + "teams", 1);
        yaml.set(path + "max-players", 1);
        assertEquals(
                2,
                DailyGameSettings.read(yaml, null).get(GameTypeEnum.LaserShowdown).rules().teams());
        assertEquals(
                2,
                DailyGameSettings.read(yaml, null)
                        .get(GameTypeEnum.LaserShowdown)
                        .rules()
                        .minPlayers());
    }
}
