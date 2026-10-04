package ink.ziip.championshipscore.api.game.riptiderush.mechanics;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.api.game.riptiderush.course.RiptideCourseGeometry;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;

class RiptideMathRunTest {
    private static final World WORLD = world("raft");
    private static final RiptideCourseGeometry SOUTH =
            RiptideCourseGeometry.resolve(
                    new Location(WORLD, .5, 80, -110.5), new Location(WORLD, .5, 80, 389.5), 7, 9);
    private static final RiptideMathRun.Gate FIRST =
            new RiptideMathRun.Gate(2, 30, new RiptideMathQuestion(20, 22, 42, 52, true));
    private static final RiptideMathRun.Gate SECOND =
            new RiptideMathRun.Gate(5, 76, new RiptideMathQuestion(31, 22, 53, 63, false));

    @Test
    void correctPassThenSwitchingSidesOrReturningThroughGateNeverChangesAnswer() {
        var run = new RiptideMathRun(SOUTH, List.of(FIRST, SECOND), at(29, 2));
        assertEquals(RiptideMathRun.Result.CORRECT, run.sample(at(31, 2)).getFirst().result());
        // Old implementation sampled here (raft centre 34), after the player had changed lanes.
        assertTrue(run.sample(at(34, -2)).isEmpty());
        assertTrue(run.sample(at(29, -2)).isEmpty());
        assertTrue(run.sample(at(31, -2)).isEmpty());
        var next = run.sample(at(77, -2));
        assertEquals(1, next.size());
        assertEquals(SECOND, next.getFirst().gate());
        assertEquals(RiptideMathRun.Result.CORRECT, next.getFirst().result());
    }

    @Test
    void frontAndRearPlayersAnswerIndependentlyOnlyWhenTheyCross() {
        var front = new RiptideMathRun(SOUTH, List.of(FIRST), at(29, 2));
        var rear = new RiptideMathRun(SOUTH, List.of(FIRST), at(25, -2));
        assertEquals(RiptideMathRun.Result.CORRECT, front.sample(at(31, 2)).getFirst().result());
        assertTrue(rear.sample(at(27, -2)).isEmpty());
        assertTrue(rear.sample(at(29, 2)).isEmpty());
        assertEquals(RiptideMathRun.Result.CORRECT, rear.sample(at(31, 2)).getFirst().result());
    }

    @Test
    void trajectoryUsesLaneAtGateInsteadOfDestinationAfterSwitchingSides() {
        var run = new RiptideMathRun(SOUTH, List.of(FIRST), at(29, 2));
        // At the gate lateral = +1; by the destination the player is already in the right lane.
        var answer = run.sample(at(34, -3)).getFirst();
        assertEquals(1D, answer.lateral(), 1e-9);
        assertEquals(RiptideMathRun.Result.CORRECT, answer.result());
    }

    @Test
    void wrongGateIsCommittedOnceAndCannotBeCorrectedAfterCrossing() {
        var run = new RiptideMathRun(SOUTH, List.of(FIRST), at(29, -2));
        assertEquals(RiptideMathRun.Result.WRONG, run.sample(at(31, -2)).getFirst().result());
        assertTrue(run.sample(at(34, 2)).isEmpty());
    }

    @Test
    void centrePillarOuterPostsAndOverUnderGateAreNotAnswers() {
        for (double lateral : new double[] {0, .5, -.5, 3.5, -3.5, 5}) {
            var run = new RiptideMathRun(SOUTH, List.of(FIRST), at(29, lateral));
            assertEquals(
                    RiptideMathRun.Result.OUTSIDE_GATE,
                    run.sample(at(31, lateral)).getFirst().result());
        }
        for (double y : new double[] {78, 84}) {
            var run = new RiptideMathRun(SOUTH, List.of(FIRST), at(29, 2).add(0, y - 80, 0));
            assertEquals(
                    RiptideMathRun.Result.OUTSIDE_GATE,
                    run.sample(at(31, 2).add(0, y - 80, 0)).getFirst().result());
        }
        var jump = new RiptideMathRun(SOUTH, List.of(FIRST), at(29, 2).add(0, 1.2, 0));
        assertEquals(
                RiptideMathRun.Result.CORRECT,
                jump.sample(at(31, 2).add(0, 1.2, 0)).getFirst().result());
    }

    @Test
    void shieldRecoveryDoesNotDisableJudgingOrCountTeleportAsAnAnswer() {
        var run = new RiptideMathRun(SOUTH, List.of(FIRST, SECOND), at(29, -2));
        assertEquals(RiptideMathRun.Result.WRONG, run.sample(at(31, -2)).getFirst().result());
        run.recoverAt(at(28, 0));
        assertTrue(run.sample(at(31, 0)).isEmpty());
        assertEquals(SECOND, run.preview(at(70, 2), 2, 18, 100, false));
        run.sample(at(75, 2));
        assertEquals(RiptideMathRun.Result.WRONG, run.sample(at(77, 2)).getFirst().result());
    }

