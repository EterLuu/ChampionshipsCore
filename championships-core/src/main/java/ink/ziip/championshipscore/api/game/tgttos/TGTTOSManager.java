package ink.ziip.championshipscore.api.game.tgttos;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.manager.BaseGameInstanceManager;
import ink.ziip.championshipscore.api.game.tgttos.config.TGTTOSConfig;
import ink.ziip.championshipscore.api.game.tgttos.runtime.TGTTOSTeamArea;

import java.io.File;

public class TGTTOSManager extends BaseGameInstanceManager<TGTTOSTeamArea> {
    public TGTTOSManager(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
    }

    @Override
    public void load() {
        if (!loadArenaWorld("tgttos")) return;
        deferMapLoad(
                () -> {
                    loadMapDefinitions(
                            new File(plugin.getDataFolder(), "tgttos"),
                            (name, file) -> {
                                areas.put(
                                        name,
                                        new TGTTOSTeamArea(plugin, new TGTTOSConfig(plugin, name)));
                            });
                });
    }

    @Override
    public boolean addArea(String name) {
        if (areas.containsKey(name)) return false;

        TGTTOSConfig tgttosConfig = new TGTTOSConfig(plugin, name);
        tgttosConfig.initializeConfiguration(plugin.getFolder());
        tgttosConfig.setAreaName(name);
        tgttosConfig.saveOptions();

        TGTTOSTeamArea tgttosTeamArea =
                areas.putIfAbsent(name, new TGTTOSTeamArea(plugin, tgttosConfig));

        return tgttosTeamArea == null;
    }

    @Override
    protected boolean allowsSharedMapWorlds() {
        return true;
    }
}
