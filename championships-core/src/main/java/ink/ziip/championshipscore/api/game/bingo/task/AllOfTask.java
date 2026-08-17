package ink.ziip.championshipscore.api.game.bingo.task;

import ink.ziip.championshipscore.api.game.bingo.task.pool.Dimension;
import ink.ziip.championshipscore.api.game.bingo.util.BingoComponents;
import ink.ziip.championshipscore.api.game.bingo.util.MessageService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Collect <em>every</em> material in {@code items} at the same time - the "complete the set" sibling
 * of {@link OneOfTask}. Rendered as a plain item cell with the representative item centred and a
 * yellow "ALL" corner stamp (the ALL twin of one_of's ANY stamp), and completion is checked from the
 * player's full inventory whenever any member item is observed.
 *
 * <p>{@link #display} is the representative icon and is allowed to sit outside the member set (e.g.
 * the mushroom pair shows the red mushroom); {@link #objectiveId()} is {@code all:<display material>}
 * so the tier list can rank each complete-set objective separately. {@link #label} optionally names a
 * {@code task.family.*} localization token for the "collect all" title.
 */
public record AllOfTask(Set<Material> items, Material display, String label, int count, Dimension dimension)
        implements TaskData {

    public AllOfTask(Set<Material> items, Material display, String label, int count, Dimension dimension) {
        // Sort the set for deterministic icon/equality behaviour and freeze it.
        LinkedHashSet<Material> sorted = new LinkedHashSet<>();
        if (items != null) {
            items.stream().filter(Objects::nonNull)
                    .sorted(Comparator.comparing(Material::name))
                    .forEach(sorted::add);
        }
        if (sorted.isEmpty()) throw new IllegalArgumentException("AllOfTask requires at least one item");
        this.items = Collections.unmodifiableSet(sorted);
        this.display = display != null ? display : sorted.iterator().next();
        this.label = label == null || label.isBlank() ? null : label.trim().toLowerCase(Locale.ROOT);
        this.count = Math.clamp(count, 1, 64);
        this.dimension = dimension == null ? Dimension.OVERWORLD : dimension;
    }

    @Override
    public TaskType getType() {
        return TaskType.ITEM_SET;
    }

    @Override
    public String objectiveId() {
        return "all:" + display.name();
    }

    @Override
    public Component getName() {
        MessageService msg = MessageService.global();
        // Localization token: the configured label wins, then the members' shared family affix
        // (RED_MUSHROOM + BROWN_MUSHROOM -> "mushroom"), then the display item's own enum token.
        String token = label;
        if (token == null || token.isEmpty() || !msg.has("task.family." + token)) {
            token = OneOfTask.familyToken(items);
        }
        if (token == null || token.isEmpty() || !msg.has("task.family." + token)) {
            token = display.name().toLowerCase(Locale.ROOT);
        }
        String familyKey = "task.family." + token;
        Component name = msg.has(familyKey)
                ? Component.text(msg.tr(familyKey))
                : BingoComponents.itemName(display);
        return Component.text().color(NamedTextColor.YELLOW)
                .append(msg.component("task.all_of_prefix"))
                .append(name)
                .build();
    }

    @Override
    public Component[] getItemDescription() {
        MessageService msg = MessageService.global();
        List<Component> lore = new ArrayList<>(items.size() + 2);
        lore.add(msg.component("task.all_of"));
        lore.add(msg.component("task.all_of_includes"));
        for (Material member : items) {
            lore.add(Component.text().color(NamedTextColor.GRAY)
                    .append(Component.text("- "))
                    .append(BingoComponents.itemName(member))
                    .build());
        }
        return lore.toArray(Component[]::new);
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
        return new AllOfTask(items, display, label, newAmount, dimension);
    }

    @Override
    public boolean isTaskEqual(TaskData other) {
        return other instanceof AllOfTask set && items.equals(set.items);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AllOfTask that = (AllOfTask) o;
        return items.equals(that.items);
    }

    @Override
    public int hashCode() {
        return items.hashCode();
    }
}
