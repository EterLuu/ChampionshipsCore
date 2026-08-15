package ink.ziip.championshipscore.api.game.area.prepare.gui;

import ink.ziip.championshipscore.api.gui.MenuId;
import ink.ziip.championshipscore.configuration.config.message.ConfiguredGui;
import ink.ziip.championshipscore.configuration.config.message.GuiConfig;

import ink.ziip.championshipscore.api.game.area.prepare.PrepareModeInventory;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareSession;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareSessionManager;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareStep;
import ink.ziip.championshipscore.api.game.area.prepare.step.AceRaceProgressPointListStep;
import ink.ziip.championshipscore.api.game.area.prepare.step.AceRaceRespawnPointListStep;
import ink.ziip.championshipscore.util.Utils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/** Safe list editor: add, inspect, edit, reorder, or remove one configured row at a time. */
public final class ListStepGui {
    private static final String MENU_PATH = MenuId.MAP_EDITOR_LIST_EDITOR.path();
    private static final int ADD_SLOT = 0;
    private static final int VIEW_SLOT = 1;
    private static final int INFO_SLOT = 4;
    private static final int BACK_SLOT = 8;

    private static final int ENTRY_FIRST_SLOT = 0;
    private static final int ENTRY_LAST_SLOT = 44;
    private static final int ENTRY_PAGE_SIZE = 45;
    private static final int PREVIOUS_SLOT = 45;
    private static final int ENTRY_BACK_SLOT = 49;
    private static final int NEXT_SLOT = 53;

    private ListStepGui() {
    }

    public static final class Holder implements InventoryHolder {
        final String stepKey;
        Inventory inventory;

        Holder(String stepKey) {
            this.stepKey = stepKey;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }

    public static final class EntryHolder implements InventoryHolder {
        final PrepareSession session;
        final String stepKey;
        int page;
        Inventory inventory;

        EntryHolder(@NotNull PrepareSession session, @NotNull String stepKey) {
            this.session = session;
            this.stepKey = stepKey;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }

    public static final class EditHolder implements InventoryHolder {
        final PrepareSession session;
        final String stepKey;
        final int index;
        Inventory inventory;

        EditHolder(@NotNull PrepareSession session, @NotNull String stepKey, int index) {
            this.session = session;
            this.stepKey = stepKey;
            this.index = index;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }

    public static void open(@NotNull PrepareSessionManager manager, @NotNull Player player,
                            @NotNull PrepareSession session, @NotNull PrepareStep step) {
        Holder holder = new Holder(step.key());
        Inventory inv = Bukkit.createInventory(holder, 9,
                Component.text(GuiConfig.text("map-editor.menus.list-editor.copy.point-management") + PlainTextComponentSerializer.plainText().serialize(step.displayName()))
                        .decoration(TextDecoration.ITALIC, false));
        holder.inventory = inv;
        refresh(inv, session, step);
        player.openInventory(inv);
    }

    private static void refresh(@NotNull Inventory inv, @NotNull PrepareSession session, @NotNull PrepareStep step) {
        inv.setItem(ADD_SLOT, item(Material.LIME_WOOL, step.listAddLabel(), NamedTextColor.GREEN,
                List.of(step.listAddHint().color(NamedTextColor.GRAY))));
        inv.setItem(VIEW_SLOT, configured("view", null, java.util.Map.of(), Material.BOOK,
                Component.text(GuiConfig.text(MENU_PATH + ".copy.view-the-set-list")),
                List.of(Component.text(GuiConfig.text(MENU_PATH + ".copy.edit-serial-number-item-by-item-actual-information-or-delete")).color(NamedTextColor.GRAY))));
        inv.setItem(INFO_SLOT, item(Material.PAPER, Component.text(GuiConfig.text(MENU_PATH + ".copy.current-number-of-points") + step.listCount(session)), NamedTextColor.WHITE,
                List.of(Component.text(step.isSet(session) ? GuiConfig.text("map-editor.copy.already-set") : GuiConfig.text("map-editor.copy.not-set")).color(NamedTextColor.GRAY))));
        inv.setItem(BACK_SLOT, configured("back", null, java.util.Map.of(), Material.ARROW,
                Component.text(GuiConfig.text("map-editor.copy.return")),
                List.of(Component.text(GuiConfig.text("map-editor.copy.return-to-prepare-toolbar")).color(NamedTextColor.GRAY))));
    }

