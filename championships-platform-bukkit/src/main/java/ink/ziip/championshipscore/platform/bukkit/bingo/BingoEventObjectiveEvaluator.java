package ink.ziip.championshipscore.platform.bukkit.bingo;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Authoritative Bukkit evaluation of every pollable Bingo event objective. */
public final class BingoEventObjectiveEvaluator {
    private static final Map<String, List<Material>> WEAR_FAMILIES = Map.of(
            "LEATHER", List.of(Material.LEATHER_HELMET, Material.LEATHER_CHESTPLATE, Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS),
            "IRON", List.of(Material.IRON_HELMET, Material.IRON_CHESTPLATE, Material.IRON_LEGGINGS, Material.IRON_BOOTS),
            "GOLDEN", List.of(Material.GOLDEN_HELMET, Material.GOLDEN_CHESTPLATE, Material.GOLDEN_LEGGINGS, Material.GOLDEN_BOOTS),
            "DIAMOND", List.of(Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE, Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS),
            "COPPER", List.of(Material.COPPER_HELMET, Material.COPPER_CHESTPLATE, Material.COPPER_LEGGINGS, Material.COPPER_BOOTS),
            "CHAIN", List.of(Material.CHAINMAIL_HELMET, Material.CHAINMAIL_CHESTPLATE, Material.CHAINMAIL_LEGGINGS, Material.CHAINMAIL_BOOTS));

    private BingoEventObjectiveEvaluator() {
    }

    public static boolean matches(Player player, BingoEventObjectiveRule rule, BingoObjectiveProgress progress) {
        UUID playerId = player.getUniqueId();
        String trigger = rule.trigger();
        String param = rule.param();
        int count = rule.count();
        return switch (trigger) {
            case "wear" -> "CHAIN".equalsIgnoreCase(param) ? wearsAny(player, param) : wearsFull(player, param);
            case "wear_full_enchanted" -> wearsFullEnchanted(player);
            case "wear_dyed" -> count >= 4
                    ? wearsFullDyedLeatherDistinct(player) : distinctDyedLeatherColors(player) >= count;
            case "wear_duration" -> wearDurationMet(player, playerId, param, count, progress);
            case "effect" -> hasEffect(player, param);
            case "effect_at_once" -> player.getActivePotionEffects().size() >= count;
            case "reach_level" -> player.getLevel() >= parseIntOr(param, Integer.MAX_VALUE);
            case "reach" -> atReach(player, param);
            case "hunger_empty" -> player.getFoodLevel() == 0;
            case "spy" -> spyOn(player, param);
            case "unique_collect" -> distinctMembersHeld(player, rule.members()) >= count;
            case "all_collect" -> hasAllMembers(player, rule.members());
            case "stack_of_64" -> hasStackOf64(player);
            case "fill_inventory_unique" -> fillInventoryUniqueMet(player);
            case "craft_unique" -> progress.distinctCount(playerId, "craft_unique") >= count;
            case "eat_unique" -> progress.distinctCount(playerId, "eat_unique") >= count;
            case "eat_all" -> progress.distinctCount(playerId, "eat_all:" + param) >= count;
            case "breed_unique" -> progress.distinctCount(playerId, "breed_unique") >= count;
            case "leash_unique" -> distinctLeashedSpecies(player) >= count;
            case "compost_unique" -> progress.distinctCount(playerId, "compost_unique") >= count;
            case "kill_family" -> progress.count(playerId, "kill_family:" + param) >= count;
            case "kill_unique" -> progress.distinctCount(playerId, "kill_unique:" + param) >= count;
            case "visit_biomes" -> visitBiomesMet(player, playerId, param, count, rule.biomeKeys(), progress);
            case "advancement_count" -> progress.count(playerId, "advancement_count") >= count;
            case "spy_unique" -> spyUniqueMet(player, playerId, count, progress);
            default -> false;
        };
    }

    private static boolean wearDurationMet(Player player, UUID playerId, String param, int minutes,
                                           BingoObjectiveProgress progress) {
        ItemStack helmet = player.getInventory().getHelmet();
        boolean active = "CARVED_PUMPKIN".equalsIgnoreCase(param)
                && helmet != null && helmet.getType() == Material.CARVED_PUMPKIN;
        return progress.observeElapsed(playerId, "wear_duration:" + param, active) >= minutes * 60_000L;
    }

    private static boolean wearsAny(Player player, String family) {
        List<Material> pieces = WEAR_FAMILIES.get(family.toUpperCase(Locale.ROOT));
        if (pieces == null) return false;
        for (ItemStack item : player.getInventory().getArmorContents()) {
            if (item != null && pieces.contains(item.getType())) return true;
        }
        return false;
    }

    private static boolean wearsFull(Player player, String family) {
        List<Material> pieces = WEAR_FAMILIES.get(family.toUpperCase(Locale.ROOT));
        if (pieces == null) return false;
        Set<Material> needed = EnumSet.copyOf(pieces);
        for (ItemStack item : player.getInventory().getArmorContents()) {
            if (item == null || !needed.remove(item.getType())) return false;
        }
        return needed.isEmpty();
    }

    private static boolean wearsFullEnchanted(Player player) {
        for (ItemStack item : player.getInventory().getArmorContents()) {
            if (item == null) return false;
            ItemMeta meta = item.getItemMeta();
            if (meta == null || !meta.hasEnchants()) return false;
        }
        return true;
    }

