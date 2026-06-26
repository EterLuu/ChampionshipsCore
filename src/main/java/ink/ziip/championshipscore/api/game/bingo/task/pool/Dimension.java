package ink.ziip.championshipscore.api.game.bingo.task.pool;

import java.util.Locale;
import java.util.Optional;

/**
 * The dimension a pooled task pulls players into. Every entry has exactly one dimension, and the
 * generator filters out entries whose dimension is disabled (nether/end off, or a card difficulty
 * that excludes the end).
 */
public enum Dimension {
    OVERWORLD("overworld"),
    NETHER("nether"),
    THE_END("the_end");

    private final String key;

    Dimension(String key) {
        this.key = key;
    }

    /** Lower-case identifier used in card-pool YAML and config blacklists. */
    public String key() {
        return key;
    }

    public static Optional<Dimension> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String normalized = raw.toLowerCase(Locale.ROOT);
        for (Dimension d : values()) {
            if (d.key.equals(normalized) || d.name().equalsIgnoreCase(normalized)) {
                return Optional.of(d);
            }
        }
        return Optional.empty();
    }
}
