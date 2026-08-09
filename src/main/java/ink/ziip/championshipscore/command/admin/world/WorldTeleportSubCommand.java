package ink.ziip.championshipscore.command.admin.world;

import ink.ziip.championshipscore.command.BaseSubCommand;
import ink.ziip.championshipscore.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
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
            Utils.sendAdminError(sender, "该命令只能由玩家执行");
            return true;
        }

        World world = Bukkit.getWorld(args[0]);
        if (world == null) {
            Utils.sendAdminError(sender, "世界 &#fff566" + args[0] + " &#ededed未加载 &#696969• 请先使用 create");
            return true;
        }

        Location target = plugin.getGameManager().getMapTeleportLocation(world.getName());
        if (target == null)
            target = world.getSpawnLocation();
        if (!player.teleport(target)) {
            Utils.sendAdminError(sender, "无法传送至世界 &#fff566" + world.getName());
            return true;
        }
        player.setGameMode(org.bukkit.GameMode.CREATIVE);
        player.setAllowFlight(true);
        player.setFlying(true);
        Utils.sendAdminSuccess(sender, "已传送至世界 &#fff566" + world.getName() + " &#696969• 飞行已开启");
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
