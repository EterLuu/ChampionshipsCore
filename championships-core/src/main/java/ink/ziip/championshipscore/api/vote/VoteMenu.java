package ink.ziip.championshipscore.api.vote;

import ink.ziip.championshipscore.configuration.config.message.GuiConfig;

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
                Component.text(GuiConfig.text("api-vote-votemenu.text-001"), NamedTextColor.GOLD)
                        .append(Component.text(GuiConfig.text("api-vote-votemenu.text-002"), NamedTextColor.WHITE))
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
                    Component.text(GuiConfig.text("api-vote-votemenu.text-003"), NamedTextColor.GRAY),
                    List.of(Component.text(GuiConfig.text("api-vote-votemenu.text-004"), NamedTextColor.DARK_GRAY)), false, 1));
        }

        inventory.setItem(TIME_SLOT, timeItem());
        inventory.setItem(OVERVIEW_SLOT, overviewItem(candidates, totalVotes, highestVotes));
        inventory.setItem(TURNOUT_SLOT, turnoutItem(totalVotes));
        inventory.setItem(BALLOT_SLOT, ballotItem(selected));
        inventory.setItem(CLOSE_SLOT, item(Material.BARRIER,
                Component.text(GuiConfig.text("api-vote-votemenu.text-005"), NamedTextColor.RED),
                List.of(), false, 1));
    }

    private ItemStack gameItem(@NotNull GameTypeEnum gameType, boolean selected, int totalVotes, int highestVotes) {
        GameEntry entry = GAME_ENTRIES.getOrDefault(gameType,
                new GameEntry(Material.PAPER, NamedTextColor.WHITE,
                        GuiConfig.text("api-vote-votemenu.text-006"), GuiConfig.text("api-vote-votemenu.text-007")));
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
        lore.add(Component.text(votes + GuiConfig.text("api-vote-votemenu.text-008"), NamedTextColor.WHITE)
                .append(Component.text(GuiConfig.text("common.separator") + percentage + "%", NamedTextColor.GRAY)));
        lore.add(Component.empty());
        if (selected) {
            lore.add(Component.text(GuiConfig.text("api-vote-votemenu.text-009"), NamedTextColor.AQUA).decorate(TextDecoration.BOLD));
            lore.add(Component.text(GuiConfig.text("api-vote-votemenu.text-010"), NamedTextColor.DARK_GRAY));
        } else if (leading) {
            lore.add(Component.text(GuiConfig.text("api-vote-votemenu.text-011"), NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
            lore.add(Component.text(GuiConfig.text("api-vote-votemenu.text-012"), NamedTextColor.YELLOW));
        } else {
            lore.add(Component.text(GuiConfig.text("api-vote-votemenu.text-013"), NamedTextColor.GREEN));
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
                        Component.text(GuiConfig.text("api-vote-votemenu.text-014"), NamedTextColor.GRAY),
                        Component.text(GuiConfig.text("api-vote-votemenu.text-015"), NamedTextColor.DARK_GRAY)
                ), false, 1);
    }

    private ItemStack overviewItem(List<GameTypeEnum> candidates, int totalVotes, int highestVotes) {
        List<GameTypeEnum> leaders = highestVotes == 0 ? List.of() : candidates.stream()
                .filter(game -> manager.getVoteNums(game) == highestVotes)
                .toList();
        Component leaderLine;
        if (leaders.isEmpty()) {
            leaderLine = Component.text(GuiConfig.text("api-vote-votemenu.text-016"), NamedTextColor.DARK_GRAY);
        } else if (leaders.size() == 1) {
            leaderLine = Component.text(GuiConfig.text("api-vote-votemenu.text-017"), NamedTextColor.GRAY)
                    .append(Component.text(leaders.getFirst().toString(), NamedTextColor.GOLD));
        } else {
            leaderLine = Component.text(leaders.size() + GuiConfig.text("api-vote-votemenu.text-018"), NamedTextColor.GOLD);
        }

        return item(Material.NETHER_STAR,
                Component.text(GuiConfig.text("api-vote-votemenu.text-019"), NamedTextColor.GOLD).decorate(TextDecoration.BOLD),
                List.of(
                        Component.text(candidates.size() + GuiConfig.text("api-vote-votemenu.text-020"), NamedTextColor.WHITE)
                                .append(Component.text(GuiConfig.text("common.separator") + totalVotes + GuiConfig.text("api-vote-votemenu.text-021"), NamedTextColor.GRAY)),
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
                Component.text(totalVotes + "/" + eligibleVoters, NamedTextColor.AQUA)
                        .append(Component.text(GuiConfig.text("api-vote-votemenu.text-022"), NamedTextColor.WHITE))
                        .decorate(TextDecoration.BOLD),
                List.of(
                        Component.text("■".repeat(filled), NamedTextColor.AQUA)
                                .append(Component.text("□".repeat(VOTE_BAR_LENGTH - filled), NamedTextColor.DARK_GRAY))
                                .append(Component.text("  " + percentage + "%", NamedTextColor.GRAY)),
                        Component.text(GuiConfig.text("api-vote-votemenu.text-023"), NamedTextColor.DARK_GRAY)
                ), false, 1);
    }

    private ItemStack ballotItem(GameTypeEnum selected) {
        if (selected == null) {
            return item(Material.PAPER,
                    Component.text(GuiConfig.text("api-vote-votemenu.text-024"), NamedTextColor.GRAY).decorate(TextDecoration.BOLD),
                    List.of(
                            Component.text(GuiConfig.text("api-vote-votemenu.text-025"), NamedTextColor.WHITE),
                            Component.text(GuiConfig.text("api-vote-votemenu.text-026"), NamedTextColor.DARK_GRAY)
                    ), false, 1);
        }

        GameEntry entry = GAME_ENTRIES.getOrDefault(selected,
                new GameEntry(Material.PAPER, NamedTextColor.WHITE, GuiConfig.text("api-vote-votemenu.text-006"), ""));
        return item(Material.WRITABLE_BOOK,
                Component.text(GuiConfig.text("api-vote-votemenu.text-027"), NamedTextColor.AQUA).decorate(TextDecoration.BOLD),
                List.of(
                        Component.text(selected.toString(), entry.color).decorate(TextDecoration.BOLD),
                        Component.text(GuiConfig.text("api-vote-votemenu.text-028"), NamedTextColor.GREEN)
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
                GuiConfig.text("api-vote-votemenu.text-029"), GuiConfig.text("api-vote-votemenu.text-030")));
        entries.put(GameTypeEnum.ParkourTag, new GameEntry(Material.GOLDEN_CARROT, NamedTextColor.AQUA,
                GuiConfig.text("api-vote-votemenu.text-031"), GuiConfig.text("api-vote-votemenu.text-032")));
        entries.put(GameTypeEnum.BattleBox, new GameEntry(Material.WHITE_WOOL, NamedTextColor.GOLD,
                GuiConfig.text("api-vote-votemenu.text-033"), GuiConfig.text("api-vote-votemenu.text-034")));
        entries.put(GameTypeEnum.TNTRun, new GameEntry(Material.TNT, NamedTextColor.RED,
                GuiConfig.text("api-vote-votemenu.text-035"), GuiConfig.text("api-vote-votemenu.text-036")));
        entries.put(GameTypeEnum.SnowballShowdown, new GameEntry(Material.SNOWBALL, NamedTextColor.WHITE,
                GuiConfig.text("api-vote-votemenu.text-037"), GuiConfig.text("api-vote-votemenu.text-038")));
        entries.put(GameTypeEnum.SkyWars, new GameEntry(Material.GRASS_BLOCK, NamedTextColor.YELLOW,
                GuiConfig.text("api-vote-votemenu.text-039"), GuiConfig.text("api-vote-votemenu.text-040")));
        entries.put(GameTypeEnum.TGTTOS, new GameEntry(Material.FEATHER, NamedTextColor.LIGHT_PURPLE,
                GuiConfig.text("api-vote-votemenu.text-041"), GuiConfig.text("api-vote-votemenu.text-042")));
        entries.put(GameTypeEnum.ParkourWarrior, new GameEntry(Material.IRON_BOOTS, NamedTextColor.WHITE,
                GuiConfig.text("api-vote-votemenu.text-043"), GuiConfig.text("api-vote-votemenu.text-044")));
        entries.put(GameTypeEnum.HotyCodyDusky, new GameEntry(Material.COD, NamedTextColor.AQUA,
                GuiConfig.text("api-vote-votemenu.text-045"), GuiConfig.text("api-vote-votemenu.text-046")));
        entries.put(GameTypeEnum.BuildMart, new GameEntry(Material.CRAFTING_TABLE, NamedTextColor.GOLD,
                GuiConfig.text("api-vote-votemenu.text-047"), GuiConfig.text("api-vote-votemenu.text-048")));
        entries.put(GameTypeEnum.AceRace, new GameEntry(Material.ELYTRA, NamedTextColor.GREEN,
                GuiConfig.text("api-vote-votemenu.text-049"), GuiConfig.text("api-vote-votemenu.text-050")));
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
