package ink.ziip.championshipscore.logging;

import ink.ziip.championshipscore.api.game.model.GameTypeEnum;
import ink.ziip.championshipscore.platform.bukkit.text.LegacyText;

import org.bukkit.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Stable Core log labels and database timestamps. */
public final class LogText {
    private LogText() {}

    public static String currentTimestamp() {
        LocalDateTime currentTime = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
        return currentTime.format(formatter);
    }

    public static String formatGameLog(
            GameTypeEnum gameType, String area, String stage, String event, String message) {
        String game = gameType == null ? "-" : gameType.toString();
        return "["
                + plainLogValue(game)
                + " - "
                + plainLogValue(area)
                + "] "
                + plainLogValue(stage)
                + " · "
                + plainLogValue(event)
                + " | "
                + plainLogValue(message);
    }

    public static String formatModuleLog(String module, String event, String message) {
        return "["
                + plainLogValue(module)
                + "] "
                + plainLogValue(event)
                + " | "
                + plainLogValue(message);
    }

    private static String plainLogValue(String value) {
        if (value == null || value.isBlank()) return "-";
        return LegacyText.plainText(value);
    }
}
