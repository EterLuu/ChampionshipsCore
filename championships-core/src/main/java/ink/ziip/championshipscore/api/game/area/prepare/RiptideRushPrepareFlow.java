package ink.ziip.championshipscore.api.game.area.prepare;

import ink.ziip.championshipscore.api.game.area.prepare.step.ConfirmWorldStep;
import ink.ziip.championshipscore.api.game.area.prepare.step.RiptideCourseEditorStep;
import ink.ziip.championshipscore.api.game.riptiderush.config.RiptideRushConfig;
import ink.ziip.championshipscore.api.game.riptiderush.course.RiptideCourseGenerator;
import ink.ziip.championshipscore.api.game.riptiderush.course.RiptideCourseGeometry;
import ink.ziip.championshipscore.api.game.riptiderush.course.RiptideCoursePlanner;
import ink.ziip.championshipscore.api.game.setup.SetupTarget;
import ink.ziip.championshipscore.configuration.config.message.GuiConfig;

import org.bukkit.Material;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Map-owned pool and course rules; publishing stores a reproducible example and its configuration.
 */
public final class RiptideRushPrepareFlow extends SnapshotMapPrepareFlow {
    public RiptideRushPrepareFlow() {
        super(World.Environment.NORMAL);
    }

    @Override
    public void onSessionExit(@NotNull PrepareSession session) {
        ink.ziip.championshipscore.api.game.riptiderush.editor.RiptideWorkshop.cancel(session);
        RiptideCourseGenerator.cancel(org.bukkit.Bukkit.getWorld(session.getTarget().worldName()));
    }

    @Override
    public @NotNull List<PrepareStep> buildSteps(@NotNull SetupTarget target) {
        return List.of(
                new ConfirmWorldStep(
                        player -> isInCorrectWorld(player, target), target.worldName()),
                new RiptideCourseEditorStep(RiptideCourseEditorStep.Category.PASS),
                new RiptideCourseEditorStep(RiptideCourseEditorStep.Category.MATH),
                new RiptideCourseEditorStep(RiptideCourseEditorStep.Category.STOPPED),
                new RiptideCourseEditorStep(RiptideCourseEditorStep.Category.RHYTHM),
                new RiptideCourseEditorStep(RiptideCourseEditorStep.Category.COURSE));
    }

    @Override
    public @NotNull List<String> validate(@NotNull PrepareSession session) {
        List<String> errors = new ArrayList<>(validateForDisplay(session));
        if (!errors.isEmpty()) return errors;
        var config = cfg(session.getTarget());
        try {
            RiptideCoursePlanner.plan(config, config.getPreviewSeed());
        } catch (RuntimeException exception) {
            errors.add(exception.getMessage());
        }
        return errors;
    }

    /** Sidebar refreshes must never run the backtracking course search. */
    @Override
    public @NotNull List<String> validateForDisplay(@NotNull PrepareSession session) {
        List<String> errors = new ArrayList<>(super.validate(session));
        if (ink.ziip.championshipscore.api.game.riptiderush.editor.RiptideWorkshop.get(session)
                != null)
            errors.add(
                    GuiConfig.text(
                            "map-editor.games.riptide-rush.menus.course-editor.items.finish-building.title"));
        RiptideRushConfig config = cfg(session.getTarget());
        RiptideCourseGeometry geometry;
        try {
            geometry = config.resolveGeometry();
        } catch (RuntimeException exception) {
            errors.add(
                    GuiConfig.text(
                                    "map-editor.menus.step-list.games.riptide-rush.validation.geometry.title")
                            + " 原因："
                            + exception.getMessage());
            return errors;
        }
        int levelCount =
                config.getPassCount()
                        + config.getMathCount()
                        + config.getStoppedCount()
                        + config.getRhythmCount();
        if (levelCount > 0
                && geometry.totalSteps() / (levelCount + 1D) < config.getMinimumLevelSpacing())
            errors.add(
                    GuiConfig.text(
                            "map-editor.menus.step-list.games.riptide-rush.validation.spacing.title"));
        if (config.getTimer() < 1
                || !(config.hasValidMovementSpeeds()
                        && config.getHorizontalPadding() >= 0D
                        && Double.isFinite(config.getHorizontalPadding())
                        && config.getFallDistance() > 0D
                        && Double.isFinite(config.getFallDistance())))
            errors.add(
                    GuiConfig.text(
                            "map-editor.menus.step-list.games.riptide-rush.validation.movement.title"));
        if (!validBlock(config.getRaftMaterial())
                || !validBlock(config.getObstacleMaterial())
                || !validTrail(config.getTrailMaterial()))
            errors.add(
                    GuiConfig.text(
                            "map-editor.menus.step-list.games.riptide-rush.validation.materials.title"));
        if (config.getMathPreviewBlocks() < 1
                || config.getMinimumOperand() < 0
                || config.getMaximumOperand() < config.getMinimumOperand()
                || config.getMaximumOperand() > Integer.MAX_VALUE / 2)
            errors.add(
                    GuiConfig.text(
                            "map-editor.menus.step-list.games.riptide-rush.validation.math.title"));
        try {
            config.nextCourseSeed(); // Reject malformed explicitly fixed seeds during publication.
            RiptideCoursePlanner.validateInputs(config, config.getPreviewSeed());
        } catch (RuntimeException exception) {
            errors.add(exception.getMessage());
        }
        return errors;
    }

    @Override
    public @NotNull CompletableFuture<Boolean> publish(@NotNull PrepareSession session) {
        return generateAndSave(session);
    }

    @Override
    public @NotNull CompletableFuture<Boolean> saveDraft(@NotNull PrepareSession session) {
        // Incomplete/temporarily unsatisfiable pools must still be saveable as drafts.
        if (ink.ziip.championshipscore.api.game.riptiderush.editor.RiptideWorkshop.get(session)
                        != null
                || RiptideCourseGenerator.isGenerating(
                        org.bukkit.Bukkit.getWorld(session.getTarget().worldName()))
                || ink.ziip.championshipscore.api.game.riptiderush.editor.RiptideCourseTrial
                        .isActive(session.getTarget().worldName()))
            return CompletableFuture.completedFuture(false);
        return super.publish(session);
    }

    private CompletableFuture<Boolean> generateAndSave(PrepareSession session) {
        try {
            var config = cfg(session.getTarget());
            var plan = RiptideCoursePlanner.plan(config, config.getPreviewSeed());
            return RiptideCourseGenerator.generateAsync(
                            session.getPlugin(), config, plan, session.getTarget()::canSaveMap)
                    .thenCompose(
                            built ->
                                    built
                                            ? super.publish(session)
                                            : CompletableFuture.completedFuture(false));
        } catch (RuntimeException exception) {
            session.getPlugin()
                    .getLogger()
                    .log(Level.SEVERE, "激流勇进自动赛道生成失败 | map=" + session.getAreaName(), exception);
            return CompletableFuture.completedFuture(false);
        }
    }

    private static boolean validBlock(String configured) {
        Material material = configured == null ? null : Material.matchMaterial(configured);
        return material != null && material.isBlock() && !material.isAir();
    }

    private static boolean validTrail(String configured) {
        Material material = configured == null ? null : Material.matchMaterial(configured);
        return material != null && material.isBlock();
    }

    private static RiptideRushConfig cfg(SetupTarget target) {
        return (RiptideRushConfig) target.config();
    }
}
