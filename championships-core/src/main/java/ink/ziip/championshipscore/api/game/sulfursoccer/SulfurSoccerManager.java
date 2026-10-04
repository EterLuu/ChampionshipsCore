package ink.ziip.championshipscore.api.game.sulfursoccer;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.manager.BaseGameInstanceManager;
import ink.ziip.championshipscore.api.game.model.GameStageEnum;
import ink.ziip.championshipscore.api.game.sulfursoccer.config.SulfurSoccerConfig;
import ink.ziip.championshipscore.api.game.sulfursoccer.runtime.SulfurSoccerArea;

import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.concurrent.CompletableFuture;

public final class SulfurSoccerManager extends BaseGameInstanceManager<SulfurSoccerArea> {
    public SulfurSoccerManager(ChampionshipsCore plugin) {
        super(plugin);
    }

    @Override
    public void load() {
        deferMapLoad(
                () -> {
                    loadMapDefinitions(
                            new File(plugin.getDataFolder(), "sulfursoccer"),
                            (name, file) -> {
                                SulfurSoccerArea area =
                                        new SulfurSoccerArea(
                                                plugin,
                                                new SulfurSoccerConfig(plugin, name),
                                                false,
                                                name);
                                areas.put(name, area);
                                area.preloadMap();
                            });
                });
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
    public synchronized boolean loadAreaAfterRename(
            @NotNull String name, @NotNull String worldName) {
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
