package ink.ziip.championshipscore.api.daily;

import ink.ziip.championshipscore.api.gui.MenuId;
import ink.ziip.championshipscore.configuration.config.message.ConfiguredGui;
import ink.ziip.championshipscore.configuration.config.message.GuiConfig;
import ink.ziip.championshipscore.platform.bukkit.text.LegacyText;
import ink.ziip.championshipscore.configuration.config.message.GuiText;

import ink.ziip.championshipscore.ChampionshipsCore;
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

/**
 * Unified DAILY leaderboards mirroring the stats menu structure: game cards first (one line per
 * metric showing only the leader), map-level details on the second screen with the top three.
 */
public final class DailyLeaderboardMenu {
    private static final String MENU_PATH = MenuId.DAILY_LEADERBOARD.path();
    private static final int INVENTORY_SIZE = 54;
    private static final int DETAIL_PAGE_SIZE = DailyStatsMenu.MAP_PAGE_SIZE;
    private static final int PLAYER_SLOT = 1;
    private static final int OVERVIEW_SLOT = 4;
    private static final int RECORD_SLOT = 7;
    private static final int BACK_SLOT = 45;
    private static final int PREVIOUS_SLOT = 48;
    private static final int REFRESH_SLOT = 49;
    private static final int PAGE_SLOT = 50;
    private static final int NEXT_SLOT = 52;
    private static final int CLOSE_SLOT = 53;
    private static final int PODIUM_SIZE = 3;

    private final ChampionshipsCore plugin;
    private final DailyManager daily;
    private final DailyStatsManager stats;

    DailyLeaderboardMenu(ChampionshipsCore plugin, DailyManager daily, DailyStatsManager stats) {
        this.plugin = plugin;
        this.daily = daily;
        this.stats = stats;
    }

    void open(Player player) {
        LeaderboardHolder holder = new LeaderboardHolder(player.getUniqueId(), null);
        GuiConfig.MenuSpec menu = GuiConfig.menu(MENU_PATH, INVENTORY_SIZE, "", List.of());
        holder.inventory = Bukkit.createInventory(holder, menu.size(), menu.title());
        refreshCategories(holder);
        player.openInventory(holder.inventory);
    }

