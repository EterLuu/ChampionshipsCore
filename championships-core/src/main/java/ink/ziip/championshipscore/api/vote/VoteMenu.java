package ink.ziip.championshipscore.api.vote;

import ink.ziip.championshipscore.api.game.model.GameTypeEnum;
import ink.ziip.championshipscore.api.gui.MenuId;
import ink.ziip.championshipscore.api.gui.MenuInventory;
import ink.ziip.championshipscore.api.gui.MenuLayout;
import ink.ziip.championshipscore.configuration.config.message.ConfiguredGui;
import ink.ziip.championshipscore.configuration.config.message.GuiConfig;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;

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
    private final VoteManager manager;

    VoteMenu(@NotNull VoteManager manager) {
        this.manager = manager;
    }

    void open(@NotNull Player player, boolean previewOnly) {
        if (!player.isOnline() || Bukkit.getPlayer(player.getUniqueId()) != player) return;
        var menu = GuiConfig.menu(MENU_PATH, 54, "", candidateSlots(14));
        Holder holder = new Holder(this, player, previewOnly, layout(menu.size()));
        Inventory inventory = Bukkit.createInventory(holder, holder.layout.size(), menu.title());
        holder.inventory = inventory;
        refresh(holder);
        player.openInventory(inventory);
    }

    void refreshOpenMenus() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof Holder holder
                    && owns(holder, player)) refresh(holder);
        }
    }

    void closeAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof Holder holder
                    && holder.owner == this) player.closeInventory();
        }
    }

    @EventHandler
    public void onInventoryClick(@NotNull InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof Holder holder) || holder.owner != this) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getClickedInventory() != top)
            return;
        if (!owns(holder, player)) return;
        if (event.getRawSlot() == holder.layout.control("close")) {
            player.closeInventory();
            return;
        }
        int slot = event.getRawSlot();
        if (slot == holder.layout.control("previous") && holder.page > 0) {
            holder.page--;
            refresh(holder);
            return;
        }
        if (slot == holder.layout.control("next") && holder.page + 1 < holder.pages) {
            holder.page++;
            refresh(holder);
            return;
        }
        GameTypeEnum gameType = holder.gamesBySlot.get(event.getRawSlot());
        if (gameType == null) return;
        if (holder.previewOnly) {
            player.sendMessage(MessageConfig.VOTE_VOTE_FAILED_ADMIN);
            return;
        }
        manager.vote(player, gameType);
        if (player.getOpenInventory().getTopInventory().getHolder() == holder) {
            refresh(holder);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7F, 1.15F);
        }
    }

    @EventHandler
    public void onInventoryDrag(@NotNull InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder holder
                && holder.owner == this) event.setCancelled(true);
    }

    private void refresh(@NotNull Holder holder) {
        Inventory inventory = holder.inventory;
        inventory.clear();
        holder.gamesBySlot.clear();
        ItemStack border = configured("border", null, Map.of(), Material.BLACK_STAINED_GLASS_PANE);
        for (int slot : holder.layout.border()) inventory.setItem(slot, border);

        List<GameTypeEnum> candidates =
                List.of(GameTypeEnum.values()).stream().filter(manager::isBallotGame).toList();
        holder.page = holder.layout.clampPage(holder.page, candidates.size());
        holder.pages = holder.layout.pageCount(candidates.size());
        List<Integer> slots = holder.layout.pageSlots(holder.page, candidates.size());
        int first = holder.page * holder.layout.content().size();
        int totalVotes = manager.getTotalVoteCount();
        int highestVotes = candidates.stream().mapToInt(manager::getVoteNums).max().orElse(0);
        GameTypeEnum selected = manager.getPlayerVote(holder.viewer);

        for (int index = 0; index < candidates.size() && index < slots.size(); index++) {
            GameTypeEnum gameType = candidates.get(first + index);
            int slot = slots.get(index);
            inventory.setItem(
                    slot,
                    gameItem(gameType, selected == gameType, totalVotes, highestVotes, holder.bar));
            holder.gamesBySlot.put(slot, gameType);
        }
        if (candidates.isEmpty())
            inventory.setItem(
                    holder.emptySlot, configured("empty", null, Map.of(), Material.GRAY_DYE));

        int eligible = manager.getEligibleVoterCount();
        int turnoutPercentage = eligible == 0 ? 0 : (int) Math.round(totalVotes * 100D / eligible);
        String remainingText =
                String.format(
                        Locale.ROOT,
                        "%d:%02d",
                        manager.getRemainingSeconds() / 60,
                        Math.max(0, manager.getRemainingSeconds()) % 60);
        inventory.setItem(
                holder.layout.control("time"),
                configured("time", null, Map.of("time", remainingText), Material.CLOCK));
        inventory.setItem(
                holder.layout.control("overview"),
                overviewItem(candidates, totalVotes, highestVotes));
        inventory.setItem(
                holder.layout.control("turnout"),
                configured(
                        "turnout",
                        null,
                        Map.of(
                                "votes",
                                totalVotes,
                                "eligible",
                                eligible,
                                "percentage",
                                turnoutPercentage,
                                "bar",
                                holder.bar.render(totalVotes, eligible)),
                        Material.NAME_TAG));
        inventory.setItem(
                holder.layout.control("my-ballot"),
                configured(
                        "my-ballot",
                        selected == null ? "empty" : "selected",
                        Map.of("game", selected == null ? "" : selected.toString()),
                        Material.WRITABLE_BOOK));
        inventory.setItem(
                holder.layout.control("close"),
                configured("close", null, Map.of(), Material.BARRIER));
        if (holder.page > 0) put(holder, "previous", Map.of());
        if (holder.page + 1 < holder.pages) put(holder, "next", Map.of());
        if (holder.pages > 1)
            put(
                    holder,
                    "page",
                    Map.of(
                            "page",
                            holder.page + 1,
                            "pages",
                            holder.pages,
                            "count",
                            candidates.size()));
    }

    private ItemStack gameItem(
            @NotNull GameTypeEnum gameType,
            boolean selected,
            int totalVotes,
            int highestVotes,
            VoteBar bar) {
        if (!manager.canVoteFor(gameType))
            return configured(
                    "unavailable-game",
                    null,
                    Map.of(
                            "game",
                            gameType.toString(),
                            "reason",
                            manager.unavailableReason(gameType)),
                    Material.GRAY_DYE);
        int votes = manager.getVoteNums(gameType);
        int percentage = totalVotes == 0 ? 0 : (int) Math.round(votes * 100D / totalVotes);
        boolean leading = highestVotes > 0 && votes == highestVotes;
        String state = selected ? "selected" : leading ? "leading" : "available";
        return ConfiguredGui.item(
                MENU_PATH + ".items.games." + gameId(gameType),
                state,
                Map.of(
                        "game",
                        gameType.toString(),
                        "votes",
                        votes,
                        "percentage",
                        percentage,
                        "bar",
                        bar.render(votes, Math.max(1, totalVotes))),
                styleMaterial(gameType),
                Component.text(gameType.toString()),
                List.of(),
                selected);
    }

    private ItemStack overviewItem(
            List<GameTypeEnum> candidates, int totalVotes, int highestVotes) {
        List<GameTypeEnum> leaders =
                highestVotes == 0
                        ? List.of()
                        : candidates.stream()
                                .filter(game -> manager.getVoteNums(game) == highestVotes)
                                .toList();
        Map<String, Object> placeholders =
                Map.of(
                        "candidates",
                        candidates.size(),
                        "votes",
                        totalVotes,
                        "leader",
                        leaders.isEmpty() ? "" : leaders.getFirst().toString(),
                        "count",
                        leaders.size());
        return configured("overview", null, placeholders, Material.NETHER_STAR);
    }

    private void put(Holder holder, String item, Map<String, ?> parameters) {
        holder.inventory.setItem(
                holder.layout.control(item), configured(item, null, parameters, Material.PAPER));
    }

    private boolean owns(Holder holder, Player player) {
        return holder.owner == this
                && holder.connection == player
                && holder.viewer.equals(player.getUniqueId())
                && player.isOnline()
                && Bukkit.getPlayer(holder.viewer) == player
                && player.getOpenInventory().getTopInventory() == holder.inventory;
    }

    static MenuLayout layout(int size) {
        return MenuLayout.load(
                MENU_PATH,
                size,
                candidateSlots(14),
                java.util.stream.Stream.concat(
                                MenuLayout.range(0, 9).stream(), MenuLayout.range(45, 54).stream())
                        .toList(),
                Map.of(
                        "time",
                        1,
                        "overview",
                        4,
                        "turnout",
                        7,
                        "my-ballot",
                        40,
                        "close",
                        49,
                        "previous",
                        45,
                        "page",
                        48,
                        "next",
                        53));
    }

    record VoteBar(int length, String filled, String empty) {
        static VoteBar load() {
            return new VoteBar(
                    GuiConfig.integer(MENU_PATH + ".presentation.bar-length", 8),
                    GuiConfig.text(MENU_PATH + ".presentation.bar-filled"),
                    GuiConfig.text(MENU_PATH + ".presentation.bar-empty"));
        }

        String render(int votes, int total) {
            int count =
                    total <= 0
                            ? 0
                            : Math.max(
                                    0,
                                    Math.min(
                                            length,
                                            (int) Math.round(votes * (double) length / total)));
            return filled.repeat(count) + empty.repeat(length - count);
        }
    }

    private static String gameId(GameTypeEnum game) {
        return switch (game) {
            case Bingo -> "bingo";
            case ParkourTag -> "parkour-tag";
            case BattleBox -> "battle-box";
            case TNTRun -> "tnt-run";
            case LaserShowdown -> "laser-showdown";
            case SkyWars -> "sky-wars";
            case TGTTOS -> "tgttos";
            case ParkourWarrior -> "parkour-warrior";
            case HotyCodyDusky -> "hoty-cody-dusky";
            case BuildMart -> "build-mart";
            case AceRace -> "ace-race";
            case RiptideRush -> "riptide-rush";
            case FrostbiteFrenzy -> "frostbite";
            case LaserBox -> "laserbox";
            default -> "unknown";
        };
    }

    private static Material styleMaterial(GameTypeEnum game) {
        return switch (game) {
            case Bingo -> Material.FILLED_MAP;
            case ParkourTag -> Material.GOLDEN_CARROT;
            case BattleBox -> Material.WHITE_WOOL;
            case TNTRun -> Material.TNT;
            case LaserShowdown -> Material.CROSSBOW;
            case SkyWars -> Material.GRASS_BLOCK;
            case TGTTOS -> Material.FEATHER;
            case ParkourWarrior -> Material.IRON_BOOTS;
            case HotyCodyDusky -> Material.COD;
            case BuildMart -> Material.CRAFTING_TABLE;
            case AceRace -> Material.ELYTRA;
            case RiptideRush -> Material.OAK_BOAT;
            case FrostbiteFrenzy -> Material.BLUE_ICE;
            case LaserBox -> Material.CROSSBOW;
            default -> Material.PAPER;
        };
    }

    private static ItemStack configured(
            String item, String state, Map<String, ?> placeholders, Material material) {
        return ConfiguredGui.item(
                MENU_PATH + ".items." + item,
                state,
                placeholders,
                material,
                Component.empty(),
                List.of(),
                false);
    }

    static List<Integer> candidateSlots(int count) {
        if (count <= 0) return List.of();
        if (count <= 7) return centeredRow(18, count);
        List<Integer> slots = new ArrayList<>(count);
        int rows = (count + 6) / 7;
        for (int row = 0, remaining = count; row < rows; row++) {
            int rowCount = (remaining + rows - row - 1) / (rows - row);
            slots.addAll(centeredRow(9 + row * 9, rowCount));
            remaining -= rowCount;
        }
        return slots;
    }

    private static List<Integer> centeredRow(int rowStart, int count) {
        int firstSlot = rowStart + (9 - count) / 2;
        List<Integer> slots = new ArrayList<>(count);
        for (int index = 0; index < count; index++) slots.add(firstSlot + index);
        return slots;
    }

    private static final class Holder implements MenuInventory {
        private final VoteMenu owner;
        private final Player connection;
        private final java.util.UUID viewer;
        private final MenuLayout layout;
        private final VoteBar bar = VoteBar.load();
        private final int emptySlot;
        private int page;
        private int pages = 1;
        private final boolean previewOnly;
        private final Map<Integer, GameTypeEnum> gamesBySlot = new HashMap<>();
        private Inventory inventory;

        private Holder(VoteMenu owner, Player player, boolean previewOnly, MenuLayout layout) {
            this.owner = owner;
            this.connection = player;
            this.viewer = player.getUniqueId();
            this.previewOnly = previewOnly;
            this.layout = layout;
            emptySlot =
                    GuiConfig.slot(
                            MENU_PATH + ".layout.overlay.empty",
                            GuiConfig.slot(MENU_PATH + ".items.empty.slot", 22));
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }
}
