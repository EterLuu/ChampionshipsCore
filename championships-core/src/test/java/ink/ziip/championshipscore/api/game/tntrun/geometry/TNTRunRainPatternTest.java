package ink.ziip.championshipscore.api.game.tntrun.geometry;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.*;

class TNTRunRainPatternTest {
    @Test
    void waveUsesTwelveRoundRobinOriginsAndFifteenBlockHeight() {
        var origins =
                List.of(
                        new TNTRunRainPattern.Point(-80, 50, 20),
                        new TNTRunRainPattern.Point(80, 70, 20));
        var boxes = List.of(new TNTRunRainPattern.Bounds(-200, 0, -200, 200, 100, 200));
        var points = TNTRunRainPattern.select(origins, boxes, new Random(48));
        assertEquals(12, points.size());
        for (int i = 0; i < 12; i++) {
            var p = points.get(i);
            var origin = origins.get(i % 2);
            assertEquals(origin.y() + 15, p.y());
            assertTrue(p.x() >= origin.x() - 30 && p.x() < origin.x() + 30);
            assertTrue(p.z() >= origin.z() - 30 && p.z() < origin.z() + 30);
        }
        assertThrows(UnsupportedOperationException.class, points::clear);
    }

    @Test
    void impossibleHeightHasAFixedAttemptLimitAndEmptyInputsDoNoWork() {
        var random =
                new Random(0) {
                    int calls;

                    @Override
                    public int nextInt(int origin, int bound) {
                        assertTrue(++calls <= 12 * 64 * 2);
                        return origin;
                    }
                };
        assertTrue(
                TNTRunRainPattern.select(
                                List.of(new TNTRunRainPattern.Point(0, 90, 0)),
                                List.of(new TNTRunRainPattern.Bounds(-50, 0, -50, 50, 100, 50)),
                                random)
                        .isEmpty());
        assertTrue(TNTRunRainPattern.select(List.of(), List.of(), random).isEmpty());
    }

    @Test
    void copyGapsAndExclusiveMaximumRemainOutside() {
        var left = new TNTRunRainPattern.Bounds(-10, 0, -10, 10, 100, 10);
        var right = new TNTRunRainPattern.Bounds(30, 0, -10, 50, 100, 10);
        assertFalse(left.contains(new TNTRunRainPattern.Point(10, 20, 0)));
        assertFalse(left.contains(new TNTRunRainPattern.Point(0, 100, 0)));
        for (var p :
                TNTRunRainPattern.select(
                        List.of(new TNTRunRainPattern.Point(20, 50, 0)),
                        List.of(left, right),
                        new Random(96))) {
            assertTrue(left.contains(p) || right.contains(p));
        }
    }
}
