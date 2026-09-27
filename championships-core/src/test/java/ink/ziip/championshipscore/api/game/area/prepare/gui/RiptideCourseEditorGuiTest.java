package ink.ziip.championshipscore.api.game.area.prepare.gui;

import ink.ziip.championshipscore.api.game.riptiderush.RiptideLevelTemplate;
import ink.ziip.championshipscore.api.game.riptiderush.RiptideLevelType;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class RiptideCourseEditorGuiTest {
    @Test void everyConcreteVariantFitsTheChooserWithoutCoveringNavigation() {
        for (var type : RiptideLevelType.values()) {
            int count = (int) RiptideLevelTemplate.variants(type).stream().filter(v -> !v.equals("AUTO")).count();
            int[] slots = RiptideCourseEditorGui.choiceSlots(count);
            assertEquals(count, slots.length);
            assertEquals(count, Arrays.stream(slots).distinct().count());
            assertTrue(Arrays.stream(slots).allMatch(slot -> slot >= (count > 9 ? 0 : 9) && slot < 18));
        }
        assertArrayEquals(new int[]{12, 13, 14}, RiptideCourseEditorGui.choiceSlots(3));
        assertArrayEquals(new int[]{1, 2, 3, 4, 5, 6, 11, 12, 13, 14, 15}, RiptideCourseEditorGui.choiceSlots(11));
    }
}
