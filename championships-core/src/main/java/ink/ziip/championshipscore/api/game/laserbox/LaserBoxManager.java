package ink.ziip.championshipscore.api.game.laserbox;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.manager.BaseGameInstanceManager;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Loads configured LaserBox maps and exposes their arena copies as paired-game instances. */
public final class LaserBoxManager extends BaseGameInstanceManager<LaserBoxArea> {
    private final Map<String, List<LaserBoxArea>> instancesByMap = new ConcurrentHashMap<>();

    public LaserBoxManager(ChampionshipsCore plugin) {
        super(plugin);
    }

    @Override
    public void load() {
        BukkitScheduler scheduler = plugin.getServer().getScheduler();
        File folder = new File(plugin.getDataFolder(), "laserbox");
        folder.mkdirs();
        scheduler.runTask(plugin, ignored -> {
            String[] files = folder.list((dir, name) -> name.toLowerCase().endsWith(".yml"));
            if (files == null) return;
            Arrays.sort(files);
            Set<String> loadedWorlds = new java.util.HashSet<>();
            for (String file : files) {
                String name = file.substring(0, file.length() - 4);
                YamlConfiguration raw = YamlConfiguration.loadConfiguration(new File(folder, file));
                String world = raw.getString("world-name", "");
                if (world == null) world = "";
                LaserBoxConfig config = new LaserBoxConfig(plugin, name);
                config.initializeConfiguration(plugin.getFolder());
                if (!world.isBlank() && loadedWorlds.add(world) && !loadArenaWorld(world)) {
                    loadedWorlds.remove(world);
                    continue;
                }
                createInstances(name, config);
            }
        });
    }

    @Override
    public void unload() {
        for (LaserBoxArea area : getRuntimeInstances()) {
            if (area.getGameStageEnum() != GameStageEnum.WAITING) area.abortAndReset();
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
    public synchronized boolean loadAreaAfterRename(@NotNull String name, @NotNull String worldName) {
        if (areas.containsKey(name)) return false;
        LaserBoxConfig config = new LaserBoxConfig(plugin, name);
        config.initializeConfiguration(plugin.getFolder());
        createInstances(name, config);
        return true;
    }

    @Override
    public synchronized Collection<LaserBoxArea> getRuntimeInstances() {
        LinkedHashSet<LaserBoxArea> instances = new LinkedHashSet<>();
        instancesByMap.values().forEach(instances::addAll);
        return List.copyOf(instances);
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
        instancesByMap.put(mapName, instances);
        areas.put(mapName, instances.getFirst());
    }

    @Override
    protected void onAreaDetached(@NotNull String name) {
        instancesByMap.remove(name);
    }

    @Override
    protected boolean allowsSharedMapWorlds() {
        return true;
    }
}
