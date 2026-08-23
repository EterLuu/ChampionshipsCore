package ink.ziip.championshipscore.api.daily;

import org.jetbrains.annotations.NotNull;

/** Per-map time records; each player keeps the three fastest attempts, ranked by {@code durationMs}. */
public enum DailyRecordType {
    BINGO_FIRST_LINE,
    BINGO_FULL_CARD,
    ACERACE_FASTEST_LAP,
    ACERACE_FASTEST_THREE_LAPS
}
