package ink.ziip.championshipscore.api.game.area.rename;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.buildmart.BuildMartManager;
import ink.ziip.championshipscore.api.game.manager.BaseGameInstanceManager;
import ink.ziip.championshipscore.util.world.WorldManager;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Moves a Build Mart map's unshared default world and its named sidecar data with a map registration rename. */
final class BuildMartWorldRename {
    record DirectoryMove(@NotNull File oldPath, @NotNull File newPath) {
    }

    record State(@NotNull String oldWorldName, @NotNull String newWorldName,
                 @NotNull World.Environment environment, @NotNull List<DirectoryMove> directories) {
    }

    record Plan(@NotNull String oldWorldName, @NotNull String newWorldName, boolean movesWorld) {
    }

    private BuildMartWorldRename() {
    }

    static @NotNull String worldNameFor(@NotNull String mapName) {
        return BuildMartManager.worldNameFor(mapName);
    }

    /**
     * A default-named world belongs to a map only while no other Build Mart registration references it.
     * Shared or custom worlds keep their physical identity when one registration is renamed.
     */
    static @NotNull Plan validate(@NotNull ChampionshipsCore plugin, @NotNull BaseGameInstanceManager<?> manager,
                                  @NotNull String oldMapName, @NotNull String newMapName,
                                  @NotNull String configuredWorldName) {
        String expectedOldWorld = worldNameFor(oldMapName);
        List<String> otherConfiguredWorlds = manager.getAreaNameList().stream()
                .filter(mapName -> !mapName.equals(oldMapName))
                .map(manager::getMapConfig)
                .filter(config -> config != null)
                .map(config -> config.getConfiguredWorld())
                .toList();
        if (!ownsDefaultWorld(oldMapName, configuredWorldName, otherConfiguredWorlds))
            return new Plan(configuredWorldName, configuredWorldName, false);

        String newWorldName = worldNameFor(newMapName);
        if (!WorldManager.isValidWorldName(newWorldName)) {
            throw new IllegalStateException("地图名无法生成有效的 Build Mart 世界名：" + newWorldName);
        }
        WorldManager worldManager = plugin.getWorldManager();
        if (Bukkit.getWorld(newWorldName) != null || worldManager.getWorldFolder(newWorldName).exists()) {
            throw new IllegalStateException("目标 Build Mart 世界已存在：" + newWorldName);
        }
        if (!worldManager.getWorldFolder(expectedOldWorld).isDirectory()) {
            throw new IllegalStateException("Build Mart 世界目录不存在：" + expectedOldWorld);
        }
        for (DirectoryMove move : sidecarMoves(plugin, expectedOldWorld, newWorldName)) {
            if (move.newPath().exists())
                throw new IllegalStateException("目标世界关联目录已存在：" + move.newPath().getPath());
            if (move.oldPath().exists() && !move.oldPath().isDirectory())
                throw new IllegalStateException("世界关联路径不是目录：" + move.oldPath().getPath());
        }
        return new Plan(expectedOldWorld, newWorldName, true);
    }

    static boolean ownsDefaultWorld(@NotNull String mapName, @NotNull String configuredWorldName,
                                    @NotNull Collection<String> otherConfiguredWorlds) {
        String expectedWorldName = worldNameFor(mapName);
        return expectedWorldName.equals(configuredWorldName)
                && otherConfiguredWorlds.stream().noneMatch(configuredWorldName::equals);
    }

