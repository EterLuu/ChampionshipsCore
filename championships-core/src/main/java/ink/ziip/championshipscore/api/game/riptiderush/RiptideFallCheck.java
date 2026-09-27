package ink.ziip.championshipscore.api.game.riptiderush;

import org.bukkit.Location;
import org.bukkit.block.Block;

/** Shared deck support probing and moving-raft fall rule for rounds and trials. */
final class RiptideFallCheck {
    static final int CONFIRM_TICKS = 20;
    private Integer outsideSince;
    private Integer lastTick;
    private boolean confirmed;

    /** One sample per server tick, including a possible final settlement sample. */
    boolean sample(RiptideCourseGeometry geometry, Location location, int completedSteps,
                   double horizontalPadding, double fallDistance, boolean inLiquid,
                   boolean deckPresent, int tick) {
        if (lastTick != null && lastTick == tick) return confirmed;
        lastTick = tick;
        if (location == null || location.getWorld() == null
                || !location.getWorld().equals(geometry.centerAt(completedSteps).getWorld())) {
            confirmed = true;
            return true;
        }
        return confirm(outside(geometry, location, completedSteps, horizontalPadding,
                fallDistance, inLiquid, deckPresent), tick);
    }

    private boolean confirm(boolean outside, int tick) {
        if (!outside) { outsideSince = null; confirmed = false; return false; }
        if (outsideSince == null) outsideSince = tick;
        confirmed = tick - outsideSince >= CONFIRM_TICKS;
        return confirmed;
    }

    static boolean deckPresent(RiptideCourseGeometry geometry, Location location, int completedSteps) {
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

    static boolean outside(RiptideCourseGeometry geometry, Location location, int completedSteps,
                           double horizontalPadding, double fallDistance, boolean inLiquid,
                           boolean deckPresent) {
        double deckY = geometry.floorY() + 1D;
        // Feet must visibly leave the deck, not merely dip by one movement packet.
        // Deep falls remain outside even if the player's horizontal projection overlaps a block.
        if (location.getY() < geometry.floorY() - Math.max(0D, fallDistance)
                || location.getY() < deckY - 0.5D) return true;
        if (deckPresent) return false;
        // Retain the full 0.6-block player footprint at physical edges, plus the configured
        // horizontal margin when airborne. Touching liquid only starts the same grace period.
        return inLiquid || !geometry.containsMovingRaftHorizontally(location, completedSteps,
                Math.max(0.3D, horizontalPadding));
    }
}
