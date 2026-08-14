package ink.ziip.championshipscore.api.game.buildmart;

/** Shared movement limits for Build Mart wind vents. */
final class BuildMartWindVentPolicy {
    static final double TOP_Y = 200.0;
    static final double MAX_UPWARD_VELOCITY = 3.0;

    private BuildMartWindVentPolicy() {
    }

    static boolean affectsPlayer(boolean gliding, boolean aboveWindZone) {
        return !gliding && aboveWindZone;
    }

    static double upwardVelocity(double playerY) {
        return Math.max(0.0, Math.min(MAX_UPWARD_VELOCITY, TOP_Y - playerY));
    }
}
