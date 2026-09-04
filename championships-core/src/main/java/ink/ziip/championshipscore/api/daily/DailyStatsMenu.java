package ink.ziip.championshipscore.api.daily;

import ink.ziip.championshipscore.api.gui.MenuId;
import ink.ziip.championshipscore.configuration.config.message.ConfiguredGui;
import ink.ziip.championshipscore.configuration.config.message.GuiConfig;
import ink.ziip.championshipscore.platform.bukkit.text.LegacyText;

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
    /** Map cards per detail page: three centered rows of at most seven items. */
    static final int MAP_PAGE_SIZE = 21;

    private final DailyManager daily;

    DailyStatsMenu(DailyManager daily) {
        this.daily = daily;
    }

    void open(Player player) {
        StatsHolder holder = new StatsHolder(player.getUniqueId());
        holder.inventory = Bukkit.createInventory(holder, SIZE,
                GuiConfig.component(MENU_PATH + ".title"));
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
                GuiConfig.component(MENU_PATH + ".items.game.states.detail.title", Map.of("game", game.toString())));
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
                Component.text(GuiConfig.text(MENU_PATH + ".items.empty.title"), NamedTextColor.GRAY).decorate(TextDecoration.BOLD),
                List.of(Component.text(GuiConfig.line(MENU_PATH + ".items.empty.lore", 0), NamedTextColor.DARK_GRAY)), false));

        inventory.setItem(BACK_SLOT, configured("back", null, Map.of(), item(Material.ARROW,
                Component.text(GuiConfig.text(MENU_PATH + ".items.back.title"), NamedTextColor.WHITE).decorate(TextDecoration.BOLD), List.of(), false)));
        inventory.setItem(LEADERBOARD_SLOT, configured("leaderboard", null, Map.of(), item(Material.GOLD_INGOT,
                Component.text(GuiConfig.text(MENU_PATH + ".items.leaderboard.title"), NamedTextColor.GOLD).decorate(TextDecoration.BOLD),
                List.of(Component.text(GuiConfig.line(MENU_PATH + ".items.leaderboard.lore", 0), NamedTextColor.GRAY),
                        Component.text(GuiConfig.line(MENU_PATH + ".items.leaderboard.lore", 1), NamedTextColor.GREEN).decorate(TextDecoration.BOLD)), false)));
        inventory.setItem(CLOSE_SLOT, configured("close", null, Map.of(), item(Material.BARRIER, Component.text(GuiConfig.text(MENU_PATH + ".items.close.title"), NamedTextColor.RED), List.of(), false)));
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
                Component.text(GuiConfig.text(MENU_PATH + ".items.empty.title"), NamedTextColor.GRAY).decorate(TextDecoration.BOLD),
                List.of(Component.text(GuiConfig.line(MENU_PATH + ".items.empty.lore", 0), NamedTextColor.DARK_GRAY)), false));

        inventory.setItem(BACK_SLOT, configured("back", null, Map.of(), item(Material.ARROW,
                Component.text(GuiConfig.text(MENU_PATH + ".items.back.states.detail.title"), NamedTextColor.WHITE).decorate(TextDecoration.BOLD), List.of(), false)));
        inventory.setItem(LEADERBOARD_SLOT, configured("leaderboard", null, Map.of(), item(Material.GOLD_INGOT,
                Component.text(GuiConfig.text(MENU_PATH + ".items.leaderboard.title"), NamedTextColor.GOLD).decorate(TextDecoration.BOLD),
                List.of(Component.text(GuiConfig.line(MENU_PATH + ".items.leaderboard.lore", 0), NamedTextColor.GRAY)), false)));
        if (holder.page > 0) inventory.setItem(48, configured("previous", null, Map.of(), item(Material.ARROW,
                Component.text(GuiConfig.text(MENU_PATH + ".items.previous.title"), NamedTextColor.WHITE), List.of(), false)));
        inventory.setItem(REFRESH_SLOT, configured("refresh", null, Map.of(), item(Material.CLOCK,
                Component.text(GuiConfig.text(MENU_PATH + ".items.refresh.title"), NamedTextColor.YELLOW).decorate(TextDecoration.BOLD),
                List.of(Component.text(GuiConfig.line(MENU_PATH + ".items.refresh.lore", 0), NamedTextColor.GRAY)), false)));
        inventory.setItem(PAGE_SLOT, configured("page", null,
                Map.of("page", holder.page + 1, "pages", holder.pageCount), item(Material.PAPER,
                GuiConfig.component(MENU_PATH + ".items.page.title", Map.of("page", holder.page + 1, "pages", holder.pageCount)),
                List.of(Component.text(GuiConfig.text(MENU_PATH + ".items.page.lore", Map.of("maps", maps.size())), NamedTextColor.GRAY)), false)));
        if (holder.page + 1 < holder.pageCount) inventory.setItem(52, configured("next", null, Map.of(), item(Material.ARROW,
                Component.text(GuiConfig.text(MENU_PATH + ".items.next.title"), NamedTextColor.WHITE), List.of(), false)));
        inventory.setItem(CLOSE_SLOT, configured("close", null, Map.of(), item(Material.BARRIER, Component.text(GuiConfig.text(MENU_PATH + ".items.close.title"), NamedTextColor.RED), List.of(), false)));
    }

    private ItemStack playerSummary(UUID viewer, DailyStatSnapshot stat) {
        List<Component> lore = List.of(
                LegacyText.component(GuiConfig.line(MENU_PATH + ".items.summary.lore", 0, Map.of("games", stat.gamesPlayed()))),
                Component.empty(),
                Component.text(GuiConfig.line(MENU_PATH + ".items.summary.lore", 2), NamedTextColor.GRAY)
        );
        ItemStack stack = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta rawMeta = stack.getItemMeta();
        if (!(rawMeta instanceof SkullMeta meta)) return item(Material.PLAYER_HEAD,
                Component.text(GuiConfig.text(MENU_PATH + ".items.summary.title"), NamedTextColor.AQUA).decorate(TextDecoration.BOLD), lore, false);
        meta.setOwningPlayer(Bukkit.getOfflinePlayer(viewer));
        meta.displayName(Component.text(GuiConfig.text(MENU_PATH + ".items.summary.title"), NamedTextColor.AQUA).decorate(TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore.stream().map(line -> line.decoration(TextDecoration.ITALIC, false)).toList());
        stack.setItemMeta(meta);
        return stack;
    }

    /** Game card: this game's records across all maps (three rows for timed metrics), plus games played. */
    private ItemStack gameItem(UUID viewer, GameTypeEnum game) {
        DailyStatSnapshot stat = daily.statsManager().stat(viewer, game);
        List<Component> lore = new ArrayList<>();
        boolean recorded = stat.gamesPlayed() > 0;
        for (DailyMetric metric : DailyMetric.forGame(game)) {
            List<Double> values = daily.statsManager().metricValues(viewer, null, metric);
            if (values.isEmpty()) {
                lore.add(metricLine(DailyStatsMenu.metricRecordLabel(metric, 0),
                        GuiConfig.text(MENU_PATH + ".items.metric.states.no-record.title")));
            } else {
                recorded = true;
                for (int index = 0; index < values.size(); index++) {
                    lore.add(metricLine(DailyStatsMenu.metricRecordLabel(metric, index),
                            daily.statsManager().formatMetricValue(viewer, null, metric, values.get(index))));
                }
            }
        }
        lore.add(LegacyText.component(GuiConfig.line(MENU_PATH + ".items.game.lore", 0, Map.of("games", stat.gamesPlayed()))));
        lore.add(Component.empty());
        lore.add(Component.text(GuiConfig.text(MENU_PATH + ".items.game.lore", Map.of("maps", daily.knownMaps(game).size())), NamedTextColor.GRAY));
        lore.add(Component.empty());
        lore.add(Component.text(GuiConfig.line(MENU_PATH + ".items.game.lore", 4), NamedTextColor.GREEN).decorate(TextDecoration.BOLD));
        return item(gameMaterial(game), Component.text(game.toString(), gameColor(game))
                .decorate(TextDecoration.BOLD), lore, recorded);
    }

    private ItemStack gameSummary(GameTypeEnum game, DailyStatSnapshot stat) {
        List<Component> lore = new ArrayList<>();
        lore.add(LegacyText.component(GuiConfig.line(MENU_PATH + ".items.game.lore", 0, Map.of("games", stat.gamesPlayed()))));
        lore.add(Component.empty());
        lore.add(Component.text(GuiConfig.line(MENU_PATH + ".items.game.lore", 2), NamedTextColor.GRAY));
        return item(gameMaterial(game), Component.text(game.toString(), gameColor(game))
                .decorate(TextDecoration.BOLD), lore, stat.gamesPlayed() > 0);
    }

    /** Map item: the viewer's records on this one map (three rows for timed metrics). */
    private ItemStack mapItem(UUID viewer, GameTypeEnum game, String map) {
        List<Component> lore = new ArrayList<>();
        boolean recorded = daily.statsManager().mapStat(viewer, game, map).gamesPlayed() > 0;
        for (DailyMetric metric : DailyMetric.forGame(game)) {
            List<Double> values = daily.statsManager().metricValues(viewer, map, metric);
            if (values.isEmpty()) {
                lore.add(metricLine(DailyStatsMenu.metricRecordLabel(metric, 0),
                        GuiConfig.text(MENU_PATH + ".items.metric.states.no-record.title")));
            } else {
                recorded = true;
                for (int index = 0; index < values.size(); index++) {
                    lore.add(metricLine(DailyStatsMenu.metricRecordLabel(metric, index),
                            daily.statsManager().formatMetricValue(viewer, map, metric, values.get(index))));
                }
            }
        }
        lore.add(LegacyText.component(GuiConfig.line(MENU_PATH + ".items.game.lore", 0,
                Map.of("games", daily.statsManager().mapStat(viewer, game, map).gamesPlayed()))));
        lore.add(Component.empty());
        lore.add(Component.text(GuiConfig.line(MENU_PATH + ".items.map.lore", 2), NamedTextColor.GRAY));
        return item(gameMaterial(game), Component.text(map, gameColor(game)).decorate(TextDecoration.BOLD), lore, recorded);
    }

    /** Shared game-card positions used by both the personal stats and total leaderboard menus. */
    static List<Integer> gameSlots(int count) {
        if (count <= 0) return List.of();
        if (count <= 1) return GAME_SLOTS_ONE;
        if (count == 2) return GAME_SLOTS_TWO;
        if (count == 3) return GAME_SLOTS_THREE;
        // Keep cards evenly balanced across two rows once more than three games are shown.
        // The previous fixed six-slot list put a fourth card alone at the start of the next row.
        int firstRowCount = (count + 1) / 2;
        int secondRowCount = count - firstRowCount;
        List<Integer> slots = new ArrayList<>(count);
        slots.addAll(centeredSpacedRow(18, firstRowCount));
        slots.addAll(centeredSpacedRow(27, secondRowCount));
        return slots;
    }

    private static List<Integer> centeredSpacedRow(int rowStart, int count) {
        int center = rowStart + 4;
        int first = center - (count - 1);
        List<Integer> slots = new ArrayList<>(count);
        for (int index = 0; index < count; index++) slots.add(first + index * 2);
        return slots;
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
            case COMPOSITE -> NamedTextColor.LIGHT_PURPLE;
        };
    }

    static String metricRecordLabel(DailyMetric metric, int index) {
        if (metric.format() != DailyMetric.Format.TIME) return GuiConfig.text(metric.labelKey());
        return switch (index) {
            case 0 -> GuiConfig.text(metric.labelKey(1));
            case 1 -> GuiConfig.text(metric.labelKey(2));
            default -> GuiConfig.text(metric.labelKey(3));
        };
    }

    private static Component metricLine(String label, String value) {
        return LegacyText.component(GuiConfig.line(MENU_PATH + ".items.metric.lore", 0,
                Map.of("label", label, "value", value)));
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