    private static ItemStack configured(@NotNull String item, String state, @NotNull java.util.Map<String, ?> placeholders,
                                        @NotNull Material material, @NotNull Component title, @NotNull List<Component> lore) {
        return ConfiguredGui.item(MENU_PATH + ".items." + item, state, placeholders, material, title, lore, false);
    }

    public static void handleClick(@NotNull PrepareSessionManager manager, @NotNull InventoryClickEvent event,
                                   @NotNull Player player, @NotNull Holder holder) {
        event.setCancelled(true);
        Inventory top = event.getView().getTopInventory();
        if (event.getClickedInventory() != top) return;
        PrepareSession session = manager.getSession(player);
        if (session == null) {
            player.closeInventory();
            return;
        }
        PrepareStep step = session.step(holder.stepKey);
        if (step == null) {
            player.closeInventory();
            return;
        }
        switch (event.getRawSlot()) {
            case ADD_SLOT -> {
                if (!session.getFlow().isInCorrectWorld(player, session.getTarget())) {
                    Utils.sendAdminError(player, GuiConfig.text("map-editor.copy.please-go-to-the-current-map-world-first") + session.getTarget().worldName());
                    return;
                }
                String message = step.listAdd(session, player);
                if (message != null) player.sendMessage(message);
                refresh(top, session, step);
                PrepareModeInventory.refresh(player, session);
            }
            case VIEW_SLOT -> openEntries(player, session, step);
            case BACK_SLOT -> {
                player.closeInventory();
                PrepareModeInventory.refresh(player, session);
            }
            default -> {
            }
        }
    }

    private static void openEntries(@NotNull Player player, @NotNull PrepareSession session,
                                    @NotNull PrepareStep step) {
        EntryHolder holder = new EntryHolder(session, step.key());
        holder.inventory = Bukkit.createInventory(holder, 54,
                Component.text(GuiConfig.text("map-editor.menus.list-editor.copy.list-set") + PlainTextComponentSerializer.plainText().serialize(step.displayName()))
                        .decoration(TextDecoration.ITALIC, false));
        refreshEntries(holder, step);
        player.openInventory(holder.inventory);
    }

    private static void refreshEntries(@NotNull EntryHolder holder, @NotNull PrepareStep step) {
        Inventory inv = holder.inventory;
        inv.clear();
        List<PrepareStep.ListEntry> entries = step.listEntries(holder.session);
        int pageCount = Math.max(1, (entries.size() + ENTRY_PAGE_SIZE - 1) / ENTRY_PAGE_SIZE);
        holder.page = Math.max(0, Math.min(holder.page, pageCount - 1));
        int first = holder.page * ENTRY_PAGE_SIZE;
        for (int slot = ENTRY_FIRST_SLOT; slot <= ENTRY_LAST_SLOT; slot++) {
            int index = first + slot;
            if (index < entries.size()) {
                PrepareStep.ListEntry entry = entries.get(index);
                List<Component> lore = new ArrayList<>();
                for (String detail : entry.details()) lore.add(Component.text(detail).color(NamedTextColor.GRAY));
                lore.add(Component.text(GuiConfig.text("map-editor.menus.list-editor.copy.click-to-edit-this")).color(NamedTextColor.AQUA));
                inv.setItem(slot, item(Material.PAPER, Component.text(entry.title()), NamedTextColor.WHITE, lore));
            } else {
                inv.setItem(slot, filler());
            }
        }
        inv.setItem(PREVIOUS_SLOT, holder.page > 0
                ? item(Material.ARROW, Component.text(GuiConfig.text("map-editor.copy.previous-page")), NamedTextColor.WHITE,
                List.of(Component.text(GuiConfig.text("map-editor.copy.ordinal-prefix") + holder.page + GuiConfig.text("map-editor.copy.page-suffix")).color(NamedTextColor.GRAY)))
                : filler());
        inv.setItem(ENTRY_BACK_SLOT, item(Material.BARRIER, Component.text(GuiConfig.text("map-editor.menus.list-editor.copy.return-to-point-management")), NamedTextColor.RED,
                List.of()));
        inv.setItem(NEXT_SLOT, holder.page + 1 < pageCount
                ? item(Material.ARROW, Component.text(GuiConfig.text("map-editor.copy.next-page")), NamedTextColor.WHITE,
                List.of(Component.text(GuiConfig.text("map-editor.copy.ordinal-prefix") + (holder.page + 2) + " / " + pageCount + GuiConfig.text("map-editor.copy.page-suffix")).color(NamedTextColor.GRAY)))
                : filler());
    }

