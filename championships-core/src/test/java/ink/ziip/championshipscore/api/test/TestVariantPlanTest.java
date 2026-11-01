package ink.ziip.championshipscore.api.test;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.protocol.*;

import org.junit.jupiter.api.Test;

import java.util.List;

class TestVariantPlanTest {
    @Test
    void singlePlanFreezesVariantAndLegacyConstructorRemainsFixed() {
        var variant =
                new BingoVariantRules(BingoMode.POINTS, BingoDifficulty.HARD, 1, BingoRemix.NETHER);
        var plan = new TestSessionPlan("bingo", "bingo", "idle", variant);
        assertEquals(variant, plan.variant());
        assertEquals(
                BingoVariantRules.FIXED_POINTS,
                new TestSessionPlan("bingo", "bingo", "idle").variant());
    }

    @Test
    void eventRoundFreezesVariantWithoutChangingOtherRounds() {
        var variant =
                new BingoVariantRules(
                        BingoMode.POINTS, BingoDifficulty.EXTREME, 1, BingoRemix.ETERNAL_NIGHT);
        var rounds =
                List.of(
                        new TestEventPlan.Round("bingo", "bingo", variant),
                        new TestEventPlan.Round("tntrun", "astra"));
        assertEquals(variant, rounds.getFirst().variant());
        assertEquals(BingoVariantRules.FIXED_POINTS, rounds.get(1).variant());
    }
}
