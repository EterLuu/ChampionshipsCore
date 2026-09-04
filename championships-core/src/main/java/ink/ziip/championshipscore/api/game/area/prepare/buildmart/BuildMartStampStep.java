package ink.ziip.championshipscore.api.game.area.prepare.buildmart;

import ink.ziip.championshipscore.configuration.config.message.GuiConfig;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;

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

/** Builds the 0th base template plus N playable team-base copies on the persisted map layout. */
final class BuildMartStampStep extends PrepareStep {
    private final File base;

    BuildMartStampStep(File base) {
        super("stamp", Component.text(GuiConfig.text("map-editor.menus.step-list.games.build-mart.items.stamp.title")),
                Component.text(GuiConfig.line("map-editor.menus.step-list.games.build-mart.items.stamp.lore", 0)), Material.DISPENSER,
                StepCaptureType.STAMP);
        this.base = base;
    }

    @Override public boolean isSet(PrepareSession session) {
        return session != null && session.isStamped();
    }

    @Override public String stamp(@NotNull PrepareSession session, @NotNull Player player, int count) {
        if (count < 1) return Utils.formatAdminError(MessageConfig.MAP_EDITOR_BUILD_BASE_COUNT_POSITIVE);
        if (!base.isFile()) return Utils.formatAdminError(MessageConfig.MAP_EDITOR_BUILD_BASE_TEMPLATE_MISSING);
        World world = Bukkit.getWorld(session.getTarget().worldName());
        if (world == null) return Utils.formatAdminError(MessageConfig.MAP_EDITOR_BUILD_WORLD_NOT_LOADED);
        if (!session.getTarget().canSaveMap())
            return Utils.formatAdminError(MessageConfig.MAP_EDITOR_BUILD_INSTANCE_RUNNING);
        BuildMartConfig config = (BuildMartConfig) session.getTarget().config();
        if (config.getHubPos1() == null || config.getHubPos2() == null)
            return Utils.formatAdminError(MessageConfig.MAP_EDITOR_BUILD_HUB_MISSING);
        Vector baseOrigin = config.getBaseSourceOrigin();
        if (baseOrigin == null)
            return Utils.formatAdminError(MessageConfig.MAP_EDITOR_BUILD_BASE_ORIGIN_MISSING);
        try {
            Vector baseSize = session.getPlugin().getWorldEditManager().getSchematicDimensions(base);
            var previousGrid = config.getBaseGrid();
            Vector previousBaseSize = config.getBaseSchematicSize();
            // The persisted count excludes the 0th source template, so clear only physical copies 1..N.
            // Include one extra historical index so maps stamped before copy 0 became the true source do not
            // leave their final generated base behind after the layout is corrected.
            ArenaPreparer.clearAdditionalCopies(session.getPlugin(), world, previousGrid, config.getBaseCount() + 2,
                    previousBaseSize);
            var grid = config.prepareBaseGrid(baseOrigin, baseSize);
            // Index 0 remains the editable source template. Indices 1..N are the bases players actually use.
            ArenaPreparer.stampAdditionalCopies(session.getPlugin(), world, base, grid, count + 1);
        } catch (Exception e) {
            return Utils.formatAdminError(MessageConfig.MAP_EDITOR_BUILD_GENERATE_FAILED.replace("%error%", e.getMessage()));
        }
        config.setBaseCount(count);
        session.getTarget().config().markPrepareWorldBuilt();
        session.setWorldConfirmed(true);
        session.setStamped(true);
        player.teleport(config.getBaseGrid().origin(0).toLocation(world));
        return Utils.formatAdminSuccess(MessageConfig.MAP_EDITOR_BUILD_BASE_GENERATED
                .replace("%count%", String.valueOf(count)));
    }
}