    public static void handleEntryClick(@NotNull PrepareSessionManager manager,
                                        @NotNull InventoryClickEvent event, @NotNull Player player,
                                        @NotNull EntryHolder holder) {
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        PrepareSession session = manager.getSession(player);
        if (session == null || session != holder.session) {
            player.closeInventory();
            return;
        }
        PrepareStep step = session.step(holder.stepKey);
        if (step == null) {
            player.closeInventory();
            return;
        }
        int slot = event.getRawSlot();
        if (slot == PREVIOUS_SLOT) {
            if (holder.page > 0) {
                holder.page--;
                refreshEntries(holder, step);
            }
            return;
        }
        if (slot == NEXT_SLOT) {
            int pageCount = Math.max(1, (step.listEntries(session).size() + ENTRY_PAGE_SIZE - 1) / ENTRY_PAGE_SIZE);
            if (holder.page + 1 < pageCount) {
                holder.page++;
                refreshEntries(holder, step);
            }
            return;
        }
        if (slot == ENTRY_BACK_SLOT) {
            open(manager, player, session, step);
            return;
        }
        if (slot < ENTRY_FIRST_SLOT || slot > ENTRY_LAST_SLOT) return;
        int index = holder.page * ENTRY_PAGE_SIZE + slot;
        if (index < step.listEntries(session).size()) openEdit(player, session, step, index);
    }

    public static void openEdit(@NotNull Player player, @NotNull PrepareSession session,
                                @NotNull PrepareStep step, int index) {
        EditHolder holder = new EditHolder(session, step.key(), index);
        holder.inventory = Bukkit.createInventory(holder, 9,
                Component.text(GuiConfig.text("map-editor.menus.list-editor.copy.edit-point") + (index + 1)).decoration(TextDecoration.ITALIC, false));
        holder.inventory.setItem(0, item(Material.NAME_TAG, Component.text(GuiConfig.text("map-editor.menus.list-editor.copy.edit-serial-number")), NamedTextColor.YELLOW,
                List.of(Component.text(GuiConfig.text("map-editor.menus.list-editor.copy.adjust-the-order-of-this-item-in-the-list")).color(NamedTextColor.GRAY))));
        if (step instanceof AceRaceRespawnPointListStep respawnStep) {
            holder.inventory.setItem(2, item(Material.IRON_BARS, Component.text(GuiConfig.text("map-editor.menus.list-editor.copy.edit-own-progress-line")), NamedTextColor.LIGHT_PURPLE,
                    List.of(Component.text(respawnStep.bindingText(session, index)).color(NamedTextColor.GRAY),
                            Component.text(GuiConfig.text("map-editor.menus.list-editor.copy.select-which-progress-line-the-respawn-point-is-behind")).color(NamedTextColor.GRAY))));
        } else if (step instanceof AceRaceProgressPointListStep progressPointStep) {
            holder.inventory.setItem(2, item(Material.HEART_OF_THE_SEA,
                    Component.text(GuiConfig.text("map-editor.menus.list-editor.copy.edit-stage-equipment")), NamedTextColor.LIGHT_PURPLE,
                    List.of(Component.text(progressPointStep.equipmentText(session, index)).color(NamedTextColor.GRAY),
                            Component.text(GuiConfig.text("map-editor.menus.list-editor.copy.do-not-modify-line-selection-and-drop-height")).color(NamedTextColor.GRAY))));
        }
        holder.inventory.setItem(4, item(Material.COMPASS, Component.text(GuiConfig.text("map-editor.menus.list-editor.copy.edit-actual-information")), NamedTextColor.AQUA,
                List.of(Component.text(GuiConfig.text("map-editor.menus.list-editor.copy.use-current-position-current-selection-override")).color(NamedTextColor.GRAY))));
        holder.inventory.setItem(6, item(Material.RED_WOOL, Component.text(GuiConfig.text("map-editor.menus.list-editor.copy.delete-this-item")), NamedTextColor.RED,
                List.of(Component.text(GuiConfig.text("map-editor.menus.list-editor.copy.only-delete-the-current-item")).color(NamedTextColor.GRAY))));
        holder.inventory.setItem(8, item(Material.ARROW, Component.text(GuiConfig.text("map-editor.menus.list-editor.copy.return-to-list")), NamedTextColor.WHITE, List.of()));
        player.openInventory(holder.inventory);
    }

