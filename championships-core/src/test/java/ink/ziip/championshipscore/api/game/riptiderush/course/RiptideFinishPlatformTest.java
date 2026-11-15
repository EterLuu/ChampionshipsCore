package ink.ziip.championshipscore.api.game.riptiderush.course;

import static ink.ziip.championshipscore.api.game.riptiderush.support.RiptideTestFixtures.*;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class RiptideFinishPlatformTest {
    @Test
    void backgroundContainsEveryAllowedFinishIncludingBothEndpoints() {
        var building = new RiptideFinishPlatform.Bounds(-18, -9, 5, 40, 19, 27);
        var background = new RiptideFinishPlatform.Bounds(-18, -9, 505, 40, 19, 693);
        for (int length = 500; length <= 650; length++)
            assertTrue(background.contains(building, length));
        assertFalse(background.contains(building, 499));
        assertFalse(
                background.contains(new RiptideFinishPlatform.Bounds(-19, -9, 5, 40, 19, 27), 500));
        assertFalse(
                background.contains(
                        new RiptideFinishPlatform.Bounds(-18, -10, 5, 40, 19, 27), 500));
        assertFalse(background.contains(building, 667));
    }

    @Test
    void boundsFollowAllFourCourseDirectionsAndIncludeTheWholeBuilding() throws Exception {
        var b = new RiptideFinishPlatform.Bounds(-18, -9, 505, 40, 19, 693);
        for (int[] axis : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            var c = config(new TestWorld(), axis);
            var g = c.resolveGeometry();
            var min = b.minimum(g);
            var max = b.maximum(g);
            for (int f : new int[] {505, 693})
                for (int l : new int[] {-18, 40}) {
                    assertTrue(g.blockX(f, l) >= min.getX() && g.blockX(f, l) <= max.getX());
                    assertTrue(g.blockZ(f, l) >= min.getZ() && g.blockZ(f, l) <= max.getZ());
                }
            assertEquals(g.floorY() - 9, min.getY());
            assertEquals(g.floorY() + 19, max.getY());
        }
    }

    @Test
    void malformedOrExcessiveBoundsAreRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new RiptideFinishPlatform.Bounds(-65, 0, 1, 0, 1, 2));
        assertThrows(
                IllegalArgumentException.class,
                () -> new RiptideFinishPlatform.Bounds(0, 0, 5, 0, 0, 4));
        assertThrows(
                IllegalArgumentException.class,
                () -> new RiptideFinishPlatform.Bounds(-64, -32, 0, 64, 64, 4224));
    }
}
