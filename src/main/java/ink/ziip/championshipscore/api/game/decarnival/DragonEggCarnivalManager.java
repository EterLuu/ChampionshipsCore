package ink.ziip.championshipscore.api.game.decarnival;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.manager.BaseAreaManager;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitScheduler;

import java.io.File;

public class DragonEggCarnivalManager extends BaseAreaManager<DragonEggCarnivalArea> {

    public DragonEggCarnivalManager(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
    }

    @Override
    public void load() {
        BukkitScheduler scheduler = plugin.getServer().getScheduler();
        File areasFolder = new File(plugin.getDataFolder() + File.separator + "decarnival");
        areasFolder.mkdirs();

        scheduler.runTask(plugin, task -> {
            String[] areaList = areasFolder.list();
            if (areaList != null) {
                for (String file : areaList) {
                    String name = file.substring(0, file.length() - 4);
                    areas.put(name, new DragonEggCarnivalArea(plugin, new DragonEggCarnivalConfig(plugin, name), false, name));
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
    }

    @Override
    public boolean addArea(String name) {
        if (areas.containsKey(name))
            return false;

        plugin.getWorldManager().createEmptyWorld("decarnival_" + name, World.Environment.THE_END);

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

        dragonEggCarnivalArea.saveMap();
        return true;
    }
}
