package ink.ziip.championshipscore.command.team;

import ink.ziip.championshipscore.command.BaseSubCommand;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class TeamDeleteSubCommand extends BaseSubCommand {
    public TeamDeleteSubCommand() {
        super(
                "delete",
                MessageConfig.codeText("team-delete-sub-command.constructor-1"),
                MessageConfig.codeText("team-delete-sub-command.constructor-2"));
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args) {
        if (args.length != 1) {
            sendUsage(sender);
            return true;
        }
        plugin.getTeamManager()
                .deleteTeam(args[0])
                .thenAccept(
                        result -> {
                            if (result
                                    == ink.ziip.championshipscore.api.team.TeamManager
                                            .TeamDeletionResult.DELETED) {
                                sender.sendMessage(
                                        MessageConfig.TEAM_SUCCESSFULLY_DELETED.replace(
                                                "%team%", args[0]));
                                return;
                            }
                            String reason =
                                    result
                                                    == ink.ziip.championshipscore.api.team
                                                            .TeamManager.TeamDeletionResult.ACTIVE
                                            ? MessageConfig.codeText(
                                                    "team-delete-sub-command.on-command-2")
                                            : result
                                                            == ink.ziip.championshipscore.api.team
                                                                    .TeamManager.TeamDeletionResult
                                                                    .FAILED
                                                    ? MessageConfig.codeText(
                                                            "team-delete-sub-command.on-command-1")
                                                    : MessageConfig.REASON_TEAM_DOES_NOT_EXIST;
                            sender.sendMessage(
                                    MessageConfig.TEAM_DELETED_FAILED
                                            .replace("%team%", args[0])
                                            .replace("%reason%", reason));
                        });
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args) {
        if (args.length == 1) {
            List<String> returnList = plugin.getTeamManager().getTeamNameList();
            return filterStartsWith(returnList, args[0]);
        }

        return Collections.emptyList();
    }
}
