package ink.ziip.championshipscore.api.game.hotycodydusky;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.manager.BaseAreaManager;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import org.bukkit.scheduler.BukkitScheduler;

import java.io.File;

public class HotyCodyDuskyManager extends BaseAreaManager<HotyCodyDuskyTeamArea> {

    public HotyCodyDuskyManager(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
    }

    @Override
    public void load() {
        BukkitScheduler scheduler = plugin.getServer().getScheduler();
        File areasFolder = new File(plugin.getDataFolder() + File.separator + "hotycodydusky");
        areasFolder.mkdirs();

        scheduler.runTask(plugin, task -> {
            String[] areaList = areasFolder.list();
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