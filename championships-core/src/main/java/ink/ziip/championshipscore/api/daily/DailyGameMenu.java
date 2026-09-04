package ink.ziip.championshipscore.api.daily;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.gui.MenuId;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.configuration.config.message.ConfiguredGui;
import ink.ziip.championshipscore.configuration.config.message.GuiConfig;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Free-play selector using configured game-card buttons and state variants. */
public final class DailyGameMenu {
    private static final String MENU_PATH = MenuId.DAILY_GAME_SELECTION.path();
    private static final int INVENTORY_SIZE = 54;
    private static final int PARTY_SLOT = 1;
    private static final int OVERVIEW_SLOT = 4;
    private static final int STATS_SLOT = 7;
    private static final int LEAVE_SLOT = 45;
    private static final int LEADERBOARD_SLOT = 47;
    private static final int REFRESH_SLOT = 49;
    private static final int BACK_SLOT = 51;
    private static final int CLOSE_SLOT = 53;

    private final ChampionshipsCore plugin;
    private final DailyManager daily;

    DailyGameMenu(ChampionshipsCore plugin, DailyManager daily) {
        this.plugin = plugin;
        this.daily = daily;
    }

    public void open(@NotNull Player player) {
        MenuHolder holder = new MenuHolder(player.getUniqueId());
        GuiConfig.MenuSpec menu = GuiConfig.menu(MENU_PATH, INVENTORY_SIZE, "", candidateSlots(11));
        Inventory inventory = Bukkit.createInventory(holder, menu.size(), menu.title());
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
        if (slot == slot("close", CLOSE_SLOT)) { player.closeInventory(); return; }
        if (slot == slot("refresh", REFRESH_SLOT)) { refresh(holder); clickSound(player, 1.1F); return; }
        if (slot == slot("party", PARTY_SLOT)) { daily.openPartyMenu(player); clickSound(player, 1.1F); return; }
        if (slot == slot("statistics", STATS_SLOT)) { daily.openStatsMenu(player); clickSound(player, 1.15F); return; }
        if (slot == slot("back", BACK_SLOT)) { daily.openMenu(player); clickSound(player, 1F); return; }
        if (slot == slot("leaderboard", LEADERBOARD_SLOT)) { daily.openLeaderboard(player); clickSound(player, 1.2F); return; }
        if (slot == slot("leave", LEAVE_SLOT) && (daily.isQueued(player.getUniqueId()) || daily.session(player.getUniqueId()) != null)) {
            daily.leavePlay(player.getUniqueId()); refresh(holder); clickSound(player, 0.8F); return;
        }
        GameTypeEnum game = holder.gamesBySlot.get(slot);
        if (game != null && daily.selectGame(player, game)) clickSound(player, 1.2F);
    }

    boolean isBingoSlot(@NotNull MenuHolder holder, int slot) {
        return holder.gamesBySlot.get(slot) == GameTypeEnum.Bingo;
    }

    private void refresh(@NotNull MenuHolder holder) {
        Inventory inventory = holder.inventory;
        inventory.clear();
        holder.gamesBySlot.clear();
        ItemStack border = configured("border", null, Map.of(), Material.BLACK_STAINED_GLASS_PANE);
        for (int slot : GuiConfig.slots(MENU_PATH + ".layout.border",
                List.of(0, 1, 2, 3, 5, 6, 8, 45, 46, 48, 50, 52)))
            if (slot >= 0 && slot < inventory.getSize()) inventory.setItem(slot, border);

        DailyPlayerSnapshot snapshot = daily.snapshot(holder.viewer);
        List<GameTypeEnum> games = daily.enabledGames().stream().sorted(java.util.Comparator.comparingInt(Enum::ordinal)).toList();
        int totalQueued = games.stream().mapToInt(daily::queueSize).sum();
        int activeGames = (int) games.stream().filter(daily::isGameRunning).count();
        inventory.setItem(slot("party", PARTY_SLOT), partyItem(holder.viewer, snapshot));
        inventory.setItem(slot("overview", OVERVIEW_SLOT), configured("overview", null,
                Map.of("games", games.size(), "queued", totalQueued, "active", activeGames), Material.NETHER_STAR));
        inventory.setItem(slot("statistics", STATS_SLOT), configured("statistics", null,
                Map.of("games", daily.statsManager().stat(holder.viewer, null).gamesPlayed()), Material.WRITABLE_BOOK));

        List<Integer> slots = GuiConfig.slots(MENU_PATH + ".layout.content", candidateSlots(games.size()));
        for (int index = 0; index < games.size() && index < slots.size(); index++) {
            GameTypeEnum game = games.get(index);
            DailyRules rules = daily.rules(game);
            if (rules == null) continue;
            int slot = slots.get(index);
            inventory.setItem(slot, gameItem(holder.viewer, game, rules));
            holder.gamesBySlot.put(slot, game);
        }
        if (games.isEmpty()) inventory.setItem(slot("empty", 22), configured("empty", null, Map.of(), Material.GRAY_DYE));

        boolean participating = daily.isQueued(holder.viewer) || daily.session(holder.viewer) != null;
        inventory.setItem(slot("leave", LEAVE_SLOT), configured("leave", participating ? "active" : "inactive", Map.of(), participating ? Material.REDSTONE_TORCH : Material.GRAY_DYE));
        inventory.setItem(slot("leaderboard", LEADERBOARD_SLOT), configured("leaderboard", null, Map.of(), Material.GOLD_INGOT));
        inventory.setItem(slot("refresh", REFRESH_SLOT), configured("refresh", null, Map.of(), Material.CLOCK));
        inventory.setItem(slot("back", BACK_SLOT), configured("back", null, Map.of(), Material.ARROW));
        inventory.setItem(slot("close", CLOSE_SLOT), configured("close", null, Map.of(), Material.BARRIER));
    }

