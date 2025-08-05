package ink.ziip.championshipscore.api.game.bingo;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.manager.BaseAreaManager;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import org.bukkit.scheduler.BukkitScheduler;

import java.io.File;

public class BingoSpectatorAreaManager extends BaseAreaManager<BingoTeamArea> {

    public BingoSpectatorAreaManager(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
    }

    @Override
    public void load() {
        BukkitScheduler scheduler = plugin.getServer().getScheduler();
        File areasFolder = new File(plugin.getDataFolder() + File.separator + "bingo");
        areasFolder.mkdirs();

        scheduler.runTask(plugin, task -> {
            String[] areaList = areasFolder.list();
            if (areaList != null) {
                for (String file : areaList) {
                    String name = file.substring(0, file.length() - 4);
                    areas.put(name, new BingoTeamArea(plugin, null, new BingoConfig(plugin, name)));
                }
            }
        });
    }

    @Override
    public void unload() {
        for (BingoTeamArea area : areas.values()) {
            if (area.getGameStageEnum() != GameStageEnum.WAITING) {
                area.endGameFinally();
            }
        }
    }

    @Override
    public boolean addArea(String name) {
        if (areas.containsKey(name))
            return false;

        BingoTeamArea bingoTeamArea = areas.putIfAbsent(name, new BingoTeamArea(plugin, null, new BingoConfig(plugin, name)));

        return bingoTeamArea == null;
    }
}
