package ink.ziip.championshipscore.api.game.riptiderush;

import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

/** Cardinal centre-line geometry derived entirely from start/end points and generator dimensions. */
public final class RiptideCourseGeometry {
    private final Location start;
    private final int stepX;
    private final int stepZ;
    private final int crossX;
    private final int crossZ;
    private final int totalSteps;
    private final int floorY;
    private final int halfWidth;
    private final int halfLength;

    private RiptideCourseGeometry(Location start, int stepX, int stepZ, int totalSteps,
                               int floorY, int halfWidth, int halfLength) {
        this.start = start.clone();
        this.stepX = stepX;
        this.stepZ = stepZ;
        this.crossX = stepZ;
        this.crossZ = -stepX;
        this.totalSteps = totalSteps;
        this.floorY = floorY;
        this.halfWidth = halfWidth;
        this.halfLength = halfLength;
    }

    public static @NotNull RiptideCourseGeometry resolve(@NotNull Location start, @NotNull Location finish,
                                                       int raftWidth, int raftLength) {
        if (start.getWorld() == null || finish.getWorld() == null
                || !start.getWorld().getName().equals(finish.getWorld().getName()))
            throw new IllegalArgumentException("start and finish must be in the same loaded world");
        if (raftWidth < 3 || raftLength < 3 || raftWidth % 2 == 0 || raftLength % 2 == 0)
            throw new IllegalArgumentException("raft width and length must be odd and at least three");
        if (start.getBlockY() != finish.getBlockY())
            throw new IllegalArgumentException("start and finish must be at the same height");

        int deltaX = finish.getBlockX() - start.getBlockX();
        int deltaZ = finish.getBlockZ() - start.getBlockZ();
        if ((deltaX != 0 && deltaZ != 0) || (deltaX == 0 && deltaZ == 0))
            throw new IllegalArgumentException("course must follow one axis");
        int stepX;
        int stepZ;
        int totalSteps;
        if (deltaX != 0) {
            stepX = deltaX > 0 ? 1 : -1;
            stepZ = 0;
            totalSteps = Math.abs(deltaX);
        } else {
            stepX = 0;
            stepZ = deltaZ > 0 ? 1 : -1;
            totalSteps = Math.abs(deltaZ);
        }
        if (totalSteps < 16) throw new IllegalArgumentException("course must be at least sixteen blocks long");
        return new RiptideCourseGeometry(start, stepX, stepZ, totalSteps,
                start.getBlockY() - 1, raftWidth / 2, raftLength / 2);
    }

    /** Evenly maps the ordered logical level list to physical points on the centre line. */
    public int levelStep(int index, int levelCount) {
        if (index < 0 || index >= levelCount || levelCount < 1)
            throw new IllegalArgumentException("invalid level index");
        return (int) Math.round((index + 1D) * totalSteps / (levelCount + 1D));
    }

    public @NotNull Location centerAt(int forwardStep) {
        return new Location(start.getWorld(), blockX(forwardStep, 0) + 0.5D,
                floorY + 1D, blockZ(forwardStep, 0) + 0.5D,
                start.getYaw(), start.getPitch());
    }

    /** All stopped decks end one block before their gold entrance. */
    int stoppedStep(int entranceStep) { return entranceStep - halfLength - 1; }

    /** The stopped deck and its entrance occupy the same longitudinal span for every child type. */
    int occupiedStart(RiptideCoursePlan.Level level) {
        return level.stopsRaft() ? stoppedStep(level.step()) - halfLength : level.step() - level.extent();
    }

    int occupiedEnd(RiptideCoursePlan.Level level) {
        return level.stopsRaft() ? level.step() : level.step() + level.extent();
    }

    public int blockX(int forwardStep, int lateralOffset) {
        return start.getBlockX() + stepX * forwardStep + crossX * lateralOffset;
    }

    public int blockZ(int forwardStep, int lateralOffset) {
        return start.getBlockZ() + stepZ * forwardStep + crossZ * lateralOffset;
    }

    /** Signed distance from the centre line; positive is left in the players' forward direction. */
    public double lateralOffset(@NotNull Location location, int forwardStep) {
        Location center = centerAt(forwardStep);
        return (location.getX() - center.getX()) * crossX
                + (location.getZ() - center.getZ()) * crossZ;
    }

    public double forwardOffset(@NotNull Location location, int forwardStep) {
        Location center = centerAt(forwardStep);
        return (location.getX() - center.getX()) * stepX
                + (location.getZ() - center.getZ()) * stepZ;
    }

    /** Positive-area overlap of the player's 0.6-block footprint with a physical deck cell. */
    static boolean overlapsCell(Location feet, int x, int z) {
        double radius = 0.3D;
        double epsilon = 1e-7D;
        return feet.getX() + radius > x + epsilon && feet.getX() - radius < x + 1D - epsilon
                && feet.getZ() + radius > z + epsilon && feet.getZ() - radius < z + 1D - epsilon;
    }

    public boolean containsMovingRaft(@NotNull Location location, int completedSteps,
                                      double horizontalPadding, double fallDistance) {
        if (!containsMovingRaftHorizontally(location, completedSteps, horizontalPadding)) return false;
        return location.getY() >= floorY - fallDistance;
    }

    /**
     * Tests only the moving raft's footprint. Vertical support is deliberately separate because a
     * player may jump above the deck while temporarily outside the footprint.
     */
    public boolean containsMovingRaftHorizontally(@NotNull Location location, int completedSteps,
                                                  double horizontalPadding) {
        if (location.getWorld() == null || start.getWorld() == null
                || !location.getWorld().getName().equals(start.getWorld().getName())) return false;
        Location center = centerAt(completedSteps);
        double dx = location.getX() - center.getX();
        double dz = location.getZ() - center.getZ();
        double along = dx * stepX + dz * stepZ;
        double across = dx * crossX + dz * crossZ;
        return Math.abs(along) <= halfLength + 0.5D + horizontalPadding
                && Math.abs(across) <= halfWidth + 0.5D + horizontalPadding;
    }

    public @NotNull Vector areaMinimum(int sideMargin, int verticalDrop) {
        return areaCorner(sideMargin, floorY - verticalDrop, true);
    }

    public @NotNull Vector areaMaximum(int sideMargin, int height) {
        return areaCorner(sideMargin, floorY + height, false);
    }

    private Vector areaCorner(int margin, int y, boolean minimum) {
        int first = -halfLength - margin;
        int last = totalSteps + halfLength + margin;
        int lateral = halfWidth + margin;
        int[] xs = {blockX(first, -lateral), blockX(first, lateral), blockX(last, -lateral), blockX(last, lateral)};
        int[] zs = {blockZ(first, -lateral), blockZ(first, lateral), blockZ(last, -lateral), blockZ(last, lateral)};
        return new Vector(minimum ? min(xs) : max(xs), y, minimum ? min(zs) : max(zs));
    }

    private static int min(int[] values) {
        int result = values[0];
        for (int value : values) result = Math.min(result, value);
        return result;
    }

    private static int max(int[] values) {
        int result = values[0];
        for (int value : values) result = Math.max(result, value);
        return result;
    }

    public int stepX() { return stepX; }
    public int stepZ() { return stepZ; }
    public int totalSteps() { return totalSteps; }
    public int floorY() { return floorY; }
    public int halfWidth() { return halfWidth; }
    public int halfLength() { return halfLength; }
    public int raftWidth() { return halfWidth * 2 + 1; }
    public int raftLength() { return halfLength * 2 + 1; }
}
