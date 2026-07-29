package ink.ziip.championshipscore.api.game.area.prepare.step;

import ink.ziip.championshipscore.api.game.area.BaseArea;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareSession;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareStep;
import ink.ziip.championshipscore.api.game.area.prepare.StepCaptureType;
import ink.ziip.championshipscore.util.Utils;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A list of serialized locations (e.g. {@code escapee-spawn-points}). Add appends the player's current
 * location (via {@link Utils#getLocationConfigString}); clear empties the list. Considered set when the
 * list is non-empty. Managed through a small sub-GUI ({@code ListStepGui}) rather than a direct click.
 */
public class ListStep extends PrepareStep {

    private final Predicate<BaseArea> emptyPredicate;
    private final java.util.function.BiConsumer<BaseArea, String> adder;
    private final Consumer<BaseArea> clearer;
    private final Function<BaseArea, Integer> counter;

    public ListStep(@NotNull String key, @NotNull Component name, @NotNull Component description,
                    @NotNull Material icon,
                    @NotNull Predicate<BaseArea> emptyPredicate,
                    @NotNull java.util.function.BiConsumer<BaseArea, String> adder,
                    @NotNull Consumer<BaseArea> clearer,
                    @NotNull Function<BaseArea, Integer> counter) {
        super(key, name, description, icon, StepCaptureType.LIST);
        this.emptyPredicate = emptyPredicate;
        this.adder = adder;
        this.clearer = clearer;
        this.counter = counter;
    }

    @Override
    public boolean isSet(PrepareSession session) {
        return session != null && !emptyPredicate.test(session.getArea());
    }

    @Override
    public String listAdd(@NotNull PrepareSession session, @NotNull Player player) {
        adder.accept(session.getArea(), Utils.getLocationConfigString(player.getLocation()));
        session.getArea().getGameConfig().saveOptions();
        return Utils.formatAdminSuccess("已添加点位 #696969• #ededed当前 #fff566" + listCount(session) + " #ededed个");
    }

    @Override
    public String listClear(@NotNull PrepareSession session, @NotNull Player player) {
        clearer.accept(session.getArea());
        session.getArea().getGameConfig().saveOptions();
        return Utils.formatAdminSuccess("已清空点位列表。");
    }

    @Override
    public int listCount(@NotNull PrepareSession session) {
        return counter.apply(session.getArea());
    }
}
