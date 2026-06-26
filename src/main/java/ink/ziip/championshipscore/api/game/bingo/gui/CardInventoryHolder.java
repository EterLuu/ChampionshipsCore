package ink.ziip.championshipscore.api.game.bingo.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/** Marker holder so the click listener can recognise (and lock) the read-only card view. */
public final class CardInventoryHolder implements InventoryHolder {
    private Inventory inventory;

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
