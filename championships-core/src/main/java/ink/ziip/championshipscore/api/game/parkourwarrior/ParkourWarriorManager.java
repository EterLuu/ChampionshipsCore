package ink.ziip.championshipscore.api.game.parkourwarrior;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.manager.BaseGameInstanceManager;
import ink.ziip.championshipscore.api.game.model.GameTypeEnum;
import ink.ziip.championshipscore.api.game.parkourwarrior.config.ParkourWarriorConfig;
import ink.ziip.championshipscore.api.game.parkourwarrior.runtime.ParkourWarriorTeamArea;

import java.io.File;

public class ParkourWarriorManager extends BaseGameInstanceManager<ParkourWarriorTeamArea> {

    public ParkourWarriorManager(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
    }

    @Override
    public void load() {
        if (!loadArenaWorld("rawarrior")) return;
        deferMapLoad(
                () -> {
                    loadMapDefinitions(
                            new File(plugin.getDataFolder(), "parkourwarrior"),
                            (name, file) -> {
                                ParkourWarriorConfig config =
                                        new ParkourWarriorConfig(plugin, name);
                                config.initializeConfiguration(plugin.getFolder());
                                createInstances(name, config);
                            });
                });
    }

    @Override
    public boolean addArea(String name) {
        if (areas.containsKey(name)) return false;

        ParkourWarriorConfig config = new ParkourWarriorConfig(plugin, name);
        config.initializeConfiguration(plugin.getFolder());
        config.setAreaName(name);
        config.saveOptions();
        createInstances(name, config);
        return true;
    }

    private void createInstances(String mapName, ParkourWarriorConfig config) {
        createMapInstances(
                mapName,
                plugin.getConfigurationManager()
                        .getCCConfig()
                        .dailyGame(GameTypeEnum.ParkourWarrior)
                        .concurrentInstances(),
                index -> new ParkourWarriorTeamArea(plugin, config, index, false));
    }

    @Override
    protected boolean allowsSharedMapWorlds() {
        return true;
    }
}
