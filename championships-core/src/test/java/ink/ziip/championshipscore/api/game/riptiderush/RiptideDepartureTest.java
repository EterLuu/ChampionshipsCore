package ink.ziip.championshipscore.api.game.riptiderush;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RiptideDepartureTest {
    @Test
    void completedChallengeWaitsFortyCourseTicksBeforeMovementCanResume() {
        var departure = new RiptideDeparture();
        assertFalse(departure.tick());
        for (int challenge = 0; challenge < 3; challenge++) {
            departure.begin();
            for (int tick = 0; tick < 40; tick++) assertTrue(departure.tick(), "Still stopped on tick " + tick);
            assertFalse(departure.active());
            assertFalse(departure.tick());
        }
        departure.begin();
        departure.tick();
        departure.clear();
        assertFalse(departure.tick(), "Reset must not delay a new round");
    }

    @Test
    void plannerIncludesOneDeparturePerFloorOrWholeSideGroup() throws Exception {
        var config = RiptideTestFixtures.config();
        var g = config.resolveGeometry();
        int travel = RiptideCoursePlanner.estimateTicks(config, g, List.of());
        var floor = new RiptideCoursePlan.Level(1, 150, RiptideLevelTemplate.create("floor", RiptideLevelType.COLOR_FLOOR),
                "COLOR", 0, false, 1);
        var wall = RiptideLevelTemplate.create("wall", RiptideLevelType.PASS);
        var walls = List.of(new RiptideCoursePlan.SideWall(wall, "GAP", 0, 1, 1),
                new RiptideCoursePlan.SideWall(wall, "GAP", 0, -1, 2),
                new RiptideCoursePlan.SideWall(wall, "GAP", 0, 1, 3));
        var side = new RiptideCoursePlan.Level(2, 320, wall, "GAP", 0, false, 1, 0, 0, "SIDE", 1, walls);
        int floorTime = RiptideDifficulty.floorTicks(150 - g.halfLength(), g.totalSteps());
        int sideTime = RiptideSideSweep.totalTicks(g.halfWidth(), RiptideCoursePlanner.speedAt(config, g, 320), walls);
        assertEquals(travel + floorTime + 40, RiptideCoursePlanner.estimateTicks(config, g, List.of(floor)));
        assertEquals(travel + sideTime + 40, RiptideCoursePlanner.estimateTicks(config, g, List.of(side)));
        assertEquals(travel + floorTime + sideTime + 80, RiptideCoursePlanner.estimateTicks(config, g, List.of(floor, side)));
    }
}
