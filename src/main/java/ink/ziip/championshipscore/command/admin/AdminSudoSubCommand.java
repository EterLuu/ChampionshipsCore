package ink.ziip.championshipscore.command.admin;

import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.command.BaseSubCommand;
import ink.ziip.championshipscore.util.Utils;
import org.bukkit.Bukkit;
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
        super("sudo", "让指定队伍或全体玩家执行命令", "/cc admin sudo <队伍ID|all> <命令...>");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length < 2) {
            sendUsage(sender);
            return true;
        }
        String[] split = Arrays.copyOfRange(args, 1, args.length);
        String commands = String.join(" ", split);

        if (args[0].equalsIgnoreCase("all")) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.performCommand(commands);
            }
            return true;
        }

        ChampionshipTeam team = plugin.getTeamManager().getTeam(args[0]);
        if (team == null) {
            Utils.sendAdminError(sender, "队伍不存在：&#fff566" + args[0]);
            return true;
        }
        for (Player teamPlayer : team.getOnlinePlayers()) {
            teamPlayer.performCommand(commands);
        }

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> returnList = plugin.getTeamManager().getTeamNameList();
            returnList.add("all");
            return filterStartsWith(returnList, args[0]);
        }
        return Collections.emptyList();
    }
}
