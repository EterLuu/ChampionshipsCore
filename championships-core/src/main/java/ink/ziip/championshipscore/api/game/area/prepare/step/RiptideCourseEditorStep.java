package ink.ziip.championshipscore.api.game.area.prepare.step;

import ink.ziip.championshipscore.platform.bukkit.text.LegacyText;
import ink.ziip.championshipscore.api.game.area.prepare.*;
import ink.ziip.championshipscore.api.game.area.prepare.gui.RiptideCourseEditorGui;
import ink.ziip.championshipscore.api.game.area.prepare.gui.RiptideEditorPage;
import ink.ziip.championshipscore.api.game.riptiderush.*;
import ink.ziip.championshipscore.api.gui.MenuId;
import ink.ziip.championshipscore.configuration.config.message.GuiConfig;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import java.util.Map;

/** Top-level editor categories; stopped challenges contain the floor, dodge and side mechanics. */
public final class RiptideCourseEditorStep extends PrepareStep {
    public enum Category { PASS, MATH, STOPPED, RHYTHM, COURSE }
    private final Category category;
    public RiptideCourseEditorStep(Category category) {
        super("course_" + (category == Category.COURSE ? "settings" : category.name().toLowerCase(java.util.Locale.ROOT)),
                GuiConfig.component(path(category, "title")),
                LegacyText.component(GuiConfig.line(path(category, "lore"), 0)),
                category == Category.COURSE ? Material.COMPARATOR : category == Category.STOPPED ? Material.CLOCK
                        : levelType(category).icon(), StepCaptureType.SELECT);
        this.category = category;
    }
    private static RiptideLevelType levelType(Category category) {
        return switch (category) {
            case PASS -> RiptideLevelType.PASS;
            case MATH -> RiptideLevelType.MATH;
            case RHYTHM -> RiptideLevelType.RHYTHM;
            default -> throw new IllegalArgumentException("此类别没有独立关卡类型");
        };
    }
    private static String path(Category category, String leaf) {
        return MenuId.RIPTIDE_RUSH_EDITOR.path() + ".steps." + category.name().toLowerCase(java.util.Locale.ROOT) + "." + leaf;
    }
    @Override public boolean requiresWorldEdit() { return category != Category.COURSE; }
    @Override public boolean isSet(PrepareSession session) {
        if (session == null) return false;
        var c = (RiptideRushConfig) session.getTarget().config();
        if (category == Category.COURSE) return c.getTimer() > 0;
        try {
            if (category == Category.STOPPED) {
                var allocation = c.previewStoppedAllocation();
                boolean floorReady = allocation.colorFloor() == 0 || c.resolvePool().stream()
                        .anyMatch(t -> t.type() == RiptideLevelType.COLOR_FLOOR && t.enabled());
                boolean dodgeReady = allocation.dodge() == 0 || c.resolvePool().stream()
                        .anyMatch(t -> t.type() == RiptideLevelType.DODGE && t.enabled());
                boolean sideReady = allocation.sideSweep() == 0 || c.resolvePool().stream()
                        .anyMatch(t -> t.type() == RiptideLevelType.PASS && t.enabled());
                return floorReady && dodgeReady && sideReady;
            }
            var type = levelType(category);
            int quota = RiptideCoursePlanner.quota(c, type);
            return quota == 0 || c.resolvePool().stream().anyMatch(t -> t.type() == type && t.enabled());
        } catch (IllegalArgumentException error) { return false; }
    }
    @Override public String stateText(PrepareSession session) {
        if (session == null) return null;
        var c = (RiptideRushConfig) session.getTarget().config();
        try {
            String root = MenuId.RIPTIDE_RUSH_EDITOR.path() + ".steps.";
            if (category == Category.STOPPED)
                return GuiConfig.text(root + "stopped.state.title", Map.of("quota", c.getStoppedCount()));
            if (category == Category.COURSE)
                return GuiConfig.text(root + "course.state.title", Map.of("value", c.getTimer()));
            var type = levelType(category);
            return GuiConfig.text(root + "category-state.title", Map.of("count", c.resolvePool().stream()
                            .filter(t -> t.type() == type && t.enabled()).count(), "quota", RiptideCoursePlanner.quota(c, type)));
        } catch (IllegalArgumentException error) { return null; }
    }
    @Override public void openSelection(PrepareSessionManager manager, Player player, PrepareSession session) {
        RiptideCourseEditorGui.open(manager, player, session,
                category == Category.COURSE ? RiptideEditorPage.course() : category == Category.STOPPED
                        ? RiptideEditorPage.stoppedChallenges() : RiptideEditorPage.category(levelType(category)));
    }
}
