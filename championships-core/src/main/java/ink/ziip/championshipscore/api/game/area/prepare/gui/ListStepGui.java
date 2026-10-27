package ink.ziip.championshipscore.api.game.area.prepare.gui;

import ink.ziip.championshipscore.api.game.area.prepare.PrepareModeInventory;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareSession;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareSessionManager;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareStep;
import ink.ziip.championshipscore.api.game.area.prepare.step.AceRaceProgressPointListStep;
import ink.ziip.championshipscore.api.game.area.prepare.step.AceRaceRespawnPointListStep;
import ink.ziip.championshipscore.api.gui.GuiMenu;
import ink.ziip.championshipscore.api.gui.MenuId;
import ink.ziip.championshipscore.api.gui.MenuInventory;
import ink.ziip.championshipscore.api.gui.MenuLayout;
import ink.ziip.championshipscore.configuration.config.message.ConfiguredGui;
import ink.ziip.championshipscore.configuration.config.message.GuiConfig;
import ink.ziip.championshipscore.configuration.config.message.GuiText;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.platform.bukkit.text.LegacyText;
import ink.ziip.championshipscore.presentation.text.CoreMessages;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Safe list editor: add, inspect, edit, reorder, or remove one configured row at a time. */
public final class ListStepGui {
    private static final String MENU_PATH = MenuId.MAP_EDITOR_LIST_EDITOR.path();

    private ListStepGui() {}

    public static final class Holder implements MenuInventory {
        final PrepareSession session;
        final String stepKey;
        final MenuLayout layout = layout(MenuId.MAP_EDITOR_LIST_MANAGE);
        Inventory inventory;

        Holder(PrepareSession session, String stepKey) {
            this.session = session;
            this.stepKey = stepKey;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }

    public static final class EntryHolder implements MenuInventory {
        final PrepareSession session;
        final String stepKey;
        int page;
        final MenuLayout layout = layout(MenuId.MAP_EDITOR_LIST_ENTRIES);
        List<PrepareStep.ListEntry> rows = List.of();
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

    public static final class EditHolder implements MenuInventory {
        final PrepareSession session;
        final String stepKey;
        final int index;
        final int returnPage;
        final MenuLayout layout = layout(MenuId.MAP_EDITOR_LIST_ENTRY_EDIT);
        final List<PrepareStep.ListEntry> rows;
        Inventory inventory;

        EditHolder(@NotNull PrepareSession session, @NotNull String stepKey, int index) {
            this.session = session;
            this.stepKey = stepKey;
            this.index = index;
            this.returnPage = index / layout(MenuId.MAP_EDITOR_LIST_ENTRIES).content().size();
            this.rows = snapshot(session.step(stepKey).listEntries(session));
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }

    public static void open(
            @NotNull PrepareSessionManager manager,
            @NotNull Player player,
            @NotNull PrepareSession session,
            @NotNull PrepareStep step) {
        Holder holder = new Holder(session, step.key());
        Inventory inv =
                Bukkit.createInventory(
                        holder,
                        holder.layout.size(),
                        GuiConfig.component(
                                MENU_PATH + ".title",
                                java.util.Map.of("step", step.displayName())));
        holder.inventory = inv;
        refresh(holder, session, step);
        player.openInventory(inv);
    }

    private static void refresh(
            @NotNull Holder holder, @NotNull PrepareSession session, @NotNull PrepareStep step) {
        Inventory inv = holder.inventory;
        inv.clear();
        fillBorder(inv, holder.layout);
        inv.setItem(holder.layout.control("add"), configured("add", null, java.util.Map.of()));
        inv.setItem(holder.layout.control("view"), configured("view", null, java.util.Map.of()));
        inv.setItem(
                holder.layout.control("info"),
                configured(
                        "info",
                        step.isSet(session) ? "set" : "unset",
                        java.util.Map.of("count", step.listCount(session))));
        inv.setItem(holder.layout.control("back"), configured("back", null, java.util.Map.of()));
    }

    private static ItemStack configured(
            @NotNull String item, String state, @NotNull java.util.Map<String, ?> placeholders) {
        return ConfiguredGui.item(
                MENU_PATH + ".items." + item, state, placeholders, new ItemStack(Material.BARRIER));
    }

    public static void handleClick(
            @NotNull PrepareSessionManager manager,
            @NotNull InventoryClickEvent event,
            @NotNull Player player,
            @NotNull Holder holder) {
        event.setCancelled(true);
        Inventory top = event.getView().getTopInventory();
        if (event.getClickedInventory() != top) return;
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
        if (slot == holder.layout.control("add")) {
            if (!session.getFlow().isInCorrectWorld(player, session.getTarget())) {
                CoreMessages.sendAdminError(
                        player,
                        MessageConfig.MAP_EDITOR_STEP_GO_TO_MAP_WORLD_FIRST.replace(
                                "%world%", session.getTarget().worldName()));
                return;
            }
            String message = step.listAdd(session, player);
            if (message != null) player.sendMessage(message);
            refresh(holder, session, step);
            PrepareModeInventory.refresh(player, session);
        } else if (slot == holder.layout.control("view")) {
            openEntries(player, session, step);
        } else if (slot == holder.layout.control("back")) {
            player.closeInventory();
            PrepareModeInventory.refresh(player, session);
        }
    }

