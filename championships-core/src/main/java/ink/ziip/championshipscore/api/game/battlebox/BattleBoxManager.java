package ink.ziip.championshipscore.api.game.battlebox;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.battlebox.config.BattleBoxConfig;
import ink.ziip.championshipscore.api.game.battlebox.runtime.BattleBoxArea;
import ink.ziip.championshipscore.api.game.manager.BaseGameInstanceManager;

import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

public class BattleBoxManager extends BaseGameInstanceManager<BattleBoxArea> {

    public BattleBoxManager(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
    }

    @Override
    public void load() {
        deferMapLoad(
                () -> {
                    Set<String> loadedWorlds = new HashSet<>();
                    loadMapDefinitions(
                            new File(plugin.getDataFolder(), "battlebox"),
                            (name, file) -> {
                                YamlConfiguration raw = YamlConfiguration.loadConfiguration(file);
                                String worldName = raw.getString("world-name", "");
                                if (worldName == null || worldName.isBlank()) {
                                    BattleBoxConfig config = new BattleBoxConfig(plugin, name);
                                    config.initializeConfiguration(plugin.getFolder());
                                    createInstances(name, config);
                                    return;
                                }
                                if (loadedWorlds.add(worldName) && !loadArenaWorld(worldName)) {
                                    loadedWorlds.remove(worldName);
                                    return;
                                }
                                BattleBoxConfig config = new BattleBoxConfig(plugin, name);
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
        BattleBoxConfig battleBoxConfig = new BattleBoxConfig(plugin, name);
        battleBoxConfig.initializeConfiguration(plugin.getFolder());
        battleBoxConfig.setAreaName(name);
        battleBoxConfig.setWorldName("");
        battleBoxConfig.saveOptions();

        createInstances(name, battleBoxConfig);
        return true;
    }

    /**
     * Returns the map's permanently allocated instances, growing the pool after a prepare count
     * change.
     */
    private void createInstances(String mapName, BattleBoxConfig config) {
        createMapInstances(
                mapName,
                config.getCopyCount(),
                index -> new BattleBoxArea(plugin, config, index, false));
    }

    @Override
    protected boolean allowsSharedMapWorlds() {
        return true;
    }

    @Override
    public synchronized boolean loadAreaAfterRename(
            @NotNull String name, @NotNull String worldName) {
        if (areas.containsKey(name)) return false;
        BattleBoxConfig config = new BattleBoxConfig(plugin, name);
        config.initializeConfiguration(plugin.getFolder());
        createInstances(name, config);
        return true;
    }
}