    private ItemStack partyItem(UUID viewer, DailyPlayerSnapshot snapshot) {
        DailyParty party = daily.partyManager().getParty(viewer);
        ItemStack item = configured("party", party == null ? "solo" : "party",
                Map.of("leader", snapshot.partyLeader(), "size", snapshot.partySize()), Material.PLAYER_HEAD);
        if (party == null) return item;
        List<Component> members = new ArrayList<>();
        for (UUID member : party.members()) {
            String name = Bukkit.getOfflinePlayer(member).getName();
            members.add(Component.text((member.equals(party.leader()) ? "★ " : "• ")
                    + (name == null ? member.toString().substring(0, 8) : name)));
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<Component> lore = new ArrayList<>(meta.lore() == null ? List.of() : meta.lore());
            lore.addAll(members);
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack gameItem(UUID viewer, GameTypeEnum game, DailyRules rules) {
        DailySession active = daily.activeSession(game);
        int queued = daily.queueSize(game);
        int availableSlots = daily.availableSlotCount(game);
        boolean selected = daily.isSelected(viewer, game);
        String state = selected ? (availableSlots > 0 ? "selected" : "selected-waiting")
                : (availableSlots > 0 ? "available" : "waiting");
        DailyStatSnapshot stat = daily.statsManager().stat(viewer, game);
        List<String> maps = mapNames(game);
        Map<String, Object> placeholders = new HashMap<>();
        placeholders.put("stage", active == null ? "-" : active.instance().getGameStageEnum().toString());
        placeholders.put("active_sessions", active == null ? 0 : daily.activeSessionCount(game));
        placeholders.put("slots", availableSlots);
        placeholders.put("queued", queued);
        placeholders.put("min", rules.minPlayers());
        placeholders.put("max", rules.maxPlayers());
        placeholders.put("countdown", daily.queueCountdown(game));
        placeholders.put("maps", maps.isEmpty() ? "-" : String.join("、", maps));
        placeholders.put("games_played", stat.gamesPlayed());
        placeholders.put("team_size", rules.teams());
        placeholders.put("team_count", rules.teams());
        return configured("games." + gameId(game), state, placeholders, styleMaterial(game));
    }

    private List<String> mapNames(GameTypeEnum game) {
        List<String> maps = game == GameTypeEnum.Bingo
                ? plugin.getGameManager().getBingoManager().getAreaNameList()
                : game == GameTypeEnum.AceRace
                ? plugin.getGameManager().getAceRaceManager().getAreaNameList()
                : game == GameTypeEnum.DragonEggCarnival
                ? plugin.getGameManager().getDragonEggCarnivalManager().getAreaNameList()
                : game == GameTypeEnum.ParkourWarrior
                ? plugin.getGameManager().getParkourWarriorManager().getAreaNameList() : List.of();
        return maps.stream().sorted(String.CASE_INSENSITIVE_ORDER).limit(3).toList();
    }

    private static String gameId(GameTypeEnum game) {
        return switch (game) {
            case Bingo -> "bingo";
            case AceRace -> "ace-race";
            case DragonEggCarnival -> "dragon-egg";
            case ParkourWarrior -> "parkour-warrior";
            default -> "unknown";
        };
    }

    private static Material styleMaterial(GameTypeEnum game) {
        return switch (game) {
            case Bingo -> Material.FILLED_MAP;
            case AceRace -> Material.ELYTRA;
            case DragonEggCarnival -> Material.DRAGON_EGG;
            case ParkourWarrior -> Material.LEATHER_BOOTS;
            default -> Material.PAPER;
        };
    }

    private static ItemStack configured(String item, String state, Map<String, ?> placeholders, Material material) {
        return ConfiguredGui.item(MENU_PATH + ".items." + item, state, placeholders, material,
                Component.empty(), List.of(), false);
    }

    private static int slot(String item, int fallback) {
        return ConfiguredGui.slot(MENU_PATH + ".items." + item, fallback);
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

    static final class MenuHolder implements InventoryHolder {
        private final UUID viewer;
        private final Map<Integer, GameTypeEnum> gamesBySlot = new HashMap<>();
        private Inventory inventory;

        private MenuHolder(UUID viewer) {
            this.viewer = viewer;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }
}
