package ink.ziip.championshipscore.api.game.tgttos;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.manager.BaseAreaManager;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import ink.ziip.championshipscore.util.scheduler.FoliaScheduler;

import java.io.File;

public class TGTTOSManager extends BaseAreaManager<TGTTOSTeamArea> {
    public TGTTOSManager(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
    }

    @Override
    public void load() {
        FoliaScheduler scheduler = FoliaScheduler.global(plugin);
        File areasFolder = new File(plugin.getDataFolder() + File.separator + "tgttos");
        areasFolder.mkdirs();

        scheduler.runTask(task -> {
            String[] areaList = areasFolder.list((d, n) -> n.toLowerCase().endsWith(".yml"));
            if (areaList != null) {
                for (String file : areaList) {
                    String name = file.substring(0, file.length() - 4);
                    areas.put(name, new TGTTOSTeamArea(plugin, new TGTTOSConfig(plugin, name)));
                }
            }
        });
    }

    @Override
    public void unload() {
        for (TGTTOSTeamArea area : areas.values()) {
            if (area.getGameStageEnum() != GameStageEnum.WAITING) {
                area.endGameFinally();
            }
        }
        clearAreas();
    }

    @Override
    public boolean addArea(String name) {
        if (areas.containsKey(name))
            return false;

        TGTTOSConfig tgttosConfig = new TGTTOSConfig(plugin, name);
        tgttosConfig.initializeConfiguration(plugin.getFolder());
        tgttosConfig.setAreaName(name);
        tgttosConfig.saveOptions();

        TGTTOSTeamArea tgttosTeamArea = areas.putIfAbsent(name, new TGTTOSTeamArea(plugin, tgttosConfig));

        return tgttosTeamArea == null;
    }
}
