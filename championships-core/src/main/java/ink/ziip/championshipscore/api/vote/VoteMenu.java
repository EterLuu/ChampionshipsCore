package ink.ziip.championshipscore.api.vote;

import ink.ziip.championshipscore.api.gui.MenuId;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.configuration.config.message.ConfiguredGui;
import ink.ziip.championshipscore.configuration.config.message.GuiConfig;

import net.kyori.adventure.text.Component;
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
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Live voting inventory backed directly by {@link VoteManager}. */
final class VoteMenu implements Listener {
    private static final String MENU_PATH = MenuId.VOTING_BALLOT.path();
    private static final int INVENTORY_SIZE = 54;
    private static final int TIME_SLOT = 1;
    private static final int OVERVIEW_SLOT = 4;
    private static final int TURNOUT_SLOT = 7;
    private static final int BALLOT_SLOT = 40;
    private static final int CLOSE_SLOT = 49;
    private static final int VOTE_BAR_LENGTH = 8;

    private final VoteManager manager;

    VoteMenu(@NotNull VoteManager manager) {
        this.manager = manager;
    }

    void open(@NotNull Player player) {
        Holder holder = new Holder(player.getUniqueId());
        GuiConfig.MenuSpec menu = GuiConfig.menu(MENU_PATH, INVENTORY_SIZE, "", candidateSlots(11));
        Inventory inventory = Bukkit.createInventory(holder, menu.size(), menu.title());
        holder.inventory = inventory;
        refresh(holder);
        player.openInventory(inventory);
    }

