package ink.ziip.championshipscore.api.game.iamnotarobot.geometry;

import ink.ziip.championshipscore.api.game.arena.RowArenaGrid;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.util.Vector;

import java.util.List;

/**
 * A personal urban skyway track inside a team's standalone Stage 0 city block; positions are
 * relative to the unified team venue origin ({@link VerificationCourse#GRID}).
 */
public record EntryTrack(int center) {
    public record Checkpoint(int index, int triggerMinZ, int triggerMaxZ, double spawnZ) {}

    public static final int COUNT = 4;

    /** City block centered on X=9, with four terminal doors opening into the warehouse. */
    public static final int MIN_X = -35, WIDTH = 89;

    public static final int MIN_Y = -6, HEIGHT = 41;
    public static final int MIN_Z = -124, LENGTH = 120;
    public static final int START_GATE_Z = -115;
    public static final int FINISH_WALL_Z = -6;
    public static final int VENUE_LENGTH = VerificationCourse.LENGTH - MIN_Z;
    public static final Vector VENUE_SIZE = new Vector(WIDTH, HEIGHT, VENUE_LENGTH);
    public static final String STRUCTURE = "iamnotarobot/verification-flat.nbt";

    /** Four sequential sub-stage checkpoints; falling in any section restarts that section only. */
    public static final List<Checkpoint> CHECKPOINTS =
            List.of(
                    new Checkpoint(0, -123, -116, -118.5),
                    new Checkpoint(1, -89, -84, -86.5),
                    new Checkpoint(2, -60, -55, -57.5),
                    new Checkpoint(3, -32, -27, -29.5));

    public static final int CHECKPOINT_COUNT = CHECKPOINTS.size();

    /** Minimum corner of each team's standalone venue copy, spaced 256 blocks apart along +X. */
    public static final RowArenaGrid GRID =
            new RowArenaGrid(
                    VerificationCourse.GRID.origin(0).add(new Vector(MIN_X, MIN_Y, MIN_Z)),
                    VerificationCourse.STEP);

    /**
     * Shared team venue origin aligned with the warehouse and downstream puzzle rooms at 256 blocks
     * per copy.
     */
    public static Vector origin(int index) {
        return VerificationCourse.GRID.origin(index);
    }

    public static EntryTrack forSlot(int slot) {
        if (slot < 0 || slot >= COUNT)
            throw new IllegalArgumentException("Invalid verification track: " + slot);
        return new EntryTrack(-6 + 10 * slot);
    }

    public Vector spawn() {
        return checkpointSpawn(0);
    }

    public Vector checkpointSpawn(int stage) {
        int clamped = Math.max(0, Math.min(CHECKPOINT_COUNT - 1, stage));
        return new Vector(center + 0.5, 1, CHECKPOINTS.get(clamped).spawnZ());
    }

    public int checkpointAt(int x, int y, int z) {
        if (Math.abs(x - center) > 3 || y < 1 || y > 2) return -1;
        for (Checkpoint checkpoint : CHECKPOINTS) {
            if (z >= checkpoint.triggerMinZ() && z <= checkpoint.triggerMaxZ())
                return checkpoint.index();
        }
        return -1;
    }

    public boolean contains(int x, int y, int z) {
        return Math.abs(x - center) <= 4 && y >= 0 && y <= 10 && z > MIN_Z && z < FINISH_WALL_Z;
    }

    public boolean atFinish(int x, int y, int z) {
        return Math.abs(x - center) <= 3 && y >= 1 && y <= 2 && z >= -11 && z <= -7;
    }

    public Vector finishSpawn() {
        return new Vector(center + 0.5, 1, -9.5);
    }

    public static void setStartGate(World world, Vector entryOrigin, boolean open) {
        Material material = open ? Material.AIR : Material.RED_STAINED_GLASS;
        for (int slot = 0; slot < COUNT; slot++) {
            int trackCenter = forSlot(slot).center();
            for (int x = trackCenter - 2; x <= trackCenter + 2; x++) {
                for (int y = 1; y <= 4; y++) {
                    world.getBlockAt(
                                    entryOrigin.getBlockX() + x,
                                    entryOrigin.getBlockY() + y,
                                    entryOrigin.getBlockZ() + START_GATE_Z)
                            .setType(material, false);
                }
            }
        }
    }
}
