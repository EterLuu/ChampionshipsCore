package ink.ziip.championshipscore.worker;

import ink.ziip.championshipscore.platform.bukkit.text.LegacyText;
import ink.ziip.championshipscore.protocol.BingoPresentation;
import ink.ziip.championshipscore.protocol.MatchState;
import ink.ziip.championshipscore.shared.presentation.RuleIntroductionTimeline;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.List;

/** Manifest-driven rule presentation with the same 10-second first-section timing as Core. */
final class WorkerPresentationService {
    private WorkerPresentationService() {
    }

    static int sectionAt(int elapsedSeconds, int durationSeconds, int sectionCount) {
        return RuleIntroductionTimeline.sectionAt(elapsedSeconds, durationSeconds, sectionCount);
    }

    static void sendSection(Player player, List<String> lines) {
        for (String line : lines) player.sendMessage(component(line));
    }

    static Component component(String text) {
        return LegacyText.component(text);
    }

    static Component message(BingoPresentation presentation, String key, String... replacements) {
        String text = presentation.message(key);
        for (int index = 0; index + 1 < replacements.length; index += 2) {
            text = text.replace(replacements[index], replacements[index + 1]);
        }
        return component(text);
    }

    /** Resolves one Core-owned Bingo sidebar line without invoking PlaceholderAPI on the worker. */
    static String sidebarLine(String template, String gameName, String status, int viewerTasks) {
        return template.replace("{game.name}", gameName)
                .replace("{game.status}", status)
                .replace("{viewer.tasks}", Integer.toString(viewerTasks));
    }

    /** Bingo's time is already visible in the BossBar, so the sidebar mirrors Core-owned game stages. */
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
