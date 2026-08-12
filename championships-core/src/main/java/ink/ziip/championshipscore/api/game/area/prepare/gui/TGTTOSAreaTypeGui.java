package ink.ziip.championshipscore.api.game.area.prepare.gui;

import ink.ziip.championshipscore.configuration.config.message.GuiConfig;

import ink.ziip.championshipscore.api.game.area.prepare.PrepareModeInventory;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareSession;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareSessionManager;
import ink.ziip.championshipscore.api.game.area.prepare.tgttos.TGTTOSAreaTypeStep;
import ink.ziip.championshipscore.api.game.tgttos.TGTTOSConfig;
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

/** Paged selector for the TGTTOS map equipment/game-mode profile. */
public final class TGTTOSAreaTypeGui {
    private static final int OPTION_FIRST_SLOT = 0;
    private static final int OPTION_LAST_SLOT = 44;
    private static final int PAGE_SIZE = 45;
    private static final int PREVIOUS_SLOT = 45;
    private static final int BACK_SLOT = 49;
    private static final int NEXT_SLOT = 53;

    private TGTTOSAreaTypeGui() {
    }

    public static final class Holder implements InventoryHolder {
        final PrepareSession session;
        final TGTTOSAreaTypeStep step;
        int page;
        Inventory inventory;

        Holder(@NotNull PrepareSession session, @NotNull TGTTOSAreaTypeStep step) {
            this.session = session;
            this.step = step;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }

    public static void open(@NotNull Player player, @NotNull PrepareSession session,
                            @NotNull TGTTOSAreaTypeStep step) {
        Holder holder = new Holder(session, step);
        holder.inventory = Bukkit.createInventory(holder, 54,
                Component.text(GuiConfig.text("prepare-gui-tgttosareatypegui.text-001")).decoration(TextDecoration.ITALIC, false));
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
        int slot = event.getRawSlot();
        if (slot == PREVIOUS_SLOT) {
            if (holder.page > 0) {
                holder.page--;
                refresh(holder);
            }
            return;
        }
        if (slot == NEXT_SLOT) {
            if (holder.page + 1 < pageCount()) {
                holder.page++;
                refresh(holder);
            }
            return;
        }
        if (slot == BACK_SLOT) {
            back(player, session);
            return;
        }
        if (slot < OPTION_FIRST_SLOT || slot > OPTION_LAST_SLOT) return;
        int index = holder.page * PAGE_SIZE + slot;
        List<TGTTOSAreaTypeStep.Option> options = TGTTOSAreaTypeStep.options();
        if (index >= options.size()) return;
        String message = holder.step.select(session, options.get(index));
        if (message != null) player.sendMessage(message);
        back(player, session);
    }

    private static void refresh(@NotNull Holder holder) {
        Inventory inventory = holder.inventory;
        inventory.clear();
        List<TGTTOSAreaTypeStep.Option> options = TGTTOSAreaTypeStep.options();
        String current = ((TGTTOSConfig) holder.session.getTarget().config()).getAreaType();
        for (int slot = OPTION_FIRST_SLOT; slot <= OPTION_LAST_SLOT; slot++) {
            int index = holder.page * PAGE_SIZE + slot;
            inventory.setItem(slot, index < options.size()
                    ? option(options.get(index), current)
                    : filler());
        }
        inventory.setItem(PREVIOUS_SLOT, holder.page > 0
                ? menuItem(Material.ARROW, GuiConfig.text("prepare-gui-tgttosareatypegui.text-002"), NamedTextColor.WHITE)
                : filler());
        inventory.setItem(BACK_SLOT, menuItem(Material.BARRIER, GuiConfig.text("prepare-gui-tgttosareatypegui.text-003"), NamedTextColor.RED));
        inventory.setItem(NEXT_SLOT, holder.page + 1 < pageCount()
                ? menuItem(Material.ARROW, GuiConfig.text("prepare-gui-tgttosareatypegui.text-004"), NamedTextColor.WHITE)
                : filler());
    }

    private static int pageCount() {
        return Math.max(1, (TGTTOSAreaTypeStep.options().size() + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    private static ItemStack option(@NotNull TGTTOSAreaTypeStep.Option option, String current) {
        ItemStack item = new ItemStack(option.icon());
        boolean selected = option.value().equalsIgnoreCase(current == null ? "" : current);
        item.editMeta(meta -> {
            meta.displayName(Component.text(option.name()).color(selected ? NamedTextColor.GREEN : NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(Component.text(option.description()).color(NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text(selected ? GuiConfig.text("prepare-gui-tgttosareatypegui.text-005") : GuiConfig.text("prepare-gui-tgttosareatypegui.text-006"))
                            .color(selected ? NamedTextColor.GREEN : NamedTextColor.WHITE)
                            .decoration(TextDecoration.ITALIC, false)));
        });
        return item;
    }

    private static ItemStack menuItem(@NotNull Material material, @NotNull String name,
                                      @NotNull NamedTextColor color) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> meta.displayName(Component.text(name).color(color)
                .decoration(TextDecoration.ITALIC, false)));
        return item;
    }

    private static ItemStack filler() {
        return new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
    }

    private static void back(@NotNull Player player, @NotNull PrepareSession session) {
        player.closeInventory();
        PrepareModeInventory.refresh(player, session);
    }
}