    private static void openEntries(
            @NotNull Player player, @NotNull PrepareSession session, @NotNull PrepareStep step) {
        openEntries(player, session, step, 0);
    }

    private static void openEntries(
            Player player, PrepareSession session, PrepareStep step, int page) {
        EntryHolder holder = new EntryHolder(session, step.key());
        holder.page = page;
        holder.inventory =
                Bukkit.createInventory(
                        holder,
                        holder.layout.size(),
                        GuiConfig.component(
                                MENU_PATH + ".title",
                                java.util.Map.of("step", step.displayName())));
        refreshEntries(holder, step);
        player.openInventory(holder.inventory);
    }

    private static void refreshEntries(@NotNull EntryHolder holder, @NotNull PrepareStep step) {
        Inventory inv = holder.inventory;
        inv.clear();
        fillBorder(inv, holder.layout);
        holder.rows = snapshot(step.listEntries(holder.session));
        int pageCount = holder.layout.pageCount(holder.rows.size());
        holder.page = holder.layout.clampPage(holder.page, holder.rows.size());
        for (int slot : holder.layout.content()) {
            int index = holder.layout.index(holder.page, slot);
            inv.setItem(
                    slot,
                    index < holder.rows.size() ? entryItem(holder.rows.get(index)) : filler());
        }
        inv.setItem(
                holder.layout.control("previous"),
                holder.page > 0
                        ? configured(
                                "previous",
                                null,
                                java.util.Map.of("page", holder.page, "pages", pageCount))
                        : filler());
        inv.setItem(
                holder.layout.control("entry-back"),
                configured("entry-back", null, java.util.Map.of()));
        inv.setItem(
                holder.layout.control("next"),
                holder.page + 1 < pageCount
                        ? configured(
                                "next",
                                null,
                                java.util.Map.of("page", holder.page + 2, "pages", pageCount))
                        : filler());
    }

    public static void handleEntryClick(
            @NotNull PrepareSessionManager manager,
            @NotNull InventoryClickEvent event,
            @NotNull Player player,
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
        if (slot == holder.layout.control("previous")) {
            if (holder.page > 0) {
                holder.page--;
                refreshEntries(holder, step);
            }
            return;
        }
        if (slot == holder.layout.control("next")) {
            int pageCount = holder.layout.pageCount(holder.rows.size());
            if (holder.page + 1 < pageCount) {
                holder.page++;
                refreshEntries(holder, step);
            }
            return;
        }
        if (slot == holder.layout.control("entry-back")) {
            open(manager, player, session, step);
            return;
        }
        int index = holder.layout.index(holder.page, slot);
        if (index < 0 || index >= holder.rows.size()) return;
        if (!holder.rows.equals(step.listEntries(session))) {
            refreshEntries(holder, step);
            return;
        }
        openEdit(player, session, step, index);
    }

    public static void openEdit(
            @NotNull Player player,
            @NotNull PrepareSession session,
            @NotNull PrepareStep step,
            int index) {
        if (index < 0 || index >= step.listEntries(session).size()) {
            openEntries(player, session, step);
            return;
        }
        EditHolder holder = new EditHolder(session, step.key(), index);
        holder.inventory =
                Bukkit.createInventory(
                        holder,
                        holder.layout.size(),
                        GuiConfig.component(
                                MENU_PATH + ".items.editor.title",
                                java.util.Map.of("number", index + 1)));
        fillBorder(holder.inventory, holder.layout);
        holder.inventory.setItem(
                holder.layout.control("order"), configured("order", null, java.util.Map.of()));
        if (step instanceof AceRaceRespawnPointListStep respawnStep) {
            holder.inventory.setItem(
                    holder.layout.control("binding"),
                    configured(
                            "binding",
                            "progress-line",
                            java.util.Map.of("binding", respawnStep.bindingText(session, index))));
        } else if (step instanceof AceRaceProgressPointListStep progressPointStep) {
            holder.inventory.setItem(
                    holder.layout.control("binding"),
                    configured(
                            "binding",
                            "equipment",
                            java.util.Map.of(
                                    "equipment", progressPointStep.equipmentText(session, index))));
        }
        holder.inventory.setItem(
                holder.layout.control("actual"), configured("actual", null, java.util.Map.of()));
        holder.inventory.setItem(
                holder.layout.control("delete"), configured("delete", null, java.util.Map.of()));
        holder.inventory.setItem(
                holder.layout.control("return"), configured("return", null, java.util.Map.of()));
        player.openInventory(holder.inventory);
    }

