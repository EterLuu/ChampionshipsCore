package ink.ziip.championshipscore.loadtest;

final class NaturalSpawnPlanner {
    private static final double GOLDEN_ANGLE = Math.PI * (3.0 - Math.sqrt(5.0));
    private static final double GOLDEN_FRACTION = 0.6180339887498949;

    private NaturalSpawnPlanner() {
    }

    static Offset offset(long sequence, int ownerCount, double minimumDistance,
                         double maximumDistance) {
        if (sequence < 0L || ownerCount < 1 || minimumDistance < 0.0
                || maximumDistance <= minimumDistance) {
            throw new IllegalArgumentException("Invalid natural spawn planner input");
        }
        int owner = (int) (sequence % ownerCount);
        long ownerSequence = sequence / ownerCount;
        double unit = (ownerSequence * GOLDEN_FRACTION) % 1.0;
        double minimumSquared = minimumDistance * minimumDistance;
        double maximumSquared = maximumDistance * maximumDistance;
        double radius = Math.sqrt(minimumSquared + unit * (maximumSquared - minimumSquared));
        double angle = ownerSequence * GOLDEN_ANGLE + owner * Math.PI * 2.0 / ownerCount;
        return new Offset(owner, Math.cos(angle) * radius, Math.sin(angle) * radius);
    }

    record Offset(int ownerIndex, double x, double z) {
        double distance() {
            return Math.hypot(x, z);
        }
    }
}
