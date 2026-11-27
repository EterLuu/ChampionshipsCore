package ink.ziip.championshipscore.api.game.riptiderush.runtime;

import ink.ziip.championshipscore.api.game.riptiderush.course.RiptideCourseGeometry;

import org.bukkit.Location;
import org.bukkit.block.Block;

/** Shared deck support probing and moving-raft fall rule for rounds and trials. */
public final class RiptideFallCheck {
    static final int CONFIRM_TICKS = 20;
    private Integer outsideSince;
    private Integer lastTick;
    private boolean confirmed;
    private boolean finished;

    /** One sample per server tick, including a possible final settlement sample. */
    public boolean sample(
            RiptideCourseGeometry geometry,
            Location location,
            int completedSteps,
            double horizontalPadding,
            double fallDistance,
            boolean inLiquid,
            boolean deckPresent,
            int tick) {
        // Arrival is personal: the front of the raft can reach the finish before its centre.
        // Once through the finish, walking on the pier (or back off it) is no longer a raft fall.
        if (finished) return false;
        if (lastTick != null && lastTick == tick) return confirmed;
        lastTick = tick;
        if (location == null
                || location.getWorld() == null
                || !location.getWorld().equals(geometry.centerAt(completedSteps).getWorld())) {
            confirmed = true;
            return true;
        }
        if (!inLiquid && reachedFinish(geometry, location, completedSteps)) {
            finished = true;
            outsideSince = null;
            confirmed = false;
            return false;
        }
        return confirm(
                outside(
                        geometry,
                        location,
                        completedSteps,
                        horizontalPadding,
                        fallDistance,
                        inLiquid,
                        deckPresent),
                tick);
    }

    private static boolean reachedFinish(
            RiptideCourseGeometry geometry, Location feet, int raftStep) {
        if (raftStep < geometry.totalSteps() - geometry.halfLength()) return false;
        if (feet.getY() < geometry.floorY() + 1D - .05D || feet.getY() > geometry.floorY() + 2.5D)
            return false;
        // When the raft docks, everyone still on its deck has arrived, including its rear edge.
        if (raftStep >= geometry.totalSteps()
                && geometry.containsMovingRaftHorizontally(feet, geometry.totalSteps(), .3D))
            return true;
        double forward = geometry.forwardOffset(feet, geometry.totalSteps());
        // Before docking, only the finish opening admits a player, not the banks or distant
        // scenery.
        return forward >= 0D
                && forward <= geometry.halfLength() + 1D
                && Math.abs(geometry.lateralOffset(feet, geometry.totalSteps()))
                        <= geometry.halfWidth() + .5D;
    }

    private boolean confirm(boolean outside, int tick) {
        if (!outside) {
            outsideSince = null;
            confirmed = false;
            return false;
        }
        if (outsideSince == null) outsideSince = tick;
        confirmed = tick - outsideSince >= CONFIRM_TICKS;
        return confirmed;
    }

    public static boolean deckPresent(
            RiptideCourseGeometry geometry, Location location, int completedSteps) {
        if (location.getWorld() == null) return false;
        for (int forward = -geometry.halfLength(); forward <= geometry.halfLength(); forward++) {
            for (int lateral = -geometry.halfWidth(); lateral <= geometry.halfWidth(); lateral++) {
                int x = geometry.blockX(completedSteps + forward, lateral);
                int z = geometry.blockZ(completedSteps + forward, lateral);
                if (!RiptideCourseGeometry.overlapsCell(location, x, z)) continue;
                Block block = location.getWorld().getBlockAt(x, geometry.floorY(), z);
                if (!block.isEmpty() && !block.isLiquid()) return true;
            }
        }
        return false;
    }

    static boolean outside(
            RiptideCourseGeometry geometry,
            Location location,
            int completedSteps,
            double horizontalPadding,
            double fallDistance,
            boolean inLiquid,
            boolean deckPresent) {
        double deckY = geometry.floorY() + 1D;
        // Feet must visibly leave the deck, not merely dip by one movement packet.
        // Deep falls remain outside even if the player's horizontal projection overlaps a block.
        if (location.getY() < geometry.floorY() - Math.max(0D, fallDistance)
                || location.getY() < deckY - 0.5D) return true;
        if (deckPresent) return false;
        // Retain the full 0.6-block player footprint at physical edges, plus the configured
        // horizontal margin when airborne. Touching liquid only starts the same grace period.
        return inLiquid
                || !geometry.containsMovingRaftHorizontally(
                        location, completedSteps, Math.max(0.3D, horizontalPadding));
    }
}
