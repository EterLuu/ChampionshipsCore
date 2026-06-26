package ink.ziip.championshipscore.api.game.bingo.task;

import ink.ziip.championshipscore.api.game.bingo.util.Materials;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.entity.EntityType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * A statistic together with its optional qualifier (entity or item type). Bukkit's "sub statistics"
 * (e.g. MINE_BLOCK, KILL_ENTITY) require a qualifier, which this record carries.
 */
public record StatisticHandle(@NotNull Statistic statistic, @Nullable EntityType entityType, @Nullable Material itemType) {

    private static final Map<Statistic, String> TRANSLATION_KEYS = Map.ofEntries(
            Map.entry(Statistic.DROP, "dropped"),
            Map.entry(Statistic.PICKUP, "picked_up"),
            Map.entry(Statistic.USE_ITEM, "used"),
            Map.entry(Statistic.BREAK_ITEM, "broken"),
            Map.entry(Statistic.CRAFT_ITEM, "crafted"),
            Map.entry(Statistic.KILL_ENTITY, "killed"),
            Map.entry(Statistic.ENTITY_KILLED_BY, "killed_by"),
            Map.entry(Statistic.MINE_BLOCK, "mined"),
            Map.entry(Statistic.DAMAGE_DEALT, "damage_dealt"),
            Map.entry(Statistic.DAMAGE_TAKEN, "damage_taken"),
            Map.entry(Statistic.DAMAGE_DEALT_ABSORBED, "damage_dealt_absorbed"),
            Map.entry(Statistic.DAMAGE_DEALT_RESISTED, "damage_dealt_resisted"),
            Map.entry(Statistic.DAMAGE_RESISTED, "damage_resisted"),
            Map.entry(Statistic.DAMAGE_ABSORBED, "damage_absorbed"),
            Map.entry(Statistic.DAMAGE_BLOCKED_BY_SHIELD, "damage_blocked_by_shield"),
            Map.entry(Statistic.TALKED_TO_VILLAGER, "talked_to_villager"),
            Map.entry(Statistic.TRADED_WITH_VILLAGER, "traded_with_villager"),
            Map.entry(Statistic.DEATHS, "deaths"),
            Map.entry(Statistic.MOB_KILLS, "mob_kills"),
            Map.entry(Statistic.PLAYER_KILLS, "player_kills"),
            Map.entry(Statistic.FISH_CAUGHT, "fish_caught"),
            Map.entry(Statistic.ANIMALS_BRED, "animals_bred"),
            Map.entry(Statistic.LEAVE_GAME, "leave_game"),
            Map.entry(Statistic.JUMP, "jump"),
            Map.entry(Statistic.DROP_COUNT, "drop"),
            Map.entry(Statistic.PLAY_ONE_MINUTE, "play_time"),
            Map.entry(Statistic.TOTAL_WORLD_TIME, "total_world_time"),
            Map.entry(Statistic.SNEAK_TIME, "sneak_time"),
            Map.entry(Statistic.TIME_SINCE_DEATH, "time_since_death"),
            Map.entry(Statistic.RAID_TRIGGER, "raid_trigger"),
            Map.entry(Statistic.ARMOR_CLEANED, "clean_armor"),
            Map.entry(Statistic.BANNER_CLEANED, "clean_banner"),
            Map.entry(Statistic.ITEM_ENCHANTED, "enchant_item"),
            Map.entry(Statistic.TIME_SINCE_REST, "time_since_rest"),
            Map.entry(Statistic.RAID_WIN, "raid_win"),
            Map.entry(Statistic.TARGET_HIT, "target_hit"),
            Map.entry(Statistic.CLEAN_SHULKER_BOX, "clean_shulker_box"),
            Map.entry(Statistic.CAKE_SLICES_EATEN, "eat_cake_slice"),
            Map.entry(Statistic.CAULDRON_FILLED, "fill_cauldron"),
            Map.entry(Statistic.BREWINGSTAND_INTERACTION, "interact_with_brewingstand"),
            Map.entry(Statistic.BEACON_INTERACTION, "interact_with_beacon"),
            Map.entry(Statistic.NOTEBLOCK_PLAYED, "play_noteblock"),
            Map.entry(Statistic.CAULDRON_USED, "use_cauldron"),
            Map.entry(Statistic.NOTEBLOCK_TUNED, "tune_noteblock"),
            Map.entry(Statistic.FLOWER_POTTED, "pot_flower"),
            Map.entry(Statistic.TRAPPED_CHEST_TRIGGERED, "trigger_trapped_chest"),
            Map.entry(Statistic.RECORD_PLAYED, "play_record"),
            Map.entry(Statistic.FURNACE_INTERACTION, "interact_with_furnace"),
            Map.entry(Statistic.CRAFTING_TABLE_INTERACTION, "interact_with_crafting_table"),
            Map.entry(Statistic.SLEEP_IN_BED, "sleep_in_bed"),
            Map.entry(Statistic.SHULKER_BOX_OPENED, "open_shulker_box"),
            Map.entry(Statistic.INTERACT_WITH_BLAST_FURNACE, "interact_with_blast_furnace"),
            Map.entry(Statistic.INTERACT_WITH_SMOKER, "interact_with_smoker"),
            Map.entry(Statistic.INTERACT_WITH_LECTERN, "interact_with_lectern"),
            Map.entry(Statistic.INTERACT_WITH_CAMPFIRE, "interact_with_campfire"),
            Map.entry(Statistic.INTERACT_WITH_CARTOGRAPHY_TABLE, "interact_with_cartography_table"),
            Map.entry(Statistic.INTERACT_WITH_LOOM, "interact_with_loom"),
            Map.entry(Statistic.INTERACT_WITH_STONECUTTER, "interact_with_stonecutter"),
            Map.entry(Statistic.BELL_RING, "bell_ring"),
            Map.entry(Statistic.INTERACT_WITH_ANVIL, "interact_with_anvil"),
            Map.entry(Statistic.INTERACT_WITH_GRINDSTONE, "interact_with_grindstone"),
            Map.entry(Statistic.INTERACT_WITH_SMITHING_TABLE, "interact_with_smithing_table"),
            Map.entry(Statistic.OPEN_BARREL, "open_barrel"),
            Map.entry(Statistic.CHEST_OPENED, "open_chest"),
            Map.entry(Statistic.ENDERCHEST_OPENED, "open_enderchest"),
            Map.entry(Statistic.HOPPER_INSPECTED, "inspect_hopper"),
            Map.entry(Statistic.DROPPER_INSPECTED, "inspect_dropper"),
            Map.entry(Statistic.DISPENSER_INSPECTED, "inspect_dispenser"),
            Map.entry(Statistic.STRIDER_ONE_CM, "strider_one_cm"),
            Map.entry(Statistic.MINECART_ONE_CM, "minecart_one_cm"),
            Map.entry(Statistic.CLIMB_ONE_CM, "climb_one_cm"),
            Map.entry(Statistic.FLY_ONE_CM, "fly_one_cm"),
            Map.entry(Statistic.WALK_UNDER_WATER_ONE_CM, "walk_under_water_one_cm"),
            Map.entry(Statistic.BOAT_ONE_CM, "boat_one_cm"),
            Map.entry(Statistic.PIG_ONE_CM, "pig_one_cm"),
            Map.entry(Statistic.HORSE_ONE_CM, "horse_one_cm"),
            Map.entry(Statistic.CROUCH_ONE_CM, "crouch_one_cm"),
            Map.entry(Statistic.AVIATE_ONE_CM, "aviate_one_cm"),
            Map.entry(Statistic.WALK_ONE_CM, "walk_one_cm"),
            Map.entry(Statistic.WALK_ON_WATER_ONE_CM, "walk_on_water_one_cm"),
            Map.entry(Statistic.SWIM_ONE_CM, "swim_one_cm"),
            Map.entry(Statistic.FALL_ONE_CM, "fall_one_cm"),
            Map.entry(Statistic.SPRINT_ONE_CM, "sprint_one_cm"),
            Map.entry(Statistic.HAPPY_GHAST_ONE_CM, "happy_ghast_one_cm"),
            Map.entry(Statistic.NAUTILUS_ONE_CM, "nautilus_one_cm")
    );

