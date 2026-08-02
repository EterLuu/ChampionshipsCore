package ink.ziip.championshipscore.api.game.skywars;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.manager.BaseGameInstanceManager;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import ink.ziip.championshipscore.util.scheduler.FoliaScheduler;
import org.bukkit.World;

import java.io.File;

public class SkyWarsManager extends BaseGameInstanceManager<SkyWarsTeamArea> {
    private final SkyWarsVariantRegistry variantRegistry;

    public SkyWarsManager(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
        variantRegistry = new SkyWarsVariantRegistry(championshipsCore);
    }

    @Override
    public void load() {
        variantRegistry.load();
        File areasFolder = new File(plugin.getDataFolder() + File.separator + "skywars");
        areasFolder.mkdirs();

        FoliaScheduler.global(plugin).runTask(task -> {
            String[] areaList = areasFolder.list((d, n) -> n.toLowerCase().endsWith(".yml"));
            if (areaList != null) {
                for (String file : areaList) {
                    String name = file.substring(0, file.length() - 4);
                    SkyWarsTeamArea area = new SkyWarsTeamArea(plugin, new SkyWarsConfig(plugin, name),
                            false, name, variantRegistry);
                    areas.put(name, area);
                    area.preloadMap();
                }
            }
        });
    }

    @Override
    public void unload() {
        for (SkyWarsTeamArea area : areas.values()) {
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

        if (!plugin.getWorldManager().loadWorld("skywars_" + name, World.Environment.NORMAL, false))
            return false;

        SkyWarsConfig skyWarsConfig = new SkyWarsConfig(plugin, name);
        skyWarsConfig.initializeConfiguration(plugin.getFolder());
        skyWarsConfig.setAreaName(name);
        skyWarsConfig.saveOptions();

        SkyWarsTeamArea skyWarsArea = new SkyWarsTeamArea(plugin, skyWarsConfig, true, name, variantRegistry);
        areas.put(name, skyWarsArea);

        return true;
    }

    public boolean saveArea(String name) {
        SkyWarsTeamArea skyWarsArea = areas.get(name);
        if (skyWarsArea == null)
            return false;

        if (skyWarsArea.getGameStageEnum() != GameStageEnum.WAITING) {
            return false;
        }

        return skyWarsArea.saveMap(World.Environment.NORMAL);
    }
}
