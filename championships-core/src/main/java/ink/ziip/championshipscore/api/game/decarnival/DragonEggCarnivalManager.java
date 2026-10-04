package ink.ziip.championshipscore.api.game.decarnival;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.decarnival.config.DragonEggCarnivalConfig;
import ink.ziip.championshipscore.api.game.decarnival.runtime.DragonEggCarnivalArea;
import ink.ziip.championshipscore.api.game.manager.BaseGameInstanceManager;
import ink.ziip.championshipscore.api.game.model.GameStageEnum;

import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.concurrent.CompletableFuture;

public class DragonEggCarnivalManager extends BaseGameInstanceManager<DragonEggCarnivalArea> {

    public DragonEggCarnivalManager(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
    }

    @Override
    public void load() {
        deferMapLoad(
                () -> {
                    loadMapDefinitions(
                            new File(plugin.getDataFolder(), "decarnival"),
                            (name, file) -> {
                                DragonEggCarnivalArea area =
                                        new DragonEggCarnivalArea(
                                                plugin,
                                                new DragonEggCarnivalConfig(plugin, name),
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
        DragonEggCarnivalConfig dragonEggCarnivalConfig = new DragonEggCarnivalConfig(plugin, name);
        dragonEggCarnivalConfig.initializeConfiguration(plugin.getFolder());
        dragonEggCarnivalConfig.setAreaName(name);
        dragonEggCarnivalConfig.bindConfiguredWorld("");
        dragonEggCarnivalConfig.saveOptions();

        DragonEggCarnivalArea dragonEggCarnivalArea =
                new DragonEggCarnivalArea(plugin, dragonEggCarnivalConfig, true, name);
        areas.put(name, dragonEggCarnivalArea);

        return true;
    }

    @Override
    public synchronized boolean loadAreaAfterRename(
            @NotNull String name, @NotNull String worldName) {
        if (areas.containsKey(name)) return false;
        DragonEggCarnivalConfig config = new DragonEggCarnivalConfig(plugin, name);
        config.initializeConfiguration(plugin.getFolder());
        DragonEggCarnivalArea area = new DragonEggCarnivalArea(plugin, config, false, name);
        areas.put(name, area);
        area.preloadMap();
        return true;
    }

    public CompletableFuture<Boolean> saveArea(String name) {
        DragonEggCarnivalArea dragonEggCarnivalArea = areas.get(name);
        if (dragonEggCarnivalArea == null) return CompletableFuture.completedFuture(false);

        if (dragonEggCarnivalArea.getGameStageEnum() != GameStageEnum.WAITING) {
            return CompletableFuture.completedFuture(false);
        }

        return dragonEggCarnivalArea.saveMap(World.Environment.THE_END);
    }
}
