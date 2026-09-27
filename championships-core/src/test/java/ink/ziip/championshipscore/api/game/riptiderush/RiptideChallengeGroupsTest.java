package ink.ziip.championshipscore.api.game.riptiderush;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RiptideChallengeGroupsTest {
    @Test void mathAndRhythmDoublesAndTriplesStartOneStageEarlierAndTrialTheWholeGroup() throws Exception {
        var c = RiptideTestFixtures.config();
        var g = c.resolveGeometry();
        for (var type : List.of(RiptideLevelType.MATH, RiptideLevelType.RHYTHM)) {
            for (int step : List.of(50, 140, 250, 350)) {
                var template = RiptideLevelTemplate.create("challenge", type);
                var level = new RiptideCoursePlan.Level(1, step, template,
                        type == RiptideLevelType.MATH ? "ADD" : "SHUTTER", 0, false, 5);
                var levels = RiptideChallengeGroups.arrange(c, g, List.of(level));
                assertEquals(step < 100 ? 1 : step < 200 ? 2 : 3, levels.size());
                var plan = new RiptideCoursePlan(1, RiptideCoursePlanner.VERSION, 0, levels, 500);
                for (var gate : levels) assertEquals(levels, plan.trialLevels(gate));
                for (int i = 1; i < levels.size(); i++) {
                    assertTrue(RiptideCoursePlanner.safeTransition(c, g, levels.get(i-1), levels.get(i)));
                    assertNotEquals(levels.get(i-1).contentSeed(), levels.get(i).contentSeed());
                }
                assertEquals(levels, RiptideChallengeGroups.arrange(c, g, List.of(level)));
            }
        }
    }

    @Test void noGroupStartsBeforeItsMilestoneOrOverlapsAnotherBuilding() throws Exception {
        var c = RiptideTestFixtures.config(); var g = c.resolveGeometry();
        var template = RiptideLevelTemplate.create("challenge", RiptideLevelType.MATH);
        for (int step : List.of(100, 101, 200, 201)) {
            var level = new RiptideCoursePlan.Level(1, step, template, "ADD", 0, false, 5);
            var levels = RiptideChallengeGroups.arrange(c, g, List.of(level));
            assertTrue(levels.size() < (step < 200 ? 2 : 3));
        }
        var thick = template.withBlueprint(new RiptideBlueprint("", 7, 15, 12, List.of()));
        var a = new RiptideCoursePlan.Level(1, 205, thick, "ADD", 0, false, 1);
        var b = new RiptideCoursePlan.Level(2, 250, thick, "ADD", 0, false, 2);
        var d = new RiptideCoursePlan.Level(3, 295, thick, "ADD", 0, false, 3);
        var result = RiptideChallengeGroups.arrange(c, g, List.of(a,b,d));
        assertEquals(4, result.size());
        assertEquals(a, result.getFirst());
        assertEquals(d, result.getLast());
        for (int i = 1; i < result.size(); i++)
            assertTrue(RiptideCoursePlanner.safeTransition(c, g, result.get(i-1), result.get(i)));
    }
}
