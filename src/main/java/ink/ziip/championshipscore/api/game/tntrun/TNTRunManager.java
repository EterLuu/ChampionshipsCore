package ink.ziip.championshipscore.api.game.tntrun;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.manager.BaseGameInstanceManager;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import org.bukkit.World;
import ink.ziip.championshipscore.util.scheduler.FoliaScheduler;

import java.io.File;

public class TNTRunManager extends BaseGameInstanceManager<TNTRunTeamArea> {

    public TNTRunManager(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
    }

    @Override
    public void load() {
        FoliaScheduler scheduler = FoliaScheduler.global(plugin);
        File areasFolder = new File(plugin.getDataFolder() + File.separator + "tntrun");
        areasFolder.mkdirs();

        scheduler.runTask(task -> {
            String[] areaList = areasFolder.list((d, n) -> n.toLowerCase().endsWith(".yml"));
            if (areaList != null) {
                for (String file : areaList) {
                    String name = file.substring(0, file.length() - 4);
                    TNTRunTeamArea area = new TNTRunTeamArea(plugin, new TNTRunConfig(plugin, name), false, name);
                    areas.put(name, area);
                    area.preloadMap();
                }
            }
        });
    }

    @Override
    public void unload() {
        for (TNTRunTeamArea area : areas.values()) {
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

        if (!plugin.getWorldManager().loadWorld("tntrun_" + name, World.Environment.NORMAL, false))
            return false;

        TNTRunConfig tntRunConfig = new TNTRunConfig(plugin, name);
        tntRunConfig.initializeConfiguration(plugin.getFolder());
        tntRunConfig.setAreaName(name);
        tntRunConfig.saveOptions();

        TNTRunTeamArea tntRunArea = new TNTRunTeamArea(plugin, tntRunConfig, true, name);
        areas.put(name, tntRunArea);

        return true;
    }

    public boolean saveArea(String name) {
        TNTRunTeamArea tntRunArea = areas.get(name);
        if (tntRunArea == null)
            return false;

        if (tntRunArea.getGameStageEnum() != GameStageEnum.WAITING) {
            return false;
        }

        return tntRunArea.saveMap(World.Environment.NORMAL);
    }
}