    @Test
    void allCardinalDirectionsMatchRedLeftAndBlueRightRegardlessOfCameraYaw() {
        // Explicit world directions: south's left is east; north's left is west;
        // east's left is north; west's left is south.
        int[][] directions = {{0, 1, 1, 0}, {0, -1, -1, 0}, {1, 0, 0, -1}, {-1, 0, 0, 1}};
        for (int[] d : directions) {
            var geometry =
                    RiptideCourseGeometry.resolve(
                            new Location(WORLD, .5, 80, .5),
                            new Location(WORLD, .5 + d[0] * 100, 80, .5 + d[1] * 100),
                            7,
                            9);
            for (boolean correctLeft : new boolean[] {true, false}) {
                int side = correctLeft ? 1 : -1;
                var gate =
                        new RiptideMathRun.Gate(
                                2, 30, new RiptideMathQuestion(20, 22, 42, 52, correctLeft));
                Location from =
                        new Location(
                                WORLD,
                                .5 + d[0] * 29 + d[2] * 2 * side,
                                80,
                                .5 + d[1] * 29 + d[3] * 2 * side,
                                137,
                                0);
                Location to = from.clone().add(d[0] * 2, 0, d[1] * 2);
                to.setYaw(-45);
                var run = new RiptideMathRun(geometry, List.of(gate), from);
                assertEquals(
                        42,
                        correctLeft ? gate.question().leftAnswer() : gate.question().rightAnswer());
                assertEquals(RiptideMathRun.Result.CORRECT, run.sample(to).getFirst().result());
            }
        }
    }

    @Test
    void previewIsLimitedToFourSecondsAtAllSpeedsAndCannotRestartByBackingUp() {
        for (double speed : new double[] {2, 2.8, 4}) {
            var run = new RiptideMathRun(SOUTH, List.of(FIRST), at(0, 2));
            double firstStep = 30 - speed * 4;
            assertNull(run.preview(at(firstStep - .01, 2), speed, 18, 0, false));
            assertEquals(FIRST, run.preview(at(firstStep, 2), speed, 18, 10, false));
            assertEquals(FIRST, run.preview(at(29, 2), speed, 18, 89, false));
            assertNull(run.preview(at(29, 2), speed, 18, 90, false));
            assertNull(run.preview(at(0, 2), speed, 18, 100, false));
            assertNull(run.preview(at(29, 2), speed, 18, 101, false));
        }
    }

    @Test
    void previewYieldsToPauseAndClearsImmediatelyOnCorrectCrossing() {
        var run = new RiptideMathRun(SOUTH, List.of(FIRST, SECOND), at(22, 2));
        assertNull(run.preview(at(22, 2), 2, 18, 0, true));
        assertEquals(FIRST, run.preview(at(22, 2), 2, 18, 100, false));
        run.sample(at(31, 2));
        assertNull(run.preview(at(31, 2), 2, 18, 101, false));
        assertEquals(SECOND, run.preview(at(68, -2), 2, 18, 200, false));
    }

    @Test
    void noMotionAndBackwardCrossingDoNotChooseAnAnswer() {
        var run = new RiptideMathRun(SOUTH, List.of(FIRST), at(31, 2));
        assertTrue(run.sample(at(31, 2)).isEmpty());
        assertTrue(run.sample(at(29, 2)).isEmpty());
        assertEquals(RiptideMathRun.Result.CORRECT, run.sample(at(30, 2)).getFirst().result());
        assertTrue(run.sample(at(31, 2)).isEmpty());
    }

    @Test
    void sideSweepLongerThanPreviewDoesNotConsumeNextQuestion() {
        var run = new RiptideMathRun(SOUTH, List.of(FIRST), at(24, 2));
        assertEquals(FIRST, run.preview(at(24, 2), 2, 18, 10, false));
        for (int tick = 11; tick <= 155; tick++) run.suspendPreview();
        assertEquals(FIRST, run.preview(at(24, 2), 2, 18, 156, false));
        assertEquals(FIRST, run.preview(at(29, 2), 2, 18, 215, false));
        assertEquals(RiptideMathRun.Result.CORRECT, run.sample(at(31, 2)).getFirst().result());
        assertNull(run.preview(at(31, 2), 2, 18, 216, false));
    }

    private static Location at(double step, double lateral) {
        return new Location(WORLD, .5 + lateral, 80, -110.5 + step);
    }

    @Test
    void multipleAcceptedSegmentsWithinOneTickKeepTheActualDoorChoice() {
        var run = new RiptideMathRun(SOUTH, List.of(FIRST, SECOND), at(29, 2));
        assertEquals(RiptideMathRun.Result.CORRECT, run.sample(at(30.2, 2)).getFirst().result());
        assertTrue(run.sample(at(31, -2)).isEmpty());
        assertTrue(run.sample(at(31, -2)).isEmpty()); // tick fallback sees no additional answer
    }

    @Test
    void spectatorAdvancesQuestionsWithoutSubmittingAnAnswer() {
        var run = new RiptideMathRun(SOUTH, List.of(FIRST, SECOND), at(0, 0));
        run.followCourse(at(22, 0));
        assertEquals(FIRST, run.preview(at(22, 0), 2, 18, 100, false));
        run.followCourse(at(34, 0));
        assertNull(run.preview(at(34, 0), 2, 18, 200, false));
        run.followCourse(at(68, 0));
        assertEquals(SECOND, run.preview(at(68, 0), 2, 18, 300, false));
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
