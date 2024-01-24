package ink.ziip.championshipscore.command.team;

import ink.ziip.championshipscore.command.BaseSubCommand;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class TeamAddSubCommand extends BaseSubCommand {
    public TeamAddSubCommand() {
        super("add");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 3) {
            // TODO add pattern
            if (plugin.getTeamManager().addTeam(args[0], args[1], args[2])) {
                String message = MessageConfig.TEAM_SUCCESSFULLY_ADDED
                        .replace("%team%", args[0]);
                sender.sendMessage(message);
            } else {
                String message = MessageConfig.TEAM_ADDED_FAILED
                        .replace("%team%", args[0])
                        .replace("%reason%", MessageConfig.REASON_TEAM_ALREADY_EXIST);
                sender.sendMessage(message);
            }
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        return Collections.emptyList();
    }
}
