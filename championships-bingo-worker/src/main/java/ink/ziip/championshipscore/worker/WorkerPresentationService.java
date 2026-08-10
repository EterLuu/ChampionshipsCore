package ink.ziip.championshipscore.worker;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import ink.ziip.championshipscore.protocol.BingoPresentation;
import ink.ziip.championshipscore.protocol.MatchState;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Manifest-driven rule presentation with the same 10-second first-section timing as Core. */
final class WorkerPresentationService {
    private static final int FIRST_SECTION_SECOND = 10;
    private static final int DEFAULT_INTERVAL_SECONDS = 10;
    private static final LegacyComponentSerializer AMPERSAND = LegacyComponentSerializer.builder()
            .character('&').hexColors().useUnusualXRepeatedCharacterHexFormat().build();
    private static final LegacyComponentSerializer SECTION = LegacyComponentSerializer.legacySection();
    private static final Pattern COMPACT_HEX = Pattern.compile("&?#([0-9a-fA-F]{6})");

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
        if (text == null || text.isEmpty()) return Component.empty();
        // Core's configs use both &#RRGGBB and ordinary & colour codes. Adventure's legacy parser
        // accepts the latter and the conversion below normalises the compact hex form first.
        return text.indexOf('§') >= 0 ? SECTION.deserialize(text) : AMPERSAND.deserialize(expandHex(text));
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

    /** Bingo's time is already visible in the BossBar, so the sidebar mirrors Core game stages. */
    static String sidebarStatus(MatchState state) {
        return switch (state) {
            case PREPARING, READY, ROUTING -> "预备中";
            case COUNTDOWN -> "倒计时";
            case RUNNING -> "进行中";
            case SETTLING -> "结算中";
            case FINISHED, ABORTED -> "已结束";
            default -> "等待中";
        };
    }

    private static String expandHex(String text) {
        Matcher matcher = COMPACT_HEX.matcher(text);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder expanded = new StringBuilder("&x");
            for (int index = 0; index < hex.length(); index++) expanded.append('&').append(hex.charAt(index));
            matcher.appendReplacement(result, Matcher.quoteReplacement(expanded.toString()));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
