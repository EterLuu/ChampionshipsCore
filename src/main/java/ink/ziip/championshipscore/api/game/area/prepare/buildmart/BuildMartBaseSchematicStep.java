package ink.ziip.championshipscore.api.game.area.prepare.buildmart;

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
        super("base_schematic", plugin -> schematic, Component.text("保存 0 号基地模板"),
                Component.text("在 0 号位置选中完整基地模板；它只用于复制，不会分配给参赛队伍"));
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
