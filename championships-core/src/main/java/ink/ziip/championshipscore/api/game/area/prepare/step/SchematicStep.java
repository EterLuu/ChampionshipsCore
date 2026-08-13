package ink.ziip.championshipscore.api.game.area.prepare.step;

import ink.ziip.championshipscore.configuration.config.message.GuiConfig;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareSession;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareStep;
import ink.ziip.championshipscore.api.game.area.prepare.StepCaptureType;
import ink.ziip.championshipscore.util.Utils;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.function.Function;

/**
 * Saves the player's current WorldEdit selection to a fixed schematic file (e.g.
 * {@code parkourtag/schematics/arena.schem}). Considered set once the file exists on disk.
 */
public class SchematicStep extends PrepareStep {

    private final Function<ChampionshipsCore, File> fileResolver;

    public SchematicStep(@NotNull Function<ChampionshipsCore, File> fileResolver,
                         @NotNull Component name, @NotNull Component description) {
        this("schematic", fileResolver, name, description);
    }

    public SchematicStep(@NotNull String key, @NotNull Function<ChampionshipsCore, File> fileResolver,
                         @NotNull Component name, @NotNull Component description) {
        super(key, name, description, Material.STRUCTURE_BLOCK, StepCaptureType.SCHEMATIC);
        this.fileResolver = fileResolver;
    }

    private File file(PrepareSession session) {
        return fileResolver.apply(session.getPlugin());
    }

    @Override
    public boolean isSet(PrepareSession session) {
        // Legacy/published maps already have a complete physical world even if their original source
        // schematic predates per-map asset storage. New drafts set world-built=false and therefore still
        // require the explicit schematic -> stamp sequence.
        return session != null && (file(session).isFile()
                || session.getTarget().config().isPrepareWorldBuilt());
    }

    @Override
    public String capture(@NotNull PrepareSession session, @NotNull Player player) {
        File file = file(session);
        File parent = file.getParentFile();
        if (parent != null) parent.mkdirs();
        try {
            session.getPlugin().getWorldEditManager().saveSelectionAsSchematic(player, file);
            onSchematicSaved(session, player, file);
        } catch (Exception e) {
            String detail = e.getMessage();
            if (detail == null || detail.isBlank()) detail = e.getClass().getSimpleName();
            return Utils.formatAdminError(GuiConfig.text("map-editor.steps.schematic.failed-to-save-template-please-check-worldedit-selection") + detail);
        }
        session.markDirty();
        return Utils.formatAdminSuccess(GuiConfig.text("map-editor.steps.schematic.venue-template-saved") + file.getName());
    }

    /** Allows game-specific steps to persist metadata that cannot be recovered reliably from the file. */
    protected void onSchematicSaved(@NotNull PrepareSession session, @NotNull Player player,
                                    @NotNull File file) throws Exception {
    }
}
