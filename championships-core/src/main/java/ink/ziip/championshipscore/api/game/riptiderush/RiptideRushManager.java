package ink.ziip.championshipscore.api.game.riptiderush;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.manager.BaseGameInstanceManager;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;

public final class RiptideRushManager extends BaseGameInstanceManager<RiptideRushArea> {
    public RiptideRushManager(ChampionshipsCore plugin) {
        super(plugin);
    }

    @Override
    public void load() {
        File folder = new File(plugin.getDataFolder(), "riptiderush");
        migrateLegacyFolder(folder);
        folder.mkdirs();
        plugin.getServer().getScheduler().runTask(plugin, ignored -> {
            String[] files = folder.list((directory, name) -> name.toLowerCase().endsWith(".yml"));
            if (files == null) return;
            Arrays.sort(files);
            for (String file : files) {
                String name = file.substring(0, file.length() - 4);
                RiptideRushConfig config = new RiptideRushConfig(plugin, name);
                config.initializeConfiguration(plugin.getFolder());
                RiptideRushArea area = new RiptideRushArea(plugin, config, false, name);
                areas.put(name, area);
                area.preloadMap();
            }
        });
    }

    private void migrateLegacyFolder(File target) {
        File legacy = new File(plugin.getDataFolder(), "raftsurvival");
        if (!legacy.isDirectory()) return;
        File[] files = legacy.listFiles((directory, name) -> name.toLowerCase().endsWith(".yml"));
        if (files == null) return;
        for (File source : files) {
            File destination = new File(target, source.getName());
            if (destination.exists()) continue;
            try {
                target.mkdirs();
                Files.move(source.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE);
                plugin.getLogger().info("激流勇进地图配置已从旧目录迁移：" + source.getName());
            } catch (IOException atomicFailure) {
                try {
                    Files.move(source.toPath(), destination.toPath());
                    plugin.getLogger().info("激流勇进地图配置已从旧目录迁移：" + source.getName());
                } catch (IOException failure) {
                    plugin.getLogger().warning("无法迁移旧激流勇进地图配置 " + source.getName() + " | " + failure.getMessage());
                }
            }
        }
    }

    @Override
    public void unload() {
        RiptideWorkshop.cancelAll();
        RiptideCourseTrial.stopAll();
        RiptideCourseGenerator.cancelAll();
        for (RiptideRushArea area : areas.values()) {
            if (area.getGameStageEnum() != GameStageEnum.WAITING) area.abortAndReset();
        }
        clearAreas();
    }

    @Override
    public boolean addArea(String name) {
        return addArea(name, "");
    }

    @Override
    public boolean addArea(String name, String worldName) {
        if (areas.containsKey(name)) return false;
        RiptideRushConfig config = new RiptideRushConfig(plugin, name);
        config.initializeConfiguration(plugin.getFolder());
        config.setAreaName(name);
        config.bindConfiguredWorld("");
        config.saveOptions();
        areas.put(name, new RiptideRushArea(plugin, config, true, name));
        return true;
    }

    @Override
    public synchronized boolean loadAreaAfterRename(@NotNull String name, @NotNull String worldName) {
        if (areas.containsKey(name)) return false;
        RiptideRushConfig config = new RiptideRushConfig(plugin, name);
        config.initializeConfiguration(plugin.getFolder());
        RiptideRushArea area = new RiptideRushArea(plugin, config, false, name);
        areas.put(name, area);
        area.preloadMap();
        return true;
    }

    public java.util.concurrent.CompletableFuture<Boolean> saveArea(String name) {
        RiptideRushArea area = areas.get(name);
        if (area == null || area.getGameStageEnum() != GameStageEnum.WAITING)
            return java.util.concurrent.CompletableFuture.completedFuture(false);
        return area.saveMap(World.Environment.NORMAL);
    }
}
