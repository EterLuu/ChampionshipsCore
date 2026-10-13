package ink.ziip.championshipscore.api.game.area.prepare.step;

import ink.ziip.championshipscore.api.game.area.prepare.PrepareSession;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareStep;
import ink.ziip.championshipscore.api.game.area.prepare.StepCaptureType;
import ink.ziip.championshipscore.api.game.arena.ArenaGrid;
import ink.ziip.championshipscore.api.game.arena.ArenaPreparer;
import ink.ziip.championshipscore.api.game.config.FourArenaConfig;
import ink.ziip.championshipscore.configuration.config.message.GuiConfig;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.platform.bukkit.text.LegacyText;
import ink.ziip.championshipscore.presentation.text.CoreMessages;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/** Generates the three physical replicas without replacing the hand-built source. */
public final class FourArenaStampStep extends PrepareStep {
    public FourArenaStampStep() {
        super(
                "stamp",
                LegacyText.component(
                        GuiConfig.text(
                                "map-editor.games.four-arena.menus.prepare.items.stamp.title")),
                LegacyText.component(
                        GuiConfig.line(
                                "map-editor.games.four-arena.menus.prepare.items.stamp.lore", 0)),
                Material.DISPENSER,
                StepCaptureType.STAMP);
    }

    @Override
    public boolean isSet(PrepareSession session) {
        return session != null
                && ((FourArenaConfig) session.getTarget().config()).isCopyLayoutCurrent();
    }

    @Override
    public String stateText(PrepareSession session) {
        return GuiConfig.text(
                "map-editor.menus.step-list.items.step.states."
                        + (isSet(session) ? "stamped" : "unstamped")
                        + ".title");
    }

    @Override
    public String stamp(@NotNull PrepareSession session, @NotNull Player player, int count) {
        if (count != FourArenaConfig.ARENA_COUNT)
            return CoreMessages.formatAdminError(
                    MessageConfig.codeText("four-arena-config.four-required"));
        if (!session.getTarget().canSaveMap())
            return CoreMessages.formatAdminError(
                    MessageConfig.MAP_EDITOR_STEP_ARENA_INSTANCE_RUNNING);
        File file = FourArenaSchematicStep.templateFile(session.getTarget());
        if (!file.isFile())
            return CoreMessages.formatAdminError(
                    MessageConfig.MAP_EDITOR_STEP_ARENA_TEMPLATE_MISSING);
        World world = Bukkit.getWorld(session.getTarget().worldName());
        if (world == null)
            return CoreMessages.formatAdminError(
                    MessageConfig.MAP_EDITOR_STEP_WORLD_NOT_LOADED.replace(
                            "%world%", session.getTarget().worldName()));
        FourArenaConfig config = (FourArenaConfig) session.getTarget().config();
        try {
            Vector size = session.getPlugin().getWorldEditManager().getSchematicDimensions(file);
            ArenaGrid next = config.planCopyGrid(size);
            Vector expected =
                    config.getArenaMax()
                            .clone()
                            .subtract(config.getArenaMin())
                            .add(new Vector(1, 1, 1));
            if (!size.equals(expected))
                throw new IllegalArgumentException(
                        MessageConfig.codeText("four-arena-config.template-mismatch"));
            ArenaPreparer.validateNonOverlappingCopies(next, count, size);
            if (config.hasCopyLayout()) {
                BoundingBox source = ArenaPreparer.copyBoxes(next, 1, size).getFirst();
                if (ArenaPreparer.copyBoxes(config.getCopyGrid(), count, config.getCopySize())
                        .subList(1, count)
                        .stream()
                        .anyMatch(source::overlaps))
                    throw new IllegalArgumentException(
                            MessageConfig.codeText("four-arena-config.source-overlap"));
            }
            config.setCopiesGenerated(false);
            session.setStamped(false);
            session.markDirty();
            if (config.hasCopyLayout())
                ArenaPreparer.clearAdditionalCopies(
                        session.getPlugin(),
                        world,
                        config.getCopyGrid(),
                        count,
                        config.getCopySize());
            ArenaPreparer.stampAdditionalCopies(session.getPlugin(), world, file, next, count);
            config.recordCopyLayout(next, size);
            config.markPrepareWorldBuilt();
        } catch (Exception failure) {
            return CoreMessages.formatAdminError(
                    MessageConfig.MAP_EDITOR_STEP_ARENA_GENERATE_FAILED.replace(
                            "%detail%", String.valueOf(failure.getMessage())));
        }
        session.setWorldConfirmed(true);
        session.setStamped(true);
        return CoreMessages.formatAdminSuccess(
                MessageConfig.MAP_EDITOR_STEP_ARENA_TOTAL_SET
                        .replace("%count%", "4")
                        .replace("%copies%", "3"));
    }
}
