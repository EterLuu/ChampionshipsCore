package ink.ziip.championshipscore.api.game.lasershowdown.runtime;

import ink.ziip.championshipscore.api.game.lasershowdown.model.LaserShowdownLife;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Private weapon-selection inventory used by the laser version of Laser Showdown. */
final class LaserShowdownGunMenuHolder implements InventoryHolder {
    private final LaserShowdownTeamArea area;
    private Inventory inventory;
    private final LaserShowdownLife life;

    LaserShowdownGunMenuHolder(LaserShowdownTeamArea area, LaserShowdownLife life) {
        this.life = life;
        this.area = area;
    }

    LaserShowdownTeamArea area() {
        return area;
    }

    LaserShowdownLife life() {
        return life;
    }

    void inventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
