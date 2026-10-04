package ink.ziip.championshipscore.api.game.area.prepare.gui;

import ink.ziip.championshipscore.api.game.riptiderush.course.RiptideLevelType;

/**
 * Explicit parent navigation: cancelling a chooser or confirmation returns to the exact originating
 * page.
 */
public record RiptideEditorPage(
        Screen screen,
        RiptideLevelType type,
        String id,
        int page,
        String field,
        RiptideEditorPage parent) {
    public enum Screen {
        STOPPED_CHALLENGES,
        SIDE,
        CATEGORY,
        ENTRY,
        ADD,
        CHOICE,
        DELETE,
        COURSE,
        PREVIEW,
        PLACEMENT,
        WORKSHOP,
        ABANDON
    }

    public static RiptideEditorPage stoppedChallenges() {
        return new RiptideEditorPage(Screen.STOPPED_CHALLENGES, null, null, 0, null, null);
    }

    public static RiptideEditorPage category(RiptideLevelType type) {
        return new RiptideEditorPage(
                Screen.CATEGORY,
                type,
                null,
                0,
                null,
                type == RiptideLevelType.COLOR_FLOOR || type == RiptideLevelType.DODGE
                        ? stoppedChallenges()
                        : null);
    }

    public RiptideEditorPage sidePool() {
        return new RiptideEditorPage(Screen.CATEGORY, RiptideLevelType.PASS, null, 0, null, this);
    }

    public boolean isSidePool() {
        return screen == Screen.CATEGORY && parent != null && parent.screen == Screen.SIDE;
    }

    public RiptideEditorPage categoryOrigin() {
        for (var origin = this; origin != null; origin = origin.parent)
            if (origin.screen == Screen.CATEGORY) return origin;
        throw new IllegalStateException("No originating category");
    }

    public static RiptideEditorPage course() {
        return new RiptideEditorPage(Screen.COURSE, null, null, 0, null, null);
    }

    public RiptideEditorPage atPage(int value) {
        return new RiptideEditorPage(screen, type, id, Math.max(0, value), field, parent);
    }

    public RiptideEditorPage child(Screen target, String entryId, String property) {
        return new RiptideEditorPage(target, type, entryId, 0, property, this);
    }
}
