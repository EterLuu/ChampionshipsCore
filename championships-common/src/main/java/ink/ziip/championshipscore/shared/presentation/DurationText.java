package ink.ziip.championshipscore.shared.presentation;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Properties;

/** Pure duration formatting shared by Core and remote game workers. */
public final class DurationText {
    private DurationText() {}

    /** Configurable completion clock, independent of the host locale. */
    public static String clock(long seconds, String unknown, String shortForm, String longForm) {
        if (seconds < 0) return unknown;
        return MessageTemplate.render(
                seconds >= 3600 ? longForm : shortForm,
                java.util.Map.of(
                        "%hours%",
                        Long.toString(seconds / 3600),
                        "%minutes-padded%",
                        String.format(Locale.ROOT, "%02d", seconds / 60 % 60),
                        "%seconds-padded%",
                        String.format(Locale.ROOT, "%02d", seconds % 60)));
    }

    /** Formats a non-negative number of seconds as a two-digit minutes/seconds clock. */
    public static String minutesSeconds(long totalSeconds) {
        long seconds = Math.max(0L, totalSeconds);
        return String.format(Locale.ROOT, "%02d:%02d", seconds / 60L, seconds % 60L);
    }

    /** Only for persisted manifests created before Core froze configurable duration templates. */
    public static String legacyBossBarSeconds(long totalSeconds) {
        return bossBarSeconds(
                totalSeconds, LegacyTemplates.SECONDS, LegacyTemplates.MINUTES_SECONDS);
    }

    /** Localized forms include their units, so the surrounding title must not append a unit. */
    public static String bossBarSeconds(
            long totalSeconds, String secondsTemplate, String minutesSecondsTemplate) {
        long seconds = Math.max(0L, totalSeconds);
        String template = seconds > 60L ? minutesSecondsTemplate : secondsTemplate;
        return template.replace("%minutes%", Long.toString(seconds / 60L))
                .replace("%seconds%", Long.toString(seconds > 60L ? seconds % 60L : seconds));
    }

    private static final class LegacyTemplates {
        private static final Properties VALUES = load();
        private static final String SECONDS = VALUES.getProperty("seconds");
        private static final String MINUTES_SECONDS = VALUES.getProperty("minutes-seconds");

        private static Properties load() {
            try (var stream =
                    DurationText.class.getResourceAsStream(
                            "/presentation/legacy-bossbar.properties")) {
                if (stream == null)
                    throw new IllegalStateException("Missing legacy BossBar templates");
                var templates = new Properties();
                templates.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
                if (!templates.containsKey("seconds") || !templates.containsKey("minutes-seconds"))
                    throw new IllegalStateException("Incomplete legacy BossBar templates");
                return templates;
            } catch (IOException error) {
                throw new IllegalStateException("Cannot load legacy BossBar templates", error);
            }
        }
    }
}
