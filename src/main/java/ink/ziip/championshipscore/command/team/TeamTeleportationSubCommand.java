package ink.ziip.championshipscore.command.team;

import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.command.BaseSubCommand;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

public class TeamTeleportationSubCommand extends BaseSubCommand {
    public TeamTeleportationSubCommand() {
        super("tphere");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length < 1)
            return true;

        Player player = Bukkit.getPlayer(sender.getName());
        if (player != null) {
            Location location = player.getLocation();

            for (String content : Arrays.copyOfRange(args, 1, args.length)) {
                ChampionshipTeam team = plugin.getTeamManager().getTeam(content);
                if (team != null) {
                    for (Player teamPlayer : team.getOnlinePlayers()) {
                        teamPlayer.teleport(location);
                    }
                }
            }
        }

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        return plugin.getTeamManager().getTeamNameList();
    }
}
