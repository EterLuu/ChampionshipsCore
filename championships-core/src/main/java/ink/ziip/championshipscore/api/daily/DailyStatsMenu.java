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

/** Categorized personal records: game cards first, map-level details on the second screen. */
final class DailyStatsMenu {
    private static final String MENU_PATH = MenuId.DAILY_STATISTICS.path();
    private final DailyManager daily;

    DailyStatsMenu(DailyManager daily) {
        this.daily = daily;
    }

    void open(Player player) {
        open(player, 0);
    }

    private void open(Player player, int page) {
        StatsHolder holder = new StatsHolder(player.getUniqueId());
        holder.page = page;
        holder.inventory =
                Bukkit.createInventory(
                        holder, holder.layout.size(), GuiConfig.component(MENU_PATH + ".title"));
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
        clickPage(player, slot, holder);
    }

    void click(Player player, int slot, DetailHolder holder) {
        clickPage(player, slot, holder);
    }

    private void clickPage(Player player, int slot, DailyMenuPage holder) {
        if (!holder.viewer.equals(player.getUniqueId())) {
            player.closeInventory();
            return;
        }
        boolean detail = holder instanceof DetailHolder;
        float pitch = 1F;
        if (slot == holder.layout.control("back")) {
            if (detail) open(player, holder.returnPage);
            else daily.openMenu(player);
        } else if (slot == holder.layout.control("leaderboard")) {
            daily.openLeaderboard(player);
            pitch = 1.15F;
        } else if (slot == holder.layout.control("close")) {
            player.closeInventory();
            return;
        } else if (slot == holder.layout.control("refresh") && detail) {
            refresh((DetailHolder) holder);
            pitch = 1.1F;
        } else if (slot == holder.layout.control("previous") && holder.page > 0) {
            holder.page--;
            refreshPage(holder);
        } else if (slot == holder.layout.control("next") && holder.page + 1 < holder.pageCount) {
            holder.page++;
            refreshPage(holder);
        } else {
            GameTypeEnum game = holder.gamesBySlot.get(slot);
            if (game == null) return;
            openDetail(player, game, holder.page);
            pitch = 1.15F;
        }
        clickSound(player, pitch);
    }

    private void refreshPage(DailyMenuPage holder) {
        if (holder instanceof DetailHolder detail) refresh(detail);
        else refresh((StatsHolder) holder);
    }

    private void openDetail(Player player, GameTypeEnum game, int returnPage) {
        DetailHolder holder = new DetailHolder(player.getUniqueId(), game, returnPage);
        holder.inventory =
                Bukkit.createInventory(
                        holder,
                        holder.layout.size(),
                        GuiConfig.component(
                                MENU_PATH + ".items.game.states.detail.title",
                                Map.of("game", game.toString())));
        refresh(holder);
        player.openInventory(holder.inventory);
    }

    private void refresh(StatsHolder holder) {
        holder.clear();
        DailyStatSnapshot total = daily.statsManager().stat(holder.viewer, null);
        holder.inventory.setItem(
                holder.layout.control("summary"), playerSummary(holder.viewer, total));
        List<GameTypeEnum> games =
                daily.enabledGames().stream()
                        .sorted(Comparator.comparingInt(Enum::ordinal))
                        .toList();
        int from = holder.paginate(games.size());
        List<Integer> slots = holder.layout.pageSlots(holder.page, games.size());
        for (int offset = 0; offset < slots.size(); offset++) {
            GameTypeEnum game = games.get(from + offset);
            holder.inventory.setItem(slots.get(offset), gameItem(holder.viewer, game));
            holder.gamesBySlot.put(slots.get(offset), game);
        }
        holder.footer(games.size(), false, null, false);
    }

    private void refresh(DetailHolder holder) {
        holder.clear();
        DailyStatSnapshot stat = daily.statsManager().stat(holder.viewer, holder.game);
        holder.inventory.setItem(holder.layout.control("summary"), gameSummary(holder.game, stat));
        List<String> maps =
                daily.knownMaps(holder.game).stream()
                        .sorted(String.CASE_INSENSITIVE_ORDER)
                        .toList();
        int from = holder.paginate(maps.size());
        List<Integer> slots = holder.layout.pageSlots(holder.page, maps.size());
        for (int offset = 0; offset < slots.size(); offset++)
            holder.inventory.setItem(
                    slots.get(offset),
                    mapItem(holder.viewer, holder.game, maps.get(from + offset)));
        holder.footer(maps.size(), true, "detail", true);
    }

    private ItemStack playerSummary(UUID viewer, DailyStatSnapshot stat) {
        return configured(
                "summary",
                null,
                Map.of("games", stat.gamesPlayed()),
                ink.ziip.championshipscore.api.gui.GuiMenu.playerHead(
                        viewer,
                        GuiConfig.component(MENU_PATH + ".items.summary.title"),
                        summaryLore(stat.gamesPlayed())));
    }

