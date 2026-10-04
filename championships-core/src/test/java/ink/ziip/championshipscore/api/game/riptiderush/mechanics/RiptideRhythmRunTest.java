package ink.ziip.championshipscore.api.game.riptiderush.mechanics;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.api.game.riptiderush.course.RiptideCourseGeometry;
import ink.ziip.championshipscore.api.game.riptiderush.course.RiptideCoursePlan;
import ink.ziip.championshipscore.api.game.riptiderush.course.RiptideCoursePlanner;
import ink.ziip.championshipscore.api.game.riptiderush.course.RiptideLevelTemplate;
import ink.ziip.championshipscore.api.game.riptiderush.course.RiptideLevelType;
import ink.ziip.championshipscore.api.game.riptiderush.support.RiptideTestFixtures;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.*;

class RiptideRhythmRunTest {
    @Nested
    class RiptideRhythmRunCases {
        @Test
        void fixedGateAnimatesWhileRaftAdvancesAndRestoresOnExitOrCancel() {
            for (String variant :
                    RiptideLevelTemplate.variants(RiptideLevelType.RHYTHM).stream()
                            .filter(v -> !v.equals("AUTO"))
                            .toList()) {
                var f = new Fixture(variant);
                f.run.tick(10, 5.2, List.of(f.player));
                assertTrue(f.blocks.isEmpty());
                f.run.tick(15, 5.2, List.of(f.player));
                assertEquals(0, f.snapshots);
                f.run.tick(16, 5.2, List.of(f.player));
                assertEquals(28, f.snapshots);
                var originalFrame = Map.copyOf(f.blocks);
                boolean changed = false;
                for (int tick = 0; tick < 42; tick++) {
                    f.run.tick(15 + tick / 5, 5.2, List.of(f.player));
                    changed |= !originalFrame.equals(f.blocks);
                }
                assertTrue(changed);
                assertTrue(f.restored.isEmpty());
                f.run.tick(36, 5.2, List.of(f.player));
                assertEquals(28, f.restored.size());
                assertTrue(f.blocks.values().stream().allMatch(m -> m == Material.AIR));
                f.run.tick(37, 5.2, List.of(f.player));
                assertEquals(28, f.snapshots);
                f.run.close();
                var cancelled = new Fixture(variant);
                var decoration =
                        List.of(
                                cancelled.g.blockX(30, 0),
                                cancelled.g.floorY() + 4,
                                cancelled.g.blockZ(30, 0));
                cancelled.blocks.put(decoration, Material.OAK_PLANKS);
                cancelled.run.tick(20, 5.2, List.of(cancelled.player));
                cancelled.run.close();
                assertEquals(28, cancelled.restored.size());
                assertEquals(Material.OAK_PLANKS, cancelled.blocks.get(decoration));
            }
        }

        @Test
        void raisedWindowAllowsJumpingPlayersAndPushesStandingPlayersOnEveryAxis() {
            for (int[] axis : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                var f = new Fixture("VERTICAL_WINDOW", axis);
                for (int tick = 0; tick < 32; tick++) f.run.tick(30, 5.2, List.of(f.player));
                f.position = f.g.centerAt(30).add(0, 1, 0);
                f.run.tick(30, 5.2, List.of(f.player));
                assertEquals(0, f.teleports, "A jumping body fits the raised two-block aperture");
                assertEquals(
                        2,
                        f.sounds,
                        "Height change sounds even though the open columns did not change");
                for (int y = 1; y <= 4; y++) {
                    Material expected =
                            y == 1
                                    ? Material.IRON_BLOCK
                                    : y == 4 ? Material.LIME_CONCRETE : Material.AIR;
                    assertEquals(
                            expected,
                            f.blocks.getOrDefault(
                                    List.of(f.g.blockX(30, 0), f.g.floorY() + y, f.g.blockZ(30, 0)),
                                    Material.AIR));
                }
                f.position = f.g.centerAt(30);
                f.run.tick(30, 5.2, List.of(f.player));
                assertEquals(1, f.teleports);
                assertEquals(-.81, f.g.forwardOffset(f.position, 30), 1e-8);
                f.run.close();
                assertEquals(28, f.restored.size());
            }
        }

