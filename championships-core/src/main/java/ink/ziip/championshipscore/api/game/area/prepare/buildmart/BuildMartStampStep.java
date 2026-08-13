package ink.ziip.championshipscore.api.game.area.prepare.buildmart;

import ink.ziip.championshipscore.configuration.config.message.GuiConfig;

import ink.ziip.championshipscore.api.game.area.prepare.PrepareSession;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareStep;
import ink.ziip.championshipscore.api.game.area.prepare.StepCaptureType;
import ink.ziip.championshipscore.api.game.arena.ArenaPreparer;
import ink.ziip.championshipscore.api.game.buildmart.BuildMartConfig;
import ink.ziip.championshipscore.util.Utils;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/** Builds the 0th base template plus N playable team-base copies around the hand-built hub. */
final class BuildMartStampStep extends PrepareStep {
    private final File base;

    BuildMartStampStep(File base) {
        super("stamp", Component.text(GuiConfig.text("map-editor.games.build-mart.steps.base-generation.generate-team-base")),
                Component.text(GuiConfig.text("map-editor.games.build-mart.steps.base-generation.team-base-count-input-hint")), Material.DISPENSER,
                StepCaptureType.STAMP);
        this.base = base;
    }

    @Override public boolean isSet(PrepareSession session) {
        return session != null && session.isStamped();
    }

    @Override public String stamp(@NotNull PrepareSession session, @NotNull Player player, int count) {
        if (count < 1) return Utils.formatAdminError(GuiConfig.text("map-editor.games.build-mart.steps.base-generation.the-number-of-teams-must-be-greater-than-0"));
        if (!base.isFile()) return Utils.formatAdminError(GuiConfig.text("map-editor.games.build-mart.steps.base-generation.please-save-the-number-0-base-template-first"));
        World world = Bukkit.getWorld(session.getTarget().worldName());
        if (world == null) return Utils.formatAdminError(GuiConfig.text("map-editor.games.build-mart.steps.base-generation.the-map-world-has-not-been-loaded-yet"));
        if (!session.getTarget().canSaveMap())
            return Utils.formatAdminError(GuiConfig.text("map-editor.games.build-mart.steps.base-generation.there-is-still-a-game-instance-running-on-the-same-map-cannot-be-spawned"));
        BuildMartConfig config = (BuildMartConfig) session.getTarget().config();
        if (config.getHubPos1() == null || config.getHubPos2() == null)
            return Utils.formatAdminError(GuiConfig.text("map-editor.games.build-mart.steps.base-generation.please-set-the-resource-hall-boundary-first"));
        Vector baseOrigin = config.getBaseSourceOrigin();
        if (baseOrigin == null)
            return Utils.formatAdminError(GuiConfig.text("map-editor.games.build-mart.steps.base-generation.please-re-save-the-number-0-base-template-once-to-record-the-location-of-the-template-in-the-world"));
        try {
            Vector baseSize = session.getPlugin().getWorldEditManager().getSchematicDimensions(base);
            var previousGrid = config.getBaseGrid();
            Vector previousBaseSize = config.getBaseSchematicSize();
            // The persisted count excludes the 0th source template, so clear only physical copies 1..N.
            // Include one extra old ring index so maps stamped before copy 0 became the true centre do not
            // leave their final generated base behind after the layout is corrected.
            ArenaPreparer.clearAdditionalCopies(session.getPlugin(), world, previousGrid, config.getBaseCount() + 2,
                    previousBaseSize);
            var grid = config.prepareBaseGrid(baseOrigin, baseSize);
            // Index 0 remains the editable source template. Indices 1..N are the bases players actually use.
            ArenaPreparer.stampAdditionalCopies(session.getPlugin(), world, base, grid, count + 1);
        } catch (Exception e) {
            return Utils.formatAdminError(GuiConfig.text("map-editor.games.build-mart.steps.base-generation.failed-to-generate-map") + e.getMessage());
        }
        config.setBaseCount(count);
        session.getTarget().config().markPrepareWorldBuilt();
        session.setWorldConfirmed(true);
        session.setStamped(true);
        player.teleport(config.getBaseGrid().origin(0).toLocation(world));
        return Utils.formatAdminSuccess(GuiConfig.text("map-editor.games.build-mart.steps.base-generation.base-template-number-0-has-been-reserved-and-generated") + count
                + GuiConfig.text("map-editor.games.build-mart.steps.base-generation.actual-team-base-please-post-after-completing-the-points"));
    }
}
