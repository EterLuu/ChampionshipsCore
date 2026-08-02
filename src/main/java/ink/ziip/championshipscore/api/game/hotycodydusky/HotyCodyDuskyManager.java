package ink.ziip.championshipscore.api.game.hotycodydusky;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.manager.BaseGameInstanceManager;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import ink.ziip.championshipscore.util.scheduler.FoliaScheduler;

import java.io.File;

public class HotyCodyDuskyManager extends BaseGameInstanceManager<HotyCodyDuskyTeamArea> {

    public HotyCodyDuskyManager(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
    }

    @Override
    public void load() {
        if (!loadArenaWorld("hotycodydusky"))
            return;

        File areasFolder = new File(plugin.getDataFolder() + File.separator + "hotycodydusky");
        areasFolder.mkdirs();

        FoliaScheduler.global(plugin).runTask(task -> {
            String[] areaList = areasFolder.list((d, n) -> n.toLowerCase().endsWith(".yml"));
            if (areaList != null) {
                for (String file : areaList) {
                    String name = file.substring(0, file.length() - 4);
                    areas.put(name, new HotyCodyDuskyTeamArea(plugin, new HotyCodyDuskyConfig(plugin, name)));
                }
            }
        });
    }

    @Override
    public void unload() {
        for (HotyCodyDuskyTeamArea area : areas.values()) {
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

        HotyCodyDuskyConfig hotyCodyDuskyConfig = new HotyCodyDuskyConfig(plugin, name);
        hotyCodyDuskyConfig.initializeConfiguration(plugin.getFolder());
        hotyCodyDuskyConfig.setAreaName(name);
        hotyCodyDuskyConfig.saveOptions();

        HotyCodyDuskyTeamArea hotyCodyDuskyTeamArea = areas.putIfAbsent(name, new HotyCodyDuskyTeamArea(plugin, hotyCodyDuskyConfig));

        return hotyCodyDuskyTeamArea == null;
    }
}
