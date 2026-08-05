package ink.ziip.championshipscore.api.game.tntrun;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.manager.BaseGameInstanceManager;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import org.bukkit.World;

import java.util.concurrent.CompletableFuture;
import org.bukkit.scheduler.BukkitScheduler;

import java.io.File;

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

    public CompletableFuture<Boolean> saveArea(String name) {
        TNTRunTeamArea tntRunArea = areas.get(name);
        if (tntRunArea == null)
            return CompletableFuture.completedFuture(false);

        if (tntRunArea.getGameStageEnum() != GameStageEnum.WAITING) {
            return CompletableFuture.completedFuture(false);
        }

        return tntRunArea.saveMap(World.Environment.NORMAL);
    }
}
