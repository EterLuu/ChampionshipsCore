package ink.ziip.championshipscore.worker;

import ink.ziip.championshipscore.platform.bukkit.text.LegacyText;
import ink.ziip.championshipscore.protocol.BingoPresentation;
import ink.ziip.championshipscore.protocol.MatchState;
import ink.ziip.championshipscore.shared.presentation.DurationText;
import ink.ziip.championshipscore.shared.presentation.MessageTemplate;
import ink.ziip.championshipscore.shared.presentation.RuleIntroductionTimeline;

import net.kyori.adventure.text.Component;

import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Manifest-driven rule presentation with the same 10-second first-section timing as Core. */
final class WorkerPresentationService {
    private WorkerPresentationService() {}

    static int sectionAt(int elapsedSeconds, int durationSeconds, int sectionCount) {
        return RuleIntroductionTimeline.sectionAt(elapsedSeconds, durationSeconds, sectionCount);
    }

    static void sendSection(Player player, List<String> lines) {
        for (String line : lines) player.sendMessage(component(line));
    }

    static Component component(String text) {
        return LegacyText.component(text);
    }

    static Component timerTitle(
            BingoPresentation presentation, boolean pvp, int remaining, int grace) {
        boolean hasPvp = presentation.messages().containsKey("bingo.timer-pvp");
        boolean hasProtection = presentation.messages().containsKey("bingo.timer-protection");
        if (hasPvp != hasProtection)
            throw new IllegalArgumentException("Both Bingo timer templates are required");
        String time = bossBarTime(presentation, remaining);
        String protection = bossBarTime(presentation, grace);
        if (hasPvp)
            return message(
                    presentation,
                    pvp ? "bingo.timer-pvp" : "bingo.timer-protection",
                    "%time%",
                    time,
                    "%grace%",
                    protection);
        // Older persisted manifests retain their exact composition and are never modified on read.
        return message(presentation, "bingo.timer", "%time%", time)
                .append(component(legacyText("timer.separator")))
                .append(
                        pvp
                                ? message(presentation, "bingo.pvp-active")
                                : message(
                                        presentation,
                                        "bingo.pvp-protection",
                                        "%time%",
                                        protection));
    }

    static String legacyText(String key) {
        String value = LegacyTextResources.VALUES.getProperty(key);
        if (value == null) throw new IllegalArgumentException("Unknown legacy display key: " + key);
        return value;
    }

    private static final class LegacyTextResources {
        private static final java.util.Properties VALUES = load();

        private static java.util.Properties load() {
            try (var stream =
                    WorkerPresentationService.class.getResourceAsStream(
                            "/presentation/legacy-worker-text.properties")) {
                if (stream == null)
                    throw new IllegalStateException("Missing legacy worker text resource");
                var properties = new java.util.Properties();
                properties.load(
                        new java.io.InputStreamReader(
                                stream, java.nio.charset.StandardCharsets.UTF_8));
                return properties;
            } catch (java.io.IOException failure) {
                throw new IllegalStateException(
                        "Unable to load legacy worker text resource", failure);
            }
        }
    }

    static String bossBarTime(BingoPresentation presentation, long seconds) {
        // Do not mutate old manifests: their persisted hash covers the original presentation map.
        if (!presentation.messages().containsKey("duration.seconds")
                && !presentation.messages().containsKey("duration.minutes-seconds"))
            return DurationText.legacyBossBarSeconds(seconds);
        return DurationText.bossBarSeconds(
                seconds,
                presentation.message("duration.seconds"),
                presentation.message("duration.minutes-seconds"));
    }

    static Component message(BingoPresentation presentation, String key, String... replacements) {
        var values = new HashMap<String, String>();
        for (int index = 0; index + 1 < replacements.length; index += 2) {
            values.put(replacements[index], replacements[index + 1]);
        }
        String text = MessageTemplate.render(presentation.message(key), values);
        // The Core-owned prefix is an explicit decoration pass, including nested message text.
        // Ordinary arguments are never expanded again.
        return component(
                text.replace("%prefix%", presentation.messages().getOrDefault("prefix", "")));
    }

    /** Resolves one Core-owned Bingo sidebar line without invoking PlaceholderAPI on the worker. */
    static String sidebarLine(String template, String gameName, String status, int viewerTasks) {
        return MessageTemplate.render(
                template,
                Map.of(
                        "{game.name}", gameName,
                        "{game.status}", status,
                        "{viewer.tasks}", Integer.toString(viewerTasks)));
    }

    /**
     * Bingo's time is already visible in the BossBar, so the sidebar mirrors Core-owned game
     * stages.
     */
    static String sidebarStatus(BingoPresentation presentation, MatchState state) {
        return switch (state) {
            case PREPARING, READY, ROUTING -> status(presentation, "preparation");
            case COUNTDOWN -> status(presentation, "countdown");
            case RUNNING -> status(presentation, "progress");
            case SETTLING -> status(presentation, "stopping");
            case FINISHED, ABORTED -> status(presentation, "end");
            case SUSPENDED -> status(presentation, "loading");
            default -> status(presentation, "waiting");
        };
    }

    private static String status(BingoPresentation presentation, String key) {
        return presentation.message("sidebar.status." + key);
    }
}
