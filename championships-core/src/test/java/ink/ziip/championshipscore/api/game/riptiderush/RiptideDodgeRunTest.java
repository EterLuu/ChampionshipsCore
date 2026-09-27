package ink.ziip.championshipscore.api.game.riptiderush;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class RiptideDodgeRunTest {
    @Test
    void eachWaveSurroundsTheArenaWithFourInwardDirections() {
        var run = new RiptideDodgeRun("ZOMBIE", 1234L, 15, 9);
        List<RiptideDodgeRun.Spawn> spawns = run.spawns();
        assertEquals(21, spawns.size());
        for (int wave = 0; wave < 3; wave++) {
            final int waveStart = wave * RiptideDodgeRun.WAVE_TICKS;
            final int waveEnd = (wave + 1) * RiptideDodgeRun.WAVE_TICKS;
            var directions = spawns.stream()
                    .filter(s -> s.tick() >= waveStart && s.tick() < waveEnd)
                    .map(RiptideDodgeRun.Spawn::direction)
                    .collect(Collectors.toSet());
            assertEquals(EnumSet.of(RiptideDodgeRun.Direction.NORTH,
                    RiptideDodgeRun.Direction.SOUTH,
                    RiptideDodgeRun.Direction.EAST,
                    RiptideDodgeRun.Direction.WEST), directions);
        }
        assertTrue(spawns.stream().noneMatch(s -> s.direction() == RiptideDodgeRun.Direction.DOWN
                || s.direction() == RiptideDodgeRun.Direction.NORTHEAST
                || s.direction() == RiptideDodgeRun.Direction.SOUTHWEST));
    }

    @Test
    void lanesUseTheCorrectArenaDimension() {
        var run = new RiptideDodgeRun("SPIDER", 1L, 15, 9);
        var horizontal = run.spawns().stream()
                .filter(s -> s.direction() == RiptideDodgeRun.Direction.EAST
                        || s.direction() == RiptideDodgeRun.Direction.WEST)
                .map(RiptideDodgeRun.Spawn::lateral).toList();
        var vertical = run.spawns().stream()
                .filter(s -> s.direction() == RiptideDodgeRun.Direction.NORTH
                        || s.direction() == RiptideDodgeRun.Direction.SOUTH)
                .map(RiptideDodgeRun.Spawn::lateral).toList();
        assertTrue(horizontal.stream().anyMatch(v -> Math.abs(v) > 2D));
        assertTrue(vertical.stream().anyMatch(v -> Math.abs(v) > 4D));
    }

    @Test
    void wavesAccelerateWhileMobProfilesRemainDistinct() {
        for (var mob : RiptideDodgeRun.Mob.values()) {
            assertTrue(RiptideDodgeRun.speed(mob, 0) < RiptideDodgeRun.speed(mob, 1));
            assertTrue(RiptideDodgeRun.speed(mob, 1) < RiptideDodgeRun.speed(mob, 2));
        }
        assertNotEquals(RiptideDodgeRun.speed(RiptideDodgeRun.Mob.ZOMBIE, 0),
                RiptideDodgeRun.speed(RiptideDodgeRun.Mob.SPIDER, 0));
    }
}
