package ink.ziip.championshipscore.api.game.battlebox;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.config.MapWorldNames;
import ink.ziip.championshipscore.api.game.manager.BaseGameInstanceManager;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import ink.ziip.championshipscore.util.scheduler.FoliaScheduler;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class BattleBoxManager extends BaseGameInstanceManager<BattleBoxArea> {
    private final Map<String, List<BattleBoxArea>> instancesByMap = new ConcurrentHashMap<>();

    public BattleBoxManager(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
    }

    @Override
    public void load() {
        File areasFolder = new File(plugin.getDataFolder() + File.separator + "battlebox");
        areasFolder.mkdirs();

        FoliaScheduler.global(plugin).runTask(task -> {
            String[] areaList = areasFolder.list((d, n) -> n.toLowerCase().endsWith(".yml"));
            if (areaList != null) {
                Arrays.sort(areaList);
                Set<String> loadedWorlds = new HashSet<>();
                for (String file : areaList) {
                    String name = file.substring(0, file.length() - 4);
                    File configFile = new File(areasFolder, file);
                    String worldName = YamlConfiguration.loadConfiguration(configFile)
                            .getString("world-name", "battlebox");
                    if (worldName == null || worldName.isBlank()) worldName = "battlebox";
                    if (!loadedWorlds.add(worldName)) {
                        plugin.getLogger().severe("BattleBox 地图 " + name + " 与其他配置共用世界 "
                                + worldName + "，已跳过以防实例重叠");
                        continue;
                    }
                    if (!loadArenaWorld(worldName)) continue;
                    BattleBoxConfig config = new BattleBoxConfig(plugin, name);
                    config.initializeConfiguration(plugin.getFolder());
                    createInstances(name, config);
                }
            }
        });
    }

    @Override
    public void unload() {
        for (List<BattleBoxArea> instances : instancesByMap.values()) {
            for (BattleBoxArea instance : instances) {
                if (instance.getGameStageEnum() != GameStageEnum.WAITING) {
                    instance.endGameFinally();
                }
            }
        }
        clearAreas();
        instancesByMap.clear();
    }

    @Override
    public boolean addArea(String name) {
        if (areas.containsKey(name))
            return false;

        String worldName = MapWorldNames.forMap("battlebox", name);
        if (!loadArenaWorld(worldName)) return false;
        BattleBoxConfig battleBoxConfig = new BattleBoxConfig(plugin, name);
        battleBoxConfig.initializeConfiguration(plugin.getFolder());
        battleBoxConfig.setAreaName(name);
        battleBoxConfig.setWorldName(worldName);
        battleBoxConfig.saveOptions();

        createInstances(name, battleBoxConfig);
        return true;
    }

    /** Returns the map's permanently allocated instances, growing the pool after a prepare count change. */
    public synchronized @NotNull List<BattleBoxArea> getMapInstances(@NotNull String mapName) {
        List<BattleBoxArea> instances = instancesByMap.get(mapName);
        BattleBoxArea first = areas.get(mapName);
        if (instances == null || first == null) return List.of();

        int desired = Math.max(1, first.getGameConfig().getCopyCount());
        while (instances.size() < desired) {
            instances.add(new BattleBoxArea(plugin, first.getGameConfig(), instances.size(), false));
        }
        while (instances.size() > desired) {
            BattleBoxArea extra = instances.getLast();
            if (extra.getGameStageEnum() != GameStageEnum.WAITING) break;
            instances.removeLast().dispose();
        }
        return List.copyOf(instances.subList(0, desired));
    }

    @Override
    public synchronized Collection<BattleBoxArea> getRuntimeInstances() {
        LinkedHashSet<BattleBoxArea> instances = new LinkedHashSet<>();
        instancesByMap.values().forEach(instances::addAll);
        return List.copyOf(instances);
    }

    private void createInstances(String mapName, BattleBoxConfig config) {
        int count = Math.max(1, config.getCopyCount());
        List<BattleBoxArea> instances = new ArrayList<>(count);
        for (int copyIndex = 0; copyIndex < count; copyIndex++) {
            instances.add(new BattleBoxArea(plugin, config, copyIndex, false));
        }
        instancesByMap.put(mapName, instances);
        areas.put(mapName, instances.getFirst());
    }
}
