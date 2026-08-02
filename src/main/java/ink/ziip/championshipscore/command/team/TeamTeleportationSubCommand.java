package ink.ziip.championshipscore.command.team;

import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.command.BaseSubCommand;
import ink.ziip.championshipscore.util.Utils;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class TeamTeleportationSubCommand extends BaseSubCommand {
    public TeamTeleportationSubCommand() {
        super("tphere", "将队伍传送到你所在的位置", "/cc team tphere <队伍ID|all>");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length != 1 || !(sender instanceof Player player)) {
            sendUsage(sender);
            return true;
        }

        Location location = player.getLocation();

        if (args[0].equalsIgnoreCase("all")) {
            for (ChampionshipTeam championshipTeam : plugin.getTeamManager().getTeamList()) {
                championshipTeam.teleportAllPlayers(location);
            }
            return true;
        }

        ChampionshipTeam team = plugin.getTeamManager().getTeam(args[0]);
        if (team == null) {
            Utils.sendAdminError(sender, "队伍不存在：&#fff566" + args[0]);
            return true;
        }
        for (Player teamPlayer : team.getOnlinePlayers()) {
            teamPlayer.teleport(location);
        }

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length < 1)
            return java.util.Collections.emptyList();
        List<String> candidates = new java.util.ArrayList<>(plugin.getTeamManager().getTeamNameList());
        candidates.add("all");
        return filterStartsWith(candidates, args[args.length - 1]);
    }
}
