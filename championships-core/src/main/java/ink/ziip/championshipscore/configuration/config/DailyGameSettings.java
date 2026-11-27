package ink.ziip.championshipscore.configuration.config;

import ink.ziip.championshipscore.api.daily.DailyRules;
import ink.ziip.championshipscore.api.game.model.GameTypeEnum;

import org.bukkit.configuration.file.YamlConfiguration;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public record DailyGameSettings(DailyRules rules, int concurrentInstances) {
    static Map<GameTypeEnum, DailyGameSettings> read(
            YamlConfiguration config, YamlConfiguration defaults) {
        var settings = new EnumMap<GameTypeEnum, DailyGameSettings>(GameTypeEnum.class);
        for (GameTypeEnum game :
                List.of(
                        GameTypeEnum.AceRace,
                        GameTypeEnum.Bingo,
                        GameTypeEnum.ParkourWarrior,
                        GameTypeEnum.DragonEggCarnival,
                        GameTypeEnum.LaserShowdown,
                        GameTypeEnum.CraftingFeast)) {
            String path = "daily.games." + game.name() + ".";
            int min = integer(config, defaults, path + "min-players", 0);
            int max = integer(config, defaults, path + "max-players", 0);
            int size = integer(config, defaults, path + "team-size", 0);
            int teams = integer(config, defaults, path + "teams", 0);
            int countdown = integer(config, defaults, path + "countdown-seconds", 0);
            DailyRules rules =
                    game == GameTypeEnum.LaserShowdown
                            ? new DailyRules(
                                    Math.max(2, min),
                                    Math.clamp(max, 2, 16),
                                    1,
                                    Math.clamp(teams, 2, 16),
                                    countdown)
                            : new DailyRules(min, max, size, teams, countdown);
            settings.put(
                    game,
                    new DailyGameSettings(
                            rules, integer(config, defaults, path + "concurrent-instances", 1)));
        }
        return Map.copyOf(settings);
    }

    private static int integer(
            YamlConfiguration config, YamlConfiguration defaults, String path, int fallback) {
        Object value = config.get(path);
        if (value == null && defaults != null) value = defaults.get(path);
        if (value == null) return fallback;
        try {
            return (Integer) ConfigurationValueReader.convert(value, int.class);
        } catch (RuntimeException failure) {
            throw new IllegalArgumentException("Invalid integer for " + path, failure);
        }
    }
}
