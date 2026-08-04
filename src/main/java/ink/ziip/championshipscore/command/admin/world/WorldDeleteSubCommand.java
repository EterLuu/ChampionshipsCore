package ink.ziip.championshipscore.command.admin.world;

import ink.ziip.championshipscore.api.game.instance.BaseGameInstance;
import ink.ziip.championshipscore.api.game.manager.BaseGameInstanceManager;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.command.BaseSubCommand;
import ink.ziip.championshipscore.util.Utils;
import ink.ziip.championshipscore.util.world.WorldManager;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class WorldDeleteSubCommand extends BaseSubCommand {
    public WorldDeleteSubCommand() {
        super("delete", "永久删除未被地图配置引用的世界", "/cc admin world delete <世界> confirm");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length != 2 || !"confirm".equalsIgnoreCase(args[1])) {
            sendUsage(sender);
            return true;
        }

        String worldName = args[0];
        WorldManager worldManager = plugin.getWorldManager();
        World world = Bukkit.getWorld(worldName);
        File worldFolder = worldManager.getWorldFolder(worldName);
        if (world == null && !worldFolder.isDirectory()) {
            Utils.sendAdminError(sender, "世界 &#fff566" + worldName + " &#ededed不存在");
            return true;
        }
        if ((world != null && worldManager.isMainWorld(world))
                || worldName.equals(worldManager.getMainWorld() == null ? "" : worldManager.getMainWorld().getName())) {
            Utils.sendAdminError(sender, "主大厅世界不能删除");
            return true;
        }
        if (WorldManager.isBingoWorldName(worldName)) {
            Utils.sendAdminError(sender, "Bingo 三维度由游戏管理，不能通过此命令删除");
            return true;
        }

        String mapOwner = findMapOwner(worldName);
        if (mapOwner != null) {
            Utils.sendAdminError(sender, "世界 &#fff566" + worldName + " &#ededed仍被地图 &#fff566" + mapOwner
                    + " &#ededed引用，不能单独删除");
            return true;
        }

        int movedPlayers = world == null ? 0 : world.getPlayerCount();
        if (world != null && !worldManager.unloadWorld(worldName, false)) {
            Utils.sendAdminError(sender, "世界 &#fff566" + worldName + " &#ededed卸载失败 &#696969• 请检查控制台");
            return true;
        }
        if (!worldManager.deleteWorldFiles(worldFolder)) {
            Utils.sendAdminError(sender, "世界 &#fff566" + worldName + " &#ededed删除失败 &#696969• 请检查控制台");
            return true;
        }

        Utils.sendAdminSuccess(sender, "已永久删除世界 &#fff566" + worldName
                + (movedPlayers == 0 ? "" : " &#696969• 已迁回 " + movedPlayers + " 名玩家"));
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> worlds = deletableWorlds();
            return filterStartsWith(worlds, args[0]);
        }
        if (args.length == 2) return filterStartsWith(List.of("confirm"), args[1]);
        return Collections.emptyList();
    }

    private List<String> deletableWorlds() {
        WorldManager worldManager = plugin.getWorldManager();
        List<String> worlds = new ArrayList<>(worldManager.getStoredWorldNames());
        worlds.removeIf(name -> WorldManager.isBingoWorldName(name) || findMapOwner(name) != null || isMainWorld(name));
        return worlds;
    }

    private boolean isMainWorld(String name) {
        World main = plugin.getWorldManager().getMainWorld();
        return main != null && main.getName().equals(name);
    }

    private @Nullable String findMapOwner(String worldName) {
        for (GameTypeEnum gameType : GameTypeEnum.values()) {
            BaseGameInstanceManager<? extends BaseGameInstance> manager = plugin.getGameManager().getAreaManager(gameType);
            if (manager == null) continue;
            for (BaseGameInstance instance : manager.getRuntimeInstances()) {
                if (worldName.equals(instance.getWorldName()))
                    return gameType.name() + ":" + instance.getGameConfig().getConfigName();
            }
        }
        return null;
    }
}
