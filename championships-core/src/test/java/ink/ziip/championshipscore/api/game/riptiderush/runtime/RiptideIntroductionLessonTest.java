package ink.ziip.championshipscore.api.game.riptiderush.runtime;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class RiptideIntroductionLessonTest {
    @Test
    void movementReachesTheStoppedDeckBeforeEitherStoppedChallengeBegins() {
        assertEquals(70, RiptideIntroductionLesson.stepAt(840));
        assertEquals(RiptideIntroductionLesson.Stage.COLOR, RiptideIntroductionLesson.stageAt(840));
        for (int tick = 840; tick < 1460; tick++)
            assertEquals(70, RiptideIntroductionLesson.stepAt(tick));
    }

    @Test
    void fullNinetySecondJourneyIsMonotonicAndMovesAtMostOneBlockEachTick() {
        int previous = 0;
        for (int tick = 0; tick < RiptideIntroductionLesson.DURATION_TICKS; tick++) {
            int step = RiptideIntroductionLesson.stepAt(tick);
            assertTrue(step >= previous && step <= previous + 1, "tick " + tick);
            assertTrue(step <= RiptideIntroductionLesson.COURSE_LENGTH);
            previous = step;
        }
        assertEquals(137, previous);
    }

    @Test
    void welcomeAndFinishGivePlayersTimeOnAStationaryDeck() {
        for (int tick = 0; tick < 200; tick++) {
            assertEquals(
                    RiptideIntroductionLesson.Stage.WELCOME,
                    RiptideIntroductionLesson.stageAt(tick));
            assertEquals(0, RiptideIntroductionLesson.stepAt(tick));
        }
        for (int tick = 1720; tick < 1800; tick++) {
            assertEquals(
                    RiptideIntroductionLesson.Stage.FINISH,
                    RiptideIntroductionLesson.stageAt(tick));
            assertEquals(137, RiptideIntroductionLesson.stepAt(tick));
        }
    }

    @Test
    void accelerationUsesTheNormalFinalSpeedAfterTheShieldBreakCue() {
        assertEquals(
                RiptideIntroductionLesson.Stage.DODGE, RiptideIntroductionLesson.stageAt(1459));
        assertEquals(
                RiptideIntroductionLesson.Stage.SPEED, RiptideIntroductionLesson.stageAt(1460));
        assertEquals(
                52,
                RiptideIntroductionLesson.stepAt(1660) - RiptideIntroductionLesson.stepAt(1460));
        assertEquals(
                22, RiptideIntroductionLesson.stepAt(400) - RiptideIntroductionLesson.stepAt(200));
    }
}
