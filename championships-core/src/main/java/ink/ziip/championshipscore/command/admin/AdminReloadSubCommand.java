package ink.ziip.championshipscore.command.admin;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.command.BaseSubCommand;
import ink.ziip.championshipscore.util.Utils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class AdminReloadSubCommand extends BaseSubCommand {
    public AdminReloadSubCommand() {
        super("reload", "重载插件配置", "/cc admin reload --confirm");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length != 1 || !args[0].equalsIgnoreCase("--confirm")) {
            sendUsage(sender);
            return true;
        }

        ChampionshipsCore plugin = ChampionshipsCore.getInstance();
        if (plugin.getPrepareSessionManager().hasActiveSessions()) {
            Utils.sendAdminError(sender, "仍有地图 prepare 会话进行中，不能重载");
            return true;
        }
        plugin.getScheduleManager().unload();
        plugin.getGameManager().unload();
        plugin.getConfigurationManager().reload();
        plugin.getGameManager().load();
        plugin.getVisibilityManager().reconcileAll();
        plugin.getScheduleManager().load();
        plugin.getSidebarManager().reload();
        plugin.getSidebarManager().invalidateAll();
        Utils.sendAdminSuccess(sender, "插件配置与游戏场地已重载");

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) return complete(List.of("--confirm"), args[0]);
        return Collections.emptyList();
    }
}
