package ink.ziip.championshipscore.api.game.manager;

import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameManagerSpectateStageTest {
    @Test
    void regularSpectatorsOpenAtPreparation() {
        assertFalse(GameManager.isSpectatingStageAllowed(GameStageEnum.WAITING, false));
        assertFalse(GameManager.isSpectatingStageAllowed(GameStageEnum.LOADING, false));
        assertTrue(GameManager.isSpectatingStageAllowed(GameStageEnum.PREPARATION, false));
        assertTrue(GameManager.isSpectatingStageAllowed(GameStageEnum.COUNTDOWN, false));
        assertTrue(GameManager.isSpectatingStageAllowed(GameStageEnum.PROGRESS, false));
        assertFalse(GameManager.isSpectatingStageAllowed(GameStageEnum.STOPPING, false));
        assertFalse(GameManager.isSpectatingStageAllowed(GameStageEnum.END, false));
    }

    @Test
    void administratorsMayEnterAvailablePreGameArenas() {
        assertTrue(GameManager.isSpectatingStageAllowed(GameStageEnum.WAITING, true));
        assertTrue(GameManager.isSpectatingStageAllowed(GameStageEnum.LOADING, true));
        assertTrue(GameManager.isSpectatingStageAllowed(GameStageEnum.PREPARATION, true));
        assertFalse(GameManager.isSpectatingStageAllowed(GameStageEnum.STOPPING, true));
        assertFalse(GameManager.isSpectatingStageAllowed(GameStageEnum.END, true));
    }
}
