package ink.ziip.championshipscore.api.game.riptiderush;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.*;

class RiptideColorFloorInventoryTest {
    @Test
    void everyStorageSlotAndOffhandChangeTogetherAndRestoreOriginalItems() {
        var state = new InventoryState();
        state.storage[0] = new TestItem(Material.STICK, 3);
        state.storage[35] = new TestItem(Material.APPLE, 2);
        state.offHand = new TestItem(Material.TORCH, 7);
        var saved = RiptideColorFloorInventory.capture(state.inventory());
        for (Material target : new Material[]{Material.RED_CONCRETE, Material.WAXED_OXIDIZED_CHISELED_COPPER}) {
            RiptideColorFloorInventory.show(state.inventory(), new TestItem(target, 64));
            for (ItemStack item : state.storage) {
                assertEquals(target, item.getType());
                assertEquals(64, item.getAmount());
            }
            assertNotSame(state.storage[0], state.storage[1]);
            assertEquals(target, state.offHand.getType());
            assertEquals(64, state.offHand.getAmount());
        }
        saved.restore(state.inventory());
        assertEquals(Material.STICK, state.storage[0].getType());
        assertEquals(3, state.storage[0].getAmount());
        assertNull(state.storage[1]);
        assertEquals(Material.APPLE, state.storage[35].getType());
        assertEquals(Material.TORCH, state.offHand.getType());
        assertEquals(7, state.offHand.getAmount());
    }

    // Paper creates real ItemStacks through the running server's registry. This fixture tests
    // snapshot ownership and slot updates without pretending to provide that registry.
    private static class TestItem extends ItemStack {
        private final Material material;
        private final int amount;
        TestItem(Material material, int amount) { this.material = material; this.amount = amount; }
        @Override public Material getType() { return material; }
        @Override public int getAmount() { return amount; }
        @Override public ItemStack clone() { return new TestItem(material, amount); }
    }

    private static class InventoryState {
        ItemStack[] storage = new ItemStack[36];
        ItemStack offHand;
        PlayerInventory inventory() {
            return (PlayerInventory) Proxy.newProxyInstance(PlayerInventory.class.getClassLoader(),
                    new Class<?>[]{PlayerInventory.class}, (proxy, method, args) -> switch (method.getName()) {
                        case "getStorageContents" -> storage.clone();
                        case "getItemInOffHand" -> offHand;
                        case "setStorageContents" -> { storage = ((ItemStack[]) args[0]).clone(); yield null; }
                        case "setItemInOffHand" -> { offHand = (ItemStack) args[0]; yield null; }
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
        }
    }
}
