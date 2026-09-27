package ink.ziip.championshipscore.api.game.riptiderush;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RiptideStoppedAllocationTest {
    @Test void weightedModeConservesParentQuotaAndHonorsSideLimit() {
        var standard = RiptideStoppedAllocation.allocate(8, "WEIGHTED", 3, 3, 2, 1);
        assertEquals(new RiptideStoppedAllocation(3, 3, 2), standard);

        var capped = RiptideStoppedAllocation.allocate(8, "WEIGHTED", 0, 1, 5, 1);
        assertEquals(8, capped.total());
        assertEquals(2, capped.sideSweep());
    }

    @Test void randomModeIsSeededAndStillRespectsWeightsAndLimits() {
        var first = RiptideStoppedAllocation.allocate(8, "RANDOM", 3, 3, 2, -918273L);
        assertEquals(first, RiptideStoppedAllocation.allocate(8, "RANDOM", 3, 3, 2, -918273L));
        assertEquals(8, first.total());
        assertTrue(first.sideSweep() <= 2);
        assertNotEquals(first, RiptideStoppedAllocation.allocate(8, "RANDOM", 3, 3, 2, 81237L));
    }

    @Test void invalidWeightsAndUnallocatableQuotasAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> RiptideStoppedAllocation.allocate(1, "WEIGHTED", 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> RiptideStoppedAllocation.allocate(3, "RANDOM", 0, 0, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> RiptideStoppedAllocation.allocate(1, "OTHER", 1, 1, 1, 0));
    }

    @Test void editorRejectsUnallocatableChangesWithoutMutatingTheDraft() throws Exception {
        var config = RiptideTestFixtures.config();
        config.setStoppedCount(8);
        config.setStoppedChildWeight(RiptideLevelType.DODGE, 3);
        config.setStoppedChildWeight(RiptideLevelType.COLOR_FLOOR, 0);
        assertThrows(IllegalArgumentException.class,
                () -> config.setStoppedChildWeight(RiptideLevelType.DODGE, 0));
        assertEquals(3, config.getDodgeWeight());
        assertThrows(IllegalArgumentException.class, () -> config.setStoppedCount(65));
        assertEquals(8, config.getStoppedCount());
        assertEquals(8, config.previewStoppedAllocation().total());
    }
}
