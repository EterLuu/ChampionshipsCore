package ink.ziip.championshipscore.api.game.laserbox;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.laserbox.config.LaserBoxConfig;
import ink.ziip.championshipscore.api.game.laserbox.runtime.LaserBoxArea;
import ink.ziip.championshipscore.api.game.manager.BaseGameInstanceManager;
import ink.ziip.championshipscore.api.game.model.GameStageEnum;

import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Loads configured LaserBox maps and exposes their arena copies as paired-game instances. */
public final class LaserBoxManager extends BaseGameInstanceManager<LaserBoxArea> {

    public LaserBoxManager(ChampionshipsCore plugin) {
        super(plugin);
    }

    @Override
    public void load() {
        deferMapLoad(
                () -> {
                    Set<String> loadedWorlds = new HashSet<>();
                    loadMapDefinitions(
                            new File(plugin.getDataFolder(), "laserbox"),
                            (name, file) -> {
                                YamlConfiguration raw = YamlConfiguration.loadConfiguration(file);
                                String world = raw.getString("world-name", "");
                                if (world == null) world = "";
                                LaserBoxConfig config = new LaserBoxConfig(plugin, name);
                                config.initializeConfiguration(plugin.getFolder());
                                if (!world.isBlank()
                                        && loadedWorlds.add(world)
                                        && !loadArenaWorld(world)) {
                                    loadedWorlds.remove(world);
                                    return;
                                }
                                createInstances(name, config);
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
        LaserBoxConfig config = new LaserBoxConfig(plugin, name);
        config.initializeConfiguration(plugin.getFolder());
        config.setAreaName(name);
        config.bindConfiguredWorld(worldName == null ? "" : worldName);
        config.beginPrepareDraft();
        config.saveOptions();
        createInstances(name, config);
        return true;
    }

    @Override
    public synchronized boolean loadAreaAfterRename(
            @NotNull String name, @NotNull String worldName) {
        if (areas.containsKey(name)) return false;
        LaserBoxConfig config = new LaserBoxConfig(plugin, name);
        config.initializeConfiguration(plugin.getFolder());
        createInstances(name, config);
        return true;
    }

    public synchronized List<LaserBoxArea> getMapInstances(String mapName) {
        List<LaserBoxArea> instances = instancesByMap.get(mapName);
        LaserBoxArea first = areas.get(mapName);
        if (instances == null || first == null) return List.of();
        int desired = Math.max(1, first.getGameConfig().getCopyCount());
        while (instances.size() < desired)
            instances.add(new LaserBoxArea(plugin, first.getGameConfig(), instances.size(), false));
        while (instances.size() > desired) {
            LaserBoxArea extra = instances.getLast();
            if (extra.getGameStageEnum() != GameStageEnum.WAITING) break;
            instances.removeLast().dispose();
        }
        return List.copyOf(instances.subList(0, Math.min(desired, instances.size())));
    }

    private void createInstances(String mapName, LaserBoxConfig config) {
        int count = Math.max(1, config.getCopyCount());
        List<LaserBoxArea> instances = new ArrayList<>(count);
        for (int copyIndex = 0; copyIndex < count; copyIndex++)
            instances.add(new LaserBoxArea(plugin, config, copyIndex, false));
        registerMapInstances(mapName, instances);
    }

    @Override
    protected boolean allowsSharedMapWorlds() {
        return true;
    }
}
