package ink.ziip.championshipscore.api.game.riptiderush;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RiptideCourseGeometryTest {
    private static final World WORLD = world("raft-test");

    @Test
    void resolvesCardinalCourseAndMovingFootprint() {
        RiptideCourseGeometry geometry = RiptideCourseGeometry.resolve(
                new Location(WORLD, 1.5, 65, 1.5), new Location(WORLD, 1.5, 65, 41.5),
                7, 9);

        assertEquals(0, geometry.stepX());
        assertEquals(1, geometry.stepZ());
        assertEquals(40, geometry.totalSteps());
        assertEquals(10, geometry.levelStep(0, 3));
        assertEquals(20, geometry.levelStep(1, 3));
        assertEquals(30, geometry.levelStep(2, 3));
        assertEquals(1.5D, geometry.lateralOffset(new Location(WORLD, 3, 65, 21.5), 20));
        assertEquals(-1.5D, geometry.lateralOffset(new Location(WORLD, 0, 65, 21.5), 20));
        assertTrue(geometry.containsMovingRaft(new Location(WORLD, 2, 65, 12), 10, 0, 6));
        assertFalse(geometry.containsMovingRaft(new Location(WORLD, 2, 57, 12), 10, 0, 6));
    }

    @Test
    void rejectsDiagonalDifferentHeightAndEvenRaftDimensions() {
        assertThrows(IllegalArgumentException.class, () -> RiptideCourseGeometry.resolve(
                new Location(WORLD, 1.5, 65, 1.5), new Location(WORLD, 20, 65, 20),
                7, 9));
        assertThrows(IllegalArgumentException.class, () -> RiptideCourseGeometry.resolve(
                new Location(WORLD, 1.5, 65, 1.5), new Location(WORLD, 1.5, 66, 20),
                7, 9));
        assertThrows(IllegalArgumentException.class, () -> RiptideCourseGeometry.resolve(
                new Location(WORLD, 1.5, 65, 1.5), new Location(WORLD, 1.5, 65, 20),
                6, 9));
    }

    private static World world(String name) {
        return (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> name;
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> name;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