        @Test
        void fixedWindowHasSolidFrameAndClosesAcrossAnOccupiedOpening() {
            var f = new Fixture("WINDOW_SHUTTER");
            f.position = f.g.centerAt(30);
            f.run.tick(30, 5.2, List.of(f.player));
            assertEquals(0, f.teleports);
            assertEquals(
                    Material.IRON_BLOCK,
                    f.blocks.get(List.of(f.g.blockX(30, 0), f.g.floorY() + 3, f.g.blockZ(30, 0))));
            assertEquals(
                    Material.IRON_BLOCK,
                    f.blocks.get(List.of(f.g.blockX(30, 2), f.g.floorY() + 1, f.g.blockZ(30, 2))));
            for (int tick = 1; tick <= 42; tick++) f.run.tick(30, 5.2, List.of(f.player));
            assertEquals(1, f.teleports);
            assertEquals(0, f.blocks.values().stream().filter(m -> m == Material.AIR).count());
            f.run.close();
            assertTrue(f.blocks.values().stream().allMatch(m -> m == Material.AIR));
        }

        @Test
        void occupiedCellClosesAndPushesPlayerOutsideTheWall() {
            var f = new Fixture("SHUTTER");
            f.position = f.g.centerAt(30);
            for (int tick = 0; tick <= 45; tick++) f.run.tick(30, 5.2, List.of(f.player));
            var occupied = List.of(f.g.blockX(30, 0), f.g.floorY() + 1, f.g.blockZ(30, 0));
            assertEquals(Material.IRON_BLOCK, f.blocks.get(occupied));
            assertEquals(-.81, f.g.forwardOffset(f.position, 30), 1e-8);
            assertEquals(
                    Material.IRON_BLOCK,
                    f.blocks.get(List.of(f.g.blockX(30, 2), f.g.floorY() + 1, f.g.blockZ(30, 2))));
            f.position = f.g.centerAt(32);
            f.run.tick(30, 5.2, List.of(f.player));
            assertEquals(Material.IRON_BLOCK, f.blocks.get(occupied));
            f.run.close();
        }

        @Test
        void closurePushesToTheNearestFaceOnEveryAxisWithoutMovingClearPlayers() {
            for (int[] axis : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                for (double offset : new double[] {-.4, 0, .4, 1.2}) {
                    var f = new Fixture("SHUTTER", axis);
                    f.position = f.g.centerAt(30).add(axis[0] * offset, 0, axis[1] * offset);
                    for (int tick = 0; tick <= 45; tick++) f.run.tick(30, 5.2, List.of(f.player));
                    assertEquals(
                            Math.abs(offset) > .8 ? offset : offset > 0 ? .81 : -.81,
                            f.g.forwardOffset(f.position, 30),
                            1e-8);
                    assertEquals(0, f.g.lateralOffset(f.position, 30), 1e-8);
                    assertEquals(80, f.position.getY(), 1e-8);
                    assertEquals(Math.abs(offset) > .8 ? 0 : 1, f.teleports);
                    f.run.close();
                }
            }
        }

        private static final class Fixture {
            final Map<List<Integer>, Material> blocks = new HashMap<>();
            final Set<List<Integer>> restored = new HashSet<>();
            int snapshots;
            int teleports;
            int sounds;
            Location position;
            final World world =
                    proxy(
                            World.class,
                            (p, m, a) ->
                                    switch (m) {
                                        case "getName" -> "rhythm";
                                        case "equals" -> p == a[0];
                                        case "hashCode" -> 1;
                                        case "getBlockAt" -> {
                                            var key =
                                                    List.of(
                                                            (Integer) a[0],
                                                            (Integer) a[1],
                                                            (Integer) a[2]);
                                            yield proxy(
                                                    Block.class,
                                                    (b, n, v) ->
                                                            switch (n) {
                                                                case "getX" -> key.get(0);
                                                                case "getY" -> key.get(1);
                                                                case "getZ" -> key.get(2);
                                                                case "getType" ->
                                                                        blocks.getOrDefault(
                                                                                key, Material.AIR);
                                                                case "setType" -> {
                                                                    blocks.put(
                                                                            key, (Material) v[0]);
                                                                    yield null;
                                                                }
                                                                case "getState" -> {
                                                                    snapshots++;
                                                                    var original =
                                                                            blocks.getOrDefault(
                                                                                    key,
                                                                                    Material.AIR);
                                                                    yield proxy(
                                                                            BlockState.class,
                                                                            (s, k, x) -> {
                                                                                if (k.equals(
                                                                                        "update")) {
                                                                                    blocks.put(
                                                                                            key,
                                                                                            original);
                                                                                    restored.add(
                                                                                            key);
                                                                                    return true;
                                                                                }
                                                                                return null;
                                                                            });
                                                                }
                                                                default -> null;
                                                            });
                                        }
                                        default -> null;
                                    });
            final RiptideCourseGeometry g;
            final Player player =
                    proxy(
                            Player.class,
                            (p, m, a) ->
                                    switch (m) {
                                        case "getLocation" -> position.clone();
                                        case "teleport" -> {
                                            teleports++;
                                            position = ((Location) a[0]).clone();
                                            yield true;
                                        }
                                        case "playSound" -> {
                                            sounds++;
                                            yield null;
                                        }
                                        case "getBoundingBox" ->
                                                new BoundingBox(
                                                        position.getX() - .3,
                                                        position.getY(),
                                                        position.getZ() - .3,
                                                        position.getX() + .3,
                                                        position.getY() + 1.8,
                                                        position.getZ() + .3);
                                        default -> null;
                                    });
            final RiptideRhythmRun run;

