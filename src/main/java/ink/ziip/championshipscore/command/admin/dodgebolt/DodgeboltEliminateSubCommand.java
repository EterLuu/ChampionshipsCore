package ink.ziip.championshipscore.command.admin.dodgebolt;

import ink.ziip.championshipscore.api.game.dodgebolt.DodgeboltArea;
import ink.ziip.championshipscore.command.BaseSubCommand;
import ink.ziip.championshipscore.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class DodgeboltEliminateSubCommand extends BaseSubCommand {
    DodgeboltEliminateSubCommand() {
        super("eliminate", "裁判判定一名选手出局", "/cc admin dodgebolt eliminate <场地> <玩家>");
    }
    @Override public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, String label, String[] args) {
        if (args.length != 2) { sendUsage(sender); return true; }
        DodgeboltArea area = plugin.getGameManager().getDodgeboltManager().getArea(args[0]);
        Player player = Bukkit.getPlayerExact(args[1]);
        if (area == null || player == null || !area.eliminate(player, false))
            Utils.sendAdminError(sender, "无法判定该玩家出局");
        else Utils.sendAdminSuccess(sender, "已判定 &#fff566" + player.getName() + " &#ededed出局");
        return true;
    }
    @Override public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, String label, String[] args) {
        if (args.length == 1) return filterStartsWith(plugin.getGameManager().getDodgeboltManager().getAreaNameList(), args[0]);
        if (args.length == 2) {
            List<String> names = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) names.add(player.getName());
            return filterStartsWith(names, args[1]);
        }
        return Collections.emptyList();
    }
}