    private static int distinctDyedLeatherColors(Player player) {
        Set<Integer> colors = new HashSet<>();
        for (ItemStack item : player.getInventory().getArmorContents()) {
            if (item != null && item.getItemMeta() instanceof LeatherArmorMeta meta && meta.isDyed()) {
                colors.add(meta.getColor().asRGB());
            }
        }
        return colors.size();
    }

    private static boolean wearsFullDyedLeatherDistinct(Player player) {
        Set<Integer> colors = new HashSet<>();
        for (ItemStack item : player.getInventory().getArmorContents()) {
            if (item == null || !(item.getItemMeta() instanceof LeatherArmorMeta meta) || !meta.isDyed()
                    || !colors.add(meta.getColor().asRGB())) return false;
        }
        return colors.size() == 4;
    }

    private static boolean hasEffect(Player player, String effect) {
        String normalized = effect.toLowerCase(Locale.ROOT);
        NamespacedKey key = normalized.indexOf(':') >= 0
                ? NamespacedKey.fromString(normalized) : NamespacedKey.minecraft(normalized);
        PotionEffectType type = key == null ? null : Registry.MOB_EFFECT.get(key);
        return type != null && player.hasPotionEffect(type);
    }

    private static boolean atReach(Player player, String place) {
        World world = player.getWorld();
        Location location = player.getLocation();
        return switch (place.toUpperCase(Locale.ROOT)) {
            case "BEDROCK" -> location.getY() < world.getMinHeight() + 10.0
                    && location.clone().add(0, -1, 0).getBlock().getType() == Material.BEDROCK;
            case "HEIGHT_LIMIT" -> location.getY() >= world.getMaxHeight();
            case "NETHER_ROOF" -> world.getEnvironment() == World.Environment.NETHER && location.getY() >= 128.0;
            default -> false;
        };
    }

    private static boolean fillInventoryUniqueMet(Player player) {
        Set<Material> distinct = EnumSet.noneOf(Material.class);
        ItemStack[] contents = player.getInventory().getStorageContents();
        if (contents.length == 0) return false;
        for (ItemStack item : contents) {
            if (item == null || item.getType().isAir() || !distinct.add(item.getType())) return false;
        }
        return true;
    }

    private static int distinctLeashedSpecies(Player player) {
        Set<EntityType> species = EnumSet.noneOf(EntityType.class);
        for (Entity entity : player.getWorld().getNearbyEntities(player.getLocation(), 12, 12, 12)) {
            if (entity instanceof LivingEntity living && living.isLeashed() && living.getLeashHolder() == player) {
                species.add(entity.getType());
            }
        }
        return species.size();
    }

    private static boolean visitBiomesMet(Player player, UUID playerId, String param, int count,
                                          Set<String> biomeKeys, BingoObjectiveProgress progress) {
        String shortKey = player.getLocation().getBlock().getBiome().getKey().getKey();
        String fullKey = player.getLocation().getBlock().getBiome().getKey().asString();
        if (biomeKeys.contains(shortKey) || biomeKeys.contains(fullKey)) {
            progress.recordDistinct(playerId, "visit_biomes:" + param, fullKey);
        }
        return progress.distinctCount(playerId, "visit_biomes:" + param) >= count;
    }

    private static int distinctMembersHeld(Player player, Set<Material> members) {
        Set<Material> distinct = EnumSet.noneOf(Material.class);
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && members.contains(item.getType())) distinct.add(item.getType());
        }
        return distinct.size();
    }

    /** Returns whether the player's storage contains every requested material at least once. */
    public static boolean hasAllMembers(Player player, Set<Material> members) {
        if (members.isEmpty()) return false;
        Set<Material> held = EnumSet.noneOf(Material.class);
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item != null && !item.getType().isAir()) held.add(item.getType());
        }
        return held.containsAll(members);
    }

    private static boolean hasStackOf64(Player player) {
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item != null && item.getAmount() >= 64) return true;
        }
        return false;
    }

    private static boolean spyOn(Player player, String entityType) {
        if (!usingSpyglass(player)) return false;
        RayTraceResult result = player.getWorld().rayTraceEntities(player.getEyeLocation(),
                player.getEyeLocation().getDirection(), 64,
                entity -> entity instanceof Mob && entity.getType().name().equalsIgnoreCase(entityType)
                        && player.hasLineOfSight(entity));
        return result != null && result.getHitEntity() != null;
    }

    private static boolean spyUniqueMet(Player player, UUID playerId, int target,
                                        BingoObjectiveProgress progress) {
        if (!usingSpyglass(player)) return false;
        RayTraceResult result = player.getWorld().rayTraceEntities(player.getEyeLocation(),
                player.getEyeLocation().getDirection(), 64,
                entity -> entity instanceof Mob && entity != player && player.hasLineOfSight(entity));
        if (result == null || result.getHitEntity() == null) return false;
        progress.recordDistinct(playerId, "spy_unique", result.getHitEntity().getType().name());
        return progress.distinctCount(playerId, "spy_unique") >= target;
    }

    private static boolean usingSpyglass(Player player) {
        return player.isHandRaised() && (player.getInventory().getItemInMainHand().getType() == Material.SPYGLASS
                || player.getInventory().getItemInOffHand().getType() == Material.SPYGLASS);
    }

    private static int parseIntOr(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
