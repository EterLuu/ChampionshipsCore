package ink.ziip.championshipscore.api.game.acerace;

import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;

/** A proximity marker which is bound to its surrounding progress segment from its position at load time. */
record AceRaceRespawnPoint(@NotNull Location location) {
    private static final double CAPTURE_RADIUS_SQUARED = 16.0D;

    boolean reached(@NotNull Location from, @NotNull Location to) {
        return crossingProgress(from, to) >= 0.0D;
    }

    /** Returns where this marker is reached along the movement segment, or -1 when not reached. */
    double crossingProgress(@NotNull Location from, @NotNull Location to) {
        if (from.getWorld() != to.getWorld()
                || (location.getWorld() != null
                && (from.getWorld() != location.getWorld() || to.getWorld() != location.getWorld())))
            return -1.0D;

        double segmentX = to.getX() - from.getX();
        double segmentY = to.getY() - from.getY();
        double segmentZ = to.getZ() - from.getZ();
        double lengthSquared = segmentX * segmentX + segmentY * segmentY + segmentZ * segmentZ;
        double progress = 0.0D;
        if (lengthSquared > 0.0D) {
            progress = ((location.getX() - from.getX()) * segmentX
                    + (location.getY() - from.getY()) * segmentY
                    + (location.getZ() - from.getZ()) * segmentZ) / lengthSquared;
            progress = Math.max(0.0D, Math.min(1.0D, progress));
        }

        double nearestX = from.getX() + segmentX * progress;
        double nearestY = from.getY() + segmentY * progress;
        double nearestZ = from.getZ() + segmentZ * progress;
        double distanceX = location.getX() - nearestX;
        double distanceY = location.getY() - nearestY;
        double distanceZ = location.getZ() - nearestZ;
        return distanceX * distanceX + distanceY * distanceY + distanceZ * distanceZ
                <= CAPTURE_RADIUS_SQUARED ? progress : -1.0D;
    }

    @NotNull Location destination() {
        return location.clone();
    }
}
