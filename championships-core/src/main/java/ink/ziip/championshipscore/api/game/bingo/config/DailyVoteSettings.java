package ink.ziip.championshipscore.api.game.bingo.config;

import org.bukkit.configuration.file.YamlConfiguration;

/**
 * One reload snapshot of the lobby vote and remix settings; GUI capacity does not limit choices.
 */
public record DailyVoteSettings(
        int seconds,
        int genesisSeconds,
        int genesisOptions,
        boolean remixEnabled,
        double remixChance) {
    public static DailyVoteSettings from(YamlConfiguration config) {
        return new DailyVoteSettings(
                Math.max(5, config.getInt("daily-vote.seconds", 20)),
                Math.max(5, config.getInt("daily-vote.genesis-seconds", 30)),
                Math.max(1, config.getInt("daily-vote.genesis-options", 9)),
                config.getBoolean("remix.enabled", true),
                Math.clamp(config.getDouble("remix.chance", 0.05D), 0D, 1D));
    }
}
