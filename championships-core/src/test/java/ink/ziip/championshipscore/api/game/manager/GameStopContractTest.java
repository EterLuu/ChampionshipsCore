package ink.ziip.championshipscore.api.game.manager;

import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameStopContractTest {
    @Test
    void onlyLifecycleActiveStagesCanBeSelected() {
        assertFalse(GameManager.isStoppableStage(GameStageEnum.WAITING));
        assertTrue(GameManager.isStoppableStage(GameStageEnum.LOADING));
        assertTrue(GameManager.isStoppableStage(GameStageEnum.PREPARATION));
        assertTrue(GameManager.isStoppableStage(GameStageEnum.COUNTDOWN));
        assertTrue(GameManager.isStoppableStage(GameStageEnum.PROGRESS));
        assertTrue(GameManager.isStoppableStage(GameStageEnum.STOPPING));
        assertFalse(GameManager.isStoppableStage(GameStageEnum.END));
    }

    @Test
    void onlyPlayedStagesUseNormalSettlement() {
        assertFalse(GameManager.settlesOnAdministrativeStop(GameStageEnum.LOADING));
        assertFalse(GameManager.settlesOnAdministrativeStop(GameStageEnum.PREPARATION));
        assertFalse(GameManager.settlesOnAdministrativeStop(GameStageEnum.COUNTDOWN));
        assertTrue(GameManager.settlesOnAdministrativeStop(GameStageEnum.PROGRESS));
        assertTrue(GameManager.settlesOnAdministrativeStop(GameStageEnum.STOPPING));
    }
}
