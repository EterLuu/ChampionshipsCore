package ink.ziip.championshipscore.api.game.start;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.api.game.model.GameRunMode;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class GameExecutionContextTest {
    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "GAME, STANDALONE, NORMAL, false, false, false",
        "EVENT, FORMAL_EVENT, FORMAL_RANK, false, true, true",
        "DAILY, STANDALONE, DAILY, false, false, false",
        // Standalone tests use the normal settlement writer but never formal rank.
        "TEST, STANDALONE, TEST, true, true, false",
        // Test events keep the event gameplay shape without formal settlement.
        "TEST_EVENT, TEST_EVENT, TEST, true, true, false"
    })
    void runModeSelectsFlowAndSettlement(
            GameRunMode mode,
            GameFlow flow,
            SettlementPolicy settlement,
            boolean test,
            boolean writesSettlement,
            boolean formalEvent) {
        GameExecutionContext context = GameExecutionContext.forRunMode(mode);

        assertAll(
                mode.name(),
                () -> assertEquals(mode, context.runMode()),
                () -> assertEquals(flow, context.flow()),
                () -> assertEquals(settlement, context.settlement()),
                () -> assertEquals(test, context.test()),
                () -> assertEquals(writesSettlement, context.writesSettlement()),
                () -> assertEquals(formalEvent, context.formalEvent()));
    }
}
