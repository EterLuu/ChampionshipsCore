package ink.ziip.championshipscore.api.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.Nullable;

/** Marks an inventory as a read-only menu rather than a container for player items. */
public interface MenuInventory extends InventoryHolder {
    /** Cancels all item movement and returns only a click in this menu's top inventory. */
    static @Nullable Player clickedPlayer(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof MenuInventory)) return null;
        event.setCancelled(true);
        return event.getClickedInventory() == top && event.getWhoClicked() instanceof Player player
                ? player : null;
    }
}
