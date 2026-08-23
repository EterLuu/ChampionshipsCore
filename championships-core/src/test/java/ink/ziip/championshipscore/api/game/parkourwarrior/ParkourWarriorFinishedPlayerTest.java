package ink.ziip.championshipscore.api.game.parkourwarrior;

import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParkourWarriorFinishedPlayerTest {
    @Test
    void reconnectingCompletedRunnerStaysSpectatorOnlyWhileMatchIsInProgress() {
        assertTrue(ParkourWarriorTeamArea.shouldRestoreFinishedPlayerAsSpectator(
                GameStageEnum.PROGRESS, true));
        assertFalse(ParkourWarriorTeamArea.shouldRestoreFinishedPlayerAsSpectator(
                GameStageEnum.PROGRESS, false));
        assertFalse(ParkourWarriorTeamArea.shouldRestoreFinishedPlayerAsSpectator(
                GameStageEnum.COUNTDOWN, true));
        assertFalse(ParkourWarriorTeamArea.shouldRestoreFinishedPlayerAsSpectator(
                GameStageEnum.END, true));
    }
}
