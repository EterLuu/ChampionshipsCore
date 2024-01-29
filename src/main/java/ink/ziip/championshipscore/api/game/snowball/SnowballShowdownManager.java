package ink.ziip.championshipscore.api.game.snowball;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.manager.BaseAreaManager;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import org.bukkit.scheduler.BukkitScheduler;

import java.io.File;

public class SnowballShowdownManager extends BaseAreaManager<SnowballShowdownTeamArea> {
    public SnowballShowdownManager(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
    }

    @Override
    public void load() {
        BukkitScheduler scheduler = plugin.getServer().getScheduler();
        File areasFolder = new File(plugin.getDataFolder() + File.separator + "snowball");
        areasFolder.mkdirs();

        scheduler.runTask(plugin, task -> {
            String[] areaList = areasFolder.list();
            if (areaList != null) {
                for (String file : areaList) {
                    String name = file.substring(0, file.length() - 4);
                    areas.put(name, new SnowballShowdownTeamArea(plugin, new SnowballShowdownConfig(plugin, name)));
                }
            }
        });
    }

    @Override
    public void unload() {
        for (SnowballShowdownTeamArea area : areas.values()) {
            if (area.getGameStageEnum() != GameStageEnum.WAITING) {
                area.endGameFinally();
            }
        }
    }

    @Override
    public boolean addArea(String name) {
        if (areas.containsKey(name))
            return false;

        SnowballShowdownConfig snowballShowdownConfig = new SnowballShowdownConfig(plugin, name);
        snowballShowdownConfig.initializeConfiguration(plugin.getFolder());
        snowballShowdownConfig.setAreaName(name);
        snowballShowdownConfig.saveOptions();

        SnowballShowdownTeamArea snowballShowdownTeamArea = areas.putIfAbsent(name, new SnowballShowdownTeamArea(plugin, snowballShowdownConfig));

        return snowballShowdownTeamArea == null;
    }
}