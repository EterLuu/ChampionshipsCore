package ink.ziip.championshipscore.protocol;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BingoVariantRulesTest {
    @Test
    void difficultyDurationsMatchMineBingo() {
        assertEquals(900, BingoDifficulty.EASY.durationSeconds());
        assertEquals(1800, BingoDifficulty.LITE.durationSeconds());
        assertEquals(2700, BingoDifficulty.NORMAL.durationSeconds());
        assertEquals(3600, BingoDifficulty.HARD.durationSeconds());
        assertEquals(5400, BingoDifficulty.EXTREME.durationSeconds());
    }

    @Test
    void cardChangingRemixAddsTenMinutesOnce() {
        BingoVariantRules rules = new BingoVariantRules(
                BingoMode.DOMINATION, BingoDifficulty.NORMAL, 1, BingoRemix.SCALE);
        assertEquals(3300, rules.durationSeconds(600));
    }
}
