package ink.ziip.championshipscore.api.game.riptiderush.mechanics;

import ink.ziip.championshipscore.api.game.riptiderush.mechanics.RiptideDodgeSchedule.Spawn;

import org.bukkit.util.BoundingBox;

import java.util.List;

/** Fixed-time stopped-raft challenge with seeded, individual random charges. */
public final class RiptideDodgeRun {
    public static final int DURATION_TICKS = RiptideDodgeSchedule.DURATION_TICKS;
    public static final double COLLISION_WIDTH = RiptideDodgeSchedule.COLLISION_WIDTH;

    private final List<Spawn> spawns;
    private int tick;
    private int nextSpawn;

    public RiptideDodgeRun(String variant, long seed, int width) {
        this(variant, seed, width, width);
    }

    public RiptideDodgeRun(String variant, long seed, int width, int length) {
        this(variant, seed, width, length, 0);
    }

    public RiptideDodgeRun(String variant, long seed, int width, int length, int stage) {
        spawns = RiptideDodgeSchedule.generate(variant, seed, width, length, stage);
    }

    /** Uniform horizontal collision size, retaining each entity's native vertical box. */
    public static BoundingBox collisionBox(BoundingBox nativeBox) {
        double centerX = (nativeBox.getMinX() + nativeBox.getMaxX()) / 2D;
        double centerZ = (nativeBox.getMinZ() + nativeBox.getMaxZ()) / 2D;
        double half = COLLISION_WIDTH / 2D;
        return new BoundingBox(
                centerX - half,
                nativeBox.getMinY(),
                centerZ - half,
                centerX + half,
                nativeBox.getMaxY(),
                centerZ + half);
    }

    public List<Spawn> spawns() {
        return spawns;
    }

    public RiptideDodgeSchedule.Direction direction() {
        return spawns.getFirst().direction();
    }

    public List<Spawn> tick() {
        if (complete()) return List.of();
        List<Spawn> due =
                nextSpawn < spawns.size() && spawns.get(nextSpawn).tick() == tick
                        ? List.of(spawns.get(nextSpawn++))
                        : List.of();
        tick++;
        return due;
    }

    public boolean complete() {
        return tick >= DURATION_TICKS;
    }

    public int tickNumber() {
        return tick;
    }
}
