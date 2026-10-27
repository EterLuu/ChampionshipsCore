package ink.ziip.championshipscore.api.game.bingo.gui;

import ink.ziip.championshipscore.platform.bukkit.gui.BingoCardMenu;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/** Keeps the card view read-only by cancelling any interaction with it. */
public final class CardMenuListener implements Listener {

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (BingoCardMenu.owns(event.getView().getTopInventory())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (BingoCardMenu.owns(event.getView().getTopInventory())) {
            event.setCancelled(true);
        }
    }
}
