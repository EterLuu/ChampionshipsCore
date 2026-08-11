package ink.ziip.championshipscore.api.daily;

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
                Component.text("个人战绩", NamedTextColor.DARK_AQUA).decorate(TextDecoration.BOLD));
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
                Component.text("个人战绩 · ", NamedTextColor.DARK_AQUA)
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
                Component.text("暂无开放游戏", NamedTextColor.GRAY).decorate(TextDecoration.BOLD),
                List.of(Component.text("完成的记录会一直保留", NamedTextColor.DARK_GRAY)), false));

        inventory.setItem(BACK_SLOT, item(Material.ARROW,
                Component.text("返回大厅", NamedTextColor.WHITE).decorate(TextDecoration.BOLD), List.of(), false));
        inventory.setItem(LEADERBOARD_SLOT, item(Material.GOLD_INGOT,
                Component.text("查看排行榜", NamedTextColor.GOLD).decorate(TextDecoration.BOLD),
                List.of(Component.text("查看胜场与个人纪录排名", NamedTextColor.GRAY),
                        Component.text("点击打开", NamedTextColor.GREEN).decorate(TextDecoration.BOLD)), false));
        inventory.setItem(CLOSE_SLOT, item(Material.BARRIER, Component.text("关闭", NamedTextColor.RED), List.of(), false));
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
            inventory.setItem(20, metricItem(Material.GOLDEN_SWORD, "胜场", stat.wins() + " 场",
                    "赢得的 Bingo 场次", NamedTextColor.GREEN, stat.wins() > 0));
            inventory.setItem(22, metricItem(Material.PAINTING, "连线数", stat.lineCount() + " 条",
                    "所有 Bingo 对局累计完成的连线", NamedTextColor.LIGHT_PURPLE, stat.lineCount() > 0));
            inventory.setItem(24, metricItem(Material.FILLED_MAP, "完成任务总数", stat.completedTasks() + " 个",
                    "所有 Bingo 对局累计完成的任务", NamedTextColor.AQUA, stat.completedTasks() > 0));
            inventory.setItem(31, metricItem(Material.CLOCK, "单场最多完成", stat.maxCompletedTasks() + " 个",
                    "一场 Bingo 中完成任务数的最高纪录", NamedTextColor.GOLD, stat.maxCompletedTasks() > 0));
        } else {
            int from = holder.page * DETAIL_SLOTS.size();
            int to = Math.min(maps.size(), from + DETAIL_SLOTS.size());
            for (int index = from; index < to; index++) {
                inventory.setItem(DETAIL_SLOTS.get(index - from), mapItem(holder.viewer, holder.game, maps.get(index)));
            }
            if (maps.isEmpty()) inventory.setItem(31, item(Material.GRAY_DYE,
                    Component.text("暂无细分纪录", NamedTextColor.GRAY).decorate(TextDecoration.BOLD),
                    List.of(Component.text("完成对应游戏后，地图纪录会显示在这里", NamedTextColor.DARK_GRAY)), false));
        }

        inventory.setItem(BACK_SLOT, item(Material.ARROW,
                Component.text("返回游戏分类", NamedTextColor.WHITE).decorate(TextDecoration.BOLD), List.of(), false));
        inventory.setItem(LEADERBOARD_SLOT, item(Material.GOLD_INGOT,
                Component.text("查看排行榜", NamedTextColor.GOLD).decorate(TextDecoration.BOLD),
                List.of(Component.text("查看胜场与个人纪录排名", NamedTextColor.GRAY)), false));
        if (holder.page > 0) inventory.setItem(48, item(Material.ARROW,
                Component.text("上一页", NamedTextColor.WHITE), List.of(), false));
        inventory.setItem(REFRESH_SLOT, item(Material.CLOCK,
                Component.text("刷新", NamedTextColor.YELLOW).decorate(TextDecoration.BOLD),
                List.of(Component.text("更新当前游戏的详细纪录", NamedTextColor.GRAY)), false));
        if (holder.game == GameTypeEnum.Bingo) {
            inventory.setItem(PAGE_SLOT, item(Material.PAPER,
                    Component.text("全部地图累计", NamedTextColor.AQUA).decorate(TextDecoration.BOLD),
                    List.of(Component.text("Bingo 数据按游戏累计，不按地图拆分", NamedTextColor.GRAY)), false));
        } else {
            inventory.setItem(PAGE_SLOT, item(Material.PAPER,
                    Component.text("第 " + (holder.page + 1) + " / " + holder.pageCount + " 页", NamedTextColor.AQUA)
                            .decorate(TextDecoration.BOLD),
                    List.of(Component.text(maps.size() + " 张地图", NamedTextColor.GRAY)), false));
            if (holder.page + 1 < holder.pageCount) inventory.setItem(52, item(Material.ARROW,
                    Component.text("下一页", NamedTextColor.WHITE), List.of(), false));
        }
        inventory.setItem(CLOSE_SLOT, item(Material.BARRIER, Component.text("关闭", NamedTextColor.RED), List.of(), false));
    }

    private ItemStack playerSummary(UUID viewer, DailyStatSnapshot stat) {
        List<Component> lore = List.of(
                line("完成场次  ", stat.gamesPlayed() + " 场", NamedTextColor.WHITE),
                line("获胜场次  ", stat.wins() + " 场", NamedTextColor.GREEN),
                line("总胜率  ", winRate(stat), NamedTextColor.YELLOW),
                Component.empty(),
                Component.text("按游戏分类，点击卡片查看细分", NamedTextColor.GRAY)
        );
        ItemStack stack = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta rawMeta = stack.getItemMeta();
        if (!(rawMeta instanceof SkullMeta meta)) return item(Material.PLAYER_HEAD,
                Component.text("我的总览", NamedTextColor.AQUA).decorate(TextDecoration.BOLD), lore, false);
        meta.setOwningPlayer(Bukkit.getOfflinePlayer(viewer));
        meta.displayName(Component.text("我的总览", NamedTextColor.AQUA).decorate(TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore.stream().map(line -> line.decoration(TextDecoration.ITALIC, false)).toList());
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack gameItem(UUID viewer, GameTypeEnum game) {
        DailyStatSnapshot stat = daily.statsManager().stat(viewer, game);
        List<Component> lore = new ArrayList<>();
        if (game == GameTypeEnum.Bingo) {
            lore.add(line("胜场  ", stat.wins() + " 场", NamedTextColor.GREEN));
            lore.add(line("连线数  ", stat.lineCount() + " 条", NamedTextColor.LIGHT_PURPLE));
            lore.add(line("完成任务  ", stat.completedTasks() + " 个", NamedTextColor.AQUA));
            lore.add(line("单场最多  ", stat.maxCompletedTasks() + " 个", NamedTextColor.GOLD));
        } else {
            lore.add(line("完成场次  ", stat.gamesPlayed() + " 场", NamedTextColor.WHITE));
            lore.add(line("获胜场次  ", stat.wins() + " 场", NamedTextColor.GREEN));
            lore.add(line("胜率  ", winRate(stat), NamedTextColor.YELLOW));
        }
        lore.add(Component.empty());
        lore.add(Component.text(game == GameTypeEnum.Bingo
                ? "点击查看累计细分数据"
                : daily.knownMaps(game).size() + " 张地图可查看细分", NamedTextColor.GRAY));
        lore.add(Component.empty());
        lore.add(Component.text("点击查看详细战绩", NamedTextColor.GREEN).decorate(TextDecoration.BOLD));
        return item(gameMaterial(game), Component.text(game.toString(), gameColor(game))
                .decorate(TextDecoration.BOLD), lore, stat.gamesPlayed() > 0);
    }

    private ItemStack gameSummary(GameTypeEnum game, DailyStatSnapshot stat) {
        List<Component> lore = new ArrayList<>();
        lore.add(line("参与场次  ", stat.gamesPlayed() + " 场", NamedTextColor.WHITE));
        lore.add(line("获胜场次  ", stat.wins() + " 场", NamedTextColor.GREEN));
        lore.add(line("胜率  ", winRate(stat), NamedTextColor.YELLOW));
        if (game == GameTypeEnum.Bingo) {
            lore.add(Component.empty());
            lore.add(Component.text("下方查看 Bingo 累计细分数据", NamedTextColor.GRAY));
        } else {
            lore.add(Component.empty());
            lore.add(Component.text("下方按地图查看各项纪录", NamedTextColor.GRAY));
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
            lore.add(line(definition.label() + "  ", value < 0 ? "暂无纪录" : DailyLeaderboardMenu.formatDuration(value),
                    value < 0 ? NamedTextColor.DARK_GRAY : definition.color()));
        }
        lore.add(Component.empty());
        lore.add(Component.text("按地图独立记录", NamedTextColor.GRAY));
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
                new RecordDefinition(DailyRecordType.ACERACE_FASTEST_LAP, "最快单圈", NamedTextColor.AQUA),
                new RecordDefinition(DailyRecordType.ACERACE_FASTEST_THREE_LAPS, "最快完整三圈", NamedTextColor.GOLD)));
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
