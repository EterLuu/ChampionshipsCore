package ink.ziip.championshipscore.command;

import ink.ziip.championshipscore.api.object.game.ServerMode;
import ink.ziip.championshipscore.util.Utils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Runtime lobby-mode switch. It intentionally leaves event/start commands available in both modes. */
public final class SwitchSubCommand extends BaseSubCommand {
    public SwitchSubCommand() {
        super("switch", "切换正式赛事/自由游玩", "/cc switch [championship|daily]", ADMIN_PERMISSION);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length > 1) {
            sendUsage(sender);
            return true;
        }
        ServerMode current = plugin.getDailyManager().serverMode();
        ServerMode next;
        if (args.length == 0) next = current == ServerMode.DAILY ? ServerMode.CHAMPIONSHIP : ServerMode.DAILY;
        else if (args[0].equalsIgnoreCase("daily")) next = ServerMode.DAILY;
        else if (args[0].equalsIgnoreCase("championship")) next = ServerMode.CHAMPIONSHIP;
        else {
            sendUsage(sender);
            return true;
        }
        plugin.getDailyManager().switchMode(next);
        Utils.sendAdminSuccess(sender, "服务器游玩方式已切换为 &#fff566" + plugin.getDailyManager().modeDisplay());
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String label, @NotNull String[] args) {
        return args.length == 1 ? complete(List.of("championship", "daily"), args[0]) : List.of();
    }
}
