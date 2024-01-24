package ink.ziip.championshipscore.command.team;

import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.command.BaseSubCommand;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class TeamInfoSubCommand extends BaseSubCommand {
    public TeamInfoSubCommand() {
        super("info");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeam(args[0]);
            if (championshipTeam != null)
                sender.sendMessage(plugin.getTeamManager().getTeamInfo(championshipTeam));
            else
                sender.sendMessage(MessageConfig.REASON_TEAM_DOES_NOT_EXIST);
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
