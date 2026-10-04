package ink.ziip.championshipscore.api.game.riptiderush;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.manager.BaseGameInstanceManager;
import ink.ziip.championshipscore.api.game.model.GameStageEnum;
import ink.ziip.championshipscore.api.game.riptiderush.config.RiptideRushConfig;
import ink.ziip.championshipscore.api.game.riptiderush.course.RiptideCourseGenerator;
import ink.ziip.championshipscore.api.game.riptiderush.editor.RiptideCourseTrial;
import ink.ziip.championshipscore.api.game.riptiderush.editor.RiptideWorkshop;
import ink.ziip.championshipscore.api.game.riptiderush.runtime.RiptideRushArea;

import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public final class RiptideRushManager extends BaseGameInstanceManager<RiptideRushArea> {
    public RiptideRushManager(ChampionshipsCore plugin) {
        super(plugin);
    }

    @Override
    public void load() {
        deferMapLoad(
                () -> {
                    loadMapDefinitions(
                            new File(plugin.getDataFolder(), "riptiderush"),
                            (name, file) -> {
                                RiptideRushConfig config = new RiptideRushConfig(plugin, name);
                                config.initializeConfiguration(plugin.getFolder());
                                RiptideRushArea area =
                                        new RiptideRushArea(plugin, config, false, name);
                                areas.put(name, area);
                                area.preloadMap();
                            });
                });
    }

    @Override
    public void unload() {
        RiptideWorkshop.cancelAll();
        RiptideCourseTrial.stopAll();
        RiptideCourseGenerator.cancelAll();
        super.unload();
    }

    @Override
    public boolean addArea(String name) {
        return addArea(name, "");
    }

    @Override
    public boolean addArea(String name, String worldName) {
        if (areas.containsKey(name)) return false;
        RiptideRushConfig config = new RiptideRushConfig(plugin, name);
        config.initializeConfiguration(plugin.getFolder());
        config.setAreaName(name);
        config.bindConfiguredWorld("");
        config.saveOptions();
        areas.put(name, new RiptideRushArea(plugin, config, true, name));
        return true;
    }

    @Override
    public synchronized boolean loadAreaAfterRename(
            @NotNull String name, @NotNull String worldName) {
        if (areas.containsKey(name)) return false;
        RiptideRushConfig config = new RiptideRushConfig(plugin, name);
        config.initializeConfiguration(plugin.getFolder());
        RiptideRushArea area = new RiptideRushArea(plugin, config, false, name);
        areas.put(name, area);
        area.preloadMap();
        return true;
    }

    public java.util.concurrent.CompletableFuture<Boolean> saveArea(String name) {
        RiptideRushArea area = areas.get(name);
        if (area == null || area.getGameStageEnum() != GameStageEnum.WAITING)
            return java.util.concurrent.CompletableFuture.completedFuture(false);
        return area.saveMap(World.Environment.NORMAL);
    }
}
