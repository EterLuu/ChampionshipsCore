package ink.ziip.championshipscore.api.daily;

import ink.ziip.championshipscore.api.game.model.GameTypeEnum;
import ink.ziip.championshipscore.api.gui.MenuId;
import ink.ziip.championshipscore.configuration.config.message.ConfiguredGui;
import ink.ziip.championshipscore.configuration.config.message.GuiConfig;
import ink.ziip.championshipscore.platform.bukkit.text.LegacyText;

import net.kyori.adventure.text.Component;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Unified DAILY leaderboards mirroring the stats menu structure: game cards first (one line per
 * metric showing only the leader), map-level details on the second screen with the top three.
 */
public final class DailyLeaderboardMenu {
    private static final String MENU_PATH = MenuId.DAILY_LEADERBOARD.path();
    private static final int PODIUM_SIZE = 3;

    private final DailyManager daily;
    private final DailyStatsManager stats;

    DailyLeaderboardMenu(DailyManager daily, DailyStatsManager stats) {
        this.daily = daily;
        this.stats = stats;
    }

    void open(Player player) {
        open(player, 0);
    }

    private void open(Player player, int page) {
        LeaderboardHolder holder = new LeaderboardHolder(player.getUniqueId(), null, 0);
        holder.page = page;
        holder.inventory =
                Bukkit.createInventory(
                        holder, holder.layout.size(), GuiConfig.component(MENU_PATH + ".title"));
        refreshCategories(holder);
        player.openInventory(holder.inventory);
    }

