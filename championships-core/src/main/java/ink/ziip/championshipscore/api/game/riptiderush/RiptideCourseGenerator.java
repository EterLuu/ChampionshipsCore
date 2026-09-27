package ink.ziip.championshipscore.api.game.riptiderush;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

/** Builds only the owned corridor, using the same immutable plan as runtime judging. */
public final class RiptideCourseGenerator {
    private static final Map<UUID, Runnable> BUILDING = new HashMap<>();
    private RiptideCourseGenerator() {
    }

    public static void generate(@NotNull RiptideRushConfig config) {
        generate(config, RiptideCoursePlanner.plan(config, config.getPreviewSeed()));
    }

    public static boolean isGenerating(World world) {
        return world != null && BUILDING.containsKey(world.getUID());
    }

    public static void cancel(World world) {
        Runnable cancel = world == null ? null : BUILDING.remove(world.getUID());
        if (cancel != null) cancel.run();
    }

    public static void cancelAll() {
        for (Runnable cancel : java.util.List.copyOf(BUILDING.values())) cancel.run();
        BUILDING.clear();
    }

    /** Yield between bounded slices and small batches of obstacles. Complete only after unlocking. */
    public static CompletableFuture<Boolean> generateAsync(Plugin plugin, RiptideRushConfig config,
                                                           RiptideCoursePlan plan, BooleanSupplier valid) {
        try {
            return schedule(plugin, config, plan, requiredBlock(config.getRaftMaterial(), "raft material"),
                    requiredBlock(config.getObstacleMaterial(), "obstacle material"), valid);
        } catch (RuntimeException failure) { return CompletableFuture.failedFuture(failure); }
    }

    /** Scheduling accepts already resolved materials; block registry lookup stays on the public boundary. */
    static CompletableFuture<Boolean> schedule(Plugin plugin, RiptideRushConfig config, RiptideCoursePlan plan,
                                               Material raft, Material obstacle, BooleanSupplier valid) {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("generation must start on the server thread");
        var geometry = config.resolveGeometry();
        World world = config.getStartPoint().getWorld();
        if (world == null || isGenerating(world))
            return CompletableFuture.failedFuture(new IllegalStateException("地图正在生成，请稍候"));
        var future = new CompletableFuture<Boolean>();
        try {
            int side = geometry.halfWidth() + Math.max(2, config.getObstacleMargin());
            int height = Math.max(5, config.getClearHeight());
            int slices = Math.max(1, 3000 / ((side * 2 + 1) * (height + 1)));
            var job = new BukkitRunnable() {
                int forward = -geometry.halfLength() - 2;
                int levelIndex;
                boolean raftBuilt;
                void finish(Throwable failure, boolean built) {
                    cancel(); BUILDING.remove(world.getUID());
                    if (failure == null) future.complete(built);
                    else future.completeExceptionally(failure);
                }
                @Override public void run() {
                    try {
                        if (!valid.getAsBoolean() || Bukkit.getWorld(world.getUID()) != world) {
                            finish(null, false); return;
                        }
                        int last = geometry.totalSteps() + geometry.halfLength() + 2;
                        if (forward <= last) {
                            for (int n = 0; n < slices && forward <= last; n++, forward++)
                                clearSlice(world, geometry, forward, side, height);
                            return;
                        }
                        if (!raftBuilt) { buildInitialRaft(world, geometry, raft); raftBuilt = true; }
                        for (int n = 0; n < 4 && levelIndex < plan.levels().size(); n++) {
                            var level = plan.levels().get(levelIndex++);
                            buildLevel(world, geometry, level, obstacle);
                            // An authored workspace can be much larger than a stock gate.
                            if (level.template().blueprint() != null) break;
                        }
                        if (levelIndex == plan.levels().size()) {
                            buildFinishMarker(world, geometry); finish(null, true);
                        }
                    } catch (RuntimeException failure) { finish(failure, false); }
                }
            };
            job.runTaskTimer(plugin, 1L, 1L);
            BUILDING.put(world.getUID(), () -> { job.cancel(); BUILDING.remove(world.getUID()); future.complete(false); });
        } catch (RuntimeException failure) { BUILDING.remove(world.getUID()); future.completeExceptionally(failure); }
        return future;
    }

