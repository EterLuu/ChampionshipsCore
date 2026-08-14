package ink.ziip.championshipscore.api.game.tntrun;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.manager.BaseGameInstanceManager;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.concurrent.CompletableFuture;
import org.bukkit.scheduler.BukkitScheduler;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.jetbrains.annotations.NotNull;

public class TNTRunManager extends BaseGameInstanceManager<TNTRunTeamArea> {

    public TNTRunManager(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
    }

    @Override
    public void load() {
        BukkitScheduler scheduler = plugin.getServer().getScheduler();
        File areasFolder = new File(plugin.getDataFolder() + File.separator + "tntrun");
        areasFolder.mkdirs();

        scheduler.runTask(plugin, task -> {
            String[] areaList = areasFolder.list((d, n) -> n.toLowerCase().endsWith(".yml"));
            if (areaList == null) return;
            Arrays.sort(areaList);
            Set<String> loadedWorlds = new HashSet<>();
            for (String file : areaList) {
                String name = file.substring(0, file.length() - 4);
                File configFile = new File(areasFolder, file);
                YamlConfiguration raw = YamlConfiguration.loadConfiguration(configFile);
                String worldName = raw.getString("world-name", "tntrun");
                boolean pending = raw.contains("world-name") && (worldName == null || worldName.isBlank());
                if (!pending) {
                    if (worldName == null || worldName.isBlank()) worldName = "tntrun";
                    if (loadedWorlds.add(worldName) && !loadArenaWorld(worldName)) {
                        loadedWorlds.remove(worldName);
                        continue;
                    }
                }
                TNTRunConfig config = new TNTRunConfig(plugin, name);
                config.initializeConfiguration(plugin.getFolder());
                TNTRunTeamArea area = new TNTRunTeamArea(plugin, config, false, name);
                areas.put(name, area);
                area.initializeInSharedWorld();
            }
        });
    }

    @Override
    public void unload() {
        for (TNTRunTeamArea area : areas.values()) {
            if (area.getGameStageEnum() != GameStageEnum.WAITING) {
                area.abortAndReset();
            }
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
        TNTRunConfig tntRunConfig = new TNTRunConfig(plugin, name);
        tntRunConfig.initializeConfiguration(plugin.getFolder());
        tntRunConfig.setAreaName(name);
        tntRunConfig.bindConfiguredWorld("");
        tntRunConfig.saveOptions();

        TNTRunTeamArea tntRunArea = new TNTRunTeamArea(plugin, tntRunConfig, true, name);
        areas.put(name, tntRunArea);

        return true;
    }

    @Override
    public synchronized boolean loadAreaAfterRename(@NotNull String name, @NotNull String worldName) {
        if (areas.containsKey(name)) return false;
        TNTRunConfig config = new TNTRunConfig(plugin, name);
        config.initializeConfiguration(plugin.getFolder());
        TNTRunTeamArea area = new TNTRunTeamArea(plugin, config, false, name);
        areas.put(name, area);
        area.initializeInSharedWorld();
        return true;
    }

    public CompletableFuture<Boolean> saveArea(String name) {
        TNTRunTeamArea tntRunArea = areas.get(name);
        if (tntRunArea == null)
            return CompletableFuture.completedFuture(false);

        if (tntRunArea.getGameStageEnum() != GameStageEnum.WAITING) {
            return CompletableFuture.completedFuture(false);
        }

        World world = plugin.getServer().getWorld(tntRunArea.getWorldName());
        if (world == null) return CompletableFuture.completedFuture(false);
        world.save();
        return CompletableFuture.completedFuture(true);
    }

    @Override
    protected boolean allowsSharedMapWorlds() {
        return true;
    }
}
