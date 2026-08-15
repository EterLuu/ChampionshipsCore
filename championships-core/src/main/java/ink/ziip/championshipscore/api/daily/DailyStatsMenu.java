package ink.ziip.championshipscore.api.daily;

import ink.ziip.championshipscore.api.gui.MenuId;
import ink.ziip.championshipscore.configuration.config.message.ConfiguredGui;
import ink.ziip.championshipscore.configuration.config.message.GuiConfig;

import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Categorized personal records: game cards first, map-level details on the second screen. */
final class DailyStatsMenu {
    private static final String MENU_PATH = MenuId.DAILY_STATISTICS.path();
    private static final int SIZE = 54;
    private static final int SUMMARY_SLOT = 4;
    private static final int BACK_SLOT = 45;
    private static final int LEADERBOARD_SLOT = 47;
    private static final int REFRESH_SLOT = 49;
    private static final int PAGE_SLOT = 50;
    private static final int CLOSE_SLOT = 53;
    private static final List<Integer> GAME_SLOTS_ONE = List.of(22);
    private static final List<Integer> GAME_SLOTS_TWO = List.of(21, 23);
    private static final List<Integer> GAME_SLOTS_THREE = List.of(20, 22, 24);
    private static final List<Integer> GAME_SLOTS_MANY = List.of(20, 22, 24, 29, 31, 33);
    /** Map cards per detail page: three centered rows of at most seven items. */
    static final int MAP_PAGE_SIZE = 21;

    private final DailyManager daily;

    DailyStatsMenu(DailyManager daily) {
        this.daily = daily;
    }

    void open(Player player) {
        StatsHolder holder = new StatsHolder(player.getUniqueId());
        holder.inventory = Bukkit.createInventory(holder, SIZE,
                Component.text(GuiConfig.text("daily.menus.statistics-screen.copy.personal-record"), NamedTextColor.DARK_AQUA).decorate(TextDecoration.BOLD));
        refresh(holder);
        player.openInventory(holder.inventory);
    }

