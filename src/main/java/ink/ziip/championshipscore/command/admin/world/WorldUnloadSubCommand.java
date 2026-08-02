package ink.ziip.championshipscore.command.admin.world;

import ink.ziip.championshipscore.command.BaseSubCommand;
import ink.ziip.championshipscore.util.Utils;
import ink.ziip.championshipscore.util.scheduler.FoliaScheduler;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
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

        String worldName = args[0];
        FoliaScheduler scheduler = FoliaScheduler.global(plugin);
        scheduler.runTask(() -> {
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                reply(sender, () -> Utils.sendAdminError(sender, "世界 &#fff566" + worldName + " &#ededed未加载"));
                return;
            }
            if (plugin.getWorldManager().isMainWorld(world)) {
                reply(sender, () -> Utils.sendAdminError(sender, "主大厅世界不能卸载"));
                return;
            }
            int movedPlayers = world.getPlayerCount();
            plugin.getWorldManager().unloadWorldAsync(worldName, true).whenComplete((success, error) ->
                    reply(sender, () -> {
                        if (error != null || !Boolean.TRUE.equals(success)) {
                            Utils.sendAdminError(sender, "世界 &#fff566" + worldName
                                    + " &#ededed卸载失败 &#696969• 请检查控制台");
                        } else {
                            Utils.sendAdminSuccess(sender, "已保存并卸载世界 &#fff566" + worldName
                                    + (movedPlayers == 0 ? "" : " &#696969• 已迁回 " + movedPlayers + " 名玩家"));
                        }
                    }));
        });
        return true;
    }

    private void reply(CommandSender sender, Runnable message) {
        if (sender instanceof Player player) {
            FoliaScheduler.global(plugin).runEntity(player, message);
        } else {
            FoliaScheduler.global(plugin).runTask(message);
        }
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