    public static void generate(@NotNull RiptideRushConfig config, RiptideCoursePlan plan) {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("course generation must run on the server thread");
        RiptideCourseGeometry geometry = config.resolveGeometry();
        World world = config.getStartPoint().getWorld();
        if (world == null) throw new IllegalArgumentException("course world is not loaded");
        Material raft = requiredBlock(config.getRaftMaterial(), "raft material");
        Material obstacle = requiredBlock(config.getObstacleMaterial(), "obstacle material");
        clearOwnedCorridor(world, geometry, config);
        build(world, geometry, plan, raft, obstacle);
    }

    static void build(World world, RiptideCourseGeometry geometry, RiptideCoursePlan plan,
                      Material raft, Material obstacle) {
        buildInitialRaft(world, geometry, raft);
        for (var level : plan.levels()) buildLevel(world, geometry, level, obstacle);
        buildFinishMarker(world, geometry);
    }

    private static void buildLevel(World world, RiptideCourseGeometry geometry,
                                    RiptideCoursePlan.Level level, Material obstacle) {
        if (level.isSideSweep()) return; // Animated sweep is owned by the shared round/trial controller.
        if (level.template().blueprint() != null) {
            level.template().blueprint().paste(geometry, level.step(),
                    level.type() == RiptideLevelType.PASS && level.mirrored(), level.type() == RiptideLevelType.MATH); return;
        }
        switch (level.type()) {
            case MATH -> buildMathGate(world, geometry, level.step(), obstacle);
            case PASS -> buildPassObstacle(world, geometry, level.step(), obstacle, level);
            case COLOR_FLOOR, DODGE, RHYTHM -> buildChallengeMarker(world, geometry, level.step());
        }
    }

    static void clearSlice(World world, RiptideCourseGeometry geometry, int forward, int side, int height) {
        for (int lateral = -side; lateral <= side; lateral++)
            for (int y = 0; y <= height; y++)
                set(world, geometry, forward, lateral, y, Material.AIR);
    }

    private static void clearOwnedCorridor(World world, RiptideCourseGeometry geometry,
                                           RiptideRushConfig config) {
        int side = geometry.halfWidth() + Math.max(2, config.getObstacleMargin());
        int first = -geometry.halfLength() - 2;
        int last = geometry.totalSteps() + geometry.halfLength() + 2;
        int top = geometry.floorY() + Math.max(5, config.getClearHeight());
        for (int forward = first; forward <= last; forward++) {
            for (int lateral = -side; lateral <= side; lateral++) {
                for (int y = geometry.floorY(); y <= top; y++) {
                    world.getBlockAt(geometry.blockX(forward, lateral), y,
                            geometry.blockZ(forward, lateral)).setType(Material.AIR, false);
                }
            }
        }
    }

    private static void buildInitialRaft(World world, RiptideCourseGeometry geometry, Material raft) {
        for (int forward = -geometry.halfLength(); forward <= geometry.halfLength(); forward++) {
            for (int lateral = -geometry.halfWidth(); lateral <= geometry.halfWidth(); lateral++) {
                set(world, geometry, forward, lateral, 0, raft);
            }
        }
    }

    /** Shape, offset and mirroring belong to the plan, never to a slot's ordinal. */
    private static void buildPassObstacle(World world, RiptideCourseGeometry geometry, int step,
                                          Material obstacle, RiptideCoursePlan.Level level) {
        for(var block:passBlocks(geometry.halfWidth(),level,obstacle))
            set(world,geometry,step+block.forward(),block.lateral(),block.y(),block.material());
    }

    record PassBlock(int forward, int lateral, int y, Material material) { }

