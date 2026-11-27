package ink.ziip.championshipscore.api.game.hotycodydusky.runtime;

import ink.ziip.championshipscore.api.game.arena.GamePickup;
import ink.ziip.championshipscore.api.game.hotycodydusky.mechanics.BasinLoot;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.platform.bukkit.text.LegacyText;

import io.papermc.paper.datacomponent.DataComponentTypes;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.UUID;

/** The playing-field inventory. Every control and claimed prize belongs to this run. */
final class BasinHotbar {
    static final int SKILL_SLOT = 4;
    private static final int TOOL_SLOTS = 2;
    private static final NamespacedKey OWNER =
            new NamespacedKey("championshipscore", "hoty_loadout");
    private final String session = UUID.randomUUID().toString();
    private final ItemStack filler =
            control(Material.STRUCTURE_VOID, MessageConfig.codeText("hoty-items.slot-filled"));
    private final ItemStack barrier =
            control(
                    Material.BARRIER,
                    MessageConfig.codeText("hoty-items.slot-locked"),
                    MessageConfig.codeText("hoty-items.slot-locked-hint"));
    private final EnumMap<PurpleArenaRun.Ability, ItemStack> icons =
            new EnumMap<>(PurpleArenaRun.Ability.class);

    private final ItemStack wornVest =
            describedControl(
                    Material.PAPER,
                    MessageConfig.codeText("hoty-items.vest-worn"),
                    MessageConfig.codeText("hoty-items.vest-worn-hint"));

    BasinHotbar() {
        wornVest.setData(DataComponentTypes.ITEM_MODEL, Key.key("leather_chestplate"));
    }

    boolean isWornVest(ItemStack item) {
        return wornVest.isSimilar(item);
    }

    boolean hasWornVest(Player player) {
        return BasinLoot.of(player.getInventory().getChestplate()) == BasinLoot.VEST;
    }

    void wearVest(Player player) {
        var inv = player.getInventory();
        ItemStack vest = inv.getItemInMainHand();
        if (!installed(player)
                || !toolSlot(inv.getHeldItemSlot())
                || BasinLoot.of(vest) != BasinLoot.VEST) return;
        var chest = inv.getChestplate();
        if (chest != null && !chest.isEmpty()) return;
        inv.setChestplate(vest);
        inv.setItemInMainHand(wornVest);
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 1, 1);
    }

    boolean breakVest(Player player) {
        var inv = player.getInventory();
        if (BasinLoot.of(inv.getChestplate()) != BasinLoot.VEST) return false;
        inv.setChestplate(null);
        for (int slot = 0; slot < TOOL_SLOTS; slot++) {
            if (isWornVest(inv.getItem(slot))) inv.clear(slot);
        }
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1, 1);
        return true;
    }

    static boolean toolSlot(int slot) {
        return slot >= 0 && slot < TOOL_SLOTS;
    }

    void prepare(Player player) {
        var inventory = player.getInventory();
        int storageSlots = inventory.getStorageContents().length;
        for (int slot = TOOL_SLOTS; slot < storageSlots; slot++)
            inventory.setItem(slot, slot == SKILL_SLOT ? barrier : filler);
    }

    boolean installed(Player player) {
        return owns(player.getInventory().getItem(SKILL_SLOT));
    }

    void show(Player player, PurpleArenaRun.Ability ability) {
        var inv = player.getInventory();
        if (!installed(player)) return;
        inv.setItem(
                SKILL_SLOT,
                ability == null
                        ? barrier
                        : icons.computeIfAbsent(
                                ability,
                                a ->
                                        describedControl(
                                                a.icon,
                                                MessageConfig.codeText(
                                                        "hoty-items.slot-skill", a.title()),
                                                MessageConfig.codeText(a.messageKey + "-hint"),
                                                MessageConfig.codeText("hoty-items.slot-controls"),
                                                MessageConfig.codeText(
                                                        "hoty-items.slot-shortcuts"))));
    }

    boolean give(Player player, BasinLoot loot) {
        var inv = player.getInventory();
        int held = inv.getHeldItemSlot();
        int first = toolSlot(held) ? held : 0;
        int free = -1;
        int carried = BasinLoot.of(player.getItemOnCursor()) == null ? 0 : 1;
        for (int offset = 0; offset < TOOL_SLOTS; offset++) {
            int slot = (first + offset) % TOOL_SLOTS;
            ItemStack current = inv.getItem(slot);
            if (current == null || current.isEmpty()) {
                if (free < 0) free = slot;
            } else carried++;
        }
        if (free < 0 || carried >= TOOL_SLOTS) return false;
        var stack = loot.stack();
        tag(stack);
        inv.setItem(free, stack);
        return true;
    }

    void clear(Player player) {
        var inv = player.getInventory();
        for (int slot = 0; slot < inv.getSize(); slot++)
            if (owns(inv.getItem(slot))) inv.clear(slot);
        if (owns(player.getItemOnCursor())) player.setItemOnCursor(null);
    }

    private boolean owns(ItemStack stack) {
        return stack != null
                && session.equals(
                        stack.getPersistentDataContainer().get(OWNER, PersistentDataType.STRING));
    }

    private void tag(ItemStack stack) {
        stack.editPersistentDataContainer(
                data -> data.set(OWNER, PersistentDataType.STRING, session));
    }

    private ItemStack control(Material material, String title, String... lore) {
        return control(material, text(title), lore);
    }

    private ItemStack describedControl(Material material, String title, String... lore) {
        return control(material, GamePickup.heldName(text(title), text(lore[0])), lore);
    }

    private ItemStack control(Material material, Component title, String... lore) {
        ItemStack stack = new ItemStack(material);
        stack.editMeta(
                meta -> {
                    meta.displayName(title);
                    if (lore.length > 0)
                        meta.lore(Arrays.stream(lore).map(BasinHotbar::text).toList());
                });
        tag(stack);
        return stack;
    }

    private static Component text(String s) {
        return LegacyText.component(s).decoration(TextDecoration.ITALIC, false);
    }
}
