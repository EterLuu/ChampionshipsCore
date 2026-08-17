package ink.ziip.championshipscore.worker;

import ink.ziip.championshipscore.protocol.BingoTaskSpec;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Statistic;
import org.bukkit.advancement.Advancement;
import org.bukkit.entity.EntityType;

import java.util.Locale;
import java.util.Map;

/** Resolves the frozen display fields, with compatibility fallbacks for pre-display-field manifests. */
final class WorkerTaskDisplay {
    private WorkerTaskDisplay() {
    }

    static Material icon(BingoTaskSpec task) {
        Material frozen = material(task.attributes().get("display.material"), null);
        if (frozen != null) return frozen;
        Map<String, String> attributes = task.attributes();
        return switch (task.taskType().toLowerCase(Locale.ROOT)) {
            case "item", "potion" -> material(attributes.get("material"), Material.PAPER);
            case "item_set", "all_of" -> {
                String[] materials = attributes.getOrDefault("materials", "").split(",");
                yield material(materials.length == 0 ? null : materials[0], Material.CHEST);
            }
            case "advancement" -> advancementIcon(attributes.get("key"));
            case "statistic" -> statisticIcon(attributes);
            case "event" -> material(attributes.get("display.material"), Material.PAPER);
            default -> Material.PAPER;
        };
    }

    static int amount(BingoTaskSpec task) {
        String frozen = task.attributes().get("display.amount");
        if (frozen != null) return positiveInt(frozen, 1);

        String raw = task.attributes().getOrDefault("count", task.attributes().getOrDefault("target", "1"));
        int value = positiveInt(raw, 1);
        Statistic statistic = enumValue(Statistic.class, task.attributes().get("statistic"));
        // Old manifests store travel execution targets in centimetres. Core's card number is the
        // configured ten-block unit, so reverse the mapper's x1000 execution conversion.
        if (statistic != null && statistic.name().endsWith("_ONE_CM")) value /= 1000;
        return Math.max(1, value);
    }

    static Key statisticSubject(BingoTaskSpec task) {
        Key frozen = key(task.attributes().get("display.icon-key"));
        if (frozen != null) return frozen;
        frozen = key(task.attributes().get("display.entity"));
        if (frozen != null) return frozen;
        EntityType entity = enumValue(EntityType.class, task.attributes().get("entity"));
        if (entity != null) return entity.key();
        Material icon = icon(task);
        return icon == null ? null : icon.key();
    }

    static boolean glows(BingoTaskSpec task) {
        return "advancement".equalsIgnoreCase(task.taskType())
                || "statistic".equalsIgnoreCase(task.taskType())
                || "event".equalsIgnoreCase(task.taskType());
    }

    static Key key(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return Key.key(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    static Material material(String raw, Material fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        Material material = Material.matchMaterial(raw.replace("MINECRAFT:", ""));
        return material == null ? fallback : material;
    }

    static Advancement advancement(String raw) {
        NamespacedKey key = raw == null ? null : NamespacedKey.fromString(raw);
        return key == null ? null : Bukkit.getAdvancement(key);
    }

    private static Material advancementIcon(String key) {
        Advancement advancement = advancement(key);
        return advancement == null || advancement.getDisplay() == null
                ? Material.FILLED_MAP : advancement.getDisplay().icon().getType();
    }

    private static Material statisticIcon(Map<String, String> attributes) {
        Material subject = material(attributes.get("material"), null);
        if (subject != null) return subject;
        EntityType entity = enumValue(EntityType.class, attributes.get("entity"));
        if (entity != null) {
            Material egg = material(entity.name() + "_SPAWN_EGG", null);
            return egg == null ? Material.PAPER : egg;
        }
        Statistic statistic = enumValue(Statistic.class, attributes.get("statistic"));
        if (statistic == null) return Material.GLOBE_BANNER_PATTERN;
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
            case DAMAGE_ABSORBED -> Material.GOLDEN_APPLE;
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
            case RAID_TRIGGER, RAID_WIN -> Material.OMINOUS_BOTTLE;
            case INTERACT_WITH_ANVIL -> Material.ANVIL;
            case INTERACT_WITH_GRINDSTONE -> Material.GRINDSTONE;
            case TARGET_HIT -> Material.TARGET;
            case INTERACT_WITH_SMITHING_TABLE -> Material.SMITHING_TABLE;
            case STRIDER_ONE_CM -> Material.WARPED_FUNGUS_ON_A_STICK;
            case HAPPY_GHAST_ONE_CM -> Material.DRIED_GHAST;
            case NAUTILUS_ONE_CM -> Material.GOLDEN_NAUTILUS_ARMOR;
            default -> Material.GLOBE_BANNER_PATTERN;
        };
    }

    private static int positiveInt(String raw, int fallback) {
        try {
            return Math.max(1, Integer.parseInt(raw));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    static <T extends Enum<T>> T enumValue(Class<T> type, String name) {
        if (name == null) return null;
        try {
            return Enum.valueOf(type, name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
