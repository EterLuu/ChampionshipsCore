package ink.ziip.championshipscore.api.game.area.prepare.step;

import ink.ziip.championshipscore.api.game.area.prepare.PrepareSession;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareStep;
import ink.ziip.championshipscore.api.game.area.prepare.StepCaptureType;
import ink.ziip.championshipscore.api.game.setup.SetupTarget;
import ink.ziip.championshipscore.util.Utils;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.function.BiConsumer;
import java.util.function.Predicate;

/**
 * Captures the player's current WorldEdit selection (pos1/pos2) into a pair of config fields, e.g.
 * {@code area-pos1}/{@code area-pos2}. Requires a selection; if none is made, returns a prompt to make one.
 */
public class WeSelectionStep extends PrepareStep {

    private final Predicate<SetupTarget> setPredicate;
    private final BiConsumer<SetupTarget, Vector[]> setter; // [pos1, pos2]
    private final String doneMessage;

    public WeSelectionStep(@NotNull String key, @NotNull Component name, @NotNull Component description,
                           @NotNull Material icon,
                           @NotNull Predicate<SetupTarget> setPredicate,
                           @NotNull BiConsumer<SetupTarget, Vector[]> setter,
                           @NotNull String doneMessage) {
        super(key, name, description, icon, StepCaptureType.WE_SELECTION);
        this.setPredicate = setPredicate;
        this.setter = setter;
        this.doneMessage = doneMessage;
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
            return Utils.formatAdminError("请先用 WorldEdit 选取两个端点。");
        }
        setter.accept(session.getTarget(), selection);
        session.getTarget().config().saveOptions();
        return doneMessage;
    }
}
