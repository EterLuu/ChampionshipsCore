package ink.ziip.championshipscore.api.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ink.ziip.championshipscore.protocol.BingoVariantRules;

import org.junit.jupiter.api.Test;

class TestSessionPlanTest {
    @Test
    void planWithoutVariantFreezesFixedPoints() {
        assertEquals(
                BingoVariantRules.FIXED_POINTS,
                new TestSessionPlan("bingo", "bingo", "idle").variant());
    }
}
