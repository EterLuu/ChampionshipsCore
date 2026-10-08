package ink.ziip.championshipscore.api.game.laserbox.runtime;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Holder used to route the private LaserBox weapon-selection inventory to its arena. */
final class LaserBoxGunMenuHolder implements InventoryHolder {
    private final LaserBoxArea area;
    private Inventory inventory;

    LaserBoxGunMenuHolder(LaserBoxArea area) {
        this.area = area;
    }

    LaserBoxArea area() {
        return area;
    }

    void inventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
