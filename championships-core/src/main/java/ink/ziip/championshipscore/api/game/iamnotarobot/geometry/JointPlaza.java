package ink.ziip.championshipscore.api.game.iamnotarobot.geometry;

import ink.ziip.championshipscore.api.game.arena.ArenaGrid;
import ink.ziip.championshipscore.api.game.iamnotarobot.model.JointShape;

import org.bukkit.Material;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/** Coordinates are relative to the lane origin. */
public final class JointPlaza {
    public static final String STRUCTURE = "iamnotarobot/verification-joint.nbt";
    public static final int MIN_X = -43, MIN_Y = -6, MIN_Z = 39;
    public static final int WIDTH = 105, HEIGHT = 56, LENGTH = 68;
    public static final int EXIT_Z = 82;
    public static final ArenaGrid GRID =
            lane -> VerificationCourse.GRID.origin(lane).add(new Vector(MIN_X, MIN_Y, MIN_Z));

    public enum Side {
        WEST,
        EAST
    }

    public record Cell(int x, int y, int z) {}

    /** Centre and size of a north-facing display rectangle, in blocks. */
    public record Face(double x, double y, double z, double width, double height, float yaw) {}

    record FeetBox(double x0, double y0, double z0, double x1, double y1, double z1) {
        boolean contains(double x, double y, double z) {
            return x >= x0 && x < x1 && y >= y0 && y < y1 && z >= z0 && z < z1;
        }
    }

    public static final int GRAY_MIN_X = -4, GRAY_MAX_X = 4, WHITE_MIN_X = 13, WHITE_MAX_X = 21;
    public static final int WHITE_BASE_X = WHITE_MIN_X + JointShape.MAX_DEPTH;
    public static final int STROKE = WHITE_BASE_X - GRAY_MAX_X - 1;
    public static final int FACE_X = WHITE_MAX_X + 1;
    public static final int BLOCK_MIN_Y = 2, BLOCK_MAX_Y = 8, BLOCK_MIN_Z = 60, BLOCK_MAX_Z = 66;
    public static final int WEST_TRUCK_MIN_X = -17,
            EAST_TRUCK_MAX_X = 34,
            TRUCK_MIN_Y = 1,
            TRUCK_TOP_Y = 10;
    public static final int BUTTON_Z = 55;
    private static final int WORK_AREA_CEILING = 14;
    private static final FeetBox ABOVE_LOADS_AND_CABS =
            new FeetBox(
                    WEST_TRUCK_MIN_X,
                    BLOCK_MAX_Y + 1,
                    BLOCK_MIN_Z,
                    EAST_TRUCK_MAX_X + 1,
                    WORK_AREA_CEILING,
                    BLOCK_MAX_Z + 1);
    private static final FeetBox SWEEP =
            new FeetBox(
                    GRAY_MAX_X + 1 - JointShape.MAX_DEPTH,
                    BLOCK_MIN_Y,
                    BLOCK_MIN_Z,
                    FACE_X + 1,
                    WORK_AREA_CEILING,
                    BLOCK_MAX_Z + 1);
    private static final FeetBox GRAY_WORKSPACE =
            new FeetBox(
                    GRAY_MAX_X + 1 - JointShape.MAX_DEPTH,
                    1,
                    BLOCK_MIN_Z,
                    GRAY_MAX_X + 1,
                    BLOCK_MAX_Y + 1,
                    BLOCK_MAX_Z + 1);
    private static final FeetBox WHITE_CUT =
            new FeetBox(
                    WHITE_MIN_X, 1, BLOCK_MIN_Z, WHITE_BASE_X, BLOCK_MAX_Y + 1, BLOCK_MAX_Z + 1);
    private static final FeetBox EXIT = new FeetBox(7, 1, EXIT_Z, 12, 5, EXIT_Z + 1);
    // Half-open walkable feet volumes across the outdoor construction yard and viewing towers.
    static final List<FeetBox> WALKS = buildWalks();

