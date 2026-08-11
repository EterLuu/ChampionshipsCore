package ink.ziip.championshipscore.worker;

import ink.ziip.championshipscore.platform.bukkit.text.LegacyText;
import net.kyori.adventure.text.Component;
import ink.ziip.championshipscore.protocol.BingoPresentation;
import ink.ziip.championshipscore.protocol.MatchState;
import org.bukkit.entity.Player;

import java.util.List;

/** Manifest-driven rule presentation with the same 10-second first-section timing as Core. */
final class WorkerPresentationService {
    private static final int FIRST_SECTION_SECOND = 10;
    private static final int DEFAULT_INTERVAL_SECONDS = 10;
    private WorkerPresentationService() {
    }

    static int sectionAt(int elapsedSeconds, int durationSeconds, int sectionCount) {
        if (sectionCount < 1 || elapsedSeconds < FIRST_SECTION_SECOND) return -1;
        int availableAfterFirst = Math.max(1, durationSeconds - FIRST_SECTION_SECOND - 1);
        int interval = sectionCount <= 1 ? DEFAULT_INTERVAL_SECONDS
                : Math.max(1, Math.min(DEFAULT_INTERVAL_SECONDS, availableAfterFirst / (sectionCount - 1)));
        int sinceFirst = elapsedSeconds - FIRST_SECTION_SECOND;
        if (sinceFirst % interval != 0) return -1;
        int section = sinceFirst / interval;
        return section < sectionCount ? section : -1;
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
            case PREPARING, READY, ROUTING -> status(presentation, "preparation", "预备中");
            case COUNTDOWN -> status(presentation, "countdown", "倒计时");
            case RUNNING -> status(presentation, "progress", "进行中");
            case SETTLING -> status(presentation, "stopping", "结算中");
            case FINISHED, ABORTED -> status(presentation, "end", "已结束");
            case SUSPENDED -> status(presentation, "loading", "加载中");
            default -> status(presentation, "waiting", "等待中");
        };
    }

    private static String status(BingoPresentation presentation, String key, String fallback) {
        return presentation.messages().getOrDefault("sidebar.status." + key, fallback);
    }
}
