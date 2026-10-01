package ink.ziip.championshipscore.api.game.frostbite;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

final class FrostbiteFrozenEquipment {
    private FrostbiteFrozenEquipment() {}

    static void apply(PlayerInventory inventory) {
        ItemStack[] storage = new ItemStack[inventory.getStorageContents().length];
        for (int i = 0; i < storage.length; i++) storage[i] = new ItemStack(Material.ICE);
        inventory.setStorageContents(storage);
        ItemStack[] armor = new ItemStack[4];
        for (int i = 0; i < armor.length; i++) armor[i] = new ItemStack(Material.ICE);
        inventory.setArmorContents(armor);
        inventory.setItemInOffHand(new ItemStack(Material.ICE));
    }

    static void clear(PlayerInventory inventory) {
        inventory.clear();
        inventory.setArmorContents(new ItemStack[4]);
        inventory.setItemInOffHand(null);
    }
}
