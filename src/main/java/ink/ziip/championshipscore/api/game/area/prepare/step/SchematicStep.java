package ink.ziip.championshipscore.api.game.area.prepare.step;

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
        super("schematic", name, description, Material.STRUCTURE_BLOCK, StepCaptureType.SCHEMATIC);
        this.fileResolver = fileResolver;
    }

    private File file(PrepareSession session) {
        return fileResolver.apply(session.getPlugin());
    }

    @Override
    public boolean isSet(PrepareSession session) {
        return session != null && file(session).isFile();
    }

    @Override
    public String capture(@NotNull PrepareSession session, @NotNull Player player) {
        File file = file(session);
        File parent = file.getParentFile();
        if (parent != null) parent.mkdirs();
        try {
            session.getPlugin().getWorldEditManager().saveSelectionAsSchematic(player, file);
        } catch (Exception e) {
            return Utils.formatAdminError("保存模板失败，请检查 WorldEdit 选区：#fff566" + e.getMessage());
        }
        return Utils.formatAdminSuccess("已保存场地模板：#fff566" + file.getName());
    }
}
