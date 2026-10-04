package ink.ziip.championshipscore.api.game.tntrun;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.manager.BaseGameInstanceManager;
import ink.ziip.championshipscore.api.game.model.GameStageEnum;
import ink.ziip.championshipscore.api.game.tntrun.config.TNTRunConfig;
import ink.ziip.championshipscore.api.game.tntrun.runtime.TNTRunTeamArea;

import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class TNTRunManager extends BaseGameInstanceManager<TNTRunTeamArea> {

    public TNTRunManager(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
    }

    @Override
    public void load() {
        deferMapLoad(
                () -> {
                    Set<String> loadedWorlds = new HashSet<>();
                    loadMapDefinitions(
                            new File(plugin.getDataFolder(), "tntrun"),
                            (name, file) -> {
                                YamlConfiguration raw = YamlConfiguration.loadConfiguration(file);
                                String worldName = raw.getString("world-name", "");
                                boolean pending = worldName == null || worldName.isBlank();
                                if (!pending) {
                                    if (loadedWorlds.add(worldName) && !loadArenaWorld(worldName)) {
                                        loadedWorlds.remove(worldName);
                                        return;
                                    }
                                }
                                TNTRunConfig config = new TNTRunConfig(plugin, name);
                                config.initializeConfiguration(plugin.getFolder());
                                TNTRunTeamArea area =
                                        new TNTRunTeamArea(plugin, config, false, name);
                                areas.put(name, area);
                                area.initializeInSharedWorld();
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
    public synchronized boolean loadAreaAfterRename(
            @NotNull String name, @NotNull String worldName) {
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
        if (tntRunArea == null) return CompletableFuture.completedFuture(false);

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