    static @NotNull State rename(@NotNull ChampionshipsCore plugin, @NotNull String oldWorldName,
                                 @NotNull String newWorldName) throws Exception {
        World oldWorld = Bukkit.getWorld(oldWorldName);
        if (oldWorld == null) throw new IllegalStateException("Build Mart 世界未加载：" + oldWorldName);
        World.Environment environment = oldWorld.getEnvironment();
        WorldManager worldManager = plugin.getWorldManager();
        if (!worldManager.unloadWorld(oldWorldName, true))
            throw new IllegalStateException("无法卸载 Build Mart 世界：" + oldWorldName);

        List<DirectoryMove> moved = new ArrayList<>();
        try {
            moveRequired(worldManager, worldManager.getWorldFolder(oldWorldName),
                    worldManager.getWorldFolder(newWorldName), moved);
            for (DirectoryMove move : sidecarMoves(plugin, oldWorldName, newWorldName)) {
                if (!move.oldPath().exists()) continue;
                moveRequired(worldManager, move.oldPath(), move.newPath(), moved);
            }
            if (!worldManager.loadWorld(newWorldName, environment, false))
                throw new IllegalStateException("无法加载改名后的 Build Mart 世界：" + newWorldName);
            return new State(oldWorldName, newWorldName, environment, List.copyOf(moved));
        } catch (Exception exception) {
            restoreAfterFailure(plugin, oldWorldName, newWorldName, environment, moved);
            throw exception;
        }
    }

    static void rollback(@NotNull ChampionshipsCore plugin, @NotNull State state) throws Exception {
        WorldManager worldManager = plugin.getWorldManager();
        World renamed = Bukkit.getWorld(state.newWorldName());
        if (renamed != null && !worldManager.unloadWorld(state.newWorldName(), false))
            throw new IllegalStateException("无法卸载改名后的 Build Mart 世界：" + state.newWorldName());
        Exception failure = moveBack(worldManager, state.directories());
        if (!worldManager.loadWorld(state.oldWorldName(), state.environment(), false) && failure == null)
            failure = new IllegalStateException("无法重新加载原 Build Mart 世界：" + state.oldWorldName());
        if (failure != null) throw failure;
    }

    private static void moveRequired(@NotNull WorldManager worldManager, @NotNull File oldPath,
                                     @NotNull File newPath, @NotNull List<DirectoryMove> moved) {
        if (!oldPath.isDirectory() || newPath.exists() || !worldManager.moveDirectory(oldPath, newPath))
            throw new IllegalStateException("无法移动世界关联目录：" + oldPath.getPath());
        moved.add(new DirectoryMove(oldPath, newPath));
    }

    private static void restoreAfterFailure(@NotNull ChampionshipsCore plugin, @NotNull String oldWorldName,
                                            @NotNull String newWorldName, @NotNull World.Environment environment,
                                            @NotNull List<DirectoryMove> moved) {
        WorldManager worldManager = plugin.getWorldManager();
        if (Bukkit.getWorld(newWorldName) != null) worldManager.unloadWorld(newWorldName, false);
        moveBack(worldManager, moved);
        if (Bukkit.getWorld(oldWorldName) == null) worldManager.loadWorld(oldWorldName, environment, false);
    }

    private static Exception moveBack(@NotNull WorldManager worldManager, @NotNull List<DirectoryMove> moved) {
        Exception failure = null;
        for (int index = moved.size() - 1; index >= 0; index--) {
            DirectoryMove move = moved.get(index);
            if (move.oldPath().exists() || !move.newPath().exists()) continue;
            if (!worldManager.moveDirectory(move.newPath(), move.oldPath()) && failure == null)
                failure = new IllegalStateException("无法恢复世界关联目录：" + move.oldPath().getPath());
        }
        return failure;
    }

    private static @NotNull List<DirectoryMove> sidecarMoves(@NotNull ChampionshipsCore plugin,
                                                               @NotNull String oldWorldName,
                                                               @NotNull String newWorldName) {
        File plugins = plugin.getDataFolder().getParentFile();
        return List.of(
                new DirectoryMove(new File(new File(plugin.getDataFolder(), "maps"), oldWorldName),
                        new File(new File(plugin.getDataFolder(), "maps"), newWorldName)),
                new DirectoryMove(new File(new File(plugins, "WorldGuard/worlds"), oldWorldName),
                        new File(new File(plugins, "WorldGuard/worlds"), newWorldName)),
                new DirectoryMove(new File(new File(plugins, "FastAsyncWorldEdit/history"), oldWorldName),
                        new File(new File(plugins, "FastAsyncWorldEdit/history"), newWorldName)));
    }
}
