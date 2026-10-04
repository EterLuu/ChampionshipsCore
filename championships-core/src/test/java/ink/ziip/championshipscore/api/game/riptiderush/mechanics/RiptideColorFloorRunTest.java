package ink.ziip.championshipscore.api.game.riptiderush.mechanics;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.api.game.riptiderush.course.RiptideCourseGeometry;
import ink.ziip.championshipscore.api.game.riptiderush.course.RiptideDifficulty;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

class RiptideColorFloorRunTest {
    private static final List<Material> COLORS =
            RiptideColorFloorRun.materials(RiptideColorFloorRun.Theme.ORE).subList(0, 6);
    private static final World WORLD = world();

    @Test
    void sevenDeadlinesUseExactInitialStageTiming() {
        var run = run(7, 9, RiptideColorFloorRun.Theme.ORE, 5);
        assertEquals(List.of(120, 100, 100, 80, 80, 80, 80), run.durations());
        int ticks = 0;
        Material firstTarget = run.target();
        var firstFloor = run.floor();
        for (int tick = 0; tick < 30; tick++) {
            assertTrue(run.preparing());
            assertFalse(run.tick());
            assertEquals(120, run.remainingTicks());
            assertEquals(firstTarget, run.target());
            assertEquals(firstFloor, run.floor());
            ticks++;
        }
        assertFalse(run.preparing());
        Material previous = null;
        for (int round = 1; round <= 7; round++) {
            assertEquals(round, run.roundNumber());
            assertNotEquals(previous, run.target());
            previous = run.target();
            for (int i = 1; i < run.durations().get(round - 1); i++) {
                assertFalse(run.tick());
                ticks++;
            }
            assertTrue(run.tick());
            ticks++;
            assertFalse(run.tick(), "a deadline cannot eliminate players twice");
            assertEquals(round < 7, run.advance());
        }
        assertEquals(670, ticks);
    }

    @Test
    void layoutModeIsDrawnIndependentlyForEachRound() {
        boolean foundMixedSeed = false;
        for (int seed = 0; seed < 32 && !foundMixedSeed; seed++) {
            var run = run(7, 9, RiptideColorFloorRun.Theme.ORE, seed);
            boolean preset = run.presetPattern();
            boolean random = !preset;
            for (int round = 0; round < 7; round++) {
                while (!run.tick()) {}
                if (!run.advance()) break;
                preset |= run.presetPattern();
                random |= !run.presetPattern();
            }
            foundMixedSeed = preset && random;
        }
        assertTrue(foundMixedSeed, "round mode must not be fixed for the whole stage");
    }

    @Test
    void everyThemeOffersTheRequiredNumberOfRealAnswersAcrossSeedsAndSmallRafts() {
        for (var theme : RiptideColorFloorRun.Theme.values()) {
            for (int size : new int[] {3, 7, 9}) {
                for (int seed = 0; seed < 50; seed++) {
                    var run = run(size, size == 7 ? 9 : size, theme, seed);
                    for (int round = 0; round < 7; round++) {
                        assertEquals(
                                theme == RiptideColorFloorRun.Theme.COPPER ? 8 : 6,
                                new HashSet<>(run.floor()).size());
                        assertTrue(run.floor().contains(run.target()));
                        assertTrue(RiptideColorFloorRun.materials(theme).containsAll(run.floor()));
                        while (!run.tick()) {}
                        run.advance();
                    }
                }
            }
        }
    }

    @Test
    void copperPoolIsExactlyFourWaxedAgesAcrossThreeFullBlockShapes() {
        var pool = RiptideColorFloorRun.materials(RiptideColorFloorRun.Theme.COPPER);
        assertEquals(12, new HashSet<>(pool).size());
        assertTrue(pool.stream().allMatch(material -> material.name().startsWith("WAXED_")));
        assertEquals(
                4, pool.stream().filter(material -> material.name().contains("CHISELED")).count());
        assertEquals(4, pool.stream().filter(material -> material.name().contains("CUT")).count());
    }

    @Test
    void structuredConcretePatternsHaveRecognizableGeometryAndAllSixColors() {
        for (var pattern : RiptideColorFloorRun.Pattern.values()) {
            var floor = RiptideColorFloorRun.layout(7, 9, COLORS, pattern, new Random(42));
            assertEquals(63, floor.size());
            assertEquals(6, new HashSet<>(floor).size());
            for (int z = 0; z < 9; z++)
                for (int x = 0; x < 7; x++) {
                    if (pattern == RiptideColorFloorRun.Pattern.HORIZONTAL)
                        assertEquals(floor.get(z * 7), floor.get(z * 7 + x));
                    if (pattern == RiptideColorFloorRun.Pattern.VERTICAL)
                        assertEquals(floor.get(x), floor.get(z * 7 + x));
                    if (pattern == RiptideColorFloorRun.Pattern.RINGS) {
                        assertEquals(floor.get(z * 7 + x), floor.get((8 - z) * 7 + x));
                        assertEquals(floor.get(z * 7 + x), floor.get(z * 7 + 6 - x));
                    }
                    if (pattern == RiptideColorFloorRun.Pattern.PATCHES) {
                        for (int z2 = 0; z2 < 9; z2++)
                            for (int x2 = 0; x2 < 7; x2++)
                                if (x * 3 / 7 == x2 * 3 / 7 && z * 2 / 9 == z2 * 2 / 9)
                                    assertEquals(floor.get(z * 7 + x), floor.get(z2 * 7 + x2));
                    }
                }
        }
    }

