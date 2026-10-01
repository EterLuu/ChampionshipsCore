package ink.ziip.championshipscore.api.game.riptiderush;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RiptideStageRulesTest {
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

    @Test void stoppedQuotaIsAllocatedToChildrenAndStagesKeepTheirKinds() throws Exception {
        var c = RiptideTestFixtures.config();
        c.setStoppedCount(8);
        c.setColorFloorWeight(3); c.setDodgeWeight(3); c.setSideSweepWeight(2);
        var allocation = c.stoppedAllocation(1);
        assertEquals(3, allocation.colorFloor());
        assertEquals(3, allocation.dodge());
        assertEquals(2, allocation.sideSweep());
        assertEquals(8, allocation.total());
        assertEquals(allocation, c.stoppedAllocation(1));
        assertTrue(c.stoppedAllocation(2).sideSweep() <= 2);
        var template = RiptideLevelTemplate.create("color_floor", RiptideLevelType.COLOR_FLOOR);
        var side = new RiptideCoursePlan.Level(1, 300, template, "COPPER", 0, false, 1,
                0, 0, "SIDE", 1);
        assertEquals(RiptideLevelType.COLOR_FLOOR, side.type());
        assertEquals(RiptideStageKind.SIDE_SWEEP, side.kind());
        assertTrue(side.stopsRaft());
        assertEquals(RiptideStageGroup.STOPPED, side.kind().group());
        var dodge = new RiptideCoursePlan.Level(1, 300,
                RiptideLevelTemplate.create("dodge", RiptideLevelType.DODGE), "ZOMBIE", 0, false, 1);
        assertEquals(RiptideStageKind.DODGE, dodge.kind());
        assertTrue(dodge.stopsRaft());
        assertEquals(RiptideStageGroup.STOPPED, dodge.kind().group());
        var floor = new RiptideCoursePlan.Level(1, 300, template, "COPPER", 0, false, 1);
        assertEquals(RiptideStageKind.COLOR_FLOOR, floor.kind());
        assertTrue(floor.colorFloor());
        assertEquals(RiptideStageGroup.STOPPED, floor.kind().group());
        assertEquals(RiptideStageGroup.MOVING, RiptideStageKind.PASS.group());
        assertEquals(RiptideStageGroup.MOVING, RiptideStageKind.MATH.group());
        assertEquals(RiptideStageGroup.MOVING, RiptideStageKind.RHYTHM.group());
    }

    @Test
    void exposesOnlySemanticMechanicCategories() {
        assertEquals(List.of(RiptideLevelType.MATH, RiptideLevelType.PASS, RiptideLevelType.COLOR_FLOOR, RiptideLevelType.DODGE, RiptideLevelType.RHYTHM),
                List.of(RiptideLevelType.values()));
    }

    @Test
    void parsesPersistedTypesCaseInsensitivelyAndRejectsObstacleShapes() {
        assertEquals(RiptideLevelType.MATH, RiptideLevelType.parse("math"));
        assertEquals(RiptideLevelType.PASS, RiptideLevelType.parse(" PASS "));
        assertThrows(IllegalArgumentException.class, () -> RiptideLevelType.parse("wall-hole"));
        assertThrows(IllegalArgumentException.class, () -> RiptideLevelType.parse("parkour"));
    }
}
