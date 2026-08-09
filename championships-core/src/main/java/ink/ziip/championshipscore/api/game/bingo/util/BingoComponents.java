package ink.ziip.championshipscore.api.game.bingo.util;

import ink.ziip.championshipscore.api.game.bingo.task.StatisticHandle;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.advancement.Advancement;
import org.bukkit.entity.EntityType;
import org.jetbrains.annotations.NotNull;

/**
 * Builds client-side translatable {@link Component}s for items, advancements, statistics and entities,
 * so names render in each player's own locale. Ported from BingoReloaded's ComponentUtils, trimmed to
 * the Paper-only key logic the task system needs.
 */
public final class BingoComponents {
    private BingoComponents() {
    }

    public static Component itemName(Material item) {
        return Component.translatable(itemKey(item));
    }

    public static Component advancementTitle(@NotNull Advancement advancement) {
        return Component.translatable(advancementKey(advancement) + ".title");
    }

    public static Component advancementDescription(@NotNull Advancement advancement) {
        return Component.translatable(advancementKey(advancement) + ".description");
    }

    public static Component statistic(StatisticHandle statistic, Component... with) {
        return Component.translatable(statisticKey(statistic), with);
    }

    public static Component entityName(EntityType entity) {
        return Component.translatable(entityKey(entity));
    }

    private static String advancementKey(@NotNull Advancement advancement) {
        String result = advancement.key().value().replace("/", ".");
        // Mojang's lang keys diverge from advancement keys for a few entries.
        result = switch (result) {
            case "husbandry.obtain_netherite_hoe" -> "husbandry.netherite_hoe";
            case "husbandry.bred_all_animals" -> "husbandry.breed_all_animals";
            case "adventure.read_power_of_chiseled_bookshelf" -> "adventure.read_power_from_chiseled_bookshelf";
            default -> result;
        };
        return "advancements." + result;
    }

    private static String statisticKey(StatisticHandle statistic) {
        String prefix = statistic.isSubStatistic() ? "stat_type.minecraft." : "stat.minecraft.";
        String result = statistic.translationKey();
        return !result.isEmpty() ? prefix + result : statistic.statisticType().key().asString();
    }

    private static String itemKey(Material item) {
        return (item.isBlock() ? "block" : "item") + ".minecraft." + item.key().value();
    }

    private static String entityKey(EntityType entity) {
        return "entity.minecraft." + entity.key().value();
    }
}
