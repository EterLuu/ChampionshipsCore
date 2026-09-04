package ink.ziip.championshipscore.command.admin;

import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.command.BaseSubCommand;
import ink.ziip.championshipscore.util.Utils;
import org.bukkit.Bukkit;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.ArrayList;
import java.util.List;

public class AdminTeleportationSubCommand extends BaseSubCommand {
    public AdminTeleportationSubCommand() {
        super("teleport", "将指定队伍、游戏玩家或观众传送到你的位置",
                "/cc admin teleport <队伍ID|gameplayers|spectators>");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length != 1) {
            sendUsage(sender);
            return true;
        }
        if (!(sender instanceof Player player)) {
            Utils.sendAdminError(sender, MessageConfig.COMMAND_PLAYER_ONLY);
            return true;
        }
        int teleported = 0;
        String target = args[0];
        if (target.equalsIgnoreCase("gameplayers")) {
            for (ChampionshipTeam championshipTeam : plugin.getTeamManager().getTeamList()) {
                for (Player teamPlayer : championshipTeam.getOnlinePlayers()) {
                    if (teamPlayer.teleport(player.getLocation())) teleported++;
                }
            }
        } else if (target.equalsIgnoreCase("spectators")) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (plugin.getTeamManager().getTeamByPlayer(online.getUniqueId()) == null) {
                    if (online.teleport(player.getLocation())) teleported++;
                }
            }
        } else {
            ChampionshipTeam team = plugin.getTeamManager().getTeam(target);
            if (team == null) {
                Utils.sendAdminError(sender, MessageConfig.ADMIN_TEAM_MISSING.replace("%team%", target));
                return true;
            }
            for (Player teamPlayer : team.getOnlinePlayers()) {
                if (teamPlayer.teleport(player.getLocation())) teleported++;
            }
        }

        Utils.sendAdminSuccess(sender, MessageConfig.ADMIN_TELEPORTED_PLAYERS.replace("%count%", String.valueOf(teleported)));

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> targets = new ArrayList<>(plugin.getTeamManager().getTeamNameList());
            targets.add("gameplayers");
            targets.add("spectators");
            return filterStartsWith(targets, args[0]);
        }
        return Collections.emptyList();
    }
}
