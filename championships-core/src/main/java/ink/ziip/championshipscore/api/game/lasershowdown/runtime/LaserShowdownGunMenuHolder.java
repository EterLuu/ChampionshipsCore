package ink.ziip.championshipscore.api.game.lasershowdown.runtime;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Private weapon-selection inventory used by the laser version of Laser Showdown. */
final class LaserShowdownGunMenuHolder implements InventoryHolder {
    private final LaserShowdownTeamArea area;
    private Inventory inventory;

    LaserShowdownGunMenuHolder(LaserShowdownTeamArea area) {
        this.area = area;
    }

    LaserShowdownTeamArea area() {
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