    void refreshOpenMenus() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof LeaderboardHolder holder) {
                if (holder.game == null) refreshCategories(holder);
                else refreshBoard(holder);
            }
        }
    }

    private void openBoard(Player player, GameTypeEnum game) {
        LeaderboardHolder holder = new LeaderboardHolder(player.getUniqueId(), game);
        holder.inventory = Bukkit.createInventory(holder, INVENTORY_SIZE,
                GuiConfig.component(MENU_PATH + ".items.category.title",
                        Map.of("board", game.toString())));
        refreshBoard(holder);
        player.openInventory(holder.inventory);
    }

    void click(Player player, int slot, LeaderboardHolder holder) {
        if (!holder.viewer.equals(player.getUniqueId())) {
            player.closeInventory();
            return;
        }
        if (slot == CLOSE_SLOT) {
            player.closeInventory();
            return;
        }
        if (slot == BACK_SLOT) {
            if (holder.game == null) daily.openMenu(player);
            else open(player);
            clickSound(player, 1F);
            return;
        }
        if (slot == REFRESH_SLOT) {
            if (holder.game == null) refreshCategories(holder); else refreshBoard(holder);
            clickSound(player, 1.1F);
            return;
        }
        if (slot == PREVIOUS_SLOT && holder.page > 0) {
            holder.page--;
            if (holder.game == null) refreshCategories(holder); else refreshBoard(holder);
            clickSound(player, 1F);
            return;
        }
        if (slot == NEXT_SLOT && holder.page + 1 < holder.pageCount) {
            holder.page++;
            if (holder.game == null) refreshCategories(holder); else refreshBoard(holder);
            clickSound(player, 1F);
            return;
        }
        if (holder.game == null) {
            GameTypeEnum game = holder.gamesBySlot.get(slot);
            if (game != null) {
                openBoard(player, game);
                clickSound(player, 1.2F);
            }
        }
    }

    private void refreshCategories(LeaderboardHolder holder) {
        Inventory inventory = holder.inventory;
        inventory.clear();
        holder.gamesBySlot.clear();
        drawBorder(inventory);
        List<GameTypeEnum> games = daily.enabledGames().stream()
                .filter(game -> !DailyMetric.forGame(game).isEmpty())
                .sorted(Comparator.comparingInt(Enum::ordinal)).toList();
        holder.pageCount = Math.max(1, (games.size() + 6) / 7);
        holder.page = Math.max(0, Math.min(holder.page, holder.pageCount - 1));

        inventory.setItem(PLAYER_SLOT, playerSummary(holder.viewer));
        int totalEntries = 0;
        for (GameTypeEnum game : games) {
            for (DailyMetric metric : DailyMetric.forGame(game)) {
                totalEntries += stats.leaderboard(metric.boardId(null)).size();
            }
        }
        final int entryCount = totalEntries;
        List<Component> overviewLore = new ArrayList<>();
        for (String line : GuiConfig.lines(MENU_PATH + ".items.overview.lore")) {
            overviewLore.add(LegacyText.component(line
                    .replace("%games%", String.valueOf(games.size()))
                    .replace("%entries%", String.valueOf(entryCount))));
        }
        inventory.setItem(OVERVIEW_SLOT, item(Material.NETHER_STAR,
                Component.text(GuiConfig.text(MENU_PATH + ".items.overview.title"), NamedTextColor.GOLD).decorate(TextDecoration.BOLD),
                overviewLore, false));
        inventory.setItem(RECORD_SLOT, personalRecords(holder.viewer, games));

        // Keep the leaderboard's game cards aligned with the corresponding stats menu.
        // In particular, the shared layout uses spaced, centered cards instead of treating
        // an arbitrary center slot as a row start (which could place cards at the row edge or
        // even spill into the next row when only a few games are enabled).
        List<Integer> slots = DailyStatsMenu.gameSlots(games.size());
        for (int index = 0; index < games.size() && index < slots.size(); index++) {
            GameTypeEnum game = games.get(index);
            int slot = slots.get(index);
            inventory.setItem(slot, gameItem(game, holder.viewer));
            holder.gamesBySlot.put(slot, game);
        }
        if (games.isEmpty()) inventory.setItem(22, item(Material.GRAY_DYE,
                Component.text(GuiConfig.text(MENU_PATH + ".items.empty.title"), NamedTextColor.GRAY).decorate(TextDecoration.BOLD),
                List.of(Component.text(GuiConfig.line(MENU_PATH + ".items.empty.lore", 0), NamedTextColor.DARK_GRAY)), false));

        inventory.setItem(BACK_SLOT, configured("back", null, Map.of(), item(Material.ARROW,
                Component.text(GuiConfig.text(MENU_PATH + ".items.back.states.selection.title"), NamedTextColor.WHITE).decorate(TextDecoration.BOLD),
                List.of(), false)));
        inventory.setItem(REFRESH_SLOT, configured("refresh", null, Map.of(), refreshItem()));
        inventory.setItem(CLOSE_SLOT, configured("close", null, Map.of(), closeItem()));
    }

    /** Map-level board: one item per map, every metric listing its top three. */
    private void refreshBoard(LeaderboardHolder holder) {
        Inventory inventory = holder.inventory;
        inventory.clear();
        drawBorder(inventory);

        List<String> maps = daily.knownMaps(holder.game).stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
        holder.pageCount = Math.max(1, (maps.size() + DETAIL_PAGE_SIZE - 1) / DETAIL_PAGE_SIZE);
        holder.page = Math.max(0, Math.min(holder.page, holder.pageCount - 1));

        inventory.setItem(OVERVIEW_SLOT, item(gameMaterial(holder.game),
                Component.text(holder.game.toString(), gameColor(holder.game)).decorate(TextDecoration.BOLD),
                List.of(
                        Component.text(GuiConfig.line(MENU_PATH + ".items.overview.states.board.lore", 0), NamedTextColor.GRAY),
                        Component.text(GuiConfig.text(MENU_PATH + ".items.overview.states.board.lore", Map.of("maps", maps.size())), NamedTextColor.WHITE)
                ), false));
        inventory.setItem(RECORD_SLOT, personalRecords(holder.viewer, List.of(holder.game)));

        int from = holder.page * DETAIL_PAGE_SIZE;
        int to = Math.min(maps.size(), from + DETAIL_PAGE_SIZE);
        List<Integer> slots = DailyStatsMenu.mapSlots(to - from);
        for (int index = from; index < to; index++) {
            inventory.setItem(slots.get(index - from), mapItem(holder.game, maps.get(index)));
        }
        if (maps.isEmpty()) inventory.setItem(22, item(Material.GRAY_DYE,
                Component.text(GuiConfig.text(MENU_PATH + ".items.empty.title"), NamedTextColor.GRAY).decorate(TextDecoration.BOLD),
                List.of(
                        Component.text(GuiConfig.line(MENU_PATH + ".items.empty.lore", 0), NamedTextColor.DARK_GRAY),
                        Component.text(GuiConfig.line(MENU_PATH + ".items.empty.lore", 1), NamedTextColor.YELLOW)
                ), false));

        inventory.setItem(BACK_SLOT, configured("back", null, Map.of(), item(Material.ARROW,
                Component.text(GuiConfig.text(MENU_PATH + ".items.back.states.board.title"), NamedTextColor.WHITE).decorate(TextDecoration.BOLD),
                List.of(), false)));
        if (holder.page > 0) inventory.setItem(PREVIOUS_SLOT, configured("previous", null, Map.of(), item(Material.ARROW,
                Component.text(GuiConfig.text(MENU_PATH + ".items.previous.title"), NamedTextColor.WHITE), List.of(), false)));
        inventory.setItem(REFRESH_SLOT, configured("refresh", null, Map.of(), refreshItem()));
        inventory.setItem(PAGE_SLOT, configured("page", null,
                Map.of("page", holder.page + 1, "pages", holder.pageCount), item(Material.PAPER,
                GuiConfig.component(MENU_PATH + ".items.page.title", Map.of("page", holder.page + 1, "pages", holder.pageCount)),
                List.of(Component.text(GuiConfig.text(MENU_PATH + ".items.page.lore", Map.of("maps", maps.size())), NamedTextColor.GRAY)), false)));
        if (holder.page + 1 < holder.pageCount) inventory.setItem(NEXT_SLOT, configured("next", null, Map.of(), item(Material.ARROW,
                Component.text(GuiConfig.text(MENU_PATH + ".items.next.title"), NamedTextColor.WHITE), List.of(), false)));
        inventory.setItem(CLOSE_SLOT, configured("close", null, Map.of(), closeItem()));
    }

    private ItemStack playerSummary(UUID viewer) {
        return playerHead(viewer,
                Component.text(GuiConfig.text(MENU_PATH + ".items.player.title"), NamedTextColor.AQUA).decorate(TextDecoration.BOLD),
                List.of(
                        Component.text(GuiConfig.line(MENU_PATH + ".items.player.lore", 0), NamedTextColor.GRAY),
                        Component.empty(),
                        Component.text(GuiConfig.line(MENU_PATH + ".items.player.lore", 1), NamedTextColor.DARK_GRAY)
                ), false);
    }

    /** The viewer's own cross-map records; timed metrics show the three fastest attempts. */
    private ItemStack personalRecords(UUID viewer, List<GameTypeEnum> games) {
        List<Component> lore = new ArrayList<>();
        for (GameTypeEnum game : games) {
            for (DailyMetric metric : DailyMetric.forGame(game)) {
                List<Double> values = stats.metricValues(viewer, null, metric);
                if (values.isEmpty()) {
                    lore.add(Component.text(GuiConfig.text(metric.labelKey()), NamedTextColor.GRAY)
                            .append(Component.text(GuiConfig.line(MENU_PATH + ".items.records.states.empty.lore", 0),
                                    DailyStatsMenu.metricColor(metric))));
                    continue;
                }
                for (int index = 0; index < values.size(); index++) {
                    lore.add(Component.text(DailyStatsMenu.metricRecordLabel(metric, index), NamedTextColor.GRAY)
                            .append(Component.text(stats.formatMetricValue(viewer, null, metric, values.get(index)),
                                    DailyStatsMenu.metricColor(metric))));
                }
            }
        }
        if (lore.isEmpty()) lore.add(Component.text(GuiConfig.line(MENU_PATH + ".items.records.states.empty.lore", 0), NamedTextColor.DARK_GRAY));
        lore.add(Component.empty());
        lore.add(Component.text(GuiConfig.line(MENU_PATH + ".items.records.lore", 0), NamedTextColor.YELLOW));
        return item(Material.CLOCK,
                Component.text(GuiConfig.text(MENU_PATH + ".items.records.title"), NamedTextColor.LIGHT_PURPLE).decorate(TextDecoration.BOLD), lore, false);
    }

    /** Game card: for every metric only the cross-map leader, plus the viewer's own position. */
    private ItemStack gameItem(GameTypeEnum game, UUID viewer) {
        List<Component> lore = new ArrayList<>();
        boolean listed = false;
        for (DailyMetric metric : DailyMetric.forGame(game)) {
            List<DailyLeaderboardEntry> entries = stats.leaderboard(metric.boardId(null));
            lore.add(Component.text(GuiConfig.text(metric.labelKey()), NamedTextColor.GRAY));
            if (entries.isEmpty()) {
                lore.add(LegacyText.component(GuiConfig.line(MENU_PATH + ".items.category.lore", 1)));
                continue;
            }
            DailyLeaderboardEntry leader = entries.getFirst();
            lore.add(LegacyText.component(GuiConfig.line(MENU_PATH + ".items.category.lore", 0,
                    Map.of("leader", leader.name(), "value", stats.formatLeaderboardValue(metric, leader)))));
            int position = position(entries, viewer);
            if (position > 0) listed = true;
        }
        lore.add(Component.empty());
        lore.add(Component.text(GuiConfig.text(MENU_PATH + ".items.category.lore", Map.of("maps", daily.knownMaps(game).size(), "leader", "", "value", "")), NamedTextColor.GRAY));
        lore.add(Component.empty());
        lore.add(Component.text(GuiConfig.line(MENU_PATH + ".items.category.lore", 2), NamedTextColor.GREEN).decorate(TextDecoration.BOLD));
        return item(gameMaterial(game), Component.text(game.toString(), gameColor(game))
                .decorate(TextDecoration.BOLD), lore, listed);
    }

    /** Map card: every metric of one map listing its top three. */
    private ItemStack mapItem(GameTypeEnum game, String map) {
        List<Component> lore = new ArrayList<>();
        boolean recorded = false;
        for (DailyMetric metric : DailyMetric.forGame(game)) {
            lore.add(Component.text(GuiConfig.text(metric.labelKey()), NamedTextColor.GRAY));
            List<DailyLeaderboardEntry> entries = stats.leaderboard(metric.boardId(map));
            if (entries.isEmpty()) {
                lore.add(LegacyText.component(GuiConfig.line(MENU_PATH + ".items.category.lore", 1)));
                continue;
            }
            recorded = true;
            for (int index = 0; index < Math.min(PODIUM_SIZE, entries.size()); index++) {
                DailyLeaderboardEntry entry = entries.get(index);
                NamedTextColor rankColor = index == 0 ? NamedTextColor.GOLD
                        : index == 1 ? NamedTextColor.AQUA : NamedTextColor.YELLOW;
                        lore.add(LegacyText.component(GuiConfig.line(MENU_PATH + ".items.row.lore", 0,
                    Map.of("rank", index + 1, "player", entry.name(),
                           "value", stats.formatLeaderboardValue(metric, entry)))));
            }
        }
        lore.add(Component.empty());
        lore.add(Component.text(GuiConfig.line(MENU_PATH + ".items.category.lore", 4), NamedTextColor.DARK_GRAY));
        return item(gameMaterial(game), Component.text(map, gameColor(game)).decorate(TextDecoration.BOLD), lore, recorded);
    }

    private static int position(List<DailyLeaderboardEntry> entries, UUID viewer) {
        for (int index = 0; index < entries.size(); index++)
            if (entries.get(index).player().equals(viewer)) return index + 1;
        return -1;
    }

    public static String formatDuration(long millis) {
        long minutes = millis / 60_000L;
        long seconds = (millis % 60_000L) / 1_000L;
        long remainder = millis % 1_000L;
        return "%d:%02d.%03d".formatted(minutes, seconds, remainder);
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

    private static void drawBorder(Inventory inventory) {
        ItemStack border = item(Material.BLACK_STAINED_GLASS_PANE, Component.text(" "), List.of(), false);
        for (int slot = 0; slot < 9; slot++) inventory.setItem(slot, border);
        for (int slot = 45; slot < INVENTORY_SIZE; slot++) inventory.setItem(slot, border);
    }

    private static ItemStack refreshItem() {
        return item(Material.CLOCK, Component.text(GuiConfig.text(MENU_PATH + ".items.refresh.title"), NamedTextColor.YELLOW).decorate(TextDecoration.BOLD),
                List.of(Component.text(GuiConfig.line(MENU_PATH + ".items.refresh.lore", 0), NamedTextColor.DARK_GRAY)), false);
    }

    private static ItemStack closeItem() {
        return item(Material.BARRIER, Component.text(GuiConfig.text(MENU_PATH + ".items.close.title"), NamedTextColor.RED), List.of(), false);
    }

    private static ItemStack item(Material material, Component name, List<Component> lore, boolean glint) {
        return ink.ziip.championshipscore.api.gui.GuiMenu.item(material, name, lore, glint);
    }

    /** Renders a fixed footer/control button from gui.yml, keeping the hardcoded item as fallback. */
    private static ItemStack configured(String key, String state, Map<String, ?> placeholders, ItemStack fallback) {
        return ConfiguredGui.item(MENU_PATH + ".items." + key, state, placeholders, fallback);
    }

    private static ItemStack playerHead(UUID owner, Component name, List<Component> lore, boolean glint) {
        return ink.ziip.championshipscore.api.gui.GuiMenu.playerHead(owner, name, lore, glint);
    }

    private static void clickSound(Player player, float pitch) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7F, pitch);
    }

    static final class LeaderboardHolder implements InventoryHolder {
        private final UUID viewer;
        private final GameTypeEnum game;
        private final Map<Integer, GameTypeEnum> gamesBySlot = new HashMap<>();
        private Inventory inventory;
        private int page;
        private int pageCount = 1;
        private LeaderboardHolder(UUID viewer, GameTypeEnum game) {
            this.viewer = viewer;
            this.game = game;
        }
        @Override public @NotNull Inventory getInventory() { return inventory; }
    }
}
