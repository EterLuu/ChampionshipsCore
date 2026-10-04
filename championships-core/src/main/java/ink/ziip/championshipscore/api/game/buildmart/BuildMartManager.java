package ink.ziip.championshipscore.api.game.buildmart;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.buildmart.blueprint.BuildMartOrderPool;
import ink.ziip.championshipscore.api.game.buildmart.config.BuildMartConfig;
import ink.ziip.championshipscore.api.game.buildmart.runtime.BuildMartArea;
import ink.ziip.championshipscore.api.game.buildmart.runtime.BuildMartMaterialManifest;
import ink.ziip.championshipscore.api.game.manager.BaseGameInstanceManager;
import ink.ziip.championshipscore.api.game.model.GameTypeEnum;
import ink.ziip.championshipscore.logging.LogText;

import lombok.Getter;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;

/**
 * Owns the Build Mart maps. Per-map configs live in {@code plugin/buildmart/areas/*.yml}; shared
 * blueprints live in {@code plugin/buildmart/blueprints/}.
 */
public class BuildMartManager extends BaseGameInstanceManager<BuildMartArea> {
    /** Shared blueprint pool (normal + golden), loaded once and read by every area's library. */
    @Getter private BuildMartOrderPool orderPool = new BuildMartOrderPool();

    public BuildMartManager(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
    }

    /** Default world name for a Build Mart map that owns its physical world. */
    public static String worldNameFor(String mapName) {
        return "buildmart_" + mapName;
    }

    @Override
    public void load() {
        deferMapLoad(
                () -> {
                    File blueprintsFolder =
                            new File(plugin.getDataFolder(), "buildmart/blueprints");
                    blueprintsFolder.mkdirs();
                    copyExampleBlueprints(blueprintsFolder);
                    orderPool = BuildMartOrderPool.load(plugin, blueprintsFolder);
                    Set<String> loadedWorlds = new HashSet<>();
                    loadMapDefinitions(
                            new File(plugin.getDataFolder(), "buildmart/areas"),
                            (name, file) -> {
                                YamlConfiguration raw = YamlConfiguration.loadConfiguration(file);
                                String worldName = raw.getString("world-name", "");
                                if (worldName == null || worldName.isBlank()) {
                                    BuildMartConfig config = new BuildMartConfig(plugin, name);
                                    config.initializeConfiguration(plugin.getFolder());
                                    config.bindConfiguredWorld("");
                                    config.saveOptions();
                                    BuildMartMaterialManifest.write(plugin, config);
                                    BuildMartArea area = new BuildMartArea(plugin, config);
                                    areas.put(name, area);
                                    area.initializeForSetup();
                                    return;
                                }
                                if (loadedWorlds.add(worldName) && !loadArenaWorld(worldName)) {
                                    loadedWorlds.remove(worldName);
                                    return;
                                }

                                BuildMartConfig config = new BuildMartConfig(plugin, name);
                                config.initializeConfiguration(plugin.getFolder());
                                BuildMartMaterialManifest.write(plugin, config);
                                BuildMartArea area = new BuildMartArea(plugin, config);
                                areas.put(name, area);
                                area.initializeForSetup();
                            });
                });
    }

    /** Names of the bundled starter blueprints written out when the blueprints folder is empty. */
    private static final String[] EXAMPLE_BLUEPRINTS = {
        "example_cross.yml", "example_hut.yml", "example_golden_tower.yml"
    };

    /**
     * Seeds {@code blueprintsFolder} with the bundled example blueprints, but only when it is
     * empty.
     */
    private void copyExampleBlueprints(File blueprintsFolder) {
        String[] existing = blueprintsFolder.list((d, n) -> n.toLowerCase().endsWith(".yml"));
        if (existing != null && existing.length > 0) return;
        for (String name : EXAMPLE_BLUEPRINTS) {
            File target = new File(blueprintsFolder, name);
            if (target.exists()) continue;
            try (InputStream in = plugin.getResource("buildmart/blueprints/" + name)) {
                if (in != null) Files.copy(in, target.toPath());
            } catch (Exception e) {
                plugin.getLogger()
                        .warning(
                                LogText.formatGameLog(
                                        GameTypeEnum.BuildMart,
                                        "-",
                                        "加载",
                                        "蓝图",
                                        "无法写出示例蓝图=" + name + " | " + e.getMessage()));
            }
        }
    }

    /**
     * Re-scans {@code buildmart/blueprints} into the shared pool (after a blueprint is exported).
     */
    public void reloadOrderPool() {
        File blueprintsFolder =
                new File(new File(plugin.getDataFolder(), "buildmart"), "blueprints");
        blueprintsFolder.mkdirs();
        orderPool = BuildMartOrderPool.load(plugin, blueprintsFolder);
    }

    public void updateBlueprint(
            ink.ziip.championshipscore.api.game.buildmart.blueprint.BuildMartBlueprint blueprint) {
        orderPool = orderPool.withBlueprint(blueprint);
    }

    @Override
    public boolean addArea(String name) {
        return false;
    }

    @Override
    public boolean addArea(String name, String worldName) {
        if (areas.containsKey(name)) return false;
        BuildMartConfig buildMartConfig = new BuildMartConfig(plugin, name);
        buildMartConfig.initializeConfiguration(plugin.getFolder());
        buildMartConfig.setAreaName(name);
        buildMartConfig.setWorldName("");
        buildMartConfig.useRowLayoutForDraft();
        buildMartConfig.saveOptions();

        BuildMartArea newArea = new BuildMartArea(plugin, buildMartConfig);
        BuildMartArea buildMartArea = areas.putIfAbsent(name, newArea);
        if (buildMartArea == null) {
            newArea.initializeForSetup();
        } else {
            newArea.dispose();
        }

        return buildMartArea == null;
    }

    @Override
    public synchronized boolean loadAreaAfterRename(String name, String worldName) {
        if (areas.containsKey(name)) return false;
        BuildMartConfig config = new BuildMartConfig(plugin, name);
        config.initializeConfiguration(plugin.getFolder());
        if (!worldName.equals(config.getConfiguredWorld())) return false;
        BuildMartArea area = new BuildMartArea(plugin, config);
        areas.put(name, area);
        area.initializeForSetup();
        return true;
    }

    @Override
    protected boolean allowsSharedMapWorlds() {
        return true;
    }
}
