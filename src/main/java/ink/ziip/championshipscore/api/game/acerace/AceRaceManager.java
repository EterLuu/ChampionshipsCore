package ink.ziip.championshipscore.api.game.acerace;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.manager.BaseGameInstanceManager;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import org.bukkit.GameRules;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitScheduler;

import java.io.File;

public class AceRaceManager extends BaseGameInstanceManager<AceRaceArea> {
    public AceRaceManager(ChampionshipsCore plugin) {
        super(plugin);
    }

    @Override
    public void load() {
        if (!loadArenaWorld("acerace")) return;
        World world = plugin.getServer().getWorld("acerace");
        if (world != null) world.setGameRule(GameRules.FALL_DAMAGE, false);
        File areasFolder = new File(plugin.getDataFolder(), "acerace");
        areasFolder.mkdirs();
        BukkitScheduler scheduler = plugin.getServer().getScheduler();
        scheduler.runTask(plugin, task -> {
            String[] areaList = areasFolder.list((directory, name) -> name.toLowerCase().endsWith(".yml"));
            if (areaList == null) return;
            for (String file : areaList) {
                String name = file.substring(0, file.length() - 4);
                areas.put(name, new AceRaceArea(plugin, new AceRaceConfig(plugin, name)));
            }
        });
    }

    @Override
    public void unload() {
        for (AceRaceArea area : areas.values()) {
            if (area.getGameStageEnum() != GameStageEnum.WAITING) area.endGameFinally();
        }
        clearAreas();
    }

    @Override
    public boolean addArea(String name) {
        if (areas.containsKey(name)) return false;
        AceRaceConfig config = new AceRaceConfig(plugin, name);
        config.initializeConfiguration(plugin.getFolder());
        config.setAreaName(name);
        config.saveOptions();
        return areas.putIfAbsent(name, new AceRaceArea(plugin, config)) == null;
    }
}
