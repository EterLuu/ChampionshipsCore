package ink.ziip.championshipscore.api.game.tntrun.geometry;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

/** Selects a bounded rain wave from immutable arena coordinates, without server objects. */
public final class TNTRunRainPattern {
    public record Point(double x, double y, double z) {}

    public record Bounds(
            double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        boolean contains(Point point) {
            return point.x >= minX
                    && point.x < maxX
                    && point.y >= minY
                    && point.y < maxY
                    && point.z >= minZ
                    && point.z < maxZ;
        }
    }

    private TNTRunRainPattern() {}

    public static List<Point> select(
            List<Point> origins, List<Bounds> bounds, RandomGenerator random) {
        if (origins.isEmpty() || bounds.isEmpty()) return List.of();
        List<Point> result = new ArrayList<>(12);
        for (int i = 0; i < 12; i++) {
            Point origin = origins.get(i % origins.size());
            for (int attempt = 0; attempt < 64; attempt++) {
                Point point =
                        new Point(
                                origin.x + random.nextInt(-30, 30),
                                origin.y + 15,
                                origin.z + random.nextInt(-30, 30));
                if (inside(bounds, point)) {
                    result.add(point);
                    break;
                }
            }
        }
        return List.copyOf(result);
    }

    private static boolean inside(List<Bounds> bounds, Point point) {
        for (Bounds box : bounds) if (box.contains(point)) return true;
        return false;
    }
}
