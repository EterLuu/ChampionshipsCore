package ink.ziip.championshipscore.api.game.start;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.api.game.model.GameRunMode;

import org.junit.jupiter.api.Test;

class GameExecutionContextTest {
    @Test
    void testEventKeepsEventGameplayShapeButNeverFormalSettlement() {
        GameExecutionContext context = GameExecutionContext.forRunMode(GameRunMode.TEST_EVENT);

        assertEquals(GameRunMode.TEST_EVENT, context.runMode());
        assertEquals(GameFlow.TEST_EVENT, context.flow());
        assertEquals(SettlementPolicy.TEST, context.settlement());
        assertTrue(context.test());
        assertTrue(context.writesSettlement());
        assertFalse(context.formalEvent());
    }

    @Test
    void standaloneTestUsesTheNormalSettlementWriter() {
        GameExecutionContext context = GameExecutionContext.forRunMode(GameRunMode.TEST);

        assertTrue(context.test());
        assertTrue(context.writesSettlement());
        assertFalse(context.formalEvent());
    }

    @Test
    void existingModesKeepTheirOriginalContext() {
        GameExecutionContext game = GameExecutionContext.forRunMode(GameRunMode.GAME);
        GameExecutionContext event = GameExecutionContext.forRunMode(GameRunMode.EVENT);
        GameExecutionContext daily = GameExecutionContext.forRunMode(GameRunMode.DAILY);

        assertEquals(GameFlow.STANDALONE, game.flow());
        assertEquals(SettlementPolicy.NORMAL, game.settlement());
        assertEquals(GameFlow.FORMAL_EVENT, event.flow());
        assertEquals(SettlementPolicy.FORMAL_RANK, event.settlement());
        assertEquals(GameFlow.STANDALONE, daily.flow());
        assertEquals(SettlementPolicy.DAILY, daily.settlement());
    }
}
