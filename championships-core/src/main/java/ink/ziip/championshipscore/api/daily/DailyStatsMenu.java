package ink.ziip.championshipscore.api.daily;

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
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Categorized personal records: game cards first, map-level details on the second screen. */
final class DailyStatsMenu {
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
    private static final List<Integer> DETAIL_SLOTS = createDetailSlots();
    private static final Map<GameTypeEnum, List<RecordDefinition>> RECORDS = createRecordDefinitions();

    private final DailyManager daily;

    DailyStatsMenu(DailyManager daily) {
        this.daily = daily;
    }

    void open(Player player) {
        StatsHolder holder = new StatsHolder(player.getUniqueId());
        holder.inventory = Bukkit.createInventory(holder, SIZE,
                Component.text(GuiConfig.text("daily.menus.statistics.personal-record"), NamedTextColor.DARK_AQUA).decorate(TextDecoration.BOLD));
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
        } else if (slot == PAGE_SLOT) {
            clickSound(player, 0.9F);
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
                Component.text(GuiConfig.text("daily.menus.statistics.statistics-header-prefix"), NamedTextColor.DARK_AQUA)
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
                Component.text(GuiConfig.text("daily.menus.statistics.no-open-games-yet"), NamedTextColor.GRAY).decorate(TextDecoration.BOLD),
                List.of(Component.text(GuiConfig.text("daily.menus.statistics.completed-records-will-be-retained-forever"), NamedTextColor.DARK_GRAY)), false));

        inventory.setItem(BACK_SLOT, item(Material.ARROW,
                Component.text(GuiConfig.text("daily.menus.statistics.return-to-lobby"), NamedTextColor.WHITE).decorate(TextDecoration.BOLD), List.of(), false));
        inventory.setItem(LEADERBOARD_SLOT, item(Material.GOLD_INGOT,
                Component.text(GuiConfig.text("daily.menus.statistics.view-the-leaderboard"), NamedTextColor.GOLD).decorate(TextDecoration.BOLD),
                List.of(Component.text(GuiConfig.text("daily.menus.statistics.view-win-and-personal-record-rankings"), NamedTextColor.GRAY),
                        Component.text(GuiConfig.text("daily.menus.statistics.click-to-open"), NamedTextColor.GREEN).decorate(TextDecoration.BOLD)), false));
        inventory.setItem(CLOSE_SLOT, item(Material.BARRIER, Component.text(GuiConfig.text("daily.menus.statistics.close"), NamedTextColor.RED), List.of(), false));
    }

    private void refresh(DetailHolder holder) {
        Inventory inventory = holder.inventory;
        inventory.clear();
        drawBorder(inventory);

        DailyStatSnapshot stat = daily.statsManager().stat(holder.viewer, holder.game);
        inventory.setItem(SUMMARY_SLOT, gameSummary(holder.game, stat));
        List<String> maps = holder.game == GameTypeEnum.Bingo ? List.of()
                : daily.knownMaps(holder.game).stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
        holder.pageCount = Math.max(1, (maps.size() + DETAIL_SLOTS.size() - 1) / DETAIL_SLOTS.size());
        holder.page = Math.max(0, Math.min(holder.page, holder.pageCount - 1));

        if (holder.game == GameTypeEnum.Bingo) {
            inventory.setItem(20, metricItem(Material.GOLDEN_SWORD, GuiConfig.text("daily.menus.statistics.victory"), stat.wins() + GuiConfig.text("daily.menus.statistics.match-count-suffix"),
                    GuiConfig.text("daily.menus.statistics.bingo-sessions-won"), NamedTextColor.GREEN, stat.wins() > 0));
            inventory.setItem(22, metricItem(Material.PAINTING, GuiConfig.text("daily.menus.statistics.number-of-connections"), stat.lineCount() + GuiConfig.text("daily.menus.statistics.record-count-suffix"),
                    GuiConfig.text("daily.menus.statistics.cumulative-completed-connections-across-all-bingo-games"), NamedTextColor.LIGHT_PURPLE, stat.lineCount() > 0));
            inventory.setItem(24, metricItem(Material.FILLED_MAP, GuiConfig.text("daily.menus.statistics.total-tasks-completed"), stat.completedTasks() + GuiConfig.text("daily.menus.statistics.item-count-suffix"),
                    GuiConfig.text("daily.menus.statistics.cumulative-completed-tasks-for-all-bingo-games"), NamedTextColor.AQUA, stat.completedTasks() > 0));
            inventory.setItem(31, metricItem(Material.CLOCK, GuiConfig.text("daily.menus.statistics.most-completed-in-a-single-game"), stat.maxCompletedTasks() + GuiConfig.text("daily.menus.statistics.item-count-suffix"),
                    GuiConfig.text("daily.menus.statistics.highest-number-of-tasks-completed-in-a-bingo-game"), NamedTextColor.GOLD, stat.maxCompletedTasks() > 0));
        } else {
            int from = holder.page * DETAIL_SLOTS.size();
            int to = Math.min(maps.size(), from + DETAIL_SLOTS.size());
            for (int index = from; index < to; index++) {
                inventory.setItem(DETAIL_SLOTS.get(index - from), mapItem(holder.viewer, holder.game, maps.get(index)));
            }
            if (maps.isEmpty()) inventory.setItem(31, item(Material.GRAY_DYE,
                    Component.text(GuiConfig.text("daily.menus.statistics.no-subdivision-records-yet"), NamedTextColor.GRAY).decorate(TextDecoration.BOLD),
                    List.of(Component.text(GuiConfig.text("daily.menus.statistics.after-completing-the-corresponding-game-the-map-record-will-be-displayed-here"), NamedTextColor.DARK_GRAY)), false));
        }

        inventory.setItem(BACK_SLOT, item(Material.ARROW,
                Component.text(GuiConfig.text("daily.menus.statistics.return-to-game-category"), NamedTextColor.WHITE).decorate(TextDecoration.BOLD), List.of(), false));
        inventory.setItem(LEADERBOARD_SLOT, item(Material.GOLD_INGOT,
                Component.text(GuiConfig.text("daily.menus.statistics.view-the-leaderboard"), NamedTextColor.GOLD).decorate(TextDecoration.BOLD),
                List.of(Component.text(GuiConfig.text("daily.menus.statistics.view-win-and-personal-record-rankings"), NamedTextColor.GRAY)), false));
        if (holder.page > 0) inventory.setItem(48, item(Material.ARROW,
                Component.text(GuiConfig.text("daily.menus.statistics.previous-page"), NamedTextColor.WHITE), List.of(), false));
        inventory.setItem(REFRESH_SLOT, item(Material.CLOCK,
                Component.text(GuiConfig.text("daily.menus.statistics.refresh"), NamedTextColor.YELLOW).decorate(TextDecoration.BOLD),
                List.of(Component.text(GuiConfig.text("daily.menus.statistics.update-the-detailed-records-of-the-current-game"), NamedTextColor.GRAY)), false));
        if (holder.game == GameTypeEnum.Bingo) {
            inventory.setItem(PAGE_SLOT, item(Material.PAPER,
                    Component.text(GuiConfig.text("daily.menus.statistics.all-maps-accumulated"), NamedTextColor.AQUA).decorate(TextDecoration.BOLD),
                    List.of(Component.text(GuiConfig.text("daily.menus.statistics.bingo-data-accumulated-by-game-not-split-by-map"), NamedTextColor.GRAY)), false));
        } else {
            inventory.setItem(PAGE_SLOT, item(Material.PAPER,
                    Component.text(GuiConfig.text("daily.menus.statistics.ordinal-prefix") + (holder.page + 1) + " / " + holder.pageCount + GuiConfig.text("daily.menus.statistics.page-suffix"), NamedTextColor.AQUA)
                            .decorate(TextDecoration.BOLD),
                    List.of(Component.text(maps.size() + GuiConfig.text("daily.menus.statistics.map"), NamedTextColor.GRAY)), false));
            if (holder.page + 1 < holder.pageCount) inventory.setItem(52, item(Material.ARROW,
                    Component.text(GuiConfig.text("daily.menus.statistics.next-page"), NamedTextColor.WHITE), List.of(), false));
        }
        inventory.setItem(CLOSE_SLOT, item(Material.BARRIER, Component.text(GuiConfig.text("daily.menus.statistics.close"), NamedTextColor.RED), List.of(), false));
    }

    private ItemStack playerSummary(UUID viewer, DailyStatSnapshot stat) {
        List<Component> lore = List.of(
                line(GuiConfig.text("daily.menus.statistics.complete-session"), stat.gamesPlayed() + GuiConfig.text("daily.menus.statistics.match-count-suffix"), NamedTextColor.WHITE),
                line(GuiConfig.text("daily.menus.statistics.wins"), stat.wins() + GuiConfig.text("daily.menus.statistics.match-count-suffix"), NamedTextColor.GREEN),
                line(GuiConfig.text("daily.menus.statistics.overall-winning-rate"), winRate(stat), NamedTextColor.YELLOW),
                Component.empty(),
                Component.text(GuiConfig.text("daily.menus.statistics.sort-by-game-click-on-card-to-see-breakdown"), NamedTextColor.GRAY)
        );
        ItemStack stack = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta rawMeta = stack.getItemMeta();
        if (!(rawMeta instanceof SkullMeta meta)) return item(Material.PLAYER_HEAD,
                Component.text(GuiConfig.text("daily.menus.statistics.my-overview"), NamedTextColor.AQUA).decorate(TextDecoration.BOLD), lore, false);
        meta.setOwningPlayer(Bukkit.getOfflinePlayer(viewer));
        meta.displayName(Component.text(GuiConfig.text("daily.menus.statistics.my-overview"), NamedTextColor.AQUA).decorate(TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore.stream().map(line -> line.decoration(TextDecoration.ITALIC, false)).toList());
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack gameItem(UUID viewer, GameTypeEnum game) {
        DailyStatSnapshot stat = daily.statsManager().stat(viewer, game);
        List<Component> lore = new ArrayList<>();
        if (game == GameTypeEnum.Bingo) {
            lore.add(line(GuiConfig.text("daily.menus.statistics.wins-label"), stat.wins() + GuiConfig.text("daily.menus.statistics.match-count-suffix"), NamedTextColor.GREEN));
            lore.add(line(GuiConfig.text("daily.menus.statistics.lines-completed-label"), stat.lineCount() + GuiConfig.text("daily.menus.statistics.record-count-suffix"), NamedTextColor.LIGHT_PURPLE));
            lore.add(line(GuiConfig.text("daily.menus.statistics.complete-the-task"), stat.completedTasks() + GuiConfig.text("daily.menus.statistics.item-count-suffix"), NamedTextColor.AQUA));
            lore.add(line(GuiConfig.text("daily.menus.statistics.most-in-a-single-game"), stat.maxCompletedTasks() + GuiConfig.text("daily.menus.statistics.item-count-suffix"), NamedTextColor.GOLD));
        } else {
            lore.add(line(GuiConfig.text("daily.menus.statistics.complete-session"), stat.gamesPlayed() + GuiConfig.text("daily.menus.statistics.match-count-suffix"), NamedTextColor.WHITE));
            lore.add(line(GuiConfig.text("daily.menus.statistics.wins"), stat.wins() + GuiConfig.text("daily.menus.statistics.match-count-suffix"), NamedTextColor.GREEN));
            lore.add(line(GuiConfig.text("daily.menus.statistics.winning-rate"), winRate(stat), NamedTextColor.YELLOW));
        }
        lore.add(Component.empty());
        lore.add(Component.text(game == GameTypeEnum.Bingo
                ? GuiConfig.text("daily.menus.statistics.click-to-view-cumulative-breakdown-data")
                : daily.knownMaps(game).size() + GuiConfig.text("daily.menus.statistics.maps-to-view-subdivisions"), NamedTextColor.GRAY));
        lore.add(Component.empty());
        lore.add(Component.text(GuiConfig.text("daily.menus.statistics.click-to-view-detailed-results"), NamedTextColor.GREEN).decorate(TextDecoration.BOLD));
        return item(gameMaterial(game), Component.text(game.toString(), gameColor(game))
                .decorate(TextDecoration.BOLD), lore, stat.gamesPlayed() > 0);
    }

    private ItemStack gameSummary(GameTypeEnum game, DailyStatSnapshot stat) {
        List<Component> lore = new ArrayList<>();
        lore.add(line(GuiConfig.text("daily.menus.statistics.participate-in-sessions"), stat.gamesPlayed() + GuiConfig.text("daily.menus.statistics.match-count-suffix"), NamedTextColor.WHITE));
        lore.add(line(GuiConfig.text("daily.menus.statistics.wins"), stat.wins() + GuiConfig.text("daily.menus.statistics.match-count-suffix"), NamedTextColor.GREEN));
        lore.add(line(GuiConfig.text("daily.menus.statistics.winning-rate"), winRate(stat), NamedTextColor.YELLOW));
        if (game == GameTypeEnum.Bingo) {
            lore.add(Component.empty());
            lore.add(Component.text(GuiConfig.text("daily.menus.statistics.view-the-cumulative-breakdown-of-bingo-data-below"), NamedTextColor.GRAY));
        } else {
            lore.add(Component.empty());
            lore.add(Component.text(GuiConfig.text("daily.menus.statistics.click-the-map-below-to-view-various-records"), NamedTextColor.GRAY));
        }
        return item(gameMaterial(game), Component.text(game.toString(), gameColor(game))
                .decorate(TextDecoration.BOLD), lore, stat.gamesPlayed() > 0);
    }

    private ItemStack metricItem(Material material, String title, String value, String description,
                                 NamedTextColor color, boolean glint) {
        return item(material, Component.text(title, color).decorate(TextDecoration.BOLD),
                List.of(Component.text(value, NamedTextColor.WHITE).decorate(TextDecoration.BOLD),
                        Component.empty(), Component.text(description, NamedTextColor.GRAY)), glint);
    }

    private ItemStack mapItem(UUID viewer, GameTypeEnum game, String map) {
        List<Component> lore = new ArrayList<>();
        boolean recorded = false;
        for (RecordDefinition definition : RECORDS.getOrDefault(game, List.of())) {
            long value = daily.statsManager().bestRecord(viewer, game, map, definition.type());
            if (value >= 0) recorded = true;
            lore.add(line(definition.label() + "  ", value < 0 ? GuiConfig.text("daily.menus.statistics.no-record-yet") : DailyLeaderboardMenu.formatDuration(value),
                    value < 0 ? NamedTextColor.DARK_GRAY : definition.color()));
        }
        lore.add(Component.empty());
        lore.add(Component.text(GuiConfig.text("daily.menus.statistics.record-independently-by-map"), NamedTextColor.GRAY));
        return item(gameMaterial(game), Component.text(map, gameColor(game)).decorate(TextDecoration.BOLD), lore, recorded);
    }

    private static List<Integer> gameSlots(int count) {
        if (count <= 1) return GAME_SLOTS_ONE;
        if (count == 2) return GAME_SLOTS_TWO;
        if (count == 3) return GAME_SLOTS_THREE;
        return GAME_SLOTS_MANY;
    }

    private static List<Integer> createDetailSlots() {
        List<Integer> slots = new ArrayList<>();
        for (int slot = 18; slot <= 44; slot++) slots.add(slot);
        return List.copyOf(slots);
    }

    private static Map<GameTypeEnum, List<RecordDefinition>> createRecordDefinitions() {
        Map<GameTypeEnum, List<RecordDefinition>> records = new EnumMap<>(GameTypeEnum.class);
        records.put(GameTypeEnum.AceRace, List.of(
                new RecordDefinition(DailyRecordType.ACERACE_FASTEST_LAP, GuiConfig.text("daily.menus.statistics.fastest-lap"), NamedTextColor.AQUA),
                new RecordDefinition(DailyRecordType.ACERACE_FASTEST_THREE_LAPS, GuiConfig.text("daily.menus.statistics.fastest-three-complete-laps"), NamedTextColor.GOLD)));
        return Map.copyOf(records);
    }

    private static void drawBorder(Inventory inventory) {
        ItemStack border = item(Material.BLACK_STAINED_GLASS_PANE, Component.text(" "), List.of(), false);
        for (int slot = 0; slot < 9; slot++) inventory.setItem(slot, border);
        for (int slot = 45; slot < SIZE; slot++) inventory.setItem(slot, border);
    }

    private static Material gameMaterial(GameTypeEnum game) {
        return game == GameTypeEnum.Bingo ? Material.FILLED_MAP
                : game == GameTypeEnum.AceRace ? Material.ELYTRA : Material.PAPER;
    }

    private static NamedTextColor gameColor(GameTypeEnum game) {
        return game == GameTypeEnum.Bingo ? NamedTextColor.LIGHT_PURPLE
                : game == GameTypeEnum.AceRace ? NamedTextColor.AQUA : NamedTextColor.WHITE;
    }

    private static Component line(String label, String value, NamedTextColor color) {
        return Component.text(label, NamedTextColor.GRAY).append(Component.text(value, color));
    }

    private static String winRate(DailyStatSnapshot stat) {
        return stat.gamesPlayed() == 0 ? "0%"
                : Math.round(stat.wins() * 100D / stat.gamesPlayed()) + "%";
    }

    private static ItemStack item(Material material, Component name, List<Component> lore, boolean glint) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(name.decoration(TextDecoration.ITALIC, false));
            meta.lore(lore.stream().map(line -> line.decoration(TextDecoration.ITALIC, false)).toList());
            meta.setEnchantmentGlintOverride(glint);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static void clickSound(Player player, float pitch) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7F, pitch);
    }

    private record RecordDefinition(DailyRecordType type, String label, NamedTextColor color) {}

    static final class StatsHolder implements InventoryHolder {
        private final UUID viewer;
        private final Map<Integer, GameTypeEnum> gamesBySlot = new java.util.HashMap<>();
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
