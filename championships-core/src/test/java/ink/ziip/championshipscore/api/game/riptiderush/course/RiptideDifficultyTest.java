package ink.ziip.championshipscore.api.game.riptiderush.course;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.api.game.riptiderush.mechanics.RiptideColorFloorRun;
import ink.ziip.championshipscore.api.game.riptiderush.support.RiptideTestFixtures;

import org.junit.jupiter.api.Test;

import java.util.*;

class RiptideDifficultyTest {

    @Test
    void passAndSideBandsSwitchAtExactAccelerationTicks() {
        for (int total : List.of(500, 503))
            for (int step : boundarySteps(total))
                for (int difficulty = 1; difficulty <= 3; difficulty++) {
                    double progress = step / (double) total;
                    boolean pass =
                            progress < .2
                                    ? difficulty == 1
                                    : progress < .4 ? difficulty == 2 : difficulty >= 2;
                    boolean side =
                            progress < .4
                                    ? false
                                    : progress < .6 ? difficulty <= 2 : difficulty >= 2;
                    assertEquals(pass, RiptideDifficulty.allowsPass(difficulty, step, total));
                    assertEquals(side, RiptideDifficulty.allowsSide(difficulty, step, total));
                }
    }

    @Test
    void absentRequiredDifficultyDoesNotFallBackToAnEasyWall() throws Exception {
        var c = RiptideTestFixtures.config();
        c.setTemplates(
                c.resolvePool().stream()
                        .filter(t -> t.type() != RiptideLevelType.PASS || t.difficulty() == 1)
                        .toList());
        assertThrows(IllegalArgumentException.class, () -> RiptideCoursePlanner.plan(c, 1));
        assertTrue(
                RiptideWallGroups.selectSideWalls(
                                c, c.resolveGeometry(), 300, new HashMap<>(), new Random(1))
                        .isEmpty());
    }

    @Test
    void exactSevenRoundTimingAtEveryStageBoundary() {
        var expected =
                List.of(
                        List.of(120, 100, 100, 80, 80, 80, 80),
                        List.of(100, 80, 80, 80, 80, 70, 70),
                        List.of(80, 70, 70, 70, 70, 60, 60),
                        List.of(60, 60, 60, 60, 60, 50, 50),
                        List.of(60, 50, 50, 40, 40, 40, 40));
        for (int total : List.of(500, 503))
            for (int step : boundarySteps(total)) {
                int stage = Math.min(4, (int) ((long) step * 5 / total));
                assertEquals(expected.get(stage), RiptideDifficulty.floorRoundTicks(step, total));
            }
        for (int stage = 0; stage < 5; stage++) {
            var run =
                    new RiptideColorFloorRun(
                            7,
                            9,
                            RiptideDifficulty.floorRoundTicks(stage * 100, 500),
                            new Random(1),
                            RiptideColorFloorRun.Theme.ORE);
            for (int tick = 0; tick < RiptideColorFloorRun.INTRO_TICKS; tick++)
                assertFalse(run.tick());
            for (int round = 0; round < 7; round++) {
                for (int tick = 1; tick < expected.get(stage).get(round); tick++)
                    assertFalse(run.tick());
                assertTrue(run.tick());
                assertFalse(run.tick());
                assertEquals(round < 6, run.advance());
            }
        }
    }

    @Test
    void exactMilestonesControlFloorsAndOperations() {
        int[] times = {640, 560, 480, 400, 320};
        for (int step : boundarySteps(500)) {
            int stage = Math.min(4, step / 100);
            assertEquals(times[stage], RiptideDifficulty.floorTicks(step, 500));
            assertEquals(5 + Math.min(stage, 3), RiptideDifficulty.floorMaterials(step, 500));
            assertEquals(stage >= 1, RiptideDifficulty.allowsMath("SUBTRACT", step, 500));
            assertEquals(stage >= 3, RiptideDifficulty.allowsMath("MULTIPLY", step, 500));
            assertEquals(step >= 106, RiptideDifficulty.allowsMath("DOUBLE", step, 500));
        }
    }

    @Test
    void allThemesHaveExactStagePaletteForEveryRoundIncludingAuthoredFloors() {
        for (var theme : RiptideColorFloorRun.Theme.values())
            for (int stage = 0; stage < 5; stage++) {
                var authored = new ArrayList<org.bukkit.Material>();
                for (int i = 0; i < 63; i++)
                    authored.add(RiptideColorFloorRun.materials(theme).get(i % 2));
                for (var cells : List.of(List.<org.bukkit.Material>of(), authored)) {
                    int count = RiptideDifficulty.floorMaterials(stage * 100, 500);
                    int ticks = RiptideDifficulty.floorTicks(stage * 100, 500);
                    var run =
                            new RiptideColorFloorRun(
                                    9,
                                    7,
                                    RiptideDifficulty.floorRoundTicks(stage * 100, 500),
                                    new Random(123),
                                    theme,
                                    cells,
                                    count);
                    int elapsed = 0;
                    do {
                        assertEquals(count, new HashSet<>(run.floor()).size());
                        assertTrue(run.floor().contains(run.target()));
                        while (true) {
                            elapsed++;
                            if (run.tick()) break;
                        }
                    } while (run.advance());
                    assertEquals(ticks + RiptideColorFloorRun.INTRO_TICKS, elapsed);
                }
            }
    }

    private static List<Integer> boundarySteps(int total) {
        var steps = new TreeSet<Integer>(List.of(0, total));
        for (int band = 0; band < 5; band++) {
            steps.add((int) ((band + .5) * total / 5));
            int boundary = (int) Math.ceil((band + 1) * total / 5D);
            for (int offset : List.of(-1, 0, 1)) {
                int step = boundary + offset;
                if (step <= total) steps.add(step);
            }
        }
        int doubleStart = (int) Math.ceil(total / 5D) + 6;
        steps.addAll(List.of(doubleStart - 1, doubleStart, doubleStart + 1));
        return List.copyOf(steps);
    }
}
