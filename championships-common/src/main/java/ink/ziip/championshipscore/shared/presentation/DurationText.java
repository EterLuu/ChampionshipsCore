package ink.ziip.championshipscore.shared.presentation;

import java.util.Locale;

/** Pure duration formatting shared by Core and remote game workers. */
public final class DurationText {
    private DurationText() {}

    /** Formats a non-negative number of seconds as a two-digit minutes/seconds clock. */
    public static String minutesSeconds(long totalSeconds) {
        long seconds = Math.max(0L, totalSeconds);
        return String.format(Locale.ROOT, "%02d:%02d", seconds / 60L, seconds % 60L);
    }

    /** BossBar durations use minutes and seconds only above the strict 60-second boundary. */
    public static String bossBarSeconds(long totalSeconds) {
        return bossBarSeconds(totalSeconds, "%seconds%秒", "%minutes%分%seconds%秒");
    }

    /** Localized forms include their units, so the surrounding title must not append a unit. */
    public static String bossBarSeconds(
            long totalSeconds, String secondsTemplate, String minutesSecondsTemplate) {
        long seconds = Math.max(0L, totalSeconds);
        String template = seconds > 60L ? minutesSecondsTemplate : secondsTemplate;
        return template.replace("%minutes%", Long.toString(seconds / 60L))
                .replace("%seconds%", Long.toString(seconds > 60L ? seconds % 60L : seconds));
    }
}
