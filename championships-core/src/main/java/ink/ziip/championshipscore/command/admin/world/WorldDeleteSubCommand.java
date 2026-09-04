package ink.ziip.championshipscore.command.admin.world;

import ink.ziip.championshipscore.api.game.instance.BaseGameInstance;
import ink.ziip.championshipscore.api.game.manager.BaseGameInstanceManager;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.command.BaseSubCommand;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.util.Utils;
import ink.ziip.championshipscore.util.world.WorldManager;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class WorldDeleteSubCommand extends BaseSubCommand {
    private static final long CONFIRM_WINDOW_MILLIS = 30_000L;
    private final java.util.Map<java.util.UUID, PendingDelete> pendingDeletes = new java.util.concurrent.ConcurrentHashMap<>();

    private record PendingDelete(String worldName, long expiresAt) {}

    public WorldDeleteSubCommand() {
        super("delete", "永久删除未被地图配置引用的世界", "/cc admin world delete <世界> confirm");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length < 1 || args.length > 2) {
            sendUsage(sender);
            return true;
        }

        String worldName = args[0];
        boolean confirmedArgument = args.length == 2 && "confirm".equalsIgnoreCase(args[1]);
        if (args.length == 2 && !confirmedArgument) {
            sendUsage(sender);
            return true;
        }
        WorldManager worldManager = plugin.getWorldManager();
        World world = Bukkit.getWorld(worldName);
        File worldFolder = worldManager.getWorldFolder(worldName);
        if (world == null && !worldFolder.isDirectory()) {
            Utils.sendAdminError(sender, MessageConfig.ADMIN_WORLD_MISSING.replace("%world%", worldName));
            return true;
        }
        if ((world != null && worldManager.isMainWorld(world))
                || worldName.equals(worldManager.getMainWorld() == null ? "" : worldManager.getMainWorld().getName())) {
            Utils.sendAdminError(sender, MessageConfig.ADMIN_WORLD_MAIN_PROTECTED_DELETE);
            return true;
        }
        if (WorldManager.isBingoWorldName(worldName)) {
            Utils.sendAdminError(sender, MessageConfig.ADMIN_WORLD_BINGO_PROTECTED_DELETE);
            return true;
        }

        String mapOwner = findMapOwner(worldName);
        if (mapOwner != null) {
            Utils.sendAdminError(sender, MessageConfig.ADMIN_WORLD_MAP_OWNER_PROTECTED
                    .replace("%world%", worldName).replace("%map%", mapOwner));
            return true;
        }

        if (sender instanceof Player player) {
            PendingDelete pending = pendingDeletes.get(player.getUniqueId());
            boolean confirmed = confirmedArgument && pending != null
                    && pending.worldName().equalsIgnoreCase(worldName)
                    && pending.expiresAt() >= System.currentTimeMillis();
            if (!confirmed) {
                pendingDeletes.put(player.getUniqueId(),
                        new PendingDelete(worldName, System.currentTimeMillis() + CONFIRM_WINDOW_MILLIS));
                Utils.sendAdminError(player, MessageConfig.ADMIN_WORLD_DELETE_CONFIRM
                        .replace("%world%", worldName)
                        .replace("%command%", MessageConfig.ADMIN_WORLD_DELETE_COMMAND.replace("%world%", worldName)));
                return true;
            }
            pendingDeletes.remove(player.getUniqueId());
        } else if (!confirmedArgument) {
            sendUsage(sender);
            return true;
        }

        int movedPlayers = world == null ? 0 : world.getPlayerCount();
        if (world != null && !worldManager.unloadWorld(worldName, false)) {
            Utils.sendAdminError(sender, MessageConfig.ADMIN_WORLD_UNLOAD_FAILED.replace("%world%", worldName));
            return true;
        }
        if (!worldManager.deleteWorldFiles(worldFolder)) {
            Utils.sendAdminError(sender, MessageConfig.ADMIN_WORLD_DELETE_FAILED.replace("%world%", worldName));
            return true;
        }

        Utils.sendAdminSuccess(sender, MessageConfig.ADMIN_WORLD_DELETED
                .replace("%world%", worldName)
                .replace("%moved%", movedPlayers == 0 ? "" : MessageConfig.ADMIN_WORLD_MOVED_PLAYERS.replace("%count%", String.valueOf(movedPlayers))));
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> worlds = allKnownWorlds();
            return filterStartsWith(worlds, args[0]);
        }
        if (args.length == 2) return filterStartsWith(List.of("confirm"), args[1]);
        return Collections.emptyList();
    }

    private List<String> allKnownWorlds() {
        WorldManager worldManager = plugin.getWorldManager();
        java.util.Set<String> allWorlds = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        allWorlds.addAll(worldManager.getStoredWorldNames());
        Bukkit.getWorlds().forEach(world -> allWorlds.add(world.getName()));
        return new ArrayList<>(allWorlds);
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