            Fixture(String variant) {
                this(variant, new int[] {0, 1});
            }

            Fixture(String variant, int[] axis) {
                g =
                        RiptideCourseGeometry.resolve(
                                new Location(world, .5, 80, .5),
                                new Location(world, .5 + axis[0] * 500, 80, .5 + axis[1] * 500),
                                7,
                                9);
                position = g.centerAt(10);
                var t = RiptideLevelTemplate.create("gate", RiptideLevelType.RHYTHM);
                run =
                        new RiptideRhythmRun(
                                g,
                                List.of(
                                        new RiptideCoursePlan.Level(
                                                1, 30, t, variant, 0, false, 1, 0, 0, "", 0)));
            }
        }

        @FunctionalInterface
        interface Call {
            Object invoke(Object proxy, String method, Object[] args);
        }

        private static <T> T proxy(Class<T> type, Call call) {
            return type.cast(
                    Proxy.newProxyInstance(
                            type.getClassLoader(),
                            new Class[] {type},
                            (p, m, a) -> call.invoke(p, m.getName(), a)));
        }
    }

    @Nested
    class RiptideRhythmGateCases {
        @Test
        void allPatternsRepeatAndOfferFullBodyPassageAtEverySpeed() {
            for (double speed : List.of(2.2, 2.95, 3.7, 4.45, 5.2, 5.5)) {
                int cycle = RiptideRhythmGate.cycleTicks(speed);
                for (String variant :
                        RiptideLevelTemplate.variants(RiptideLevelType.RHYTHM).stream()
                                .filter(v -> !v.equals("AUTO") && !RiptideRhythmGate.window(v))
                                .toList()) {
                    var masks = new HashSet<Integer>();
                    int previous = -1, duration = 0;
                    for (int tick = 0; tick < cycle * 2; tick++) {
                        int mask = 0;
                        for (int lateral = -3; lateral <= 3; lateral++) {
                            boolean open =
                                    RiptideRhythmGate.opening(
                                            variant, false, tick, speed, lateral, 1);
                            assertEquals(
                                    open,
                                    RiptideRhythmGate.opening(
                                            variant, false, tick + cycle, speed, lateral, 1));
                            assertEquals(
                                    open,
                                    RiptideRhythmGate.opening(
                                            variant, true, tick, speed, -lateral, 1));
                            if (open) mask |= 1 << (lateral + 3);
                        }
                        if (mask != previous) {
                            if (previous > 0)
                                assertTrue(
                                        duration >= 16,
                                        "At least 0.8s to clear the shutter: " + variant);
                            previous = mask;
                            duration = 0;
                        }
                        duration++;
                        masks.add(mask);
                    }
                    assertTrue(masks.size() >= 2);
                    if (variant.equals("CENTER_SIDES"))
                        assertEquals(Set.of(0b0011100, 0b1100011), masks);
                    if (variant.equals("ALTERNATING"))
                        assertEquals(Set.of(0b0000111, 0b1110000), masks);
                    if (variant.equals("SWEEP"))
                        assertEquals(Set.of(0b0000111, 0b0011100, 0b1110000), masks);
                    if (variant.equals("IN_OUT"))
                        assertEquals(Set.of(0b1111111, 0b0111110, 0b0011100), masks);
                    if (variant.equals("CROSS_BEAT"))
                        assertEquals(Set.of(0, 0b0000111, 0b1111111, 0b1110000), masks);
                }
            }
        }

