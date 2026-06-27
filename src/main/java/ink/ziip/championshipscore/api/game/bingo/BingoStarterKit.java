package ink.ziip.championshipscore.api.game.bingo;

import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * The bingo starter kit handed to every participant at round start:
 * <ul>
 *   <li>team-coloured leather helmet/leggings/boots — Unbreakable + Protection IV (no chestplate,
 *       the slot holds the elytra);</li>
 *   <li>an Unbreakable elytra (chest slot);</li>
 *   <li>16 flight-duration-1 firework rockets (elytra boost);</li>
 *   <li>32 golden carrots;</li>
 *   <li>Efficiency III + Unbreakable stone pickaxe / axe / shovel (the shovel also has Silk Touch),
 *       plus an Unbreakable stone sword; no hoe.</li>
 * </ul>
 *
 * <p>The kit's item types are kept off the generated card (see {@code bingo/tags/starter_kit.yml} +
 * {@code filters.exclude}) so no cell is auto-completed just by holding the kit.
 */
public final class BingoStarterKit {
    private BingoStarterKit() {
    }

    public static void give(Player player, ChampionshipTeam team) {
        if (player == null || team == null) return;
        PlayerInventory inv = player.getInventory();

        // Team-coloured leather armor (helmet/leggings/boots); the chest slot holds the elytra.
        inv.setHelmet(protective(team.getHelmet()));
        inv.setLeggings(protective(team.getLeggings()));
        inv.setBoots(protective(team.getBoots()));
        inv.setChestplate(unbreakable(new ItemStack(Material.ELYTRA)));

        // Stone tools: Efficiency III + Unbreakable; the shovel also gets Silk Touch.
        inv.addItem(tool(Material.STONE_PICKAXE, false));
        inv.addItem(tool(Material.STONE_AXE, false));
        inv.addItem(tool(Material.STONE_SHOVEL, true));
        // Stone sword (Unbreakable; Efficiency doesn't apply to swords).
        inv.addItem(unbreakable(new ItemStack(Material.STONE_SWORD)));

        inv.addItem(new ItemStack(Material.GOLDEN_CARROT, 32));
        inv.addItem(fireworks(16));
    }

    /** Unbreakable + Protection IV on a (team-coloured) leather armor piece. */
    private static ItemStack protective(ItemStack item) {
        if (item == null) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setUnbreakable(true);
            meta.addEnchant(Enchantment.PROTECTION, 4, true);
            item.setItemMeta(meta);
        }
        return item;
    }

    /** Efficiency III + Unbreakable stone tool, optionally with Silk Touch (the shovel). */
    private static ItemStack tool(Material material, boolean silkTouch) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setUnbreakable(true);
            meta.addEnchant(Enchantment.EFFICIENCY, 3, true);
            if (silkTouch) meta.addEnchant(Enchantment.SILK_TOUCH, 1, true);
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

    /** Firework rockets with flight duration 1, for elytra boosting. */
    private static ItemStack fireworks(int amount) {
        ItemStack item = new ItemStack(Material.FIREWORK_ROCKET, amount);
        FireworkMeta meta = (FireworkMeta) item.getItemMeta();
        if (meta != null) {
            meta.setPower(1);
            item.setItemMeta(meta);
        }
        return item;
    }
}
