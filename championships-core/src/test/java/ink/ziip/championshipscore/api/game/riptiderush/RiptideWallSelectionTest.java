package ink.ziip.championshipscore.api.game.riptiderush;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class RiptideWallSelectionTest {
    @Test void copiedSnapshotsShareOneUseAndLegacyWallCapsCannotEnableRepeats() {
        var original = RiptideHitwCatalog.templates().getFirst();
        var copy = new RiptideLevelTemplate("copy", "copy", original.type(), original.variant(), true,
                10, 64, original.difficulty(), original.blueprint());
        assertEquals(1, copy.maxUses());
        assertFalse(copy.serialize().containsKey("max-uses"));
        assertEquals(original.designKey("CUSTOM"), copy.designKey("CUSTOM"));
        assertEquals(RiptideHitwCatalog.passages(original, true), RiptideHitwCatalog.passages(copy, true));
        var first = new RiptideCoursePlan.Level(1, 100, original, "CUSTOM", 0, false, 1);
        var second = new RiptideCoursePlan.Level(2, 200, copy, "CUSTOM", 0, true, 2);
        assertFalse(RiptideWallGroups.uniqueWalls(List.of(first, second)));
        var side = new RiptideCoursePlan.Level(2, 200, copy, "CUSTOM", 0, false, 2,
                0, 0, "SIDE", 1, List.of(new RiptideCoursePlan.SideWall(copy, "CUSTOM", 0, 1, 7)));
        assertFalse(RiptideWallGroups.uniqueWalls(List.of(first, side)));
        assertEquals(64, RiptideLevelTemplate.create("math", RiptideLevelType.MATH).maxUses());
    }

    @Test void commonLateralPassageIsRejectedEvenWithDifferentBuildingsAndMoreSpacing() throws Exception {
        var c = RiptideTestFixtures.config();
        var g = c.resolveGeometry();
        var a = RiptideHitwCatalog.templates().getFirst();
        var first = new RiptideCoursePlan.Level(1, 210, a, "CUSTOM", 0, false, 1);
        var b = RiptideHitwCatalog.templates().stream().filter(t -> !t.designKey("CUSTOM").equals(a.designKey("CUSTOM")))
                .filter(t -> RiptideHitwCatalog.passages(t, false).stream().anyMatch(p ->
                        RiptideHitwCatalog.passages(a, false).stream().anyMatch(q -> Math.abs(p.lateral() - q.lateral()) < .75)))
                .findFirst().orElseThrow();
        var second = new RiptideCoursePlan.Level(2, 300, b, "CUSTOM", 0, false, 2);
        assertFalse(RiptideCoursePlanner.safeTransition(c, g, first, second));
    }

    @Test void sideWallsNeverRelaxUniquenessWhenPoolIsTooSmall() throws Exception {
        var c = RiptideTestFixtures.config();
        var wall = RiptideHitwCatalog.templates().stream().filter(t -> t.difficulty() == 2).findFirst().orElseThrow();
        c.setTemplates(List.of(wall));
        assertTrue(RiptideWallGroups.selectSideWalls(c, c.resolveGeometry(), 350,
                new java.util.HashMap<>(Map.of()), new java.util.Random(1)).isEmpty());
    }
}
