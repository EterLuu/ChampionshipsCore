package ink.ziip.championshipscore.api.game.parkourwarrior;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.manager.BaseAreaManager;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import ink.ziip.championshipscore.util.scheduler.FoliaScheduler;

import java.io.File;

public class ParkourWarriorManager extends BaseAreaManager<ParkourWarriorTeamArea> {
    public ParkourWarriorManager(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
    }

    @Override
    public void load() {
        FoliaScheduler scheduler = FoliaScheduler.global(plugin);
        File areasFolder = new File(plugin.getDataFolder() + File.separator + "parkourwarrior");
        areasFolder.mkdirs();

        scheduler.runTask(task -> {
            String[] areaList = areasFolder.list((d, n) -> n.toLowerCase().endsWith(".yml"));
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
        clearAreas();
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
