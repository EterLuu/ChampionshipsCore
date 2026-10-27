package ink.ziip.championshipscore.api.daily;

import ink.ziip.championshipscore.api.game.model.GameTypeEnum;
import ink.ziip.championshipscore.api.gui.MenuId;
import ink.ziip.championshipscore.api.gui.MenuInventory;
import ink.ziip.championshipscore.api.gui.MenuLayout;
import ink.ziip.championshipscore.configuration.config.message.ConfiguredGui;
import ink.ziip.championshipscore.configuration.config.message.GuiConfig;

import net.kyori.adventure.text.Component;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The shared immutable layout and navigation for daily lobby and record screens. */
class DailyMenuPage implements MenuInventory {
    final UUID viewer;
    final String family;
    final MenuLayout layout;
    final int returnPage;
    final int emptySlot;
    final Map<Integer, GameTypeEnum> gamesBySlot = new HashMap<>();
    Inventory inventory;
    int page;
    int pageCount = 1;

    DailyMenuPage(UUID viewer, MenuId screen, boolean leaderboard, boolean detail, int returnPage) {
        this(
                viewer,
                screen,
                54,
                MenuLayout.range(18, 45),
                MenuLayout.range(45, 54),
                recordControls(leaderboard),
                returnPage,
                detail);
    }

    DailyMenuPage(
            UUID viewer,
            MenuId screen,
            int size,
            List<Integer> content,
            List<Integer> border,
            Map<String, Integer> controls) {
        this(viewer, screen, size, content, border, controls, 0, false);
    }

    private DailyMenuPage(
            UUID viewer,
            MenuId screen,
            int size,
            List<Integer> content,
            List<Integer> border,
            Map<String, Integer> controls,
            int returnPage,
            boolean detail) {
        this.viewer = viewer;
        this.family = MenuId.itemFamilyFor(screen.path());
        this.returnPage = returnPage;
        layout =
                MenuLayout.load(
                        screen.path(),
                        family,
                        GuiConfig.menu(screen.path(), size, "", List.of()).size(),
                        content,
                        border,
                        controls);
        emptySlot =
                GuiConfig.slot(
                        screen.path() + ".layout.overlay.empty",
                        GuiConfig.slot(
                                family
                                        + ".items.empty"
                                        + (detail ? ".states.detail" : "")
                                        + ".slot",
                                GuiConfig.slot(family + ".items.empty.slot", 22)));
    }

    private static Map<String, Integer> recordControls(boolean leaderboard) {
        var controls =
                new LinkedHashMap<>(
                        Map.of(
                                "back",
                                45,
                                "previous",
                                48,
                                "refresh",
                                49,
                                "page",
                                50,
                                "next",
                                52,
                                "close",
                                53));
        if (leaderboard) controls.putAll(Map.of("player", 1, "overview", 4, "records", 7));
        else controls.putAll(Map.of("summary", 4, "leaderboard", 47));
        return controls;
    }

    boolean navigate(int slot) {
        if (slot == layout.control("previous") && page > 0) {
            page--;
            return true;
        }
        if (slot == layout.control("next") && page + 1 < pageCount) {
            page++;
            return true;
        }
        return false;
    }

    void navigation(int count) {
        if (page > 0) put("previous", null, Map.of());
        if (page + 1 < pageCount) put("next", null, Map.of());
        if (pageCount > 1)
            put("page", null, Map.of("page", page + 1, "pages", pageCount, "count", count));
        if (count == 0) inventory.setItem(emptySlot, item("empty", null, Map.of()));
    }

    void clear() {
        inventory.clear();
        gamesBySlot.clear();
        ItemStack border = item("border", null, Map.of());
        for (int slot : layout.border()) inventory.setItem(slot, border);
    }

    int paginate(int count) {
        page = layout.clampPage(page, count);
        pageCount = layout.pageCount(count);
        return page * layout.content().size();
    }

    ItemStack item(String id, String state, Map<String, ?> parameters) {
        return ConfiguredGui.item(
                family + ".items." + id,
                state,
                parameters,
                Material.PAPER,
                Component.empty(),
                List.of(),
                false);
    }

    void put(String id, String state, Map<String, ?> parameters) {
        inventory.setItem(layout.control(id), item(id, state, parameters));
    }

    void footer(int count, boolean detail, String backState, boolean refresh) {
        put("back", backState, Map.of());
        put("close", null, Map.of());
        if (layout.controls().containsKey("leaderboard")) put("leaderboard", null, Map.of());
        if (refresh) put("refresh", null, Map.of());
        if (page > 0) put("previous", null, Map.of());
        if (page + 1 < pageCount) put("next", null, Map.of());
        if (detail || pageCount > 1)
            put(
                    "page",
                    detail ? null : "selection",
                    Map.of(
                            "page", page + 1, "pages", pageCount, "maps", count, "games", count,
                            "count", count));
        if (count == 0)
            inventory.setItem(emptySlot, item("empty", detail ? "detail" : null, Map.of()));
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