    /**
     * Game card: this game's records across all maps (three rows for timed metrics), plus games
     * played.
     */
    private ItemStack gameItem(UUID viewer, GameTypeEnum game) {
        DailyStatSnapshot stat = daily.statsManager().stat(viewer, game);
        List<Component> lore = new ArrayList<>();
        boolean recorded = stat.gamesPlayed() > 0;
        for (DailyMetric metric : DailyMetric.forGame(game)) {
            List<Double> values = daily.statsManager().metricValues(viewer, null, metric);
            if (values.isEmpty()) {
                lore.addAll(
                        metricLines(
                                DailyStatsMenu.metricRecordLabel(metric, 0),
                                GuiConfig.text(
                                        MENU_PATH + ".items.metric.states.no-record.title")));
            } else {
                recorded = true;
                for (int index = 0; index < values.size(); index++) {
                    lore.addAll(
                            metricLines(
                                    DailyStatsMenu.metricRecordLabel(metric, index),
                                    daily.statsManager()
                                            .formatMetricValue(
                                                    viewer, null, metric, values.get(index))));
                }
            }
        }
        return DailyRecordItem.render(
                MENU_PATH + ".items.game",
                game,
                game.toString(),
                Map.of("games", stat.gamesPlayed(), "maps", daily.knownMaps(game).size()),
                lore,
                recorded);
    }

    private ItemStack gameSummary(GameTypeEnum game, DailyStatSnapshot stat) {
        return DailyRecordItem.render(
                MENU_PATH + ".items.game-summary",
                game,
                game.toString(),
                Map.of("games", stat.gamesPlayed(), "maps", daily.knownMaps(game).size()),
                List.of(),
                stat.gamesPlayed() > 0);
    }

    /** Map item: the viewer's records on this one map (three rows for timed metrics). */
    private ItemStack mapItem(UUID viewer, GameTypeEnum game, String map) {
        List<Component> lore = new ArrayList<>();
        DailyMapStat stat = daily.statsManager().mapStat(viewer, game, map);
        boolean recorded = stat.gamesPlayed() > 0;
        for (DailyMetric metric : DailyMetric.forGame(game)) {
            List<Double> values = daily.statsManager().metricValues(viewer, map, metric);
            if (values.isEmpty()) {
                lore.addAll(
                        metricLines(
                                DailyStatsMenu.metricRecordLabel(metric, 0),
                                GuiConfig.text(
                                        MENU_PATH + ".items.metric.states.no-record.title")));
            } else {
                recorded = true;
                for (int index = 0; index < values.size(); index++) {
                    lore.addAll(
                            metricLines(
                                    DailyStatsMenu.metricRecordLabel(metric, index),
                                    daily.statsManager()
                                            .formatMetricValue(
                                                    viewer, map, metric, values.get(index))));
                }
            }
        }
        return DailyRecordItem.render(
                MENU_PATH + ".items.map",
                game,
                map,
                Map.of("map", map, "games", stat.gamesPlayed()),
                lore,
                recorded);
    }

    static List<Component> summaryLore(long gamesPlayed) {
        return GuiConfig.lines(MENU_PATH + ".items.summary.lore", Map.of("games", gamesPlayed))
                .stream()
                .map(LegacyText::component)
                .toList();
    }

    static String metricRecordLabel(DailyMetric metric, int index) {
        if (metric.format() != DailyMetric.Format.TIME)
            return GuiConfig.text(metric.labelKey() + ".title");
        return switch (index) {
            case 0 -> GuiConfig.text(metric.labelKey(1) + ".title");
            case 1 -> GuiConfig.text(metric.labelKey(2) + ".title");
            default -> GuiConfig.text(metric.labelKey(3) + ".title");
        };
    }

    private static List<Component> metricLines(String label, String value) {
        return GuiConfig.lines(
                        MENU_PATH + ".items.metric.lore", Map.of("label", label, "value", value))
                .stream()
                .map(LegacyText::component)
                .toList();
    }

    /** Apply configured presentation while retaining the player-head profile. */
    private static ItemStack configured(
            String key, String state, Map<String, ?> placeholders, ItemStack fallback) {
        return ConfiguredGui.item(MENU_PATH + ".items." + key, state, placeholders, fallback);
    }

    private static void clickSound(Player player, float pitch) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7F, pitch);
    }

    static final class StatsHolder extends DailyMenuPage {
        private StatsHolder(UUID viewer) {
            super(viewer, MenuId.DAILY_STATISTICS_OVERVIEW, false, false, 0);
        }
    }

    static final class DetailHolder extends DailyMenuPage {
        private final GameTypeEnum game;

        private DetailHolder(UUID viewer, GameTypeEnum game, int returnPage) {
            super(viewer, MenuId.DAILY_STATISTICS_DETAIL, false, true, returnPage);
            this.game = game;
        }
    }
}