    public static void handleEditClick(
            @NotNull PrepareSessionManager manager,
            @NotNull InventoryClickEvent event,
            @NotNull Player player,
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
        if (!holder.rows.equals(step.listEntries(session))) {
            openEntries(player, session, step, holder.returnPage);
            return;
        }
        int slot = event.getRawSlot();
        if (slot == holder.layout.control("order")) {
            AnvilInputGui.openInteger(
                    player,
                    session,
                    GuiConfig.text(MENU_PATH + ".items.order.title"),
                    holder.index + 1,
                    value -> {
                        if (manager.getSession(player) != session) return;
                        if (!holder.rows.equals(step.listEntries(session))) {
                            openEntries(player, session, step, holder.returnPage);
                            return;
                        }
                        String message = step.listSetOrder(session, player, holder.index, value);
                        if (message != null) player.sendMessage(message);
                        openEntries(player, session, step, holder.returnPage);
                    });
        } else if (slot == holder.layout.control("binding")) {
            if (step instanceof AceRaceRespawnPointListStep)
                AceRaceRespawnPointBindingGui.open(manager, player, session, holder.index);
            else if (step instanceof AceRaceProgressPointListStep progressPointStep)
                progressPointStep.editEquipment(session, player, holder.index);
        } else if (slot == holder.layout.control("actual")) {
            if (!session.getFlow().isInCorrectWorld(player, session.getTarget())) {
                CoreMessages.sendAdminError(
                        player,
                        MessageConfig.MAP_EDITOR_STEP_GO_TO_MAP_WORLD_FIRST.replace(
                                "%world%", session.getTarget().worldName()));
                return;
            }
            String message = step.listEdit(session, player, holder.index);
            if (message != null) player.sendMessage(message);
            if (!step.listEditHandlesNavigation()) openEdit(player, session, step, holder.index);
        } else if (slot == holder.layout.control("delete")) {
            String message = step.listRemove(session, player, holder.index);
            if (message != null) player.sendMessage(message);
            openEntries(player, session, step, holder.returnPage);
        } else if (slot == holder.layout.control("return")) {
            openEntries(player, session, step, holder.returnPage);
        }
    }

    static MenuLayout layout(MenuId screen) {
        boolean entries = screen == MenuId.MAP_EDITOR_LIST_ENTRIES;
        int size = GuiConfig.menu(screen.path(), entries ? 54 : 9, "", List.of()).size();
        Map<String, Integer> controls =
                switch (screen) {
                    case MAP_EDITOR_LIST_MANAGE ->
                            Map.of("add", 0, "view", 1, "info", 4, "back", 8);
                    case MAP_EDITOR_LIST_ENTRIES ->
                            Map.of("previous", 45, "entry-back", 49, "next", 53);
                    case MAP_EDITOR_LIST_ENTRY_EDIT ->
                            Map.of("order", 0, "binding", 2, "actual", 4, "delete", 6, "return", 8);
                    default ->
                            throw new IllegalArgumentException(
                                    "Not a list editor screen: " + screen);
                };
        return MenuLayout.load(
                screen.path(),
                MENU_PATH,
                size,
                entries ? MenuLayout.range(0, 45) : List.of(),
                List.of(),
                controls);
    }

    private static List<PrepareStep.ListEntry> snapshot(List<PrepareStep.ListEntry> entries) {
        return List.copyOf(entries);
    }

    private static ItemStack entryItem(PrepareStep.ListEntry entry) {
        ItemStack item =
                ConfiguredGui.item(
                        MENU_PATH + ".items.entry",
                        null,
                        Map.of("title", entry.title()),
                        GuiMenu.item(
                                Material.PAPER,
                                LegacyText.component(entry.title())
                                        .colorIfAbsent(NamedTextColor.WHITE),
                                List.of(),
                                false));
        item.editMeta(
                meta -> {
                    var lore = new ArrayList<Component>();
                    for (String detail : entry.details())
                        lore.add(
                                GuiText.row(MENU_PATH + ".presentation.detail-row", detail)
                                        .decorationIfAbsent(
                                                TextDecoration.ITALIC, TextDecoration.State.FALSE));
                    if (meta.lore() != null) lore.addAll(meta.lore());
                    meta.lore(lore);
                });
        return item;
    }

    private static void fillBorder(Inventory inventory, MenuLayout layout) {
        for (int slot : layout.border()) inventory.setItem(slot, filler());
    }

    private static ItemStack filler() {
        return configured("border", null, Map.of());
    }
}
