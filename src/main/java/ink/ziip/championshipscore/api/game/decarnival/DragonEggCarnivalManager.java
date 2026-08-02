package ink.ziip.championshipscore.api.game.decarnival;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.manager.BaseGameInstanceManager;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import org.bukkit.World;
import ink.ziip.championshipscore.util.scheduler.FoliaScheduler;

import java.io.File;

public class DragonEggCarnivalManager extends BaseGameInstanceManager<DragonEggCarnivalArea> {

    public DragonEggCarnivalManager(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
    }

    @Override
    public void load() {
        FoliaScheduler scheduler = FoliaScheduler.global(plugin);
        File areasFolder = new File(plugin.getDataFolder() + File.separator + "decarnival");
        areasFolder.mkdirs();

        scheduler.runTask(task -> {
            String[] areaList = areasFolder.list((d, n) -> n.toLowerCase().endsWith(".yml"));
            if (areaList != null) {
                for (String file : areaList) {
                    String name = file.substring(0, file.length() - 4);
                    DragonEggCarnivalArea area = new DragonEggCarnivalArea(plugin, new DragonEggCarnivalConfig(plugin, name), false, name);
                    areas.put(name, area);
                    area.preloadMap();
                }
            }
        });
    }

    @Override
    public void unload() {
        for (DragonEggCarnivalArea area : areas.values()) {
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

        if (!plugin.getWorldManager().loadWorld("decarnival_" + name, World.Environment.THE_END, false))
            return false;

        DragonEggCarnivalConfig dragonEggCarnivalConfig = new DragonEggCarnivalConfig(plugin, name);
        dragonEggCarnivalConfig.initializeConfiguration(plugin.getFolder());
        dragonEggCarnivalConfig.setAreaName(name);
        dragonEggCarnivalConfig.saveOptions();

        DragonEggCarnivalArea dragonEggCarnivalArea = new DragonEggCarnivalArea(plugin, dragonEggCarnivalConfig, true, name);
        areas.put(name, dragonEggCarnivalArea);

        return true;
    }

    public boolean saveArea(String name) {
        DragonEggCarnivalArea dragonEggCarnivalArea = areas.get(name);
        if (dragonEggCarnivalArea == null)
            return false;

        if (dragonEggCarnivalArea.getGameStageEnum() != GameStageEnum.WAITING) {
            return false;
        }

        return dragonEggCarnivalArea.saveMap(World.Environment.THE_END);
    }
}
