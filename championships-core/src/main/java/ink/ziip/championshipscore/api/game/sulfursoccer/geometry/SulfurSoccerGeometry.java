package ink.ziip.championshipscore.api.game.sulfursoccer.geometry;

import ink.ziip.championshipscore.api.game.sulfursoccer.model.SulfurSoccerSide;

import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

/** Block-inclusive WorldEdit selections and swept ball-center goal detection. */
public final class SulfurSoccerGeometry {
    private SulfurSoccerGeometry() {}

    public static BoundingBox box(Vector first, Vector second) {
        if (!finite(first) || !finite(second))
            throw new IllegalArgumentException("请设置有效的球场、半场和球门选区");
        return BoundingBox.of(
                Vector.getMinimum(first, second),
                Vector.getMaximum(first, second).add(new Vector(1, 1, 1)));
    }

    public static boolean finite(Vector point) {
        return point != null
                && Double.isFinite(point.getX())
                && Double.isFinite(point.getY())
                && Double.isFinite(point.getZ());
    }

    /** Returns the first entry along the movement segment, or infinity when it misses. */
    public static double entry(BoundingBox goal, Vector from, Vector to) {
        if (!finite(from) || !finite(to)) return Double.POSITIVE_INFINITY;
        if (goal.contains(from)) return 0;
        double[] start = {from.getX(), from.getY(), from.getZ()};
        double[] end = {to.getX(), to.getY(), to.getZ()};
        double[] min = {goal.getMinX(), goal.getMinY(), goal.getMinZ()};
        double[] max = {goal.getMaxX(), goal.getMaxY(), goal.getMaxZ()};
        double near = 0, far = 1;
        for (int axis = 0; axis < 3; axis++) {
            double delta = end[axis] - start[axis];
            if (Math.abs(delta) < 1E-9) {
                if (start[axis] < min[axis] || start[axis] >= max[axis])
                    return Double.POSITIVE_INFINITY;
                continue;
            }
            double a = (min[axis] - start[axis]) / delta;
            double b = (max[axis] - start[axis]) / delta;
            near = Math.max(near, Math.min(a, b));
            far = Math.min(far, Math.max(a, b));
            if (near > far) return Double.POSITIVE_INFINITY;
        }
        // A tangent at the excluded upper face does not enter the goal volume.
        if (near == far && !goal.contains(to)) return Double.POSITIVE_INFINITY;
        return near;
    }

    public static SulfurSoccerSide crossedGoal(
            BoundingBox right, BoundingBox left, Vector from, Vector to) {
        double rightEntry = entry(right, from, to);
        double leftEntry = entry(left, from, to);
        if (!Double.isFinite(rightEntry) && !Double.isFinite(leftEntry)) return null;
        return rightEntry <= leftEntry ? SulfurSoccerSide.RIGHT : SulfurSoccerSide.LEFT;
    }
}
