package ink.ziip.championshipscore.api.daily;

import ink.ziip.championshipscore.api.gui.MenuId;
import ink.ziip.championshipscore.configuration.config.message.GuiConfig;
import ink.ziip.championshipscore.configuration.config.message.ConfiguredGui;

import ink.ziip.championshipscore.ChampionshipsCore;
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
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Free-play selector using the same information hierarchy as the vote and spectate menus. */
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
        GuiConfig.MenuSpec menu = GuiConfig.menu(MENU_PATH, INVENTORY_SIZE,
                Component.text("选择一场游戏"), candidateSlots(11));
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
        if (slot == slot("close", CLOSE_SLOT)) {
            player.closeInventory();
            return;
        }
        if (slot == slot("refresh", REFRESH_SLOT)) {
            refresh(holder);
            clickSound(player, 1.1F);
            return;
        }
        if (slot == slot("party", PARTY_SLOT)) {
            daily.openPartyMenu(player);
            clickSound(player, 1.1F);
            return;
        }
        if (slot == slot("statistics", STATS_SLOT)) {
            daily.openStatsMenu(player);
            clickSound(player, 1.15F);
            return;
        }
        if (slot == slot("back", BACK_SLOT)) {
            daily.openMenu(player);
            clickSound(player, 1F);
            return;
        }
        if (slot == slot("leaderboard", LEADERBOARD_SLOT)) {
            daily.openLeaderboard(player);
            clickSound(player, 1.2F);
            return;
        }
        if (slot == slot("leave", LEAVE_SLOT) && (daily.isQueued(player.getUniqueId()) || daily.session(player.getUniqueId()) != null)) {
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
        ItemStack border = configured("border", null, Map.of(),
                item(Material.BLACK_STAINED_GLASS_PANE, Component.text(" "), List.of(), false));
        for (int slot : GuiConfig.slots(MENU_PATH + ".layout.border",
                List.of(0, 1, 2, 3, 5, 6, 8, 45, 46, 48, 50, 52)))
            if (slot >= 0 && slot < inventory.getSize()) inventory.setItem(slot, border);

        DailyPlayerSnapshot snapshot = daily.snapshot(holder.viewer);
        List<GameTypeEnum> games = daily.enabledGames().stream().sorted(java.util.Comparator.comparingInt(Enum::ordinal)).toList();
        int totalQueued = games.stream().mapToInt(daily::queueSize).sum();
        int activeGames = (int) games.stream().filter(daily::isGameRunning).count();
        inventory.setItem(slot("party", PARTY_SLOT), configured("party", null,
                Map.of("leader", snapshot.partyLeader(), "size", snapshot.partySize()), partyItem(holder.viewer, snapshot)));
        inventory.setItem(slot("overview", OVERVIEW_SLOT), configured("overview", null,
                Map.of("games", games.size(), "queued", totalQueued, "active", activeGames), overviewItem(games.size(), totalQueued, activeGames)));
        inventory.setItem(slot("statistics", STATS_SLOT), configured("statistics", null, Map.of(), statsItem(holder.viewer)));

        List<Integer> slots = GuiConfig.slots(MENU_PATH + ".layout.content", candidateSlots(games.size()));
        for (int index = 0; index < games.size() && index < slots.size(); index++) {
            GameTypeEnum game = games.get(index);
            DailyRules rules = daily.rules(game);
            if (rules == null) continue;
            int slot = slots.get(index);
            inventory.setItem(slot, configured("game", daily.isSelected(holder.viewer, game) ? "selected" : "available",
                    Map.of("game", game.toString(), "queued", daily.queueSize(game)), gameItem(holder.viewer, game, rules)));
            holder.gamesBySlot.put(slot, game);
        }
        if (games.isEmpty()) inventory.setItem(slot("empty", 22), configured("empty", null, Map.of(), item(Material.GRAY_DYE,
                Component.text(GuiConfig.text("daily.menus.game-selection-screen.copy.no-open-games-yet"), NamedTextColor.GRAY).decorate(TextDecoration.BOLD),
                List.of(Component.text(GuiConfig.text("daily.menus.game-selection-screen.copy.please-wait-for-the-administrator-to-open-the-game-game"), NamedTextColor.DARK_GRAY)), false)));

        boolean participating = daily.isQueued(holder.viewer) || daily.session(holder.viewer) != null;
        inventory.setItem(slot("leave", LEAVE_SLOT), configured("leave", participating ? "active" : "inactive", Map.of(), item(participating ? Material.REDSTONE_TORCH : Material.GRAY_DYE,
                Component.text(participating ? GuiConfig.text("daily.menus.game-selection-screen.copy.leave-current-game") : GuiConfig.text("daily.menus.game-selection-screen.copy.not-yet-join-the-game"),
                        participating ? NamedTextColor.RED : NamedTextColor.GRAY).decorate(TextDecoration.BOLD),
                participating ? List.of(
                        Component.text(GuiConfig.text("daily.menus.game-selection-screen.copy.the-accompanying-team-will-leave-together"), NamedTextColor.YELLOW),
                        Component.text(GuiConfig.text("daily.menus.game-selection-screen.copy.if-there-is-no-one-on-the-field-the-game-will-end-directly"), NamedTextColor.DARK_GRAY))
                        : List.of(Component.text(GuiConfig.text("daily.menus.game-selection-screen.copy.select-the-item-above-to-join-the-match"), NamedTextColor.DARK_GRAY)), false)));
        inventory.setItem(slot("leaderboard", LEADERBOARD_SLOT), configured("leaderboard", null, Map.of(), leaderboardItem(holder.viewer)));
        inventory.setItem(slot("refresh", REFRESH_SLOT), configured("refresh", null, Map.of(), item(Material.CLOCK,
                Component.text(GuiConfig.text("daily.menus.game-selection-screen.copy.refresh"), NamedTextColor.YELLOW).decorate(TextDecoration.BOLD),
                List.of(Component.text(GuiConfig.text("daily.menus.game-selection-screen.copy.queue-and-countdown-automatically-updated-every-second"), NamedTextColor.DARK_GRAY)), false)));
        inventory.setItem(slot("back", BACK_SLOT), configured("back", null, Map.of(), item(Material.ARROW,
                Component.text(GuiConfig.text("daily.menus.game-selection-screen.copy.return-to-lobby"), NamedTextColor.WHITE).decorate(TextDecoration.BOLD), List.of(), false)));
        inventory.setItem(slot("close", CLOSE_SLOT), configured("close", null, Map.of(), item(Material.BARRIER,
                Component.text(GuiConfig.text("daily.menus.game-selection-screen.copy.close"), NamedTextColor.RED), List.of(), false)));
    }

    private static int slot(String item, int fallback) {
        return ConfiguredGui.slot(MENU_PATH + ".items." + item, fallback);
    }

    private static ItemStack configured(String item, String state, Map<String, ?> placeholders, ItemStack fallback) {
        return ConfiguredGui.item(MENU_PATH + ".items." + item, state, placeholders, fallback);
    }

    private ItemStack partyItem(UUID viewer, DailyPlayerSnapshot snapshot) {
        DailyParty party = daily.partyManager().getParty(viewer);
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(GuiConfig.text("daily.menus.game-selection-screen.copy.captain"), NamedTextColor.GRAY)
                .append(Component.text(snapshot.partyLeader(), NamedTextColor.AQUA)));
        lore.add(Component.text(GuiConfig.text("daily.menus.game-selection-screen.copy.number-of-people"), NamedTextColor.GRAY)
                .append(Component.text(snapshot.partySize() + GuiConfig.text("daily.menus.game-selection-screen.copy.player-count-suffix"), NamedTextColor.WHITE)));
        if (party != null) {
            lore.add(Component.empty());
            lore.add(Component.text(GuiConfig.text("daily.menus.game-selection-screen.copy.peer-members"), NamedTextColor.GOLD));
            for (UUID member : party.members()) {
                String name = Bukkit.getOfflinePlayer(member).getName();
                lore.add(Component.text((member.equals(party.leader()) ? "★ " : "• ")
                        + (name == null ? member.toString().substring(0, 8) : name),
                        member.equals(viewer) ? NamedTextColor.GREEN : NamedTextColor.WHITE));
            }
        } else {
            lore.add(Component.empty());
            lore.add(Component.text(GuiConfig.text("daily.menus.game-selection-screen.copy.currently-entering-matches-as-an-individual"), NamedTextColor.DARK_GRAY));
            lore.add(Component.text(GuiConfig.text("daily.menus.game-selection-screen.copy.click-to-open-the-team-function"), NamedTextColor.YELLOW));
        }
        lore.add(Component.empty());
        lore.add(Component.text(GuiConfig.text("daily.menus.game-selection-screen.copy.any-member-can-change-the-game-for-the-whole-team"), NamedTextColor.DARK_GRAY));
        return playerHead(viewer,
                Component.text(GuiConfig.text("daily.menus.game-selection-screen.copy.party"), NamedTextColor.AQUA).decorate(TextDecoration.BOLD), lore, party != null);
    }

    private ItemStack overviewItem(int games, int totalQueued, int activeGames) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(games + GuiConfig.text("daily.menus.game-selection-screen.copy.open-games"), NamedTextColor.WHITE)
                .append(Component.text(GuiConfig.text("common.separator") + totalQueued + GuiConfig.text("daily.menus.game-selection-screen.copy.people-are-waiting"), NamedTextColor.GRAY)));
        lore.add(Component.text(activeGames == 0 ? GuiConfig.text("daily.menus.game-selection-screen.copy.all-current-games-are-eligible") : activeGames + GuiConfig.text("daily.menus.game-selection-screen.copy.items-are-in-play"),
                activeGames == 0 ? NamedTextColor.GREEN : NamedTextColor.GOLD));
        lore.add(Component.text(GuiConfig.text("daily.menus.game-selection-screen.copy.no-need-to-form-a-team-in-advance-automatically-form-a-team-after-matching-is-completed"), NamedTextColor.DARK_GRAY));
        lore.add(Component.text(GuiConfig.text("daily.menus.game-selection-screen.copy.click-on-an-item-to-join-or-change-it-for-the-entire-team"), NamedTextColor.YELLOW));
        return item(Material.NETHER_STAR,
                Component.text(GuiConfig.text("daily.menus.game-selection-screen.copy.match-lobby"), NamedTextColor.GREEN).decorate(TextDecoration.BOLD),
                lore, false);
    }

    private ItemStack statsItem(UUID viewer) {
        DailyStatSnapshot stat = daily.statsManager().stat(viewer, null);
        return item(Material.WRITABLE_BOOK,
                Component.text(GuiConfig.text("daily.menus.game-selection-screen.copy.my-record"), NamedTextColor.LIGHT_PURPLE).decorate(TextDecoration.BOLD),
                List.of(
                        Component.text(GuiConfig.text("daily.menus.game-selection-screen.copy.number-of-sessions"), NamedTextColor.GRAY).append(Component.text(stat.gamesPlayed(), NamedTextColor.WHITE)),
                        Component.text(GuiConfig.text("daily.menus.game-selection-screen.copy.each-game-s-results-are-recorded-independently-points-are-not-inherited"), NamedTextColor.DARK_GRAY)
                ), false);
    }

    private ItemStack gameItem(UUID viewer, GameTypeEnum game, DailyRules rules) {
        GameStyle style = STYLES.getOrDefault(game,
                new GameStyle(Material.PAPER, NamedTextColor.WHITE, GuiConfig.text("daily.menus.game-selection-screen.copy.game-items"), GuiConfig.text("daily.menus.game-selection-screen.copy.complete-challenges-with-other-players")));
        int queued = daily.queueSize(game);
        int queuedGroups = daily.queueGroupCount(game);
        int countdown = daily.queueCountdown(game);
        int filled = Math.min(BAR_LENGTH, (int) Math.round(queued * BAR_LENGTH / (double) rules.minPlayers()));
        boolean selected = daily.isSelected(viewer, game);
        DailySession active = daily.activeSession(game);
        boolean running = active != null;
        int activeSessions = daily.activeSessionCount(game);
        int availableSlots = daily.availableSlotCount(game);
        int needed = Math.max(0, rules.minPlayers() - queued);
        DailyStatSnapshot stat = daily.statsManager().stat(viewer, game);
        List<String> maps = mapNames(game);

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(style.category, NamedTextColor.DARK_GRAY));
        lore.add(Component.text(style.description, NamedTextColor.GRAY));
        lore.add(Component.empty());
        if (running) {
            lore.add(Component.text(GuiConfig.text("daily.menus.game-selection-screen.copy.game-state"), NamedTextColor.GRAY)
                    .append(Component.text(active.instance().getGameStageEnum().toString(), NamedTextColor.GOLD)));
            lore.add(Component.text(GuiConfig.text("daily.menus.game-selection-screen.copy.map-in-progress"), NamedTextColor.GRAY)
                    .append(Component.text(active.map(), NamedTextColor.AQUA))
                    .append(activeSessions > 1
                            ? Component.text(GuiConfig.text("common.separator") + activeSessions + GuiConfig.text("daily.menus.game-selection-screen.copy.match-count-suffix"), NamedTextColor.DARK_GRAY)
                            : Component.empty()));
            lore.add(Component.text(GuiConfig.text("daily.menus.game-selection-screen.copy.instance-capacity"), NamedTextColor.GRAY)
                    .append(Component.text(availableSlots > 0
                                    ? availableSlots + GuiConfig.text("daily.menus.game-selection-screen.copy.idle-instances")
                                    : GuiConfig.text("daily.menus.game-selection-screen.copy.all-instances-are-in-use"),
                            availableSlots > 0 ? NamedTextColor.GREEN : NamedTextColor.GOLD)));
        } else {
            lore.add(Component.text(GuiConfig.text("daily.menus.game-selection-screen.copy.game-state"), NamedTextColor.GRAY)
                    .append(Component.text(availableSlots > 0 ? GuiConfig.text("daily.menus.game-selection-screen.copy.can-be-added-to-the-match") : GuiConfig.text("daily.menus.game-selection-screen.copy.waiting-for-idle-instance"),
                            availableSlots > 0 ? NamedTextColor.GREEN : NamedTextColor.GOLD)));
        }
        lore.add(Component.empty());
        lore.add(Component.text("■".repeat(filled), countdown >= 0 ? NamedTextColor.GOLD : style.color)
                .append(Component.text("□".repeat(BAR_LENGTH - filled), NamedTextColor.DARK_GRAY)));
        lore.add(Component.text(queued + "/" + rules.minPlayers() + GuiConfig.text("daily.menus.game-selection-screen.copy.the-number-of-people-reaches-the-opening-number"), NamedTextColor.WHITE));
        lore.add(countdown >= 0
                ? Component.text(GuiConfig.text("daily.menus.game-selection-screen.copy.about-to-depart"), NamedTextColor.GRAY).append(Component.text(countdown + GuiConfig.text("daily.menus.game-selection-screen.copy.seconds"), NamedTextColor.GOLD))
                : queued > 0 && queuedGroups < 2 && !DailyManager.allowsSoloQueue(game)
                ? Component.text(GuiConfig.text("daily.menus.game-selection-screen.copy.requires-another-player-or-party"), NamedTextColor.YELLOW)
                : needed == 0 ? Component.text(GuiConfig.text("daily.menus.game-selection-screen.copy.preparing-for-countdown"), NamedTextColor.GREEN)
                : Component.text(GuiConfig.text("daily.menus.game-selection-screen.copy.still-need") + needed + GuiConfig.text("daily.menus.game-selection-screen.copy.fellow-traveler"), NamedTextColor.YELLOW));
        lore.add(Component.empty());
        lore.add(Component.text(GuiConfig.text("daily.menus.game-selection-screen.copy.team-rules"), NamedTextColor.GOLD));
        lore.add(Component.text(GuiConfig.text("daily.menus.game-selection-screen.copy.each-team-has-at-most") + rules.teamSize() + GuiConfig.text("daily.menus.game-selection-screen.copy.people-most") + rules.teams() + GuiConfig.text("daily.menus.game-selection-screen.copy.team-count-suffix"), NamedTextColor.WHITE));
        if (!DailyManager.allowsSoloQueue(game)) {
            lore.add(Component.text(GuiConfig.text("daily.menus.game-selection-screen.copy.requires-at-least-2-independent-players-or-companion-squads"), NamedTextColor.GRAY));
        }
        lore.add(Component.text(GuiConfig.text("daily.menus.game-selection-screen.copy.single-field-capacity") + rules.minPlayers() + "–" + rules.maxPlayers() + GuiConfig.text("daily.menus.game-selection-screen.copy.player-count-suffix"), NamedTextColor.GRAY));
        lore.add(Component.text(GuiConfig.text("daily.menus.game-selection-screen.copy.map"), NamedTextColor.GRAY)
                .append(Component.text(maps.isEmpty() ? GuiConfig.text("daily.menus.game-selection-screen.copy.waiting-place") : String.join("、", maps),
                        maps.isEmpty() ? NamedTextColor.DARK_GRAY : NamedTextColor.AQUA)));
        lore.add(Component.empty());
        lore.add(Component.text(GuiConfig.text("daily.menus.game-selection-screen.copy.my-results"), NamedTextColor.GRAY)
                .append(Component.text(stat.gamesPlayed() + GuiConfig.text("daily.menus.game-selection-screen.copy.match-count-suffix"), NamedTextColor.WHITE)));
        lore.add(Component.empty());
        if (selected && availableSlots <= 0) {
            lore.add(Component.text(GuiConfig.text("daily.menus.game-selection-screen.copy.already-in-the-waiting-queue"), NamedTextColor.AQUA).decorate(TextDecoration.BOLD));
            lore.add(Component.text(GuiConfig.text("daily.menus.game-selection-screen.copy.matching-will-be-automatically-resumed-after-the-instance-becomes-idle"), NamedTextColor.GOLD));
        } else if (selected) {
            lore.add(Component.text(GuiConfig.text("daily.menus.game-selection-screen.copy.current-selection"), NamedTextColor.AQUA).decorate(TextDecoration.BOLD));
            lore.add(Component.text(GuiConfig.text("daily.menus.game-selection-screen.copy.clicking-again-will-not-join-again"), NamedTextColor.DARK_GRAY));
        } else {
            lore.add(Component.text(availableSlots > 0 ? GuiConfig.text("daily.menus.game-selection-screen.copy.click-to-join-the-match") : GuiConfig.text("daily.menus.game-selection-screen.copy.click-to-join-the-waiting-room"),
                    availableSlots > 0 ? NamedTextColor.GREEN : NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
            lore.add(Component.text(availableSlots > 0
                    ? GuiConfig.text("daily.menus.game-selection-screen.copy.the-party-will-be-selected-simultaneously")
                    : GuiConfig.text("daily.menus.game-selection-screen.copy.the-countdown-starts-automatically-after-the-instance-becomes-idle"), NamedTextColor.DARK_GRAY));
        }
        Component marker = selected ? Component.text("✓ ", NamedTextColor.AQUA)
                : running ? Component.text("● ", NamedTextColor.GOLD) : Component.empty();
        Component name = marker.append(Component.text(game.toString(), style.color))
                .decorate(TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false);
        return item(style.material, name, lore, selected);
    }

    private ItemStack leaderboardItem(UUID viewer) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(GuiConfig.text("daily.menus.game-selection-screen.copy.click-to-view-game-data-and-time-records"), NamedTextColor.YELLOW));
        return item(Material.GOLD_INGOT,
                Component.text(GuiConfig.text("daily.menus.game-selection-screen.copy.ranking-list"), NamedTextColor.GOLD).decorate(TextDecoration.BOLD), lore, false);
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

    private static ItemStack item(Material material, Component name, List<Component> lore, boolean glint) {
        return ink.ziip.championshipscore.api.gui.GuiMenu.item(material, name, lore, glint);
    }

    private static ItemStack playerHead(UUID owner, Component name, List<Component> lore, boolean glint) {
        return ink.ziip.championshipscore.api.gui.GuiMenu.playerHead(owner, name, lore, glint);
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
                GuiConfig.text("daily.menus.game-selection-screen.copy.explore-teamwork"), GuiConfig.text("daily.menus.game-selection-screen.copy.complete-shared-bingo-tasks-strive-for-connections-and-full-collection")));
        styles.put(GameTypeEnum.AceRace, new GameStyle(Material.ELYTRA, NamedTextColor.AQUA,
                GuiConfig.text("daily.menus.game-selection-screen.copy.racing-independent-track-instance"), GuiConfig.text("daily.menus.game-selection-screen.copy.controlling-the-elytra-and-trident-challenge-the-fastest-complete-lap")));
        styles.put(GameTypeEnum.DragonEggCarnival, new GameStyle(Material.DRAGON_EGG, NamedTextColor.LIGHT_PURPLE,
                GuiConfig.text("daily.menus.game-selection-screen.copy.confrontation-original-end-dragon-battle"), GuiConfig.text("daily.menus.game-selection-screen.copy.compete-for-three-end-game-progressions-be-the-first-to-complete-two-of-them")));
        styles.put(GameTypeEnum.ParkourWarrior, new GameStyle(Material.LEATHER_BOOTS, NamedTextColor.GREEN,
                GuiConfig.text("daily.menus.game-selection-screen.copy.parkour-challenge-multi-difficulty-checkpoints"), GuiConfig.text("daily.menus.game-selection-screen.copy.challenge-checkpoints-of-each-difficulty-and-reach-the-finish-first")));
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
