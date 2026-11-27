package ink.ziip.championshipscore.api.game.acerace;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.acerace.config.AceRaceConfig;
import ink.ziip.championshipscore.api.game.acerace.runtime.AceRaceArea;
import ink.ziip.championshipscore.api.game.manager.BaseGameInstanceManager;
import ink.ziip.championshipscore.api.game.model.GameTypeEnum;

import org.bukkit.GameRules;
import org.bukkit.World;

import java.io.File;

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
        createMapInstances(
                mapName,
                plugin.getConfigurationManager()
                        .getCCConfig()
                        .dailyGame(GameTypeEnum.AceRace)
                        .concurrentInstances(),
                index -> new AceRaceArea(plugin, config, index, false));
    }

    @Override
    protected boolean allowsSharedMapWorlds() {
        return true;
    }
}
