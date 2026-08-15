package ink.ziip.championshipscore.api.gui;

import ink.ziip.championshipscore.configuration.config.message.ConfiguredGui;
import ink.ziip.championshipscore.configuration.config.message.GuiConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Shared skeleton for every gui.yml-driven inventory.
 *
 * <p>Concrete menus extend this class (instead of declaring their own {@code implements
 * InventoryHolder} inner holder) and inherit the viewer/pagination state plus the common item,
 * border and slot helpers that were previously duplicated in ~10 menu classes.</p>
 */
public abstract class GuiMenu implements InventoryHolder {
    protected final UUID viewer;
    protected Inventory inventory;
    protected int page;
    protected int pageCount = 1;

    protected GuiMenu(@NotNull UUID viewer) {
        this.viewer = viewer;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    // ----- configuration helpers -------------------------------------------------------------

    /** Reads {@code title}/{@code size}/{@code layout.content} for the menu. */
    protected static GuiConfig.MenuSpec menu(@NotNull MenuId id, int fallbackSize,
                                             @NotNull String fallbackTitle,
                                             @NotNull List<Integer> fallbackSlots) {
        return GuiConfig.menu(id.path(), fallbackSize, fallbackTitle, fallbackSlots);
    }

    /** Reads {@code title}/{@code size}/{@code layout.content} for the menu. */
    protected static GuiConfig.MenuSpec menu(@NotNull MenuId id, int fallbackSize,
                                             @NotNull Component fallbackTitle,
                                             @NotNull List<Integer> fallbackSlots) {
        return GuiConfig.menu(id.path(), fallbackSize, fallbackTitle, fallbackSlots);
    }

    /** Resolves a fixed item's slot from {@code <menu>.items.<item>.slot}. */
    protected static int itemSlot(@NotNull MenuId id, @NotNull String item, int fallback) {
        return ConfiguredGui.slot(id.item(item), fallback);
    }

    /** Renders a configured item over an existing fallback stack (PDC/metadata preserved). */
    protected static ItemStack configured(@NotNull MenuId id, @NotNull String item, String state,
                                          @NotNull Map<String, ?> placeholders,
                                          @NotNull ItemStack fallback) {
        return ConfiguredGui.item(id.item(item), state, placeholders, fallback);
    }

    /** Fills the border slots declared in {@code <menu>.layout.border}. */
    protected static void fillBorder(@NotNull Inventory inventory, @NotNull MenuId id,
                                     @NotNull List<Integer> fallbackSlots) {
        GuiConfig.ItemSpec border = GuiConfig.item(id.item("border"), Map.of());
        for (int slot : GuiConfig.slots(id.layout("border"), fallbackSlots)) {
            if (slot >= 0 && slot < inventory.getSize()) {
                inventory.setItem(slot, item(border.material(), border.title(), border.lore(), border.glint()));
            }
        }
    }

    /**
     * Renders the standard navigation footer declared in {@code <menu>.layout.footer}.
     *
     * <p>The slot for each control comes from the menu config; the display text always comes from
     * {@code common.copy} so a single edit updates every menu. The {@code page} control additionally
     * receives {@code %page%}/{@code %pages%}/{@code %count%} placeholders.</p>
     */
    protected static void fillFooter(@NotNull Inventory inventory, @NotNull MenuId id,
                                     @NotNull Map<String, Object> pagePlaceholders) {
        fillFooterItem(inventory, id, "previous", "previous-page", Map.of(), Material.ARROW);
        fillFooterItem(inventory, id, "next", "next-page", Map.of(), Material.ARROW);
        fillFooterItem(inventory, id, "page", "page", pagePlaceholders, Material.PAPER);
        fillFooterItem(inventory, id, "back", "back", Map.of(), Material.ARROW);
        fillFooterItem(inventory, id, "close", "close", Map.of(), Material.BARRIER);
        fillFooterItem(inventory, id, "refresh", "refresh", Map.of(), Material.CLOCK);
    }

    private static void fillFooterItem(@NotNull Inventory inventory, @NotNull MenuId id,
                                       @NotNull String control, @NotNull String textKey,
                                       @NotNull Map<String, Object> placeholders, @NotNull Material material) {
        int slot = GuiConfig.integer(id.layout("footer." + control), -1);
        if (slot < 0 || slot >= inventory.getSize()) return;
        String prefix = "common.copy.";
        String text = GuiConfig.text(prefix + textKey, placeholders);
        inventory.setItem(slot, item(material, Component.text(text), List.of(), false));
    }

    // ----- shared item builders --------------------------------------------------------------

    /** Builds a plain item with the common italic-off presentation applied to name and lore. */
    public static ItemStack item(@NotNull Material material, @NotNull Component name,
                                 @NotNull List<Component> lore, boolean glint) {
        return item(material, name, lore, glint, 1);
    }

    /** Builds a plain item without enchantment glint. */
    public static ItemStack item(@NotNull Material material, @NotNull Component name,
                                 @NotNull List<Component> lore) {
        return item(material, name, lore, false, 1);
    }

    /** Builds a plain item with an explicit stack amount. */
    public static ItemStack item(@NotNull Material material, @NotNull Component name,
                                 @NotNull List<Component> lore, boolean glint, int amount) {
        ItemStack stack = new ItemStack(material, amount);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(name.decoration(TextDecoration.ITALIC, false));
            meta.lore(lore.stream().map(line -> line.decoration(TextDecoration.ITALIC, false)).toList());
            meta.setEnchantmentGlintOverride(glint);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    /** Builds a player-head item, matching the presentation used by the daily menus. */
    public static ItemStack playerHead(@NotNull UUID owner, @NotNull Component name,
                                       @NotNull List<Component> lore, boolean glint) {
        ItemStack stack = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) stack.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(owner));
            meta.displayName(name.decoration(TextDecoration.ITALIC, false));
            meta.lore(lore.stream().map(line -> line.decoration(TextDecoration.ITALIC, false)).toList());
            meta.setEnchantmentGlintOverride(glint);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    /** Builds a player-head item without enchantment glint. */
    public static ItemStack playerHead(@NotNull UUID owner, @NotNull Component name,
                                       @NotNull List<Component> lore) {
        return playerHead(owner, name, lore, false);
    }

    // ----- pagination / layout helpers -------------------------------------------------------

    /** Clamps the current page into the valid range after {@code pageCount} changes. */
    protected void clampPage() {
        page = Math.max(0, Math.min(page, pageCount - 1));
    }

    /** Centres up to 9 entries on a single inventory row. */
    protected static List<Integer> centeredRow(int rowStart, int count) {
        List<Integer> slots = new ArrayList<>(count);
        int first = rowStart + (9 - count) / 2;
        for (int index = 0; index < count; index++) slots.add(first + index);
        return slots;
    }

    protected static void clickSound(@NotNull Player player, float pitch) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7F, pitch);
    }
}
