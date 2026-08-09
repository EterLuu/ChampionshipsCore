package ink.ziip.championshipscore.api.game.bingo.task;

import ink.ziip.championshipscore.api.game.bingo.task.pool.Dimension;
import ink.ziip.championshipscore.api.game.bingo.util.BingoComponents;
import ink.ziip.championshipscore.api.game.bingo.util.MessageService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Statistic;

import java.util.Objects;

/** Reach {@code count} of the given statistic. */
public record StatisticTask(StatisticHandle statistic, int count, Dimension dimension) implements TaskData {

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
        // Mirror the catalog kinds: mine/craft/kill resolve to their item/entity subject; everything
        // else is an untyped statistic keyed by its enum name.
        return switch (statistic.statisticType()) {
            case MINE_BLOCK -> "mine:" + statistic.itemType();
            case CRAFT_ITEM -> "craft:" + statistic.itemType();
            case KILL_ENTITY -> "kill:" + statistic.entityType();
            default -> "stat:" + statistic.statisticType().name();
        };
    }

    @Override
    public Component getName() {
        // Special override: FISH_CAUGHT uses a friendlier display name from the lang file.
        if (statistic.statisticType() == Statistic.FISH_CAUGHT) {
            MessageService msg = MessageService.global();
            return Component.text().color(NamedTextColor.LIGHT_PURPLE)
                    .append(Component.text("*"))
                    .append(Component.text(msg.tr("task.fish_caught_name")))
                    .append(Component.text(": "))
                    .append(Component.text(count))
                    .append(Component.text("*"))
                    .build();
        }

        Component amount = Component.text(count);
        TextComponent.Builder builder = Component.text().append(Component.text("*"))
                .color(NamedTextColor.LIGHT_PURPLE);

        switch (StatisticCategories.of(statistic.statisticType())) {
            case ROOT_STATISTIC -> {
                if (statistic.statisticType().equals(Statistic.KILL_ENTITY)) {
                    Component entityName = BingoComponents.entityName(statistic.entityType());
                    Component[] inPlace = new Component[]{amount, Component.empty()};
                    builder.append(BingoComponents.statistic(statistic, inPlace))
                            .append(Component.text("("))
                            .append(entityName)
                            .append(Component.text(")"));
                } else if (statistic.statisticType().equals(Statistic.ENTITY_KILLED_BY)) {
                    Component entityName = BingoComponents.entityName(statistic.entityType());
                    Component[] inPlace = new Component[]{Component.empty(), amount, Component.empty()};
                    builder.append(Component.text("("))
                            .append(entityName)
                            .append(Component.text(")"))
                            .append(BingoComponents.statistic(statistic, inPlace));
                } else {
                    builder.append(BingoComponents.statistic(statistic))
                            .append(Component.text(" "))
                            .append(BingoComponents.itemName(statistic.itemType()))
                            .append(Component.text(": "))
                            .append(amount);
                }
            }
            case TRAVEL -> builder.append(BingoComponents.statistic(statistic))
                    .append(Component.text(": "))
                    .append(Component.text(count * 10))
                    .append(Component.text(" " + MessageService.global().tr("task.blocks_unit")));
            default -> builder.append(BingoComponents.statistic(statistic))
                    .append(Component.text(": "))
                    .append(amount);
        }
        builder.append(Component.text("*"));
        return builder.build();
    }

    @Override
    public Component[] getItemDescription() {
        if (statistic.statisticType() == Statistic.FISH_CAUGHT) {
            return new Component[]{MessageService.global().component("task.fish_caught_desc")};
        }
        return new Component[]{
                MessageService.global().component("task.statistic")
        };
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
