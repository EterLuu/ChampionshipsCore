package ink.ziip.championshipscore.api.game.riptiderush;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/** Temporary answer items never replace armour, and the original inventory is restored on exit. */
record RiptideColorFloorInventory(ItemStack[] storage, ItemStack offHand) {
    static RiptideColorFloorInventory capture(PlayerInventory inventory) {
        ItemStack[] storage = inventory.getStorageContents().clone();
        for (int i = 0; i < storage.length; i++) storage[i] = copy(storage[i]);
        return new RiptideColorFloorInventory(storage, copy(inventory.getItemInOffHand()));
    }

    static void show(PlayerInventory inventory, ItemStack target) {
        ItemStack[] items = new ItemStack[inventory.getStorageContents().length];
        for (int i = 0; i < items.length; i++) items[i] = target.clone();
        inventory.setStorageContents(items);
        inventory.setItemInOffHand(target.clone());
    }

    void restore(PlayerInventory inventory) {
        inventory.setStorageContents(storage);
        inventory.setItemInOffHand(offHand);
    }

    private static ItemStack copy(ItemStack item) { return item == null ? null : item.clone(); }
}
