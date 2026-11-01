package ink.ziip.championshipscore.api.test;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.protocol.BingoDifficulty;
import ink.ziip.championshipscore.protocol.BingoMode;
import ink.ziip.championshipscore.protocol.BingoRemix;
import ink.ziip.championshipscore.protocol.BingoVariantRules;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Loads a frozen Test Event profile from the Core data directory or bundled defaults. */
public final class TestEventProfileLoader {
    private final ChampionshipsCore plugin;

    public TestEventProfileLoader(ChampionshipsCore plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    public TestEventPlan load(String profile) {
        String normalized = normalizeProfile(profile);
        File file = new File(new File(plugin.getDataFolder(), "test/events"), normalized + ".yml");
        if (!file.isFile()) {
            String resource = "test/events/" + normalized + ".yml";
            if (plugin.getResource(resource) == null)
                throw new IllegalArgumentException("unknown test event profile: " + normalized);
            try {
                Files.createDirectories(file.toPath().getParent());
                plugin.saveResource(resource, false);
            } catch (IOException failure) {
                throw new IllegalStateException("could not prepare test event profile", failure);
            }
        }
        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(file);
        List<TestEventPlan.Round> rounds = new ArrayList<>();
        List<?> rawRounds = configuration.getList("rounds");
        if (rawRounds == null || rawRounds.isEmpty())
            throw new IllegalArgumentException("test event profile has no rounds: " + normalized);
        for (Object raw : rawRounds) {
            if (!(raw instanceof java.util.Map<?, ?> map))
                throw new IllegalArgumentException("test event round must be a mapping");
            Object game = map.get("game");
            Object mapName = map.get("map");
            Object difficulty = map.get("difficulty");
            Object remix = map.get("remix");
            rounds.add(
                    new TestEventPlan.Round(
                            String.valueOf(game),
                            mapName == null ? null : String.valueOf(mapName),
                            parseVariant(difficulty, remix)));
        }
        return new TestEventPlan(normalized, rounds);
    }

    private static BingoVariantRules parseVariant(Object difficulty, Object remix) {
        if (difficulty == null && remix == null) return BingoVariantRules.FIXED_POINTS;
        if (difficulty == null)
            throw new IllegalArgumentException("test event difficulty is required with remix");
        BingoDifficulty parsedDifficulty =
                BingoDifficulty.valueOf(
                        String.valueOf(difficulty).trim().toUpperCase(java.util.Locale.ROOT));
        BingoRemix parsedRemix =
                remix == null
                        ? BingoRemix.NONE
                        : BingoRemix.valueOf(
                                String.valueOf(remix).trim().toUpperCase(java.util.Locale.ROOT));
        return new BingoVariantRules(BingoMode.POINTS, parsedDifficulty, 1, parsedRemix);
    }

    private static String normalizeProfile(String profile) {
        Objects.requireNonNull(profile, "profile");
        String normalized = profile.trim().toLowerCase(java.util.Locale.ROOT);
        if (!normalized.matches("[a-z0-9_-]{1,48}"))
            throw new IllegalArgumentException("invalid test event profile: " + profile);
        return normalized;
    }
}
