package ink.ziip.championshipscore.api.game.riptiderush.runtime;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.api.game.riptiderush.course.RiptideCourseGeometry;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.UUID;

class RiptideFallCheckTest {
    @Nested
    class RiptideFallCheckCases {
        private static final World WORLD = world("raft-fall");
        private static final RiptideCourseGeometry GEOMETRY =
                RiptideCourseGeometry.resolve(
                        new Location(WORLD, .5, 80, -20.5),
                        new Location(WORLD, .5, 80, 79.5),
                        7,
                        9);

        @Test
        void edgesJumpsAndSmallVerticalDipsRemainSafeInEveryDirection() {
            for (int[] axis : new int[][] {{0, 1}, {0, -1}, {1, 0}, {-1, 0}}) {
                var start = GEOMETRY.centerAt(0);
                var g =
                        RiptideCourseGeometry.resolve(
                                start, start.clone().add(axis[0] * 100, 0, axis[1] * 100), 7, 9);
                for (double forward : new double[] {-4.79, 0, 4.79}) {
                    for (double lateral : new double[] {-3.79, 0, 3.79}) {
                        for (double height : new double[] {-.1, 0, 1.25}) {
                            var feet =
                                    g.centerAt(10)
                                            .add(
                                                    axis[0] * forward + axis[1] * lateral,
                                                    height,
                                                    axis[1] * forward - axis[0] * lateral);
                            assertFalse(RiptideFallCheck.outside(g, feet, 10, .65, 6, false, true));
                            assertFalse(
                                    RiptideFallCheck.outside(g, feet, 10, .65, 6, false, false),
                                    "A moving deck or jump should retain the configured edge"
                                            + " margin");
                        }
                    }
                }
            }
        }

