package ink.ziip.championshipscore.api.daily;

import ink.ziip.championshipscore.configuration.config.message.GuiConfig;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.util.Utils;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Rich cached leaderboards styled consistently with vote and spectate inventories. */
public final class DailyLeaderboardMenu {
    private static final int INVENTORY_SIZE = 54;
    private static final int PAGE_SIZE = 36;
    private static final int CATEGORY_PAGE_SIZE = 28;
    private static final int PLAYER_SLOT = 1;
    private static final int OVERVIEW_SLOT = 4;
    private static final int RECORD_SLOT = 7;
    private static final int BACK_SLOT = 45;
    private static final int PREVIOUS_SLOT = 48;
    private static final int REFRESH_SLOT = 49;
    private static final int PAGE_SLOT = 50;
    private static final int NEXT_SLOT = 52;
    private static final int CLOSE_SLOT = 53;

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
        holder.inventory = Bukkit.createInventory(holder, INVENTORY_SIZE,
                Utils.toComponent(MessageConfig.DAILY_MENU_LEADERBOARD_TITLE));
        refreshCategories(holder);
        player.openInventory(holder.inventory);
    }

    void refreshOpenMenus() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof LeaderboardHolder holder) {
                if (holder.boardId == null) refreshCategories(holder);
                else refreshBoard(holder);
            }
        }
    }

    private void openBoard(Player player, String boardId) {
        LeaderboardHolder holder = new LeaderboardHolder(player.getUniqueId(), boardId);
        Board board = findBoard(boardId);
        holder.inventory = Bukkit.createInventory(holder, INVENTORY_SIZE, Utils.toComponent(replace(
                MessageConfig.DAILY_MENU_BOARD_TITLE, "%board%", board.title())));
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
            if (holder.boardId == null) daily.openMenu(player);
            else open(player);
            clickSound(player, 1F);
            return;
        }
        if (slot == REFRESH_SLOT) {
            if (holder.boardId == null) refreshCategories(holder); else refreshBoard(holder);
            clickSound(player, 1.1F);
            return;
        }
        if (holder.boardId == null) {
            if (slot == PREVIOUS_SLOT && holder.page > 0) {
                holder.page--;
                refreshCategories(holder);
                clickSound(player, 1F);
                return;
            }
            if (slot == NEXT_SLOT && holder.page + 1 < holder.pageCount) {
                holder.page++;
                refreshCategories(holder);
                clickSound(player, 1F);
                return;
            }
            String board = holder.boardsBySlot.get(slot);
            if (board != null) {
                openBoard(player, board);
                clickSound(player, 1.2F);
            }
            return;
        }
        if (slot == PREVIOUS_SLOT && holder.page > 0) {
            holder.page--;
            refreshBoard(holder);
            clickSound(player, 1F);
        } else if (slot == NEXT_SLOT && holder.page + 1 < holder.pageCount) {
            holder.page++;
            refreshBoard(holder);
            clickSound(player, 1F);
        }
    }

    private void refreshCategories(LeaderboardHolder holder) {
        Inventory inventory = holder.inventory;
        inventory.clear();
        holder.boardsBySlot.clear();
        drawBorder(inventory);
        List<Board> boards = boards();
        holder.pageCount = Math.max(1, (boards.size() + CATEGORY_PAGE_SIZE - 1) / CATEGORY_PAGE_SIZE);
        holder.page = Math.max(0, Math.min(holder.page, holder.pageCount - 1));
        int totalRows = boards.stream().mapToInt(board -> stats.leaderboard(board.id()).size()).sum();
        inventory.setItem(PLAYER_SLOT, playerSummary(holder.viewer));
        inventory.setItem(OVERVIEW_SLOT, item(Material.NETHER_STAR,
                Component.text(GuiConfig.text("api-daily-dailyleaderboardmenu.text-001"), NamedTextColor.GOLD).decorate(TextDecoration.BOLD),
                List.of(
                        Component.text(boards.size() + GuiConfig.text("api-daily-dailyleaderboardmenu.text-002"), NamedTextColor.WHITE)
                                .append(Component.text(GuiConfig.text("common.separator") + totalRows + GuiConfig.text("api-daily-dailyleaderboardmenu.text-003"), NamedTextColor.GRAY)),
                        Component.text(GuiConfig.text("api-daily-dailyleaderboardmenu.text-004"), NamedTextColor.DARK_GRAY),
                        Component.text(GuiConfig.text("api-daily-dailyleaderboardmenu.text-005"), NamedTextColor.YELLOW)
                ), false));
        inventory.setItem(RECORD_SLOT, personalRecords(holder.viewer));

        int from = holder.page * CATEGORY_PAGE_SIZE;
        int to = Math.min(boards.size(), from + CATEGORY_PAGE_SIZE);
        List<Integer> slots = categorySlots(to - from);
        for (int index = from; index < to; index++) {
            Board board = boards.get(index);
            int slot = slots.get(index - from);
            inventory.setItem(slot, boardItem(board, holder.viewer));
            holder.boardsBySlot.put(slot, board.id());
        }
        inventory.setItem(BACK_SLOT, item(Material.ARROW,
                Component.text(GuiConfig.text("api-daily-dailyleaderboardmenu.text-006"), NamedTextColor.WHITE).decorate(TextDecoration.BOLD),
                List.of(Component.text(GuiConfig.text("api-daily-dailyleaderboardmenu.text-007"), NamedTextColor.DARK_GRAY)), false));
        if (holder.page > 0) inventory.setItem(PREVIOUS_SLOT, item(Material.ARROW,
                Component.text(GuiConfig.text("api-daily-dailyleaderboardmenu.text-008"), NamedTextColor.WHITE), List.of(), false));
        inventory.setItem(REFRESH_SLOT, refreshItem());
        inventory.setItem(PAGE_SLOT, item(Material.PAPER,
                Component.text(GuiConfig.text("api-daily-dailyleaderboardmenu.text-009") + (holder.page + 1) + "/" + holder.pageCount + GuiConfig.text("api-daily-dailyleaderboardmenu.text-010"), NamedTextColor.AQUA)
                        .decorate(TextDecoration.BOLD),
                List.of(Component.text(boards.size() + GuiConfig.text("api-daily-dailyleaderboardmenu.text-002"), NamedTextColor.GRAY)), false));
        if (holder.page + 1 < holder.pageCount) inventory.setItem(NEXT_SLOT, item(Material.ARROW,
                Component.text(GuiConfig.text("api-daily-dailyleaderboardmenu.text-011"), NamedTextColor.WHITE), List.of(), false));
        inventory.setItem(CLOSE_SLOT, closeItem());
    }

    private void refreshBoard(LeaderboardHolder holder) {
        Inventory inventory = holder.inventory;
        inventory.clear();
        drawBorder(inventory);
        Board board = findBoard(holder.boardId);
        List<DailyLeaderboardEntry> entries = stats.leaderboard(board.id());
        holder.pageCount = Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        holder.page = Math.max(0, Math.min(holder.page, holder.pageCount - 1));

        inventory.setItem(OVERVIEW_SLOT, boardOverview(board, entries, holder.viewer));
        int from = holder.page * PAGE_SIZE;
        int to = Math.min(entries.size(), from + PAGE_SIZE);
        for (int index = from; index < to; index++) {
            int slot = 9 + index - from;
            inventory.setItem(slot, rowItem(entries.get(index), index + 1, holder.viewer));
        }
        if (entries.isEmpty()) inventory.setItem(22, item(Material.GRAY_DYE,
                Component.text(GuiConfig.text("api-daily-dailyleaderboardmenu.text-012"), NamedTextColor.GRAY).decorate(TextDecoration.BOLD),
                List.of(
                        Component.text(GuiConfig.text("api-daily-dailyleaderboardmenu.text-013"), NamedTextColor.DARK_GRAY),
                        Component.text(GuiConfig.text("api-daily-dailyleaderboardmenu.text-014"), NamedTextColor.YELLOW)
                ), false));

        inventory.setItem(BACK_SLOT, item(Material.ARROW,
                Component.text(GuiConfig.text("api-daily-dailyleaderboardmenu.text-015"), NamedTextColor.WHITE).decorate(TextDecoration.BOLD),
                List.of(Component.text(board.title(), NamedTextColor.DARK_GRAY)), false));
        if (holder.page > 0) inventory.setItem(PREVIOUS_SLOT, item(Material.ARROW,
                Component.text(GuiConfig.text("api-daily-dailyleaderboardmenu.text-008"), NamedTextColor.WHITE), List.of(), false));
        inventory.setItem(REFRESH_SLOT, refreshItem());
        inventory.setItem(PAGE_SLOT, item(Material.PAPER,
                Component.text(GuiConfig.text("api-daily-dailyleaderboardmenu.text-009") + (holder.page + 1) + "/" + holder.pageCount + GuiConfig.text("api-daily-dailyleaderboardmenu.text-010"), NamedTextColor.AQUA)
                        .decorate(TextDecoration.BOLD),
                List.of(Component.text(entries.size() + GuiConfig.text("api-daily-dailyleaderboardmenu.text-016"), NamedTextColor.GRAY)), false));
        if (holder.page + 1 < holder.pageCount) inventory.setItem(NEXT_SLOT, item(Material.ARROW,
                Component.text(GuiConfig.text("api-daily-dailyleaderboardmenu.text-011"), NamedTextColor.WHITE), List.of(), false));
        inventory.setItem(CLOSE_SLOT, closeItem());
    }

    private ItemStack playerSummary(UUID viewer) {
        DailyStatSnapshot stat = stats.stat(viewer, null);
        int position = position(stats.leaderboard("wins"), viewer);
        return playerHead(viewer,
                Component.text(GuiConfig.text("api-daily-dailyleaderboardmenu.text-017"), NamedTextColor.AQUA).decorate(TextDecoration.BOLD),
                List.of(
                        Component.text(GuiConfig.text("api-daily-dailyleaderboardmenu.text-018"), NamedTextColor.GRAY)
                                .append(Component.text(position < 0 ? GuiConfig.text("api-daily-dailyleaderboardmenu.text-019") : "#" + position,
                                        position < 0 ? NamedTextColor.DARK_GRAY : NamedTextColor.GOLD)),
                        Component.text(GuiConfig.text("api-daily-dailyleaderboardmenu.text-020"), NamedTextColor.GRAY).append(Component.text(stat.gamesPlayed() + GuiConfig.text("api-daily-dailyleaderboardmenu.text-021") + stat.wins() + GuiConfig.text("api-daily-dailyleaderboardmenu.text-022"), NamedTextColor.GREEN)),
                        Component.text(GuiConfig.text("api-daily-dailyleaderboardmenu.text-023"), NamedTextColor.GRAY).append(Component.text(winRate(stat), NamedTextColor.YELLOW)),
                        Component.empty(),
                        Component.text(GuiConfig.text("api-daily-dailyleaderboardmenu.text-024"), NamedTextColor.DARK_GRAY)
                ), position > 0);
    }

    private ItemStack personalRecords(UUID viewer) {
        List<Component> lore = new ArrayList<>();
        appendPersonalRecords(lore, viewer, GameTypeEnum.Bingo, DailyRecordType.BINGO_FIRST_LINE, GuiConfig.text("api-daily-dailyleaderboardmenu.text-025"));
        appendPersonalRecords(lore, viewer, GameTypeEnum.Bingo, DailyRecordType.BINGO_FULL_CARD, GuiConfig.text("api-daily-dailyleaderboardmenu.text-026"));
        appendPersonalRecords(lore, viewer, GameTypeEnum.AceRace, DailyRecordType.ACERACE_FASTEST_LAP, GuiConfig.text("api-daily-dailyleaderboardmenu.text-027"));
        appendPersonalRecords(lore, viewer, GameTypeEnum.AceRace,
                DailyRecordType.ACERACE_FASTEST_THREE_LAPS, GuiConfig.text("api-daily-dailyleaderboardmenu.text-028"));
        if (lore.isEmpty()) lore.add(Component.text(GuiConfig.text("api-daily-dailyleaderboardmenu.text-029"), NamedTextColor.DARK_GRAY));
        lore.add(Component.empty());
        lore.add(Component.text(GuiConfig.text("api-daily-dailyleaderboardmenu.text-030"), NamedTextColor.YELLOW));
        return item(Material.CLOCK,
                Component.text(GuiConfig.text("api-daily-dailyleaderboardmenu.text-031"), NamedTextColor.LIGHT_PURPLE).decorate(TextDecoration.BOLD), lore, false);
    }

    private void appendPersonalRecords(List<Component> lore, UUID viewer, GameTypeEnum game,
                                       DailyRecordType type, String label) {
        for (String map : knownMaps(game)) {
            long record = stats.bestRecord(viewer, game, map, type);
            if (record < 0) continue;
            lore.add(Component.text(label, NamedTextColor.GRAY)
                    .append(Component.text(formatDuration(record), NamedTextColor.GOLD))
                    .append(Component.text(GuiConfig.text("common.separator") + map, NamedTextColor.DARK_GRAY)));
        }
    }

    private ItemStack boardItem(Board board, UUID viewer) {
        List<DailyLeaderboardEntry> entries = stats.leaderboard(board.id());
        int viewerPosition = position(entries, viewer);
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(board.category(), NamedTextColor.DARK_GRAY));
        lore.add(Component.text(board.description(), NamedTextColor.GRAY));
        lore.add(Component.empty());
        if (entries.isEmpty()) {
            lore.add(Component.text(GuiConfig.text("api-daily-dailyleaderboardmenu.text-012"), NamedTextColor.DARK_GRAY));
        } else {
            lore.add(Component.text(GuiConfig.text("api-daily-dailyleaderboardmenu.text-032"), NamedTextColor.GRAY)
                    .append(Component.text(entries.getFirst().name(), NamedTextColor.GOLD))
                    .append(Component.text(GuiConfig.text("common.separator") + value(entries.getFirst()), NamedTextColor.WHITE)));
            for (int index = 1; index < Math.min(3, entries.size()); index++) {
                DailyLeaderboardEntry entry = entries.get(index);
                lore.add(Component.text("#" + (index + 1) + "  ", NamedTextColor.DARK_GRAY)
                        .append(Component.text(entry.name(), NamedTextColor.WHITE))
                        .append(Component.text(GuiConfig.text("common.separator") + value(entry), NamedTextColor.GRAY)));
            }
        }
        lore.add(Component.empty());
        lore.add(Component.text(viewerPosition < 0 ? GuiConfig.text("api-daily-dailyleaderboardmenu.text-033") : GuiConfig.text("api-daily-dailyleaderboardmenu.text-034") + viewerPosition,
                viewerPosition < 0 ? NamedTextColor.DARK_GRAY : NamedTextColor.AQUA));
        lore.add(Component.text(entries.size() + GuiConfig.text("api-daily-dailyleaderboardmenu.text-035"), NamedTextColor.DARK_GRAY));
        lore.add(Component.empty());
        lore.add(Component.text(GuiConfig.text("api-daily-dailyleaderboardmenu.text-036"), NamedTextColor.GREEN).decorate(TextDecoration.BOLD));
        Component marker = viewerPosition > 0 ? Component.text("✓ ", NamedTextColor.AQUA) : Component.empty();
        return item(board.material(), marker.append(Component.text(board.title(), board.color()))
                .decorate(TextDecoration.BOLD), lore, viewerPosition > 0);
    }

    private ItemStack boardOverview(Board board, List<DailyLeaderboardEntry> entries, UUID viewer) {
        int viewerPosition = position(entries, viewer);
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(board.category(), NamedTextColor.GRAY));
        lore.add(Component.text(entries.size() + GuiConfig.text("api-daily-dailyleaderboardmenu.text-037"), NamedTextColor.WHITE));
        if (!entries.isEmpty()) lore.add(Component.text(GuiConfig.text("api-daily-dailyleaderboardmenu.text-032"), NamedTextColor.GRAY)
                .append(Component.text(entries.getFirst().name(), NamedTextColor.GOLD))
                .append(Component.text(GuiConfig.text("common.separator") + value(entries.getFirst()), NamedTextColor.WHITE)));
        lore.add(Component.text(viewerPosition < 0 ? GuiConfig.text("api-daily-dailyleaderboardmenu.text-038") : GuiConfig.text("api-daily-dailyleaderboardmenu.text-034") + viewerPosition,
                viewerPosition < 0 ? NamedTextColor.DARK_GRAY : NamedTextColor.AQUA));
        return item(board.material(), Component.text(board.title(), board.color()).decorate(TextDecoration.BOLD), lore,
                viewerPosition > 0);
    }

    private ItemStack rowItem(DailyLeaderboardEntry entry, int rank, UUID viewer) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.setOwningPlayer(Bukkit.getOfflinePlayer(entry.player()));
        NamedTextColor rankColor = rank == 1 ? NamedTextColor.GOLD
                : rank == 2 ? NamedTextColor.AQUA : rank == 3 ? NamedTextColor.YELLOW : NamedTextColor.GRAY;
        meta.displayName(Component.text("#" + rank + "  ", rankColor)
                .append(Component.text(entry.name(), entry.player().equals(viewer) ? NamedTextColor.GREEN : NamedTextColor.WHITE))
                .decorate(TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(entry.duration() ? GuiConfig.text("api-daily-dailyleaderboardmenu.text-039") : GuiConfig.text("api-daily-dailyleaderboardmenu.text-040"), NamedTextColor.GRAY)
                .append(Component.text(value(entry), NamedTextColor.GOLD)));
        if (rank <= 3) lore.add(Component.text(rank == 1 ? GuiConfig.text("api-daily-dailyleaderboardmenu.text-041") : GuiConfig.text("api-daily-dailyleaderboardmenu.text-042"), rankColor));
        if (entry.player().equals(viewer)) {
            lore.add(Component.empty());
            lore.add(Component.text(GuiConfig.text("api-daily-dailyleaderboardmenu.text-043"), NamedTextColor.GREEN).decorate(TextDecoration.BOLD));
        }
        meta.lore(lore.stream().map(line -> line.decoration(TextDecoration.ITALIC, false)).toList());
        meta.setEnchantmentGlintOverride(entry.player().equals(viewer));
        item.setItemMeta(meta);
        return item;
    }

    private List<Board> boards() {
        List<Board> boards = new ArrayList<>();
        boards.add(new Board("wins", MessageConfig.DAILY_LEADERBOARD_WINS, Material.GOLDEN_SWORD,
                NamedTextColor.GREEN, GuiConfig.text("api-daily-dailyleaderboardmenu.text-044"), GuiConfig.text("api-daily-dailyleaderboardmenu.text-045")));
        for (String map : knownMaps(GameTypeEnum.Bingo)) {
            String slug = DailyStatsManager.mapSlug(map);
            boards.add(new Board("bingo_first_line_" + slug, replace(
                    MessageConfig.DAILY_LEADERBOARD_BINGO_FIRST_LINE, "%map%", map), Material.MAP,
                    NamedTextColor.LIGHT_PURPLE, GuiConfig.text("api-daily-dailyleaderboardmenu.text-046") + map, GuiConfig.text("api-daily-dailyleaderboardmenu.text-047")));
            boards.add(new Board("bingo_full_card_" + slug, replace(
                    MessageConfig.DAILY_LEADERBOARD_BINGO_FULL_CARD, "%map%", map), Material.FILLED_MAP,
                    NamedTextColor.GOLD, GuiConfig.text("api-daily-dailyleaderboardmenu.text-046") + map, GuiConfig.text("api-daily-dailyleaderboardmenu.text-048")));
        }
        for (String map : knownMaps(GameTypeEnum.AceRace)) {
            String slug = DailyStatsManager.mapSlug(map);
            boards.add(new Board("acerace_fastest_lap_" + slug, replace(
                    MessageConfig.DAILY_LEADERBOARD_ACERACE_FASTEST_LAP, "%map%", map), Material.CLOCK,
                    NamedTextColor.AQUA, GuiConfig.text("api-daily-dailyleaderboardmenu.text-049") + map, GuiConfig.text("api-daily-dailyleaderboardmenu.text-050")));
            boards.add(new Board("acerace_fastest_three_laps_" + slug, replace(
                    MessageConfig.DAILY_LEADERBOARD_ACERACE_FASTEST_THREE_LAPS, "%map%", map), Material.CLOCK,
                    NamedTextColor.GOLD, GuiConfig.text("api-daily-dailyleaderboardmenu.text-049") + map, GuiConfig.text("api-daily-dailyleaderboardmenu.text-051")));
        }
        return List.copyOf(boards);
    }

    private Set<String> knownMaps(GameTypeEnum game) {
        Set<String> maps = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        maps.addAll(game == GameTypeEnum.Bingo
                ? plugin.getGameManager().getBingoManager().getAreaNameList()
                : plugin.getGameManager().getAceRaceManager().getAreaNameList());
        maps.addAll(stats.recordMaps(game));
        return java.util.Collections.unmodifiableSet(maps);
    }

    private Board findBoard(String id) {
        return boards().stream().filter(board -> board.id().equals(id)).findFirst()
                .orElse(new Board(id, id, Material.PAPER, NamedTextColor.WHITE, GuiConfig.text("api-daily-dailyleaderboardmenu.text-052"), GuiConfig.text("api-daily-dailyleaderboardmenu.text-053")));
    }

    private static void drawBorder(Inventory inventory) {
        ItemStack border = item(Material.BLACK_STAINED_GLASS_PANE, Component.text(" "), List.of(), false);
        for (int slot = 0; slot < 9; slot++) inventory.setItem(slot, border);
        for (int slot = 45; slot < INVENTORY_SIZE; slot++) inventory.setItem(slot, border);
    }

    private static ItemStack refreshItem() {
        return item(Material.CLOCK, Component.text(GuiConfig.text("api-daily-dailyleaderboardmenu.text-054"), NamedTextColor.YELLOW).decorate(TextDecoration.BOLD),
                List.of(Component.text(GuiConfig.text("api-daily-dailyleaderboardmenu.text-055"), NamedTextColor.DARK_GRAY)), false);
    }

    private static ItemStack closeItem() {
        return item(Material.BARRIER, Component.text(GuiConfig.text("api-daily-dailyleaderboardmenu.text-056"), NamedTextColor.RED), List.of(), false);
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

    private static ItemStack playerHead(UUID owner, Component name, List<Component> lore, boolean glint) {
        ItemStack stack = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) stack.getItemMeta();
        meta.setOwningPlayer(Bukkit.getOfflinePlayer(owner));
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        meta.lore(lore.stream().map(line -> line.decoration(TextDecoration.ITALIC, false)).toList());
        meta.setEnchantmentGlintOverride(glint);
        stack.setItemMeta(meta);
        return stack;
    }

    private static List<Integer> categorySlots(int count) {
        if (count <= 7) return centeredRow(18, count);
        int rows = (count + 6) / 7;
        List<Integer> rowStarts = switch (rows) {
            case 2 -> List.of(18, 27);
            case 3 -> List.of(9, 18, 27);
            default -> List.of(9, 18, 27, 36);
        };
        List<Integer> slots = new ArrayList<>(count);
        int base = count / rows;
        int extra = count % rows;
        int remaining = count;
        for (int index = 0; index < rows; index++) {
            int rowCount = base + (index < extra ? 1 : 0);
            slots.addAll(centeredRow(rowStarts.get(index), rowCount));
            remaining -= rowCount;
            if (remaining == 0) break;
        }
        return slots;
    }

    private static List<Integer> centeredRow(int rowStart, int count) {
        int first = rowStart + (9 - count) / 2;
        List<Integer> slots = new ArrayList<>(count);
        for (int index = 0; index < count; index++) slots.add(first + index);
        return slots;
    }

    private static int position(List<DailyLeaderboardEntry> entries, UUID viewer) {
        for (int index = 0; index < entries.size(); index++)
            if (entries.get(index).player().equals(viewer)) return index + 1;
        return -1;
    }

    private static String value(DailyLeaderboardEntry entry) {
        return entry.duration() ? formatDuration((long) entry.value()) : (long) entry.value() + GuiConfig.text("api-daily-dailyleaderboardmenu.text-022");
    }

    private static String winRate(DailyStatSnapshot stat) {
        return stat.gamesPlayed() == 0 ? "0%"
                : Math.round(stat.wins() * 100D / stat.gamesPlayed()) + "%";
    }

    public static String formatDuration(long millis) {
        long minutes = millis / 60_000L;
        long seconds = (millis % 60_000L) / 1_000L;
        long remainder = millis % 1_000L;
        return "%d:%02d.%03d".formatted(minutes, seconds, remainder);
    }

    private static String replace(String value, String... pairs) {
        String result = value == null ? "" : value;
        for (int index = 0; index + 1 < pairs.length; index += 2) result = result.replace(pairs[index], pairs[index + 1]);
        return result;
    }

    private static void clickSound(Player player, float pitch) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7F, pitch);
    }

    private record Board(String id, String title, Material material, NamedTextColor color,
                         String category, String description) {}

    static final class LeaderboardHolder implements InventoryHolder {
        private final UUID viewer;
        private final String boardId;
        private final Map<Integer, String> boardsBySlot = new HashMap<>();
        private Inventory inventory;
        private int page;
        private int pageCount = 1;
        private LeaderboardHolder(UUID viewer, String boardId) {
            this.viewer = viewer;
            this.boardId = boardId;
        }
        @Override public @NotNull Inventory getInventory() { return inventory; }
    }
}
