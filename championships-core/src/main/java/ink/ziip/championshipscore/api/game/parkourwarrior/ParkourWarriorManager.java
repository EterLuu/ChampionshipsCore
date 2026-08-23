package ink.ziip.championshipscore.api.game.parkourwarrior;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.manager.BaseGameInstanceManager;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import ink.ziip.championshipscore.configuration.config.CCConfig;
import org.bukkit.scheduler.BukkitScheduler;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ParkourWarriorManager extends BaseGameInstanceManager<ParkourWarriorTeamArea> {
    private final Map<String, List<ParkourWarriorTeamArea>> instancesByMap = new ConcurrentHashMap<>();

    public ParkourWarriorManager(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
    }

    @Override
    public void load() {
        if (!loadArenaWorld("rawarrior"))
            return;

        BukkitScheduler scheduler = plugin.getServer().getScheduler();
        File areasFolder = new File(plugin.getDataFolder() + File.separator + "parkourwarrior");
        areasFolder.mkdirs();

        scheduler.runTask(plugin, task -> {
            String[] areaList = areasFolder.list((d, n) -> n.toLowerCase().endsWith(".yml"));
            if (areaList == null) return;
            for (String file : areaList) {
                String name = file.substring(0, file.length() - 4);
                ParkourWarriorConfig config = new ParkourWarriorConfig(plugin, name);
                config.initializeConfiguration(plugin.getFolder());
                createInstances(name, config);
            }
        });
    }

    @Override
    public void unload() {
        for (ParkourWarriorTeamArea area : getRuntimeInstances()) {
            if (area.getGameStageEnum() != GameStageEnum.WAITING) {
                area.abortAndReset();
            }
        }
        clearAreas();
        instancesByMap.clear();
    }

    @Override
    public boolean addArea(String name) {
        if (areas.containsKey(name))
            return false;

        ParkourWarriorConfig config = new ParkourWarriorConfig(plugin, name);
        config.initializeConfiguration(plugin.getFolder());
        config.setAreaName(name);
        config.saveOptions();
        createInstances(name, config);
        return true;
    }

    public synchronized @NotNull List<ParkourWarriorTeamArea> getMapInstances(@NotNull String mapName) {
        List<ParkourWarriorTeamArea> instances = instancesByMap.get(mapName);
        return instances == null ? List.of() : List.copyOf(instances);
    }

    @Override
    public synchronized Collection<ParkourWarriorTeamArea> getRuntimeInstances() {
        List<ParkourWarriorTeamArea> instances = new ArrayList<>();
        instancesByMap.values().forEach(instances::addAll);
        return List.copyOf(instances);
    }

    @Override
    public synchronized boolean deleteArea(String name) {
        List<ParkourWarriorTeamArea> instances = instancesByMap.get(name);
        ParkourWarriorTeamArea representative = areas.get(name);
        if (instances == null || representative == null || !canEditMap(name)) return false;
        try {
            java.nio.file.Files.deleteIfExists(plugin.getFolder().resolve(representative.getGameConfig().getFileName()));
        } catch (java.io.IOException exception) {
            plugin.getLogger().warning("无法删除跑路战士地图配置 " + name + " | " + exception.getMessage());
            return false;
        }
        instances.forEach(ParkourWarriorTeamArea::dispose);
        instancesByMap.remove(name);
        areas.remove(name);
        return true;
    }

    private void createInstances(String mapName, ParkourWarriorConfig config) {
        int count = Math.max(1, CCConfig.DAILY_PARKOUR_WARRIOR_CONCURRENT_INSTANCES);
        List<ParkourWarriorTeamArea> instances = new ArrayList<>(count);
        for (int copyIndex = 0; copyIndex < count; copyIndex++) {
            instances.add(new ParkourWarriorTeamArea(plugin, config, copyIndex, false));
        }
        instancesByMap.put(mapName, instances);
        areas.put(mapName, instances.getFirst());
    }

    @Override
    protected boolean allowsSharedMapWorlds() {
        return true;
    }

    @Override
    protected void onAreaDetached(@NotNull String name) {
        instancesByMap.remove(name);
    }
}