    @Test
    void randomFloorsDistributeEachChoiceEvenlyButChangeWithTheSeed() {
        var first =
                RiptideColorFloorRun.layout(
                        7, 9, COLORS, RiptideColorFloorRun.Pattern.RANDOM, new Random(1));
        var next =
                RiptideColorFloorRun.layout(
                        7, 9, COLORS, RiptideColorFloorRun.Pattern.RANDOM, new Random(2));
        assertNotEquals(first, next);
        for (Material color : COLORS) {
            long count = first.stream().filter(material -> material == color).count();
            assertTrue(count == 10 || count == 11);
        }
    }

    @Test
    void matchesActualBlockCoordinatesInAllDirectionsIncludingNegativeCoordinates() {
        for (int[] direction : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            var geometry =
                    RiptideCourseGeometry.resolve(
                            new Location(WORLD, -20.5, 80, -110.5),
                            new Location(
                                    WORLD,
                                    -20.5 + direction[0] * 100,
                                    80,
                                    -110.5 + direction[1] * 100),
                            7,
                            9);
            var run = run(7, 9, RiptideColorFloorRun.Theme.COPPER, 14);
            for (int z = 0; z < 9; z++)
                for (int x = 0; x < 7; x++) {
                    var feet =
                            new Location(
                                    WORLD,
                                    geometry.blockX(30 + z - 4, x - 3) + .5,
                                    80,
                                    geometry.blockZ(30 + z - 4, x - 3) + .5);
                    boolean expected = run.floor().get(z * 7 + x) == run.target();
                    assertEquals(expected, run.matches(feet, geometry, 30));
                    assertEquals(expected, run.matches(feet.clone().add(0, 1.25, 0), geometry, 30));
                    assertFalse(run.matches(feet.clone().add(0, -1, 0), geometry, 30));
                    assertFalse(run.matches(feet.clone().add(0, 3, 0), geometry, 30));
                    feet.setWorld(world());
                    assertFalse(run.matches(feet, geometry, 30));
                }
            // Movement tolerance outside the edge must not count as a valid floor answer.
            assertFalse(
                    run.matches(
                            geometry.centerAt(30).add(direction[1] * 3.81, 0, -direction[0] * 3.81),
                            geometry,
                            30));
            assertFalse(
                    run.matches(
                            geometry.centerAt(30).add(direction[0] * 4.81, 0, direction[1] * 4.81),
                            geometry,
                            30));
        }
    }

    @Test
    void targetBlockSupportsFeetOverhangingEveryEdgeAndCornerEvenDuringJump() {
        for (int[] direction : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            var geometry =
                    RiptideCourseGeometry.resolve(
                            new Location(WORLD, -20.5, 80, -110.5),
                            new Location(
                                    WORLD,
                                    -20.5 + direction[0] * 100,
                                    80,
                                    -110.5 + direction[1] * 100),
                            7,
                            9);
            var run = run(7, 9, RiptideColorFloorRun.Theme.ORE, 14);
            int index = run.floor().indexOf(run.target());
            int x = geometry.blockX(30 + index / 7 - 4, index % 7 - 3);
            int z = geometry.blockZ(30 + index / 7 - 4, index % 7 - 3);
            for (double dx : new double[] {-.29, .5, 1.29})
                for (double dz : new double[] {-.29, .5, 1.29})
                    for (double dy : new double[] {0, 1.25})
                        assertTrue(
                                run.matches(
                                        new Location(WORLD, x + dx, 80 + dy, z + dz),
                                        geometry,
                                        30));
            assertFalse(
                    RiptideCourseGeometry.overlapsCell(
                            new Location(WORLD, x - .3, 80, z + .5), x, z));
        }
    }

    private static RiptideColorFloorRun run(
            int width, int length, RiptideColorFloorRun.Theme theme, int seed) {
        return new RiptideColorFloorRun(
                width, length, RiptideDifficulty.floorRoundTicks(0, 500), new Random(seed), theme);
    }

    private static World world() {
        return (World)
                Proxy.newProxyInstance(
                        World.class.getClassLoader(),
                        new Class<?>[] {World.class},
                        (proxy, method, args) ->
                                switch (method.getName()) {
                                    case "getName" -> "raft";
                                    case "equals" -> proxy == args[0];
                                    case "hashCode" -> System.identityHashCode(proxy);
                                    default ->
                                            throw new UnsupportedOperationException(
                                                    method.getName());
                                });
    }
}
