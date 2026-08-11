package ink.ziip.championshipscore.api.daily;

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
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Free-play selector using the same information hierarchy as the vote and spectate menus. */
public final class DailyGameMenu {
    private static final int INVENTORY_SIZE = 54;
    private static final int PARTY_SLOT = 1;
    private static final int OVERVIEW_SLOT = 4;
    private static final int STATS_SLOT = 7;
    private static final int LEAVE_SLOT = 45;
    private static final int LEADERBOARD_SLOT = 47;
    private static final int REFRESH_SLOT = 49;
    private static final int BACK_SLOT = 51;
    private static final int CLOSE_SLOT = 53;
    private static final int BAR_LENGTH = 10;
    private static final Map<GameTypeEnum, GameStyle> STYLES = createStyles();

    private final ChampionshipsCore plugin;
    private final DailyManager daily;

    DailyGameMenu(ChampionshipsCore plugin, DailyManager daily) {
        this.plugin = plugin;
        this.daily = daily;
    }

    public void open(@NotNull Player player) {
        MenuHolder holder = new MenuHolder(player.getUniqueId());
        Inventory inventory = Bukkit.createInventory(holder, INVENTORY_SIZE,
                Utils.toComponent(MessageConfig.DAILY_MENU_GAME_TITLE));
        holder.inventory = inventory;
        refresh(holder);
        player.openInventory(inventory);
    }

