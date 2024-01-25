package ink.ziip.championshipscore.api.game.tntrun;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.manager.BaseAreaManager;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitScheduler;

import java.io.File;

public class TNTRunManager extends BaseAreaManager<TNTRunTeamArea> {

    public TNTRunManager(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
    }

    @Override
    public void load() {
        BukkitScheduler scheduler = plugin.getServer().getScheduler();
        File areasFolder = new File(plugin.getDataFolder() + File.separator + "tntrun");
        areasFolder.mkdirs();

        scheduler.runTask(plugin, task -> {
            String[] areaList = areasFolder.list();
            if (areaList != null) {
                for (String file : areaList) {
                    String name = file.substring(0, file.length() - 4);
                    areas.put(name, new TNTRunTeamArea(plugin, new TNTRunConfig(plugin, name), false, name));
                }
            }
        });
    }

    @Override
    public void unload() {
        for (TNTRunTeamArea area : areas.values()) {
            if (area.getGameStageEnum() != GameStageEnum.WAITING) {
                area.endGame();
            }
        }
    }

    @Override
    public boolean addArea(String name) {
        if (areas.containsKey(name))
            return false;

        plugin.getWorldManager().createEmptyWorld("tntrun_" + name, World.Environment.NORMAL);

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

        tntRunArea.saveMap();
        return true;
    }
}
