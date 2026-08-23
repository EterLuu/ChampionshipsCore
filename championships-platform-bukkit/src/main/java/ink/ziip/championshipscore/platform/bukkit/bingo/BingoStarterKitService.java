package ink.ziip.championshipscore.platform.bukkit.bingo;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.keys.EnchantmentKeys;
import net.kyori.adventure.text.Component;
import ink.ziip.championshipscore.protocol.BingoRemix;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Identical Bingo starting equipment shared by local Core and the dedicated Folia worker. */
public final class BingoStarterKitService {
    private static final Registry<Enchantment> ENCHANTMENTS =
            RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT);
    private static final List<ItemStack> KIT_ITEMS = List.of(
            tool(Material.STONE_PICKAXE), tool(Material.STONE_AXE), tool(Material.STONE_SHOVEL),
            ironSword(), new ItemStack(Material.BREAD, 32));
    private static final Map<Material, Integer> PROVIDED = buildProvided();
    private static final Set<String> CONFLICTING_ADVANCEMENTS = gearAdvancementConflicts(PROVIDED.keySet());

    private BingoStarterKitService() {
    }

    public static void give(Player player, Color teamColor, Component compassName,
                            List<Component> compassLore) {
        give(player, teamColor, compassName, compassLore, BingoRemix.NONE);
    }

    public static void give(Player player, Color teamColor, Component compassName,
                            List<Component> compassLore, BingoRemix remix) {
        if (player == null) return;
        Color color = teamColor == null ? Color.WHITE : teamColor;
        PlayerInventory inventory = player.getInventory();
        List<ItemStack> kit = KIT_ITEMS;
        if (remix == BingoRemix.UPGRADE) {
            inventory.setHelmet(unbreakable(new ItemStack(Material.NETHERITE_HELMET)));
            inventory.setLeggings(unbreakable(new ItemStack(Material.NETHERITE_LEGGINGS)));
            inventory.setBoots(unbreakable(new ItemStack(Material.NETHERITE_BOOTS)));
            inventory.setChestplate(unbreakable(new ItemStack(Material.ELYTRA)));
            kit = List.of(tool(Material.NETHERITE_PICKAXE), tool(Material.NETHERITE_AXE),
                    tool(Material.NETHERITE_SHOVEL), unbreakable(new ItemStack(Material.NETHERITE_SWORD)),
                    new ItemStack(Material.FIREWORK_ROCKET, 16), new ItemStack(Material.BREAD, 32));
        } else if (remix == BingoRemix.SPEEDRUN) {
            inventory.setHelmet(protective(leather(Material.LEATHER_HELMET, color)));
            inventory.setLeggings(unbreakable(new ItemStack(Material.GOLDEN_LEGGINGS)));
            inventory.setBoots(protectiveBoots(leather(Material.LEATHER_BOOTS, color)));
            inventory.setChestplate(unbreakable(new ItemStack(Material.NETHERITE_CHESTPLATE)));
            kit = List.of(tool(Material.DIAMOND_PICKAXE), tool(Material.DIAMOND_AXE),
                    tool(Material.DIAMOND_SHOVEL), unbreakable(new ItemStack(Material.DIAMOND_SWORD)),
                    new ItemStack(Material.WATER_BUCKET), new ItemStack(Material.FLINT_AND_STEEL),
                    new ItemStack(Material.OBSIDIAN, 12), new ItemStack(Material.BREAD, 16));
        } else {
            inventory.setHelmet(protective(leather(Material.LEATHER_HELMET, color)));
            inventory.setLeggings(protective(leather(Material.LEATHER_LEGGINGS, color)));
            inventory.setBoots(protectiveBoots(leather(Material.LEATHER_BOOTS, color)));
            inventory.setChestplate(unbreakable(new ItemStack(Material.ELYTRA)));
        }
        for (ItemStack item : kit) {
            for (ItemStack overflow : inventory.addItem(item.clone()).values()) {
                player.getWorld().dropItem(player.getLocation(), overflow);
            }
        }
        for (ItemStack overflow : inventory.addItem(compass(compassName, compassLore)).values()) {
            player.getWorld().dropItem(player.getLocation(), overflow);
        }
    }

    public static boolean hasKit(Player player) {
        if (player == null) return false;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || !item.getType().name().endsWith("_PICKAXE")) continue;
            ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.isUnbreakable()) return true;
        }
        return false;
    }

    public static int providedAmount(Material material) {
        return PROVIDED.getOrDefault(material, 0);
    }

    public static Set<String> conflictingAdvancementKeys() {
        return CONFLICTING_ADVANCEMENTS;
    }

    private static Map<Material, Integer> buildProvided() {
        Map<Material, Integer> provided = new EnumMap<>(Material.class);
        for (ItemStack item : KIT_ITEMS) provided.merge(item.getType(), item.getAmount(), Integer::sum);
        provided.put(Material.COMPASS, 1);
        provided.put(Material.LEATHER_HELMET, 1);
        provided.put(Material.LEATHER_LEGGINGS, 1);
        provided.put(Material.LEATHER_BOOTS, 1);
        provided.put(Material.ELYTRA, 1);
        return Map.copyOf(provided);
    }

    private static ItemStack leather(Material material, Color color) {
        ItemStack item = new ItemStack(material);
        if (item.getItemMeta() instanceof LeatherArmorMeta meta) {
            meta.setColor(color);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack protective(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setUnbreakable(true);
            meta.addEnchant(ENCHANTMENTS.getOrThrow(EnchantmentKeys.PROTECTION), 4, true);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack protectiveBoots(ItemStack item) {
        item = protective(item);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.addEnchant(ENCHANTMENTS.getOrThrow(EnchantmentKeys.FEATHER_FALLING), 4, true);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack tool(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setUnbreakable(true);
            meta.addEnchant(ENCHANTMENTS.getOrThrow(EnchantmentKeys.EFFICIENCY), 3, true);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack ironSword() {
        ItemStack item = new ItemStack(Material.IRON_SWORD);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setUnbreakable(true);
            meta.addEnchant(ENCHANTMENTS.getOrThrow(EnchantmentKeys.SHARPNESS), 2, true);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack compass(Component name, List<Component> lore) {
        ItemStack item = new ItemStack(Material.COMPASS);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(name);
            meta.lore(List.copyOf(lore));
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack unbreakable(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setUnbreakable(true);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static Set<String> gearAdvancementConflicts(Set<Material> materials) {
        Set<String> keys = new HashSet<>();
        for (Material material : materials) {
            String name = material.name();
            if (name.endsWith("_PICKAXE") && !name.startsWith("WOODEN_") && !name.startsWith("GOLDEN_")) {
                keys.add("story/upgrade_tools");
            }
            if (name.equals("IRON_PICKAXE")) keys.add("story/iron_tools");
            if (name.equals("ELYTRA")) keys.add("end/elytra");
            if (isArmor(name)) {
                keys.add("story/obtain_armor");
                if (name.startsWith("DIAMOND_") || name.startsWith("NETHERITE_")) keys.add("story/shiny_gear");
            }
        }
        return Set.copyOf(keys);
    }

    private static boolean isArmor(String name) {
        return name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE")
                || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS");
    }
}