    void refreshOpenMenus() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Object holder = player.getOpenInventory().getTopInventory().getHolder();
            if (holder instanceof StatsHolder stats) refresh(stats);
            else if (holder instanceof DetailHolder detail) refresh(detail);
        }
    }

    void click(Player player, int slot, StatsHolder holder) {
        if (!holder.viewer.equals(player.getUniqueId())) {
            player.closeInventory();
            return;
        }
        if (slot == BACK_SLOT) {
            daily.openMenu(player);
            clickSound(player, 1F);
            return;
        }
        if (slot == LEADERBOARD_SLOT) {
            daily.openLeaderboard(player);
            clickSound(player, 1.15F);
            return;
        }
        if (slot == CLOSE_SLOT) {
            player.closeInventory();
            return;
        }
        GameTypeEnum game = holder.gamesBySlot.get(slot);
        if (game != null) {
            openDetail(player, game);
            clickSound(player, 1.15F);
        }
    }

    void click(Player player, int slot, DetailHolder holder) {
        if (!holder.viewer.equals(player.getUniqueId())) {
            player.closeInventory();
            return;
        }
        if (slot == BACK_SLOT) {
            open(player);
            clickSound(player, 1F);
        } else if (slot == LEADERBOARD_SLOT) {
            daily.openLeaderboard(player);
            clickSound(player, 1.15F);
        } else if (slot == REFRESH_SLOT) {
            refresh(holder);
            clickSound(player, 1.1F);
        } else if (slot == CLOSE_SLOT) {
            player.closeInventory();
        } else if (slot == 48 && holder.page > 0) {
            holder.page--;
            refresh(holder);
            clickSound(player, 1F);
        } else if (slot == 52 && holder.page + 1 < holder.pageCount) {
            holder.page++;
            refresh(holder);
            clickSound(player, 1F);
        }
    }

    private void openDetail(Player player, GameTypeEnum game) {
        DetailHolder holder = new DetailHolder(player.getUniqueId(), game);
        holder.inventory = Bukkit.createInventory(holder, SIZE,
                Component.text(GuiConfig.text("daily.menus.statistics-screen.copy.statistics-header-prefix"), NamedTextColor.DARK_AQUA)
                        .append(Component.text(game.toString(), gameColor(game)))
                        .decorate(TextDecoration.BOLD));
        refresh(holder);
        player.openInventory(holder.inventory);
    }

    private void refresh(StatsHolder holder) {
        Inventory inventory = holder.inventory;
        inventory.clear();
        drawBorder(inventory);
        holder.gamesBySlot.clear();

        DailyStatSnapshot total = daily.statsManager().stat(holder.viewer, null);
        inventory.setItem(SUMMARY_SLOT, playerSummary(holder.viewer, total));

        List<GameTypeEnum> games = daily.enabledGames().stream()
                .sorted(Comparator.comparingInt(Enum::ordinal)).toList();
        List<Integer> slots = gameSlots(games.size());
        for (int index = 0; index < games.size() && index < slots.size(); index++) {
            GameTypeEnum game = games.get(index);
            int slot = slots.get(index);
            inventory.setItem(slot, gameItem(holder.viewer, game));
            holder.gamesBySlot.put(slot, game);
        }
        if (games.isEmpty()) inventory.setItem(22, item(Material.GRAY_DYE,
                Component.text(GuiConfig.text("daily.menus.statistics-screen.copy.no-open-games-yet"), NamedTextColor.GRAY).decorate(TextDecoration.BOLD),
                List.of(Component.text(GuiConfig.text("daily.menus.statistics-screen.copy.completed-records-will-be-retained-forever"), NamedTextColor.DARK_GRAY)), false));

        inventory.setItem(BACK_SLOT, configured("back", null, Map.of(), item(Material.ARROW,
                Component.text(GuiConfig.text("daily.menus.statistics-screen.copy.return-to-lobby"), NamedTextColor.WHITE).decorate(TextDecoration.BOLD), List.of(), false)));
        inventory.setItem(LEADERBOARD_SLOT, configured("leaderboard", null, Map.of(), item(Material.GOLD_INGOT,
                Component.text(GuiConfig.text("daily.menus.statistics-screen.copy.view-the-leaderboard"), NamedTextColor.GOLD).decorate(TextDecoration.BOLD),
                List.of(Component.text(GuiConfig.text("daily.menus.statistics-screen.copy.view-game-data-and-personal-record-rankings"), NamedTextColor.GRAY),
                        Component.text(GuiConfig.text("daily.menus.statistics-screen.copy.click-to-open"), NamedTextColor.GREEN).decorate(TextDecoration.BOLD)), false)));
        inventory.setItem(CLOSE_SLOT, configured("close", null, Map.of(), item(Material.BARRIER, Component.text(GuiConfig.text("daily.menus.statistics-screen.copy.close"), NamedTextColor.RED), List.of(), false)));
    }

    private void refresh(DetailHolder holder) {
        Inventory inventory = holder.inventory;
        inventory.clear();
        drawBorder(inventory);

        DailyStatSnapshot stat = daily.statsManager().stat(holder.viewer, holder.game);
        inventory.setItem(SUMMARY_SLOT, gameSummary(holder.game, stat));
        List<String> maps = daily.knownMaps(holder.game).stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
        holder.pageCount = Math.max(1, (maps.size() + MAP_PAGE_SIZE - 1) / MAP_PAGE_SIZE);
        holder.page = Math.max(0, Math.min(holder.page, holder.pageCount - 1));

        int from = holder.page * MAP_PAGE_SIZE;
        int to = Math.min(maps.size(), from + MAP_PAGE_SIZE);
        List<Integer> slots = mapSlots(to - from);
        for (int index = from; index < to; index++) {
            inventory.setItem(slots.get(index - from), mapItem(holder.viewer, holder.game, maps.get(index)));
        }
        if (maps.isEmpty()) inventory.setItem(31, item(Material.GRAY_DYE,
                Component.text(GuiConfig.text("daily.menus.statistics-screen.copy.no-subdivision-records-yet"), NamedTextColor.GRAY).decorate(TextDecoration.BOLD),
                List.of(Component.text(GuiConfig.text("daily.menus.statistics-screen.copy.after-completing-the-corresponding-game-the-map-record-will-be-displayed-here"), NamedTextColor.DARK_GRAY)), false));

        inventory.setItem(BACK_SLOT, configured("back", null, Map.of(), item(Material.ARROW,
                Component.text(GuiConfig.text("daily.menus.statistics-screen.copy.return-to-game-category"), NamedTextColor.WHITE).decorate(TextDecoration.BOLD), List.of(), false)));
        inventory.setItem(LEADERBOARD_SLOT, configured("leaderboard", null, Map.of(), item(Material.GOLD_INGOT,
                Component.text(GuiConfig.text("daily.menus.statistics-screen.copy.view-the-leaderboard"), NamedTextColor.GOLD).decorate(TextDecoration.BOLD),
                List.of(Component.text(GuiConfig.text("daily.menus.statistics-screen.copy.view-game-data-and-personal-record-rankings"), NamedTextColor.GRAY)), false)));
        if (holder.page > 0) inventory.setItem(48, configured("previous", null, Map.of(), item(Material.ARROW,
                Component.text(GuiConfig.text("daily.menus.statistics-screen.copy.previous-page"), NamedTextColor.WHITE), List.of(), false)));
        inventory.setItem(REFRESH_SLOT, configured("refresh", null, Map.of(), item(Material.CLOCK,
                Component.text(GuiConfig.text("daily.menus.statistics-screen.copy.refresh"), NamedTextColor.YELLOW).decorate(TextDecoration.BOLD),
                List.of(Component.text(GuiConfig.text("daily.menus.statistics-screen.copy.update-the-detailed-records-of-the-current-game"), NamedTextColor.GRAY)), false)));
        inventory.setItem(PAGE_SLOT, configured("page", null,
                Map.of("page", holder.page + 1, "pages", holder.pageCount), item(Material.PAPER,
                Component.text(GuiConfig.text("daily.menus.statistics-screen.copy.ordinal-prefix") + (holder.page + 1) + " / " + holder.pageCount + GuiConfig.text("daily.menus.statistics-screen.copy.page-suffix"), NamedTextColor.AQUA)
                        .decorate(TextDecoration.BOLD),
                List.of(Component.text(maps.size() + GuiConfig.text("daily.menus.statistics-screen.copy.map"), NamedTextColor.GRAY)), false)));
        if (holder.page + 1 < holder.pageCount) inventory.setItem(52, configured("next", null, Map.of(), item(Material.ARROW,
                Component.text(GuiConfig.text("daily.menus.statistics-screen.copy.next-page"), NamedTextColor.WHITE), List.of(), false)));
        inventory.setItem(CLOSE_SLOT, configured("close", null, Map.of(), item(Material.BARRIER, Component.text(GuiConfig.text("daily.menus.statistics-screen.copy.close"), NamedTextColor.RED), List.of(), false)));
    }

    private ItemStack playerSummary(UUID viewer, DailyStatSnapshot stat) {
        List<Component> lore = List.of(
                line(GuiConfig.text("daily.menus.statistics-screen.copy.complete-session"), stat.gamesPlayed() + GuiConfig.text("daily.menus.statistics-screen.copy.match-count-suffix"), NamedTextColor.WHITE),
                Component.empty(),
                Component.text(GuiConfig.text("daily.menus.statistics-screen.copy.sort-by-game-click-on-card-to-see-breakdown"), NamedTextColor.GRAY)
        );
        ItemStack stack = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta rawMeta = stack.getItemMeta();
        if (!(rawMeta instanceof SkullMeta meta)) return item(Material.PLAYER_HEAD,
                Component.text(GuiConfig.text("daily.menus.statistics-screen.copy.my-overview"), NamedTextColor.AQUA).decorate(TextDecoration.BOLD), lore, false);
        meta.setOwningPlayer(Bukkit.getOfflinePlayer(viewer));
        meta.displayName(Component.text(GuiConfig.text("daily.menus.statistics-screen.copy.my-overview"), NamedTextColor.AQUA).decorate(TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore.stream().map(line -> line.decoration(TextDecoration.ITALIC, false)).toList());
        stack.setItemMeta(meta);
        return stack;
    }

    /** Game card: this game's best value of every metric across all maps, plus games played. */
    private ItemStack gameItem(UUID viewer, GameTypeEnum game) {
        DailyStatSnapshot stat = daily.statsManager().stat(viewer, game);
        List<Component> lore = new ArrayList<>();
        boolean recorded = stat.gamesPlayed() > 0;
        for (DailyMetric metric : DailyMetric.forGame(game)) {
            double value = daily.statsManager().metricValue(viewer, null, metric);
            if (!Double.isNaN(value)) recorded = true;
            lore.add(line(GuiConfig.text(metric.labelKey()) + "  ",
                    Double.isNaN(value) ? GuiConfig.text("daily.menus.statistics-screen.copy.no-record-yet")
                            : DailyMetric.format(metric, value),
                    Double.isNaN(value) ? NamedTextColor.DARK_GRAY : metricColor(metric)));
        }
        lore.add(line(GuiConfig.text("daily.menus.statistics-screen.copy.participate-in-sessions"),
                stat.gamesPlayed() + GuiConfig.text("daily.menus.statistics-screen.copy.match-count-suffix"), NamedTextColor.WHITE));
        lore.add(Component.empty());
        lore.add(Component.text(daily.knownMaps(game).size() + GuiConfig.text("daily.menus.statistics-screen.copy.maps-to-view-subdivisions"), NamedTextColor.GRAY));
        lore.add(Component.empty());
        lore.add(Component.text(GuiConfig.text("daily.menus.statistics-screen.copy.click-to-view-detailed-results"), NamedTextColor.GREEN).decorate(TextDecoration.BOLD));
        return item(gameMaterial(game), Component.text(game.toString(), gameColor(game))
                .decorate(TextDecoration.BOLD), lore, recorded);
    }

    private ItemStack gameSummary(GameTypeEnum game, DailyStatSnapshot stat) {
        List<Component> lore = new ArrayList<>();
        lore.add(line(GuiConfig.text("daily.menus.statistics-screen.copy.participate-in-sessions"), stat.gamesPlayed() + GuiConfig.text("daily.menus.statistics-screen.copy.match-count-suffix"), NamedTextColor.WHITE));
        lore.add(Component.empty());
        lore.add(Component.text(GuiConfig.text("daily.menus.statistics-screen.copy.click-the-map-below-to-view-various-records"), NamedTextColor.GRAY));
        return item(gameMaterial(game), Component.text(game.toString(), gameColor(game))
                .decorate(TextDecoration.BOLD), lore, stat.gamesPlayed() > 0);
    }

    /** Map item: the viewer's best value of every metric on this one map. */
    private ItemStack mapItem(UUID viewer, GameTypeEnum game, String map) {
        List<Component> lore = new ArrayList<>();
        boolean recorded = daily.statsManager().mapStat(viewer, game, map).gamesPlayed() > 0;
        for (DailyMetric metric : DailyMetric.forGame(game)) {
            double value = daily.statsManager().metricValue(viewer, map, metric);
            if (!Double.isNaN(value)) recorded = true;
            lore.add(line(GuiConfig.text(metric.labelKey()) + "  ",
                    Double.isNaN(value) ? GuiConfig.text("daily.menus.statistics-screen.copy.no-record-yet")
                            : DailyMetric.format(metric, value),
                    Double.isNaN(value) ? NamedTextColor.DARK_GRAY : metricColor(metric)));
        }
        lore.add(line(GuiConfig.text("daily.menus.statistics-screen.copy.participate-in-sessions"),
                daily.statsManager().mapStat(viewer, game, map).gamesPlayed()
                        + GuiConfig.text("daily.menus.statistics-screen.copy.match-count-suffix"), NamedTextColor.WHITE));
        lore.add(Component.empty());
        lore.add(Component.text(GuiConfig.text("daily.menus.statistics-screen.copy.record-independently-by-map"), NamedTextColor.GRAY));
        return item(gameMaterial(game), Component.text(map, gameColor(game)).decorate(TextDecoration.BOLD), lore, recorded);
    }

    private static List<Integer> gameSlots(int count) {
        if (count <= 1) return GAME_SLOTS_ONE;
        if (count == 2) return GAME_SLOTS_TWO;
        if (count == 3) return GAME_SLOTS_THREE;
        return GAME_SLOTS_MANY;
    }

    /** Centered grid slots for one page of map cards: rows of seven, the middle row when few. */
    static List<Integer> mapSlots(int count) {
        if (count <= 0) return List.of();
        int rows = Math.min(3, (count + 6) / 7);
        List<Integer> rowStarts = rows == 1 ? List.of(27) : List.of(18, 27, 36).subList(0, rows);
        List<Integer> slots = new ArrayList<>(count);
        int base = count / rows;
        int extra = count % rows;
        int row = 0;
        for (int rowStart : rowStarts) {
            int rowCount = base + (row++ < extra ? 1 : 0);
            int first = rowStart + (9 - rowCount) / 2;
            for (int index = 0; index < rowCount; index++) slots.add(first + index);
        }
        return slots;
    }

    private static void drawBorder(Inventory inventory) {
        ItemStack border = item(Material.BLACK_STAINED_GLASS_PANE, Component.text(" "), List.of(), false);
        for (int slot = 0; slot < 9; slot++) inventory.setItem(slot, border);
        for (int slot = 45; slot < SIZE; slot++) inventory.setItem(slot, border);
    }

    private static Material gameMaterial(GameTypeEnum game) {
        return game == GameTypeEnum.Bingo ? Material.FILLED_MAP
                : game == GameTypeEnum.AceRace ? Material.ELYTRA
                : game == GameTypeEnum.DragonEggCarnival ? Material.DRAGON_EGG : Material.PAPER;
    }

    private static NamedTextColor gameColor(GameTypeEnum game) {
        return game == GameTypeEnum.Bingo ? NamedTextColor.LIGHT_PURPLE
                : game == GameTypeEnum.AceRace ? NamedTextColor.AQUA
                : game == GameTypeEnum.DragonEggCarnival ? NamedTextColor.GOLD : NamedTextColor.WHITE;
    }

    static NamedTextColor metricColor(DailyMetric metric) {
        return switch (metric.format()) {
            case TIME -> NamedTextColor.AQUA;
            case DAMAGE -> NamedTextColor.RED;
            case PERCENT -> NamedTextColor.GREEN;
            case COUNT -> NamedTextColor.GOLD;
        };
    }

    private static Component line(String label, String value, NamedTextColor color) {
        return Component.text(label, NamedTextColor.GRAY).append(Component.text(value, color));
    }

    private static ItemStack item(Material material, Component name, List<Component> lore, boolean glint) {
        return ink.ziip.championshipscore.api.gui.GuiMenu.item(material, name, lore, glint);
    }

    /** Renders a fixed footer/control button from gui.yml, keeping the hardcoded item as fallback. */
    private static ItemStack configured(String key, String state, Map<String, ?> placeholders, ItemStack fallback) {
        return ConfiguredGui.item(MENU_PATH + ".items." + key, state, placeholders, fallback);
    }

    private static void clickSound(Player player, float pitch) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7F, pitch);
    }

    static final class StatsHolder implements InventoryHolder {
        private final UUID viewer;
        private final Map<Integer, GameTypeEnum> gamesBySlot = new HashMap<>();
        private Inventory inventory;

        private StatsHolder(UUID viewer) {
            this.viewer = viewer;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }

    static final class DetailHolder implements InventoryHolder {
        private final UUID viewer;
        private final GameTypeEnum game;
        private Inventory inventory;
        private int page;
        private int pageCount = 1;

        private DetailHolder(UUID viewer, GameTypeEnum game) {
            this.viewer = viewer;
            this.game = game;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }
}
