package ink.ziip.championshipscore.api.game.acerace;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.acerace.config.AceRaceConfig;
import ink.ziip.championshipscore.api.game.acerace.runtime.AceRaceArea;
import ink.ziip.championshipscore.api.game.manager.BaseGameInstanceManager;
import ink.ziip.championshipscore.configuration.config.CCConfig;

import org.bukkit.GameRules;
import org.bukkit.World;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class AceRaceManager extends BaseGameInstanceManager<AceRaceArea> {

    public AceRaceManager(ChampionshipsCore plugin) {
        super(plugin);
    }

    @Override
    public void load() {
        if (!loadArenaWorld("acerace")) return;
        World world = plugin.getServer().getWorld("acerace");
        if (world != null) world.setGameRule(GameRules.FALL_DAMAGE, false);
        deferMapLoad(
                () -> {
                    loadMapDefinitions(
                            new File(plugin.getDataFolder(), "acerace"),
                            (name, file) -> {
                                AceRaceConfig config = new AceRaceConfig(plugin, name);
                                config.initializeConfiguration(plugin.getFolder());
                                createInstances(name, config);
                            });
                });
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

    private void createInstances(String mapName, AceRaceConfig config) {
        int count = Math.max(1, CCConfig.DAILY_ACERACE_CONCURRENT_INSTANCES);
        List<AceRaceArea> instances = new ArrayList<>(count);
        for (int copyIndex = 0; copyIndex < count; copyIndex++) {
            instances.add(new AceRaceArea(plugin, config, copyIndex, false));
        }
        registerMapInstances(mapName, instances);
    }

    @Override
    protected boolean allowsSharedMapWorlds() {
        return true;
    }
}