    void refreshOpenMenus() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Inventory top = player.getOpenInventory().getTopInventory();
            if (top.getHolder() instanceof MenuHolder holder) refresh(holder);
        }
    }

    void click(@NotNull Player player, int slot, @NotNull MenuHolder holder) {
        if (!holder.viewer.equals(player.getUniqueId())) {
            player.closeInventory();
            return;
        }
        if (slot == CLOSE_SLOT) {
            player.closeInventory();
            return;
        }
        if (slot == REFRESH_SLOT) {
            refresh(holder);
            clickSound(player, 1.1F);
            return;
        }
        if (slot == PARTY_SLOT) {
            daily.openPartyMenu(player);
            clickSound(player, 1.1F);
            return;
        }
        if (slot == STATS_SLOT) {
            daily.openStatsMenu(player);
            clickSound(player, 1.15F);
            return;
        }
        if (slot == BACK_SLOT) {
            daily.openMenu(player);
            clickSound(player, 1F);
            return;
        }
        if (slot == LEADERBOARD_SLOT) {
            daily.openLeaderboard(player);
            clickSound(player, 1.2F);
            return;
        }
        if (slot == LEAVE_SLOT && (daily.isQueued(player.getUniqueId()) || daily.session(player.getUniqueId()) != null)) {
            daily.leavePlay(player.getUniqueId());
            refresh(holder);
            clickSound(player, 0.8F);
            return;
        }
        GameTypeEnum game = holder.gamesBySlot.get(slot);
        if (game != null && daily.selectGame(player, game)) clickSound(player, 1.2F);
    }

    private void refresh(@NotNull MenuHolder holder) {
        Inventory inventory = holder.inventory;
        inventory.clear();
        holder.gamesBySlot.clear();
        ItemStack border = item(Material.BLACK_STAINED_GLASS_PANE, Component.text(" "), List.of(), false);
        for (int slot = 0; slot < 9; slot++) inventory.setItem(slot, border);
        for (int slot = 45; slot < INVENTORY_SIZE; slot++) inventory.setItem(slot, border);

        DailyPlayerSnapshot snapshot = daily.snapshot(holder.viewer);
        List<GameTypeEnum> games = daily.enabledGames().stream().sorted(java.util.Comparator.comparingInt(Enum::ordinal)).toList();
        int totalQueued = games.stream().mapToInt(daily::queueSize).sum();
        int activeGames = (int) games.stream().filter(daily::isGameRunning).count();
        inventory.setItem(PARTY_SLOT, partyItem(holder.viewer, snapshot));
        inventory.setItem(OVERVIEW_SLOT, overviewItem(games.size(), totalQueued, activeGames));
        inventory.setItem(STATS_SLOT, statsItem(holder.viewer));

        List<Integer> slots = candidateSlots(games.size());
        for (int index = 0; index < games.size(); index++) {
            GameTypeEnum game = games.get(index);
            DailyRules rules = daily.rules(game);
            if (rules == null) continue;
            int slot = slots.get(index);
            inventory.setItem(slot, gameItem(holder.viewer, game, rules));
            holder.gamesBySlot.put(slot, game);
        }
        if (games.isEmpty()) inventory.setItem(22, item(Material.GRAY_DYE,
                Component.text("暂无开放项目", NamedTextColor.GRAY).decorate(TextDecoration.BOLD),
                List.of(Component.text("请等待管理员开放游戏项目", NamedTextColor.DARK_GRAY)), false));

        boolean participating = daily.isQueued(holder.viewer) || daily.session(holder.viewer) != null;
        inventory.setItem(LEAVE_SLOT, item(participating ? Material.REDSTONE_TORCH : Material.GRAY_DYE,
                Component.text(participating ? "离开当前游玩" : "尚未加入游玩",
                        participating ? NamedTextColor.RED : NamedTextColor.GRAY).decorate(TextDecoration.BOLD),
                participating ? List.of(
                        Component.text("同行小队将一起离开", NamedTextColor.YELLOW),
                        Component.text("若场内无人，游戏将直接结束", NamedTextColor.DARK_GRAY))
                        : List.of(Component.text("选择上方项目加入匹配", NamedTextColor.DARK_GRAY)), false));
        inventory.setItem(LEADERBOARD_SLOT, leaderboardItem(holder.viewer));
        inventory.setItem(REFRESH_SLOT, item(Material.CLOCK,
                Component.text("刷新", NamedTextColor.YELLOW).decorate(TextDecoration.BOLD),
                List.of(Component.text("队列与倒计时每秒自动更新", NamedTextColor.DARK_GRAY)), false));
        inventory.setItem(BACK_SLOT, item(Material.ARROW,
                Component.text("返回大厅", NamedTextColor.WHITE).decorate(TextDecoration.BOLD), List.of(), false));
        inventory.setItem(CLOSE_SLOT, item(Material.BARRIER, Component.text("关闭", NamedTextColor.RED), List.of(), false));
    }

    private ItemStack partyItem(UUID viewer, DailyPlayerSnapshot snapshot) {
        DailyParty party = daily.partyManager().getParty(viewer);
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("队长  ", NamedTextColor.GRAY)
                .append(Component.text(snapshot.partyLeader(), NamedTextColor.AQUA)));
        lore.add(Component.text("人数  ", NamedTextColor.GRAY)
                .append(Component.text(snapshot.partySize() + " 人", NamedTextColor.WHITE)));
        if (party != null) {
            lore.add(Component.empty());
            lore.add(Component.text("同行成员", NamedTextColor.GOLD));
            for (UUID member : party.members()) {
                String name = Bukkit.getOfflinePlayer(member).getName();
                lore.add(Component.text((member.equals(party.leader()) ? "★ " : "• ")
                        + (name == null ? member.toString().substring(0, 8) : name),
                        member.equals(viewer) ? NamedTextColor.GREEN : NamedTextColor.WHITE));
            }
        } else {
            lore.add(Component.empty());
            lore.add(Component.text("当前以个人身份进入匹配", NamedTextColor.DARK_GRAY));
            lore.add(Component.text("点击打开组队功能", NamedTextColor.YELLOW));
        }
        lore.add(Component.empty());
        lore.add(Component.text("任意成员都可以为全队改选游戏", NamedTextColor.DARK_GRAY));
        return playerHead(viewer,
                Component.text("同行小队", NamedTextColor.AQUA).decorate(TextDecoration.BOLD), lore, party != null);
    }

    private ItemStack overviewItem(int games, int totalQueued, int activeGames) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(games + " 个开放项目", NamedTextColor.WHITE)
                .append(Component.text("  ·  " + totalQueued + " 人正在等候", NamedTextColor.GRAY)));
        lore.add(Component.text(activeGames == 0 ? "当前所有项目均可匹配" : activeGames + " 个项目正在游戏中",
                activeGames == 0 ? NamedTextColor.GREEN : NamedTextColor.GOLD));
        lore.add(Component.text("无需预先分队，匹配完成后自动组队", NamedTextColor.DARK_GRAY));
        lore.add(Component.text("点击项目即可加入或为全队改选", NamedTextColor.YELLOW));
        return item(Material.NETHER_STAR,
                Component.text("匹配大厅", NamedTextColor.GREEN).decorate(TextDecoration.BOLD),
                lore, false);
    }

    private ItemStack statsItem(UUID viewer) {
        DailyStatSnapshot stat = daily.statsManager().stat(viewer, null);
        return item(Material.WRITABLE_BOOK,
                Component.text("我的战绩", NamedTextColor.LIGHT_PURPLE).decorate(TextDecoration.BOLD),
                List.of(
                        Component.text("场次  ", NamedTextColor.GRAY).append(Component.text(stat.gamesPlayed(), NamedTextColor.WHITE)),
                        Component.text("胜场  ", NamedTextColor.GRAY).append(Component.text(stat.wins(), NamedTextColor.GREEN)),
                        Component.text("胜率  ", NamedTextColor.GRAY).append(Component.text(winRate(stat), NamedTextColor.YELLOW)),
                        Component.text("每场成绩独立记录，不继承积分", NamedTextColor.DARK_GRAY)
                ), false);
    }

    private ItemStack gameItem(UUID viewer, GameTypeEnum game, DailyRules rules) {
        GameStyle style = STYLES.getOrDefault(game,
                new GameStyle(Material.PAPER, NamedTextColor.WHITE, "游戏项目", "与其他玩家一起完成挑战"));
        int queued = daily.queueSize(game);
        int queuedGroups = daily.queueGroupCount(game);
        int countdown = daily.queueCountdown(game);
        int filled = Math.min(BAR_LENGTH, (int) Math.round(queued * BAR_LENGTH / (double) rules.minPlayers()));
        boolean selected = daily.isSelected(viewer, game);
        DailySession active = daily.activeSession(game);
        boolean running = active != null;
        int needed = Math.max(0, rules.minPlayers() - queued);
        DailyStatSnapshot stat = daily.statsManager().stat(viewer, game);
        List<String> maps = mapNames(game);

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(style.category, NamedTextColor.DARK_GRAY));
        lore.add(Component.text(style.description, NamedTextColor.GRAY));
        lore.add(Component.empty());
        if (running) {
            lore.add(Component.text("游戏状态  ", NamedTextColor.GRAY)
                    .append(Component.text(active.instance().getGameStageEnum().toString(), NamedTextColor.GOLD)));
            lore.add(Component.text("进行中地图  ", NamedTextColor.GRAY)
                    .append(Component.text(active.map(), NamedTextColor.AQUA))
                    .append(daily.activeSessionCount(game) > 1
                            ? Component.text("  ·  " + daily.activeSessionCount(game) + " 场", NamedTextColor.DARK_GRAY)
                            : Component.empty()));
        } else {
            lore.add(Component.text("游戏状态  ", NamedTextColor.GRAY)
                    .append(Component.text("可加入匹配", NamedTextColor.GREEN)));
        }
        lore.add(Component.empty());
        lore.add(Component.text("■".repeat(filled), countdown >= 0 ? NamedTextColor.GOLD : style.color)
                .append(Component.text("□".repeat(BAR_LENGTH - filled), NamedTextColor.DARK_GRAY)));
        lore.add(Component.text(queued + " / " + rules.minPlayers() + " 人达到开场人数", NamedTextColor.WHITE));
        lore.add(countdown >= 0
                ? Component.text("即将启程  ", NamedTextColor.GRAY).append(Component.text(countdown + " 秒", NamedTextColor.GOLD))
                : queued > 0 && queuedGroups < 2
                ? Component.text("还需另一名玩家或同行小队", NamedTextColor.YELLOW)
                : needed == 0 ? Component.text("正在准备倒计时", NamedTextColor.GREEN)
                : Component.text("还需 " + needed + " 名同游者", NamedTextColor.YELLOW));
        lore.add(Component.empty());
        lore.add(Component.text("组队规则", NamedTextColor.GOLD));
        lore.add(Component.text("每队至多 " + rules.teamSize() + " 人  ·  最多 " + rules.teams() + " 队", NamedTextColor.WHITE));
        lore.add(Component.text("至少需要 2 个独立玩家或同行小队", NamedTextColor.GRAY));
        lore.add(Component.text("单场容量 " + rules.minPlayers() + "–" + rules.maxPlayers() + " 人", NamedTextColor.GRAY));
        lore.add(Component.text("地图  ", NamedTextColor.GRAY)
                .append(Component.text(maps.isEmpty() ? "等待场地" : String.join("、", maps),
                        maps.isEmpty() ? NamedTextColor.DARK_GRAY : NamedTextColor.AQUA)));
        lore.add(Component.empty());
        lore.add(Component.text("我的成绩  ", NamedTextColor.GRAY)
                .append(Component.text(stat.gamesPlayed() + " 场", NamedTextColor.WHITE))
                .append(Component.text("  ·  " + stat.wins() + " 胜", NamedTextColor.GREEN)));
        lore.add(Component.text("胜率 " + winRate(stat), NamedTextColor.YELLOW));
        lore.add(Component.empty());
        if (running && selected) {
            lore.add(Component.text("✓  已在候场队列", NamedTextColor.AQUA).decorate(TextDecoration.BOLD));
            lore.add(Component.text("当前场次结束后将恢复匹配", NamedTextColor.GOLD));
        } else if (running) {
            lore.add(Component.text("游戏进行中", NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
            lore.add(Component.text("当前暂时无法加入匹配", NamedTextColor.RED));
        } else if (selected) {
            lore.add(Component.text("✓  当前选择", NamedTextColor.AQUA).decorate(TextDecoration.BOLD));
            lore.add(Component.text("再次点击不会重复加入", NamedTextColor.DARK_GRAY));
        } else {
            lore.add(Component.text("点击加入匹配", NamedTextColor.GREEN).decorate(TextDecoration.BOLD));
            lore.add(Component.text("同行小队将同步选择", NamedTextColor.DARK_GRAY));
        }
        Component marker = selected ? Component.text("✓ ", NamedTextColor.AQUA)
                : running ? Component.text("● ", NamedTextColor.GOLD) : Component.empty();
        Component name = marker.append(Component.text(game.toString(), style.color))
                .decorate(TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false);
        return item(style.material, name, lore, selected);
    }

    private ItemStack leaderboardItem(UUID viewer) {
        List<DailyLeaderboardEntry> board = daily.statsManager().leaderboard("wins");
        int position = -1;
        for (int index = 0; index < board.size(); index++) if (board.get(index).player().equals(viewer)) { position = index + 1; break; }
        List<Component> lore = new ArrayList<>();
        if (!board.isEmpty()) lore.add(Component.text("当前领跑  ", NamedTextColor.GRAY)
                .append(Component.text(board.getFirst().name(), NamedTextColor.GOLD))
                .append(Component.text("  ·  " + (long) board.getFirst().value() + " 胜", NamedTextColor.WHITE)));
        lore.add(Component.text(position < 0 ? "我还没有胜场记录" : "我的胜场榜名次  #" + position,
                position < 0 ? NamedTextColor.DARK_GRAY : NamedTextColor.AQUA));
        lore.add(Component.empty());
        lore.add(Component.text("点击查看积分、胜场与竞速纪录", NamedTextColor.YELLOW));
        return item(Material.GOLD_INGOT,
                Component.text("排行榜", NamedTextColor.GOLD).decorate(TextDecoration.BOLD), lore, false);
    }

    private static String winRate(DailyStatSnapshot stat) {
        return stat.gamesPlayed() == 0 ? "0%"
                : Math.round(stat.wins() * 100D / stat.gamesPlayed()) + "%";
    }

    private List<String> mapNames(GameTypeEnum game) {
        List<String> maps = game == GameTypeEnum.Bingo
                ? plugin.getGameManager().getBingoManager().getAreaNameList()
                : game == GameTypeEnum.AceRace
                ? plugin.getGameManager().getAceRaceManager().getAreaNameList() : List.of();
        return maps.stream().sorted(String.CASE_INSENSITIVE_ORDER).limit(3).toList();
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

    private static List<Integer> candidateSlots(int count) {
        if (count <= 0) return List.of();
        if (count <= 7) return centeredRow(18, count);
        int firstRow = (count + 1) / 2;
        List<Integer> slots = new ArrayList<>(count);
        slots.addAll(centeredRow(9, firstRow));
        slots.addAll(centeredRow(18, count - firstRow));
        return slots;
    }

    private static List<Integer> centeredRow(int rowStart, int count) {
        int first = rowStart + (9 - count) / 2;
        List<Integer> slots = new ArrayList<>(count);
        for (int index = 0; index < count; index++) slots.add(first + index);
        return slots;
    }

    private static void clickSound(Player player, float pitch) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7F, pitch);
    }

    private static Map<GameTypeEnum, GameStyle> createStyles() {
        Map<GameTypeEnum, GameStyle> styles = new EnumMap<>(GameTypeEnum.class);
        styles.put(GameTypeEnum.Bingo, new GameStyle(Material.FILLED_MAP, NamedTextColor.LIGHT_PURPLE,
                "探索 · 团队协作", "完成共享宾果任务，争取连线与全收集"));
        styles.put(GameTypeEnum.AceRace, new GameStyle(Material.ELYTRA, NamedTextColor.AQUA,
                "竞速 · 独立赛道实例", "驾驭鞘翅与三叉戟，挑战最快完整圈"));
        return Map.copyOf(styles);
    }

    private record GameStyle(Material material, NamedTextColor color, String category, String description) {}

    static final class MenuHolder implements InventoryHolder {
        private final UUID viewer;
        private final Map<Integer, GameTypeEnum> gamesBySlot = new HashMap<>();
        private Inventory inventory;
        private MenuHolder(UUID viewer) { this.viewer = viewer; }
        @Override public @NotNull Inventory getInventory() { return inventory; }
    }
}
