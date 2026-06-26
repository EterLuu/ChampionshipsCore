package ink.ziip.championshipscore.api.game.bingo.task;

import ink.ziip.championshipscore.api.game.bingo.task.pool.Dimension;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;

import java.util.Set;

/**
 * Immutable description of a single bingo objective. Concrete kinds: {@link ItemTask},
 * {@link AdvancementTask}, {@link StatisticTask}, {@link OneOfTask}. Completion state lives on
 * {@link GameTask}, not here.
 */
public sealed interface TaskData permits ItemTask, AdvancementTask, StatisticTask, OneOfTask {
    enum TaskType {
        ITEM("item"),
        STATISTIC("statistic"),
        ADVANCEMENT("advancement"),
        ITEM_SET("item_set");

        public final String id;

        TaskType(String id) {
            this.id = id;
        }
    }

    TaskType getType();

    /**
     * Stable identifier matching {@link ink.ziip.championshipscore.api.game.bingo.task.pool.PoolEntrySpec#objectiveId()},
     * used by tier lists and tag rules to target this objective. Items use the bare material name;
     * other kinds carry a prefix ({@code mine:}, {@code craft:}, {@code kill:}, {@code stat:},
     * {@code advancement:}, {@code set:}).
     */
    String objectiveId();

    /** Display name (translatable so it renders in each viewer's locale). */
    Component getName();

    /** Longer description for chat output. */
    Component getChatDescription();

    /** Lore lines for the card item. */
    Component[] getItemDescription();

    boolean isTaskEqual(TaskData other);

    boolean shouldItemGlow();

    /** Which dimension this task belongs to; used for filtering. */
    Dimension dimension();

    Material getDisplayMaterial(CardDisplayInfo context);

    int getRequiredAmount();

    /** @return a copy with the new required amount. */
    TaskData setRequiredAmount(int newAmount);

    /** True when this task's dimension is in the disabled set. */
    default boolean inAnyDimension(Set<Dimension> dimensions) {
        return dimensions.contains(dimension());
    }
}
