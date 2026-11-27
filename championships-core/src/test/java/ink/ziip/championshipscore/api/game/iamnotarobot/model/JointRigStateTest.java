package ink.ziip.championshipscore.api.game.iamnotarobot.model;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.api.game.iamnotarobot.model.JointRigState.Phase;

import org.junit.jupiter.api.Test;

class JointRigStateTest {
    @Test
    void onlyReadyAllowsEditingAndPressingAndFailedAttemptsRetainRoundStart() {
        var state = new JointRigState();
        assertFalse(state.canEdit());
        assertFalse(state.press());
        assertTrue(state.open());
        long carving = state.epoch();
        assertFalse(state.open());
        assertFalse(state.canEdit());
        assertTrue(state.ready(carving, 20));
        assertTrue(state.canEdit());
        long digEpoch = state.epoch();
        assertTrue(state.press());
        assertFalse(state.canEdit());
        assertFalse(state.press());
        assertFalse(state.finish(carving, JointFit.Outcome.PASS, 80));
        assertTrue(state.finish(state.epoch(), JointFit.Outcome.BLOCKED, 80));
        assertEquals(1, state.round());
        assertEquals(70, state.duration(90));
        assertTrue(state.canPress());
        assertNotEquals(digEpoch, state.epoch());
        state.press();
        assertTrue(state.finish(state.epoch(), JointFit.Outcome.GAP, 80));
        assertEquals(Phase.READY, state.phase());
    }

    @Test
    void threePassesCompleteAndEveryRoundInvalidatesPriorWork() {
        var state = new JointRigState();
        state.open();
        for (int round = 1; round <= 3; round++) {
            assertEquals(round, state.round());
            long carving = state.epoch();
            assertTrue(state.ready(carving, 10L * round));
            assertTrue(state.press());
            long testing = state.epoch();
            assertTrue(state.current(testing, Phase.TESTING, round));
            assertFalse(state.current(testing, Phase.TESTING, round + 1));
            assertTrue(state.finish(testing, JointFit.Outcome.PASS, 80));
            assertFalse(state.current(testing, Phase.TESTING, round));
            assertFalse(state.ready(carving, 100));
            assertFalse(state.finish(testing, JointFit.Outcome.PASS, 80));
        }
        assertEquals(Phase.COMPLETE, state.phase());
        assertFalse(state.open());
        assertFalse(state.canEdit());
    }

    @Test
    void resetDuringCarvingOrTestingRejectsAllPreviousTokens() {
        for (boolean testing : new boolean[] {false, true}) {
            var state = new JointRigState();
            state.open();
            if (testing) {
                state.ready(state.epoch(), 10);
                state.press();
            }
            long old = state.epoch();
            state.reset();
            assertEquals(Phase.DORMANT, state.phase());
            assertEquals(1, state.round());
            assertFalse(state.ready(old, 20));
            assertFalse(state.finish(old, JointFit.Outcome.PASS, 80));
            state.open();
            assertFalse(state.ready(old, 30));
            assertFalse(state.finish(old, JointFit.Outcome.PASS, 80));
        }
    }

    @Test
    void firstDemoLocksBothActionsAndRestartsTimeEvenIfInterrupted() {
        for (var outcome : JointFit.Outcome.values()) {
            var state = new JointRigState();
            for (int game = 0; game < 2; game++) {
                state.open();
                assertFalse(state.pressDemo());
                state.ready(state.epoch(), 20);
                assertTrue(state.pressDemo());
                assertTrue(state.demo());
                assertFalse(state.canPress());
                assertFalse(state.canEdit());
                assertFalse(state.pressDemo());
                // Suspension and normal rollback both finish the demo with the current clock.
                assertTrue(state.finish(state.epoch(), outcome, 120));
                assertEquals(1, state.round());
                assertEquals(Phase.READY, state.phase());
                assertFalse(state.demo());
                assertFalse(state.pressDemo());
                assertEquals(5, state.duration(125));
                state.reset();
            }
        }
    }
}
