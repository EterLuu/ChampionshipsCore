package ink.ziip.championshipscore.api.daily;

import ink.ziip.championshipscore.configuration.config.message.GuiConfig;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/** The only item used to enter the public-play lobby. It is never identified by its display name. */
final class DailyLobbyItem {
    static final NamespacedKey MARKER = NamespacedKey.fromString("championshipscore:daily_lobby_menu");

    private DailyLobbyItem() {}

    static ItemStack create() {
        ItemStack item = new ItemStack(Material.COMPASS);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(GuiConfig.text("api-daily-dailylobbyitem.text-001"), NamedTextColor.AQUA)
                    .decorate(TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(Component.text(GuiConfig.text("api-daily-dailylobbyitem.text-002"), NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)));
            meta.getPersistentDataContainer().set(MARKER, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    static boolean is(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(MARKER, PersistentDataType.BYTE);
    }

    /** Keeps exactly one marked item, and never overwrites a player's existing item. */
    static boolean give(Player player) {
        PlayerInventory inventory = player.getInventory();
        int marked = -1;
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (!is(item)) continue;
            if (marked < 0) marked = slot;
            else inventory.setItem(slot, null);
        }
        if (marked >= 0) return true;
        int empty = inventory.firstEmpty();
        if (empty < 0) return false;
        inventory.setItem(empty, create());
        return true;
    }

    static void take(Player player) {
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (is(inventory.getItem(slot))) inventory.setItem(slot, null);
        }
    }
}
