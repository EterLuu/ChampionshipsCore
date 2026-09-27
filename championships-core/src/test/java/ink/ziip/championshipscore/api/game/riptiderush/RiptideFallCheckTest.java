package ink.ziip.championshipscore.api.game.riptiderush;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RiptideFallCheckTest {
    private static final World WORLD = world("raft-fall");
    private static final RiptideCourseGeometry GEOMETRY = RiptideCourseGeometry.resolve(
            new Location(WORLD, .5, 80, -20.5), new Location(WORLD, .5, 80, 79.5), 7, 9);

    @Test
    void edgesJumpsAndSmallVerticalDipsRemainSafeInEveryDirection() {
        for (int[] axis : new int[][]{{0, 1}, {0, -1}, {1, 0}, {-1, 0}}) {
            var start = GEOMETRY.centerAt(0);
            var g = RiptideCourseGeometry.resolve(start, start.clone().add(axis[0] * 100, 0, axis[1] * 100), 7, 9);
            for (double forward : new double[]{-4.79, 0, 4.79}) {
                for (double lateral : new double[]{-3.79, 0, 3.79}) {
                    for (double height : new double[]{-.1, 0, 1.25}) {
                        var feet = g.centerAt(10).add(axis[0] * forward + axis[1] * lateral, height,
                                axis[1] * forward - axis[0] * lateral);
                        assertFalse(RiptideFallCheck.outside(g, feet, 10, .65, 6, false, true));
                        assertFalse(RiptideFallCheck.outside(g, feet, 10, .65, 6, false, false),
                                "A moving deck or jump should retain the configured edge margin");
                    }
                }
            }
        }
    }

    @Test
    void unsupportedHorizontalDepartureAndVisibleVerticalFallNeedAFullSecond() {
        for (var offset : new double[][]{{4.16, 0, 0}, {0, 0, 5.16}, {0, -.51, 0}, {0, -12, 0}}) {
            var feet = GEOMETRY.centerAt(10).add(offset[0], offset[1], offset[2]);
            var check = new RiptideFallCheck();
            for (int tick = 100; tick < 120; tick++) {
                assertFalse(check.sample(GEOMETRY, feet, 10, .65, 6, false, false, tick));
                // Settlement may sample again in the same server tick; it must not accelerate time.
                assertFalse(check.sample(GEOMETRY, feet, 10, .65, 6, false, false, tick));
            }
            assertTrue(check.sample(GEOMETRY, feet, 10, .65, 6, false, false, 120));
        }
    }

    @Test
    void landingClearsGraceAndPlayersHaveIndependentTimers() {
        var first = new RiptideFallCheck();
        var second = new RiptideFallCheck();
        var outside = GEOMETRY.centerAt(10).add(5, 0, 0);
        var safe = GEOMETRY.centerAt(10);
        assertFalse(first.sample(GEOMETRY, outside, 10, .65, 6, false, false, 0));
        assertFalse(second.sample(GEOMETRY, outside, 10, .65, 6, false, false, 10));
        assertFalse(first.sample(GEOMETRY, safe, 10, .65, 6, false, true, 19));
        assertFalse(first.sample(GEOMETRY, outside, 10, .65, 6, false, false, 20));
        assertTrue(second.sample(GEOMETRY, outside, 10, .65, 6, false, false, 30));
        assertFalse(first.sample(GEOMETRY, outside, 10, .65, 6, false, false, 39));
        assertTrue(first.sample(GEOMETRY, outside, 10, .65, 6, false, false, 40));
        assertFalse(first.sample(GEOMETRY, safe, 10, .65, 6, false, true, 41));
        assertFalse(first.sample(GEOMETRY, outside, 10, .65, 6, false, false, 42));
    }

    @Test
    void liquidUsesTheSameGraceAndDoesNotOverrideAnEdgeWithSupport() {
        var center = GEOMETRY.centerAt(10);
        assertFalse(RiptideFallCheck.outside(GEOMETRY, center, 10, .65, 6, true, true));
        var check = new RiptideFallCheck();
        for (int tick = 0; tick < 20; tick++)
            assertFalse(check.sample(GEOMETRY, center, 10, .65, 6, true, false, tick));
        assertTrue(check.sample(GEOMETRY, center, 10, .65, 6, true, false, 20));
    }

    @Test
    void wrongWorldIsNotTreatedAsAnEdgeExcursion() {
        assertTrue(new RiptideFallCheck().sample(GEOMETRY, new Location(world("other"), 0, 80, 0),
                10, .65, 6, false, false, 0));
    }

    @Test
    void physicalSupportIncludesRearAndSideOverhangButExcludesAirAndOldTrail() {
        World deckWorld = (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> "deck";
                    case "getBlockAt" -> Proxy.newProxyInstance(org.bukkit.block.Block.class.getClassLoader(),
                            new Class<?>[]{org.bukkit.block.Block.class}, (block, call, values) -> switch (call.getName()) {
                                case "isEmpty" -> !((int) args[0] == 3 && (int) args[2] == 6);
                                case "isLiquid" -> false;
                                default -> throw new UnsupportedOperationException(call.getName());
                            });
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        var g = RiptideCourseGeometry.resolve(new Location(deckWorld, .5, 80, .5),
                new Location(deckWorld, .5, 80, 100.5), 7, 9);
        var edge = new Location(deckWorld, 4.2, 80, 5.8);
        assertTrue(RiptideFallCheck.deckPresent(g, edge, 10));
        assertFalse(RiptideFallCheck.deckPresent(g, edge.clone().add(.11, 0, 0), 10));
        assertFalse(RiptideFallCheck.deckPresent(g, g.centerAt(10), 10));
        assertFalse(RiptideFallCheck.deckPresent(g, edge, 11), "old deck cell is now behind the raft");
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
