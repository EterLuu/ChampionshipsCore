package ink.ziip.championshipscore.api.game.riptiderush.mechanics;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.api.game.riptiderush.mechanics.RiptideDodgeSchedule.*;

import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

class RiptideDodgeRunTest {
    @Test
    void collisionSizeAndApproachClearanceArePreserved() {
        assertEquals(8D, RiptideDodgeSchedule.spawnDistance(7));
        assertEquals(9D, RiptideDodgeSchedule.spawnDistance(9));
        assertEquals(8.5D, RiptideDodgeSchedule.DOWNWARD_SPAWN_OFFSET);
        var box = RiptideDodgeRun.collisionBox(new BoundingBox(10, 20, 30, 11.8, 21.4, 32));
        assertEquals(.6D, box.getWidthX(), 1e-9);
        assertEquals(.6D, box.getWidthZ(), 1e-9);
        assertEquals(1.4D, box.getHeight(), 1e-9);
        assertEquals(10.9D, box.getCenterX(), 1e-9);
        assertEquals(31D, box.getCenterZ(), 1e-9);
    }

    @Test
    void spiderCollisionAllowsAPlayerToPassWithASmallHorizontalGap() {
        var nativeBox = new BoundingBox(-.7, 0, -.7, .7, .9, .7);
        var collision = RiptideDodgeRun.collisionBox(nativeBox);
        var clearPlayer = new BoundingBox(.31, 0, -.3, .91, 1.8, .3);
        var touchingPlayer = new BoundingBox(.29, 0, -.3, .89, 1.8, .3);
        assertTrue(nativeBox.overlaps(clearPlayer));
        assertFalse(collision.overlaps(clearPlayer));
        assertTrue(collision.overlaps(touchingPlayer));
    }

    @Test
    void runtimeEmitsExactlyTheScheduleUsedByTheOfflineSimulation() {
        for (Mob mob : Mob.values())
            for (int phase = 0; phase < 5; phase++) {
                var run = new RiptideDodgeRun(mob.name(), 1234L, 7, 9, phase);
                var expected = RiptideDodgeSchedule.generate(mob.name(), 1234L, 7, 9, phase);
                List<Spawn> emitted = new ArrayList<>();
                for (int tick = 0; tick < RiptideDodgeRun.DURATION_TICKS; tick++) {
                    assertFalse(run.complete());
                    var due = run.tick();
                    assertTrue(due.size() <= 1);
                    final int now = tick;
                    assertTrue(due.stream().allMatch(s -> s.tick() == now));
                    if (tick < RiptideDodgeSchedule.INITIAL_DELAY_TICKS) assertTrue(due.isEmpty());
                    emitted.addAll(due);
                }
                assertTrue(run.complete());
                assertEquals(expected, emitted);
                assertTrue(run.tick().isEmpty());
                assertEquals(180, run.tickNumber());
            }
    }

    @Test
    void randomSchedulesRespectTheIntervalAndActiveCapAcrossDeckSizes() {
        for (int[] deck : new int[][] {{3, 3}, {5, 7}, {7, 9}, {9, 7}, {15, 9}}) {
            for (Mob mob : Mob.values())
                for (int phase = 0; phase < 5; phase++)
                    for (int seed = 0; seed < 40; seed++) {
                        Settings settings =
                                RiptideDodgeSchedule.settingsFor(deck[0], deck[1], phase, mob);
                        var spawns =
                                new RiptideDodgeRun(mob.name(), seed, deck[0], deck[1], phase)
                                        .spawns();
                        int previous = -100;
                        for (var spawn : spawns) {
                            assertTrue(spawn.tick() - previous >= settings.minimumInterval());
                            assertTrue(spawn.tick() < 180);
                            previous = spawn.tick();
                        }
                        for (int tick = 0; tick < 180; tick++) {
                            final int now = tick;
                            assertTrue(
                                    spawns.stream().filter(s -> s.activeAt(now)).count()
                                            <= settings.maximumActive());
                        }
                    }
        }
    }

