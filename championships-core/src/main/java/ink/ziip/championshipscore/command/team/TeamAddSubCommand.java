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
        super("add", "添加队伍", "/cc team add <内部队伍名> <颜色名> <聊天颜色代码>");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length != 3) {
            sendUsage(sender);
            return true;
        }
        plugin.getTeamManager().addTeam(args[0], args[1], args[2]).thenAccept(created -> {
            String message = (created ? MessageConfig.TEAM_SUCCESSFULLY_ADDED
                    : MessageConfig.TEAM_ADDED_FAILED).replace("%team%", args[0]);
            if (!created) message = message.replace("%reason%", MessageConfig.REASON_TEAM_ALREADY_EXIST);
            sender.sendMessage(message);
        });
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        return Collections.emptyList();
    }
}
