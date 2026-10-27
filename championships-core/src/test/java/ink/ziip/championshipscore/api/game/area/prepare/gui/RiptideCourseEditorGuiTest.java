package ink.ziip.championshipscore.api.game.area.prepare.gui;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.api.game.riptiderush.course.RiptideCoursePlan;
import ink.ziip.championshipscore.api.game.riptiderush.course.RiptideLevelTemplate;
import ink.ziip.championshipscore.api.game.riptiderush.course.RiptideLevelType;
import ink.ziip.championshipscore.configuration.ConfigurationStateExtension;
import ink.ziip.championshipscore.configuration.config.message.GuiConfig;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

@ExtendWith(ConfigurationStateExtension.class)
class RiptideCourseEditorGuiTest {
    private YamlConfiguration document;

    @BeforeEach
    void loadGui() throws Exception {
        document =
                YamlConfiguration.loadConfiguration(Path.of("src/main/resources/gui.yml").toFile());
        var field = GuiConfig.class.getDeclaredField("active");
        field.setAccessible(true);
        field.set(null, document);
    }

    private static int[] choiceSlots(int count) {
        var page =
                RiptideEditorPage.category(RiptideLevelType.RHYTHM)
                        .child(RiptideEditorPage.Screen.ENTRY, "example", null)
                        .child(RiptideEditorPage.Screen.CHOICE, "example", "variant");
        return RiptideEditorLayout.load(page, count).pageSlots(0, count).stream()
                .mapToInt(Integer::intValue)
                .toArray();
    }

    @Test
    void everyConcreteVariantFitsTheChooserWithoutCoveringNavigation() {
        for (var type : RiptideLevelType.values()) {
            int count =
                    (int)
                            RiptideLevelTemplate.variants(type).stream()
                                    .filter(v -> !v.equals("AUTO"))
                                    .count();
            int[] slots = choiceSlots(count);
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
        assertArrayEquals(new int[] {12, 13, 14}, choiceSlots(3));
        assertArrayEquals(new int[] {1, 2, 3, 4, 5, 6, 11, 12, 13, 14, 15}, choiceSlots(11));
        assertArrayEquals(java.util.stream.IntStream.range(0, 26).toArray(), choiceSlots(26));
    }

    @Test
    void smallChoosersAndCategoriesVisitEveryEntryAndKeepTheOriginalOrder() {
        var page =
                RiptideEditorPage.category(RiptideLevelType.RHYTHM)
                        .child(RiptideEditorPage.Screen.ENTRY, "example", null)
                        .child(RiptideEditorPage.Screen.CHOICE, "example", "variant");
        for (var screen :
                List.of(
                        page,
                        RiptideEditorPage.category(RiptideLevelType.RHYTHM),
                        RiptideEditorPage.category(RiptideLevelType.RHYTHM)
                                .child(RiptideEditorPage.Screen.ADD, null, null),
                        RiptideEditorPage.course()
                                .child(RiptideEditorPage.Screen.PREVIEW, null, null))) {
            String path = RiptideEditorLayout.menu(screen).path();
            document.set(path + ".layout.content", List.of(16, 10));
            var layout = RiptideEditorLayout.load(screen, 26);
            var visited = new java.util.ArrayList<Integer>();
            for (int index = 0; index < layout.pageCount(27); index++)
                for (int slot : layout.pageSlots(index, 27))
                    visited.add(layout.index(index, slot, 27));
            assertEquals(java.util.stream.IntStream.range(0, 27).boxed().toList(), visited);
            assertEquals(List.of(16), layout.pageSlots(13, 27));
            document.set(path + ".layout.content", List.of(11));
            assertEquals(List.of(16, 10), layout.content());
        }
    }

    @Test
    void chooserReservesNavigationOnlyWhenPaginationIsNeeded() {
        var page =
                RiptideEditorPage.category(RiptideLevelType.RHYTHM)
                        .child(RiptideEditorPage.Screen.CHOICE, "example", "variant");
        var full = RiptideEditorLayout.load(page, 26);
        assertEquals(26, full.content().size());
        assertFalse(full.controls().containsKey("next"));
        var multiple = RiptideEditorLayout.load(page, 27);
        assertEquals(24, multiple.content().size());
        assertEquals(2, multiple.pageCount(27));
        assertEquals(20, multiple.control("next"));
        assertFalse(multiple.pageSlots(0, 27).contains(20));
    }

    @Test
    void displayedFloorTimingComesFromTheCurrentMechanicAndConfiguredSeparator() {
        document.set(RiptideCourseEditorGui.PATH + ".items.list-separator.title", " · ");
        assertEquals(
                java.util.Map.of(
                        "value",
                        "32 · 28 · 24 · 20 · 16",
                        "materials",
                        "5 · 6 · 7 · 8",
                        "rounds",
                        7),
                RiptideCourseEditorGui.floorTiming());
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
