package ink.ziip.championshipscore.api.game.buildmart.blueprint;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildMartOrderPoolTest {

    @Test
    void allOneToFiveStarBlueprintsAreNormalOrders() {
        for (int stars = 1; stars <= 5; stars++) {
            assertTrue(BuildMartOrderPool.isNormalRating(stars));
        }
        assertFalse(BuildMartOrderPool.isNormalRating(7));
    }

    @Test
    void onlyThreeStarBlueprintsFeedTheGoldenPool() {
        assertTrue(BuildMartOrderPool.isGoldenSourceRating(3));
        assertFalse(BuildMartOrderPool.isGoldenSourceRating(2));
        assertFalse(BuildMartOrderPool.isGoldenSourceRating(5));
        assertFalse(BuildMartOrderPool.isGoldenSourceRating(7));
    }
}