        @Test
        void windowsRepeatMirrorAndKeepTwoBlockHeadroomAndTimeToPassAtEverySpeed() {
            for (String variant :
                    List.of(
                            "HORIZONTAL_WINDOW",
                            "VERTICAL_WINDOW",
                            "WINDOW_SHUTTER",
                            "STAGGERED_WINDOWS")) {
                for (double speed : List.of(2.2, 2.95, 3.7, 4.45, 5.2, 5.5)) {
                    int cycle = RiptideRhythmGate.cycleTicks(speed);
                    int previous = -1, duration = 0;
                    var frames = new HashSet<Integer>();
                    for (int tick = 0; tick < cycle * 2; tick++) {
                        int mask = 0;
                        for (int lateral = -3; lateral <= 3; lateral++) {
                            int column = 0;
                            for (int y = 0; y <= 4; y++) {
                                boolean open =
                                        RiptideRhythmGate.opening(
                                                variant, false, tick, speed, lateral, y);
                                assertEquals(
                                        open,
                                        RiptideRhythmGate.opening(
                                                variant, true, tick, speed, -lateral, y));
                                assertEquals(
                                        open,
                                        RiptideRhythmGate.opening(
                                                variant, false, tick + cycle, speed, lateral, y));
                                if (y == 0 || y == 4) assertFalse(open);
                                else if (open) column |= 1 << (y - 1);
                            }
                            assertTrue(
                                    column == 0 || column == 3 || column == 6,
                                    "Only walk-through or one-block jump windows");
                            mask |= column << ((lateral + 3) * 3);
                        }
                        if (mask != previous) {
                            if (previous > 0) assertTrue(duration >= 16, variant);
                            previous = mask;
                            duration = 0;
                        }
                        if (mask != 0)
                            assertEquals(
                                    6, Integer.bitCount(mask), "Three columns, two blocks high");
                        duration++;
                        frames.add(mask);
                    }
                    assertEquals(variant.equals("HORIZONTAL_WINDOW") ? 3 : 2, frames.size());
                    assertEquals(variant.equals("WINDOW_SHUTTER"), frames.contains(0));
                }
            }
        }

        @Test
        void windowPositionsFollowHorizontalVerticalAndAlternatingPatterns() {
            double speed = 5.2; // 64-tick cycle
            for (int beat = 0; beat < 4; beat++) {
                int center = new int[] {-2, 0, 2, 0}[beat];
                for (int lateral = -3; lateral <= 3; lateral++)
                    for (int y = 1; y <= 3; y++)
                        assertEquals(
                                Math.abs(lateral - center) <= 1 && y <= 2,
                                RiptideRhythmGate.opening(
                                        "HORIZONTAL_WINDOW", false, beat * 16, speed, lateral, y));
            }
            for (int tick : List.of(0, 31, 32, 63))
                for (int lateral = -3; lateral <= 3; lateral++)
                    for (int y = 1; y <= 3; y++) {
                        assertEquals(
                                Math.abs(lateral) <= 1 && (tick < 32 ? y <= 2 : y >= 2),
                                RiptideRhythmGate.opening(
                                        "VERTICAL_WINDOW", false, tick, speed, lateral, y));
                        assertEquals(
                                tick < 32 ? lateral < 0 && y <= 2 : lateral > 0 && y >= 2,
                                RiptideRhythmGate.opening(
                                        "STAGGERED_WINDOWS", false, tick, speed, lateral, y));
                    }
        }

        @Test
        void rhythmAddsNoStoppingTimeAndNewQuotasRemainPlannable() throws Exception {
            var c = RiptideTestFixtures.config();
            c.setPassCount(15);
            c.setMathCount(5);
            c.setStoppedCount(5);
            c.setRhythmCount(5);
            var seen = new HashSet<String>();
            for (int seed = 0; seed < 8; seed++) {
                var plan = RiptideCoursePlanner.plan(c, seed);
                plan.levels().stream()
                        .filter(l -> l.type() == RiptideLevelType.RHYTHM)
                        .forEach(l -> seen.add(l.variant()));
                assertEquals(
                        5,
                        plan.levels().stream()
                                .filter(l -> l.type() == RiptideLevelType.RHYTHM)
                                .map(RiptideCoursePlan.Level::number)
                                .distinct()
                                .count());
                assertTrue(plan.estimatedTicks() < c.getTimer() * 20);
                var without =
                        plan.levels().stream()
                                .filter(l -> l.type() != RiptideLevelType.RHYTHM)
                                .toList();
                assertEquals(
                        plan.estimatedTicks(),
                        RiptideCoursePlanner.estimateTicks(c, c.resolveGeometry(), without));
            }
            assertTrue(
                    seen.stream()
                            .anyMatch(
                                    v ->
                                            Set.of(
                                                            "HORIZONTAL_WINDOW",
                                                            "VERTICAL_WINDOW",
                                                            "WINDOW_SHUTTER",
                                                            "STAGGERED_WINDOWS")
                                                    .contains(v)));
        }
    }
}