    void refreshOpenMenus() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof Holder holder) refresh(holder);
        }
    }

    void closeAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof Holder) player.closeInventory();
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
        if (event.getRawSlot() == slot("close", CLOSE_SLOT)) {
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
        if (event.getView().getTopInventory().getHolder() instanceof Holder) event.setCancelled(true);
    }

    private void refresh(@NotNull Holder holder) {
        Inventory inventory = holder.inventory;
        inventory.clear();
        holder.gamesBySlot.clear();
        ItemStack border = configured("border", null, Map.of(), Material.BLACK_STAINED_GLASS_PANE);
        for (int slot : GuiConfig.slots(MENU_PATH + ".layout.border",
                List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 45, 46, 47, 48, 49, 50, 51, 52, 53)))
            if (slot >= 0 && slot < inventory.getSize()) inventory.setItem(slot, border);

        List<GameTypeEnum> candidates = List.of(GameTypeEnum.values()).stream().filter(manager::canVoteFor).toList();
        List<Integer> slots = GuiConfig.slots(MENU_PATH + ".layout.content", candidateSlots(candidates.size()));
        int totalVotes = manager.getTotalVoteCount();
        int highestVotes = candidates.stream().mapToInt(manager::getVoteNums).max().orElse(0);
        GameTypeEnum selected = manager.getPlayerVote(holder.viewer);

        for (int index = 0; index < candidates.size() && index < slots.size(); index++) {
            GameTypeEnum gameType = candidates.get(index);
            int slot = slots.get(index);
            inventory.setItem(slot, gameItem(gameType, selected == gameType, totalVotes, highestVotes));
            holder.gamesBySlot.put(slot, gameType);
        }
        if (candidates.isEmpty()) inventory.setItem(slot("empty", 22), configured("empty", null, Map.of(), Material.GRAY_DYE));

        int eligible = manager.getEligibleVoterCount();
        int turnoutPercentage = eligible == 0 ? 0 : (int) Math.round(totalVotes * 100D / eligible);
        String remainingText = String.format(Locale.ROOT, "%d:%02d", manager.getRemainingSeconds() / 60, Math.max(0, manager.getRemainingSeconds()) % 60);
        inventory.setItem(slot("time", TIME_SLOT), configured("time", null, Map.of("time", remainingText), Material.CLOCK));
        inventory.setItem(slot("overview", OVERVIEW_SLOT), overviewItem(candidates, totalVotes, highestVotes));
        inventory.setItem(slot("turnout", TURNOUT_SLOT), configured("turnout", null,
                Map.of("votes", totalVotes, "eligible", eligible, "percentage", turnoutPercentage,
                        "bar", voteBar(totalVotes, eligible)), Material.NAME_TAG));
        inventory.setItem(slot("my-ballot", BALLOT_SLOT), configured("my-ballot",
                selected == null ? "empty" : "selected", Map.of("game", selected == null ? "" : selected.toString()), Material.WRITABLE_BOOK));
        inventory.setItem(slot("close", CLOSE_SLOT), configured("close", null, Map.of(), Material.BARRIER));
    }

    private ItemStack gameItem(@NotNull GameTypeEnum gameType, boolean selected, int totalVotes, int highestVotes) {
        int votes = manager.getVoteNums(gameType);
        int percentage = totalVotes == 0 ? 0 : (int) Math.round(votes * 100D / totalVotes);
        boolean leading = highestVotes > 0 && votes == highestVotes;
        String state = selected ? "selected" : leading ? "leading" : "available";
        return configured("games." + gameId(gameType), state, Map.of(
                "game", gameType.toString(), "votes", votes, "percentage", percentage,
                "bar", voteBar(votes, Math.max(1, totalVotes))), styleMaterial(gameType));
    }

    private ItemStack overviewItem(List<GameTypeEnum> candidates, int totalVotes, int highestVotes) {
        List<GameTypeEnum> leaders = highestVotes == 0 ? List.of() : candidates.stream()
                .filter(game -> manager.getVoteNums(game) == highestVotes).toList();
        Map<String, Object> placeholders = Map.of(
                "candidates", candidates.size(), "votes", totalVotes,
                "leader", leaders.isEmpty() ? "" : leaders.getFirst().toString(),
                "count", leaders.size());
        return configured("overview", null, placeholders, Material.NETHER_STAR);
    }

    private String voteBar(int votes, int total) {
        int filled = total == 0 ? 0 : Math.min(VOTE_BAR_LENGTH, (int) Math.round(votes * VOTE_BAR_LENGTH / (double) total));
        return "■".repeat(filled) + "□".repeat(VOTE_BAR_LENGTH - filled);
    }

    private static String gameId(GameTypeEnum game) {
        return switch (game) {
            case Bingo -> "bingo";
            case ParkourTag -> "parkour-tag";
            case BattleBox -> "battle-box";
            case TNTRun -> "tnt-run";
            case SnowballShowdown -> "snowball-showdown";
            case SkyWars -> "sky-wars";
            case TGTTOS -> "tgttos";
            case ParkourWarrior -> "parkour-warrior";
            case HotyCodyDusky -> "hoty-cody-dusky";
            case BuildMart -> "build-mart";
            case AceRace -> "ace-race";
            default -> "unknown";
        };
    }

    private static Material styleMaterial(GameTypeEnum game) {
        return switch (game) {
            case Bingo -> Material.FILLED_MAP;
            case ParkourTag -> Material.GOLDEN_CARROT;
            case BattleBox -> Material.WHITE_WOOL;
            case TNTRun -> Material.TNT;
            case SnowballShowdown -> Material.SNOWBALL;
            case SkyWars -> Material.GRASS_BLOCK;
            case TGTTOS -> Material.FEATHER;
            case ParkourWarrior -> Material.IRON_BOOTS;
            case HotyCodyDusky -> Material.COD;
            case BuildMart -> Material.CRAFTING_TABLE;
            case AceRace -> Material.ELYTRA;
            default -> Material.PAPER;
        };
    }

    private static int slot(String item, int fallback) {
        return ConfiguredGui.slot(MENU_PATH + ".items." + item, fallback);
    }

    private static ItemStack configured(String item, String state, Map<String, ?> placeholders, Material material) {
        return ConfiguredGui.item(MENU_PATH + ".items." + item, state, placeholders, material,
                Component.empty(), List.of(), false);
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
        for (int index = 0; index < count; index++) slots.add(firstSlot + index);
        return slots;
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
