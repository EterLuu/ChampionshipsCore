package ink.ziip.championshipscore.api.game.area.prepare.gui;

import ink.ziip.championshipscore.api.game.area.prepare.PrepareKeys;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareModeInventory;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareSession;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareSessionManager;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareStep;
import ink.ziip.championshipscore.api.game.area.prepare.StepCaptureType;
import ink.ziip.championshipscore.api.gui.MenuId;
import ink.ziip.championshipscore.api.gui.MenuInventory;
import ink.ziip.championshipscore.api.gui.MenuLayout;
import ink.ziip.championshipscore.configuration.config.message.ConfiguredGui;
import ink.ziip.championshipscore.configuration.config.message.GuiConfig;

import net.kyori.adventure.text.Component;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A paged, six-row step picker. It keeps large prepare flows usable without excessive page hopping.
 */
public final class StepMenuGui {
    private static final String MENU_PATH = MenuId.MAP_EDITOR_STEP_LIST.path();

    private StepMenuGui() {}

    public static final class Holder implements MenuInventory {
        final PrepareSession session;
        int page;
        Inventory inventory;
        MenuLayout layout;

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
        GuiConfig.MenuSpec menu =
                GuiConfig.menu(
                        MENU_PATH,
                        54,
                        GuiConfig.text(MENU_PATH + ".title"),
                        MenuLayout.range(0, 45));
        holder.layout = layout(menu.size());
        Inventory inventory = Bukkit.createInventory(holder, menu.size(), menu.title());
        holder.inventory = inventory;
        refresh(holder);
        player.openInventory(inventory);
    }

    private static void refresh(@NotNull Holder holder) {
        PrepareSession session = holder.session;
        Inventory inventory = holder.inventory;
        inventory.clear();
        for (int slot : holder.layout.border()) inventory.setItem(slot, filler());
        int pageCount = holder.layout.pageCount(session.getSteps().size());
        holder.page = Math.max(0, Math.min(holder.page, pageCount - 1));

        for (int slot : holder.layout.content()) {
            int index = holder.layout.index(holder.page, slot);
            if (index < session.getSteps().size()) {
                inventory.setItem(
                        slot, stepItem(session, session.getSteps().get(index), index + 1));
            } else {
                inventory.setItem(slot, filler());
            }
        }

        boolean hasPrev = holder.page > 0;
        boolean hasNext = holder.page + 1 < pageCount;
        inventory.setItem(
                holder.layout.control("previous"),
                configured(
                        "previous",
                        hasPrev ? null : "disabled",
                        Map.of(
                                "page",
                                holder.page,
                                "pages",
                                pageCount,
                                "total",
                                session.getSteps().size())));
        inventory.setItem(holder.layout.control("back"), configured("back", null, Map.of()));
        inventory.setItem(
                holder.layout.control("next"),
                configured(
                        "next",
                        hasNext ? null : "disabled",
                        Map.of("page", holder.page + 2, "pages", pageCount)));
    }

    static MenuLayout layout(int size) {
        return MenuLayout.load(
                MENU_PATH,
                size,
                MenuLayout.range(0, 45),
                List.of(),
                java.util.Map.of("previous", 45, "back", 49, "next", 53));
    }

    private static ItemStack configured(
            @NotNull String item, String state, @NotNull Map<String, ?> placeholders) {
        return ConfiguredGui.item(
                MENU_PATH + ".items." + item,
                state,
                placeholders,
                Material.BARRIER,
                Component.text(item),
                List.of(),
                false);
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
        int slot = event.getRawSlot();
        int pageCount = holder.layout.pageCount(session.getSteps().size());
        if (slot == holder.layout.control("previous")) {
            if (holder.page > 0) {
                holder.page--;
                refresh(holder);
            } else {
                back(player, session);
            }
            return;
        }
        if (slot == holder.layout.control("next")) {
            if (holder.page + 1 < pageCount) {
                holder.page++;
                refresh(holder);
            }
            return;
        }
        if (slot == holder.layout.control("back")) {
            back(player, session);
            return;
        }
        int index = holder.layout.index(holder.page, slot);
        if (index < 0 || index >= session.getSteps().size()) return;
        manager.handleStepClick(player, session, session.getSteps().get(index).key());
        if (player.getOpenInventory().getTopInventory().getHolder() == holder) {
            refresh(holder);
        }
    }

    private static void back(@NotNull Player player, @NotNull PrepareSession session) {
        player.closeInventory();
        PrepareModeInventory.refresh(player, session);
    }

    private static ItemStack stepItem(
            @NotNull PrepareSession session, @NotNull PrepareStep step, int number) {
        boolean set = step.isSet(session);
        int count = step.captureType() == StepCaptureType.LIST ? step.listCount(session) : 0;
        String stateKey =
                switch (step.captureType()) {
                    case CONFIRM_WORLD -> session.isWorldConfirmed() ? "confirmed" : "unconfirmed";
                    case STAMP -> session.isStamped() ? "stamped" : "unstamped";
                    case LIST -> set ? "set-list" : "unset";
                    default -> set ? "set" : "unset";
                };
        String state =
                step.stateText(session) != null
                        ? step.stateText(session)
                        : GuiConfig.text(
                                MENU_PATH + ".items.step.states." + stateKey + ".title",
                                Map.of("count", count));
        String action =
                GuiConfig.text(
                        MENU_PATH
                                + ".items.step.actions."
                                + step.captureType()
                                        .name()
                                        .toLowerCase(Locale.ROOT)
                                        .replace('_', '-')
                                + ".title");
        ItemStack fallback = PrepareKeys.item(step.icon(), Component.empty(), List.of());
        // The step state is rendered in the lore. Passing it to ConfiguredGui would replace the
        // complete
        // title with the state's short label and hide the step number/name.
        ItemStack item =
                ConfiguredGui.item(
                        MENU_PATH + ".items.step",
                        null,
                        Map.of(
                                "number", number,
                                "title", step.displayName(),
                                "description", step.description(),
                                "count", count,
                                "state", state,
                                "action", action),
                        fallback);
        PrepareKeys.setStep(item, step.key());
        return item;
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
