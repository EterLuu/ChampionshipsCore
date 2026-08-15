package ink.ziip.championshipscore.api.daily;

import ink.ziip.championshipscore.configuration.config.message.ConfiguredGui;
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
    private static final String ITEM_PATH = "daily.hotbar.lobby";
    static final NamespacedKey MARKER = NamespacedKey.fromString("championshipscore:daily_lobby_menu");

    private DailyLobbyItem() {}

    static ItemStack create() {
        // Fallback preserves the click-target marker; gui.yml provides material/title/lore.
        ItemStack fallback = new ItemStack(Material.COMPASS);
        ItemMeta meta = fallback.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("游戏大厅", NamedTextColor.AQUA)
                    .decorate(TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(Component.text("右键打开大厅菜单", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)));
            meta.getPersistentDataContainer().set(MARKER, PersistentDataType.BYTE, (byte) 1);
            fallback.setItemMeta(meta);
        }
        return ConfiguredGui.item(ITEM_PATH, null, java.util.Map.of(), fallback);
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
