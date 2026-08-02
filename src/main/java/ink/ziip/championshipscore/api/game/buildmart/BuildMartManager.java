package ink.ziip.championshipscore.api.game.buildmart;

import ink.ziip.championshipscore.util.scheduler.FoliaScheduler;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.buildmart.blueprint.BuildMartOrderPool;
import ink.ziip.championshipscore.api.game.manager.BaseAreaManager;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import lombok.Getter;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;

/**
 * Owns the Build Mart areas. Per-area configs live in {@code plugin/buildmart/areas/*.yml}; shared
 * blueprint data lives directly under {@code plugin/buildmart/}. Phase 1 only scans and instantiates
 * the area configs; the blueprint-pool and golden-display init is added by the later phases.
 */
public class BuildMartManager extends BaseAreaManager<BuildMartArea> {
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
                for (String file : areaList) {
                    String name = file.substring(0, file.length() - 4);
                    areas.put(name, new BuildMartArea(plugin, new BuildMartConfig(plugin, name)));
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
                plugin.getLogger().warning("[BuildMart] 无法写出示例蓝图 " + name + ": " + e.getMessage());
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

        BuildMartConfig buildMartConfig = new BuildMartConfig(plugin, name);
        buildMartConfig.initializeConfiguration(plugin.getFolder());
        buildMartConfig.setAreaName(name);
        buildMartConfig.saveOptions();

        BuildMartArea buildMartArea = areas.putIfAbsent(name, new BuildMartArea(plugin, buildMartConfig));

        return buildMartArea == null;
    }
}
