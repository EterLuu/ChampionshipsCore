package ink.ziip.championshipscore.api.game.sulfursoccer;

import org.bukkit.Location;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/** Derives the goal mouth and three keeper lanes from either orientation of a published pitch. */
record SulfurSoccerPenaltyLayout(boolean alongX, int direction, Vector ball, Vector shooter,
                                Vector keeper, int paneDepth, int laneMin, int laneWidth,
                                int bottom, int top) {
    static SulfurSoccerPenaltyLayout resolve(BoundingBox field, BoundingBox goal, BoundingBox otherGoal, double floorY) {
        Vector delta = goal.getCenter().subtract(otherGoal.getCenter());
        boolean alongX = Math.abs(delta.getX()) >= Math.abs(delta.getZ());
        int direction = (alongX ? delta.getX() : delta.getZ()) >= 0 ? 1 : -1;
        double minDepth = alongX ? goal.getMinX() : goal.getMinZ();
        double maxDepth = alongX ? goal.getMaxX() : goal.getMaxZ();
        double face = direction > 0 ? minDepth : maxDepth;
        int laneMin = (int) (alongX ? goal.getMinZ() : goal.getMinX());
        int width = (int) (alongX ? goal.getWidthZ() : goal.getWidthX());
        if (width < 3 || floorY < goal.getMinY() || floorY + 1.8 > goal.getMaxY())
            throw new IllegalArgumentException("点球球门必须至少宽 3 格，且能容纳站在球场地面的守门员");
        double across = laneMin + width / 2.0;
        double ballDepth = Math.floor(face - direction * 5.5) + 0.5;
        Vector ball = point(alongX, ballDepth, floorY, across);
        Vector shooter = point(alongX, ballDepth - direction * 2, floorY, across);
        Vector keeper = point(alongX, face + direction * Math.min(0.75, (maxDepth - minDepth) / 2), floorY, across);
        if (!field.contains(ball) || !field.contains(shooter) || !goal.contains(keeper)
                || goal.contains(ball) || otherGoal.contains(ball) || otherGoal.contains(shooter))
            throw new IllegalArgumentException("点球球门前需要至少 8 格球场空间，用于足球与射手隔离位置");
        int paneDepth = (int) Math.floor(face - direction * 0.5);
        return new SulfurSoccerPenaltyLayout(alongX, direction, ball, shooter, keeper, paneDepth, laneMin, width,
                (int) Math.floor(floorY), (int) Math.ceil(goal.getMaxY()));
    }

    private static Vector point(boolean alongX, double depth, double y, double across) {
        return new Vector(alongX ? depth : across, y, alongX ? across : depth);
    }

    /** Left/right are from the keeper's view while facing the shooter. */
    List<Vector> paneBlocks(int slot) {
        if (slot < 0 || slot > 2) throw new IllegalArgumentException("守门方向必须为左、中、右");
        int lane = (alongX ? direction : -direction) > 0 ? 2 - slot : slot;
        int first = laneMin + lane * laneWidth / 3;
        int end = laneMin + (lane + 1) * laneWidth / 3;
        List<Vector> blocks = new ArrayList<>();
        for (int across = first; across < end; across++)
            for (int y = bottom; y < top; y++) blocks.add(point(alongX, paneDepth, y, across));
        return blocks;
    }

    Location shooterLocation(org.bukkit.World world) {
        return shooter.toLocation(world).setDirection(keeper.clone().subtract(shooter));
    }

    Location keeperLocation(org.bukkit.World world) {
        return keeper.toLocation(world).setDirection(shooter.clone().subtract(keeper));
    }

    /** Keep the shooter at least one empty block from the cube without obstructing attack raycasts. */
    boolean allowsShooter(Location location) {
        double depth = alongX ? location.getX() : location.getZ();
        double anchor = alongX ? shooter.getX() : shooter.getZ();
        double across = alongX ? location.getZ() : location.getX();
        double lateral = alongX ? shooter.getZ() : shooter.getX();
        return (depth - anchor) * direction <= 0.01 && Math.abs(depth - anchor) <= 0.25
                && Math.abs(across - lateral) <= 1.25 && Math.abs(location.getY() - shooter.getY()) < 0.01;
    }
}
