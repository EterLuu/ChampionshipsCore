package ink.ziip.championshipscore.api.game.buildmart.blueprint;

import ink.ziip.championshipscore.ChampionshipsCore;
import lombok.Getter;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The loaded set of blueprints split into the normal pool (1–6 stars, drawn into the hub library) and the
 * golden pool (7+ stars, surfaced one at a time on the golden timer). Blueprints live in
 * {@code plugin/buildmart/blueprints/*.yml}.
 */
public class BuildMartOrderPool {
    /** Star rating at or above which a blueprint is treated as golden. */
    public static final int GOLDEN_STAR_THRESHOLD = 7;

    @Getter
    private final List<BuildMartBlueprint> normal = new ArrayList<>();
    @Getter
    private final List<BuildMartBlueprint> golden = new ArrayList<>();
    private final Map<String, BuildMartBlueprint> byId = new HashMap<>();

    /** Scans {@code blueprintsDir} for {@code *.yml} blueprints and sorts them into the two pools. */
    public static BuildMartOrderPool load(ChampionshipsCore plugin, File blueprintsDir) {
        BuildMartOrderPool pool = new BuildMartOrderPool();
        File[] files = blueprintsDir.listFiles((d, n) -> n.toLowerCase().endsWith(".yml"));
        if (files != null) {
            for (File file : files) {
                BuildMartBlueprint blueprint = BuildMartBlueprint.load(plugin, file);
                if (blueprint == null) continue;
                pool.byId.put(blueprint.getId(), blueprint);
                if (blueprint.getStars() >= GOLDEN_STAR_THRESHOLD) {
                    pool.golden.add(blueprint);
                } else {
                    pool.normal.add(blueprint);
                }
            }
        }
        plugin.getLogger().info("[BuildMart] 已加载 " + pool.normal.size() + " 个普通蓝图，"
                + pool.golden.size() + " 个黄金蓝图。");
        return pool;
    }

    public boolean isEmpty() {
        return normal.isEmpty() && golden.isEmpty();
    }

    public BuildMartBlueprint byId(String id) {
        return byId.get(id);
    }

    /**
     * Draws up to {@code count} distinct normal blueprints, weighted toward lower star ratings (a
     * 1-star order is more likely to surface than a 5-star). Returns fewer than {@code count} only when
     * the pool is smaller than that.
     */
    public List<BuildMartBlueprint> drawNormal(int count) {
        List<BuildMartBlueprint> remaining = new ArrayList<>(normal);
        List<BuildMartBlueprint> picked = new ArrayList<>();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        while (!remaining.isEmpty() && picked.size() < count) {
            int totalWeight = 0;
            for (BuildMartBlueprint b : remaining) totalWeight += weight(b);
            int roll = random.nextInt(totalWeight);
            int cumulative = 0;
            for (int i = 0; i < remaining.size(); i++) {
                cumulative += weight(remaining.get(i));
                if (roll < cumulative) {
                    picked.add(remaining.remove(i));
                    break;
                }
            }
        }
        return picked;
    }

    /** Lower stars → higher weight, so common builds dominate the library. */
    private static int weight(BuildMartBlueprint blueprint) {
        return Math.max(1, GOLDEN_STAR_THRESHOLD - blueprint.getStars());
    }

    /** A random golden blueprint, or {@code null} when none are configured. */
    public BuildMartBlueprint randomGolden() {
        if (golden.isEmpty()) return null;
        return golden.get(ThreadLocalRandom.current().nextInt(golden.size()));
    }
}
