package ink.ziip.championshipscore.api.game.acerace;

import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

/**
 * An axis-aligned WorldEdit line selection. The horizontal axis with the larger span is treated as the
 * line and the other axis as its normal.
 */
record AceRaceLine(@NotNull Vector pos1, @NotNull Vector pos2) {
    private static final double EPSILON = 1.0E-6D;

    private int minX() { return Math.min(pos1.getBlockX(), pos2.getBlockX()); }
    private int maxX() { return Math.max(pos1.getBlockX(), pos2.getBlockX()); }
    private int minY() { return Math.min(pos1.getBlockY(), pos2.getBlockY()); }
    private int maxY() { return Math.max(pos1.getBlockY(), pos2.getBlockY()); }
    private int minZ() { return Math.min(pos1.getBlockZ(), pos2.getBlockZ()); }
    private int maxZ() { return Math.max(pos1.getBlockZ(), pos2.getBlockZ()); }

    private boolean runsAlongX() {
        return maxX() - minX() >= maxZ() - minZ();
    }

    private double centerX() { return (minX() + maxX() + 1) / 2.0D; }
    private double centerZ() { return (minZ() + maxZ() + 1) / 2.0D; }

    @NotNull Location center(@NotNull org.bukkit.World world) {
        return new Location(world, centerX(), (minY() + maxY() + 1) / 2.0D, centerZ());
    }

    /** Signed horizontal distance from the line's center along its narrow (normal) axis. */
    double signedNormalDistance(@NotNull Location location) {
        return runsAlongX() ? location.getZ() - centerZ() : location.getX() - centerX();
    }

    /** Returns the side of the line, or zero while inside the selected line thickness. */
    int side(@NotNull Location location) {
        double halfThickness = (runsAlongX() ? maxZ() - minZ() + 1 : maxX() - minX() + 1) / 2.0D;
        double distance = signedNormalDistance(location);
        if (Math.abs(distance) <= halfThickness + EPSILON) return 0;
        return distance < 0D ? -1 : 1;
    }

    /** Every race gate uses the selected horizontal line as its floor and extends upward. */
    boolean crossedAtOrAbove(@NotNull Location from, @NotNull Location to) {
        return crossed(from, to, true);
    }

    private boolean crossed(@NotNull Location from, @NotNull Location to, boolean extendUpward) {
        if (from.getWorld() != to.getWorld()) return false;
        double fromNormal = signedNormalDistance(from);
        double normalMovement = signedNormalDistance(to) - fromNormal;
        if (Math.abs(normalMovement) <= EPSILON) return false;

        double halfThickness = (runsAlongX() ? maxZ() - minZ() + 1 : maxX() - minX() + 1) / 2.0D;
        return crossesBoundary(from, to, fromNormal, normalMovement, -halfThickness, extendUpward)
                || crossesBoundary(from, to, fromNormal, normalMovement, halfThickness, extendUpward);
    }

    boolean sameGeometry(@NotNull AceRaceLine other) {
        return minX() == other.minX() && maxX() == other.maxX()
                && minY() == other.minY() && maxY() == other.maxY()
                && minZ() == other.minZ() && maxZ() == other.maxZ();
    }

    /**
     * Intersects one of the gate strip's two normal boundaries with the player's movement. Requiring
     * that intersection to fall along the selected line prevents an endpoint exit from counting.
     */
    private boolean crossesBoundary(@NotNull Location from, @NotNull Location to, double fromNormal,
                                    double normalMovement, double boundary, boolean extendUpward) {
        double progress = (boundary - fromNormal) / normalMovement;
        if (progress < -EPSILON || progress > 1.0D + EPSILON) return false;
        double crossingY = from.getY() + (to.getY() - from.getY()) * progress;
        int crossingBlockY = supportingBlockY(crossingY);
        if (crossingBlockY < minY() || (!extendUpward && crossingBlockY > maxY())) return false;

        double longitudinal = runsAlongX()
                ? from.getX() + (to.getX() - from.getX()) * progress
                : from.getZ() + (to.getZ() - from.getZ()) * progress;
        return runsAlongX()
                ? longitudinal >= minX() - EPSILON && longitudinal <= maxX() + 1.0D + EPSILON
                : longitudinal >= minZ() - EPSILON && longitudinal <= maxZ() + 1.0D + EPSILON;
    }

    /** Matches Bukkit's block lookup at a player's feet, including slabs and stairs. */
    private static int supportingBlockY(double feetY) {
        return (int) Math.floor(feetY - EPSILON);
    }

    /**
     * Checks that a line was crossed from the side containing the reference point. This rejects a
     * player walking back through the finish line from the opposite side.
     */
    boolean crossedFromReferenceSide(@NotNull Location from, @NotNull Location to, @NotNull Location reference) {
        if (!crossedAtOrAbove(from, to)) return false;
        int expectedSide = side(reference);
        if (expectedSide == 0) return false;
        int fromSide = side(from);
        int toSide = side(to);
        return fromSide == expectedSide || (fromSide == 0 && toSide == -expectedSide);
    }

    /** Compatibility direction check for maps migrated from the brief single-line format. */
    boolean crossedTowardReferenceSide(@NotNull Location from, @NotNull Location to,
                                       @NotNull Location reference) {
        if (!crossedAtOrAbove(from, to)) return false;
        int expectedSide = side(reference);
        if (expectedSide == 0) return false;
        int fromSide = side(from);
        int toSide = side(to);
        return fromSide == -expectedSide || (fromSide == 0 && toSide == expectedSide);
    }
}
