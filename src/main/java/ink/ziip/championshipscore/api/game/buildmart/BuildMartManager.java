package ink.ziip.championshipscore.api.game.buildmart;

import ink.ziip.championshipscore.util.scheduler.FoliaScheduler;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.buildmart.blueprint.BuildMartOrderPool;
import ink.ziip.championshipscore.api.game.config.MapWorldNames;
import ink.ziip.championshipscore.api.game.manager.BaseGameInstanceManager;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import lombok.Getter;
import ink.ziip.championshipscore.util.Utils;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Owns the Build Mart maps. Per-map configs live in {@code plugin/buildmart/areas/*.yml}; shared
 * blueprints live in {@code plugin/buildmart/blueprints/}.
 */
public class BuildMartManager extends BaseGameInstanceManager<BuildMartArea> {
    /** Shared blueprint pool (normal + golden), loaded once and read by every area's library. */
    @Getter
    private volatile BuildMartOrderPool orderPool = new BuildMartOrderPool();

    public BuildMartManager(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
    }

    @Override
    public void load() {
        File buildMartDir = new File(plugin.getDataFolder(), "buildmart");
        buildMartDir.mkdirs();

        // Defer the area scan to the first tick so the static world and any shared data are ready.
        FoliaScheduler.global(plugin).runTask(task -> {
            File blueprintsFolder = new File(buildMartDir, "blueprints");
            blueprintsFolder.mkdirs();
            copyExampleBlueprints(blueprintsFolder);
            orderPool = BuildMartOrderPool.load(plugin, blueprintsFolder);

            File areasFolder = new File(buildMartDir, "areas");
            areasFolder.mkdirs();
            String[] areaList = areasFolder.list((d, n) -> n.toLowerCase().endsWith(".yml"));
            if (areaList != null) {
                Arrays.sort(areaList);
                Set<String> loadedWorlds = new HashSet<>();
                for (String file : areaList) {
                    String name = file.substring(0, file.length() - 4);
                    File configFile = new File(areasFolder, file);
                    String worldName = YamlConfiguration.loadConfiguration(configFile)
                            .getString("world-name", "buildmart");
                    if (worldName == null || worldName.isBlank()) worldName = "buildmart";
                    if (!loadedWorlds.add(worldName)) {
                        plugin.getLogger().severe("BuildMart 地图 " + name + " 与其他配置共用世界 "
                                + worldName + "，已跳过以防地图互相覆盖");
                        continue;
                    }
                    if (!loadArenaWorld(worldName)) continue;

                    BuildMartConfig config = new BuildMartConfig(plugin, name);
                    config.initializeConfiguration(plugin.getFolder());
                    BuildMartArea area = new BuildMartArea(plugin, config);
                    areas.put(name, area);
                    File template = new File(new File(plugin.getDataFolder(), "maps"), worldName);
                    if (template.isDirectory()) area.preloadMap();
                    else area.initializeForSetup();
                }
            }
        });
    }

    @Override
    public void unload() {
        for (BuildMartArea area : areas.values()) {
            if (area.getGameStageEnum() != GameStageEnum.WAITING) {
                area.endGameFinally();
            }
        }
        clearAreas();
    }

    /** Names of the bundled starter blueprints written out when the blueprints folder is empty. */
    private static final String[] EXAMPLE_BLUEPRINTS = {
            "example_cross.yml", "example_hut.yml", "example_golden_tower.yml"
    };

    /** Seeds {@code blueprintsFolder} with the bundled example blueprints, but only when it is empty. */
    private void copyExampleBlueprints(File blueprintsFolder) {
        String[] existing = blueprintsFolder.list((d, n) -> n.toLowerCase().endsWith(".yml"));
        if (existing != null && existing.length > 0) return;
        for (String name : EXAMPLE_BLUEPRINTS) {
            File target = new File(blueprintsFolder, name);
            if (target.exists()) continue;
            try (InputStream in = plugin.getResource("buildmart/blueprints/" + name)) {
                if (in != null) Files.copy(in, target.toPath());
            } catch (Exception e) {
                plugin.getLogger().warning(Utils.formatGameLog(GameTypeEnum.BuildMart, "-", "加载", "蓝图",
                        "无法写出示例蓝图=" + name + " | " + e.getMessage()));
            }
        }
    }

    /** Re-scans {@code buildmart/blueprints} into the shared pool (after a blueprint is exported). */
    public void reloadOrderPool() {
        File blueprintsFolder = new File(new File(plugin.getDataFolder(), "buildmart"), "blueprints");
        blueprintsFolder.mkdirs();
        orderPool = BuildMartOrderPool.load(plugin, blueprintsFolder);
    }

    @Override
    public boolean addArea(String name) {
        if (areas.containsKey(name))
            return false;

        String worldName = MapWorldNames.forMap("buildmart", name);
        if (!loadArenaWorld(worldName)) return false;
        BuildMartConfig buildMartConfig = new BuildMartConfig(plugin, name);
        buildMartConfig.initializeConfiguration(plugin.getFolder());
        buildMartConfig.setAreaName(name);
        buildMartConfig.setWorldName(worldName);
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
}
