package ink.ziip.championshipscore.api.game.tgttos;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.manager.BaseAreaManager;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import org.bukkit.scheduler.BukkitScheduler;

import java.io.File;

public class TGTTOSManager extends BaseAreaManager<TGTTOSTeamArea> {
    public TGTTOSManager(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
    }

    @Override
    public void load() {
        BukkitScheduler scheduler = plugin.getServer().getScheduler();
        File areasFolder = new File(plugin.getDataFolder() + File.separator + "tgttos");
        areasFolder.mkdirs();

        scheduler.runTask(plugin, task -> {
            String[] areaList = areasFolder.list();
            if (areaList != null) {
                for (String file : areaList) {
                    String name = file.substring(0, file.length() - 4);
                    areas.put(name, new TGTTOSTeamArea(plugin, new TGTTOSConfig(plugin, name)));
                }
            }
        });
    }

    @Override
    public void unload() {
        for (TGTTOSTeamArea area : areas.values()) {
            if (area.getGameStageEnum() != GameStageEnum.WAITING) {
                area.endGame();
            }
        }
    }

    @Override
    public boolean addArea(String name) {
        if (areas.containsKey(name))
            return false;

        TGTTOSConfig tgttosConfig = new TGTTOSConfig(plugin, name);
        tgttosConfig.initializeConfiguration(plugin.getFolder());
        tgttosConfig.setAreaName(name);
        tgttosConfig.saveOptions();

        TGTTOSTeamArea tgttosTeamArea = areas.putIfAbsent(name, new TGTTOSTeamArea(plugin, tgttosConfig));

        return tgttosTeamArea == null;
    }
}
