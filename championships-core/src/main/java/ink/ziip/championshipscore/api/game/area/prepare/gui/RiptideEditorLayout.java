package ink.ziip.championshipscore.api.game.area.prepare.gui;

import ink.ziip.championshipscore.api.gui.MenuId;
import ink.ziip.championshipscore.api.gui.MenuLayout;
import ink.ziip.championshipscore.configuration.config.message.GuiConfig;

import java.util.List;
import java.util.Map;

/** Each editor window owns its layout while retaining the existing shared item definitions. */
final class RiptideEditorLayout {
    private RiptideEditorLayout() {}

    static MenuId menu(RiptideEditorPage page) {
        if (page.isSidePool()) return MenuId.RIPTIDE_SIDE_POOL;
        return switch (page.screen()) {
            case STOPPED_CHALLENGES -> MenuId.RIPTIDE_STOPPED;
            case SIDE -> MenuId.RIPTIDE_SIDE;
            case CATEGORY -> MenuId.RIPTIDE_CATEGORY;
            case ENTRY -> MenuId.RIPTIDE_ENTRY;
            case ADD -> MenuId.RIPTIDE_ADD;
            case CHOICE -> MenuId.RIPTIDE_CHOICE;
            case WORKSHOP -> MenuId.RIPTIDE_WORKSHOP;
            case ABANDON -> MenuId.RIPTIDE_ABANDON;
            case DELETE -> MenuId.RIPTIDE_DELETE;
            case COURSE -> MenuId.RIPTIDE_COURSE;
            case PREVIEW -> MenuId.RIPTIDE_PREVIEW;
            case PLACEMENT -> MenuId.RIPTIDE_PLACEMENT;
        };
    }

    static MenuLayout load(RiptideEditorPage page, int choices) {
        MenuId id = menu(page);
        int size = GuiConfig.menu(id.path(), large(page) ? 54 : 27, "", List.of()).size();
        Map<String, Integer> controls = controls(id);
        List<Integer> content =
                switch (page.screen()) {
                    case CATEGORY, ADD -> MenuLayout.range(0, 36);
                    case PREVIEW -> MenuLayout.range(0, 45);
                    case CHOICE -> MenuLayout.range(0, 26);
                    default -> List.of();
                };
        if (page.screen() == RiptideEditorPage.Screen.CHOICE) {
            var single =
                    MenuLayout.load(
                            id.path(),
                            RiptideCourseEditorGui.PATH,
                            size,
                            content,
                            MenuLayout.range(0, size),
                            Map.of("back", 26));
            if (choices <= single.content().size()) return single;
        }
        return MenuLayout.load(
                id.path(),
                RiptideCourseEditorGui.PATH,
                size,
                content,
                MenuLayout.range(0, size),
                controls);
    }

    static boolean large(RiptideEditorPage page) {
        return switch (page.screen()) {
            case CATEGORY, ADD, PREVIEW -> true;
            default -> false;
        };
    }

    static boolean paged(RiptideEditorPage page) {
        return large(page) || page.screen() == RiptideEditorPage.Screen.CHOICE;
    }

    private static Map<String, Integer> controls(MenuId id) {
        return switch (id) {
            case RIPTIDE_STOPPED ->
                    Map.ofEntries(
                            Map.entry("stopped-summary", 4),
                            Map.entry("stopped-floor", 11),
                            Map.entry("stopped-dodge", 13),
                            Map.entry("stopped-side", 15),
                            Map.entry("stopped-mode", 22),
                            Map.entry("back", 26));
            case RIPTIDE_SIDE ->
                    Map.ofEntries(
                            Map.entry("side-quota", 4),
                            Map.entry("side-pool", 11),
                            Map.entry("side-weight", 13),
                            Map.entry("preview", 15),
                            Map.entry("back", 26));
            case RIPTIDE_CATEGORY ->
                    Map.ofEntries(
                            Map.entry("quota", 37),
                            Map.entry("stopped-child-weight", 37),
                            Map.entry("minimum", 39),
                            Map.entry("floor-timing", 39),
                            Map.entry("maximum", 41),
                            Map.entry("add", 46),
                            Map.entry("category-summary", 50),
                            Map.entry("previous", 45),
                            Map.entry("next", 53),
                            Map.entry("back", 49));
            case RIPTIDE_SIDE_POOL ->
                    Map.ofEntries(
                            Map.entry("side-rules", 37),
                            Map.entry("add", 46),
                            Map.entry("category-summary", 50),
                            Map.entry("previous", 45),
                            Map.entry("next", 53),
                            Map.entry("back", 49));
            case RIPTIDE_ENTRY ->
                    Map.ofEntries(
                            Map.entry("summary", 4),
                            Map.entry("name", 10),
                            Map.entry("operation", 11),
                            Map.entry("enabled", 12),
                            Map.entry("weight", 13),
                            Map.entry("max-uses", 14),
                            Map.entry("difficulty", 15),
                            Map.entry("duplicate", 16),
                            Map.entry("try-template", 18),
                            Map.entry("edit-building", 20),
                            Map.entry("delete", 22),
                            Map.entry("back", 26));
            case RIPTIDE_ADD ->
                    Map.ofEntries(
                            Map.entry("new-rhythm", 46),
                            Map.entry("new-dodge", 46),
                            Map.entry("blank-building", 46),
                            Map.entry("previous", 45),
                            Map.entry("next", 53),
                            Map.entry("back", 49));
            case RIPTIDE_CHOICE ->
                    Map.ofEntries(
                            Map.entry("previous", 18),
                            Map.entry("next", 20),
                            Map.entry("back", 26));
            case RIPTIDE_WORKSHOP ->
                    Map.ofEntries(
                            Map.entry("summary", 4),
                            Map.entry("continue-building", 11),
                            Map.entry("save-building", 13),
                            Map.entry("discard-building", 15),
                            Map.entry("floor-building-help", 22),
                            Map.entry("math-building-help", 22),
                            Map.entry("pass-building-help", 22),
                            Map.entry("back", 26));
            case RIPTIDE_ABANDON ->
                    Map.ofEntries(
                            Map.entry("confirm-discard-building", 11),
                            Map.entry("continue-building", 15),
                            Map.entry("back", 26));
            case RIPTIDE_DELETE ->
                    Map.ofEntries(
                            Map.entry("summary", 4),
                            Map.entry("confirm-delete", 11),
                            Map.entry("cancel", 15),
                            Map.entry("back", 26));
            case RIPTIDE_COURSE ->
                    Map.ofEntries(
                            Map.entry("course-summary", 4),
                            Map.entry("automatic-layout", 10),
                            Map.entry("preview", 12),
                            Map.entry("seed", 14),
                            Map.entry("fixed", 16),
                            Map.entry("constraints", 22),
                            Map.entry("back", 26));
            case RIPTIDE_PREVIEW ->
                    Map.ofEntries(
                            Map.entry("previous", 45),
                            Map.entry("reroll", 46),
                            Map.entry("build", 47),
                            Map.entry("play", 48),
                            Map.entry("back", 49),
                            Map.entry("seed-info", 50),
                            Map.entry("duration", 51),
                            Map.entry("next", 53));
            case RIPTIDE_PLACEMENT ->
                    Map.ofEntries(
                            Map.entry("summary", 4),
                            Map.entry("inspect", 11),
                            Map.entry("try-placement", 15),
                            Map.entry("back", 26));
            default -> throw new IllegalArgumentException("Not a Riptide editor window: " + id);
        };
    }
}
