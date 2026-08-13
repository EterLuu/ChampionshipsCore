package ink.ziip.championshipscore.api.game.area.prepare.buildmart;

import ink.ziip.championshipscore.configuration.config.message.GuiConfig;

import ink.ziip.championshipscore.api.game.area.prepare.PrepareSession;
import ink.ziip.championshipscore.api.game.area.prepare.step.SchematicStep;
import ink.ziip.championshipscore.api.game.buildmart.BuildMartConfig;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/** Saves the base schematic together with the authoritative in-world minimum corner of copy 0. */
final class BuildMartBaseSchematicStep extends SchematicStep {
    private final File schematic;

    BuildMartBaseSchematicStep(@NotNull File schematic) {
        super("base_schematic", plugin -> schematic, Component.text(GuiConfig.text("map-editor.games.build-mart.steps.base-template.save-base-0-template")),
                Component.text(GuiConfig.text("map-editor.games.build-mart.steps.base-template.template-base-selection-hint")));
        this.schematic = schematic;
    }

    @Override
    public boolean isSet(PrepareSession session) {
        return session != null && schematic.isFile()
                && config(session).getBaseSourceOrigin() != null;
    }

    @Override
    protected void onSchematicSaved(@NotNull PrepareSession session, @NotNull Player player,
                                    @NotNull File file) {
        Vector selectionMinimum = session.getPlugin().getWorldEditManager()
                .getPlayerSelection(player, true)[0];
        config(session).recordBaseTemplateOrigin(selectionMinimum);
        session.setStamped(false);
    }

    private static BuildMartConfig config(@NotNull PrepareSession session) {
        return (BuildMartConfig) session.getTarget().config();
    }
}
