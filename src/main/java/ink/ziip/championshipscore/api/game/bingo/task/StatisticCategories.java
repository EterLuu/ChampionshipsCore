package ink.ziip.championshipscore.api.game.bingo.task;

import org.bukkit.Statistic;

/** Classifies vanilla {@link Statistic}s into {@link StatisticCategory} buckets. */
public final class StatisticCategories {
    private StatisticCategories() {
    }

    public static StatisticCategory of(Statistic stat) {
        return switch (stat) {
            case DROP,
                 PICKUP,
                 USE_ITEM,
                 BREAK_ITEM,
                 CRAFT_ITEM,
                 KILL_ENTITY,
                 ENTITY_KILLED_BY,
                 MINE_BLOCK -> StatisticCategory.ROOT_STATISTIC;

            case DAMAGE_DEALT,
                 DAMAGE_TAKEN,
                 DAMAGE_DEALT_ABSORBED,
                 DAMAGE_DEALT_RESISTED,
                 DAMAGE_RESISTED,
                 DAMAGE_ABSORBED,
                 DAMAGE_BLOCKED_BY_SHIELD -> StatisticCategory.DAMAGE;

            case TALKED_TO_VILLAGER,
                 TRADED_WITH_VILLAGER,
                 DEATHS,
                 MOB_KILLS,
                 PLAYER_KILLS,
                 FISH_CAUGHT,
                 ANIMALS_BRED,
                 LEAVE_GAME,
                 JUMP,
                 DROP_COUNT,
                 PLAY_ONE_MINUTE,
                 TOTAL_WORLD_TIME,
                 SNEAK_TIME,
                 TIME_SINCE_DEATH,
                 RAID_TRIGGER,
                 ARMOR_CLEANED,
                 BANNER_CLEANED,
                 ITEM_ENCHANTED,
                 TIME_SINCE_REST,
                 RAID_WIN,
                 TARGET_HIT,
                 CLEAN_SHULKER_BOX -> StatisticCategory.OTHER;

            case CAKE_SLICES_EATEN,
                 CAULDRON_FILLED,
                 BREWINGSTAND_INTERACTION,
                 BEACON_INTERACTION,
                 NOTEBLOCK_PLAYED,
                 CAULDRON_USED,
                 NOTEBLOCK_TUNED,
                 FLOWER_POTTED,
                 RECORD_PLAYED,
                 FURNACE_INTERACTION,
                 CRAFTING_TABLE_INTERACTION,
                 SLEEP_IN_BED,
                 INTERACT_WITH_BLAST_FURNACE,
                 INTERACT_WITH_SMOKER,
                 INTERACT_WITH_LECTERN,
                 INTERACT_WITH_CAMPFIRE,
                 INTERACT_WITH_CARTOGRAPHY_TABLE,
                 INTERACT_WITH_LOOM,
                 INTERACT_WITH_STONECUTTER,
                 BELL_RING,
                 INTERACT_WITH_ANVIL,
                 INTERACT_WITH_GRINDSTONE,
                 INTERACT_WITH_SMITHING_TABLE -> StatisticCategory.BLOCK_INTERACT;

            case OPEN_BARREL,
                 CHEST_OPENED,
                 ENDERCHEST_OPENED,
                 SHULKER_BOX_OPENED,
                 TRAPPED_CHEST_TRIGGERED,
                 HOPPER_INSPECTED,
                 DROPPER_INSPECTED,
                 DISPENSER_INSPECTED -> StatisticCategory.CONTAINER_INTERACT;

            case STRIDER_ONE_CM,
                 MINECART_ONE_CM,
                 CLIMB_ONE_CM,
                 FLY_ONE_CM,
                 WALK_UNDER_WATER_ONE_CM,
                 BOAT_ONE_CM,
                 PIG_ONE_CM,
                 HORSE_ONE_CM,
                 CROUCH_ONE_CM,
                 AVIATE_ONE_CM,
                 WALK_ONE_CM,
                 WALK_ON_WATER_ONE_CM,
                 SWIM_ONE_CM,
                 FALL_ONE_CM,
                 SPRINT_ONE_CM,
                 HAPPY_GHAST_ONE_CM,
                 NAUTILUS_ONE_CM -> StatisticCategory.TRAVEL;
        };
    }
}