    void refreshOpenMenus() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder()
                    instanceof LeaderboardHolder holder) refresh(holder);
        }
    }

    private void refresh(LeaderboardHolder holder) {
        if (holder.game == null) refreshCategories(holder);
        else refreshBoard(holder);
    }

    private void openBoard(Player player, GameTypeEnum game, int returnPage) {
        LeaderboardHolder holder = new LeaderboardHolder(player.getUniqueId(), game, returnPage);
        holder.inventory =
                Bukkit.createInventory(
                        holder,
                        holder.layout.size(),
                        GuiConfig.component(
                                MENU_PATH + ".items.category.states.detail.title",
                                Map.of("board", game.toString())));
        refreshBoard(holder);
        player.openInventory(holder.inventory);
    }

    void click(Player player, int slot, LeaderboardHolder holder) {
        if (!holder.viewer.equals(player.getUniqueId())) {
            player.closeInventory();
            return;
        }
        if (slot == holder.layout.control("close")) {
            player.closeInventory();
            return;
        }
        float pitch = 1F;
        if (slot == holder.layout.control("back")) {
            if (holder.game == null) daily.openMenu(player);
            else open(player, holder.returnPage);
        } else if (slot == holder.layout.control("refresh")) {
            refresh(holder);
            pitch = 1.1F;
        } else if (slot == holder.layout.control("previous") && holder.page > 0) {
            holder.page--;
            refresh(holder);
        } else if (slot == holder.layout.control("next") && holder.page + 1 < holder.pageCount) {
            holder.page++;
            refresh(holder);
        } else {
            GameTypeEnum game = holder.gamesBySlot.get(slot);
            if (game == null) return;
            openBoard(player, game, holder.page);
            pitch = 1.2F;
        }
        clickSound(player, pitch);
    }

    private void refreshCategories(LeaderboardHolder holder) {
        holder.clear();
        List<GameTypeEnum> games =
                daily.enabledGames().stream()
                        .filter(game -> !DailyMetric.rankedForGame(game).isEmpty())
                        .sorted(Comparator.comparingInt(Enum::ordinal))
                        .toList();
        int from = holder.paginate(games.size());
        holder.inventory.setItem(holder.layout.control("player"), playerSummary(holder.viewer));
        int entries =
                games.stream()
                        .flatMap(game -> DailyMetric.rankedForGame(game).stream())
                        .mapToInt(metric -> stats.leaderboard(metric.boardId(null)).size())
                        .sum();
        holder.put("overview", null, Map.of("games", games.size(), "entries", entries));
        holder.inventory.setItem(
                holder.layout.control("records"), personalRecords(holder.viewer, games));
        List<Integer> slots = holder.layout.pageSlots(holder.page, games.size());
        for (int offset = 0; offset < slots.size(); offset++) {
            GameTypeEnum game = games.get(from + offset);
            holder.inventory.setItem(slots.get(offset), gameItem(game, holder.viewer));
            holder.gamesBySlot.put(slots.get(offset), game);
        }
        holder.footer(games.size(), false, null, true);
    }

    /** Map-level board: one item per map, every metric listing its top three. */
    private void refreshBoard(LeaderboardHolder holder) {
        holder.clear();
        List<String> maps =
                daily.knownMaps(holder.game).stream()
                        .sorted(String.CASE_INSENSITIVE_ORDER)
                        .toList();
        int from = holder.paginate(maps.size());
        holder.inventory.setItem(
                holder.layout.control("overview"),
                DailyRecordItem.render(
                        MENU_PATH + ".items.board-summary",
                        holder.game,
                        holder.game.toString(),
                        Map.of("maps", maps.size()),
                        List.of(),
                        false));
        holder.inventory.setItem(
                holder.layout.control("records"),
                personalRecords(holder.viewer, List.of(holder.game)));
        List<Integer> slots = holder.layout.pageSlots(holder.page, maps.size());
        for (int offset = 0; offset < slots.size(); offset++)
            holder.inventory.setItem(
                    slots.get(offset), mapItem(holder.game, maps.get(from + offset)));
        holder.footer(maps.size(), true, "board", true);
    }

    private ItemStack playerSummary(UUID viewer) {
        return configured(
                "player", null, Map.of(), playerHead(viewer, Component.empty(), List.of(), false));
    }

    /** The viewer's own cross-map records; timed metrics show the three fastest attempts. */
    private ItemStack personalRecords(UUID viewer, List<GameTypeEnum> games) {
        List<Component> lore = new ArrayList<>();
        for (GameTypeEnum game : games) {
            for (DailyMetric metric : DailyMetric.rankedForGame(game)) {
                List<Double> values = stats.metricValues(viewer, null, metric);
                if (values.isEmpty()) {
                    for (String empty :
                            GuiConfig.lines(MENU_PATH + ".items.records.states.empty.lore"))
                        lore.addAll(
                                metricLore(
                                        metric,
                                        GuiConfig.text(metric.labelKey() + ".title"),
                                        empty));
                } else
                    for (int index = 0; index < values.size(); index++)
                        lore.addAll(
                                metricLore(
                                        metric,
                                        DailyStatsMenu.metricRecordLabel(metric, index),
                                        stats.formatMetricValue(
                                                viewer, null, metric, values.get(index))));
            }
        }
        if (lore.isEmpty()) lore.addAll(lines("records.states.empty", Map.of()));
        return DailyRecordItem.render(
                MENU_PATH + ".items.records", null, "", Map.of(), lore, false);
    }

    private static List<Component> metricLore(DailyMetric metric, String label, String value) {
        return GuiConfig.item(
                        MENU_PATH + ".items.personal-metric",
                        metric.format().name(),
                        Map.of("label", label, "value", value))
                .lore();
    }

    private static List<Component> lines(String id, Map<String, ?> values) {
        return GuiConfig.lines(MENU_PATH + ".items." + id + ".lore", values).stream()
                .map(LegacyText::component)
                .toList();
    }

    /** Game card: for every metric only the cross-map leader, plus the viewer's own position. */
    private ItemStack gameItem(GameTypeEnum game, UUID viewer) {
        List<Component> lore = new ArrayList<>();
        boolean listed = false;
        for (DailyMetric metric : DailyMetric.rankedForGame(game)) {
            List<DailyLeaderboardEntry> entries = stats.leaderboard(metric.boardId(null));
            lore.addAll(
                    lines(
                            "metric-title",
                            Map.of("label", GuiConfig.text(metric.labelKey() + ".title"))));
            if (entries.isEmpty()) {
                lore.addAll(lines("metric-empty", Map.of()));
                continue;
            }
            DailyLeaderboardEntry leader = entries.getFirst();
            lore.addAll(
                    lines(
                            "metric-leader",
                            Map.of(
                                    "leader",
                                    leader.name(),
                                    "value",
                                    stats.formatLeaderboardValue(metric, leader))));
            if (position(entries, viewer) > 0) listed = true;
        }
        return DailyRecordItem.render(
                MENU_PATH + ".items.category",
                game,
                game.toString(),
                Map.of("board", game.toString(), "maps", daily.knownMaps(game).size()),
                lore,
                listed);
    }

    /** Map card: every metric of one map listing its top three. */
    private ItemStack mapItem(GameTypeEnum game, String map) {
        List<Component> lore = new ArrayList<>();
        boolean recorded = false;
        for (DailyMetric metric : DailyMetric.rankedForGame(game)) {
            lore.addAll(
                    lines(
                            "metric-title",
                            Map.of("label", GuiConfig.text(metric.labelKey() + ".title"))));
            List<DailyLeaderboardEntry> entries = stats.leaderboard(metric.boardId(map));
            if (entries.isEmpty()) {
                lore.addAll(lines("metric-empty", Map.of()));
                continue;
            }
            recorded = true;
            for (int index = 0; index < Math.min(PODIUM_SIZE, entries.size()); index++) {
                DailyLeaderboardEntry entry = entries.get(index);
                lore.addAll(
                        lines(
                                "row",
                                Map.of(
                                        "rank",
                                        index + 1,
                                        "player",
                                        entry.name(),
                                        "value",
                                        stats.formatLeaderboardValue(metric, entry))));
            }
        }
        return DailyRecordItem.render(
                MENU_PATH + ".items.map", game, map, Map.of("map", map), lore, recorded);
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

    /**
     * Renders a fixed footer/control button from gui.yml, keeping the hardcoded item as fallback.
     */
    private static ItemStack configured(
            String key, String state, Map<String, ?> placeholders, ItemStack fallback) {
        return ConfiguredGui.item(MENU_PATH + ".items." + key, state, placeholders, fallback);
    }

    private static ItemStack playerHead(
            UUID owner, Component name, List<Component> lore, boolean glint) {
        return ink.ziip.championshipscore.api.gui.GuiMenu.playerHead(owner, name, lore, glint);
    }

    private static void clickSound(Player player, float pitch) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7F, pitch);
    }

    static final class LeaderboardHolder extends DailyMenuPage {
        private final GameTypeEnum game;

        private LeaderboardHolder(UUID viewer, GameTypeEnum game, int returnPage) {
            super(
                    viewer,
                    game == null
                            ? MenuId.DAILY_LEADERBOARD_OVERVIEW
                            : MenuId.DAILY_LEADERBOARD_DETAIL,
                    true,
                    game != null,
                    returnPage);
            this.game = game;
        }
    }
}
