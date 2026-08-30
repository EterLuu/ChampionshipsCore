package ink.ziip.championshipscore.platform.bukkit.text;

import java.util.Locale;

/** Recognizes the vanilla team-chat command and aliases before Bukkit dispatches them. */
public final class TeamChatCommandParser {
    private TeamChatCommandParser() {
    }

    /** Returns the trimmed message body, an empty string for missing input, or {@code null} for another command. */
    public static String messageBody(String commandLine) {
        if (commandLine == null || commandLine.isEmpty()) return null;
        int separator = commandLine.indexOf(' ');
        String command = separator < 0 ? commandLine : commandLine.substring(0, separator);
        if (command.length() < 2 || command.charAt(0) != '/') return null;
        String label = command.substring(1).toLowerCase(Locale.ROOT);
        if (!label.equals("teammsg") && !label.equals("tm")
                && !label.equals("minecraft:teammsg") && !label.equals("minecraft:tm")) return null;
        return separator < 0 ? "" : commandLine.substring(separator + 1).trim();
    }
}
