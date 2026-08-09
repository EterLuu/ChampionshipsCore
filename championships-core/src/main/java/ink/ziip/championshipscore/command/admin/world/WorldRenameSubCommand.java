package ink.ziip.championshipscore.command.admin.world;

import ink.ziip.championshipscore.api.game.config.BaseGameConfig;
import ink.ziip.championshipscore.api.game.instance.BaseGameInstance;
import ink.ziip.championshipscore.api.game.manager.BaseGameInstanceManager;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
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
            Utils.sendAdminError(sender, "世界名仅支持字母、数字、下划线和连字符");
            return true;
        }
        if (oldWorldName.equalsIgnoreCase(newWorldName)) {
            Utils.sendAdminError(sender, "新旧世界名不能相同（不支持仅修改大小写）");
            return true;
        }
        if (Bukkit.getWorld(newWorldName) != null || worldManager.getWorldFolder(newWorldName).exists()) {
            Utils.sendAdminError(sender, "目标世界 &#fff566" + newWorldName + " &#ededed已存在");
            return true;
        }

        World oldWorld = Bukkit.getWorld(oldWorldName);
        File oldFolder = worldManager.getWorldFolder(oldWorldName);
        if (oldWorld == null && !oldFolder.isDirectory()) {
            Utils.sendAdminError(sender, "世界 &#fff566" + oldWorldName + " &#ededed不存在");
            return true;
        }
        if ((oldWorld != null && worldManager.isMainWorld(oldWorld))
                || oldWorldName.equals(worldManager.getMainWorld() == null ? "" : worldManager.getMainWorld().getName())) {
            Utils.sendAdminError(sender, "主大厅世界不能重命名");
            return true;
        }
        if (WorldManager.isBingoWorldName(oldWorldName)) {
            Utils.sendAdminError(sender, "Bingo 三维度由游戏管理，不能通过此命令重命名");
            return true;
        }
        if (plugin.getPrepareSessionManager().hasActiveSessions()) {
            Utils.sendAdminError(sender, "仍有地图 prepare 会话进行中，不能重命名世界");
            return true;
        }

        World.Environment requestedEnvironment = args.length == 3 ? parseEnvironment(args[2]) : null;
        if (args.length == 3 && requestedEnvironment == null) {
            sendUsage(sender);
            return true;
        }
        if (oldWorld == null) {
            if (requestedEnvironment == null) {
                Utils.sendAdminError(sender, "未加载世界需在末尾指定原环境 &#fff566normal&#ededed、&#fff566nether&#ededed 或 &#fff566the_end");
                return true;
            }
            if (!worldManager.loadWorld(oldWorldName, requestedEnvironment, false)) {
                Utils.sendAdminError(sender, "世界 &#fff566" + oldWorldName + " &#ededed加载失败 &#696969• 请检查控制台");
                return true;
            }
            oldWorld = Bukkit.getWorld(oldWorldName);
            if (oldWorld == null) {
                Utils.sendAdminError(sender, "世界 &#fff566" + oldWorldName + " &#ededed加载失败");
                return true;
            }
        } else if (requestedEnvironment != null && requestedEnvironment != oldWorld.getEnvironment()) {
            Utils.sendAdminError(sender, "已加载世界的环境为 &#fff566" + oldWorld.getEnvironment().name().toLowerCase()
                    + "&#ededed，不能指定其他环境");
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
            Utils.sendAdminError(sender, "目标地图模板 &#fff566" + newWorldName + " &#ededed已存在");
            return true;
        }
        if (!worldManager.unloadWorld(oldWorldName, true)) {
            Utils.sendAdminError(sender, "世界 &#fff566" + oldWorldName + " &#ededed卸载失败 &#696969• 请检查控制台");
            return true;
        }
        if (!worldManager.renameWorldFiles(oldWorldName, newWorldName)) {
            worldManager.loadWorld(oldWorldName, environment, false);
            Utils.sendAdminError(sender, "世界目录重命名失败 &#696969• 原世界已尝试恢复");
            return true;
        }

        boolean templateMoved = !oldTemplate.isDirectory() || worldManager.moveDirectory(oldTemplate, newTemplate);
        if (!templateMoved || !worldManager.loadWorld(newWorldName, environment, false)) {
            rollbackFiles(worldManager, oldWorldName, newWorldName, environment, oldTemplate, newTemplate, templateMoved);
            Utils.sendAdminError(sender, "世界重命名失败 &#696969• 原世界已尝试恢复");
            return true;
        }

        World newWorld = Bukkit.getWorld(newWorldName);
        List<BaseGameConfig> migratedConfigs = newWorld == null ? null
                : updateMapConfigs(mapConfigs.keySet(), oldWorldName, oldWorld, newWorld);
        if (migratedConfigs == null) {
            rollbackFiles(worldManager, oldWorldName, newWorldName, environment, oldTemplate, newTemplate, templateMoved);
            rollbackMapConfigs(mapConfigs.keySet(), newWorldName, newWorld, oldWorldName);
            Utils.sendAdminError(sender, "地图配置迁移失败 &#696969• 原世界已尝试恢复");
            return true;
        }
        owners.forEach(manager -> manager.renameManagedWorld(oldWorldName, newWorldName));

        Utils.sendAdminSuccess(sender, "已重命名世界 &#fff566" + oldWorldName + " &#ededed为 &#fff566" + newWorldName
                + (movedPlayers == 0 ? "" : " &#696969• 已迁回 " + movedPlayers + " 名玩家"));
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
                    return "世界 &#fff566" + worldName + " &#ededed正在被游戏使用，不能重命名";
                BaseGameConfig config = instance.getGameConfig();
                if (!config.ownsNamedWorld(worldName))
                    return "世界 &#fff566" + worldName + " &#ededed的名称由游戏地图规则派生，不能通过此命令重命名";
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
