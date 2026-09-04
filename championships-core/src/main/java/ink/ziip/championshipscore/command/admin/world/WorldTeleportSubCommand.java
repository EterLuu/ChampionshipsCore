package ink.ziip.championshipscore.command.admin.world;

import ink.ziip.championshipscore.command.BaseSubCommand;
import ink.ziip.championshipscore.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class WorldTeleportSubCommand extends BaseSubCommand {
    public WorldTeleportSubCommand() {
        super("teleport", "传送至地图或世界出生点并开启飞行", "/cc admin world teleport <世界>");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length != 1) {
            sendUsage(sender);
            return true;
        }
        if (!(sender instanceof Player player)) {
            Utils.sendAdminError(sender, MessageConfig.COMMAND_PLAYER_ONLY);
            return true;
        }

        World world = Bukkit.getWorld(args[0]);
        if (world == null) {
            Utils.sendAdminError(sender, MessageConfig.ADMIN_WORLD_NOT_LOADED_CREATE.replace("%world%", args[0]));
            return true;
        }

        Location target = plugin.getGameManager().getMapTeleportLocation(world.getName());
        if (target == null)
            target = world.getSpawnLocation();
        if (!player.teleport(target)) {
            Utils.sendAdminError(sender, MessageConfig.ADMIN_WORLD_TELEPORT_FAILED.replace("%world%", world.getName()));
            return true;
        }
        player.setGameMode(org.bukkit.GameMode.CREATIVE);
        player.setAllowFlight(true);
        player.setFlying(true);
        Utils.sendAdminSuccess(sender, MessageConfig.ADMIN_WORLD_TELEPORTED.replace("%world%", world.getName()));
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String label, @NotNull String[] args) {
        if (args.length == 1)
            return filterStartsWith(plugin.getWorldManager().getLoadedWorldNames(), args[0]);
        return Collections.emptyList();
    }
}
