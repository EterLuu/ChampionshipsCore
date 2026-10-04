package ink.ziip.championshipscore.api.game.area.prepare.gui;

import static ink.ziip.championshipscore.api.game.area.prepare.gui.RiptideEditorPage.Screen.*;
import static ink.ziip.championshipscore.api.game.riptiderush.course.RiptideLevelType.*;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.riptiderush.*;
import ink.ziip.championshipscore.api.game.riptiderush.config.RiptideRushConfig;
import ink.ziip.championshipscore.api.game.riptiderush.course.RiptideBlueprint;
import ink.ziip.championshipscore.api.game.riptiderush.course.RiptideCoursePlanner;
import ink.ziip.championshipscore.api.game.riptiderush.course.RiptideLevelTemplate;

import org.junit.jupiter.api.Test;

import sun.misc.Unsafe;

import java.util.ArrayList;
import java.util.List;

class RiptideEditorModelTest {
    @Test
    void stoppedChallengeSubcategoriesKeepTheirParentsThroughEditingAndCreation() {
        var stoppedChallenges = RiptideEditorPage.stoppedChallenges();
        assertEquals(STOPPED_CHALLENGES, stoppedChallenges.screen());
        var floor = RiptideEditorPage.category(COLOR_FLOOR).atPage(2);
        assertEquals(stoppedChallenges, floor.parent());
        var dodge = RiptideEditorPage.category(DODGE);
        assertEquals(stoppedChallenges, dodge.parent());
        var side = stoppedChallenges.child(SIDE, null, null);
        var pool = side.sidePool().atPage(3);
        assertTrue(pool.isSidePool());
        assertEquals(PASS, pool.type());
        assertEquals(side, pool.parent());
        assertEquals(stoppedChallenges, side.parent());
        for (var category : List.of(floor, pool)) {
            assertEquals(category, category.child(ADD, null, null).categoryOrigin());
            assertEquals(
                    category,
                    category.child(ENTRY, "id", null).child(DELETE, "id", null).categoryOrigin());
            assertEquals(category.parent(), category.categoryOrigin().atPage(4).parent());
        }
        assertNull(RiptideEditorPage.category(PASS).parent());
    }

    private static RiptideRushConfig config() throws Exception {
        var field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        var plugin =
                (ChampionshipsCore)
                        ((Unsafe) field.get(null)).allocateInstance(ChampionshipsCore.class);
        var c = new RiptideRushConfig(plugin, "editor-test");
        c.setTemplates(
                List.of(
                        RiptideLevelTemplate.create("pass", PASS),
                        RiptideLevelTemplate.create("math", MATH),
                        RiptideLevelTemplate.create("floor", COLOR_FLOOR)));
        return c;
    }

    @Test
    void categoriesIncludeDisabledVariantsAndEditsPreserveOtherCategoriesAndStableIds()
            throws Exception {
        var c = config();
        var before = c.resolvePool();
        RiptideEditorModel.update(c, PASS, "pass", "enabled", false);
        RiptideEditorModel.update(c, PASS, "pass", "name", "左侧穿越");
        RiptideEditorModel.update(c, PASS, "pass", "variant", "GAP");
        var rows = RiptideEditorModel.rows(c, PASS);
        assertEquals(1, rows.size());
        assertEquals("pass", rows.getFirst().id());
        assertFalse(rows.getFirst().enabled());
        assertEquals("左侧穿越", rows.getFirst().name());
        assertEquals("GAP", rows.getFirst().variant());
        assertEquals(before.subList(1, 3), c.resolvePool().subList(1, 3));
    }

