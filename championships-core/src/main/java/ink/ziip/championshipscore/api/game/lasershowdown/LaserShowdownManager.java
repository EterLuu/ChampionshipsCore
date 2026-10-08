package ink.ziip.championshipscore.api.game.lasershowdown;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.manager.BaseGameInstanceManager;
import ink.ziip.championshipscore.api.game.lasershowdown.config.LaserShowdownConfig;
import ink.ziip.championshipscore.api.game.lasershowdown.runtime.LaserShowdownTeamArea;

import java.io.File;

public class LaserShowdownManager extends BaseGameInstanceManager<LaserShowdownTeamArea> {
    public LaserShowdownManager(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
    }

    @Override
    public void load() {
        if (!loadArenaWorld("lasershowdown")) return;
        deferMapLoad(
                () -> {
                    loadMapDefinitions(
                            new File(plugin.getDataFolder(), "lasershowdown"),
                            (name, file) -> {
                                areas.put(
                                        name,
                                        new LaserShowdownTeamArea(
                                                plugin, new LaserShowdownConfig(plugin, name)));
                            });
                });
    }

    @Override
    public boolean addArea(String name) {
        if (areas.containsKey(name)) return false;

        LaserShowdownConfig laserShowdownConfig = new LaserShowdownConfig(plugin, name);
        laserShowdownConfig.initializeConfiguration(plugin.getFolder());
        laserShowdownConfig.setAreaName(name);
        laserShowdownConfig.saveOptions();

        LaserShowdownTeamArea laserShowdownTeamArea =
                areas.putIfAbsent(
                        name, new LaserShowdownTeamArea(plugin, laserShowdownConfig));

        return laserShowdownTeamArea == null;
    }
}
