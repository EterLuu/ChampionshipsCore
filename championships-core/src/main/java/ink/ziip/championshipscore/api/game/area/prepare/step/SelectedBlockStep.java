package ink.ziip.championshipscore.api.game.area.prepare.step;

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

/** Captures an exact one-block WorldEdit selection as a Location. */
public class SelectedBlockStep extends PrepareStep {
    private final Predicate<SetupTarget> setPredicate;
    private final BiConsumer<SetupTarget, Location> setter;

    public SelectedBlockStep(String key, Component name, Component description, Material icon,
                             Predicate<SetupTarget> setPredicate, BiConsumer<SetupTarget, Location> setter) {
        super(key, name, description, icon, StepCaptureType.WE_SELECTION);
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
            return Utils.formatAdminError("请先用 WorldEdit 选中一个方块。");
        }
        Vector min = Vector.getMinimum(selection[0], selection[1]);
        Vector max = Vector.getMaximum(selection[0], selection[1]);
        if (min.getBlockX() != max.getBlockX() || min.getBlockY() != max.getBlockY()
                || min.getBlockZ() != max.getBlockZ())
            return Utils.formatAdminError("选区必须恰好为一个方块。");
        setter.accept(session.getTarget(), new Location(player.getWorld(),
                min.getBlockX(), min.getBlockY(), min.getBlockZ()));
        session.markDirty();
        return Utils.formatAdminSuccess("已记录选中方块。");
    }
}
