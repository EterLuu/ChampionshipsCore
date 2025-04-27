package ink.ziip.championshipscore.api.game.advancementcc;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.manager.BaseAreaManager;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import org.bukkit.scheduler.BukkitScheduler;

import java.io.File;

public class AdvancementCCManager extends BaseAreaManager<AdvancementCCArea> {
    public AdvancementCCManager(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
    }

    @Override
    public void load() {
        BukkitScheduler scheduler = plugin.getServer().getScheduler();
        File areasFolder = new File(plugin.getDataFolder() + File.separator + "acc");
        areasFolder.mkdirs();

        scheduler.runTask(plugin, task -> {
            String[] areaList = areasFolder.list();
            if (areaList != null) {
                for (String file : areaList) {
                    String name = file.substring(0, file.length() - 4);
                    areas.put(name, new AdvancementCCArea(plugin, new AdvancementCCConfig(plugin, name)));
                }
            }
        });
    }

    @Override
    public void unload() {
        for (AdvancementCCArea area : areas.values()) {
            if (area.getGameStageEnum() != GameStageEnum.WAITING) {
                area.endGameFinally();
            }
        }
    }

    @Override
    public boolean addArea(String name) {
        if (areas.containsKey(name))
            return false;

        AdvancementCCConfig advancementCCConfig = new AdvancementCCConfig(plugin, name);
        advancementCCConfig.initializeConfiguration(plugin.getFolder());
        advancementCCConfig.setAreaName(name);
        advancementCCConfig.saveOptions();

        AdvancementCCArea advancementCCArea = areas.putIfAbsent(name, new AdvancementCCArea(plugin, advancementCCConfig));

        return advancementCCArea == null;
    }
}
