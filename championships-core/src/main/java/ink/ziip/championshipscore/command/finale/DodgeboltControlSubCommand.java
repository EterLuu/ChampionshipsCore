package ink.ziip.championshipscore.command.finale;

import ink.ziip.championshipscore.api.game.dodgebolt.DodgeboltArea;
import ink.ziip.championshipscore.command.BaseSubCommand;
import ink.ziip.championshipscore.util.Utils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

final class DodgeboltControlSubCommand extends BaseSubCommand {
    DodgeboltControlSubCommand(String name, String description) {
        super(name, description, "/cc finale dodgebolt " + name + " <场地>");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length != 1) {
            sendUsage(sender);
            return true;
        }
        DodgeboltArea area = plugin.getGameManager().getDodgeboltManager().getArea(args[0]);
        if (area == null) {
            Utils.sendAdminError(sender, "找不到躲避箭场地");
            return true;
        }
        boolean success = switch (commandName) {
            case "pause" -> area.pauseMatch(null);
            case "resume" -> area.resumeMatch();
            case "restart-round" -> area.restartCurrentRound();
            case "stop" -> area.stopMatch();
            default -> false;
        };
        if (success) Utils.sendAdminSuccess(sender, "躲避箭操作已执行：&#fff566" + commandName);
        else Utils.sendAdminError(sender, "当前比赛状态不允许执行该操作");
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String label, @NotNull String[] args) {
        if (args.length == 1)
            return filterStartsWith(plugin.getGameManager().getDodgeboltManager().getAreaNameList(), args[0]);
        return Collections.emptyList();
    }
}
