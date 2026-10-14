package ink.ziip.championshipscore.command.rank;

import ink.ziip.championshipscore.command.BaseSubCommand;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class TeamBoardSubCommand extends BaseSubCommand {
    public TeamBoardSubCommand() {
        super(
                "teamboard",
                MessageConfig.codeText("team-board-sub-command.constructor-1"),
                "/cc rank teamboard");
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args) {
        if (args.length != 0) {
            sendUsage(sender);
            return true;
        }
        sender.sendMessage(plugin.getRankManager().getTeamRankString());
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args) {
        return Collections.emptyList();
    }
}
