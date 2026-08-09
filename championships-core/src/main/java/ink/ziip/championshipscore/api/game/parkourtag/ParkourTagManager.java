package ink.ziip.championshipscore.api.game.parkourtag;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.manager.BaseGameInstanceManager;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.CCConfig;
import org.bukkit.scheduler.BukkitScheduler;
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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ParkourTagManager extends BaseGameInstanceManager<ParkourTagArea> {
    private final Map<UUID, Integer> chaserTimes = new ConcurrentHashMap<>();
    private final Map<ChampionshipTeam, Long> enderEyeUsedTimes = new ConcurrentHashMap<>();
    private final Map<String, List<ParkourTagArea>> instancesByMap = new ConcurrentHashMap<>();

    public ParkourTagManager(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
    }

    @Override
    public void load() {
        BukkitScheduler scheduler = plugin.getServer().getScheduler();
        File areasFolder = new File(plugin.getDataFolder() + File.separator + "parkourtag");
        areasFolder.mkdirs();

        scheduler.runTask(plugin, task -> {
            String[] areaList = areasFolder.list((d, n) -> n.toLowerCase().endsWith(".yml"));
            if (areaList != null) {
                Arrays.sort(areaList);
                Set<String> loadedWorlds = new HashSet<>();
                for (String file : areaList) {
                    String name = file.substring(0, file.length() - 4);
                    File configFile = new File(areasFolder, file);
                    YamlConfiguration raw = YamlConfiguration.loadConfiguration(configFile);
                    String worldName = raw.getString("world-name", "parkourtag");
                    if (raw.contains("world-name") && (worldName == null || worldName.isBlank())) {
                        ParkourTagConfig config = new ParkourTagConfig(plugin, name);
                        config.initializeConfiguration(plugin.getFolder());
                        createInstances(name, config);
                        continue;
                    }
                    if (worldName == null || worldName.isBlank()) worldName = "parkourtag";
                    if (!loadedWorlds.add(worldName)) {
                        plugin.getLogger().severe("ParkourTag 地图 " + name + " 与其他配置共用世界 "
                                + worldName + "，已跳过以防实例重叠");
                        continue;
                    }
                    if (!loadArenaWorld(worldName)) continue;
                    ParkourTagConfig config = new ParkourTagConfig(plugin, name);
                    config.initializeConfiguration(plugin.getFolder());
                    createInstances(name, config);
                }
            }
        });
    }

    @Override
    public void unload() {
        for (List<ParkourTagArea> instances : instancesByMap.values()) {
            for (ParkourTagArea instance : instances) {
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
        return false;
    }

    @Override
    public boolean addArea(String name, String worldName) {
        if (areas.containsKey(name)) return false;
        ParkourTagConfig parkourTagConfig = new ParkourTagConfig(plugin, name);
        parkourTagConfig.initializeConfiguration(plugin.getFolder());
        parkourTagConfig.setAreaName(name);
        parkourTagConfig.setWorldName("");
        parkourTagConfig.saveOptions();

        createInstances(name, parkourTagConfig);
        return true;
    }

    @Override
    public synchronized boolean deleteArea(String name) {
        List<ParkourTagArea> instances = instancesByMap.get(name);
        if (instances == null || !canEditMap(name)) return false;
        ParkourTagArea representative = areas.get(name);
        if (representative == null) return false;
        try {
            java.nio.file.Files.deleteIfExists(plugin.getFolder().resolve(representative.getGameConfig().getFileName()));
        } catch (java.io.IOException exception) {
            plugin.getLogger().warning("无法删除 ParkourTag 地图配置 " + name + " | " + exception.getMessage());
            return false;
        }
        instances.forEach(ParkourTagArea::dispose);
        instancesByMap.remove(name);
        areas.remove(name);
        return true;
    }

    public synchronized @NotNull List<ParkourTagArea> getMapInstances(@NotNull String mapName) {
        List<ParkourTagArea> instances = instancesByMap.get(mapName);
        ParkourTagArea first = areas.get(mapName);
        if (instances == null || first == null) return List.of();

        int desired = Math.max(1, first.getGameConfig().getCopyCount());
        while (instances.size() < desired) {
            instances.add(new ParkourTagArea(plugin, first.getGameConfig(), instances.size(), false));
        }
        while (instances.size() > desired) {
            ParkourTagArea extra = instances.getLast();
            if (extra.getGameStageEnum() != GameStageEnum.WAITING) break;
            instances.removeLast().dispose();
        }
        return List.copyOf(instances.subList(0, desired));
    }

    @Override
    public synchronized Collection<ParkourTagArea> getRuntimeInstances() {
        LinkedHashSet<ParkourTagArea> instances = new LinkedHashSet<>();
        instancesByMap.values().forEach(instances::addAll);
        return List.copyOf(instances);
    }

    private void createInstances(String mapName, ParkourTagConfig config) {
        int count = Math.max(1, config.getCopyCount());
        List<ParkourTagArea> instances = new ArrayList<>(count);
        for (int copyIndex = 0; copyIndex < count; copyIndex++) {
            instances.add(new ParkourTagArea(plugin, config, copyIndex, false));
        }
        instancesByMap.put(mapName, instances);
        areas.put(mapName, instances.getFirst());
    }

    public void addChaserTimes(UUID uuid) {
        chaserTimes.put(uuid, chaserTimes.getOrDefault(uuid, 0) + 1);
    }

    /** Starts a new event with independent chaser quotas and Ender Eye cooldowns. */
    public void resetEventState() {
        chaserTimes.clear();
        enderEyeUsedTimes.clear();
    }

    public UUID getTeamChaser(ChampionshipTeam team) {
        for (UUID uuid : team.getMembers()) {
            if (chaserTimes.getOrDefault(uuid, 0) < CCConfig.PARKOUR_TAG_MAX_CHASER_TIMES) {
                return uuid;
            }
        }

        return UUID.fromString("00000000-0000-0000-0000-000000000000");
    }

    public void setEnderEyeUsedTimes(ChampionshipTeam championshipTeam) {
        enderEyeUsedTimes.put(championshipTeam, System.currentTimeMillis());
    }

    public boolean canUseEnderEye(ChampionshipTeam championshipTeam) {
        return (System.currentTimeMillis() - enderEyeUsedTimes.getOrDefault(championshipTeam, 0L)) > 10000L;
    }

    public boolean canBeChaser(UUID uuid) {
        return chaserTimes.getOrDefault(uuid, 0) < CCConfig.PARKOUR_TAG_MAX_CHASER_TIMES;
    }

    public int getChaserTimes(UUID uuid) {
        return chaserTimes.getOrDefault(uuid, 0);
    }
}
