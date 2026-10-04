package ink.ziip.championshipscore.api.game.snowball;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.manager.BaseGameInstanceManager;
import ink.ziip.championshipscore.api.game.snowball.config.SnowballShowdownConfig;
import ink.ziip.championshipscore.api.game.snowball.runtime.SnowballShowdownTeamArea;

import java.io.File;

public class SnowballShowdownManager extends BaseGameInstanceManager<SnowballShowdownTeamArea> {
    public SnowballShowdownManager(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
    }

    @Override
    public void load() {
        if (!loadArenaWorld("snowball")) return;
        deferMapLoad(
                () -> {
                    loadMapDefinitions(
                            new File(plugin.getDataFolder(), "snowball"),
                            (name, file) -> {
                                areas.put(
                                        name,
                                        new SnowballShowdownTeamArea(
                                                plugin, new SnowballShowdownConfig(plugin, name)));
                            });
                });
    }

    @Override
    public boolean addArea(String name) {
        if (areas.containsKey(name)) return false;

        SnowballShowdownConfig snowballShowdownConfig = new SnowballShowdownConfig(plugin, name);
        snowballShowdownConfig.initializeConfiguration(plugin.getFolder());
        snowballShowdownConfig.setAreaName(name);
        snowballShowdownConfig.saveOptions();

        SnowballShowdownTeamArea snowballShowdownTeamArea =
                areas.putIfAbsent(
                        name, new SnowballShowdownTeamArea(plugin, snowballShowdownConfig));

        return snowballShowdownTeamArea == null;
    }
}
