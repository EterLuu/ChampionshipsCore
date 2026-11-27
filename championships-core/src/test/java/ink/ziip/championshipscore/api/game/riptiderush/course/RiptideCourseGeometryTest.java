package ink.ziip.championshipscore.api.game.riptiderush.course;

import static org.junit.jupiter.api.Assertions.*;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashSet;

class RiptideCourseGeometryTest {
    private static final World WORLD = world("raft-test");

    @Test
    void resolvesCardinalCourseAndMovingFootprint() {
        RiptideCourseGeometry geometry =
                RiptideCourseGeometry.resolve(
                        new Location(WORLD, 1.5, 65, 1.5),
                        new Location(WORLD, 1.5, 65, 41.5),
                        7,
                        9);

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
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        RiptideCourseGeometry.resolve(
                                new Location(WORLD, 1.5, 65, 1.5),
                                new Location(WORLD, 20, 65, 20),
                                7,
                                9));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        RiptideCourseGeometry.resolve(
                                new Location(WORLD, 1.5, 65, 1.5),
                                new Location(WORLD, 1.5, 66, 20),
                                7,
                                9));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        RiptideCourseGeometry.resolve(
                                new Location(WORLD, 1.5, 65, 1.5),
                                new Location(WORLD, 1.5, 65, 20),
                                6,
                                9));
    }

    @Test
    void teamSpawnsStaySeparatedOnDeckForEveryDirectionAndSupportedMinimumDimension() {
        for (int[] direction : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            for (int[] size : new int[][] {{7, 9}, {3, 3}, {3, 9}, {7, 3}}) {
                var start = new Location(WORLD, 1.5, 65, 1.5, 70F, 12F);
                var geometry =
                        RiptideCourseGeometry.resolve(
                                start,
                                start.clone().add(direction[0] * 40, 0, direction[1] * 40),
                                size[0],
                                size[1]);
                var positions = new HashSet<Location>();
                for (int index = 0; index < 4; index++) {
                    Location spawn = geometry.teamSpawn(index);
                    assertTrue(positions.add(spawn), "Teammates must have distinct positions");
                    assertEquals(65D, spawn.getY());
                    assertEquals(70F, spawn.getYaw());
                    assertEquals(12F, spawn.getPitch());
                    assertTrue(geometry.containsMovingRaftHorizontally(spawn, 0, -0.3D));
                    for (Location other : positions) {
                        if (!spawn.equals(other)) assertTrue(spawn.distanceSquared(other) >= 4D);
                    }
                }
                geometry.teamSpawn(0).add(100D, 100D, 100D);
                assertEquals(start, geometry.centerAt(0), "Spawn locations must be independent");
            }
        }
    }

    private static World world(String name) {
        return (World)
                Proxy.newProxyInstance(
                        World.class.getClassLoader(),
                        new Class<?>[] {World.class},
                        (proxy, method, args) ->
                                switch (method.getName()) {
                                    case "getName" -> name;
                                    case "equals" -> proxy == args[0];
                                    case "hashCode" -> System.identityHashCode(proxy);
                                    case "toString" -> name;
                                    default ->
                                            throw new UnsupportedOperationException(
                                                    method.getName());
                                });
    }
}
