package ink.ziip.championshipscore.api.game.area.prepare.step;

import ink.ziip.championshipscore.api.game.area.prepare.PrepareSession;
import ink.ziip.championshipscore.api.game.config.FourArenaConfig;
import ink.ziip.championshipscore.api.game.setup.SetupTarget;
import ink.ziip.championshipscore.configuration.config.message.GuiConfig;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.platform.bukkit.text.LegacyText;
import ink.ziip.championshipscore.presentation.text.CoreMessages;

import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/** Capturing the source also records its boundary and invalidates the generated revision. */
public final class FourArenaSchematicStep extends SchematicStep {
    public FourArenaSchematicStep(SetupTarget target) {
        super(
                plugin -> templateFile(target),
                LegacyText.component(
                        GuiConfig.text(
                                "map-editor.games.four-arena.menus.prepare.items.save-template.title")),
                LegacyText.component(
                        GuiConfig.line(
                                "map-editor.games.four-arena.menus.prepare.items.save-template.lore",
                                0)));
    }

    static File templateFile(SetupTarget target) {
        return new File(
                target.plugin().getDataFolder(),
                target.config().getFolderName() + "schematics/" + target.name() + "/arena.schem");
    }

    @Override
    public String capture(@NotNull PrepareSession session, @NotNull Player player) {
        if (!session.getTarget().canSaveMap())
            return CoreMessages.formatAdminError(
                    MessageConfig.MAP_EDITOR_STEP_ARENA_INSTANCE_RUNNING);
        return super.capture(session, player);
    }

    @Override
    protected void onSchematicSaved(
            @NotNull PrepareSession session, @NotNull Player player, @NotNull File file) {
        Vector[] selection =
                session.getPlugin().getWorldEditManager().getPlayerSelection(player, true);
        ((FourArenaConfig) session.getTarget().config())
                .setTemplateBounds(selection[0], selection[1]);
        session.setStamped(false);
    }
}
