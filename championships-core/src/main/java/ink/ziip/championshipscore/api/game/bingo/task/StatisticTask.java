package ink.ziip.championshipscore.api.game.bingo.task;

import ink.ziip.championshipscore.api.game.bingo.task.pool.Dimension;
import ink.ziip.championshipscore.api.game.bingo.util.BingoComponents;
import ink.ziip.championshipscore.api.game.bingo.util.MessageService;
import ink.ziip.championshipscore.platform.bukkit.text.LegacyText;

import net.kyori.adventure.text.Component;

import org.bukkit.Material;
import org.bukkit.Statistic;

import java.util.Map;
import java.util.Objects;

/** Reach {@code count} of the given statistic. */
public record StatisticTask(StatisticHandle statistic, int count, Dimension dimension)
        implements TaskData {

    public StatisticTask(StatisticHandle statistic) {
        this(statistic, 1);
    }

    public StatisticTask(StatisticHandle statistic, int count) {
        this(statistic, count, Dimension.OVERWORLD);
    }

    public StatisticTask(StatisticHandle statistic, int count, Dimension dimension) {
        this.statistic = statistic;
        this.count = Math.clamp(count, 1, 64);
        this.dimension = dimension == null ? Dimension.OVERWORLD : dimension;
    }

    @Override
    public TaskType getType() {
        return TaskType.STATISTIC;
    }

    @Override
    public String objectiveId() {
        // Mirror the catalog kinds: mine/craft/kill resolve to their item/entity subject;
        // everything
        // else is an untyped statistic keyed by its enum name.
        return switch (statistic.statisticType()) {
            case MINE_BLOCK -> "mine:" + statistic.itemType();
            case CRAFT_ITEM -> "craft:" + statistic.itemType();
            case KILL_ENTITY -> "kill:" + statistic.entityType();
            default -> "stat:" + statistic.statisticType().name();
        };
    }

    /**
     * True for open-ended untyped statistics rendered with the one_of "ANY" template: subject icon
     * centred in the stat frame, yellow ANY stamp across the top, no bottom-left action badge.
     */
    public boolean usesAnyTemplate() {
        return switch (statistic.statisticType()) {
            case MOB_KILLS, DEATHS, ANIMALS_BRED, ITEM_ENCHANTED -> true;
            default -> false;
        };
    }

    @Override
    public Component getName() {
        var msg = MessageService.global();
        Component amount = Component.text(count);
        if (statistic.statisticType() == Statistic.FISH_CAUGHT)
            return msg.rich(
                    "task.presentation.statistic-name",
                    Map.of(
                            "%statistic%",
                            LegacyText.component(msg.tr("task.fish_caught_name")),
                            "%count%",
                            amount));
        return switch (StatisticCategories.of(statistic.statisticType())) {
            case ROOT_STATISTIC -> {
                if (statistic.statisticType() == Statistic.KILL_ENTITY)
                    yield msg.rich(
                            "task.presentation.kill-name",
                            Map.of(
                                    "%statistic%",
                                            BingoComponents.statistic(
                                                    statistic, amount, Component.empty()),
                                    "%subject%",
                                            BingoComponents.entityName(statistic.entityType())));
                if (statistic.statisticType() == Statistic.ENTITY_KILLED_BY)
                    yield msg.rich(
                            "task.presentation.killed-by-name",
                            Map.of(
                                    "%statistic%",
                                            BingoComponents.statistic(
                                                    statistic,
                                                    Component.empty(),
                                                    amount,
                                                    Component.empty()),
                                    "%subject%",
                                            BingoComponents.entityName(statistic.entityType())));
                yield msg.rich(
                        "task.presentation.subject-statistic-name",
                        Map.of(
                                "%statistic%",
                                BingoComponents.statistic(statistic),
                                "%subject%",
                                BingoComponents.itemName(statistic.itemType()),
                                "%count%",
                                amount));
            }
            case TRAVEL ->
                    msg.rich(
                            "task.presentation.travel-name",
                            Map.of(
                                    "%statistic%",
                                    BingoComponents.statistic(statistic),
                                    "%distance%",
                                    Component.text(count * 10),
                                    "%unit%",
                                    LegacyText.component(msg.tr("task.blocks_unit"))));
            default ->
                    msg.rich(
                            "task.presentation.statistic-name",
                            Map.of(
                                    "%statistic%",
                                    BingoComponents.statistic(statistic), "%count%", amount));
        };
    }

    @Override
    public Component[] getItemDescription() {
        if (statistic.statisticType() == Statistic.FISH_CAUGHT) {
            return new Component[] {MessageService.global().component("task.fish_caught_desc")};
        }
        return new Component[] {MessageService.global().component("task.statistic")};
    }

    @Override
    public Component getChatDescription() {
        return Component.text().append(getItemDescription()).build();
    }

    @Override
    public boolean isTaskEqual(TaskData other) {
        return other instanceof StatisticTask st && statistic.equals(st.statistic);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StatisticTask that = (StatisticTask) o;
        return statistic.equals(that.statistic);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(statistic);
    }

    @Override
    public boolean shouldItemGlow() {
        return true;
    }

    @Override
    public Material getDisplayMaterial(CardDisplayInfo context) {
        if (context.statisticDisplay() == TaskDisplayMode.GENERIC_TASK_ITEMS) {
            return Material.GLOBE_BANNER_PATTERN;
        }
        return statistic.icon();
    }

    @Override
    public int getRequiredAmount() {
        return count;
    }

    @Override
    public TaskData setRequiredAmount(int newAmount) {
        return new StatisticTask(statistic, newAmount, dimension);
    }
}
