package ink.ziip.championshipscore.api.game.bingo.task;

import ink.ziip.championshipscore.api.game.bingo.task.pool.Dimension;
import ink.ziip.championshipscore.api.game.bingo.util.BingoComponents;
import ink.ziip.championshipscore.api.game.bingo.util.MessageService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Collect {@code count} of <em>any one</em> material in {@code items}. Completing a single member
 * satisfies the whole cell. The container/chest UI shows a single fixed representative
 * ({@link #display}); the map card paints a composite built from up to four evenly-spaced members
 * (see {@link #iconMembers()}).
 */
public record OneOfTask(Set<Material> items, Material display, String name, int count, Dimension dimension)
        implements TaskData {

    public OneOfTask(Set<Material> items, Material display, String name, int count, Dimension dimension) {
        // Sort the set for deterministic icon/equality behaviour and freeze it.
        LinkedHashSet<Material> sorted = new LinkedHashSet<>();
        if (items != null) {
            items.stream().filter(java.util.Objects::nonNull)
                    .sorted(Comparator.comparing(Material::name))
                    .forEach(sorted::add);
        }
        if (sorted.isEmpty()) throw new IllegalArgumentException("OneOfTask requires at least one item");
        this.items = java.util.Collections.unmodifiableSet(sorted);
        this.display = display != null && sorted.contains(display) ? display : sorted.iterator().next();
        this.name = name == null || name.isBlank() ? null : name;
        this.count = Math.clamp(count, 1, 64);
        this.dimension = dimension == null ? Dimension.OVERWORLD : dimension;
    }

    @Override
    public TaskType getType() {
        return TaskType.ITEM_SET;
    }

    @Override
    public String objectiveId() {
        return "set:" + (name != null ? name : display.name());
    }

    /** Up to four members, evenly spaced across the sorted set, used to build the composite map icon. */
    public List<Material> iconMembers() {
        List<Material> sorted = new ArrayList<>(items);
        int n = sorted.size();
        if (n <= 4) return sorted;
        List<Material> out = new ArrayList<>(4);
        for (int i = 0; i < 4; i++) {
            out.add(sorted.get((int) ((long) i * n / 4)));
        }
        return out;
    }

    @Override
    public Component getName() {
        if (name != null) {
            return Component.text().color(NamedTextColor.YELLOW).append(Component.text(name)).build();
        }
        // No explicit name: fall back to "Any: <representative item>".
        return Component.text().color(NamedTextColor.YELLOW)
                .append(Component.text(MessageService.global().tr("task.one_of_prefix")))
                .append(BingoComponents.itemName(display))
                .build();
    }

    @Override
    public Component[] getItemDescription() {
        return new Component[]{
                MessageService.global().component("task.one_of", items.size())
        };
    }

    @Override
    public Component getChatDescription() {
        return Component.text().append(getItemDescription()).build();
    }

    @Override
    public boolean shouldItemGlow() {
        return false;
    }

    @Override
    public Material getDisplayMaterial(CardDisplayInfo context) {
        return display;
    }

    @Override
    public int getRequiredAmount() {
        return count;
    }

    @Override
    public TaskData setRequiredAmount(int newAmount) {
        return new OneOfTask(items, display, name, newAmount, dimension);
    }

    @Override
    public boolean isTaskEqual(TaskData other) {
        return other instanceof OneOfTask set && items.equals(set.items);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OneOfTask that = (OneOfTask) o;
        return items.equals(that.items);
    }

    @Override
    public int hashCode() {
        return items.hashCode();
    }
}
