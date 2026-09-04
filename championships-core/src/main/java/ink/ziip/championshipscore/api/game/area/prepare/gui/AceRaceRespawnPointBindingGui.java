package ink.ziip.championshipscore.api.game.area.prepare.gui;

import ink.ziip.championshipscore.api.gui.MenuId;
import ink.ziip.championshipscore.configuration.config.message.ConfiguredGui;
import ink.ziip.championshipscore.configuration.config.message.GuiConfig;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;

import ink.ziip.championshipscore.api.game.acerace.AceRaceArea;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareSession;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareSessionManager;
import ink.ziip.championshipscore.api.game.area.prepare.step.AceRaceRespawnPointListStep;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/** Selects the progress line reached after one Ace Race respawn marker. */
public final class AceRaceRespawnPointBindingGui {
    private static final String MENU_PATH = MenuId.ACE_RACE_RESPAWN_BINDING.path();
    private static final int PAGE_SIZE = 45;
    private static final int PREVIOUS_SLOT = 45;
    private static final int BACK_SLOT = 48;
    private static final int START_SLOT = 49;
    private static final int NEXT_SLOT = 53;

    private AceRaceRespawnPointBindingGui() {
    }

    public static final class Holder implements InventoryHolder {
        final PrepareSession session;
        final int respawnIndex;
        int page;
        Inventory inventory;

        Holder(@NotNull PrepareSession session, int respawnIndex) {
            this.session = session;
            this.respawnIndex = respawnIndex;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }

    public static void open(@NotNull PrepareSessionManager manager, @NotNull Player player,
                            @NotNull PrepareSession session, int respawnIndex) {
        Holder holder = new Holder(session, respawnIndex);
        GuiConfig.MenuSpec menu = GuiConfig.menu(MENU_PATH, 54, GuiConfig.component(MENU_PATH), List.of());
        holder.inventory = Bukkit.createInventory(holder, menu.size(), menu.title());
        refresh(holder);
        player.openInventory(holder.inventory);
    }

    public static void handleClick(@NotNull PrepareSessionManager manager,
                                   @NotNull InventoryClickEvent event, @NotNull Player player,
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
        if (slot == PREVIOUS_SLOT) {
            if (holder.page > 0) {
                holder.page--;
                refresh(holder);
            }
            return;
        }
        if (slot == NEXT_SLOT) {
            if (holder.page + 1 < pageCount(area.getProgressPoints().size())) {
                holder.page++;
                refresh(holder);
            }
            return;
        }
        if (slot == BACK_SLOT) {
            ListStepGui.openEdit(player, session, step, holder.respawnIndex);
            return;
        }
        if (slot == START_SLOT) {
            choose(manager, player, session, step, holder.respawnIndex, -1);
            return;
        }
        if (slot < 0 || slot >= PAGE_SIZE) return;
        int progressIndex = holder.page * PAGE_SIZE + slot;
        if (progressIndex < area.getProgressPoints().size())
            choose(manager, player, session, step, holder.respawnIndex, progressIndex);
    }

    private static void choose(@NotNull PrepareSessionManager manager, @NotNull Player player,
                               @NotNull PrepareSession session, @NotNull AceRaceRespawnPointListStep step,
                               int respawnIndex, int binding) {
        String message = step.setBinding(session, respawnIndex, binding);
        if (message != null) player.sendMessage(message);
        ListStepGui.openEdit(player, session, step, respawnIndex);
    }

    private static void refresh(@NotNull Holder holder) {
        AceRaceRespawnPointListStep step = new AceRaceRespawnPointListStep();
        AceRaceArea area = step.area(holder.session);
        Inventory inventory = holder.inventory;
        inventory.clear();
        if (area == null) return;
        int pageCount = pageCount(area.getProgressPoints().size());
        holder.page = Math.max(0, Math.min(holder.page, pageCount - 1));
        int first = holder.page * PAGE_SIZE;
        int current = area.getRespawnPointBinding(holder.respawnIndex);
        for (int slot = 0; slot < PAGE_SIZE; slot++) {
            int progressIndex = first + slot;
            if (progressIndex >= area.getProgressPoints().size()) {
                inventory.setItem(slot, filler());
                continue;
            }
            boolean selected = progressIndex == current;
            inventory.setItem(slot, configured("option", selected ? "selected" : null,
                    Map.of("order", progressIndex + 1)));
        }
        inventory.setItem(PREVIOUS_SLOT, holder.page > 0
                ? configured("previous", null, Map.of("page", holder.page, "pages", pageCount))
                : filler());
        inventory.setItem(BACK_SLOT, configured("back", null, Map.of()));
        inventory.setItem(START_SLOT, configured("start", current < 0 ? "selected" : null, Map.of()));
        inventory.setItem(NEXT_SLOT, holder.page + 1 < pageCount
                ? configured("next", null, Map.of("page", holder.page + 2, "pages", pageCount))
                : filler());
    }

    private static ItemStack configured(@NotNull String item, String state, @NotNull Map<String, ?> placeholders) {
        return ConfiguredGui.item(MENU_PATH + ".items." + item, state, placeholders,
                Material.BARRIER, Component.text(item), List.of(), false);
    }

    private static int pageCount(int size) {
        return Math.max(1, (size + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    private static ItemStack item(@NotNull Material material, @NotNull String name,
                                  @NotNull NamedTextColor color, String... lore) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> {
            meta.displayName(Component.text(name).color(color).decoration(TextDecoration.ITALIC, false));
            meta.lore(java.util.Arrays.stream(lore).map(line -> Component.text(line)
                    .color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)).toList());
        });
        return item;
    }

    private static ItemStack filler() {
        return item(Material.GRAY_STAINED_GLASS_PANE, " ", NamedTextColor.DARK_GRAY);
    }
}
