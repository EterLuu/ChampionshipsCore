package ink.ziip.championshipscore.api.game.frostbite;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.frostbite.config.FrostbiteConfig;
import ink.ziip.championshipscore.api.game.frostbite.runtime.FrostbiteArea;
import ink.ziip.championshipscore.api.game.manager.BaseGameInstanceManager;
import ink.ziip.championshipscore.api.game.model.GameStageEnum;

import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public final class FrostbiteManager extends BaseGameInstanceManager<FrostbiteArea> {
    public FrostbiteManager(ChampionshipsCore plugin) {
        super(plugin);
    }

    @Override
    public void load() {
        deferMapLoad(
                () -> {
                    loadMapDefinitions(
                            new File(plugin.getDataFolder(), "frostbite"),
                            (name, file) -> {
                                FrostbiteConfig config = new FrostbiteConfig(plugin, name);
                                config.initializeConfiguration(plugin.getFolder());
                                FrostbiteArea area = new FrostbiteArea(plugin, config, false, name);
                                areas.put(name, area);
                                area.preloadMap();
                            });
                });
    }

    @Override
    public boolean addArea(String name) {
        return addArea(name, "");
    }

    @Override
    public boolean addArea(String name, String worldName) {
        if (areas.containsKey(name)) return false;
        FrostbiteConfig config = new FrostbiteConfig(plugin, name);
        config.initializeConfiguration(plugin.getFolder());
        config.setAreaName(name);
        config.bindConfiguredWorld("");
        config.saveOptions();
        areas.put(name, new FrostbiteArea(plugin, config, true, name));
        return true;
    }

    @Override
    public synchronized boolean loadAreaAfterRename(
            @NotNull String name, @NotNull String worldName) {
        if (areas.containsKey(name)) return false;
        FrostbiteConfig config = new FrostbiteConfig(plugin, name);
        config.initializeConfiguration(plugin.getFolder());
        FrostbiteArea area = new FrostbiteArea(plugin, config, false, name);
        areas.put(name, area);
        area.preloadMap();
        return true;
    }

    public java.util.concurrent.CompletableFuture<Boolean> saveArea(String name) {
        FrostbiteArea area = areas.get(name);
        if (area == null || area.getGameStageEnum() != GameStageEnum.WAITING)
            return java.util.concurrent.CompletableFuture.completedFuture(false);
        return area.saveMap(World.Environment.NORMAL);
    }
}
