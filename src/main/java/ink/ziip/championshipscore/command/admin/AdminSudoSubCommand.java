package ink.ziip.championshipscore.command.admin;

import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.command.BaseSubCommand;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class AdminSudoSubCommand extends BaseSubCommand {
    public AdminSudoSubCommand() {
        super("sudo");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length >= 2) {

            if (args[1].equalsIgnoreCase("all")) {
                for (ChampionshipTeam championshipTeam : plugin.getTeamManager().getTeamList()) {
                    for (Player player : championshipTeam.getOnlinePlayers()) {
                        player.performCommand(args[0]);
                    }
                }
                return true;
            }

            for (String content : Arrays.copyOfRange(args, 1, args.length)) {
                ChampionshipTeam team = plugin.getTeamManager().getTeam(content);
                if (team != null) {
                    for (Player teamPlayer : team.getOnlinePlayers()) {
                        teamPlayer.performCommand(args[0]);
                    }
                }
            }
        }

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1)
            return Collections.emptyList();
        return plugin.getTeamManager().getTeamNameList();
    }
}
