package ink.ziip.championshipscore.command.admin.world;

import ink.ziip.championshipscore.command.BaseSubCommand;
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
        Utils.sendAdminInfo(sender, "已加载世界 #696969(" + loaded.size() + ")");
        Set<String> loadedNames = new HashSet<>();
        for (World world : loaded) {
            loadedNames.add(world.getName());
            String main = plugin.getWorldManager().isMainWorld(world) ? " #fff566[主世界]" : "";
            sender.sendMessage(Utils.translateColorCodes("#bababa• #ededed" + world.getName() + main + " #696969— "
                    + world.getEnvironment().name().toLowerCase() + " #bababa• " + world.getPlayerCount() + " 人"));
        }

        List<String> unloaded = plugin.getWorldManager().getStoredWorldNames();
        unloaded.removeIf(loadedNames::contains);
        Utils.sendAdminInfo(sender, "磁盘未加载世界 #696969(" + unloaded.size() + ")");
        if (unloaded.isEmpty())
            sender.sendMessage(Utils.translateColorCodes("#696969无"));
        else
            sender.sendMessage(Utils.translateColorCodes("#bababa" + String.join("#696969, #bababa", unloaded)));
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String label, @NotNull String[] args) {
        return Collections.emptyList();
    }
}