    public StatisticHandle(Statistic stat, Material itemType) {
        this(stat, null, itemType);
    }

    public StatisticHandle(Statistic stat) {
        this(stat, null, null);
    }

    public StatisticHandle(Statistic stat, @NotNull EntityType entityType) {
        this(stat, entityType, null);
    }

    public Statistic statisticType() {
        return statistic;
    }

    public boolean isSubStatistic() {
        return statistic.isSubstatistic();
    }

    public String translationKey() {
        return TRANSLATION_KEYS.getOrDefault(statistic, statistic.name());
    }

    public boolean hasMaterial() {
        return itemType != null;
    }

    public boolean hasEntity() {
        return entityType != null;
    }

    public @NotNull Material icon() {
        return switch (statistic) {
            case DAMAGE_DEALT -> Material.DIAMOND_SWORD;
            case DAMAGE_TAKEN -> Material.IRON_CHESTPLATE;
            case DEATHS -> Material.SKELETON_SKULL;
            case MOB_KILLS -> Material.CREEPER_HEAD;
            case PLAYER_KILLS -> Material.PLAYER_HEAD;
            case FISH_CAUGHT -> Material.TROPICAL_FISH;
            case ANIMALS_BRED -> Material.WHEAT;
            case LEAVE_GAME -> Material.BARRIER;
            case JUMP -> Material.RABBIT_FOOT;
            case DROP_COUNT, HOPPER_INSPECTED -> Material.HOPPER;
            case PLAY_ONE_MINUTE -> Material.CLOCK;
            case TOTAL_WORLD_TIME -> Material.FILLED_MAP;
            case WALK_ONE_CM -> Material.LEATHER_BOOTS;
            case WALK_ON_WATER_ONE_CM -> Material.ICE;
            case FALL_ONE_CM -> Material.LAVA_BUCKET;
            case SNEAK_TIME -> Material.SCULK_SHRIEKER;
            case CLIMB_ONE_CM -> Material.EMERALD_ORE;
            case FLY_ONE_CM -> Material.COMMAND_BLOCK;
            case WALK_UNDER_WATER_ONE_CM -> Material.GOLDEN_BOOTS;
            case MINECART_ONE_CM -> Material.MINECART;
            case BOAT_ONE_CM -> Material.OAK_BOAT;
            case PIG_ONE_CM -> Material.CARROT_ON_A_STICK;
            case HORSE_ONE_CM -> Material.SADDLE;
            case SPRINT_ONE_CM -> Material.FEATHER;
            case CROUCH_ONE_CM -> Material.SCULK_SENSOR;
            case AVIATE_ONE_CM -> Material.ELYTRA;
            case TIME_SINCE_DEATH -> Material.RECOVERY_COMPASS;
            case TALKED_TO_VILLAGER -> Material.POPPY;
            case TRADED_WITH_VILLAGER -> Material.EMERALD;
            case CAKE_SLICES_EATEN -> Material.CAKE;
            case CAULDRON_FILLED -> Material.CAULDRON;
            case CAULDRON_USED -> Material.WATER_BUCKET;
            case ARMOR_CLEANED -> Material.LEATHER_CHESTPLATE;
            case BANNER_CLEANED -> Material.WHITE_BANNER;
            case BREWINGSTAND_INTERACTION -> Material.BREWING_STAND;
            case BEACON_INTERACTION -> Material.BEACON;
            case DROPPER_INSPECTED -> Material.DROPPER;
            case DISPENSER_INSPECTED -> Material.DISPENSER;
            case NOTEBLOCK_PLAYED, NOTEBLOCK_TUNED -> Material.NOTE_BLOCK;
            case FLOWER_POTTED -> Material.FLOWER_POT;
            case TRAPPED_CHEST_TRIGGERED -> Material.TRAPPED_CHEST;
            case ENDERCHEST_OPENED -> Material.ENDER_CHEST;
            case ITEM_ENCHANTED -> Material.ENCHANTING_TABLE;
            case RECORD_PLAYED -> Material.MUSIC_DISC_CAT;
            case FURNACE_INTERACTION -> Material.FURNACE;
            case CRAFTING_TABLE_INTERACTION -> Material.CRAFTING_TABLE;
            case CHEST_OPENED -> Material.CHEST;
            case SLEEP_IN_BED -> Material.RED_BED;
            case SHULKER_BOX_OPENED -> Material.SHULKER_BOX;
            case TIME_SINCE_REST -> Material.YELLOW_BED;
            case SWIM_ONE_CM -> Material.BUBBLE_CORAL;
            case DAMAGE_DEALT_ABSORBED -> Material.DAMAGED_ANVIL;
            case DAMAGE_DEALT_RESISTED -> Material.NETHERITE_SWORD;
            case DAMAGE_BLOCKED_BY_SHIELD -> Material.SHIELD;
            case DAMAGE_ABSORBED -> Material.SPONGE;
            case DAMAGE_RESISTED -> Material.DIAMOND_CHESTPLATE;
            case CLEAN_SHULKER_BOX -> Material.SHULKER_SHELL;
            case OPEN_BARREL -> Material.BARREL;
            case INTERACT_WITH_BLAST_FURNACE -> Material.BLAST_FURNACE;
            case INTERACT_WITH_SMOKER -> Material.SMOKER;
            case INTERACT_WITH_LECTERN -> Material.LECTERN;
            case INTERACT_WITH_CAMPFIRE -> Material.CAMPFIRE;
            case INTERACT_WITH_CARTOGRAPHY_TABLE -> Material.CARTOGRAPHY_TABLE;
            case INTERACT_WITH_LOOM -> Material.LOOM;
            case INTERACT_WITH_STONECUTTER -> Material.STONECUTTER;
            case BELL_RING -> Material.BELL;
            case RAID_TRIGGER -> Material.CROSSBOW;
            case RAID_WIN -> Material.TOTEM_OF_UNDYING;
            case INTERACT_WITH_ANVIL -> Material.ANVIL;
            case INTERACT_WITH_GRINDSTONE -> Material.GRINDSTONE;
            case TARGET_HIT -> Material.TARGET;
            case INTERACT_WITH_SMITHING_TABLE -> Material.SMITHING_TABLE;
            case STRIDER_ONE_CM -> Material.WARPED_FUNGUS_ON_A_STICK;
            case HAPPY_GHAST_ONE_CM -> Material.DRIED_GHAST;
            case NAUTILUS_ONE_CM -> Material.GOLDEN_NAUTILUS_ARMOR;
            case DROP,
                 PICKUP,
                 MINE_BLOCK,
                 USE_ITEM,
                 BREAK_ITEM,
                 CRAFT_ITEM,
                 KILL_ENTITY,
                 ENTITY_KILLED_BY -> rootStatIcon();
        };
    }

    private Material rootStatIcon() {
        if (statistic.getType() == Statistic.Type.ITEM || statistic.getType() == Statistic.Type.BLOCK) {
            return itemType == null ? Material.GLOBE_BANNER_PATTERN : itemType;
        }
        if (entityType != null && statistic.getType() == Statistic.Type.ENTITY) {
            return Materials.fromKey("minecraft:" + entityType.key().value() + "_spawn_egg");
        }
        return Material.GLOBE_BANNER_PATTERN;
    }
}
