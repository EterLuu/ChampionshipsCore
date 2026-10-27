package ink.ziip.championshipscore.api.game.area.prepare.gui;

import ink.ziip.championshipscore.api.game.acerace.runtime.AceRaceArea;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareSession;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareSessionManager;
import ink.ziip.championshipscore.api.game.area.prepare.step.AceRaceRespawnPointListStep;
import ink.ziip.championshipscore.api.gui.MenuId;
import ink.ziip.championshipscore.api.gui.MenuInventory;
import ink.ziip.championshipscore.api.gui.MenuLayout;
import ink.ziip.championshipscore.configuration.config.message.ConfiguredGui;
import ink.ziip.championshipscore.configuration.config.message.GuiConfig;
import ink.ziip.championshipscore.platform.bukkit.text.LegacyText;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/** Selects the progress line reached after one Ace Race respawn marker. */
public final class AceRaceRespawnPointBindingGui {
    private static final String MENU_PATH = MenuId.ACE_RACE_RESPAWN_BINDING.path();

    private AceRaceRespawnPointBindingGui() {}

    public static final class Holder implements MenuInventory {
        final PrepareSession session;
        final int respawnIndex;
        int page;
        Inventory inventory;
        MenuLayout layout;

        Holder(@NotNull PrepareSession session, int respawnIndex) {
            this.session = session;
            this.respawnIndex = respawnIndex;
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
            int respawnIndex) {
        Holder holder = new Holder(session, respawnIndex);
        GuiConfig.MenuSpec menu =
                GuiConfig.menu(
                        MENU_PATH, 54, GuiConfig.component(MENU_PATH), MenuLayout.range(0, 45));
        holder.layout = layout(menu.size());
        holder.inventory = Bukkit.createInventory(holder, menu.size(), menu.title());
        refresh(holder);
        player.openInventory(holder.inventory);
    }

    public static void handleClick(
            @NotNull PrepareSessionManager manager,
            @NotNull InventoryClickEvent event,
            @NotNull Player player,
            @NotNull Holder holder) {
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        PrepareSession session = manager.getSession(player);
        if (session == null || session != holder.session) {
            player.closeInventory();
            return;
        }
        AceRaceRespawnPointListStep step = new AceRaceRespawnPointListStep();
        AceRaceArea area = step.area(session);
        if (area == null || area.getRespawnPointIndexForConfig(holder.respawnIndex) < 0) {
            player.closeInventory();
            return;
        }
        int slot = event.getRawSlot();
        if (slot == holder.layout.control("previous")) {
            if (holder.page > 0) {
                holder.page--;
                refresh(holder);
            }
            return;
        }
        if (slot == holder.layout.control("next")) {
            if (holder.page + 1 < holder.layout.pageCount(area.getProgressPoints().size())) {
                holder.page++;
                refresh(holder);
            }
            return;
        }
        if (slot == holder.layout.control("back")) {
            ListStepGui.openEdit(player, session, step, holder.respawnIndex);
            return;
        }
        if (slot == holder.layout.control("start")) {
            choose(manager, player, session, step, holder.respawnIndex, -1);
            return;
        }
        int progressIndex = holder.layout.index(holder.page, slot);
        if (progressIndex >= 0 && progressIndex < area.getProgressPoints().size())
            choose(manager, player, session, step, holder.respawnIndex, progressIndex);
    }

    private static void choose(
            @NotNull PrepareSessionManager manager,
            @NotNull Player player,
            @NotNull PrepareSession session,
            @NotNull AceRaceRespawnPointListStep step,
            int respawnIndex,
            int binding) {
        String message = step.setBinding(session, respawnIndex, binding);
        if (message != null) player.sendMessage(message);
        ListStepGui.openEdit(player, session, step, respawnIndex);
    }

    private static void refresh(@NotNull Holder holder) {
        AceRaceRespawnPointListStep step = new AceRaceRespawnPointListStep();
        AceRaceArea area = step.area(holder.session);
        Inventory inventory = holder.inventory;
        inventory.clear();
        for (int slot : holder.layout.border()) inventory.setItem(slot, filler());
        if (area == null) return;
        int pageCount = holder.layout.pageCount(area.getProgressPoints().size());
        holder.page = Math.max(0, Math.min(holder.page, pageCount - 1));
        int current = area.getRespawnPointBinding(holder.respawnIndex);
        for (int slot : holder.layout.content()) {
            int progressIndex = holder.layout.index(holder.page, slot);
            if (progressIndex >= area.getProgressPoints().size()) {
                inventory.setItem(slot, filler());
                continue;
            }
            boolean selected = progressIndex == current;
            inventory.setItem(
                    slot,
                    configured(
                            "option",
                            selected ? "selected" : null,
                            Map.of("order", progressIndex + 1)));
        }
        inventory.setItem(
                holder.layout.control("previous"),
                holder.page > 0
                        ? configured(
                                "previous", null, Map.of("page", holder.page, "pages", pageCount))
                        : filler());
        inventory.setItem(holder.layout.control("back"), configured("back", null, Map.of()));
        inventory.setItem(
                holder.layout.control("start"),
                configured("start", current < 0 ? "selected" : null, Map.of()));
        inventory.setItem(
                holder.layout.control("next"),
                holder.page + 1 < pageCount
                        ? configured(
                                "next", null, Map.of("page", holder.page + 2, "pages", pageCount))
                        : filler());
    }

    static MenuLayout layout(int size) {
        return MenuLayout.load(
                MENU_PATH,
                size,
                MenuLayout.range(0, 45),
                List.of(),
                java.util.Map.of("previous", 45, "back", 48, "start", 49, "next", 53));
    }

    private static ItemStack configured(
            @NotNull String item, String state, @NotNull Map<String, ?> placeholders) {
        return ConfiguredGui.item(
                MENU_PATH + ".items." + item,
                state,
                placeholders,
                Material.BARRIER,
                LegacyText.component(item),
                List.of(),
                false);
    }

    private static ItemStack filler() {
        return ConfiguredGui.item(
                MENU_PATH + ".items.border",
                null,
                java.util.Map.of(),
                Material.GRAY_STAINED_GLASS_PANE,
                net.kyori.adventure.text.Component.text(" "),
                List.of(),
                false);
    }
}
