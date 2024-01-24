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
        super("delete");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            // TODO add pattern
            if (plugin.getTeamManager().deleteTeam(args[0])) {
                String message = MessageConfig.TEAM_SUCCESSFULLY_DELETED
                        .replace("%team%", args[0]);
                sender.sendMessage(message);
            } else {
                String message = MessageConfig.TEAM_DELETED_FAILED
                        .replace("%team%", args[0])
                        .replace("%reason%", MessageConfig.REASON_TEAM_DOES_NOT_EXIST);
                sender.sendMessage(message);
            }
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> returnList = plugin.getTeamManager().getTeamNameList();
            returnList.removeIf(s -> s != null && !s.startsWith(args[0]));
            return returnList;
        }

        return Collections.emptyList();
    }
}
