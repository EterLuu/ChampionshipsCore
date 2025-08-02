package ink.ziip.championshipscore.command.admin;

import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.command.BaseSubCommand;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class AdminTeleportationSubCommand extends BaseSubCommand {
    public AdminTeleportationSubCommand() {
        super("teleport");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1 && sender instanceof Player player) {
            if (args[0].equalsIgnoreCase("gameplayers")) {
                for (ChampionshipTeam championshipTeam : plugin.getTeamManager().getTeamList()) {
                    championshipTeam.teleportAllPlayers(player.getLocation());
                }
            }
            if (args[0].equalsIgnoreCase("spectators")) {
                for (Player online : Bukkit.getOnlinePlayers()) {
                    if (plugin.getTeamManager().getTeamByPlayer(player.getUniqueId()) == null) {
                        online.teleport(player.getLocation());
                    }
                }
            }
        }

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1)
            return List.of("gameplayers", "spectators");
        return Collections.emptyList();
    }
}
