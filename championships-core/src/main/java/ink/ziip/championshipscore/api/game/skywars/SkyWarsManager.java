package ink.ziip.championshipscore.api.game.skywars;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.manager.BaseGameInstanceManager;
import ink.ziip.championshipscore.api.game.model.GameStageEnum;
import ink.ziip.championshipscore.api.game.skywars.config.SkyWarsConfig;
import ink.ziip.championshipscore.api.game.skywars.runtime.SkyWarsTeamArea;
import ink.ziip.championshipscore.api.game.skywars.runtime.SkyWarsVariantRegistry;

import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.concurrent.CompletableFuture;

public class SkyWarsManager extends BaseGameInstanceManager<SkyWarsTeamArea> {
    private final SkyWarsVariantRegistry variantRegistry;

    public SkyWarsManager(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
        variantRegistry = new SkyWarsVariantRegistry(championshipsCore);
    }

    @Override
    public void load() {
        variantRegistry.load();
        deferMapLoad(
                () -> {
                    loadMapDefinitions(
                            new File(plugin.getDataFolder(), "skywars"),
                            (name, file) -> {
                                SkyWarsTeamArea area =
                                        new SkyWarsTeamArea(
                                                plugin,
                                                new SkyWarsConfig(plugin, name),
                                                false,
                                                name,
                                                variantRegistry);
                                areas.put(name, area);
                                area.preloadMap();
                            });
                });
    }

    @Override
    public boolean addArea(String name) {
        return false;
    }

    @Override
    public boolean addArea(String name, String worldName) {
        if (areas.containsKey(name)) return false;
        SkyWarsConfig skyWarsConfig = new SkyWarsConfig(plugin, name);
        skyWarsConfig.initializeConfiguration(plugin.getFolder());
        skyWarsConfig.setAreaName(name);
        skyWarsConfig.bindConfiguredWorld("");
        skyWarsConfig.saveOptions();

        SkyWarsTeamArea skyWarsArea =
                new SkyWarsTeamArea(plugin, skyWarsConfig, true, name, variantRegistry);
        areas.put(name, skyWarsArea);

        return true;
    }

    @Override
    public synchronized boolean loadAreaAfterRename(
            @NotNull String name, @NotNull String worldName) {
        if (areas.containsKey(name)) return false;
        SkyWarsConfig config = new SkyWarsConfig(plugin, name);
        config.initializeConfiguration(plugin.getFolder());
        SkyWarsTeamArea area = new SkyWarsTeamArea(plugin, config, false, name, variantRegistry);
        areas.put(name, area);
        area.preloadMap();
        return true;
    }

    public CompletableFuture<Boolean> saveArea(String name) {
        SkyWarsTeamArea skyWarsArea = areas.get(name);
        if (skyWarsArea == null) return CompletableFuture.completedFuture(false);

        if (skyWarsArea.getGameStageEnum() != GameStageEnum.WAITING) {
            return CompletableFuture.completedFuture(false);
        }

        return skyWarsArea.saveMap(World.Environment.NORMAL);
    }
}
