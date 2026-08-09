package ink.ziip.championshipscore.api.game.buildmart.blueprint;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.util.Utils;
import lombok.Getter;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The loaded set of blueprints split into the normal pool (1–5 stars, drawn for the per-plot auto-assignment)
 * and the golden pool (exactly 7 stars, surfaced one at a time on the golden timer). Blueprints live in
 * {@code plugin/buildmart/blueprints/*.yml}. Blueprints whose star rating is outside {1..5, 7} are skipped
 * with a warning at load time.
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
                int stars = blueprint.getStars();
                if (stars == GOLDEN_STAR_THRESHOLD) {
                    pool.golden.add(blueprint);
                } else if (stars >= 1 && stars <= 5) {
                    pool.normal.add(blueprint);
                } else {
                    plugin.getLogger().warning(Utils.formatGameLog(GameTypeEnum.BuildMart, "-", "加载", "蓝图",
                            "蓝图=" + blueprint.getId() + " 星级=" + stars + " 不在普通 1-5/黄金 7 范围，已跳过"));
                }
            }
        }
        plugin.getLogger().info(Utils.formatGameLog(GameTypeEnum.BuildMart, "-", "加载", "蓝图",
                "普通=" + pool.normal.size() + " 黄金=" + pool.golden.size()));
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

    /** Lower stars → higher weight, so common builds dominate the auto-assignment draws. */
    private static int weight(BuildMartBlueprint blueprint) {
        return Math.max(1, GOLDEN_STAR_THRESHOLD - blueprint.getStars());
    }

    /** A random golden blueprint, or {@code null} when none are configured. */
    public BuildMartBlueprint randomGolden() {
        if (golden.isEmpty()) return null;
        return golden.get(ThreadLocalRandom.current().nextInt(golden.size()));
    }
}
