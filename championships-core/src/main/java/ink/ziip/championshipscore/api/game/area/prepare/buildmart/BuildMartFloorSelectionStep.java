package ink.ziip.championshipscore.api.game.area.prepare.buildmart;

import ink.ziip.championshipscore.configuration.config.message.GuiConfig;

import ink.ziip.championshipscore.api.game.area.prepare.PrepareSession;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareStep;
import ink.ziip.championshipscore.api.game.area.prepare.StepCaptureType;
import ink.ziip.championshipscore.api.game.setup.SetupTarget;
import ink.ziip.championshipscore.util.Utils;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.function.BiConsumer;
import java.util.function.Predicate;

/** Captures the minimum block corner of an exact 7x1x7 Build Mart floor selection. */
final class BuildMartFloorSelectionStep extends PrepareStep {
    private final Predicate<SetupTarget> setPredicate;
    private final BiConsumer<SetupTarget, Location> setter;

    BuildMartFloorSelectionStep(@NotNull String key, @NotNull Component name,
                                @NotNull Material icon, @NotNull Predicate<SetupTarget> setPredicate,
                                @NotNull BiConsumer<SetupTarget, Location> setter) {
        super(key, name, Component.text(GuiConfig.text("map-editor.games.build-mart.steps.floor-selection.precisely-select-the-7x1x7-floor-with-worldedit")), icon,
                StepCaptureType.WE_SELECTION);
        this.setPredicate = setPredicate;
        this.setter = setter;
    }

    @Override
    public boolean isSet(PrepareSession session) {
        return session != null && setPredicate.test(session.getTarget());
    }

    @Override
    public String capture(@NotNull PrepareSession session, @NotNull Player player) {
        Vector[] selection;
        try {
            selection = session.getPlugin().getWorldEditManager().getPlayerSelection(player, true);
        } catch (Exception e) {
            return Utils.formatAdminError(GuiConfig.text("map-editor.games.build-mart.steps.floor-selection.please-use-worldedit-to-select-the-7x1x7-floor-first"));
        }
        Vector min = Vector.getMinimum(selection[0], selection[1]);
        Vector max = Vector.getMaximum(selection[0], selection[1]);
        if (max.getBlockX() - min.getBlockX() != 6
                || max.getBlockY() != min.getBlockY()
                || max.getBlockZ() - min.getBlockZ() != 6) {
            return Utils.formatAdminError(GuiConfig.text("map-editor.games.build-mart.steps.floor-selection.the-selection-must-be-exactly-7x1x7-floor"));
        }
        setter.accept(session.getTarget(), new Location(player.getWorld(),
                min.getBlockX(), min.getBlockY(), min.getBlockZ()));
        session.markDirty();
        return Utils.formatAdminSuccess(GuiConfig.text("map-editor.games.build-mart.steps.floor-selection.the-lowest-corner-of-the-floor-has-been-recorded"));
    }
}
