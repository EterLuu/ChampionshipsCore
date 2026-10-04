package ink.ziip.championshipscore.api.game.hotycodydusky;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.hotycodydusky.config.HotyCodyDuskyConfig;
import ink.ziip.championshipscore.api.game.hotycodydusky.runtime.HotyCodyDuskyTeamArea;
import ink.ziip.championshipscore.api.game.manager.BaseGameInstanceManager;

import java.io.File;
import java.util.HashSet;

/** A map registers one round owner, independently of its physical copy count. */
public class HotyCodyDuskyManager extends BaseGameInstanceManager<HotyCodyDuskyTeamArea> {
    public HotyCodyDuskyManager(ChampionshipsCore plugin) {
        super(plugin);
    }

    @Override
    public void load() {
        deferMapLoad(
                () -> {
                    var loadedWorlds = new HashSet<String>();
                    loadMapDefinitions(
                            new File(plugin.getDataFolder(), "hotycodydusky"),
                            (name, file) -> {
                                var config = new HotyCodyDuskyConfig(plugin, name);
                                config.initializeConfiguration(plugin.getFolder());
                                String world = config.getConfiguredWorld();
                                if (!world.isBlank()
                                        && loadedWorlds.add(world)
                                        && !loadArenaWorld(world)) return;
                                config.loadFileOptions();
                                areas.put(name, new HotyCodyDuskyTeamArea(plugin, config));
                            });
                });
    }

    @Override
    public boolean addArea(String name) {
        return addArea(name, "");
    }

    @Override
    public boolean addArea(String name, String world) {
        if (areas.containsKey(name)) return false;
        var config = new HotyCodyDuskyConfig(plugin, name);
        config.initializeConfiguration(plugin.getFolder());
        config.setAreaName(name);
        config.bindConfiguredWorld("");
        config.beginPrepareDraft();
        areas.put(name, new HotyCodyDuskyTeamArea(plugin, config));
        return true;
    }

    @Override
    public synchronized boolean loadAreaAfterRename(String name, String world) {
        if (areas.containsKey(name)) return false;
        var config = new HotyCodyDuskyConfig(plugin, name);
        config.initializeConfiguration(plugin.getFolder());
        areas.put(name, new HotyCodyDuskyTeamArea(plugin, config));
        return true;
    }

    @Override
    protected boolean allowsSharedMapWorlds() {
        return true;
    }
}
