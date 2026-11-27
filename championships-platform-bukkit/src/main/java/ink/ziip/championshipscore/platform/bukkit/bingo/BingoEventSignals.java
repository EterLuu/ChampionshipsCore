package ink.ziip.championshipscore.platform.bukkit.bingo;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Campfire;
import org.bukkit.block.data.Levelled;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Set;

/** Maps Bukkit events to the frozen objective vocabulary used by both Bingo runtimes. */
public final class BingoEventSignals {
    private BingoEventSignals() {}

    private static final Set<Material> SOUPS =
            Set.of(
                    Material.BEETROOT_SOUP,
                    Material.MUSHROOM_STEW,
                    Material.RABBIT_STEW,
                    Material.SUSPICIOUS_STEW);
    private static final Set<String> TOOL_MATERIALS =
            Set.of("WOODEN", "STONE", "IRON", "GOLDEN", "DIAMOND", "COPPER");
    private static final Set<String> TOOL_TYPES =
            Set.of("PICKAXE", "AXE", "SHOVEL", "HOE", "SWORD");
    private static final Set<Material> MISC_TOOLS =
            Set.of(
                    Material.FISHING_ROD,
                    Material.FLINT_AND_STEEL,
                    Material.SHEARS,
                    Material.BRUSH,
                    Material.CARROT_ON_A_STICK,
                    Material.WARPED_FUNGUS_ON_A_STICK);
    private static final Set<String> ARMOR_MATERIALS =
            Set.of("LEATHER", "COPPER", "GOLDEN", "CHAINMAIL", "IRON", "DIAMOND");
    private static final Set<String> ARMOR_TYPES =
            Set.of("HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS");

    public static boolean isSoup(Material material) {
        return SOUPS.contains(material);
    }

    public static String brokenItemKind(Material material) {
        if (MISC_TOOLS.contains(material)) return "TOOL";
        String name = material.name();
        int separator = name.indexOf('_');
        if (separator == -1) return null;
        String family = name.substring(0, separator);
        String item = name.substring(separator + 1);
        if (ARMOR_MATERIALS.contains(family) && ARMOR_TYPES.contains(item)) return "ARMOR";
        if (TOOL_MATERIALS.contains(family) && TOOL_TYPES.contains(item)) return "TOOL";
        return null;
    }

    public static Player crystalDamager(EnderCrystal crystal) {
        EntityDamageEvent last = crystal.getLastDamageCause();
        if (!(last instanceof EntityDamageByEntityEvent damage)) return null;
        if (damage.getDamager() instanceof Player player) return player;
        if (damage.getDamager() instanceof Projectile projectile
                && projectile.getShooter() instanceof Player player) return player;
        return null;
    }

    public static boolean isComposterFull(Block block) {
        return block.getBlockData() instanceof Levelled levelled
                && levelled.getLevel() >= levelled.getMaximumLevel();
    }

    public static boolean campfireHasFreeSlot(Block block) {
        if (!(block.getState() instanceof Campfire campfire)) return false;
        for (int slot = 0; slot < campfire.getSize(); slot++) {
            ItemStack item = campfire.getItem(slot);
            if (item == null || item.getType().isAir()) return true;
        }
        return false;
    }

    public static String resolveDeathCause(Player player) {
        EntityDamageEvent last = player.getLastDamageCause();
        if (last == null) return null;
        if (last instanceof EntityDamageByEntityEvent damage) {
            Entity damager = damage.getDamager();
            switch (damager.getType()) {
                case IRON_GOLEM:
                    return "IRON_GOLEM";
                case POLAR_BEAR:
                    return "POLAR_BEAR";
                case WARDEN:
                    return "WARDEN";
                case BEE:
                    return "BEE";
                case FIREWORK_ROCKET:
                    return "FIREWORK";
                case TNT_MINECART:
                    return "TNT_MINECART";
                case TRIDENT:
                    return "TRIDENT";
                case FALLING_BLOCK:
                    if (damager instanceof FallingBlock falling) {
                        Material material = falling.getBlockData().getMaterial();
                        if (material.name().contains("ANVIL")) return "ANVIL";
                        if (material == Material.POINTED_DRIPSTONE) return "FALLING_STALACTITE";
                    }
                    break;
                default:
                    break;
            }
        }
        return switch (last.getCause()) {
            case DROWNING -> "DROWNING";
            case VOID -> "VOID";
            case FREEZE -> "FREEZE";
            case MAGIC -> "MAGIC";
            case CONTACT -> contactBlockParam(player);
            default -> null;
        };
    }

    private static String contactBlockParam(Player player) {
        Location location = player.getLocation();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = 0; dy <= 1; dy++) {
                    Material material = location.clone().add(dx, dy, dz).getBlock().getType();
                    if (material == Material.CACTUS) return "CACTUS";
                    if (material == Material.SWEET_BERRY_BUSH) return "BERRY_BUSH";
                }
            }
        }
        return null;
    }
}
