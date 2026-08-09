package ink.ziip.championshipscore.api.game.bingo.execution;

import ink.ziip.championshipscore.api.game.bingo.task.AdvancementTask;
import ink.ziip.championshipscore.api.game.bingo.task.GameTask;
import ink.ziip.championshipscore.api.game.bingo.task.ItemTask;
import ink.ziip.championshipscore.api.game.bingo.task.OneOfTask;
import ink.ziip.championshipscore.api.game.bingo.task.PotionTask;
import ink.ziip.championshipscore.api.game.bingo.task.StatisticCategories;
import ink.ziip.championshipscore.api.game.bingo.task.StatisticCategory;
import ink.ziip.championshipscore.api.game.bingo.task.StatisticHandle;
import ink.ziip.championshipscore.api.game.bingo.task.StatisticTask;
import ink.ziip.championshipscore.protocol.BingoTaskSpec;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Converts the Bukkit task model into the stable, registry-name-only wire model. */
public final class BingoTaskSpecMapper {
    private BingoTaskSpecMapper() {
    }

    public static List<BingoTaskSpec> toSpecs(List<GameTask> tasks) {
        java.util.ArrayList<BingoTaskSpec> result = new java.util.ArrayList<>(tasks.size());
        for (int cell = 0; cell < tasks.size(); cell++) result.add(toSpec(cell, tasks.get(cell)));
        return List.copyOf(result);
    }

    private static BingoTaskSpec toSpec(int cell, GameTask task) {
        Map<String, String> attributes = new LinkedHashMap<>();
        String type;
        if (task.data instanceof PotionTask potion) {
            type = "potion";
            attributes.put("material", potion.form().material.name());
            attributes.put("effect", potion.effect());
            attributes.put("count", Integer.toString(potion.count()));
        } else if (task.data instanceof ItemTask item) {
            type = "item";
            attributes.put("material", item.itemType().name());
            attributes.put("count", Integer.toString(item.count()));
        } else if (task.data instanceof OneOfTask set) {
            type = "item_set";
            attributes.put("materials", set.items().stream().map(Enum::name).sorted()
                    .collect(Collectors.joining(",")));
            attributes.put("count", Integer.toString(set.count()));
        } else if (task.data instanceof AdvancementTask advancement) {
            if (advancement.advancement() == null) {
                throw new IllegalArgumentException("Card contains an unresolved advancement at cell " + cell);
            }
            type = "advancement";
            attributes.put("key", advancement.advancement().key().asString());
        } else if (task.data instanceof StatisticTask statistic) {
            type = "statistic";
            StatisticHandle handle = statistic.statistic();
            attributes.put("statistic", handle.statisticType().name());
            if (handle.itemType() != null) attributes.put("material", handle.itemType().name());
            if (handle.entityType() != null) attributes.put("entity", handle.entityType().name());
            int target = StatisticCategories.of(handle.statisticType()) == StatisticCategory.TRAVEL
                    ? Math.multiplyExact(statistic.count(), 1000) : statistic.count();
            attributes.put("target", Integer.toString(target));
        } else {
            throw new IllegalArgumentException("Unsupported Bingo task model " + task.data.getClass().getName());
        }
        appendPresentation(task, attributes);
        return new BingoTaskSpec(cell, cell + ":" + task.data.objectiveId(), type, attributes);
    }

    private static void appendPresentation(GameTask task, Map<String, String> attributes) {
        try {
            GsonComponentSerializer serializer = GsonComponentSerializer.gson();
            attributes.put("display.name", serializer.serialize(task.data.getName()));
            net.kyori.adventure.text.Component[] description = task.data.getItemDescription();
            attributes.put("display.lore-size", Integer.toString(description.length));
            for (int index = 0; index < description.length; index++) {
                attributes.put("display.lore." + index, serializer.serialize(description[index]));
            }
        } catch (RuntimeException | LinkageError unavailableRegistry) {
            // Headless mapper tests have no Paper RegistryAccess. The Worker retains a registry-name
            // fallback, while production Core always supplies these rich display fields.
        }
    }
}
