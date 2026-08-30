package ink.ziip.championshipscore.shared.presentation;

import java.util.Locale;

/** Pure duration formatting shared by Core and remote game workers. */
public final class DurationText {
    private DurationText() {
    }

    /** Formats a non-negative number of seconds as a two-digit minutes/seconds clock. */
    public static String minutesSeconds(long totalSeconds) {
        long seconds = Math.max(0L, totalSeconds);
        return String.format(Locale.ROOT, "%02d:%02d", seconds / 60L, seconds % 60L);
    }
}