    @Test
    void invalidPropertiesAndStaleOrCrossCategoryActionsNeverPartiallyWrite() throws Exception {
        var c = config();
        var before = c.resolvePool();
        assertThrows(
                IllegalArgumentException.class,
                () -> RiptideEditorModel.update(c, MATH, "pass", "name", "wrong"));
        assertThrows(
                IllegalArgumentException.class,
                () -> RiptideEditorModel.update(c, PASS, "pass", "variant", "ADD"));
        assertThrows(
                IllegalArgumentException.class,
                () -> RiptideEditorModel.update(c, PASS, "pass", "weight", 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> RiptideEditorModel.update(c, PASS, "pass", "max-uses", 65));
        assertThrows(
                IllegalArgumentException.class,
                () -> RiptideEditorModel.update(c, PASS, "pass", "max-uses", 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> RiptideEditorModel.update(c, MATH, "math", "max-uses", 65));
        assertThrows(
                IllegalArgumentException.class,
                () -> RiptideEditorModel.update(c, PASS, "pass", "difficulty", 4));
        assertThrows(
                IllegalArgumentException.class,
                () -> RiptideEditorModel.update(c, PASS, "pass", "type", "MATH"));
        assertThrows(
                IllegalArgumentException.class,
                () -> RiptideEditorModel.update(c, PASS, "missing", "enabled", true));
        assertThrows(
                IllegalArgumentException.class, () -> RiptideEditorModel.remove(c, MATH, "pass"));
        assertThrows(IllegalArgumentException.class, () -> RiptideEditorModel.add(c, MATH, "GAP"));
        assertEquals(before, c.resolvePool());
    }

    @Test
    void addingAndDuplicatingProducesIndependentTypedEntriesWithMatchingProperties()
            throws Exception {
        var c = config();
        var source = RiptideEditorModel.add(c, COLOR_FLOOR, "COPPER");
        RiptideEditorModel.update(c, COLOR_FLOOR, source.id(), "weight", 43);
        RiptideEditorModel.update(c, COLOR_FLOOR, source.id(), "max-uses", 2);
        RiptideEditorModel.update(c, COLOR_FLOOR, source.id(), "difficulty", 2);
        var copy = RiptideEditorModel.duplicate(c, COLOR_FLOOR, source.id());
        assertNotEquals(source.id(), copy.id());
        assertEquals(COLOR_FLOOR, copy.type());
        assertEquals("COPPER", copy.variant());
        assertEquals(43, copy.weight());
        assertEquals(2, copy.maxUses());
        assertEquals(2, copy.difficulty());
        RiptideEditorModel.update(c, COLOR_FLOOR, copy.id(), "name", "铜材挑战");
        RiptideEditorModel.update(c, COLOR_FLOOR, copy.id(), "enabled", false);
        assertTrue(RiptideEditorModel.find(c, COLOR_FLOOR, source.id()).enabled());
        assertEquals(source.name(), RiptideEditorModel.find(c, COLOR_FLOOR, source.id()).name());
        assertEquals(5, c.resolvePool().size());
    }

    @Test
    void deletingLastVariantAllowsAnEmptyDraftAndSubsequentRebuilding() throws Exception {
        var c = config();
        for (var entry : List.copyOf(c.resolvePool()))
            RiptideEditorModel.remove(c, entry.type(), entry.id());
        assertTrue(c.resolvePool().isEmpty());
        assertTrue(RiptideEditorModel.rows(c, PASS).isEmpty());
        var added = RiptideEditorModel.add(c, PASS, "JUMP");
        assertEquals(List.of(added), c.resolvePool());
    }

    @Test
    void quotasAreCategoryScopedAndInvalidCountsDoNotChangeDraft() throws Exception {
        var c = config();
        RiptideEditorModel.quota(c, MATH, 0);
        assertEquals(12, c.getPassCount());
        assertEquals(0, c.getMathCount());
        assertEquals(8, c.getStoppedCount());
        assertThrows(
                IllegalArgumentException.class, () -> RiptideEditorModel.quota(c, COLOR_FLOOR, 2));
        assertThrows(IllegalArgumentException.class, () -> RiptideEditorModel.quota(c, PASS, 1));
        assertThrows(IllegalArgumentException.class, () -> RiptideEditorModel.quota(c, MATH, -1));
        assertThrows(
                IllegalArgumentException.class, () -> RiptideEditorModel.quota(c, COLOR_FLOOR, 65));
        assertEquals(12, c.getPassCount());
        assertEquals(0, c.getMathCount());
        assertEquals(8, c.getStoppedCount());
    }

    @Test
    void nestedChoosersAndDeletionKeepOriginCategoryAndClampEmptyLastPage() throws Exception {
        var c = config();
        var entries = new ArrayList<RiptideLevelTemplate>();
        for (int n = 0; n < 37; n++) entries.add(RiptideLevelTemplate.create("pass_" + n, PASS));
        c.setTemplates(entries);
        var category = RiptideEditorPage.category(PASS).atPage(1);
        var entry = category.child(ENTRY, "pass_36", null);
        var choice = entry.child(CHOICE, entry.id(), "variant");
        var confirm = entry.child(DELETE, entry.id(), null);
        assertEquals(entry, choice.parent());
        assertEquals(entry, confirm.parent());
        assertEquals(category, choice.parent().parent());
        RiptideEditorModel.remove(c, confirm.type(), confirm.id());
        var returned = confirm.parent().parent();
        assertEquals(PASS, returned.type());
        assertEquals(1, returned.page());
        assertEquals(
                0,
                RiptideEditorModel.clampPage(
                        returned.page(), RiptideEditorModel.rows(c, PASS).size(), 36));
        assertEquals(0, RiptideEditorModel.clampPage(4, 0, 36));
        assertEquals(1, RiptideEditorModel.clampPage(4, 37, 36));
    }

    @Test
    void previewDetailReturnsToSamePreviewPageAndCourseSettings() {
        var course = RiptideEditorPage.course();
        var preview = course.child(PREVIEW, null, null).atPage(1);
        var detail = preview.child(PLACEMENT, "47", null);
        assertEquals(preview, detail.parent());
        assertEquals(course, detail.parent().parent());
        assertEquals(1, detail.parent().page());
        assertEquals(PREVIEW, preview.atPage(0).screen());
        assertEquals(course, preview.atPage(0).parent());
    }

    @Test
    void fullPoolRejectsCreationAndDuplicationWithoutLosingAnyEntry() throws Exception {
        var c = config();
        var entries = new ArrayList<RiptideLevelTemplate>();
        for (int n = 0; n < RiptideRushConfig.MAX_POOL_SIZE; n++)
            entries.add(RiptideLevelTemplate.create("pass_" + n, PASS));
        c.setTemplates(entries);
        assertThrows(IllegalArgumentException.class, () -> RiptideEditorModel.add(c, PASS, "JUMP"));
        assertThrows(
                IllegalArgumentException.class,
                () -> RiptideEditorModel.duplicate(c, PASS, "pass_0"));
        assertEquals(entries, c.resolvePool());
    }

    @Test
    void blankAndArbitraryCopiesArePeersWithoutAPassSubtype() throws Exception {
        var c = config();
        var blank = RiptideEditorModel.addBlank(c, PASS);
        assertEquals("CUSTOM", blank.variant());
        assertFalse(blank.enabled());
        assertNull(blank.blueprint());
        assertThrows(
                IllegalArgumentException.class,
                () -> RiptideCoursePlanner.templatePreview(c, blank, 42));
        assertThrows(
                IllegalArgumentException.class,
                () -> RiptideEditorModel.update(c, PASS, blank.id(), "enabled", true));
        var saved = blank.withBlueprint(new RiptideBlueprint("", 0, 15, 12, List.of()));
        c.setTemplates(List.of(saved));
        RiptideEditorModel.update(c, PASS, saved.id(), "name", "潜行隧道");
        var copy = RiptideEditorModel.duplicate(c, PASS, saved.id());
        assertEquals("潜行隧道副本", copy.name());
        assertEquals("CUSTOM", copy.variant());
        var again = RiptideEditorModel.duplicate(c, PASS, copy.id());
        assertEquals("潜行隧道副本副本", again.name());
        assertEquals(3, RiptideEditorModel.rows(c, PASS).size());
        RiptideEditorModel.remove(c, PASS, saved.id());
        assertEquals(copy, RiptideEditorModel.find(c, PASS, copy.id()));
    }

    @Test
    void copyingLongNamesRetainsRequiredSuffixAndValidLength() throws Exception {
        var c = config();
        RiptideEditorModel.update(c, PASS, "pass", "name", "长".repeat(32));
        var copy = RiptideEditorModel.duplicate(c, PASS, "pass");
        assertEquals("长".repeat(30) + "副本", copy.name());
    }
}
