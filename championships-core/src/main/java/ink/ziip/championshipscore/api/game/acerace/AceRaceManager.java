package ink.ziip.championshipscore.api.game.acerace;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.manager.BaseGameInstanceManager;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import ink.ziip.championshipscore.configuration.config.CCConfig;
import org.bukkit.GameRules;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitScheduler;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;

public class AceRaceManager extends BaseGameInstanceManager<AceRaceArea> {
    private final Map<String, List<AceRaceArea>> instancesByMap = new ConcurrentHashMap<>();
    public AceRaceManager(ChampionshipsCore plugin) {
        super(plugin);
    }

    @Override
    public void load() {
        if (!loadArenaWorld("acerace")) return;
        World world = plugin.getServer().getWorld("acerace");
        if (world != null) world.setGameRule(GameRules.FALL_DAMAGE, false);
        File areasFolder = new File(plugin.getDataFolder(), "acerace");
        areasFolder.mkdirs();
        BukkitScheduler scheduler = plugin.getServer().getScheduler();
        scheduler.runTask(plugin, task -> {
            String[] areaList = areasFolder.list((directory, name) -> name.toLowerCase().endsWith(".yml"));
            if (areaList == null) return;
            for (String file : areaList) {
                String name = file.substring(0, file.length() - 4);
                AceRaceConfig config = new AceRaceConfig(plugin, name);
                config.initializeConfiguration(plugin.getFolder());
                createInstances(name, config);
            }
        });
    }

    @Override
    public void unload() {
        for (AceRaceArea area : getRuntimeInstances()) {
            if (area.getGameStageEnum() != GameStageEnum.WAITING) area.abortAndReset();
        }
        clearAreas();
        instancesByMap.clear();
    }

    @Override
    public boolean addArea(String name) {
        if (areas.containsKey(name)) return false;
        AceRaceConfig config = new AceRaceConfig(plugin, name);
        config.initializeConfiguration(plugin.getFolder());
        config.setAreaName(name);
        config.saveOptions();
        createInstances(name, config);
        return true;
    }

    public synchronized @NotNull List<AceRaceArea> getMapInstances(@NotNull String mapName) {
        List<AceRaceArea> instances = instancesByMap.get(mapName);
        return instances == null ? List.of() : List.copyOf(instances);
    }

    @Override
    public synchronized Collection<AceRaceArea> getRuntimeInstances() {
        List<AceRaceArea> instances = new ArrayList<>();
        instancesByMap.values().forEach(instances::addAll);
        return List.copyOf(instances);
    }

    @Override
    public synchronized boolean deleteArea(String name) {
        List<AceRaceArea> instances = instancesByMap.get(name);
        AceRaceArea representative = areas.get(name);
        if (instances == null || representative == null || !canEditMap(name)) return false;
        try {
            java.nio.file.Files.deleteIfExists(plugin.getFolder().resolve(representative.getGameConfig().getFileName()));
        } catch (java.io.IOException exception) {
            plugin.getLogger().warning("无法删除 AceRace 地图配置 " + name + " | " + exception.getMessage());
            return false;
        }
        instances.forEach(AceRaceArea::dispose);
        instancesByMap.remove(name);
        areas.remove(name);
        return true;
    }

    private void createInstances(String mapName, AceRaceConfig config) {
        int count = Math.max(1, CCConfig.DAILY_ACERACE_CONCURRENT_INSTANCES);
        List<AceRaceArea> instances = new ArrayList<>(count);
        for (int copyIndex = 0; copyIndex < count; copyIndex++) {
            instances.add(new AceRaceArea(plugin, config, copyIndex, false));
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