        @Test
        void unsupportedHorizontalDepartureAndVisibleVerticalFallNeedAFullSecond() {
            for (var offset :
                    new double[][] {{4.16, 0, 0}, {0, 0, 5.16}, {0, -.51, 0}, {0, -12, 0}}) {
                var feet = GEOMETRY.centerAt(10).add(offset[0], offset[1], offset[2]);
                var check = new RiptideFallCheck();
                for (int tick = 100; tick < 120; tick++) {
                    assertFalse(check.sample(GEOMETRY, feet, 10, .65, 6, false, false, tick));
                    // Settlement may sample again in the same server tick; it must not accelerate
                    // time.
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
            assertTrue(
                    new RiptideFallCheck()
                            .sample(
                                    GEOMETRY,
                                    new Location(world("other"), 0, 80, 0),
                                    10,
                                    .65,
                                    6,
                                    false,
                                    false,
                                    0));
        }

        @Test
        void crossingTheFinishRemovesRaftBoundsForThatPlayerOnEveryAxis() {
            for (int[] axis : new int[][] {{0, 1}, {0, -1}, {1, 0}, {-1, 0}}) {
                var start = GEOMETRY.centerAt(0);
                var g =
                        RiptideCourseGeometry.resolve(
                                start, start.clone().add(axis[0] * 100, 0, axis[1] * 100), 7, 9);
                var check = new RiptideFallCheck();
                // A pending fall before reaching the finish must not survive arrival.
                assertFalse(
                        check.sample(g, g.centerAt(95).add(8, 0, 8), 96, .65, 6, false, false, 0));
                assertFalse(check.sample(g, g.centerAt(100), 96, .65, 6, false, true, 1));
                for (int tick = 2; tick < 100; tick++)
                    assertFalse(
                            check.sample(
                                    g,
                                    g.centerAt(110).add(30, -3, 30),
                                    100,
                                    .65,
                                    6,
                                    true,
                                    false,
                                    tick),
                            "arrival remains safe after leaving the pier");
                var other = new RiptideFallCheck();
                var outside = g.centerAt(90).add(30, 0, 30);
                assertFalse(other.sample(g, outside, 100, .65, 6, false, false, 2));
                assertTrue(
                        other.sample(g, outside, 100, .65, 6, false, false, 22),
                        "another player or a new round has no inherited arrival");
            }
        }

        @Test
        void dockingAlsoFinishesPlayersStillStandingAtTheRearEdge() {
            var rear = GEOMETRY.centerAt(100).add(3.79, 0, -4.79);
            var arrived = new RiptideFallCheck();
            assertFalse(arrived.sample(GEOMETRY, rear, 100, .65, 6, false, true, 0));
            var away = rear.clone().add(20, 0, 20);
            assertFalse(arrived.sample(GEOMETRY, away, 100, .65, 6, false, false, 1));
            assertFalse(arrived.sample(GEOMETRY, away, 100, .65, 6, false, false, 21));
            var stillRacing = new RiptideFallCheck();
            assertFalse(stillRacing.sample(GEOMETRY, rear, 99, .65, 6, false, true, 0));
            stillRacing.sample(GEOMETRY, away, 99, .65, 6, false, false, 1);
            assertTrue(stillRacing.sample(GEOMETRY, away, 99, .65, 6, false, false, 21));
        }

        @Test
        void landingOnThePierIsSafeBeforeTheRaftCentreArrives() {
            var check = new RiptideFallCheck();
            var pier = GEOMETRY.centerAt(105);
            assertFalse(check.sample(GEOMETRY, pier, 99, .65, 6, false, false, 0));
            assertFalse(
                    check.sample(
                            GEOMETRY, pier.clone().add(12, 0, 10), 99, .65, 6, false, false, 21));
        }

        @Test
        void finishCannotBeClaimedEarlyFromTheBankBelowTheDeckOrInAnotherWorld() {
            var finish = GEOMETRY.centerAt(100);
            var locations =
                    List.of(
                            finish,
                            finish.clone().add(4, 0, 0),
                            finish.clone().add(0, -1, 0),
                            finish.clone().add(0, 4, 0),
                            finish.clone().add(0, 0, 20),
                            new Location(world("other"), 0, 80, 0));
            for (int i = 0; i < locations.size(); i++) {
                var check = new RiptideFallCheck();
                int step = i == 0 ? 80 : 100;
                var location = locations.get(i);
                check.sample(GEOMETRY, location, step, .65, 6, false, false, 0);
                check.sample(
                        GEOMETRY, finish.clone().add(20, -3, 20), step, .65, 6, true, false, 1);
                assertTrue(
                        check.sample(
                                GEOMETRY,
                                finish.clone().add(20, -3, 20),
                                step,
                                .65,
                                6,
                                true,
                                false,
                                21),
                        "case " + i);
            }
            var swimming = new RiptideFallCheck();
            swimming.sample(GEOMETRY, finish, 100, .65, 6, true, false, 0);
            assertTrue(swimming.sample(GEOMETRY, finish, 100, .65, 6, true, false, 20));
        }

        @Test
        void physicalSupportIncludesRearAndSideOverhangButExcludesAirAndOldTrail() {
            World deckWorld =
                    (World)
                            Proxy.newProxyInstance(
                                    World.class.getClassLoader(),
                                    new Class<?>[] {World.class},
                                    (proxy, method, args) ->
                                            switch (method.getName()) {
                                                case "getName" -> "deck";
                                                case "getBlockAt" ->
                                                        Proxy.newProxyInstance(
                                                                org.bukkit.block.Block.class
                                                                        .getClassLoader(),
                                                                new Class<?>[] {
                                                                    org.bukkit.block.Block.class
                                                                },
                                                                (block, call, values) ->
                                                                        switch (call.getName()) {
                                                                            case "isEmpty" ->
                                                                                    !((int) args[0]
                                                                                                    == 3
                                                                                            && (int)
                                                                                                            args[
                                                                                                                    2]
                                                                                                    == 6);
                                                                            case "isLiquid" ->
                                                                                    false;
                                                                            default ->
                                                                                    throw new UnsupportedOperationException(
                                                                                            call
                                                                                                    .getName());
                                                                        });
                                                default ->
                                                        throw new UnsupportedOperationException(
                                                                method.getName());
                                            });
            var g =
                    RiptideCourseGeometry.resolve(
                            new Location(deckWorld, .5, 80, .5),
                            new Location(deckWorld, .5, 80, 100.5),
                            7,
                            9);
            var edge = new Location(deckWorld, 4.2, 80, 5.8);
            assertTrue(RiptideFallCheck.deckPresent(g, edge, 10));
            assertFalse(RiptideFallCheck.deckPresent(g, edge.clone().add(.11, 0, 0), 10));
            assertFalse(RiptideFallCheck.deckPresent(g, g.centerAt(10), 10));
            assertFalse(
                    RiptideFallCheck.deckPresent(g, edge, 11),
                    "old deck cell is now behind the raft");
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

    @Nested
    class RiptideFloorProtectionCases {
        @Test
        void consumedShieldCoversAllRemainingRoundsOnlyForThatPlayerAndStage() {
            var protection = new RiptideFloorProtection();
            var player = UUID.randomUUID();
            var other = UUID.randomUUID();
            assertFalse(protection.protects(player));
            protection.grant(player);
            for (int round = 2; round <= 7; round++) {
                assertTrue(protection.protects(player));
                assertFalse(protection.protects(other));
            }
            protection.clear();
            assertFalse(protection.protects(player));
        }
    }
}
