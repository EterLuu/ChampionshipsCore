package ink.ziip.championshipscore.api.game.skywars;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.manager.BaseAreaManager;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import org.bukkit.World;
import ink.ziip.championshipscore.util.scheduler.FoliaScheduler;

import java.io.File;

public class SkyWarsManager extends BaseAreaManager<SkyWarsTeamArea> {
    public SkyWarsManager(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
    }

    @Override
    public void load() {
        FoliaScheduler scheduler = FoliaScheduler.global(plugin);
        File areasFolder = new File(plugin.getDataFolder() + File.separator + "skywars");
        areasFolder.mkdirs();

        scheduler.runTask(task -> {
            String[] areaList = areasFolder.list((d, n) -> n.toLowerCase().endsWith(".yml"));
            if (areaList != null) {
                for (String file : areaList) {
                    String name = file.substring(0, file.length() - 4);
                    areas.put(name, new SkyWarsTeamArea(plugin, new SkyWarsConfig(plugin, name), false, name));
                }
            }
        });
    }

    @Override
    public void unload() {
        for (SkyWarsTeamArea area : areas.values()) {
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

        plugin.getWorldManager().createEmptyWorld("skywars_" + name, World.Environment.NORMAL);

        SkyWarsConfig skyWarsConfig = new SkyWarsConfig(plugin, name);
        skyWarsConfig.initializeConfiguration(plugin.getFolder());
        skyWarsConfig.setAreaName(name);
        skyWarsConfig.saveOptions();

        SkyWarsTeamArea skyWarsArea = new SkyWarsTeamArea(plugin, skyWarsConfig, true, name);
        areas.put(name, skyWarsArea);

        return true;
    }

    public boolean saveArea(String name) {
        SkyWarsTeamArea skyWarsArea = areas.get(name);
        if (skyWarsArea == null)
            return false;

        if (skyWarsArea.getGameStageEnum() != GameStageEnum.WAITING) {
            return false;
        }

        skyWarsArea.saveMap(World.Environment.NORMAL);
        return true;
    }
}