    public static void handleEditClick(@NotNull PrepareSessionManager manager,
                                       @NotNull InventoryClickEvent event, @NotNull Player player,
                                       @NotNull EditHolder holder) {
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        PrepareSession session = manager.getSession(player);
        if (session == null || session != holder.session) {
            player.closeInventory();
            return;
        }
        PrepareStep step = session.step(holder.stepKey);
        if (step == null) {
            player.closeInventory();
            return;
        }
        switch (event.getRawSlot()) {
            case 0 -> AnvilInputGui.openInteger(player, GuiConfig.text("map-editor.menus.list-editor.copy.enter-new-serial-number"), holder.index + 1, value -> {
                String message = step.listSetOrder(session, player, holder.index, value);
                if (message != null) player.sendMessage(message);
                openEntries(player, session, step);
            });
            case 2 -> {
                if (step instanceof AceRaceRespawnPointListStep)
                    AceRaceRespawnPointBindingGui.open(manager, player, session, holder.index);
                else if (step instanceof AceRaceProgressPointListStep progressPointStep)
                    progressPointStep.editEquipment(session, player, holder.index);
            }
            case 4 -> {
                if (!session.getFlow().isInCorrectWorld(player, session.getTarget())) {
                    Utils.sendAdminError(player, GuiConfig.text("map-editor.copy.please-go-to-the-current-map-world-first") + session.getTarget().worldName());
                    return;
                }
                String message = step.listEdit(session, player, holder.index);
                if (message != null) player.sendMessage(message);
                if (!step.listEditHandlesNavigation()) openEdit(player, session, step, holder.index);
            }
            case 6 -> {
                String message = step.listRemove(session, player, holder.index);
                if (message != null) player.sendMessage(message);
                openEntries(player, session, step);
            }
            case 8 -> openEntries(player, session, step);
            default -> {
            }
        }
    }

    private static ItemStack item(@NotNull Material mat, @NotNull Component name,
                                  @NotNull NamedTextColor color, @NotNull List<Component> lore) {
        ItemStack stack = new ItemStack(mat);
        stack.editMeta(meta -> {
            meta.displayName(name.color(color).decoration(TextDecoration.ITALIC, false));
            meta.lore(lore.stream().map(c -> c.decoration(TextDecoration.ITALIC, false)).toList());
        });
        return stack;
    }

    private static ItemStack filler() {
        return item(Material.GRAY_STAINED_GLASS_PANE, Component.text(" "), NamedTextColor.DARK_GRAY, List.of());
    }
}
