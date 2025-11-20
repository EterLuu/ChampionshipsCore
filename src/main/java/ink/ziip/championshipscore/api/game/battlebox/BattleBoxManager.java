package ink.ziip.championshipscore.api.game.battlebox;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.manager.BaseAreaManager;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import org.bukkit.scheduler.BukkitScheduler;

import java.io.File;

public class BattleBoxManager extends BaseAreaManager<BattleBoxArea> {

    public BattleBoxManager(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
    }

    @Override
    public void load() {
        BukkitScheduler scheduler = plugin.getServer().getScheduler();
        File areasFolder = new File(plugin.getDataFolder() + File.separator + "battlebox");
        areasFolder.mkdirs();

        scheduler.runTask(plugin, task -> {
            String[] areaList = areasFolder.list();
            if (areaList != null) {
                for (String file : areaList) {
                    String name = file.substring(0, file.length() - 4);
                    areas.put(name, new BattleBoxArea(plugin, new BattleBoxConfig(plugin, name)));
                }
            }
        });
    }

    @Override
    public void unload() {
        for (BattleBoxArea area : areas.values()) {
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

        BattleBoxConfig battleBoxConfig = new BattleBoxConfig(plugin, name);
        battleBoxConfig.initializeConfiguration(plugin.getFolder());
        battleBoxConfig.setAreaName(name);
        battleBoxConfig.saveOptions();

        BattleBoxArea battleBoxArea = areas.putIfAbsent(name, new BattleBoxArea(plugin, battleBoxConfig));

        return battleBoxArea == null;
    }
}
