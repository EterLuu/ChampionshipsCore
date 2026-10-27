package ink.ziip.championshipscore.api.daily;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.model.GameTypeEnum;
import ink.ziip.championshipscore.api.gui.MenuId;
import ink.ziip.championshipscore.configuration.config.message.GuiConfig;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

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
        GuiConfig.MenuSpec menu = GuiConfig.menu(MENU_PATH, INVENTORY_SIZE, "", defaultGameSlots());
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
        if (slot == holder.layout.control("close")) {
            player.closeInventory();
            return;
        }
        if (slot == holder.layout.control("refresh")) {
            refresh(holder);
            clickSound(player, 1.1F);
            return;
        }
        if (slot == holder.layout.control("party")) {
            daily.openPartyMenu(player);
            clickSound(player, 1.1F);
            return;
        }
        if (slot == holder.layout.control("statistics")) {
            daily.openStatsMenu(player);
            clickSound(player, 1.15F);
            return;
        }
        if (slot == holder.layout.control("back")) {
            daily.openMenu(player);
            clickSound(player, 1F);
            return;
        }
        if (slot == holder.layout.control("leaderboard")) {
            daily.openLeaderboard(player);
            clickSound(player, 1.2F);
            return;
        }
        if (slot == holder.layout.control("leave")
                && (daily.isQueued(player.getUniqueId())
                        || daily.session(player.getUniqueId()) != null)) {
            daily.leavePlay(player.getUniqueId());
            refresh(holder);
            clickSound(player, 0.8F);
            return;
        }
        if (holder.navigate(slot)) {
            refresh(holder);
            clickSound(player, 1.1F);
            return;
        }
        GameTypeEnum game = holder.gamesBySlot.get(slot);
        if (game != null && daily.selectGame(player, game)) clickSound(player, 1.2F);
    }

    boolean isBingoSlot(@NotNull MenuHolder holder, int slot) {
        return holder.gamesBySlot.get(slot) == GameTypeEnum.Bingo;
    }

    private void refresh(@NotNull MenuHolder holder) {
        Inventory inventory = holder.inventory;
        holder.clear();

        DailyPlayerSnapshot snapshot = daily.snapshot(holder.viewer);
        List<GameTypeEnum> games =
                daily.enabledGames().stream()
                        .sorted(java.util.Comparator.comparingInt(Enum::ordinal))
                        .toList();
        int totalQueued = games.stream().mapToInt(daily::queueSize).sum();
        int activeGames = (int) games.stream().filter(daily::isGameRunning).count();
        inventory.setItem(holder.layout.control("party"), partyItem(holder, snapshot));
        inventory.setItem(
                holder.layout.control("overview"),
                holder.item(
                        "overview",
                        null,
                        Map.of(
                                "games",
                                games.size(),
                                "queued",
                                totalQueued,
                                "active",
                                activeGames)));
        inventory.setItem(
                holder.layout.control("statistics"),
                holder.item(
                        "statistics",
                        null,
                        Map.of(
                                "games",
                                daily.statsManager().stat(holder.viewer, null).gamesPlayed())));

        int offset = holder.paginate(games.size());
        List<Integer> slots = holder.layout.pageSlots(holder.page, games.size());
        for (int index = 0; index < slots.size(); index++) {
            GameTypeEnum game = games.get(offset + index);
            DailyRules rules = daily.rules(game);
            if (rules == null) continue;
            int slot = slots.get(index);
            inventory.setItem(slot, gameItem(holder, game, rules));
            holder.gamesBySlot.put(slot, game);
        }
        holder.navigation(games.size());

        boolean participating =
                daily.isQueued(holder.viewer) || daily.session(holder.viewer) != null;
        inventory.setItem(
                holder.layout.control("leave"),
                holder.item("leave", participating ? "active" : "inactive", Map.of()));
        inventory.setItem(
                holder.layout.control("leaderboard"), holder.item("leaderboard", null, Map.of()));
        inventory.setItem(holder.layout.control("refresh"), holder.item("refresh", null, Map.of()));
        inventory.setItem(holder.layout.control("back"), holder.item("back", null, Map.of()));
        inventory.setItem(holder.layout.control("close"), holder.item("close", null, Map.of()));
    }

    private ItemStack partyItem(MenuHolder holder, DailyPlayerSnapshot snapshot) {
        DailyParty party = daily.partyManager().getParty(holder.viewer);
        return DailyPartyPresentation.item(
                holder.family + ".items.party",
                party == null ? "solo" : "party",
                Map.of("leader", snapshot.partyLeader(), "size", snapshot.partySize()),
                party,
                "plain");
    }

    private ItemStack gameItem(MenuHolder holder, GameTypeEnum game, DailyRules rules) {
        UUID viewer = holder.viewer;
        DailySession active = daily.activeSession(game);
        int queued = daily.queueSize(game);
        int availableSlots = daily.availableSlotCount(game);
        boolean selected = daily.isSelected(viewer, game);
        String state =
                selected
                        ? (availableSlots > 0 ? "selected" : "selected-waiting")
                        : (availableSlots > 0 ? "available" : "waiting");
        DailyStatSnapshot stat = daily.statsManager().stat(viewer, game);
        List<String> maps = mapNames(game);
        Map<String, Object> placeholders = new HashMap<>();
        placeholders.put(
                "stage",
                active == null
                        ? GuiConfig.text(MENU_PATH + ".items.unavailable.title")
                        : active.instance().getGameStageEnum().toString());
        placeholders.put("active_sessions", active == null ? 0 : daily.activeSessionCount(game));
        placeholders.put("slots", availableSlots);
        placeholders.put("queued", queued);
        placeholders.put("min", rules.minPlayers());
        placeholders.put("max", rules.maxPlayers());
        placeholders.put("countdown", daily.queueCountdown(game));
        placeholders.put(
                "maps",
                maps.isEmpty()
                        ? GuiConfig.text(MENU_PATH + ".items.unavailable.title")
                        : String.join(
                                GuiConfig.text(MENU_PATH + ".items.map-separator.title"), maps));
        placeholders.put("games_played", stat.gamesPlayed());
        placeholders.put("team_size", rules.teamSize());
        placeholders.put("team_count", rules.teams());
        return holder.item("games." + gameId(game), state, placeholders);
    }

    private List<String> mapNames(GameTypeEnum game) {
        List<String> maps =
                game == GameTypeEnum.Bingo
                        ? plugin.getGameManager().getBingoManager().getAreaNameList()
                        : game == GameTypeEnum.AceRace
                                ? plugin.getGameManager().getAceRaceManager().getAreaNameList()
                                : game == GameTypeEnum.DragonEggCarnival
                                        ? plugin.getGameManager()
                                                .getDragonEggCarnivalManager()
                                                .getAreaNameList()
                                        : game == GameTypeEnum.ParkourWarrior
                                                ? plugin.getGameManager()
                                                        .getParkourWarriorManager()
                                                        .getAreaNameList()
                                                : game == GameTypeEnum.LaserShowdown
                                                        ? plugin.getGameManager()
                                                                .getLaserShowdownManager()
                                                                .getAreaNameList()
                                                        : List.of();
        return maps.stream()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .limit(
                        Math.max(
                                0,
                                GuiConfig.integer(
                                        MENU_PATH + ".presentation.map-preview-limit", 3)))
                .toList();
    }

    private static String gameId(GameTypeEnum game) {
        return switch (game) {
            case Bingo -> "bingo";
            case AceRace -> "ace-race";
            case DragonEggCarnival -> "dragon-egg";
            case ParkourWarrior -> "parkour-warrior";
            case LaserShowdown -> "laser-showdown";
            default -> "unknown";
        };
    }

    private static List<Integer> defaultGameSlots() {
        return List.of(10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25);
    }

    private static void clickSound(Player player, float pitch) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7F, pitch);
    }

    static final class MenuHolder extends DailyMenuPage {
        private MenuHolder(UUID viewer) {
            super(
                    viewer,
                    MenuId.DAILY_GAME_SELECTION,
                    INVENTORY_SIZE,
                    defaultGameSlots(),
                    List.of(0, 2, 3, 5, 6, 8, 45, 46, 48, 50, 52),
                    Map.ofEntries(
                            Map.entry("party", PARTY_SLOT),
                            Map.entry("overview", OVERVIEW_SLOT),
                            Map.entry("statistics", STATS_SLOT),
                            Map.entry("leave", LEAVE_SLOT),
                            Map.entry("leaderboard", LEADERBOARD_SLOT),
                            Map.entry("refresh", REFRESH_SLOT),
                            Map.entry("back", BACK_SLOT),
                            Map.entry("close", CLOSE_SLOT),
                            Map.entry("previous", 48),
                            Map.entry("page", 50),
                            Map.entry("next", 52)));
        }
    }
}
