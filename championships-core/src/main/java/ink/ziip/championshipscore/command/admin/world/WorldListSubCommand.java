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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class WorldListSubCommand extends BaseSubCommand {
    public WorldListSubCommand() {
        super("list", "展示已加载和未加载的世界", "/cc admin world list");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length != 0) {
            sendUsage(sender);
            return true;
        }

        List<World> loaded = new ArrayList<>(Bukkit.getWorlds());
        loaded.sort((left, right) -> left.getName().compareToIgnoreCase(right.getName()));
        Utils.sendAdminInfo(sender, MessageConfig.ADMIN_WORLD_LIST_LOADED
                    .replace("%count%", String.valueOf(loaded.size())));
        Set<String> loadedNames = new HashSet<>();
        for (World world : loaded) {
            loadedNames.add(world.getName());
            String main = plugin.getWorldManager().isMainWorld(world)
                    ? MessageConfig.ADMIN_WORLD_MAIN_SUFFIX : "";
            sender.sendMessage(Utils.translateColorCodes(MessageConfig.ADMIN_WORLD_ROW
                    .replace("%world%", world.getName())
                    .replace("%main%", main)
                    .replace("%environment%", world.getEnvironment().name().toLowerCase())
                    .replace("%count%", String.valueOf(world.getPlayerCount()))));
        }

        List<String> unloaded = plugin.getWorldManager().getStoredWorldNames();
        unloaded.removeIf(loadedNames::contains);
        Utils.sendAdminInfo(sender, MessageConfig.ADMIN_WORLD_LIST_UNLOADED
                    .replace("%count%", String.valueOf(unloaded.size())));
        if (unloaded.isEmpty())
            sender.sendMessage(Utils.translateColorCodes(MessageConfig.ADMIN_WORLD_NONE));
        else
            sender.sendMessage(Utils.translateColorCodes(MessageConfig.ADMIN_WORLD_UNLOADED_NAMES
                    .replace("%worlds%", String.join(MessageConfig.ADMIN_WORLD_LIST_SEPARATOR, unloaded))));
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String label, @NotNull String[] args) {
        return Collections.emptyList();
    }
}
