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
        super("stamp", Component.text(GuiConfig.text("prepare-buildmart-buildmartstampstep.text-001")),
                Component.text(GuiConfig.text("prepare-buildmart-buildmartstampstep.text-002")), Material.DISPENSER,
                StepCaptureType.STAMP);
        this.base = base;
    }

    @Override public boolean isSet(PrepareSession session) {
        return session != null && session.isStamped();
    }

    @Override public String stamp(@NotNull PrepareSession session, @NotNull Player player, int count) {
        if (count < 1) return Utils.formatAdminError(GuiConfig.text("prepare-buildmart-buildmartstampstep.text-003"));
        if (!base.isFile()) return Utils.formatAdminError(GuiConfig.text("prepare-buildmart-buildmartstampstep.text-004"));
        World world = Bukkit.getWorld(session.getTarget().worldName());
        if (world == null) return Utils.formatAdminError(GuiConfig.text("prepare-buildmart-buildmartstampstep.text-005"));
        if (!session.getTarget().canSaveMap())
            return Utils.formatAdminError(GuiConfig.text("prepare-buildmart-buildmartstampstep.text-006"));
        BuildMartConfig config = (BuildMartConfig) session.getTarget().config();
        if (config.getHubPos1() == null || config.getHubPos2() == null)
            return Utils.formatAdminError(GuiConfig.text("prepare-buildmart-buildmartstampstep.text-007"));
        Vector baseOrigin = config.getBaseSourceOrigin();
        if (baseOrigin == null)
            return Utils.formatAdminError(GuiConfig.text("prepare-buildmart-buildmartstampstep.text-008"));
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
            return Utils.formatAdminError(GuiConfig.text("prepare-buildmart-buildmartstampstep.text-009") + e.getMessage());
        }
        config.setBaseCount(count);
        session.getTarget().config().markPrepareWorldBuilt();
        session.setWorldConfirmed(true);
        session.setStamped(true);
        player.teleport(config.getBaseGrid().origin(0).toLocation(world));
        return Utils.formatAdminSuccess(GuiConfig.text("prepare-buildmart-buildmartstampstep.text-010") + count
                + GuiConfig.text("prepare-buildmart-buildmartstampstep.text-011"));
    }
}
