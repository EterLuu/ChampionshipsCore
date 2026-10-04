package ink.ziip.championshipscore.api.game.parkourtag;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.manager.BaseGameInstanceManager;
import ink.ziip.championshipscore.api.game.parkourtag.config.ParkourTagConfig;
import ink.ziip.championshipscore.api.game.parkourtag.runtime.ParkourTagArea;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.CCConfig;

import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ParkourTagManager extends BaseGameInstanceManager<ParkourTagArea> {
    private final Map<UUID, Integer> chaserTimes = new ConcurrentHashMap<>();
    private final Map<ChampionshipTeam, Long> enderEyeUsedTimes = new ConcurrentHashMap<>();

    public ParkourTagManager(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
    }

    @Override
    public void load() {
        deferMapLoad(
                () -> {
                    Set<String> loadedWorlds = new HashSet<>();
                    loadMapDefinitions(
                            new File(plugin.getDataFolder(), "parkourtag"),
                            (name, file) -> {
                                YamlConfiguration raw = YamlConfiguration.loadConfiguration(file);
                                String worldName = raw.getString("world-name", "");
                                if (worldName == null || worldName.isBlank()) {
                                    ParkourTagConfig config = new ParkourTagConfig(plugin, name);
                                    config.initializeConfiguration(plugin.getFolder());
                                    createInstances(name, config);
                                    return;
                                }
                                if (loadedWorlds.add(worldName) && !loadArenaWorld(worldName)) {
                                    loadedWorlds.remove(worldName);
                                    return;
                                }
                                ParkourTagConfig config = new ParkourTagConfig(plugin, name);
                                config.initializeConfiguration(plugin.getFolder());
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
        ParkourTagConfig parkourTagConfig = new ParkourTagConfig(plugin, name);
        parkourTagConfig.initializeConfiguration(plugin.getFolder());
        parkourTagConfig.setAreaName(name);
        parkourTagConfig.setWorldName("");
        parkourTagConfig.saveOptions();

        createInstances(name, parkourTagConfig);
        return true;
    }

    private void createInstances(String mapName, ParkourTagConfig config) {
        int count = Math.max(1, config.getCopyCount());
        List<ParkourTagArea> instances = new ArrayList<>(count);
        for (int copyIndex = 0; copyIndex < count; copyIndex++) {
            instances.add(new ParkourTagArea(plugin, config, copyIndex, false));
        }
        registerMapInstances(mapName, instances);
    }

    @Override
    protected boolean allowsSharedMapWorlds() {
        return true;
    }

    @Override
    public synchronized boolean loadAreaAfterRename(
            @NotNull String name, @NotNull String worldName) {
        if (areas.containsKey(name)) return false;
        ParkourTagConfig config = new ParkourTagConfig(plugin, name);
        config.initializeConfiguration(plugin.getFolder());
        createInstances(name, config);
        return true;
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
        return (System.currentTimeMillis() - enderEyeUsedTimes.getOrDefault(championshipTeam, 0L))
                > 10000L;
    }

    public boolean canBeChaser(UUID uuid) {
        return chaserTimes.getOrDefault(uuid, 0) < CCConfig.PARKOUR_TAG_MAX_CHASER_TIMES;
    }

    public int getChaserTimes(UUID uuid) {
        return chaserTimes.getOrDefault(uuid, 0);
    }
}
