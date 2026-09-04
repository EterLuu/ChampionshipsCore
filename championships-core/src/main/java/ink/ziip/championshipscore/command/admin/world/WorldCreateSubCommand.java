package ink.ziip.championshipscore.command.admin.world;

import ink.ziip.championshipscore.command.BaseSubCommand;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.util.Utils;
import ink.ziip.championshipscore.util.world.WorldManager;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class WorldCreateSubCommand extends BaseSubCommand {
    private static final List<String> ENVIRONMENTS = List.of("normal", "nether", "the_end");

    public WorldCreateSubCommand() {
        super("create", "创建或加载小游戏世界", "/cc admin world create <世界> [normal|nether|the_end]");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length < 1 || args.length > 2) {
            sendUsage(sender);
            return true;
        }

        String worldName = args[0];
        if (!WorldManager.isValidWorldName(worldName)) {
            Utils.sendAdminError(sender, MessageConfig.ADMIN_WORLD_INVALID_NAME);
            return true;
        }
        if (Bukkit.getWorld(worldName) != null) {
            Utils.sendAdminInfo(sender, MessageConfig.ADMIN_WORLD_ALREADY_LOADED.replace("%world%", worldName));
            return true;
        }

        World.Environment environment = args.length == 2 ? parseEnvironment(args[1]) : World.Environment.NORMAL;
        if (environment == null) {
            sendUsage(sender);
            return true;
        }

        WorldManager worldManager = plugin.getWorldManager();
        World.Environment bingoEnvironment = WorldManager.getBingoEnvironment(worldName);
        if (bingoEnvironment != null && args.length == 2 && environment != bingoEnvironment) {
            Utils.sendAdminError(sender, MessageConfig.ADMIN_WORLD_BINGO_ENVIRONMENT_REQUIRED
                    .replace("%world%", worldName)
                    .replace("%environment%", bingoEnvironment.name().toLowerCase()));
            return true;
        }

        boolean existed = worldManager.getWorldFolder(worldName).isDirectory();
        boolean loaded = bingoEnvironment == null
                ? worldManager.loadWorld(worldName, environment, false)
                : worldManager.loadBingoWorld(worldName, bingoEnvironment);
        if (!loaded) {
            Utils.sendAdminError(sender, MessageConfig.ADMIN_WORLD_LOAD_FAILED.replace("%world%", worldName));
            return true;
        }

        Utils.sendAdminSuccess(sender, MessageConfig.ADMIN_WORLD_CREATED
                .replace("%action%", existed ? MessageConfig.ADMIN_WORLD_ACTION_LOADED : MessageConfig.ADMIN_WORLD_ACTION_CREATED)
                .replace("%world%", worldName)
                .replace("%environment%", (bingoEnvironment == null ? environment : bingoEnvironment).name().toLowerCase()));
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> worlds = plugin.getWorldManager().getStoredWorldNames();
            worlds.removeIf(name -> Bukkit.getWorld(name) != null);
            return filterStartsWith(worlds, args[0]);
        }
        if (args.length == 2)
            return filterStartsWith(ENVIRONMENTS, args[1]);
        return Collections.emptyList();
    }

    private World.Environment parseEnvironment(String value) {
        return switch (value.toLowerCase()) {
            case "normal" -> World.Environment.NORMAL;
            case "nether" -> World.Environment.NETHER;
            case "the_end" -> World.Environment.THE_END;
            default -> null;
        };
    }
}
