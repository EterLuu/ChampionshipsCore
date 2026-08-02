package ink.ziip.championshipscore.command.game.start;

import ink.ziip.championshipscore.api.game.dodgebolt.DodgeboltArea;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.command.BaseSubCommand;
import ink.ziip.championshipscore.util.Utils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public final class DodgeboltStartSubCommand extends BaseSubCommand {
    public DodgeboltStartSubCommand() {
        super("dodgebolt", "直接开始躲避箭决赛",
                "/cc game start dodgebolt <场地> <队伍1> <队伍2>");
    }

    @Override public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                                       @NotNull String label, @NotNull String[] args) {
        if (args.length != 3) { sendUsage(sender); return true; }
        DodgeboltArea area = plugin.getGameManager().getDodgeboltManager().getArea(args[0]);
        ChampionshipTeam right = plugin.getTeamManager().getTeam(args[1]);
        ChampionshipTeam left = plugin.getTeamManager().getTeam(args[2]);
        if (area == null || right == null || left == null || right.equals(left)) {
            Utils.sendAdminError(sender, "场地或队伍无效");
            return true;
        }
        ChampionshipTeam higher = plugin.getRankManager().getCachedTeamPoints(right)
                >= plugin.getRankManager().getCachedTeamPoints(left) ? right : left;
        if (plugin.getGameManager().joinDodgeboltArea(args[0], right, left, higher, true)) {
            plugin.getGameManager().spectateDodgeboltFinal(area, right, left);
            Utils.sendAdminSuccess(sender, "躲避箭决赛已开始准备");
        } else {
            Utils.sendAdminError(sender, "启动失败，请检查配置、队员在线状态或场地占用");
        }
        return true;
    }

    @Override public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                           @NotNull Command command, @NotNull String label,
                                                           @NotNull String[] args) {
        if (args.length == 1) return filterStartsWith(plugin.getGameManager().getDodgeboltManager().getAreaNameList(), args[0]);
        if (args.length == 2) return filterStartsWith(plugin.getTeamManager().getTeamNameList(), args[1]);
        if (args.length == 3) {
            List<String> teams = plugin.getTeamManager().getTeamNameList();
            teams.removeIf(name -> name.equalsIgnoreCase(args[1]));
            return filterStartsWith(teams, args[2]);
        }
        return Collections.emptyList();
    }
}
