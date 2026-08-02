package ink.ziip.championshipscore.command.admin.world;

import ink.ziip.championshipscore.command.BaseSubCommand;
import ink.ziip.championshipscore.util.Utils;
import ink.ziip.championshipscore.util.scheduler.FoliaScheduler;
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
        super("teleport", "传送至世界出生点并开启飞行", "/cc admin world teleport <世界>");
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

        String worldName = args[0];
        FoliaScheduler scheduler = FoliaScheduler.global(plugin);
        scheduler.supplyGlobal(() -> {
            World world = Bukkit.getWorld(worldName);
            return world == null ? null : world.getSpawnLocation();
        }).thenAccept(target -> {
            if (target == null) {
                scheduler.runEntity(player, () -> Utils.sendAdminError(player,
                        "世界 &#fff566" + worldName + " &#ededed未加载 &#696969• 请先使用 create"));
                return;
            }
            player.teleportAsync(target).thenAccept(success -> scheduler.runEntity(player, () -> {
                if (!success) {
                    Utils.sendAdminError(player, "无法传送至世界 &#fff566" + worldName);
                    return;
                }
                player.setAllowFlight(true);
                player.setFlying(true);
                Utils.sendAdminSuccess(player,
                        "已传送至世界 &#fff566" + worldName + " &#696969• 飞行已开启");
            }));
        });
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