    private static List<FeetBox> buildWalks() {
        List<FeetBox> boxes = new ArrayList<>();
        // Entrance portal and spacious front construction forecourt
        boxes.add(new FeetBox(7, 1, 39, 12, 5, 40));
        boxes.add(new FeetBox(-18, 1, 40, 37, 8, BUTTON_Z));
        // Flanking and stepping over the central control console (z=55..56)
        boxes.add(new FeetBox(-18, 1, BUTTON_Z, 7, 8, BUTTON_Z + 1));
        boxes.add(new FeetBox(12, 1, BUTTON_Z, 37, 8, BUTTON_Z + 1));
        boxes.add(new FeetBox(-18, 1, BUTTON_Z + 1, 6, 8, BUTTON_Z + 2));
        boxes.add(new FeetBox(13, 1, BUTTON_Z + 1, 37, 8, BUTTON_Z + 2));
        boxes.add(new FeetBox(6, 2, BUTTON_Z + 1, 7, 8, BUTTON_Z + 2));
        boxes.add(new FeetBox(12, 2, BUTTON_Z + 1, 13, 8, BUTTON_Z + 2));
        boxes.add(new FeetBox(7, 4.0625, BUTTON_Z, 12, 7.5, BUTTON_Z + 1));
        boxes.add(new FeetBox(7, 4.5, BUTTON_Z + 1, 12, 7.5, BUTTON_Z + 2));
        // Leave room for jumping down from the cabs and loads on either side of the rig.
        boxes.add(new FeetBox(-18, 1, BUTTON_Z + 2, 37, WORK_AREA_CEILING, 58));
        boxes.add(new FeetBox(-18, 1, 60, WEST_TRUCK_MIN_X, WORK_AREA_CEILING, 67));
        boxes.add(new FeetBox(GRAY_MAX_X + 1, 1, 60, WHITE_MIN_X, WORK_AREA_CEILING, 67));
        boxes.add(new FeetBox(EAST_TRUCK_MAX_X + 1, 1, 60, 37, WORK_AREA_CEILING, 67));
        // Both stairways rise from the entrance forecourt towards their cab roofs.
        boxes.add(new FeetBox(-18, 1, 58, -17, WORK_AREA_CEILING, 60));
        boxes.add(new FeetBox(-7, 1, 58, 25, WORK_AREA_CEILING, 60));
        boxes.add(new FeetBox(35, 1, 58, 37, WORK_AREA_CEILING, 60));
        for (int step = 0; step < 8; step++) {
            int west = WEST_TRUCK_MIN_X + step, east = EAST_TRUCK_MAX_X - step;
            boxes.add(new FeetBox(west, 1.5 + step, 58, west + 1, WORK_AREA_CEILING, 60));
            boxes.add(new FeetBox(east, 1.5 + step, 58, east + 1, WORK_AREA_CEILING, 60));
        }
        boxes.add(new FeetBox(-9, 9, 58, -7, WORK_AREA_CEILING, 60));
        boxes.add(new FeetBox(25, 9, 58, 27, WORK_AREA_CEILING, 60));
        boxes.add(new FeetBox(-18, 1, 67, 37, WORK_AREA_CEILING, 71));
        // Under rear scaffolding support legs (z=71, pillars at x=-16,-9,-2,20,27,34)
        boxes.add(new FeetBox(-18, 1, 71, -16, 8, 72));
        boxes.add(new FeetBox(-15, 1, 71, -9, 8, 72));
        boxes.add(new FeetBox(-8, 1, 71, -2, 8, 72));
        boxes.add(new FeetBox(-1, 1, 71, 20, 8, 72));
        boxes.add(new FeetBox(21, 1, 71, 27, 8, 72));
        boxes.add(new FeetBox(28, 1, 71, 34, 8, 72));
        boxes.add(new FeetBox(35, 1, 71, 37, 8, 72));
        // Rear promenade and the ground below the two preserved viewing decks.
        boxes.add(new FeetBox(-18, 1, 72, 2, 8, 73));
        boxes.add(new FeetBox(3, 1, 72, 16, 8, 73));
        boxes.add(new FeetBox(17, 1, 72, 37, 8, 73));
        boxes.add(new FeetBox(-18, 1, 73, 37, 8, 82));
        // Twin elevated steel observation viewing decks (y=12, z=70..71)
        boxes.add(new FeetBox(-18, 12, 70, 2, 15, 72));
        boxes.add(new FeetBox(17, 12, 70, 37, 15, 72));
        return List.copyOf(boxes);
    }

