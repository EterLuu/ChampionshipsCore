package ink.ziip.championshipscore.api.game.dodgebolt;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.manager.BaseGameInstanceManager;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import ink.ziip.championshipscore.util.scheduler.FoliaScheduler;
import org.bukkit.World;

import java.io.File;

public final class DodgeboltManager extends BaseGameInstanceManager<DodgeboltArea> {
    public DodgeboltManager(ChampionshipsCore plugin) {
        super(plugin);
    }

    @Override
    public void load() {
        File folder = new File(plugin.getDataFolder(), "dodgebolt");
        folder.mkdirs();
        FoliaScheduler.global(plugin).runTask(task -> {
            String[] files = folder.list((dir, name) -> name.toLowerCase(java.util.Locale.ROOT).endsWith(".yml"));
            if (files == null) return;
            for (String file : files) {
                String name = file.substring(0, file.length() - 4);
                DodgeboltArea area = new DodgeboltArea(plugin, new DodgeboltConfig(plugin, name), false, name);
                areas.put(name, area);
                area.preloadMap();
            }
        });
    }

    @Override
    public void unload() {
        for (DodgeboltArea area : areas.values()) {
            if (area.getGameStageEnum() != GameStageEnum.WAITING) area.stopMatch();
        }
        clearAreas();
    }

    @Override
    public boolean addArea(String name) {
        if (areas.containsKey(name)) return false;
        if (!plugin.getWorldManager().loadWorld("dodgebolt_" + name, World.Environment.NORMAL, false))
            return false;
        DodgeboltConfig config = new DodgeboltConfig(plugin, name);
        config.initializeConfiguration(plugin.getFolder());
        config.setAreaName(name);
        config.saveOptions();
        DodgeboltArea area = new DodgeboltArea(plugin, config, true, name);
        areas.put(name, area);
        return true;
    }

    public boolean saveArea(String name) {
        DodgeboltArea area = areas.get(name);
        return area != null && area.getGameStageEnum() == GameStageEnum.WAITING
                && area.saveMap(World.Environment.NORMAL);
    }
}
