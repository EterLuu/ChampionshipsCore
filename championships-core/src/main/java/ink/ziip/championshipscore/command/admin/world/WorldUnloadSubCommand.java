package ink.ziip.championshipscore.command.admin.world;

import ink.ziip.championshipscore.command.BaseSubCommand;
import ink.ziip.championshipscore.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class WorldUnloadSubCommand extends BaseSubCommand {
    public WorldUnloadSubCommand() {
        super("unload", "保存并卸载世界（不删除文件）", "/cc admin world unload <世界>");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length != 1) {
            sendUsage(sender);
            return true;
        }

        World world = Bukkit.getWorld(args[0]);
        if (world == null) {
            Utils.sendAdminError(sender, "世界 &#fff566" + args[0] + " &#ededed未加载");
            return true;
        }
        if (plugin.getWorldManager().isMainWorld(world)) {
            Utils.sendAdminError(sender, "主大厅世界不能卸载");
            return true;
        }

        int movedPlayers = world.getPlayerCount();
        if (!plugin.getWorldManager().unloadWorld(world.getName(), true)) {
            Utils.sendAdminError(sender, "世界 &#fff566" + world.getName() + " &#ededed卸载失败 &#696969• 请检查控制台");
            return true;
        }

        Utils.sendAdminSuccess(sender, "已保存并卸载世界 &#fff566" + world.getName()
                + (movedPlayers == 0 ? "" : " &#696969• 已迁回 " + movedPlayers + " 名玩家"));
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> worlds = plugin.getWorldManager().getLoadedWorldNames();
            worlds.removeIf(name -> {
                World world = Bukkit.getWorld(name);
                return world == null || plugin.getWorldManager().isMainWorld(world);
            });
            return filterStartsWith(worlds, args[0]);
        }
        return Collections.emptyList();
    }
}
