package ink.ziip.championshipscore.api.game.riptiderush.runtime;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.api.game.riptiderush.course.RiptideHitwCatalog;
import ink.ziip.championshipscore.api.game.riptiderush.course.RiptideLevelType;
import ink.ziip.championshipscore.api.game.riptiderush.mechanics.RiptideColorFloorRun;
import ink.ziip.championshipscore.api.game.riptiderush.mechanics.RiptideDodgeRun;
import ink.ziip.championshipscore.api.game.riptiderush.runtime.RiptideIntroductionLesson.Stage;
import ink.ziip.championshipscore.api.game.riptiderush.support.RiptideTestFixtures;

import org.junit.jupiter.api.Test;

import java.util.Random;

class RiptideIntroductionLessonTest {
    @Test
    void completeJourneyMovesOneBlockAtATimeAndLeavesTimeToReadTheFinish() {
        int previous = 0;
        for (int tick = 0; tick < RiptideIntroductionLesson.DURATION_TICKS; tick++) {
            int step = RiptideIntroductionLesson.stepAt(tick);
            assertTrue(step >= previous && step <= previous + 1, "tick " + tick);
            previous = step;
        }
        assertEquals(140, previous);
        assertTrue(Stage.FINISH.start() <= 1720, "at least four seconds for the final explanation");
        assertEquals(4, RiptideIntroductionLesson.departureCountdown(0));
        for (int tick = 0; tick < 80; tick++)
            assertEquals(0, RiptideIntroductionLesson.stepAt(tick));
    }

    @Test
    void stoppedLessonsFinishAllRealMechanicTicksAndWaitBeforeMovingAgain() {
        var colors =
                new RiptideColorFloorRun(
                        7,
                        9,
                        RiptideIntroductionLesson.COLOR_ROUNDS,
                        new Random(1),
                        RiptideColorFloorRun.Theme.TERRACOTTA);
        int elapsed = 0, rounds = 0;
        do {
            elapsed++;
            if (colors.tick()) {
                rounds++;
                if (!colors.advance()) break;
            }
        } while (elapsed < 1800);
        assertEquals(7, rounds);
        assertEquals(
                elapsed + RiptideDeparture.WAIT_TICKS,
                Stage.TRANSFER.start() - Stage.COLOR.start());
        for (int tick = Stage.COLOR.start(); tick < Stage.TRANSFER.start(); tick++)
            assertEquals(64, RiptideIntroductionLesson.stepAt(tick));
        for (int tick = Stage.DODGE.start(); tick < Stage.SPEED.start(); tick++)
            assertEquals(96, RiptideIntroductionLesson.stepAt(tick));
        assertEquals(
                RiptideDodgeRun.DURATION_TICKS + RiptideDeparture.WAIT_TICKS,
                Stage.SPEED.start() - Stage.DODGE.start());
        assertTrue(Stage.DODGE.start() - Stage.TRANSFER.start() > 100, "travel between stops");
    }

    @Test
    void teachingSpeedsMatchAllFiveFormalBands() throws Exception {
        var config = RiptideTestFixtures.config();
        for (int step = 0; step <= 140; step++)
            assertEquals(
                    config.speedAtProgress(step / 140D),
                    RiptideIntroductionLesson.speedAt(step),
                    1e-8);
    }

    @Test
    void wallsComeFromTheRealPoolAndClearBeforeTheMathPreview() {
        var plan = RiptideIntroductionLesson.plan();
        for (var wall :
                plan.levels().stream().filter(l -> l.type() == RiptideLevelType.PASS).toList()) {
            assertTrue(RiptideHitwCatalog.isOriginal(wall.template()));
            assertTrue(
                    RiptideHitwCatalog.passages(wall.template(), false).stream()
                            .anyMatch(p -> p.sill() == 0 && !p.crouch()));
            assertTrue(wall.step() + 4 < RiptideIntroductionLesson.stepAt(Stage.MATH.start()));
        }
        var math = plan.levels().get(2);
        assertEquals(RiptideLevelType.MATH, math.type());
        assertTrue(math.step() - plan.levels().get(1).step() > 4 * 2.95 + 4);
        assertTrue(math.step() + 4 < RiptideIntroductionLesson.COLOR_STEP);
        assertEquals(RiptideIntroductionLesson.COLOR_STEP, plan.levels().get(3).step() - 5);
        assertEquals(RiptideIntroductionLesson.DODGE_STEP, plan.levels().get(4).step() - 5);
        assertTrue(
                plan.levels().get(5).step() - 4
                        > RiptideIntroductionLesson.stepAt(Stage.RHYTHM.start()));
    }
}