    public static final Material BUTTON_IDLE = Material.RED_CONCRETE,
            BUTTON_PRESSED = Material.ORANGE_CONCRETE,
            LAMP_OFF = Material.LIGHT_GRAY_CONCRETE,
            LAMP_ON = Material.LIME_CONCRETE;
    public static final List<Cell> BUTTON_CELLS =
            List.of(
                    new Cell(8, 1, BUTTON_Z),
                    new Cell(8, 2, BUTTON_Z),
                    new Cell(8, 3, BUTTON_Z),
                    new Cell(9, 1, BUTTON_Z),
                    new Cell(9, 2, BUTTON_Z),
                    new Cell(9, 3, BUTTON_Z),
                    new Cell(10, 1, BUTTON_Z),
                    new Cell(10, 2, BUTTON_Z),
                    new Cell(10, 3, BUTTON_Z));
    public static final List<List<Cell>> ROUND_LAMPS =
            List.of(
                    List.of(new Cell(11, 1, BUTTON_Z), new Cell(7, 1, BUTTON_Z)),
                    List.of(new Cell(11, 2, BUTTON_Z), new Cell(7, 2, BUTTON_Z)),
                    List.of(new Cell(11, 3, BUTTON_Z), new Cell(7, 3, BUTTON_Z)));
    public static final Face TITLE = new Face(9.5, 15.6, 71.95, 5.4, 0.85, 180);
    public static final Face STATUS = new Face(9.5, 14.1, 71.95, 4.4, 0.65, 180);
    public static final Face TRACK = new Face(9.5, 12.7, 71.95, 4.4, 0.36, 180);
    public static final Face BUTTON_LABEL = new Face(9.5, 2.5, BUTTON_Z - 0.04, 2.6, 0.6, 180);
    private static final double EJECT_MIN_X = -1.5,
            EJECT_MAX_X = 20.5,
            EJECT_FALLBACK_X = 9.5,
            EJECT_Z = 57.5;

    public static boolean contains(double x, double y, double z, boolean cleared) {
        if (ABOVE_LOADS_AND_CABS.contains(x, y, z)
                || GRAY_WORKSPACE.contains(x, y, z)
                || WHITE_CUT.contains(x, y, z)
                || (cleared && EXIT.contains(x, y, z))) return true;
        for (FeetBox box : WALKS) if (box.contains(x, y, z)) return true;
        return false;
    }

    public static Side vehicleSide(int x, int y, int z) {
        if (z < BLOCK_MIN_Z || z > BLOCK_MAX_Z || y < TRUCK_MIN_Y || y > TRUCK_TOP_Y) return null;
        if (x >= WEST_TRUCK_MIN_X && x < GRAY_MIN_X) return Side.WEST;
        if (x > WHITE_MAX_X && x <= EAST_TRUCK_MAX_X) return Side.EAST;
        return null;
    }

    @FunctionalInterface
    public interface BlockVisitor {
        void visit(int x, int y, int z);
    }

    public static void forBlock(Side side, BlockVisitor visitor) {
        int min = side == Side.WEST ? GRAY_MIN_X : WHITE_MIN_X,
                max = side == Side.WEST ? GRAY_MAX_X : WHITE_MAX_X;
        for (int x = min; x <= max; x++)
            for (int y = BLOCK_MIN_Y; y <= BLOCK_MAX_Y; y++)
                for (int z = BLOCK_MIN_Z; z <= BLOCK_MAX_Z; z++) visitor.visit(x, y, z);
    }