    @Test
    void attacksVaryBySeedAndWithinOneRunRatherThanFormingALaneSweep() {
        var first = new RiptideDodgeRun("SPIDER", 1234, 7, 9).spawns();
        assertEquals(first, new RiptideDodgeRun("SPIDER", 1234, 7, 9).spawns());
        assertNotEquals(first, new RiptideDodgeRun("SPIDER", 1235, 7, 9).spawns());
        assertEquals(1, first.stream().map(Spawn::direction).distinct().count());
        Set<Integer> gaps = new java.util.HashSet<>();
        for (int i = 1; i < first.size(); i++)
            gaps.add(first.get(i).tick() - first.get(i - 1).tick());
        assertTrue(gaps.size() > 1);
        for (Mob mob : Mob.values()) {
            Set<Direction> directions = new java.util.HashSet<>();
            for (int seed = 0; seed < 100; seed++) {
                var run = new RiptideDodgeRun(mob.name(), seed, 7, 9);
                assertTrue(run.spawns().stream().allMatch(s -> s.direction() == run.direction()));
                directions.add(run.direction());
            }
            switch (mob) {
                case ZOMBIE ->
                        assertEquals(EnumSet.of(Direction.NORTH, Direction.SOUTH), directions);
                case HUSK -> assertEquals(EnumSet.of(Direction.EAST, Direction.WEST), directions);
                case SKELETON, SPIDER ->
                        assertEquals(
                                EnumSet.of(
                                        Direction.NORTH,
                                        Direction.SOUTH,
                                        Direction.EAST,
                                        Direction.WEST),
                                directions);
                case CREEPER -> assertEquals(Set.of(Direction.DOWN), directions);
            }
        }
    }

    @Test
    void fallingAttacksCoverBothDeckDimensionsAndExpireBelowTheFloor() {
        var spawns = new RiptideDodgeRun("CREEPER", 1234, 7, 9).spawns();
        assertTrue(spawns.stream().map(Spawn::x).distinct().count() > 1);
        assertTrue(spawns.stream().map(Spawn::z).distinct().count() > 1);
        for (var spawn : spawns) {
            assertTrue(Math.abs(spawn.x()) <= 3);
            assertTrue(Math.abs(spawn.z()) <= 4);
            assertTrue(spawn.positionAt(spawn.lifetimeTicks()).y() <= -3);
        }
    }

    @Test
    void horizontalAttacksCrossTheDeckBeforeExpiryAndSpidersStayAtDeckHeight() {
        for (Mob mob : List.of(Mob.ZOMBIE, Mob.HUSK, Mob.SKELETON, Mob.SPIDER)) {
            for (var spawn : new RiptideDodgeRun(mob.name(), 1234, 7, 9).spawns()) {
                var start = spawn.positionAt(0);
                var end = spawn.positionAt(spawn.lifetimeTicks());
                if (spawn.direction().x != 0) assertTrue(start.x() * end.x() < 0);
                else assertTrue(start.z() * end.z() < 0);
                assertEquals(mob == Mob.SPIDER ? 0 : .2D, start.y());
                assertEquals(start.y(), end.y());
            }
        }
        assertEquals(1, RiptideDodgeSchedule.settingsFor(3, 3).maximumActive());
        assertTrue(
                RiptideDodgeSchedule.settingsFor(5, 7).maximumActive()
                        < RiptideDodgeSchedule.settingsFor(7, 9).maximumActive());
    }

    @Test
    void directionWarningsDescribeTheArrivalSideAndAutoUsesItsChosenMobProfile() {
        assertEquals("前方", Direction.NORTH.arrivalSide());
        assertEquals("后方", Direction.SOUTH.arrivalSide());
        assertEquals("右方", Direction.EAST.arrivalSide());
        assertEquals("左方", Direction.WEST.arrivalSide());
        assertEquals("上方", Direction.DOWN.arrivalSide());
        for (int seed = 0; seed < 100; seed++)
            for (int phase = 0; phase < 5; phase++) {
                var run = new RiptideDodgeRun("AUTO", seed, 7, 9, phase);
                var spawn = run.spawns().getFirst();
                assertEquals(
                        RiptideDodgeSchedule.speed(spawn.mob())
                                * RiptideDodgeSchedule.settingsFor(7, 9, phase, spawn.mob())
                                        .speedMultiplier(),
                        spawn.speed());
            }
    }

    @Test
    void chargeSpeedsIncreaseAtEveryCourseMilestone() {
        for (Mob mob : Mob.values()) {
            double previous = 0;
            for (int phase = 0; phase < 5; phase++) {
                double speed =
                        new RiptideDodgeRun(mob.name(), 1234, 7, 9, phase)
                                .spawns()
                                .getFirst()
                                .speed();
                assertTrue(speed > previous);
                previous = speed;
            }
        }
        assertEquals(
                new RiptideDodgeRun("ZOMBIE", 1, 7, 9, 0).spawns(),
                new RiptideDodgeRun("ZOMBIE", 1, 7, 9, -1).spawns());
        assertEquals(
                new RiptideDodgeRun("ZOMBIE", 1, 7, 9, 4).spawns(),
                new RiptideDodgeRun("ZOMBIE", 1, 7, 9, 9).spawns());
    }
}