    /** One source for built-in PASS buildings, whether travelling forward or sideways. */
    static java.util.List<PassBlock> passBlocks(int halfWidth, RiptideCoursePlan.Level level, Material obstacle) {
        var blocks = new java.util.ArrayList<PassBlock>();
        int outer = halfWidth + 1;
        switch (level.variant()) {
            case "CUSTOM" -> { } // Unsaved, disabled blank workshop entry; saved entries paste their blueprint.
            case "GAP" -> {
                int opening = level.opening();
                for (int lateral = -outer; lateral <= outer; lateral++) {
                    for (int height = 1; height <= 4; height++) {
                        if (Math.abs(lateral - opening) <= 1 && height <= 2) continue;
                        blocks.add(new PassBlock(0,lateral,height,obstacle));
                    }
                }
            }
            case "JUMP" -> {
                for (int lateral = -outer; lateral <= outer; lateral++)
                    blocks.add(new PassBlock(0,lateral,1,obstacle));
                blocks.add(new PassBlock(0,-outer,2,Material.SEA_LANTERN));
                blocks.add(new PassBlock(0,outer,2,Material.SEA_LANTERN));
            }
            case "WEAVE" -> {
                int direction = level.mirrored() ? -1 : 1;
                for (int lateral = -outer; lateral <= 0; lateral++)
                    for (int height = 1; height <= 3; height++)
                        blocks.add(new PassBlock(-3,lateral*direction,height,obstacle));
                for (int lateral = 0; lateral <= outer; lateral++)
                    for (int height = 1; height <= 3; height++)
                        blocks.add(new PassBlock(3,lateral*direction,height,obstacle));
            }
            default -> throw new IllegalArgumentException("unknown pass variant " + level.variant());
        }
        return java.util.List.copyOf(blocks);
    }

    private static void buildMathGate(World world, RiptideCourseGeometry geometry, int step,
                                      Material obstacle) {
        int outer = geometry.halfWidth() + 1;
        for (int height = 1; height <= 3; height++) {
            set(world, geometry, step, 0, height, obstacle);
            set(world, geometry, step, -outer, height, Material.LIGHT_BLUE_CONCRETE);
            set(world, geometry, step, outer, height, Material.RED_CONCRETE);
        }
        for (int lateral = -outer; lateral <= outer; lateral++) {
            Material header = lateral > 0 ? Material.RED_CONCRETE
                    : lateral < 0 ? Material.LIGHT_BLUE_CONCRETE : obstacle;
            set(world, geometry, step, lateral, 4, header);
        }
    }

    private static void buildChallengeMarker(World world, RiptideCourseGeometry geometry, int step) {
        int outer = geometry.halfWidth() + 1;
        for (int height = 1; height <= 3; height++) {
            set(world, geometry, step, -outer, height, Material.GOLD_BLOCK);
            set(world, geometry, step, outer, height, Material.GOLD_BLOCK);
        }
        for (int lateral = -outer; lateral <= outer; lateral++)
            set(world, geometry, step, lateral, 4, Material.YELLOW_CONCRETE);
    }

    private static void buildFinishMarker(World world, RiptideCourseGeometry geometry) {
        int outer = geometry.halfWidth() + 1;
        for (int height = 1; height <= 4; height++) {
            set(world, geometry, geometry.totalSteps(), -outer, height, Material.LIME_CONCRETE);
            set(world, geometry, geometry.totalSteps(), outer, height, Material.LIME_CONCRETE);
        }
        for (int lateral = -outer; lateral <= outer; lateral++)
            set(world, geometry, geometry.totalSteps(), lateral, 5, Material.LIME_CONCRETE);
    }

    private static void set(World world, RiptideCourseGeometry geometry, int forward, int lateral,
                            int height, Material material) {
        world.getBlockAt(geometry.blockX(forward, lateral), geometry.floorY() + height,
                geometry.blockZ(forward, lateral)).setType(material, false);
    }

    private static Material requiredBlock(String configured, String label) {
        Material material = configured == null ? null : Material.matchMaterial(configured);
        if (material == null || !material.isBlock() || material.isAir())
            throw new IllegalArgumentException(label + " is invalid");
        return material;
    }
}
