package ink.ziip.championshipscore.api.game.decarnival;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.manager.BaseGameInstanceManager;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import org.bukkit.World;

import java.util.concurrent.CompletableFuture;
import org.bukkit.scheduler.BukkitScheduler;

import java.io.File;

public class DragonEggCarnivalManager extends BaseGameInstanceManager<DragonEggCarnivalArea> {

    public DragonEggCarnivalManager(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
    }

    @Override
    public void load() {
        BukkitScheduler scheduler = plugin.getServer().getScheduler();
        File areasFolder = new File(plugin.getDataFolder() + File.separator + "decarnival");
        areasFolder.mkdirs();

        scheduler.runTask(plugin, task -> {
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
        return false;
    }

    @Override
    public boolean addArea(String name, String worldName) {
        if (areas.containsKey(name)) return false;
        DragonEggCarnivalConfig dragonEggCarnivalConfig = new DragonEggCarnivalConfig(plugin, name);
        dragonEggCarnivalConfig.initializeConfiguration(plugin.getFolder());
        dragonEggCarnivalConfig.setAreaName(name);
        dragonEggCarnivalConfig.bindConfiguredWorld("");
        dragonEggCarnivalConfig.saveOptions();

        DragonEggCarnivalArea dragonEggCarnivalArea = new DragonEggCarnivalArea(plugin, dragonEggCarnivalConfig, true, name);
        areas.put(name, dragonEggCarnivalArea);

        return true;
    }

    public CompletableFuture<Boolean> saveArea(String name) {
        DragonEggCarnivalArea dragonEggCarnivalArea = areas.get(name);
        if (dragonEggCarnivalArea == null)
            return CompletableFuture.completedFuture(false);

        if (dragonEggCarnivalArea.getGameStageEnum() != GameStageEnum.WAITING) {
            return CompletableFuture.completedFuture(false);
        }

        return dragonEggCarnivalArea.saveMap(World.Environment.THE_END);
    }
}
