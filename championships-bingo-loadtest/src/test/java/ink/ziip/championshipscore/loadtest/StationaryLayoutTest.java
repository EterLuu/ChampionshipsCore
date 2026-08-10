package ink.ziip.championshipscore.loadtest;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StationaryLayoutTest {
    @Test
    void separatesEveryMemberEvenAfterRotatingTheTeamLayout() {
        double separation = 384.0;
        for (double angle : new double[]{0.0, Math.PI / 4.0, Math.PI}) {
            StationaryLayout.Point first = StationaryLayout.dispersed(
                    1200.0, -900.0, angle, 0, separation);
            StationaryLayout.Point second = StationaryLayout.dispersed(
                    1200.0, -900.0, angle, 1, separation);
            StationaryLayout.Point third = StationaryLayout.dispersed(
                    1200.0, -900.0, angle, 2, separation);
            StationaryLayout.Point fourth = StationaryLayout.dispersed(
                    1200.0, -900.0, angle, 3, separation);
            Set<StationaryLayout.Point> points = new HashSet<>();
            points.add(first);
            points.add(second);
            points.add(third);
            points.add(fourth);
            assertEquals(4, points.size());
            assertEquals(separation, distance(first, second), 0.000001);
            assertEquals(separation, distance(first, third), 0.000001);
            assertEquals(separation * Math.sqrt(2.0), distance(first, fourth), 0.000001);
        }
    }

    private static double distance(StationaryLayout.Point first, StationaryLayout.Point second) {
        return Math.hypot(first.x() - second.x(), first.z() - second.z());
    }
}
