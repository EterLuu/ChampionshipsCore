package ink.ziip.championshipscore.api.game.riptiderush;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RiptideRushParticipationTest {
    @Test
    void formalRoundsRequireTwoActualStartersButPracticeAllowsOne() {
        assertFalse(RiptideRushArea.canStartRound(true, 0));
        assertFalse(RiptideRushArea.canStartRound(true, 1));
        assertTrue(RiptideRushArea.canStartRound(true, 2));
        assertFalse(RiptideRushArea.canStartRound(false, 0));
        assertTrue(RiptideRushArea.canStartRound(false, 1));
    }

    @Test
    void eliminatingOfflineTeammatesDoesNotEndSoloRunBeforeMovementStarts() {
        // The reported roster contained one online player and one disconnected teammate.
        int rosterSize = 2;
        int offlinePlayers = 1;
        assertFalse(RiptideRushArea.shouldEndAfterElimination(rosterSize - offlinePlayers, 1));
    }

    @Test
    void soloRunStillEndsWhenItsOnlyPlayerIsEliminated() {
        assertTrue(RiptideRushArea.shouldEndAfterElimination(1, 0));
        assertTrue(RiptideRushArea.shouldEndAfterElimination(0, 0));
    }

    @Test
    void multiplayerKeepsTheLastSurvivorInTheRound() {
        assertFalse(RiptideRushArea.shouldEndAfterElimination(3, 2));
        assertFalse(RiptideRushArea.shouldEndAfterElimination(3, 1));
        assertFalse(RiptideRushArea.shouldEndAfterElimination(2, 1));
        assertTrue(RiptideRushArea.shouldEndAfterElimination(2, 0));
    }
}