    public static boolean intersectsSweep(
            double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        return maxX > SWEEP.x0
                && minX < SWEEP.x1
                && maxY > SWEEP.y0
                && minY < SWEEP.y1
                && maxZ > SWEEP.z0
                && minZ < SWEEP.z1;
    }

    public static Vector ejectTarget(double x) {
        double targetX =
                Double.isFinite(x) ? Math.clamp(x, EJECT_MIN_X, EJECT_MAX_X) : EJECT_FALLBACK_X;
        return new Vector(targetX, 1, EJECT_Z);
    }

    public static Vector restoreSpawn(int slot) {
        return new Vector(7.5 + slot, 1, 41.5);
    }

    public static boolean inDigZone(int x, int y, int z) {
        return y >= BLOCK_MIN_Y && GRAY_WORKSPACE.contains(x, y, z);
    }

    /** Material in an uncut load, using its coordinates before the piston extends. */
    public static Material loadMaterial(Side side, int homeX) {
        if (side == Side.WEST)
            return homeX <= GRAY_MAX_X - JointShape.MAX_DEPTH
                    ? Material.LIGHT_GRAY_CONCRETE
                    : Material.GRAY_CONCRETE;
        return homeX >= WHITE_BASE_X ? Material.LIGHT_GRAY_CONCRETE : Material.WHITE_CONCRETE;
    }

    /** Include the old rear staircases when migrating a plaza that already has it. */
    public static boolean forkliftAccessUpgrade(
            int x, int y, int z, Material material, int version) {
        if (y >= BLOCK_MIN_Y
                && y <= BLOCK_MAX_Y
                && z >= BLOCK_MIN_Z
                && z <= BLOCK_MAX_Z
                && ((x >= GRAY_MIN_X && x <= GRAY_MAX_X - JointShape.MAX_DEPTH)
                        || (x >= WHITE_BASE_X && x <= WHITE_MAX_X))) return true;
        int westX = Math.min(x, WEST_TRUCK_MIN_X + EAST_TRUCK_MAX_X - x);
        if (stairFootprint(westX, y, BLOCK_MIN_Z + BLOCK_MAX_Z - z)) return true;
        if (version == 19) return stairFootprint(westX, y, z);
        return (x == -19 || x == 37)
                && z >= 40
                && z < EXIT_Z
                && y >= 7
                && y <= 14
                && material == Material.BARRIER;
    }

    private static boolean stairFootprint(int westX, int y, int z) {
        if (z == 67 || z == 68)
            return westX >= -17 && westX <= -8 && y >= 1 && y <= Math.min(8, westX + 18);
        return y == BLOCK_MAX_Y
                && ((z == 66 && (westX == -8 || westX == -9))
                        || (westX == -7 && (z == 64 || z == 65)));
    }

    public static boolean button(int x, int y, int z) {
        return BUTTON_CELLS.contains(new Cell(x, y, z));
    }

    public static BoundingBox displayBounds() {
        return new BoundingBox(MIN_X, MIN_Y, MIN_Z, MIN_X + WIDTH, MIN_Y + HEIGHT, MIN_Z + LENGTH);
    }

    public static boolean chunkInBounds(int relChunkX, int relChunkZ) {
        return relChunkX >= (MIN_X >> 4)
                && relChunkX <= ((MIN_X + WIDTH - 1) >> 4)
                && relChunkZ >= (MIN_Z >> 4)
                && relChunkZ <= ((MIN_Z + LENGTH - 1) >> 4);
    }

    private JointPlaza() {}

    /** Chunk-covering points in lane-relative coordinates. */
    public static List<Vector> preloadPoints() {
        List<Vector> points = new ArrayList<>();
        for (int x = MIN_X >> 4; x <= (MIN_X + WIDTH - 1) >> 4; x++) {
            for (int z = MIN_Z >> 4; z <= (MIN_Z + LENGTH - 1) >> 4; z++) {
                points.add(new Vector(x * 16, 1, z * 16));
            }
        }
        return List.copyOf(points);
    }
}
