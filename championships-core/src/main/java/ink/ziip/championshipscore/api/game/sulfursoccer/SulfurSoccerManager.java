package ink.ziip.championshipscore.api.game.sulfursoccer;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.manager.BaseGameInstanceManager;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import org.bukkit.World;

import java.util.concurrent.CompletableFuture;

import java.io.File;
import org.jetbrains.annotations.NotNull;

public final class SulfurSoccerManager extends BaseGameInstanceManager<SulfurSoccerArea> {
    public SulfurSoccerManager(ChampionshipsCore plugin) {
        super(plugin);
    }

    @Override
    public void load() {
        File folder = new File(plugin.getDataFolder(), "sulfursoccer");
        folder.mkdirs();
        plugin.getServer().getScheduler().runTask(plugin, task -> {
            String[] files = folder.list((dir, name) -> name.toLowerCase(java.util.Locale.ROOT).endsWith(".yml"));
            if (files == null) return;
            for (String file : files) {
                String name = file.substring(0, file.length() - 4);
                SulfurSoccerArea area = new SulfurSoccerArea(plugin, new SulfurSoccerConfig(plugin, name), false, name);
                areas.put(name, area);
                area.preloadMap();
            }
        });
    }

    @Override
    public void unload() {
        for (SulfurSoccerArea area : areas.values()) {
            if (area.getGameStageEnum() != GameStageEnum.WAITING) area.abortAndReset();
        }
        clearAreas();
    }

    @Override
    public boolean addArea(String name) {
        return false;
    }
    @Override
    public boolean addArea(String name, String worldName) {
        if (areas.containsKey(name)) return false;
        SulfurSoccerConfig config = new SulfurSoccerConfig(plugin, name);
        config.initializeConfiguration(plugin.getFolder());
        config.setAreaName(name);
        config.bindConfiguredWorld("");
        config.saveOptions();
        SulfurSoccerArea area = new SulfurSoccerArea(plugin, config, true, name);
        areas.put(name, area);
        return true;
    }

    @Override
    public synchronized boolean loadAreaAfterRename(@NotNull String name, @NotNull String worldName) {
        if (areas.containsKey(name)) return false;
        SulfurSoccerConfig config = new SulfurSoccerConfig(plugin, name);
        config.initializeConfiguration(plugin.getFolder());
        SulfurSoccerArea area = new SulfurSoccerArea(plugin, config, false, name);
        areas.put(name, area);
        area.preloadMap();
        return true;
    }

    public CompletableFuture<Boolean> saveArea(String name) {
        SulfurSoccerArea area = areas.get(name);
        return area == null || area.getGameStageEnum() != GameStageEnum.WAITING
                ? CompletableFuture.completedFuture(false)
                : area.saveMap(World.Environment.NORMAL);
    }
}
