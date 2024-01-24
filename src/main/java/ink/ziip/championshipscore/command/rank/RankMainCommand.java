package ink.ziip.championshipscore.command.rank;

import ink.ziip.championshipscore.api.rank.RankManager;
import ink.ziip.championshipscore.api.team.Team;
import ink.ziip.championshipscore.command.BaseMainCommand;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public class RankMainCommand extends BaseMainCommand {
    public RankMainCommand() {
        super("rank");
        addSubCommand(new PlayerBoardSubCommand());
        addSubCommand(new TeamBoardSubCommand());
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length < 1) {
            Player player = (Player) sender;
            Team team = plugin.getTeamManager().getTeamByPlayer(player);
            if (team != null) {
                RankManager rankManager = plugin.getRankManager();
                double playerPoints = rankManager.getPlayerPoints(player);
                int playerRank = rankManager.getPlayerRank(player);
                double teamPoints = rankManager.getPlayerTeamPoints(player);
                int teamRank = rankManager.getPlayerTeamRank(player);
                String message = MessageConfig.RANK_RANK_INFO
                        .replace("%player_point%", String.valueOf(playerPoints))
                        .replace("%player_rank%", String.valueOf(playerRank))
                        .replace("%team_point%", String.valueOf(teamPoints))
                        .replace("%team_rank%", String.valueOf(teamRank));
                sender.sendMessage(message);
            } else {
                sender.sendMessage(MessageConfig.RANK_NOT_PLAYER);
            }
            return true;
        }

        BaseMainCommand subCommand = subCommandMap.get(args[0]);
        if (subCommand != null) {
            return subCommand.onCommand(sender, command, label, Arrays.copyOfRange(args, 1, args.length));
        }

        return true;
    }
}
