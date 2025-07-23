package ink.ziip.championshipscore.api.game.parkourwarrior;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.manager.BaseAreaManager;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import org.bukkit.scheduler.BukkitScheduler;

import java.io.File;

public class ParkourWarriorManager extends BaseAreaManager<ParkourWarriorTeamArea> {
    public ParkourWarriorManager(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
    }

    @Override
    public void load() {
        BukkitScheduler scheduler = plugin.getServer().getScheduler();
        File areasFolder = new File(plugin.getDataFolder() + File.separator + "parkourwarrior");
        areasFolder.mkdirs();

        scheduler.runTask(plugin, task -> {
            String[] areaList = areasFolder.list();
            if (areaList != null) {
                for (String file : areaList) {
                    String name = file.substring(0, file.length() - 4);
                    areas.put(name, new ParkourWarriorTeamArea(plugin, new ParkourWarriorConfig(plugin, name)));
                }
            }
        });
    }

    @Override
    public void unload() {
        for (ParkourWarriorTeamArea area : areas.values()) {
            if (area.getGameStageEnum() != GameStageEnum.WAITING) {
                area.endGameFinally();
            }
        }
    }

    @Override
    public boolean addArea(String name) {
        if (areas.containsKey(name))
            return false;

        ParkourWarriorConfig parkourWarriorConfig = new ParkourWarriorConfig(plugin, name);
        parkourWarriorConfig.initializeConfiguration(plugin.getFolder());
        parkourWarriorConfig.setAreaName(name);
        parkourWarriorConfig.saveOptions();

        ParkourWarriorTeamArea parkourWarriorTeamArea = areas.putIfAbsent(name, new ParkourWarriorTeamArea(plugin, parkourWarriorConfig));

        return parkourWarriorTeamArea == null;
    }

}
