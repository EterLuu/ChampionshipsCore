package ink.ziip.championshipscore.command;

import ink.ziip.championshipscore.configuration.config.message.MessageConfig;

import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The user-facing command index. Keep each canonical command and its help copy together here;
 * legacy routes deliberately do not appear in this catalogue.
 */
public final class CommandCatalog {
    private record Entry(String usage, String description) {}

    private static final List<Entry> PLAYER_COMMANDS =
            List.of(
                    new Entry(
                            "/cc spawn",
                            MessageConfig.codeText("command-catalog.player-commands-1")),
                    new Entry(
                            "/cc play [leave|leaderboard]",
                            MessageConfig.codeText("command-catalog.player-commands-2")),
                    new Entry(
                            "/cc party invite|accept|leave|disband|info",
                            MessageConfig.codeText("command-catalog.player-commands-3")),
                    new Entry(
                            MessageConfig.codeText("command-catalog.player-commands-4"),
                            MessageConfig.codeText("command-catalog.player-commands-5")),
                    new Entry(
                            MessageConfig.codeText("command-catalog.player-commands-6"),
                            MessageConfig.codeText("command-catalog.player-commands-7")),
                    new Entry(
                            MessageConfig.codeText("command-catalog.player-commands-8"),
                            MessageConfig.codeText("command-catalog.player-commands-9")),
                    new Entry(
                            "/cc rank [teamboard|playerboard|info|recap]",
                            MessageConfig.codeText("command-catalog.player-commands-10")));

    private static final List<Entry> ADMIN_COMMANDS =
            List.of(
                    new Entry(
                            "/cc switch [championship|daily]",
                            MessageConfig.codeText("command-catalog.admin-commands-1")),
                    new Entry(
                            "/cc team", MessageConfig.codeText("command-catalog.admin-commands-2")),
                    new Entry(
                            "/cc team add|delete|info|list|tphere ...",
                            MessageConfig.codeText("command-catalog.admin-commands-3")),
                    new Entry(
                            MessageConfig.codeText("command-catalog.admin-commands-4"),
                            MessageConfig.codeText("command-catalog.admin-commands-5")),
                    new Entry(
                            MessageConfig.codeText("command-catalog.admin-commands-6"),
                            MessageConfig.codeText("command-catalog.admin-commands-7")),
                    new Entry(
                            MessageConfig.codeText("command-catalog.admin-commands-8"),
                            MessageConfig.codeText("command-catalog.admin-commands-9")),
                    new Entry(
                            MessageConfig.codeText("command-catalog.admin-commands-10"),
                            MessageConfig.codeText("command-catalog.admin-commands-11")),
                    new Entry(
                            MessageConfig.codeText("command-catalog.admin-commands-12"),
                            MessageConfig.codeText("command-catalog.admin-commands-13")),
                    new Entry(
                            "/cc event export",
                            MessageConfig.codeText("command-catalog.admin-commands-14")),
                    new Entry(
                            MessageConfig.codeText("command-catalog.admin-commands-15"),
                            MessageConfig.codeText("command-catalog.admin-commands-16")),
                    new Entry(
                            "/cc event reset|undo --confirm",
                            MessageConfig.codeText("command-catalog.admin-commands-17")),
                    new Entry(
                            MessageConfig.codeText("command-catalog.admin-commands-18"),
                            MessageConfig.codeText("command-catalog.admin-commands-19")),
                    new Entry(
                            "/cc finale dodgebolt"
                                    + " pause|resume|restart-round|eliminate|force-win|stop ...",
                            MessageConfig.codeText("command-catalog.admin-commands-20")),
                    new Entry(
                            MessageConfig.codeText("command-catalog.admin-commands-21"),
                            MessageConfig.codeText("command-catalog.admin-commands-22")),
                    new Entry(
                            MessageConfig.codeText("command-catalog.admin-commands-23"),
                            MessageConfig.codeText("command-catalog.admin-commands-24")),
                    new Entry(
                            MessageConfig.codeText("command-catalog.admin-commands-25"),
                            MessageConfig.codeText("command-catalog.admin-commands-26")),
                    new Entry(
                            MessageConfig.codeText("command-catalog.admin-commands-27"),
                            MessageConfig.codeText("command-catalog.admin-commands-28")),
                    new Entry(
                            "/cc admin vote|world ...",
                            MessageConfig.codeText("command-catalog.admin-commands-29")),
                    new Entry(
                            MessageConfig.codeText("command-catalog.admin-commands-30"),
                            MessageConfig.codeText("command-catalog.admin-commands-31")),
                    new Entry(
                            MessageConfig.codeText("command-catalog.admin-commands-32"),
                            MessageConfig.codeText("command-catalog.admin-commands-33")),
                    new Entry(
                            MessageConfig.codeText("command-catalog.admin-commands-34"),
                            MessageConfig.codeText("command-catalog.admin-commands-35")),
                    new Entry(
                            "/cc admin reload|sudo|teleport|set-max-player ...",
                            MessageConfig.codeText("command-catalog.admin-commands-36")));

    private CommandCatalog() {}

    public static void send(@NotNull CommandSender sender) {
        boolean admin = sender.hasPermission(MainCommand.ADMIN_PERMISSION);
        boolean player = admin || sender.hasPermission(MainCommand.PLAYER_PERMISSION);
        if (!player && !admin) {
            sender.sendMessage(MessageConfig.NO_PERMISSION);
            return;
        }

        StringBuilder message = new StringBuilder(MessageConfig.COMMAND_CATALOG_HEADER);
        if (player) appendSection(message, MessageConfig.COMMAND_CATALOG_PLAYER, PLAYER_COMMANDS);
        if (admin) appendSection(message, MessageConfig.COMMAND_CATALOG_ADMIN, ADMIN_COMMANDS);
        sender.sendMessage(message.toString());
    }

    private static void appendSection(
            @NotNull StringBuilder message, @NotNull String title, @NotNull List<Entry> entries) {
        message.append("\n").append(title);
        for (Entry entry : entries) {
            message.append("\n")
                    .append(
                            MessageConfig.COMMAND_CATALOG_ROW
                                    .replace("%usage%", entry.usage())
                                    .replace("%description%", entry.description()));
        }
    }
}
