package ink.ziip.championshipscore.api.vote;

import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Live voting inventory backed directly by {@link VoteManager}. */
final class VoteMenu implements Listener {
    private static final int INVENTORY_SIZE = 54;
    private static final int TIME_SLOT = 1;
    private static final int OVERVIEW_SLOT = 4;
    private static final int TURNOUT_SLOT = 7;
    private static final int BALLOT_SLOT = 40;
    private static final int CLOSE_SLOT = 49;
    private static final int VOTE_BAR_LENGTH = 8;
    private static final Map<GameTypeEnum, GameEntry> GAME_ENTRIES = createGameEntries();

    private final VoteManager manager;

    VoteMenu(@NotNull VoteManager manager) {
        this.manager = manager;
    }

    void open(@NotNull Player player) {
        Holder holder = new Holder(player.getUniqueId());
        Inventory inventory = Bukkit.createInventory(holder, INVENTORY_SIZE,
                Component.text("锦标赛选票", NamedTextColor.GOLD)
                        .append(Component.text(" · 下一场", NamedTextColor.WHITE))
                        .decorate(TextDecoration.BOLD)
                        .decoration(TextDecoration.ITALIC, false));
        holder.inventory = inventory;
        refresh(holder);
        player.openInventory(inventory);
    }

    void refreshOpenMenus() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Inventory top = player.getOpenInventory().getTopInventory();
            if (top.getHolder() instanceof Holder holder) {
                refresh(holder);
            }
        }
    }

    void closeAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof Holder) {
                player.closeInventory();
            }
        }
    }

    @EventHandler
    public void onInventoryClick(@NotNull InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof Holder holder)) return;

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getClickedInventory() != top) return;
        if (!holder.viewer.equals(player.getUniqueId())) {
            player.closeInventory();
            return;
        }

        if (event.getRawSlot() == CLOSE_SLOT) {
            player.closeInventory();
            return;
        }

        GameTypeEnum gameType = holder.gamesBySlot.get(event.getRawSlot());
        if (gameType == null) return;

        manager.vote(player, gameType);
        if (player.getOpenInventory().getTopInventory().getHolder() == holder) {
            refresh(holder);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7F, 1.15F);
        }
    }

    @EventHandler
    public void onInventoryDrag(@NotNull InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }

    private void refresh(@NotNull Holder holder) {
        Inventory inventory = holder.inventory;
        inventory.clear();
        holder.gamesBySlot.clear();

        ItemStack border = item(Material.BLACK_STAINED_GLASS_PANE, Component.text(" "), List.of(), false, 1);
        for (int slot = 0; slot < 9; slot++) inventory.setItem(slot, border);
        for (int slot = 45; slot < INVENTORY_SIZE; slot++) inventory.setItem(slot, border);

        List<GameTypeEnum> candidates = List.of(GameTypeEnum.values()).stream()
                .filter(manager::canVoteFor)
                .toList();
        List<Integer> slots = candidateSlots(candidates.size());
        int totalVotes = manager.getTotalVoteCount();
        int highestVotes = candidates.stream().mapToInt(manager::getVoteNums).max().orElse(0);
        GameTypeEnum selected = manager.getPlayerVote(holder.viewer);

        for (int index = 0; index < candidates.size(); index++) {
            GameTypeEnum gameType = candidates.get(index);
            int slot = slots.get(index);
            inventory.setItem(slot, gameItem(gameType, selected == gameType, totalVotes, highestVotes));
            holder.gamesBySlot.put(slot, gameType);
        }

        if (candidates.isEmpty()) {
            inventory.setItem(22, item(Material.GRAY_DYE,
                    Component.text("本轮暂无候选项目", NamedTextColor.GRAY),
                    List.of(Component.text("等待场地发布", NamedTextColor.DARK_GRAY)), false, 1));
        }

        inventory.setItem(TIME_SLOT, timeItem());
        inventory.setItem(OVERVIEW_SLOT, overviewItem(candidates, totalVotes, highestVotes));
        inventory.setItem(TURNOUT_SLOT, turnoutItem(totalVotes));
        inventory.setItem(BALLOT_SLOT, ballotItem(selected));
        inventory.setItem(CLOSE_SLOT, item(Material.BARRIER,
                Component.text("关闭", NamedTextColor.RED),
                List.of(), false, 1));
    }

    private ItemStack gameItem(@NotNull GameTypeEnum gameType, boolean selected, int totalVotes, int highestVotes) {
        GameEntry entry = GAME_ENTRIES.getOrDefault(gameType,
                new GameEntry(Material.PAPER, NamedTextColor.WHITE,
                        "锦标赛项目", "查看项目规则后作出选择"));
        int votes = manager.getVoteNums(gameType);
        int percentage = totalVotes == 0 ? 0 : (int) Math.round(votes * 100D / totalVotes);
        int filled = totalVotes == 0 ? 0 : (int) Math.round(votes * VOTE_BAR_LENGTH / (double) totalVotes);
        boolean leading = highestVotes > 0 && votes == highestVotes;

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(entry.category, NamedTextColor.DARK_GRAY));
        lore.add(Component.text(entry.description, NamedTextColor.GRAY));
        lore.add(Component.empty());
        lore.add(Component.text("■".repeat(filled), selected ? NamedTextColor.AQUA
                        : leading ? NamedTextColor.GOLD : entry.color)
                .append(Component.text("□".repeat(VOTE_BAR_LENGTH - filled), NamedTextColor.DARK_GRAY)));
        lore.add(Component.text(votes + " 票", NamedTextColor.WHITE)
                .append(Component.text("  ·  " + percentage + "%", NamedTextColor.GRAY)));
        lore.add(Component.empty());
        if (selected) {
            lore.add(Component.text("✓  我的选择", NamedTextColor.AQUA).decorate(TextDecoration.BOLD));
            lore.add(Component.text("点击其他项目即可改票", NamedTextColor.DARK_GRAY));
        } else if (leading) {
            lore.add(Component.text("★  当前领跑", NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
            lore.add(Component.text("点击投给此项目", NamedTextColor.YELLOW));
        } else {
            lore.add(Component.text("点击投票", NamedTextColor.GREEN));
        }

        Component marker = selected ? Component.text("✓ ", NamedTextColor.AQUA)
                : leading ? Component.text("★ ", NamedTextColor.GOLD) : Component.empty();
        Component name = marker.append(Component.text(gameType.toString(), entry.color))
                .decorate(TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false);
        return item(entry.material, name, lore, selected, 1);
    }

    private ItemStack timeItem() {
        int seconds = Math.max(0, manager.getRemainingSeconds());
        String time = String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
        NamedTextColor timeColor = seconds <= 15 ? NamedTextColor.RED
                : seconds <= 30 ? NamedTextColor.GOLD : NamedTextColor.GREEN;
        return item(Material.CLOCK,
                Component.text(time, timeColor).decorate(TextDecoration.BOLD),
                List.of(
                        Component.text("投票截止倒计时", NamedTextColor.GRAY),
                        Component.text("结果将在倒计时结束后锁定", NamedTextColor.DARK_GRAY)
                ), false, 1);
    }

    private ItemStack overviewItem(List<GameTypeEnum> candidates, int totalVotes, int highestVotes) {
        List<GameTypeEnum> leaders = highestVotes == 0 ? List.of() : candidates.stream()
                .filter(game -> manager.getVoteNums(game) == highestVotes)
                .toList();
        Component leaderLine;
        if (leaders.isEmpty()) {
            leaderLine = Component.text("等待第一张选票", NamedTextColor.DARK_GRAY);
        } else if (leaders.size() == 1) {
            leaderLine = Component.text("领跑  ", NamedTextColor.GRAY)
                    .append(Component.text(leaders.getFirst().toString(), NamedTextColor.GOLD));
        } else {
            leaderLine = Component.text(leaders.size() + " 个项目并列领先", NamedTextColor.GOLD);
        }

        return item(Material.NETHER_STAR,
                Component.text("下一场，由你决定", NamedTextColor.GOLD).decorate(TextDecoration.BOLD),
                List.of(
                        Component.text(candidates.size() + " 个候选项目", NamedTextColor.WHITE)
                                .append(Component.text("  ·  " + totalVotes + " 张有效选票", NamedTextColor.GRAY)),
                        leaderLine
                ), false, 1);
    }

    private ItemStack turnoutItem(int totalVotes) {
        int eligibleVoters = manager.getEligibleVoterCount();
        int percentage = eligibleVoters == 0 ? 0
                : (int) Math.round(totalVotes * 100D / eligibleVoters);
        int filled = eligibleVoters == 0 ? 0
                : Math.min(VOTE_BAR_LENGTH, (int) Math.round(totalVotes * VOTE_BAR_LENGTH / (double) eligibleVoters));
        return item(Material.NAME_TAG,
                Component.text(totalVotes + " / " + eligibleVoters, NamedTextColor.AQUA)
                        .append(Component.text("  已投", NamedTextColor.WHITE))
                        .decorate(TextDecoration.BOLD),
                List.of(
                        Component.text("■".repeat(filled), NamedTextColor.AQUA)
                                .append(Component.text("□".repeat(VOTE_BAR_LENGTH - filled), NamedTextColor.DARK_GRAY))
                                .append(Component.text("  " + percentage + "%", NamedTextColor.GRAY)),
                        Component.text("参赛者投票进度", NamedTextColor.DARK_GRAY)
                ), false, 1);
    }

    private ItemStack ballotItem(GameTypeEnum selected) {
        if (selected == null) {
            return item(Material.PAPER,
                    Component.text("选票未填写", NamedTextColor.GRAY).decorate(TextDecoration.BOLD),
                    List.of(
                            Component.text("从上方选择一个项目", NamedTextColor.WHITE),
                            Component.text("投票结束前可以随时更改", NamedTextColor.DARK_GRAY)
                    ), false, 1);
        }

        GameEntry entry = GAME_ENTRIES.getOrDefault(selected,
                new GameEntry(Material.PAPER, NamedTextColor.WHITE, "锦标赛项目", ""));
        return item(Material.WRITABLE_BOOK,
                Component.text("我的选票", NamedTextColor.AQUA).decorate(TextDecoration.BOLD),
                List.of(
                        Component.text(selected.toString(), entry.color).decorate(TextDecoration.BOLD),
                        Component.text("选票已记录", NamedTextColor.GREEN)
                ), true, 1);
    }

    private static ItemStack item(Material material, Component name, List<Component> lore,
                                  boolean glint, int amount) {
        ItemStack stack = new ItemStack(material, amount);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(name.decoration(TextDecoration.ITALIC, false));
            meta.lore(lore.stream()
                    .map(line -> line.decoration(TextDecoration.ITALIC, false))
                    .toList());
            meta.setEnchantmentGlintOverride(glint);
            stack.setItemMeta(meta);
        }
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
        int firstSlot = rowStart + (9 - count) / 2;
        List<Integer> slots = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            slots.add(firstSlot + index);
        }
        return slots;
    }

    private static Map<GameTypeEnum, GameEntry> createGameEntries() {
        Map<GameTypeEnum, GameEntry> entries = new EnumMap<>(GameTypeEnum.class);
        entries.put(GameTypeEnum.Bingo, new GameEntry(Material.FILLED_MAP, NamedTextColor.LIGHT_PURPLE,
                "探索 · 团队协作", "完成共享宾果卡，抢占格子与连线"));
        entries.put(GameTypeEnum.ParkourTag, new GameEntry(Material.GOLDEN_CARROT, NamedTextColor.AQUA,
                "追逐 · 4v4", "追击者抓捕，逃脱者争取生存时间"));
        entries.put(GameTypeEnum.BattleBox, new GameEntry(Material.WHITE_WOOL, NamedTextColor.GOLD,
                "战斗 · 4v4", "击败对手，用本队羊毛占领中心"));
        entries.put(GameTypeEnum.TNTRun, new GameEntry(Material.TNT, NamedTextColor.RED,
                "生存 · 个人赛", "在不断崩落的平台上坚持到最后"));
        entries.put(GameTypeEnum.SnowballShowdown, new GameEntry(Material.SNOWBALL, NamedTextColor.WHITE,
                "击退 · 生存赛", "用雪球将对手击出逐渐缩小的场地"));
        entries.put(GameTypeEnum.SkyWars, new GameEntry(Material.GRASS_BLOCK, NamedTextColor.YELLOW,
                "生存 · 团队战", "搜集空岛资源，在收缩边界中交战"));
        entries.put(GameTypeEnum.TGTTOS, new GameEntry(Material.FEATHER, NamedTextColor.LIGHT_PURPLE,
                "竞速 · 全队完赛", "跨越障碍抵达终点，争夺个人与团队名次"));
        entries.put(GameTypeEnum.ParkourWarrior, new GameEntry(Material.IRON_BOOTS, NamedTextColor.WHITE,
                "跑酷 · 个人赛", "挑战分支赛道，以难度和完成度计分"));
        entries.put(GameTypeEnum.HotyCodyDusky, new GameEntry(Material.COD, NamedTextColor.AQUA,
                "传递 · 生存赛", "把烫手鳕鱼传给对手并坚持到最后"));
        entries.put(GameTypeEnum.BuildMart, new GameEntry(Material.CRAFTING_TABLE, NamedTextColor.GOLD,
                "建造 · 团队协作", "采集材料并分工复原尽可能多的蓝图"));
        entries.put(GameTypeEnum.AceRace, new GameEntry(Material.ELYTRA, NamedTextColor.GREEN,
                "竞速 · 个人赛", "驾驭鞘翅与三叉戟完成多圈障碍赛道"));
        return Map.copyOf(entries);
    }

    private record GameEntry(Material material, NamedTextColor color, String category, String description) {
    }

    private static final class Holder implements InventoryHolder {
        private final java.util.UUID viewer;
        private final Map<Integer, GameTypeEnum> gamesBySlot = new HashMap<>();
        private Inventory inventory;

        private Holder(java.util.UUID viewer) {
            this.viewer = viewer;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }
}
