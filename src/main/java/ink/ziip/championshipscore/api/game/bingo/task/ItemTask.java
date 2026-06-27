package ink.ziip.championshipscore.api.game.bingo.task;

import ink.ziip.championshipscore.api.game.bingo.task.pool.Dimension;
import ink.ziip.championshipscore.api.game.bingo.util.BingoComponents;
import ink.ziip.championshipscore.api.game.bingo.util.MessageService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;

import java.util.Objects;

/** Collect {@code count} of {@code itemType}. */
public record ItemTask(Material itemType, int count, Dimension dimension) implements TaskData {

    public ItemTask(Material itemType) {
        this(itemType, 1);
    }

    public ItemTask(Material itemType, int count) {
        this(itemType, count, Dimension.OVERWORLD);
    }

    public ItemTask(Material itemType, int count, Dimension dimension) {
        this.itemType = itemType;
        this.count = Math.clamp(count, 1, 64);
        this.dimension = dimension == null ? Dimension.OVERWORLD : dimension;
    }

    @Override
    public TaskType getType() {
        return TaskType.ITEM;
    }

    @Override
    public String objectiveId() {
        return itemType.name();
    }

    @Override
    public Component getName() {
        return Component.text().color(NamedTextColor.YELLOW)
                .append(BingoComponents.itemName(itemType)).build();
    }

    @Override
    public Component[] getItemDescription() {
        return new Component[]{
                MessageService.global().component("task.collect")
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
        return itemType;
    }

    @Override
    public int getRequiredAmount() {
        return count;
    }

    @Override
    public TaskData setRequiredAmount(int newAmount) {
        return new ItemTask(itemType, newAmount, dimension);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ItemTask itemTask = (ItemTask) o;
        return itemType.equals(itemTask.itemType);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(itemType);
    }

    @Override
    public boolean isTaskEqual(TaskData other) {
        return other instanceof ItemTask itemTask && itemType.equals(itemTask.itemType);
    }
}
