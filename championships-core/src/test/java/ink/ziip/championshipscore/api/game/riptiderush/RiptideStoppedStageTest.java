package ink.ziip.championshipscore.api.game.riptiderush;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RiptideStoppedStageTest {
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
}
