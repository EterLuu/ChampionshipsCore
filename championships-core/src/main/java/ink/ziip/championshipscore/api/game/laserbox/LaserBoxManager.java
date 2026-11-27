package ink.ziip.championshipscore.api.game.laserbox;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.laserbox.config.LaserBoxConfig;
import ink.ziip.championshipscore.api.game.laserbox.runtime.LaserBoxArea;
import ink.ziip.championshipscore.api.game.laserbox.runtime.LaserBoxIntroductionArena;
import ink.ziip.championshipscore.api.game.manager.BaseGameInstanceManager;

import lombok.Getter;

import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Loads configured LaserBox maps and exposes their arena copies as paired-game instances. */
public final class LaserBoxManager extends BaseGameInstanceManager<LaserBoxArea> {

    @Getter private LaserBoxIntroductionArena introductionArena;

    public LaserBoxManager(ChampionshipsCore plugin) {
        super(plugin);
    }

    @Override
    public void load() {
        deferMapLoad(
                () -> {
                    introductionArena = new LaserBoxIntroductionArena(plugin);
                    introductionArena.load();
                    Set<String> loadedWorlds = new HashSet<>();
                    loadMapDefinitions(
                            new File(plugin.getDataFolder(), "laserbox"),
                            (name, file) -> {
                                YamlConfiguration raw = YamlConfiguration.loadConfiguration(file);
                                String world = raw.getString("world-name", "");
                                if (world == null) world = "";
                                LaserBoxConfig config = new LaserBoxConfig(plugin, name);
                                config.initializeConfiguration(plugin.getFolder());
                                if (!world.isBlank()
                                        && loadedWorlds.add(world)
                                        && !loadArenaWorld(world)) {
                                    loadedWorlds.remove(world);
                                    return;
                                }
                                createInstances(name, config);
                            });
                });
    }

    @Override
    public void unload() {
        if (introductionArena != null) introductionArena.close();
        introductionArena = null;
        super.unload();
    }

    @Override
    public boolean addArea(String name) {
        return false;
    }

    @Override
    public boolean addArea(String name, String worldName) {
        if (areas.containsKey(name)) return false;
        LaserBoxConfig config = new LaserBoxConfig(plugin, name);
        config.initializeConfiguration(plugin.getFolder());
        config.setAreaName(name);
        config.bindConfiguredWorld(worldName == null ? "" : worldName);
        config.beginPrepareDraft();
        config.saveOptions();
        createInstances(name, config);
        return true;
    }

    @Override
    public synchronized boolean loadAreaAfterRename(
            @NotNull String name, @NotNull String worldName) {
        if (areas.containsKey(name)) return false;
        LaserBoxConfig config = new LaserBoxConfig(plugin, name);
        config.initializeConfiguration(plugin.getFolder());
        createInstances(name, config);
        return true;
    }

    @Override
    public synchronized List<LaserBoxArea> getMapInstances(@NotNull String mapName) {
        LaserBoxArea first = getArea(mapName);
        if (first == null) return List.of();
        LaserBoxConfig config = first.getGameConfig();
        return resizeMapInstances(
                mapName,
                config.getCopyCount(),
                index -> new LaserBoxArea(plugin, config, index, false));
    }

    private void createInstances(String mapName, LaserBoxConfig config) {
        createMapInstances(
                mapName,
                config.getCopyCount(),
                index -> new LaserBoxArea(plugin, config, index, false));
    }

    @Override
    protected boolean allowsSharedMapWorlds() {
        return true;
    }
}
