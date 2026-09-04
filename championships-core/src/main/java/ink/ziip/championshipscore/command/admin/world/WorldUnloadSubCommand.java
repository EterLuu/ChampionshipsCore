package ink.ziip.championshipscore.command.admin.world;

import ink.ziip.championshipscore.command.BaseSubCommand;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
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
            Utils.sendAdminError(sender, MessageConfig.ADMIN_WORLD_NOT_LOADED.replace("%world%", args[0]));
            return true;
        }
        if (plugin.getWorldManager().isMainWorld(world)) {
            Utils.sendAdminError(sender, MessageConfig.ADMIN_WORLD_MAIN_PROTECTED_UNLOAD);
            return true;
        }

        int movedPlayers = world.getPlayerCount();
        if (!plugin.getWorldManager().unloadWorld(world.getName(), true)) {
            Utils.sendAdminError(sender, MessageConfig.ADMIN_WORLD_UNLOAD_FAILED.replace("%world%", world.getName()));
            return true;
        }

        Utils.sendAdminSuccess(sender, MessageConfig.ADMIN_WORLD_UNLOADED
                .replace("%world%", world.getName())
                .replace("%moved%", movedPlayers == 0 ? "" : MessageConfig.ADMIN_WORLD_MOVED_PLAYERS.replace("%count%", String.valueOf(movedPlayers))));
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
