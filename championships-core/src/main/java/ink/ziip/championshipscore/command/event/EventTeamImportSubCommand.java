package ink.ziip.championshipscore.command.event;

import ink.ziip.championshipscore.api.event.EventStateStore;
import ink.ziip.championshipscore.api.event.EventTeamImport;
import ink.ziip.championshipscore.api.team.entry.TeamImportEntry;
import ink.ziip.championshipscore.command.BaseSubCommand;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.presentation.text.CoreMessages;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public final class EventTeamImportSubCommand extends BaseSubCommand {
    public EventTeamImportSubCommand() {
        super(
                "import",
                MessageConfig.codeText("event-team-import-sub-command.constructor-1"),
                MessageConfig.codeText("event-team-import-sub-command.constructor-2"));
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args) {
        if (args.length != 2 || !args[1].equalsIgnoreCase("--confirm")) {
            sendUsage(sender);
            return true;
        }
        CoreMessages.sendAdminInfo(sender, MessageConfig.EVENT_IMPORT_VALIDATING);
        Bukkit.getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
                            try {
                                EventTeamImport imported =
                                        EventCommandSupport.webClient().fetchTeamImport(args[0]);
                                List<TeamImportEntry> teams =
                                        EventCommandSupport.validateImport(imported);
                                plugin.getTeamManager()
                                        .replaceFormalTeams(teams)
                                        .whenComplete(
                                                (replaced, failure) -> {
                                                    if (failure != null
                                                            || !Boolean.TRUE.equals(replaced)) {
                                                        error(
                                                                sender,
                                                                failure == null
                                                                        ? MessageConfig
                                                                                .EVENT_IMPORT_DATABASE_FAILED
                                                                        : failure.getMessage());
                                                        return;
                                                    }
                                                    try {
                                                        // Team import is an auxiliary operation. If
                                                        // a
                                                        // formal event is already running, keep its
                                                        // authoritative game/scoring state intact
                                                        // and
                                                        // only refresh the roster.
                                                        if (!plugin.getScheduleManager()
                                                                .hasRunningFormalEvent()) {
                                                            new EventStateStore(plugin)
                                                                    .save(imported.event());
                                                            plugin.getRankManager()
                                                                    .reloadActiveEventScoring();
                                                        }
                                                    } catch (Exception stateFailure) {
                                                        plugin.getLogger()
                                                                .warning(
                                                                        "Teams imported but active"
                                                                            + " event state could"
                                                                            + " not be saved: "
                                                                                + stateFailure
                                                                                        .getMessage());
                                                        error(
                                                                sender,
                                                                MessageConfig
                                                                        .EVENT_IMPORT_STATE_SAVE_FAILED);
                                                        return;
                                                    }
                                                    success(
                                                            sender,
                                                            MessageConfig.EVENT_IMPORT_COMPLETED
                                                                    .replace(
                                                                            "%event%",
                                                                            String.valueOf(
                                                                                    imported.event()
                                                                                            .title()))
                                                                    .replace(
                                                                            "%teams%",
                                                                            String.valueOf(
                                                                                    teams.size()))
                                                                    .replace(
                                                                            "%players%",
                                                                            String.valueOf(
                                                                                    teams.stream()
                                                                                            .mapToInt(
                                                                                                    team ->
                                                                                                            team.members()
                                                                                                                    .size())
                                                                                            .sum())));
                                                });
                            } catch (Exception failure) {
                                plugin.getLogger()
                                        .log(
                                                java.util.logging.Level.WARNING,
                                                "Unable to import cc-web event teams",
                                                failure);
                                error(sender, failure.getMessage());
                            }
                        });
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args) {
        if (args.length == 2) return complete(List.of("--confirm"), args[1]);
        return Collections.emptyList();
    }

    private void success(CommandSender sender, String message) {
        Bukkit.getScheduler().runTask(plugin, () -> CoreMessages.sendAdminSuccess(sender, message));
    }

    private void error(CommandSender sender, String message) {
        String detail =
                message == null || message.isBlank() ? MessageConfig.EVENT_UNKNOWN_ERROR : message;
        Bukkit.getScheduler()
                .runTask(
                        plugin,
                        () ->
                                CoreMessages.sendAdminError(
                                        sender,
                                        MessageConfig.EVENT_IMPORT_FAILED.replace(
                                                "%detail%", detail)));
    }
}
