package ink.ziip.championshipscore.command.admin.world;

import ink.ziip.championshipscore.api.game.config.BaseGameConfig;
import ink.ziip.championshipscore.api.game.instance.BaseGameInstance;
import ink.ziip.championshipscore.api.game.manager.BaseGameInstanceManager;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
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

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class WorldRenameSubCommand extends BaseSubCommand {
    private static final List<String> ENVIRONMENTS = List.of("normal", "nether", "the_end");

    public WorldRenameSubCommand() {
        super("rename", "重命名世界并迁移关联地图配置", "/cc admin world rename <旧世界> <新世界> [normal|nether|the_end]");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length < 2 || args.length > 3) {
            sendUsage(sender);
            return true;
        }

        String oldWorldName = args[0];
        String newWorldName = args[1];
        WorldManager worldManager = plugin.getWorldManager();
        if (!WorldManager.isValidWorldName(oldWorldName) || !WorldManager.isValidWorldName(newWorldName)) {
            Utils.sendAdminError(sender, MessageConfig.ADMIN_WORLD_INVALID_NAME);
            return true;
        }
        if (oldWorldName.equalsIgnoreCase(newWorldName)) {
            Utils.sendAdminError(sender, MessageConfig.ADMIN_WORLD_SAME_NAME);
            return true;
        }
        if (Bukkit.getWorld(newWorldName) != null || worldManager.getWorldFolder(newWorldName).exists()) {
            Utils.sendAdminError(sender, MessageConfig.ADMIN_WORLD_TARGET_EXISTS.replace("%world%", newWorldName));
            return true;
        }

        World oldWorld = Bukkit.getWorld(oldWorldName);
        File oldFolder = worldManager.getWorldFolder(oldWorldName);
        if (oldWorld == null && !oldFolder.isDirectory()) {
            Utils.sendAdminError(sender, MessageConfig.ADMIN_WORLD_MISSING.replace("%world%", oldWorldName));
            return true;
        }
        if ((oldWorld != null && worldManager.isMainWorld(oldWorld))
                || oldWorldName.equals(worldManager.getMainWorld() == null ? "" : worldManager.getMainWorld().getName())) {
            Utils.sendAdminError(sender, MessageConfig.ADMIN_WORLD_MAIN_PROTECTED_RENAME);
            return true;
        }
        if (WorldManager.isBingoWorldName(oldWorldName)) {
            Utils.sendAdminError(sender, MessageConfig.ADMIN_WORLD_BINGO_PROTECTED_RENAME);
            return true;
        }
        if (plugin.getPrepareSessionManager().hasActiveSessions()) {
            Utils.sendAdminError(sender, MessageConfig.ADMIN_WORLD_PREPARE_ACTIVE_RENAME);
            return true;
        }

        World.Environment requestedEnvironment = args.length == 3 ? parseEnvironment(args[2]) : null;
        if (args.length == 3 && requestedEnvironment == null) {
            sendUsage(sender);
            return true;
        }
        if (oldWorld == null) {
            if (requestedEnvironment == null) {
                Utils.sendAdminError(sender, MessageConfig.ADMIN_WORLD_ENVIRONMENT_REQUIRED);
                return true;
            }
            if (!worldManager.loadWorld(oldWorldName, requestedEnvironment, false)) {
                Utils.sendAdminError(sender, MessageConfig.ADMIN_WORLD_LOAD_FAILED.replace("%world%", oldWorldName));
                return true;
            }
            oldWorld = Bukkit.getWorld(oldWorldName);
            if (oldWorld == null) {
                Utils.sendAdminError(sender, MessageConfig.ADMIN_WORLD_LOAD_FAILED_SIMPLE.replace("%world%", oldWorldName));
                return true;
            }
        } else if (requestedEnvironment != null && requestedEnvironment != oldWorld.getEnvironment()) {
            Utils.sendAdminError(sender, MessageConfig.ADMIN_WORLD_ENVIRONMENT_MISMATCH
                    .replace("%environment%", oldWorld.getEnvironment().name().toLowerCase()));
            return true;
        }

        Map<BaseGameConfig, Boolean> mapConfigs = new IdentityHashMap<>();
        Set<BaseGameInstanceManager<?>> owners = new LinkedHashSet<>();
        String blockedBy = collectMapOwners(oldWorldName, mapConfigs, owners);
        if (blockedBy != null) {
            Utils.sendAdminError(sender, blockedBy);
            return true;
        }

        World.Environment environment = oldWorld.getEnvironment();
        int movedPlayers = oldWorld.getPlayerCount();
        File oldTemplate = new File(new File(plugin.getDataFolder(), "maps"), oldWorldName);
        File newTemplate = new File(new File(plugin.getDataFolder(), "maps"), newWorldName);
        if (newTemplate.exists()) {
            Utils.sendAdminError(sender, MessageConfig.ADMIN_WORLD_TEMPLATE_EXISTS.replace("%world%", newWorldName));
            return true;
        }
        if (!worldManager.unloadWorld(oldWorldName, true)) {
            Utils.sendAdminError(sender, MessageConfig.ADMIN_WORLD_UNLOAD_FAILED.replace("%world%", oldWorldName));
            return true;
        }
        if (!worldManager.renameWorldFiles(oldWorldName, newWorldName)) {
            worldManager.loadWorld(oldWorldName, environment, false);
            Utils.sendAdminError(sender, MessageConfig.ADMIN_WORLD_DIRECTORY_RENAME_FAILED);
            return true;
        }

        boolean templateMoved = !oldTemplate.isDirectory() || worldManager.moveDirectory(oldTemplate, newTemplate);
        if (!templateMoved || !worldManager.loadWorld(newWorldName, environment, false)) {
            rollbackFiles(worldManager, oldWorldName, newWorldName, environment, oldTemplate, newTemplate, templateMoved);
            Utils.sendAdminError(sender, MessageConfig.ADMIN_WORLD_RENAME_FAILED);
            return true;
        }

        World newWorld = Bukkit.getWorld(newWorldName);
        List<BaseGameConfig> migratedConfigs = newWorld == null ? null
                : updateMapConfigs(mapConfigs.keySet(), oldWorldName, oldWorld, newWorld);
        if (migratedConfigs == null) {
            rollbackFiles(worldManager, oldWorldName, newWorldName, environment, oldTemplate, newTemplate, templateMoved);
            rollbackMapConfigs(mapConfigs.keySet(), newWorldName, newWorld, oldWorldName);
            Utils.sendAdminError(sender, MessageConfig.ADMIN_WORLD_CONFIG_MIGRATION_FAILED);
            return true;
        }
        owners.forEach(manager -> manager.renameManagedWorld(oldWorldName, newWorldName));

        Utils.sendAdminSuccess(sender, MessageConfig.ADMIN_WORLD_RENAMED
                .replace("%old%", oldWorldName)
                .replace("%new%", newWorldName)
                .replace("%moved%", movedPlayers == 0 ? "" : MessageConfig.ADMIN_WORLD_MOVED_PLAYERS.replace("%count%", String.valueOf(movedPlayers))));
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> worlds = new ArrayList<>(plugin.getWorldManager().getStoredWorldNames());
            worlds.removeIf(this::isProtectedWorld);
            return filterStartsWith(worlds, args[0]);
        }
        if (args.length == 3) return filterStartsWith(ENVIRONMENTS, args[2]);
        return Collections.emptyList();
    }

    private @Nullable String collectMapOwners(String worldName, Map<BaseGameConfig, Boolean> configs,
                                              Set<BaseGameInstanceManager<?>> owners) {
        for (GameTypeEnum gameType : GameTypeEnum.values()) {
            BaseGameInstanceManager<? extends BaseGameInstance> manager = plugin.getGameManager().getAreaManager(gameType);
            if (manager == null) continue;
            for (BaseGameInstance instance : manager.getRuntimeInstances()) {
                if (!worldName.equals(instance.getWorldName())) continue;
                if (instance.getGameStageEnum() != GameStageEnum.WAITING)
                    return MessageConfig.ADMIN_WORLD_GAME_IN_USE.replace("%world%", worldName);
                BaseGameConfig config = instance.getGameConfig();
                if (!config.ownsNamedWorld(worldName))
                    return MessageConfig.ADMIN_WORLD_NAME_DERIVED.replace("%world%", worldName);
                configs.put(config, Boolean.TRUE);
                owners.add(manager);
            }
        }
        return null;
    }

    private @Nullable List<BaseGameConfig> updateMapConfigs(Set<BaseGameConfig> configs, String oldWorldName,
                                                            World oldWorld, World newWorld) {
        List<BaseGameConfig> migrated = new ArrayList<>();
        for (BaseGameConfig config : configs) {
            if (!config.renameWorldReferences(oldWorldName, oldWorld, newWorld))
                return null;
            migrated.add(config);
        }
        return migrated;
    }

    private void rollbackMapConfigs(Set<BaseGameConfig> configs, String newWorldName,
                                    World newWorld, String oldWorldName) {
        World restoredWorld = Bukkit.getWorld(oldWorldName);
        if (newWorld == null || restoredWorld == null) return;
        for (BaseGameConfig config : configs) {
            if (config.ownsNamedWorld(newWorldName))
                config.renameWorldReferences(newWorldName, newWorld, restoredWorld);
        }
    }

    private void rollbackFiles(WorldManager worldManager, String oldWorldName, String newWorldName,
                               World.Environment environment, File oldTemplate, File newTemplate,
                               boolean templateMoved) {
        worldManager.unloadWorld(newWorldName, false);
        if (templateMoved && newTemplate.isDirectory()) worldManager.moveDirectory(newTemplate, oldTemplate);
        if (worldManager.getWorldFolder(newWorldName).isDirectory())
            worldManager.renameWorldFiles(newWorldName, oldWorldName);
        worldManager.loadWorld(oldWorldName, environment, false);
    }

    private boolean isProtectedWorld(String name) {
        World main = plugin.getWorldManager().getMainWorld();
        return WorldManager.isBingoWorldName(name) || (main != null && main.getName().equals(name));
    }

    private @Nullable World.Environment parseEnvironment(String value) {
        return switch (value.toLowerCase()) {
            case "normal" -> World.Environment.NORMAL;
            case "nether" -> World.Environment.NETHER;
            case "the_end" -> World.Environment.THE_END;
            default -> null;
        };
    }
}
