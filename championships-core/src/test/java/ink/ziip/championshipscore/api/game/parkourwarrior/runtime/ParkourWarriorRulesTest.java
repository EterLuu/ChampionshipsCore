package ink.ziip.championshipscore.api.game.parkourwarrior.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ink.ziip.championshipscore.api.game.model.GameStageEnum;
import ink.ziip.championshipscore.api.game.parkourwarrior.model.PKWFinalCheckPointTypeEnum;

import org.junit.jupiter.api.Test;

class ParkourWarriorRulesTest {
    @Test
    void dailyFinalDifficultyUsesPersonalAbsoluteMultipliers() {
        assertEquals(
                1D,
                ParkourWarriorTeamArea.dailyFinalPointMultiplier(PKWFinalCheckPointTypeEnum.none));
        assertEquals(
                1D,
                ParkourWarriorTeamArea.dailyFinalPointMultiplier(PKWFinalCheckPointTypeEnum.easy));
        assertEquals(
                1.5D,
                ParkourWarriorTeamArea.dailyFinalPointMultiplier(
                        PKWFinalCheckPointTypeEnum.normal));
        assertEquals(
                2.5D,
                ParkourWarriorTeamArea.dailyFinalPointMultiplier(PKWFinalCheckPointTypeEnum.hard));
    }

    @Test
    void dailyTimeoutHalvesOnlyUnfinishedPlayers() {
        assertEquals(0.5D, ParkourWarriorTeamArea.dailyCompletionMultiplier(false, true));
        assertEquals(1D, ParkourWarriorTeamArea.dailyCompletionMultiplier(true, true));
        assertEquals(1D, ParkourWarriorTeamArea.dailyCompletionMultiplier(false, false));
    }

    @Test
    void reconnectingCompletedRunnerStaysSpectatorOnlyWhileMatchIsInProgress() {
        assertTrue(
                ParkourWarriorTeamArea.shouldRestoreFinishedPlayerAsSpectator(
                        GameStageEnum.PROGRESS, true));
        assertFalse(
                ParkourWarriorTeamArea.shouldRestoreFinishedPlayerAsSpectator(
                        GameStageEnum.PROGRESS, false));
        assertFalse(
                ParkourWarriorTeamArea.shouldRestoreFinishedPlayerAsSpectator(
                        GameStageEnum.COUNTDOWN, true));
        assertFalse(
                ParkourWarriorTeamArea.shouldRestoreFinishedPlayerAsSpectator(
                        GameStageEnum.END, true));
    }
}
