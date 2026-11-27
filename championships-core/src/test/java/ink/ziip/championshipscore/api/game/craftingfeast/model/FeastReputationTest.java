package ink.ziip.championshipscore.api.game.craftingfeast.model;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class FeastReputationTest {
    @Test
    void startsAtThreeStarsWithTheFullTip() {
        FeastReputation reputation = new FeastReputation();
        assertAll(
                () -> assertEquals(FeastReputation.START, reputation.value()),
                () -> assertEquals(3, reputation.stars()),
                () -> assertEquals(100, reputation.tipPercent()));
    }

    @Test
    void quickServiceClimbsFasterAndStarsCapTheTipAtFiftyPercentEitherWay() {
        FeastReputation quick = new FeastReputation();
        FeastReputation slow = new FeastReputation();
        quick.served(true);
        slow.served(false);
        assertEquals(
                FeastReputation.START + FeastReputation.SERVED + FeastReputation.QUICK_BONUS,
                quick.value());
        assertEquals(FeastReputation.START + FeastReputation.SERVED, slow.value());
        for (int i = 0; i < 100; i++) quick.served(true);
        assertAll(
                () -> assertEquals(FeastReputation.MAXIMUM, quick.value()),
                () -> assertEquals(5, quick.stars()),
                () -> assertEquals(150, quick.tipPercent()));
        for (int i = 0; i < 100; i++) quick.lost();
        assertAll(
                () -> assertEquals(0, quick.value()),
                () -> assertEquals(1, quick.stars()),
                () -> assertEquals(50, quick.tipPercent()));
    }

    @Test
    void oneLostGuestCostsMoreThanOneQuickServiceEarns() {
        FeastReputation reputation = new FeastReputation();
        reputation.served(true);
        reputation.lost();
        assertTrue(reputation.value() < FeastReputation.START);
    }
}
