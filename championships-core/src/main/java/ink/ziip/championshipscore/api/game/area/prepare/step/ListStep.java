package ink.ziip.championshipscore.api.game.area.prepare.step;

import ink.ziip.championshipscore.configuration.config.message.GuiConfig;

import ink.ziip.championshipscore.api.game.area.prepare.PrepareSession;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareStep;
import ink.ziip.championshipscore.api.game.area.prepare.StepCaptureType;
import ink.ziip.championshipscore.api.game.setup.SetupTarget;
import ink.ziip.championshipscore.util.Utils;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.ArrayList;
import java.util.List;

/**
 * A list of serialized locations (e.g. {@code escapee-spawn-points}). Add appends the player's current
 * location (via {@link Utils#getLocationConfigString}); clear empties the list. Considered set when the
 * list is non-empty. Managed through a small sub-GUI ({@code ListStepGui}) rather than a direct click.
 */
public class ListStep extends PrepareStep {

    private final Predicate<SetupTarget> emptyPredicate;
    private final Function<SetupTarget, List<String>> getter;
    private final BiConsumer<SetupTarget, List<String>> setter;
    private final BiConsumer<SetupTarget, String> adder;
    private final Consumer<SetupTarget> clearer;
    private final Function<SetupTarget, Integer> counter;

    public ListStep(@NotNull String key, @NotNull Component name, @NotNull Component description,
                    @NotNull Material icon,
                    @NotNull Function<SetupTarget, List<String>> getter,
                    @NotNull BiConsumer<SetupTarget, List<String>> setter,
                    @NotNull Predicate<SetupTarget> emptyPredicate,
                    @NotNull BiConsumer<SetupTarget, String> adder,
                    @NotNull Consumer<SetupTarget> clearer,
                    @NotNull Function<SetupTarget, Integer> counter) {
        super(key, name, description, icon, StepCaptureType.LIST);
        this.getter = getter;
        this.setter = setter;
        this.emptyPredicate = emptyPredicate;
        this.adder = adder;
        this.clearer = clearer;
        this.counter = counter;
    }

    @Override
    public boolean isSet(PrepareSession session) {
        return session != null && !emptyPredicate.test(session.getTarget());
    }

    @Override
    public String listAdd(@NotNull PrepareSession session, @NotNull Player player) {
        adder.accept(session.getTarget(), Utils.getLocationConfigString(Utils.centerOnBlock(player.getLocation())));
        session.markDirty();
        return Utils.formatAdminSuccess(GuiConfig.text("map-editor.steps.list-editor.points-added-current") + listCount(session) + GuiConfig.text("map-editor.steps.list-editor.item-count-suffix"));
    }

    @Override
    public String listClear(@NotNull PrepareSession session, @NotNull Player player) {
        clearer.accept(session.getTarget());
        session.markDirty();
        return Utils.formatAdminSuccess(GuiConfig.text("map-editor.steps.list-editor.point-list-cleared"));
    }

    @Override
    public int listCount(@NotNull PrepareSession session) {
        return counter.apply(session.getTarget());
    }

    @Override
    public @NotNull List<ListEntry> listEntries(@NotNull PrepareSession session) {
        List<ListEntry> entries = new ArrayList<>();
        List<String> values = values(session);
        for (int i = 0; i < values.size(); i++) {
            String value = values.get(i);
            entries.add(new ListEntry(GuiConfig.text("map-editor.steps.list-editor.point") + (i + 1), List.of(formatLocation(value))));
        }
        return entries;
    }

    @Override
    public String listEdit(@NotNull PrepareSession session, @NotNull Player player, int index) {
        List<String> values = values(session);
        if (index < 0 || index >= values.size()) return null;
        values.set(index, Utils.getLocationConfigString(Utils.centerOnBlock(player.getLocation())));
        setter.accept(session.getTarget(), values);
        session.markDirty();
        return Utils.formatAdminSuccess(GuiConfig.text("map-editor.steps.list-editor.updated") + (index + 1) + GuiConfig.text("map-editor.steps.list-editor.points"));
    }

    @Override
    public String listSetOrder(@NotNull PrepareSession session, @NotNull Player player,
                               int index, int newOrder) {
        List<String> values = values(session);
        if (index < 0 || index >= values.size() || newOrder < 1 || newOrder > values.size())
            return Utils.formatAdminError(GuiConfig.text("map-editor.steps.list-editor.the-serial-number-must-be-between-1-and") + values.size() + GuiConfig.text("map-editor.steps.list-editor.range-end-suffix"));
        String value = values.remove(index);
        values.add(newOrder - 1, value);
        setter.accept(session.getTarget(), values);
        session.markDirty();
        return Utils.formatAdminSuccess(GuiConfig.text("map-editor.steps.list-editor.the-point-has-been-adjusted-to-the") + newOrder + GuiConfig.text("map-editor.steps.list-editor.item-suffix"));
    }

    @Override
    public String listRemove(@NotNull PrepareSession session, @NotNull Player player, int index) {
        List<String> values = values(session);
        if (index < 0 || index >= values.size()) return null;
        values.remove(index);
        setter.accept(session.getTarget(), values);
        session.markDirty();
        return Utils.formatAdminSuccess(GuiConfig.text("map-editor.steps.list-editor.deleted") + (index + 1) + GuiConfig.text("map-editor.steps.list-editor.points"));
    }

    private List<String> values(@NotNull PrepareSession session) {
        return values(getter.apply(session.getTarget()));
    }

    private static List<String> values(List<String> values) {
        return values == null ? new ArrayList<>() : new ArrayList<>(values);
    }

    private static String formatLocation(String value) {
        try {
            Location location = Utils.getLocation(value);
            String world = location.getWorld() == null ? "?" : location.getWorld().getName();
            return world + " @ " + format(location.getX()) + ", " + format(location.getY()) + ", "
                    + format(location.getZ());
        } catch (Exception ignored) {
            return value;
        }
    }

    private static String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }
}
