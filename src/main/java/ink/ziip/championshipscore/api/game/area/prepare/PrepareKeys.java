package ink.ziip.championshipscore.api.game.area.prepare;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Persistent-data keys and item-building helpers shared across the prepare subsystem. Every item placed
 * into a prepare-mode inventory is tagged with {@link #MARKER} so the listener can recognise it and block
 * vanilla use (throwing, placing, dropping). Step items additionally carry {@link #STEP_KEY}; control
 * items (teleport/exit) carry {@link #ACTION}.
 */
public final class PrepareKeys {
    /** Present on every prepare-mode item; value is always {@code "1"}. */
    public static final NamespacedKey MARKER =
            NamespacedKey.fromString("championshipscore:prepare_marker");
    /** Step item -> the step's key. */
    public static final NamespacedKey STEP_KEY =
            NamespacedKey.fromString("championshipscore:prepare_step");
    /** Control item -> one of {@code teleport}/{@code steps}/{@code validate}/{@code publish}/{@code exit}. */
    public static final NamespacedKey ACTION =
            NamespacedKey.fromString("championshipscore:prepare_action");

    private PrepareKeys() {
    }

    /** True if the item belongs to a prepare-mode inventory (has the marker). */
    public static boolean isPrepareItem(@Nullable ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(MARKER, PersistentDataType.STRING);
    }

    /** Tags an item as a prepare-mode item (no step/action payload). */
    public static void tagPrepare(@NotNull ItemStack item) {
        if (item.getType().isAir()) return;
        item.editMeta(m -> m.getPersistentDataContainer().set(MARKER, PersistentDataType.STRING, "1"));
    }

    public static void setStep(@NotNull ItemStack item, @NotNull String key) {
        tagPrepare(item);
        item.editMeta(m -> m.getPersistentDataContainer().set(STEP_KEY, PersistentDataType.STRING, key));
    }

    public static @Nullable String stepKeyOf(@Nullable ItemStack item) {
        return stringOf(item, STEP_KEY);
    }

    public static void setAction(@NotNull ItemStack item, @NotNull String action) {
        tagPrepare(item);
        item.editMeta(m -> m.getPersistentDataContainer().set(ACTION, PersistentDataType.STRING, action));
    }

    public static @Nullable String actionOf(@Nullable ItemStack item) {
        return stringOf(item, ACTION);
    }

    private static @Nullable String stringOf(@Nullable ItemStack item, @NotNull NamespacedKey key) {
        if (!isPrepareItem(item)) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        return meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }

    /** Builds a tagged prepare-mode item with a non-italic name and lore. */
    public static @NotNull ItemStack item(@NotNull Material material, @NotNull Component name, @Nullable List<Component> lore) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> {
            meta.displayName(name.decoration(TextDecoration.ITALIC, false));
            if (lore != null && !lore.isEmpty()) {
                meta.lore(lore.stream().map(c -> c.decoration(TextDecoration.ITALIC, false)).toList());
            }
        });
        return item;
    }
}
