package ink.ziip.championshipscore.api.game.iamnotarobot.runtime;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class RobotProps {
    enum Kind {
        WAREHOUSE_PICK,
        SLIDER_HANDLE,
        JOINT_PICK,
        JOINT_BLOCK
    }

    enum SlotPolicy {
        HELD,
        HOTBAR,
        FREE
    }

    private record Saved(int slot, ItemStack item) {}

    static final NamespacedKey KEY = new NamespacedKey("championshipscore", "robot_prop");
    private final String owner = UUID.randomUUID().toString();
    private final Map<Kind, Map<UUID, Saved>> holders = new EnumMap<>(Kind.class);

    RobotProps() {
        for (Kind kind : Kind.values()) holders.put(kind, new HashMap<>());
    }

    static String tag(ItemStack item) {
        return item == null || !item.hasItemMeta()
                ? null
                : item.getItemMeta()
                        .getPersistentDataContainer()
                        .get(KEY, PersistentDataType.STRING);
    }

    static boolean prop(ItemStack item) {
        return tag(item) != null;
    }

    static Kind kind(ItemStack item) {
        String value = tag(item);
        if (value == null || !value.contains("/")) return null;
        try {
            return Kind.valueOf(value.substring(0, value.indexOf('/')));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    static boolean owned(ItemStack item, String owner) {
        String value = tag(item);
        return kind(item) != null && value.substring(value.indexOf('/') + 1).equals(owner);
    }

    boolean owns(ItemStack item, Kind kind) {
        return kind(item) == kind && owned(item, owner);
    }

    private static boolean empty(ItemStack item) {
        return item == null
                || item.getType() == org.bukkit.Material.AIR
                || item.getType() == org.bukkit.Material.CAVE_AIR
                || item.getType() == org.bukkit.Material.VOID_AIR;
    }

    void give(Player player, Kind kind, ItemStack item, SlotPolicy policy) {
        var inventory = player.getInventory();
        var saved = holders.get(kind).get(player.getUniqueId());
        if (saved != null && owns(inventory.getItem(saved.slot()), kind)) return;
        if (saved != null) recover(player, kind);
        int slot = inventory.getHeldItemSlot();
        if (policy == SlotPolicy.FREE) {
            slot = inventory.firstEmpty();
            if (slot < 0) slot = inventory.getHeldItemSlot();
        } else if (policy == SlotPolicy.HOTBAR) {
            slot = -1;
            for (int i = 0; i < 9; i++) {
                if (empty(inventory.getItem(i))) {
                    slot = i;
                    break;
                }
            }
            if (slot < 0) {
                slot = (inventory.getHeldItemSlot() + 1) % 9;
            }
        }
        // A prop must never displace another prop and hide its saved item.
        if (prop(inventory.getItem(slot))) {
            for (int i = 0; i < 9; i++)
                if (!prop(inventory.getItem(i))) {
                    slot = i;
                    break;
                }
        }
        holders.get(kind).put(player.getUniqueId(), new Saved(slot, inventory.getItem(slot)));
        ItemStack copy = item.clone();
        copy.editMeta(
                meta ->
                        meta.getPersistentDataContainer()
                                .set(KEY, PersistentDataType.STRING, kind.name() + "/" + owner));
        inventory.setItem(slot, copy);
    }

    void recover(Player player, Kind kind) {
        Saved saved = holders.get(kind).remove(player.getUniqueId());
        if (saved == null) return;
        remove(player, kind, false, owner);
        if (empty(saved.item())) return;
        var inventory = player.getInventory();
        ItemStack current = inventory.getItem(saved.slot());
        if (empty(current)) inventory.setItem(saved.slot(), saved.item());
        else
            for (ItemStack rest : inventory.addItem(saved.item()).values())
                player.getWorld().dropItem(player.getLocation(), rest);
    }

    void died(Player player, List<ItemStack> drops, boolean keepInventory) {
        drops.removeIf(RobotProps::prop);
        if (keepInventory) {
            for (Kind kind : Kind.values()) recover(player, kind);
            strip(player);
            return;
        }
        for (Kind kind : Kind.values()) {
            Saved saved = holders.get(kind).remove(player.getUniqueId());
            if (saved != null && !empty(saved.item())) drops.add(saved.item());
        }
        strip(player);
    }

    void stripForeign(Player player) {
        remove(player, null, true, owner);
    }

    static void strip(Player player) {
        remove(player, null, false);
    }

    private static void remove(Player player, Kind kind, boolean foreign, String owner) {
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack item = inventory.getItem(i);
            if (prop(item)
                    && (kind == null || kind(item) == kind)
                    && (owner == null || foreign != owned(item, owner))) inventory.setItem(i, null);
        }
        ItemStack cursor = player.getItemOnCursor();
        if (prop(cursor)
                && (kind == null || kind(cursor) == kind)
                && (owner == null || foreign != owned(cursor, owner))) player.setItemOnCursor(null);
    }

    private static void remove(Player player, Kind kind, boolean foreign) {
        remove(player, kind, foreign, null);
    }
}
