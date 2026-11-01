package ink.ziip.championshipscore.api.game.instance;

import static ink.ziip.championshipscore.support.TestObjects.*;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.api.game.battlebox.runtime.BattleBoxArea;
import ink.ziip.championshipscore.api.game.model.GameRunMode;
import ink.ziip.championshipscore.api.game.model.GameStageEnum;
import ink.ziip.championshipscore.api.game.start.GameExecutionContext;
import ink.ziip.championshipscore.api.game.tntrun.runtime.TNTRunTeamArea;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class TestSettlementIsolationTest {
    @ParameterizedTest
    @EnumSource(GameRunMode.class)
    void normalFinishesCalculateInMemoryAndAbortsSuppressScoring(GameRunMode mode)
            throws Exception {
        var area = allocate(TNTRunTeamArea.class);
        set(area, "gameStageEnum", GameStageEnum.WAITING);
        area.prepareRunMode(mode);
        assertTrue(area.isSettlementAllowed(), mode.name());
        assertEquals(mode == GameRunMode.EVENT, area.isFormalEventRun());

        set(area, "settlementSuppressed", true);
        // Applying owner metadata must never cancel an abort's scoring suppression.
        area.applyExecutionContext(GameExecutionContext.forRunMode(mode));
        assertFalse(area.isSettlementAllowed());
    }

    @ParameterizedTest
    @EnumSource(
            value = GameRunMode.class,
            names = {"TEST", "TEST_EVENT"})
    void testRunsUseNormalSettlementContractWithoutBecomingFormalEvents(GameRunMode mode)
            throws Exception {
        var area = allocate(TNTRunTeamArea.class);
        set(area, "gameStageEnum", GameStageEnum.WAITING);
        area.prepareRunMode(mode);
        assertTrue(area.getExecutionContext().test());
        assertTrue(area.getExecutionContext().writesSettlement());
        assertTrue(area.isSettlementAllowed());
        assertFalse(area.isFormalEventRun());

        var paired = allocate(BattleBoxArea.class);
        set(paired, "gameStageEnum", GameStageEnum.WAITING);
        paired.prepareRunMode(mode);
        assertTrue(paired.isEventRun() == (mode == GameRunMode.TEST_EVENT));
        assertTrue(paired.getExecutionContext().test());
        assertTrue(paired.getExecutionContext().writesSettlement());
        assertTrue(paired.isSettlementAllowed());
        assertFalse(paired.isFormalEventRun());
    }
}
