package ink.ziip.championshipscore.api.game.bingo.task;

import ink.ziip.championshipscore.api.game.bingo.task.pool.Dimension;
import ink.ziip.championshipscore.api.game.bingo.util.BingoComponents;
import ink.ziip.championshipscore.api.game.bingo.util.MessageService;
import io.papermc.paper.advancement.AdvancementDisplay;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.advancement.Advancement;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

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

    /** The advancement's full display stack (keeps pattern data, e.g. the ominous banner patterns). */
    public @Nullable ItemStack displayIconStack() {
        if (advancement == null || advancement.getDisplay() == null) {
            return null;
        }
        return advancement.getDisplay().icon();
    }

    /**
     * Voluntary Exile and Hero of the Village both use a {@code white_banner} carrying the
     * ominous-banner pattern NBT; a bare material icon would lose the pattern, so the map uses a
     * hand-baked ominous banner sprite for both.
     */
    public boolean usesOminousBannerIcon() {
        return advancement != null
                && (advancement.key().value().equals("adventure/voluntary_exile")
                || advancement.key().value().equals("adventure/hero_of_the_village"));
    }

    /**
     * Local Brewery ({@code nether/brew_potion}) should read as the potion it makes you brew: the
     * instant-health potion, not the vanilla display's plain untyped potion.
     */
    public boolean usesHealingPotionIcon() {
        return advancement != null && advancement.key().value().equals("nether/brew_potion");
    }

    /** {@code PotionType} override for the chest-GUI item (instant health for Local Brewery). */
    public @Nullable org.bukkit.potion.PotionType displayPotionType() {
        return usesHealingPotionIcon() ? org.bukkit.potion.PotionType.HEALING : null;
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
