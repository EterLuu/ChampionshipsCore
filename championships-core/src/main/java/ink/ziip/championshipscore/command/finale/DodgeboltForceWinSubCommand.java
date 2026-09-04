package ink.ziip.championshipscore.command.finale;

import ink.ziip.championshipscore.api.game.dodgebolt.DodgeboltArea;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.command.BaseSubCommand;
import ink.ziip.championshipscore.util.Utils;
import org.bukkit.command.Command;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

final class DodgeboltForceWinSubCommand extends BaseSubCommand {
    DodgeboltForceWinSubCommand() {
        super("force-win", "裁判直接指定决赛冠军",
                "/cc finale dodgebolt force-win <场地> <队伍>");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length != 2) {
            sendUsage(sender);
            return true;
        }
        DodgeboltArea area = plugin.getGameManager().getDodgeboltManager().getArea(args[0]);
        ChampionshipTeam team = plugin.getTeamManager().getTeam(args[1]);
        if (area == null || team == null || !area.forceChampion(team))
            Utils.sendAdminError(sender, MessageConfig.FINALE_DODGEBOLT_FORCE_WIN_INVALID);
        else
            Utils.sendAdminSuccess(sender, MessageConfig.FINALE_DODGEBOLT_FORCE_WIN_SET.replace("%team%", team.getColoredName()));
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String label, @NotNull String[] args) {
        if (args.length == 1)
            return filterStartsWith(plugin.getGameManager().getDodgeboltManager().getAreaNameList(), args[0]);
        if (args.length == 2)
            return filterStartsWith(plugin.getTeamManager().getTeamNameList(), args[1]);
        return Collections.emptyList();
    }
}
