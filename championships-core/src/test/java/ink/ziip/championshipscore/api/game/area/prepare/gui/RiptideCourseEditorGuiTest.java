package ink.ziip.championshipscore.api.game.area.prepare.gui;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.api.game.riptiderush.course.RiptideCoursePlan;
import ink.ziip.championshipscore.api.game.riptiderush.course.RiptideLevelTemplate;
import ink.ziip.championshipscore.api.game.riptiderush.course.RiptideLevelType;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

class RiptideCourseEditorGuiTest {
    @Test
    void everyConcreteVariantFitsTheChooserWithoutCoveringNavigation() {
        for (var type : RiptideLevelType.values()) {
            int count =
                    (int)
                            RiptideLevelTemplate.variants(type).stream()
                                    .filter(v -> !v.equals("AUTO"))
                                    .count();
            int[] slots = RiptideCourseEditorGui.choiceSlots(count);
            assertEquals(count, slots.length);
            assertEquals(count, Arrays.stream(slots).distinct().count());
            assertTrue(
                    Arrays.stream(slots)
                            .allMatch(
                                    slot ->
                                            count > 18
                                                    ? slot < 26
                                                    : slot >= (count > 9 ? 0 : 9) && slot < 18));
        }
        assertArrayEquals(new int[] {12, 13, 14}, RiptideCourseEditorGui.choiceSlots(3));
        assertArrayEquals(
                new int[] {1, 2, 3, 4, 5, 6, 11, 12, 13, 14, 15},
                RiptideCourseEditorGui.choiceSlots(11));
        assertArrayEquals(
                java.util.stream.IntStream.range(0, 26).toArray(),
                RiptideCourseEditorGui.choiceSlots(26));
    }

    @Test
    void singleTrialUsesTheWholeGroupedChallengePlan() {
        var template = RiptideLevelTemplate.create("math", RiptideLevelType.MATH);
        var first =
                new RiptideCoursePlan.Level(
                        1, 100, template, "ADD", 0, false, 1, -7, 1, "MATH_DOUBLE", 0);
        var second =
                new RiptideCoursePlan.Level(
                        1, 112, template, "SUBTRACT", 0, false, 2, -7, 2, "MATH_DOUBLE", 0);
        var course = new RiptideCoursePlan(99, 1, 0, java.util.List.of(first, second));
        var trial = RiptideCourseEditorGui.trialPlan(course, first);
        assertEquals(java.util.List.of(first, second), trial.levels());
    }
}
