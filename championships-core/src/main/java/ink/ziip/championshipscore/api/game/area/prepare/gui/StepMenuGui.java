package ink.ziip.championshipscore.api.game.area.prepare.gui;

import ink.ziip.championshipscore.configuration.config.message.GuiConfig;

import ink.ziip.championshipscore.api.game.area.prepare.PrepareKeys;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareModeInventory;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareSession;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareSessionManager;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareStep;
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

/** A paged, six-row step picker. It keeps large prepare flows usable without excessive page hopping. */
public final class StepMenuGui {
    private static final int PAGE_SIZE = 45;
    private static final int PREVIOUS_SLOT = 45;
    private static final int BACK_SLOT = 49;
    private static final int NEXT_SLOT = 53;
    private static final int FIRST_STEP_SLOT = 0;
    private static final int LAST_STEP_SLOT = 44;

    private StepMenuGui() {
    }

    public static final class Holder implements InventoryHolder {
        final PrepareSession session;
        int page;
        Inventory inventory;

        Holder(@NotNull PrepareSession session) {
            this.session = session;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }

    public static void open(@NotNull Player player, @NotNull PrepareSession session) {
        Holder holder = new Holder(session);
        Inventory inventory = Bukkit.createInventory(holder, 54,
                Component.text(GuiConfig.text("prepare-gui-stepmenugui.text-001")).decoration(TextDecoration.ITALIC, false));
        holder.inventory = inventory;
        refresh(holder);
        player.openInventory(inventory);
    }

    private static void refresh(@NotNull Holder holder) {
        PrepareSession session = holder.session;
        Inventory inventory = holder.inventory;
        inventory.clear();
        int pageCount = pageCount(session);
        holder.page = Math.max(0, Math.min(holder.page, pageCount - 1));
        int first = holder.page * PAGE_SIZE;

        for (int slot = FIRST_STEP_SLOT; slot <= LAST_STEP_SLOT; slot++) {
            int index = first + slot;
            if (index < session.getSteps().size()) {
                inventory.setItem(slot, stepItem(session, session.getSteps().get(index), index + 1));
            } else {
                inventory.setItem(slot, filler());
            }
        }

        inventory.setItem(PREVIOUS_SLOT, holder.page > 0
                ? menuItem(Material.ARROW, GuiConfig.text("prepare-gui-stepmenugui.text-002"), NamedTextColor.WHITE, GuiConfig.text("prepare-gui-stepmenugui.text-003") + holder.page + GuiConfig.text("prepare-gui-stepmenugui.text-004"))
                : menuItem(Material.GRAY_STAINED_GLASS_PANE, GuiConfig.text("prepare-gui-stepmenugui.text-001"), NamedTextColor.DARK_GRAY,
                GuiConfig.text("prepare-gui-stepmenugui.text-005") + session.getSteps().size() + GuiConfig.text("prepare-gui-stepmenugui.text-006")));
        inventory.setItem(BACK_SLOT, menuItem(Material.BARRIER, GuiConfig.text("prepare-gui-stepmenugui.text-007"), NamedTextColor.RED,
                GuiConfig.text("prepare-gui-stepmenugui.text-008")));
        inventory.setItem(NEXT_SLOT, holder.page + 1 < pageCount
                ? menuItem(Material.ARROW, GuiConfig.text("prepare-gui-stepmenugui.text-009"), NamedTextColor.WHITE,
                GuiConfig.text("prepare-gui-stepmenugui.text-003") + (holder.page + 2) + " / " + pageCount + GuiConfig.text("prepare-gui-stepmenugui.text-004"))
                : menuItem(Material.GRAY_STAINED_GLASS_PANE, GuiConfig.text("prepare-gui-stepmenugui.text-010"), NamedTextColor.DARK_GRAY,
                GuiConfig.text("prepare-gui-stepmenugui.text-011")));
    }

    public static void handleClick(@NotNull PrepareSessionManager manager, @NotNull InventoryClickEvent event,
                                   @NotNull Player player, @NotNull Holder holder) {
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        PrepareSession session = manager.getSession(player);
        if (session == null || session != holder.session) {
            player.closeInventory();
            return;
        }
        int slot = event.getRawSlot();
        int pageCount = pageCount(session);
        if (slot == PREVIOUS_SLOT) {
            if (holder.page > 0) {
                holder.page--;
                refresh(holder);
            } else {
                back(player, session);
            }
            return;
        }
        if (slot == NEXT_SLOT) {
            if (holder.page + 1 < pageCount) {
                holder.page++;
                refresh(holder);
            }
            return;
        }
        if (slot == BACK_SLOT) {
            back(player, session);
            return;
        }
        if (slot < FIRST_STEP_SLOT || slot > LAST_STEP_SLOT) return;
        int index = holder.page * PAGE_SIZE + slot;
        if (index >= session.getSteps().size()) return;
        manager.handleStepClick(player, session, session.getSteps().get(index).key());
        if (player.getOpenInventory().getTopInventory().getHolder() == holder) {
            refresh(holder);
        }
    }

    private static void back(@NotNull Player player, @NotNull PrepareSession session) {
        player.closeInventory();
        PrepareModeInventory.refresh(player, session);
    }

    private static int pageCount(@NotNull PrepareSession session) {
        return Math.max(1, (session.getSteps().size() + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    private static ItemStack stepItem(@NotNull PrepareSession session, @NotNull PrepareStep step, int number) {
        boolean set = step.isSet(session);
        String customState = step.stateText(session);
        String state = customState != null ? customState : switch (step.captureType()) {
            case CONFIRM_WORLD -> session.isWorldConfirmed() ? GuiConfig.text("prepare-gui-stepmenugui.text-012") : GuiConfig.text("prepare-gui-stepmenugui.text-013");
            case STAMP -> session.isStamped() ? GuiConfig.text("prepare-gui-stepmenugui.text-014") : GuiConfig.text("prepare-gui-stepmenugui.text-015");
            case LIST -> set ? GuiConfig.text("prepare-gui-stepmenugui.text-016") + step.listCount(session) + GuiConfig.text("prepare-gui-stepmenugui.text-017") : GuiConfig.text("prepare-gui-stepmenugui.text-018");
            default -> set ? GuiConfig.text("prepare-gui-stepmenugui.text-019") : GuiConfig.text("prepare-gui-stepmenugui.text-018");
        };
        ItemStack item = PrepareKeys.item(step.icon(),
                Component.text(number + ". ").color(NamedTextColor.GRAY)
                        .append(step.displayName().color(NamedTextColor.WHITE)),
                List.of(step.description().color(NamedTextColor.GRAY),
                        Component.text(state).color(set ? NamedTextColor.GREEN : NamedTextColor.YELLOW),
                        Component.text(GuiConfig.text("prepare-gui-stepmenugui.text-020")).color(NamedTextColor.AQUA)));
        PrepareKeys.setStep(item, step.key());
        return item;
    }

    private static ItemStack menuItem(@NotNull Material material, @NotNull String name,
                                      @NotNull NamedTextColor color, @NotNull String lore) {
        return PrepareKeys.item(material, Component.text(name).color(color),
                List.of(Component.text(lore).color(NamedTextColor.GRAY)));
    }

    private static ItemStack filler() {
        return PrepareKeys.item(Material.GRAY_STAINED_GLASS_PANE, Component.text(" "), List.of());
    }
}
