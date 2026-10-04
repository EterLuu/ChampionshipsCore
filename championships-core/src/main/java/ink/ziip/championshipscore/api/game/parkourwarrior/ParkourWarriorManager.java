package ink.ziip.championshipscore.api.game.parkourwarrior;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.manager.BaseGameInstanceManager;
import ink.ziip.championshipscore.api.game.parkourwarrior.config.ParkourWarriorConfig;
import ink.ziip.championshipscore.api.game.parkourwarrior.runtime.ParkourWarriorTeamArea;
import ink.ziip.championshipscore.configuration.config.CCConfig;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

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
        int count = Math.max(1, CCConfig.DAILY_PARKOUR_WARRIOR_CONCURRENT_INSTANCES);
        List<ParkourWarriorTeamArea> instances = new ArrayList<>(count);
        for (int copyIndex = 0; copyIndex < count; copyIndex++) {
            instances.add(new ParkourWarriorTeamArea(plugin, config, copyIndex, false));
        }
        registerMapInstances(mapName, instances);
    }

    @Override
    protected boolean allowsSharedMapWorlds() {
        return true;
    }
}
