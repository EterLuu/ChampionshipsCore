package ink.ziip.championshipscore.api.game.bingo.task;

import ink.ziip.championshipscore.api.game.bingo.task.pool.Dimension;
import ink.ziip.championshipscore.api.game.bingo.util.BingoComponents;
import ink.ziip.championshipscore.api.game.bingo.util.MessageService;
import io.papermc.paper.advancement.AdvancementDisplay;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.advancement.Advancement;

/** Complete the given {@link Advancement}. */
public record AdvancementTask(Advancement advancement, Dimension dimension) implements TaskData {

    public AdvancementTask(Advancement advancement) {
        this(advancement, Dimension.OVERWORLD);
    }

    public AdvancementTask {
        if (dimension == null) dimension = Dimension.OVERWORLD;
    }

    @Override
    public TaskType getType() {
        return TaskType.ADVANCEMENT;
    }

    @Override
    public String objectiveId() {
        return "advancement:" + (advancement == null ? "unknown" : advancement.key().value());
    }

    @Override
    public Component getName() {
        var builder = Component.text().append(Component.text("["))
                .color(NamedTextColor.GREEN);
        if (advancement == null) {
            builder.append(MessageService.global().component("task.unknown_advancement"));
        } else {
            builder.append(BingoComponents.advancementTitle(advancement));
        }
        builder.append(Component.text("]"));
        return builder.build();
    }

    @Override
    public Component[] getItemDescription() {
        return new Component[]{
                MessageService.global().component("task.advancement")
        };
    }

    // Advancement descriptions can contain newlines, so they only go to chat, never item names/lore.
    @Override
    public Component getChatDescription() {
        if (advancement == null) {
            return MessageService.global().component("task.unknown_advancement").color(NamedTextColor.DARK_AQUA);
        }
        return BingoComponents.advancementDescription(advancement).color(NamedTextColor.DARK_AQUA);
    }

    @Override
    public boolean shouldItemGlow() {
        return true;
    }

    @Override
    public Material getDisplayMaterial(CardDisplayInfo context) {
        Material icon = displayIcon();
        if (context.advancementDisplay() == TaskDisplayMode.GENERIC_TASK_ITEMS || icon == Material.AIR) {
            return Material.FILLED_MAP;
        }
        return icon;
    }

    /** Frame style (task/goal/challenge) used to pick the matching widget backdrop on the card. */
    public AdvancementDisplay.Frame frameType() {
        if (advancement == null || advancement.getDisplay() == null) {
            return AdvancementDisplay.Frame.TASK;
        }
        return advancement.getDisplay().frame();
    }

    private Material displayIcon() {
        if (advancement == null || advancement.getDisplay() == null) {
            return Material.AIR;
        }
        return advancement.getDisplay().icon().getType();
    }

    @Override
    public int getRequiredAmount() {
        return 1;
    }

    @Override
    public TaskData setRequiredAmount(int newAmount) {
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AdvancementTask that = (AdvancementTask) o;
        if (advancement == null) return that.advancement == null;
        return that.advancement != null && advancement.key().equals(that.advancement.key());
    }

    @Override
    public int hashCode() {
        return advancement == null ? 0 : advancement.key().hashCode();
    }

    @Override
    public boolean isTaskEqual(TaskData other) {
        return this.equals(other);
    }
}
