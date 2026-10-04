package ink.ziip.championshipscore.api.game.dodgebolt;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.dodgebolt.config.DodgeboltConfig;
import ink.ziip.championshipscore.api.game.dodgebolt.runtime.DodgeboltArea;
import ink.ziip.championshipscore.api.game.manager.BaseGameInstanceManager;
import ink.ziip.championshipscore.api.game.model.GameStageEnum;

import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.concurrent.CompletableFuture;

public final class DodgeboltManager extends BaseGameInstanceManager<DodgeboltArea> {
    public DodgeboltManager(ChampionshipsCore plugin) {
        super(plugin);
    }

    @Override
    public void load() {
        deferMapLoad(
                () -> {
                    loadMapDefinitions(
                            new File(plugin.getDataFolder(), "dodgebolt"),
                            (name, file) -> {
                                DodgeboltArea area =
                                        new DodgeboltArea(
                                                plugin,
                                                new DodgeboltConfig(plugin, name),
                                                false,
                                                name);
                                areas.put(name, area);
                                area.preloadMap();
                            });
                });
    }

    @Override
    public void unload() {
        for (DodgeboltArea area : areas.values()) {
            if (area.getGameStageEnum() != GameStageEnum.WAITING) area.stopMatch();
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
        DodgeboltConfig config = new DodgeboltConfig(plugin, name);
        config.initializeConfiguration(plugin.getFolder());
        config.setAreaName(name);
        config.bindConfiguredWorld("");
        config.saveOptions();
        DodgeboltArea area = new DodgeboltArea(plugin, config, true, name);
        areas.put(name, area);
        return true;
    }

    @Override
    public synchronized boolean loadAreaAfterRename(
            @NotNull String name, @NotNull String worldName) {
        if (areas.containsKey(name)) return false;
        DodgeboltConfig config = new DodgeboltConfig(plugin, name);
        config.initializeConfiguration(plugin.getFolder());
        DodgeboltArea area = new DodgeboltArea(plugin, config, false, name);
        areas.put(name, area);
        area.preloadMap();
        return true;
    }

    public CompletableFuture<Boolean> saveArea(String name) {
        DodgeboltArea area = areas.get(name);
        return area == null || area.getGameStageEnum() != GameStageEnum.WAITING
                ? CompletableFuture.completedFuture(false)
                : area.saveMap(World.Environment.NORMAL);
    }
}
