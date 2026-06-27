package ink.ziip.championshipscore.api.game.bingo.task.pool;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The source of objective difficulty, decoupled from the catalog. A tier list maps
 * {@linkplain PoolEntrySpec#objectiveId() objective ids} (literals or {@code *}/{@code ?} globs) to a
 * {@link Difficulty}. An unmatched objective falls back to MEDIUM.
 */
public final class Tierlist {
    public static final Tierlist EMPTY = new Tierlist(new EnumMap<>(Difficulty.class));

    private record Rule(Difficulty tier, Pattern pattern) {
    }

    private final List<Rule> rules;

    private Tierlist(Map<Difficulty, List<String>> tiers) {
        this.rules = new ArrayList<>();
        for (Difficulty tier : Difficulty.values()) {
            List<String> tokens = tiers.get(tier);
            if (tokens == null) continue;
            for (String token : tokens) {
                if (token == null || token.isBlank()) continue;
                rules.add(new Rule(tier, compile(token.trim())));
            }
        }
    }

    public static Tierlist of(Map<Difficulty, List<String>> tiers) {
        return tiers == null || tiers.isEmpty() ? EMPTY : new Tierlist(tiers);
    }

    public boolean isEmpty() {
        return rules.isEmpty();
    }

    /** The overriding tier for an objective id, or empty when no rule matches. */
    public Optional<Difficulty> resolve(String objectiveId) {
        if (objectiveId == null) return Optional.empty();
        for (Rule rule : rules) {
            if (rule.pattern().matcher(objectiveId).matches()) {
                return Optional.of(rule.tier());
            }
        }
        return Optional.empty();
    }

    private static Pattern compile(String glob) {
        StringBuilder regex = new StringBuilder("^");
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            switch (c) {
                case '*' -> regex.append(".*");
                case '?' -> regex.append('.');
                default -> {
                    if ("\\.[]{}()+-^$|".indexOf(c) >= 0) regex.append('\\');
                    regex.append(c);
                }
            }
        }
        regex.append('$');
        return Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
                | Pattern.UNICODE_CHARACTER_CLASS);
    }

    /** Convenience: normalised lookup key used when reading the YAML tier sections. */
    static Difficulty parseTier(String raw) {
        try {
            return Difficulty.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
