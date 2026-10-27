package ink.ziip.championshipscore.api.game.area.prepare.gui;

import ink.ziip.championshipscore.api.game.area.prepare.PrepareSession;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareSessionManager;
import ink.ziip.championshipscore.api.game.config.BaseGameConfig;
import ink.ziip.championshipscore.api.game.manager.BaseGameInstanceManager;
import ink.ziip.championshipscore.api.game.model.GameTypeEnum;
import ink.ziip.championshipscore.api.gui.MenuId;
import ink.ziip.championshipscore.api.gui.MenuLayout;
import ink.ziip.championshipscore.configuration.config.message.GuiConfig;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.presentation.text.CoreMessages;
import ink.ziip.championshipscore.shared.presentation.MessageTemplate;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Paged map definitions; editing and deletion always use the definition actually displayed. */
public final class AreaListGui {
    private static final String MENU_PATH = MenuId.MAP_EDITOR_AREA_LIST.path();
    private static final long DELETE_CONFIRMATION_SECONDS = 30;

    private AreaListGui() {}

    private record Entry(String name, BaseGameConfig config) {}

    public static final class Holder extends PrepareMenu {
        private final GameTypeEnum gameType;
        private final Map<Integer, Entry> slotToArea = new HashMap<>();
        private Entry deleteConfirmation;
        private long deleteConfirmationExpiresAt;
        private int page;
        private int pageCount;
        private final int emptySlot;

        Holder(Player player, PrepareSessionManager manager, GameTypeEnum gameType) {
            super(
                    player,
                    manager.getSession(player),
                    MenuId.MAP_EDITOR_AREA_LIST,
                    54,
                    MenuLayout.range(0, 45),
                    MenuLayout.range(45, 54),
                    Map.of("previous", 45, "new", 47, "page", 49, "next", 51, "close", 53),
                    Map.of("game", gameType));
            this.gameType = gameType;
            emptySlot = GuiConfig.slot(MENU_PATH + ".layout.overlay.empty", 22);
        }

        public GameTypeEnum gameType() {
            return gameType;
        }
    }

    public static void open(
            @NotNull PrepareSessionManager manager,
            @NotNull Player player,
            @NotNull GameTypeEnum gameType) {
        if (!player.hasPermission("cc.admin")) return;
        Holder holder = new Holder(player, manager, gameType);
        refresh(manager, holder);
        player.openInventory(holder.inventory);
    }

    private static BaseGameInstanceManager<?> areaManager(
            PrepareSessionManager manager, Holder holder) {
        return manager.getPlugin().getGameManager().getAreaManager(holder.gameType);
    }

    private static void refresh(PrepareSessionManager manager, Holder holder) {
        holder.clear();
        holder.slotToArea.clear();
        holder.deleteConfirmation = null;
        var areas = areaManager(manager, holder);
        List<String> names =
                areas == null
                        ? List.of()
                        : areas.getAreaNameList().stream()
                                .sorted(String.CASE_INSENSITIVE_ORDER)
                                .toList();
        holder.page = holder.layout.clampPage(holder.page, names.size());
        holder.pageCount = holder.layout.pageCount(names.size());
        int offset = holder.page * holder.layout.content().size();
        var flow = manager.flow(holder.gameType);
        for (int slot : holder.layout.pageSlots(holder.page, names.size())) {
            String name = names.get(offset++);
            var target = areas.getSetupTarget(holder.gameType, name);
            if (target == null) continue;
            int done = 0, total = 0;
            if (flow != null) {
                var preview =
                        new PrepareSession(
                                manager.getPlugin(), holder.gameType, name, target, flow);
                done = preview.configDone();
                total = preview.configTotal();
            }
            holder.inventory.setItem(
                    slot,
                    holder.item(
                            "area",
                            target.config().isPrepareReady() ? "published" : "draft",
                            Map.of("name", name, "done", done, "total", total)));
            holder.slotToArea.put(slot, new Entry(name, target.config()));
        }
        holder.put("new", null, Map.of());
        holder.put("close", null, Map.of());
        if (holder.page > 0) holder.put("previous", null, Map.of());
        if (holder.page + 1 < holder.pageCount) holder.put("next", null, Map.of());
        if (holder.pageCount > 1)
            holder.put(
                    "page",
                    null,
                    Map.of(
                            "page",
                            holder.page + 1,
                            "pages",
                            holder.pageCount,
                            "count",
                            names.size()));
        if (names.isEmpty())
            holder.inventory.setItem(holder.emptySlot, holder.item("empty", null, Map.of()));
    }

    public static void handleClick(
            @NotNull PrepareSessionManager manager,
            @NotNull InventoryClickEvent event,
            @NotNull Player player,
            @NotNull Holder holder) {
        event.setCancelled(true);
        if (event.getClickedInventory() != holder.inventory || !holder.available(manager, player))
            return;
        int slot = event.getRawSlot();
        if (slot == holder.layout.control("close")) {
            player.closeInventory();
            return;
        }
        if (slot == holder.layout.control("new")) {
            AnvilInputGui.openName(player, manager, holder.gameType);
            return;
        }
        if (slot == holder.layout.control("previous") && holder.page > 0) {
            holder.page--;
            refresh(manager, holder);
            return;
        }
        if (slot == holder.layout.control("next") && holder.page + 1 < holder.pageCount) {
            holder.page++;
            refresh(manager, holder);
            return;
        }
        Entry entry = holder.slotToArea.get(slot);
        if (entry == null) return;
        var areas = areaManager(manager, holder);
        var current = areas == null ? null : areas.getSetupTarget(holder.gameType, entry.name());
        if (current == null || current.config() != entry.config()) {
            refresh(manager, holder);
            return;
        }
        if (event.isRightClick()) {
            if (!entry.equals(holder.deleteConfirmation)
                    || holder.deleteConfirmationExpiresAt < System.currentTimeMillis()) {
                holder.deleteConfirmation = entry;
                holder.deleteConfirmationExpiresAt =
                        System.currentTimeMillis() + DELETE_CONFIRMATION_SECONDS * 1000;
                player.sendMessage(
                        CoreMessages.styled(
                                "area-list-gui.delete-confirmation",
                                MessageTemplate.render(
                                        MessageConfig.MAP_EDITOR_AREA_DELETE_CONFIRMATION,
                                        Map.of(
                                                "%map%",
                                                entry.name(),
                                                "%game%",
                                                holder.gameType.toString(),
                                                "%seconds%",
                                                String.valueOf(DELETE_CONFIRMATION_SECONDS)))));
                return;
            }
            if (manager.deleteArea(player, holder.gameType, entry.name())) refresh(manager, holder);
            return;
        }
        player.closeInventory();
        manager.enterSession(player, holder.gameType, entry.name());
    }
}
