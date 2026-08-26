package ink.ziip.championshipscore.api.daily;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseManager;
import ink.ziip.championshipscore.api.daily.adapter.AceRaceDailyGameAdapter;
import ink.ziip.championshipscore.api.daily.adapter.BingoDailyGameAdapter;
import ink.ziip.championshipscore.api.daily.adapter.DragonEggCarnivalDailyGameAdapter;
import ink.ziip.championshipscore.api.daily.adapter.ParkourWarriorDailyGameAdapter;
import ink.ziip.championshipscore.api.game.instance.BaseGameInstance;
import ink.ziip.championshipscore.api.game.instance.multiteam.BaseMultiTeamGameInstance;
import ink.ziip.championshipscore.api.game.bingo.execution.RemoteBingoInstance;
import ink.ziip.championshipscore.api.object.game.GameRunMode;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.object.game.ServerMode;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.CCConfig;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Public-play orchestration. Existing game instances remain the authority for game rules. */
public final class DailyManager extends BaseManager {
    /** Disconnect grace period for DAILY participants. */
    static final long DISCONNECT_GRACE_MILLIS = 60_000L;
    private static final String[] TEAM_COLORS = {
            "RED", "GREEN", "BLUE", "YELLOW", "CYAN", "PURPLE", "ORANGE", "WHITE",
            "LIME", "PINK", "LIGHT_BLUE", "MAGENTA", "GRAY", "BLACK", "BROWN", "LIGHT_GRAY"
    };
    private static final String[] TEAM_CODES = {
            "#ff5555", "#55ff55", "#5555ff", "#ffff55", "#55ffff", "#aa00aa", "#ffaa00", "#ffffff",
            "#00aa00", "#ff55ff", "#00aaaa", "#aa0000", "#aaaaaa", "#000000", "#555555", "#aaaaaa"
    };
    private static final String[] TEAM_NAMES = {
            "红", "绿", "蓝", "黄", "青", "紫", "橙", "白",
            "黄绿", "粉红", "淡蓝", "品红", "灰", "黑", "棕", "浅灰"
    };

    private final Map<GameTypeEnum, DailyGameAdapter> adapters = new EnumMap<>(GameTypeEnum.class);
    private final Map<GameTypeEnum, DailyQueue> queues = new EnumMap<>(GameTypeEnum.class);
    private final Map<UUID, GameTypeEnum> queueByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, DailySession> sessionByPlayer = new ConcurrentHashMap<>();
    private final Map<BaseGameInstance, DailySession> sessionByInstance = new ConcurrentHashMap<>();
    /** Absolute expiry for a participant who disconnected while their DAILY match stayed active. */
    private final Map<UUID, Long> disconnectedPlayers = new ConcurrentHashMap<>();
    /** Starts when every remaining participant in an instance is offline. */
    private final Map<BaseGameInstance, Long> allPlayersOfflineSince = new ConcurrentHashMap<>();
    private final Map<GameTypeEnum, PendingDailyStart> pendingStarts = new EnumMap<>(GameTypeEnum.class);
    private final Set<BaseGameInstance> settlingInstances = ConcurrentHashMap.newKeySet();
    private final Map<UUID, DailyPlayerSnapshot> snapshots = new ConcurrentHashMap<>();
    private final Map<GameTypeEnum, BossBar> waitingBars = new EnumMap<>(GameTypeEnum.class);
    private final DailyPartyManager partyManager = new DailyPartyManager(this);
    private final DailyLobbyMenu lobbyMenu;
    private final DailyGameMenu matchMenu;
    private final DailyStatsMenu statsMenu;
    private final DailyPartyMenu partyMenu;
    private final DailyLeaderboardMenu leaderboardMenu;
    private final DailyListener listener;
    private final DailyStatsManager statsManager;
    private final PlayerIsolationService isolationService;
    private final DailyBingoVoteController bingoVote;
    private volatile ServerMode serverMode = ServerMode.CHAMPIONSHIP;
    private BukkitTask tickTask;

    private record PendingDailyStart(DailyQueue queue, DailyRules rules,
                                     List<DailyQueue.Group> selected,
                                     List<ChampionshipTeam> teams) {
    }

    public DailyManager(ChampionshipsCore plugin, DailyStatsManager statsManager) {
        super(plugin);
        lobbyMenu = new DailyLobbyMenu(this);
        matchMenu = new DailyGameMenu(plugin, this);
        statsMenu = new DailyStatsMenu(this);
        partyMenu = new DailyPartyMenu(this);
        leaderboardMenu = new DailyLeaderboardMenu(plugin, this, statsManager);
        listener = new DailyListener(plugin, this);
        this.statsManager = statsManager;
        isolationService = new PlayerIsolationService(plugin);
        bingoVote = new DailyBingoVoteController(plugin, this);
    }

    @Override
    public void load() {
        serverMode = ServerMode.parse(CCConfig.MODE);
        adapters.put(GameTypeEnum.Bingo, new BingoDailyGameAdapter(plugin));
        adapters.put(GameTypeEnum.AceRace, new AceRaceDailyGameAdapter(plugin));
        adapters.put(GameTypeEnum.DragonEggCarnival, new DragonEggCarnivalDailyGameAdapter(plugin));
        adapters.put(GameTypeEnum.ParkourWarrior, new ParkourWarriorDailyGameAdapter(plugin));
        for (GameTypeEnum game : enabledGames()) queues.put(game, new DailyQueue(game));
        listener.register();
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, (Runnable) this::tick, 20L, 20L);
        rebuildSnapshots();
        for (Player player : Bukkit.getOnlinePlayers()) syncLobbyItem(player);
    }

    @Override
    public void unload() {
        if (tickTask != null) tickTask.cancel();
        tickTask = null;
        bingoVote.cancel();
        closeOpenMenus();
        listener.unRegister();
        for (DailySession session : Set.copyOf(sessionByInstance.values())) cleanup(session);
        for (PendingDailyStart pending : List.copyOf(pendingStarts.values()))
            pending.teams().forEach(plugin.getTeamManager()::removeTransientTeam);
        pendingStarts.clear();
        queues.clear();
        queueByPlayer.clear();
        disconnectedPlayers.clear();
        allPlayersOfflineSince.clear();
        snapshots.clear();
        partyManager.clear();
        isolationService.clear();
        plugin.getTeamManager().restoreDailyLobbyIdentities();
        waitingBars.values().forEach(BossBar::removeAll);
        waitingBars.clear();
        for (Player player : Bukkit.getOnlinePlayers()) DailyLobbyItem.take(player);
    }

    public ServerMode serverMode() { return serverMode; }
    public boolean isDailyLobby() { return serverMode == ServerMode.DAILY; }
    public DailyPartyManager partyManager() { return partyManager; }
    public DailyStatsManager statsManager() { return statsManager; }
    DailyLobbyMenu lobbyMenu() { return lobbyMenu; }
    DailyGameMenu matchMenu() { return matchMenu; }
    DailyStatsMenu statsMenu() { return statsMenu; }
    DailyPartyMenu partyMenu() { return partyMenu; }
    DailyLeaderboardMenu leaderboardMenu() { return leaderboardMenu; }
    public PlayerIsolationService isolation() { return isolationService; }
    DailyBingoVoteController bingoVote() { return bingoVote; }
    public @NotNull java.util.concurrent.CompletionStage<ink.ziip.championshipscore.protocol.BingoVariantRules>
    beginBingoVote(@NotNull List<ChampionshipTeam> teams) {
        return bingoVote.begin(teams);
    }

    boolean reopenBingoVote(@NotNull Player player) {
        return bingoVote.reopen(player);
    }

    public synchronized void switchMode(@NotNull ServerMode next) {
        if (serverMode == next) return;
        serverMode = next;
        CCConfig.MODE = next.name();
        plugin.getConfigurationManager().getCCConfig().saveOptions();
        if (next == ServerMode.CHAMPIONSHIP) clearQueues("服务器已切换到正式比赛模式");
        if (next != ServerMode.DAILY) closeOpenMenus();
        for (Player player : Bukkit.getOnlinePlayers()) syncLobbyItem(player);
        rebuildSnapshots();
        if (plugin.getSidebarManager() != null) plugin.getSidebarManager().invalidateAll();
    }

    /** Applies hot-reloadable DAILY mode/rules without re-registering its listener or tick task. */
    public synchronized void reloadConfiguration() {
        ServerMode configuredMode = ServerMode.parse(CCConfig.MODE);
        Set<GameTypeEnum> configuredGames = enabledGames();
        boolean gamesChanged = !queues.keySet().equals(configuredGames);
        if (gamesChanged) {
            clearQueues("DAILY 游戏配置已重载，请重新加入匹配");
            queues.clear();
            for (GameTypeEnum game : configuredGames) queues.put(game, new DailyQueue(game));
        }
        if (serverMode != configuredMode) {
            serverMode = configuredMode;
            if (configuredMode == ServerMode.CHAMPIONSHIP) clearQueues("服务器已切换到正式比赛模式");
            if (configuredMode != ServerMode.DAILY) closeOpenMenus();
        }
        rebuildSnapshots();
        for (Player player : Bukkit.getOnlinePlayers()) syncLobbyItem(player);
    }

    public Set<GameTypeEnum> enabledGames() {
        EnumSet<GameTypeEnum> enabled = EnumSet.noneOf(GameTypeEnum.class);
        List<String> configured = CCConfig.DAILY_ENABLED_GAMES == null ? List.of() : CCConfig.DAILY_ENABLED_GAMES;
        for (String name : configured) {
            if (name == null) continue;
            for (GameTypeEnum game : adapters.keySet()) {
                if (game.name().equalsIgnoreCase(name.trim()) && plugin.getGameManager().isGameEnabled(game)) {
                    enabled.add(game);
                }
            }
        }
        return Set.copyOf(enabled);
    }

    public @Nullable DailyRules rules(GameTypeEnum game) {
        DailyGameAdapter adapter = adapters.get(game);
        return adapter == null || !enabledGames().contains(game) ? null : adapter.rules();
    }

    /** Ace Race and Parkour Warrior are valid solo DAILY matches. */
    public static boolean allowsSoloQueue(@NotNull GameTypeEnum game) {
        return game == GameTypeEnum.AceRace || game == GameTypeEnum.ParkourWarrior;
    }

    public void openMenu(@NotNull Player player) {
        if (!isDailyLobby()) {
            message(player, MessageConfig.DAILY_UNAVAILABLE);
            return;
        }
        if (sessionByPlayer.containsKey(player.getUniqueId())
                || plugin.getGameManager().getBasePlayerArea(player.getUniqueId()) != null
                || plugin.getGameManager().getPlayerSpectatorStatus(player.getUniqueId()) != null
                || plugin.getGameManager().isWaitingForNextRound(player.getUniqueId())) {
            message(player, MessageConfig.DAILY_ALREADY_PLAYING);
            return;
        }
        lobbyMenu.open(player);
    }

    void openMatchMenu(@NotNull Player player) {
        if (!isDailyLobby()) {
            message(player, MessageConfig.DAILY_UNAVAILABLE);
            return;
        }
        matchMenu.open(player);
    }

    void openStatsMenu(@NotNull Player player) {
        if (!isDailyLobby()) {
            message(player, MessageConfig.DAILY_UNAVAILABLE);
            return;
        }
        statsMenu.open(player);
    }

    void openPartyMenu(@NotNull Player player) {
        if (!isDailyLobby()) {
            message(player, MessageConfig.DAILY_UNAVAILABLE);
            return;
        }
        partyMenu.open(player);
    }

    void openSpectateMenu(@NotNull Player player) {
        if (!isDailyLobby()) {
            message(player, MessageConfig.DAILY_UNAVAILABLE);
            return;
        }
        if (isQueued(player.getUniqueId())) {
            message(player, "匹配中无法旁观，请先在匹配菜单中取消匹配");
            return;
        }
        if (plugin.getGameManager().canManuallySpectate(player)) {
            plugin.getGameManager().openSpectateMenu(player);
        }
    }

    public void openLeaderboard(@NotNull Player player) {
        leaderboardMenu.open(player);
    }

    /** Serializes Party selection and queue migration. Any member may call this method. */
    public synchronized boolean selectGame(@NotNull Player requester, @NotNull GameTypeEnum game) {
        if (!isDailyLobby()) {
            message(requester, MessageConfig.DAILY_UNAVAILABLE);
            return false;
        }
        DailyRules targetRules = rules(game);
        DailyQueue target = queues.computeIfAbsent(game, DailyQueue::new);
        if (targetRules == null) {
            message(requester, MessageConfig.DAILY_GAME_UNAVAILABLE);
            return false;
        }

        DailyParty party = partyManager.getParty(requester.getUniqueId());
        Set<UUID> joining = party == null ? Set.of(requester.getUniqueId()) : party.members();
        UUID groupId = party == null ? requester.getUniqueId() : party.id();
        if (joining.size() > targetRules.teamSize()) {
            message(requester, replace(MessageConfig.DAILY_PARTY_TOO_LARGE,
                    "%limit%", Integer.toString(targetRules.teamSize())));
            return false;
        }
        for (UUID uuid : joining) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || sessionByPlayer.containsKey(uuid)
                    || plugin.getGameManager().getBasePlayerArea(uuid) != null
                    || plugin.getGameManager().isWaitingForNextRound(uuid)) {
                message(requester, MessageConfig.DAILY_PARTY_MEMBER_UNAVAILABLE);
                return false;
            }
        }

        GameTypeEnum previousGame = queueByPlayer.get(requester.getUniqueId());
        if (previousGame == game && joining.stream().allMatch(uuid -> queueByPlayer.get(uuid) == game)) {
            message(requester, replace(MessageConfig.DAILY_ALREADY_QUEUED, "%game%", game.toString()));
            return true;
        }
        if (!target.canAdd(joining, targetRules)) {
            message(requester, MessageConfig.DAILY_QUEUE_UNAVAILABLE);
            return false;
        }

        DailyQueue previous = previousGame == null ? null : queues.get(previousGame);
        if (previous != null) previous.removePlayer(requester.getUniqueId());
        for (UUID uuid : joining) queueByPlayer.remove(uuid);

        if (!target.add(groupId, joining, targetRules)) {
            if (previous != null) {
                DailyRules previousRules = rules(previousGame);
                if (previousRules != null) previous.add(groupId, joining, previousRules);
                for (UUID uuid : joining) queueByPlayer.put(uuid, previousGame);
            }
            message(requester, MessageConfig.DAILY_QUEUE_MIGRATION_FAILED);
            return false;
        }
        if (party != null) party.select(game);
        for (UUID uuid : joining) {
            queueByPlayer.put(uuid, game);
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                prepareWaitingPlayer(player);
                message(player, replace(MessageConfig.DAILY_QUEUE_SELECTED,
                        "%player%", requester.getName(), "%game%", game.toString()));
            }
        }
        rebuildSnapshots();
        refreshOpenPartyMenus(joining);
        if (plugin.getSidebarManager() != null) plugin.getSidebarManager().invalidateAll();
        return true;
    }

    public synchronized boolean leaveQueue(UUID player, boolean notify) {
        GameTypeEnum game = queueByPlayer.get(player);
        if (game == null) return false;
        DailyQueue queue = queues.get(game);
        Set<UUID> removed = queue == null ? Set.of(player) : queue.removePlayer(player);
        if (removed.isEmpty()) removed = Set.of(player);
        for (UUID uuid : removed) {
            queueByPlayer.remove(uuid);
            if (notify) {
                Player online = Bukkit.getPlayer(uuid);
                if (online != null) message(online, MessageConfig.DAILY_QUEUE_LEFT);
            }
            Player online = Bukkit.getPlayer(uuid);
            if (online != null) syncLobbyItem(online);
        }
        if (queue != null) refreshWaitingBar(queue, rules(game));
        rebuildSnapshots();
        return true;
    }

    /** Leaves either matchmaking or the live free-play session; Parties are always indivisible. */
    public synchronized boolean leavePlay(@NotNull UUID requester) {
        if (isQueued(requester)) return leaveQueue(requester, true);
        DailySession session = sessionByPlayer.get(requester);
        if (session == null) return false;
        Set<UUID> leaving = leaveGroup(session, requester);
        return detachActivePlayers(session, leaving, true);
    }

    /** Applies a leave request originating on the remote Bingo worker. */
    public synchronized @NotNull Set<UUID> leaveActiveFromRemote(
            @NotNull RemoteBingoInstance instance, @NotNull UUID requester) {
        DailySession session = sessionByInstance.get(instance);
        if (session == null) return Set.of();
        Set<UUID> leaving = leaveGroup(session, requester);
        return detachActivePlayers(session, leaving, false) ? leaving : Set.of();
    }

    private Set<UUID> leaveGroup(DailySession session, UUID requester) {
        DailyParty party = partyManager.getParty(requester);
        Set<UUID> candidates = party == null ? Set.of(requester) : party.members();
        Set<UUID> leaving = new LinkedHashSet<>();
        for (UUID candidate : candidates) if (session.players().contains(candidate)) leaving.add(candidate);
        return Set.copyOf(leaving);
    }

    private boolean detachActivePlayers(DailySession session, Set<UUID> leaving, boolean notifyRemote) {
        if (leaving.isEmpty()) return false;
        session.removePlayers(leaving);
        for (ChampionshipTeam team : session.teams())
            plugin.getTeamManager().removeTransientMembers(team, leaving);
        if (session.instance() instanceof BaseMultiTeamGameInstance multiTeam)
            multiTeam.removeRuntimePlayers(leaving);
        plugin.getGameManager().releaseInstancePlayers(session.instance(), leaving);
        for (UUID uuid : leaving) {
            sessionByPlayer.remove(uuid, session);
            disconnectedPlayers.remove(uuid);
            isolationService.detach(uuid);
            Player online = Bukkit.getPlayer(uuid);
            if (online != null) syncLobbyItem(online);
        }
        broadcast(leaving, replace(MessageConfig.DAILY_PLAY_LEFT,
                "%players%", Integer.toString(leaving.size())));
        rebuildSnapshots();

        if (!session.isEmpty()) allPlayersOfflineSince.remove(session.instance());

        if (session.instance() instanceof RemoteBingoInstance remote) {
            if (notifyRemote) plugin.getRemoteBingoManager().removeDailyPlayers(remote, leaving);
        } else if (session.isEmpty()) {
            session.instance().endGameFinally();
        }
        return true;
    }

    synchronized void pauseParty(DailyParty party, String reason) {
        UUID member = party.members().stream().findFirst().orElse(null);
        if (member == null || !leaveQueue(member, false)) return;
        broadcast(party.members(), "&e" + reason + "。");
    }

    public void handleQuit(UUID player) {
        DailySession session = sessionByPlayer.get(player);
        if (session != null && !(session.instance() instanceof RemoteBingoInstance))
            disconnectedPlayers.put(player, System.currentTimeMillis());
        if (partyManager.getParty(player) == null) leaveQueue(player, false);
        Bukkit.getScheduler().runTask(plugin, () -> partyManager.handleOffline(player));
    }

    public void handleJoin(UUID player) {
        disconnectedPlayers.remove(player);
        Bukkit.getScheduler().runTask(plugin, () -> {
            rebuildSnapshots();
            Player online = Bukkit.getPlayer(player);
            if (online != null) {
                syncLobbyIdentity(online);
                syncLobbyItem(online);
                if (plugin.getSidebarManager() != null) plugin.getSidebarManager().invalidate(online);
            }
        });
    }

    public boolean isQueued(UUID player) { return queueByPlayer.containsKey(player); }
    public boolean isSelected(UUID player, GameTypeEnum game) { return queueByPlayer.get(player) == game; }
    public int queueSize(GameTypeEnum game) {
        DailyQueue queue = queues.get(game);
        return queue == null ? 0 : queue.size();
    }
    public int queueCountdown(GameTypeEnum game) {
        DailyQueue queue = queues.get(game);
        return queue == null ? -1 : queue.countdown();
    }
    public int queueGroupCount(GameTypeEnum game) {
        DailyQueue queue = queues.get(game);
        return queue == null ? 0 : queue.groupCount();
    }
    public boolean isGameRunning(@NotNull GameTypeEnum game) {
        return sessionByInstance.values().stream().anyMatch(session -> session.game() == game);
    }
    public int activeSessionCount(@NotNull GameTypeEnum game) {
        return (int) sessionByInstance.values().stream().filter(session -> session.game() == game).count();
    }
    public int availableSlotCount(@NotNull GameTypeEnum game) {
        DailyGameAdapter adapter = adapters.get(game);
        return adapter == null ? 0 : Math.max(0, adapter.availableSlots());
    }
    public @Nullable DailySession activeSession(@NotNull GameTypeEnum game) {
        return sessionByInstance.values().stream().filter(session -> session.game() == game)
                .min(Comparator.comparingLong(DailySession::startedAtMillis)).orElse(null);
    }
    public @Nullable DailySession session(UUID player) { return sessionByPlayer.get(player); }
    public @Nullable DailySession session(BaseGameInstance instance) { return sessionByInstance.get(instance); }
    public void attachSpectator(BaseGameInstance instance, UUID player) {
        DailySession session = sessionByInstance.get(instance);
        if (session != null) isolationService.attach(player, session.matchId());
    }
    public void detachSpectator(UUID player) { isolationService.detach(player); }
    public DailyPlayerSnapshot snapshot(UUID player) {
        return snapshots.getOrDefault(player, DailyPlayerSnapshot.empty(modeDisplay()));
    }

    Set<String> knownMaps(@NotNull GameTypeEnum game) {
        Set<String> maps = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        if (game == GameTypeEnum.Bingo) maps.addAll(plugin.getGameManager().getBingoManager().getAreaNameList());
        else if (game == GameTypeEnum.AceRace) maps.addAll(plugin.getGameManager().getAceRaceManager().getAreaNameList());
        else if (game == GameTypeEnum.DragonEggCarnival)
            maps.addAll(plugin.getGameManager().getDragonEggCarnivalManager().getAreaNameList());
        else if (game == GameTypeEnum.ParkourWarrior)
            maps.addAll(plugin.getGameManager().getParkourWarriorManager().getAreaNameList());
        maps.addAll(statsManager.recordMaps(game));
        return Set.copyOf(maps);
    }

    /** Whether a player is currently eligible to receive the lobby entry item or join a party. */
    boolean canJoinParty(@NotNull UUID player) {
        return isDailyLobby()
                && Bukkit.getPlayer(player) != null
                && !isQueued(player)
                && sessionByPlayer.get(player) == null
                && plugin.getGameManager().getBasePlayerArea(player) == null
                && plugin.getGameManager().getPlayerSpectatorStatus(player) == null
                && !plugin.getGameManager().isWaitingForNextRound(player);
    }

    /** Human-readable Party-menu state; {@code null} means the player can currently be invited. */
    @Nullable String partyUnavailableReason(@NotNull UUID player) {
        if (Bukkit.getPlayer(player) == null) return "已离线";
        if (partyManager.getParty(player) != null) return "已有同行小队";
        if (isQueued(player)) return "匹配中";
        if (sessionByPlayer.get(player) != null
                || plugin.getGameManager().getBasePlayerArea(player) != null) return "游戏中";
        if (plugin.getGameManager().getPlayerSpectatorStatus(player) != null) return "观战中";
        if (plugin.getGameManager().isWaitingForNextRound(player)) return "结算中";
        return isDailyLobby() ? null : "当前模式不可用";
    }

    private boolean canReceiveLobbyItem(@NotNull Player player) {
        UUID uuid = player.getUniqueId();
        return isDailyLobby()
                && sessionByPlayer.get(uuid) == null
                && plugin.getGameManager().getBasePlayerArea(uuid) == null
                && plugin.getGameManager().getPlayerSpectatorStatus(uuid) == null
                && !plugin.getGameManager().isWaitingForNextRound(uuid);
    }

    void syncLobbyItem(@NotNull Player player) {
        syncLobbyIdentity(player);
        if (canReceiveLobbyItem(player)) {
            if (!DailyLobbyItem.give(player)) message(player, "物品栏没有空位，暂时无法发放大厅菜单。");
        } else {
            DailyLobbyItem.take(player);
        }
    }

    /** Re-renders active DAILY inventories after a map identity or leaderboard change. */
    public void refreshOpenMenus() {
        lobbyMenu.refreshOpenMenus();
        matchMenu.refreshOpenMenus();
        statsMenu.refreshOpenMenus();
        partyMenu.refreshOpenMenus();
        leaderboardMenu.refreshOpenMenus();
    }

    private void closeOpenMenus() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Object holder = player.getOpenInventory().getTopInventory().getHolder();
            if (holder instanceof DailyLobbyMenu.LobbyHolder
                    || holder instanceof DailyGameMenu.MenuHolder
                    || holder instanceof DailyStatsMenu.StatsHolder
                    || holder instanceof DailyStatsMenu.DetailHolder
                    || holder instanceof DailyPartyMenu.PartyHolder
                    || holder instanceof DailyLeaderboardMenu.LeaderboardHolder) {
                player.closeInventory();
            }
        }
    }

    boolean isPartyInSession(@NotNull DailyParty party) {
        return party.members().stream().anyMatch(sessionByPlayer::containsKey);
    }

    private void tick() {
        for (DailyQueue queue : List.copyOf(queues.values())) tick(queue);
        for (BaseGameInstance instance : Set.copyOf(sessionByInstance.keySet())) {
            processDisconnectedPlayers(instance);
            if (instance.getGameStageEnum() == GameStageEnum.WAITING) {
                DailySession session = sessionByInstance.get(instance);
                if (session != null) cleanup(session);
                settlingInstances.remove(instance);
            }
        }
        rebuildSnapshots();
        for (Player player : Bukkit.getOnlinePlayers()) syncLobbyIdentity(player);
        refreshOpenMenus();
    }

    /** Applies the 60-second reconnect grace period to active DAILY participants. */
    private void processDisconnectedPlayers(@NotNull BaseGameInstance instance) {
        DailySession session = sessionByInstance.get(instance);
        if (session == null || session.isEmpty()
                || instance.getGameStageEnum() == GameStageEnum.END
                || instance.getGameStageEnum() == GameStageEnum.WAITING
                || settlingInstances.contains(instance)) {
            allPlayersOfflineSince.remove(instance);
            return;
        }

        if (instance instanceof RemoteBingoInstance remote) {
            processRemoteDisconnectedPlayers(remote, session);
            return;
        }

        long now = System.currentTimeMillis();
        Set<UUID> players = session.players();
        Set<UUID> offline = players.stream()
                .filter(uuid -> {
                    Player player = Bukkit.getPlayer(uuid);
                    return player == null || !player.isOnline();
                })
                .collect(java.util.stream.Collectors.toSet());

        if (offline.size() == players.size()) {
            long since = allPlayersOfflineSince.computeIfAbsent(instance, ignored -> now);
            if (now - since >= DISCONNECT_GRACE_MILLIS) abortAbandonedSession(session);
            return;
        }
        allPlayersOfflineSince.remove(instance);

        Set<UUID> expired = new LinkedHashSet<>();
        for (UUID uuid : offline) {
            Long disconnectedAt = disconnectedPlayers.get(uuid);
            if (disconnectedAt != null && now - disconnectedAt >= DISCONNECT_GRACE_MILLIS)
                expired.add(uuid);
        }
        if (!expired.isEmpty()) detachActivePlayers(session, expired, true);
    }

    /** Uses the worker heartbeat for remote Bingo; Core never hosts these players locally. */
    private void processRemoteDisconnectedPlayers(@NotNull RemoteBingoInstance instance,
                                                  @NotNull DailySession session) {
        if (!plugin.getRemoteBingoManager().allRemoteParticipantsOffline(instance)) {
            allPlayersOfflineSince.remove(instance);
            return;
        }

        long now = System.currentTimeMillis();
        long since = allPlayersOfflineSince.computeIfAbsent(instance, ignored -> now);
        if (now - since >= DISCONNECT_GRACE_MILLIS) abortAbandonedSession(session);
    }

    /** Aborts a DAILY instance whose complete roster failed to reconnect in time. */
    private void abortAbandonedSession(@NotNull DailySession session) {
        allPlayersOfflineSince.remove(session.instance());
        if (session.instance() instanceof RemoteBingoInstance remote) {
            // Publish the worker ABORT command and release Core ownership immediately on failure.
            plugin.getRemoteBingoManager().abortDailyDisconnected(remote);
            return;
        }

        // Do not use the normal leave path here: endGameFinally() alone still permits a game-specific
        // end handler to publish a result. abortAndReset() suppresses settlement and restores the
        // instance without awarding DAILY statistics.
        Set<UUID> leaving = session.players();
        session.removePlayers(leaving);
        for (ChampionshipTeam team : session.teams())
            plugin.getTeamManager().removeTransientMembers(team, leaving);
        if (session.instance() instanceof BaseMultiTeamGameInstance multiTeam)
            multiTeam.removeRuntimePlayers(leaving);
        plugin.getGameManager().releaseInstancePlayers(session.instance(), leaving);
        for (UUID uuid : leaving) {
            sessionByPlayer.remove(uuid, session);
            disconnectedPlayers.remove(uuid);
            isolationService.detach(uuid);
        }
        rebuildSnapshots();
        session.instance().abortAndReset().whenComplete((ignored, failure) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    DailySession pending = sessionByInstance.get(session.instance());
                    if (pending != null) cleanup(pending);
                }));
    }

    private void tick(DailyQueue queue) {
        for (UUID uuid : queue.players()) {
            if (Bukkit.getPlayer(uuid) == null
                    || plugin.getGameManager().getBasePlayerArea(uuid) != null
                    || plugin.getGameManager().isWaitingForNextRound(uuid)) {
                DailyParty party = partyManager.getParty(uuid);
                if (party != null) pauseParty(party, "有成员离线或进入其他游戏，排队已暂停");
                else leaveQueue(uuid, false);
            }
        }
        if (!isDailyLobby()) {
            queue.countdown(-1);
            clearWaitingBar(queue.game());
            return;
        }
        DailyRules rules = rules(queue.game());
        if (availableSlotCount(queue.game()) <= 0) {
            queue.countdown(-1);
            refreshWaitingBar(queue, rules);
            return;
        }
        if (rules == null || queue.size() < rules.minPlayers()
                || (!allowsSoloQueue(queue.game()) && queue.groupCount() < 2)) {
            queue.countdown(-1);
            refreshWaitingBar(queue, rules);
            return;
        }
        if (queue.countdown() < 0) {
            queue.countdown(rules.countdownSeconds());
            broadcast(queue.players(), replace(MessageConfig.DAILY_QUEUE_READY,
                    "%game%", queue.game().toString(), "%time%", Integer.toString(rules.countdownSeconds())));
            refreshWaitingBar(queue, rules);
            return;
        }
        int next = queue.countdown() - 1;
        queue.countdown(next);
        if (next <= 5 || next == 10 || next == 15) broadcast(queue.players(),
                replace(MessageConfig.DAILY_QUEUE_COUNTDOWN,
                        "%game%", queue.game().toString(), "%time%", Integer.toString(next)));
        refreshWaitingBar(queue, rules);
        if (next <= 0) start(queue, rules);
    }

    private void start(DailyQueue queue, DailyRules rules) {
        List<DailyQueue.Group> selected = queue.take(rules.maxPlayers());
        int playerCount = selected.stream().mapToInt(group -> group.players().size()).sum();
        if (playerCount < rules.minPlayers()
                || (!allowsSoloQueue(queue.game()) && selected.size() < 2)) {
            queue.restore(selected, rules);
            return;
        }
        List<Set<UUID>> allocations = allocate(selected, rules);
        if (allocations.isEmpty()) {
            queue.restore(selected, rules);
            broadcast(queue.players(), MessageConfig.DAILY_QUEUE_COMPOSITION_FAILED);
            return;
        }

        String token = UUID.randomUUID().toString().replace("-", "").substring(0, 6);
        List<ChampionshipTeam> teams = new ArrayList<>();
        try {
            for (int index = 0; index < allocations.size(); index++) {
                int color = index % TEAM_COLORS.length;
                String canonicalName = teamNameForColor(TEAM_COLORS[color]);
                String configuredName = replace(MessageConfig.DAILY_TEAM_NAME,
                        "%color%", TEAM_NAMES[color], "%number%", Integer.toString(index + 1));
                teams.add(plugin.getTeamManager().createTransientTeam("ccd" + token + index,
                        configuredName.equals(canonicalName) ? configuredName : canonicalName,
                        TEAM_COLORS[color], TEAM_CODES[color], allocations.get(index)));
            }
        } catch (RuntimeException exception) {
            teams.forEach(plugin.getTeamManager()::removeTransientTeam);
            queue.restore(selected, rules);
            plugin.getLogger().warning(Utils.formatModuleLog("Daily", "临时队伍", exception.getMessage()));
            return;
        }

        DailyGameAdapter adapter = adapters.get(queue.game());
        PendingDailyStart pending = new PendingDailyStart(queue, rules, List.copyOf(selected), List.copyOf(teams));
        if (adapter == null || pendingStarts.putIfAbsent(queue.game(), pending) != null) {
            teams.forEach(plugin.getTeamManager()::removeTransientTeam);
            queue.restore(selected, rules);
            return;
        }
        adapter.start(teams).whenComplete((started, failure) -> Bukkit.getScheduler().runTask(plugin,
                () -> finishStart(pending, started, failure)));
    }

    /**
     * DAILY teams are deliberately identified only by their assigned Minecraft colour. This keeps
     * public-match presentation separate from a player's persistent championship team.
     */
    public static @NotNull String teamNameForColor(@NotNull String colorName) {
        for (int index = 0; index < TEAM_COLORS.length; index++) {
            if (TEAM_COLORS[index].equalsIgnoreCase(colorName)) return TEAM_NAMES[index] + "队";
        }
        return colorName + "队";
    }

    private void finishStart(@NotNull PendingDailyStart pending,
                             @Nullable DailyGameAdapter.StartResult started,
                             @Nullable Throwable failure) {
        if (!pendingStarts.remove(pending.queue().game(), pending)) return;
        DailyQueue queue = pending.queue();
        DailyRules rules = pending.rules();
        List<ChampionshipTeam> teams = pending.teams();
        List<DailyQueue.Group> selected = pending.selected();
        if (failure != null) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    Utils.formatModuleLog("Daily", "启动", "异步启动失败=" + queue.game()), failure);
        }
        if (started == null || started.instance() == null) {
            teams.forEach(plugin.getTeamManager()::removeTransientTeam);
            if (queues.get(queue.game()) == queue) {
                queue.restore(selected, rules);
                queue.countdown(5);
                broadcast(queue.players(), MessageConfig.DAILY_QUEUE_NO_ARENA);
            }
            return;
        }

        Set<UUID> players = new LinkedHashSet<>();
        selected.forEach(group -> players.addAll(group.players()));
        DailySession session = new DailySession(UUID.randomUUID(), queue.game(), started.map(), started.instance(),
                teams, players, System.currentTimeMillis());
        sessionByInstance.put(started.instance(), session);
        isolationService.register(session);
        for (UUID player : players) {
            queueByPlayer.remove(player);
            sessionByPlayer.put(player, session);
            Player online = Bukkit.getPlayer(player);
            if (online != null) syncLobbyItem(online);
        }
        broadcast(players, replace(MessageConfig.DAILY_MATCH_ASSIGNED,
                "%map%", started.map(), "%instance%", Integer.toString(started.instance().getCopyIndex() + 1)));
        refreshWaitingBar(queue, rules);
        rebuildSnapshots();
    }

    /**
     * Allocates indivisible queue groups into the most balanced set of teams that fits the rules.
     *
     * <p>The old implementation selected a preferred team count first and then used a first-fit
     * placement. That made three solo players become a two-versus-one match even when three teams were
     * available, and could also strand a large party with a needlessly uneven set of opponents. We now
     * evaluate every feasible team count and choose the allocation with the smallest population spread;
     * a team count near two players per team only breaks ties. This keeps parties together while making
     * balance the primary invariant.</p>
     */
    static List<Set<UUID>> allocate(List<DailyQueue.Group> groups, DailyRules rules) {
        if (groups.isEmpty() || rules.teams() < 1) return List.of();
        if (groups.size() < 2 && rules.minPlayers() > 1) return List.of();
        int players = groups.stream().mapToInt(group -> group.players().size()).sum();
        int maximumTeams = Math.min(rules.teams(), groups.size());
        int preferredTeams = Math.max(2, (players + 1) / 2);
        preferredTeams = Math.min(maximumTeams, preferredTeams);

        List<DailyQueue.Group> ordered = new ArrayList<>(groups);
        Collections.shuffle(ordered);
        ordered.sort(Comparator.comparingInt((DailyQueue.Group group) -> group.players().size()).reversed());

        List<Set<UUID>> best = List.of();
        int bestSpread = Integer.MAX_VALUE;
        int bestDistance = Integer.MAX_VALUE;
        int bestTeamCount = Integer.MAX_VALUE;
        int minimumTeams = groups.size() < 2 ? 1 : 2;
        for (int teamCount = minimumTeams; teamCount <= maximumTeams; teamCount++) {
            List<Set<UUID>> allocations = allocate(ordered, rules.teamSize(), teamCount);
            if (allocations.isEmpty()) continue;
            int spread = populationSpread(allocations);
            int distance = Math.abs(teamCount - preferredTeams);
            if (spread < bestSpread
                    || (spread == bestSpread && distance < bestDistance)
                    || (spread == bestSpread && distance == bestDistance && teamCount < bestTeamCount)) {
                best = allocations;
                bestSpread = spread;
                bestDistance = distance;
                bestTeamCount = teamCount;
            }
        }
        return best;
    }

    /** Finds all distinct team-size states for one team count, retaining one assignment per state. */
    private static List<Set<UUID>> allocate(List<DailyQueue.Group> ordered, int teamSize, int teamCount) {
        List<LinkedHashSet<UUID>> empty = new ArrayList<>();
        for (int i = 0; i < teamCount; i++) empty.add(new LinkedHashSet<>());

        Map<List<Integer>, List<LinkedHashSet<UUID>>> states = new LinkedHashMap<>();
        states.put(zeroSizes(teamCount), empty);
        for (DailyQueue.Group group : ordered) {
            Map<List<Integer>, List<LinkedHashSet<UUID>>> next = new HashMap<>();
            for (Map.Entry<List<Integer>, List<LinkedHashSet<UUID>>> state : states.entrySet()) {
                List<Integer> sizes = state.getKey();
                for (int targetIndex = 0; targetIndex < teamCount; targetIndex++) {
                    // Teams with equal sizes are interchangeable; trying one avoids duplicate states.
                    if (targetIndex > 0 && sizes.get(targetIndex).equals(sizes.get(targetIndex - 1))) continue;
                    if (sizes.get(targetIndex) + group.players().size() > teamSize) continue;

                    List<LinkedHashSet<UUID>> candidate = copyTeams(state.getValue());
                    candidate.get(targetIndex).addAll(group.players());
                    candidate.sort(Comparator.comparingInt(Set::size));
                    List<Integer> candidateSizes = candidate.stream().map(Set::size).toList();
                    next.putIfAbsent(candidateSizes, candidate);
                }
            }
            if (next.isEmpty()) return List.of();
            states = next;
        }

        return states.values().stream()
                .filter(teams -> teams.stream().noneMatch(Set::isEmpty))
                .min(Comparator.comparingInt(DailyManager::populationSpread))
                .map(teams -> teams.stream().map(LinkedHashSet::new).map(team -> (Set<UUID>) team).toList())
                .orElse(List.of());
    }

    private static List<Integer> zeroSizes(int count) {
        return java.util.stream.IntStream.range(0, count).mapToObj(ignored -> 0).toList();
    }

    private static List<LinkedHashSet<UUID>> copyTeams(List<LinkedHashSet<UUID>> source) {
        List<LinkedHashSet<UUID>> copy = new ArrayList<>(source.size());
        for (LinkedHashSet<UUID> team : source) copy.add(new LinkedHashSet<>(team));
        return copy;
    }

    private static int populationSpread(List<? extends Set<UUID>> allocations) {
        int smallest = allocations.stream().mapToInt(Set::size).min().orElse(0);
        int largest = allocations.stream().mapToInt(Set::size).max().orElse(0);
        return largest - smallest;
    }

    /** Receives the normal game end event; formal/GAME instances are ignored. */
    public void finish(@NotNull BaseGameInstance instance) {
        if (instance.getRunMode() != GameRunMode.DAILY) return;
        DailySession session = sessionByInstance.get(instance);
        if (session == null) return;
        statsManager.recordMatch(session, instance.getPlayerPointsSnapshot());
        settlingInstances.add(instance);
        scheduleLobbyResync(instance, session);
    }

    /**
     * Safety net after the result-display window (~10s). Normally the 1s tick notices the instance
     * back in WAITING, cleans the session and re-gives the lobby menu; if that window is ever
     * missed, players would stand in the lobby without their menu until relogging. Each pass just
     * retries the same idempotent steps, so harmless when the normal path already ran.
     */
    private void scheduleLobbyResync(@NotNull BaseGameInstance instance, @NotNull DailySession session) {
        Set<UUID> players = Set.copyOf(session.players());
        for (long delayTicks : new long[]{320L, 420L}) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                DailySession pending = sessionByInstance.get(instance);
                if (pending != null && instance.getGameStageEnum() == GameStageEnum.WAITING)
                    cleanup(pending);
                for (UUID uuid : players) {
                    Player player = Bukkit.getPlayer(uuid);
                    if (player != null) syncLobbyItem(player);
                }
            }, delayTicks);
        }
    }

    /** Releases a DAILY reservation that failed before it could emit the normal end event. */
    public synchronized void abort(@NotNull BaseGameInstance instance) {
        DailySession session = sessionByInstance.get(instance);
        if (session == null) return;
        broadcast(session.players(), MessageConfig.DAILY_MATCH_ABORTED);
        cleanup(session);
    }

    private void cleanup(DailySession session) {
        Set<UUID> players = session.players();
        plugin.getLogger().info(Utils.formatModuleLog("Daily", "清理",
                "game=" + session.game() + " players=" + players.size()));
        isolationService.unregister(session);
        sessionByInstance.remove(session.instance(), session);
        settlingInstances.remove(session.instance());
        allPlayersOfflineSince.remove(session.instance());
        for (UUID uuid : players) {
            sessionByPlayer.remove(uuid, session);
            disconnectedPlayers.remove(uuid);
        }
        plugin.getTeamManager().removeTransientTeams(session.teams());
        rebuildSnapshots();
        for (UUID uuid : players) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) syncLobbyItem(player);
        }
    }

    /** Completes DAILY cleanup at the authoritative post-game return point, before the next lobby tick. */
    public void onInstanceReturnedToLobby(@NotNull BaseGameInstance instance) {
        DailySession session = sessionByInstance.get(instance);
        if (session != null) cleanup(session);
    }

    private void clearQueues(String reason) {
        Set<UUID> players = new HashSet<>(queueByPlayer.keySet());
        for (UUID player : List.copyOf(players)) leaveQueue(player, false);
        broadcast(players, "&e" + reason + "。");
        queues.values().forEach(queue -> queue.countdown(-1));
    }

    private void prepareWaitingPlayer(Player player) {
        plugin.getGameManager().leaveSpectating(player);
        // Returning spectators are already routed back to the lobby by leaveSpectating; players who are
        // already in the lobby keep their position, so queueing never yanks anyone across the lobby.
        Location lobby = CCConfig.LOBBY_LOCATION;
        if (lobby != null && lobby.getWorld() != null && !player.getWorld().equals(lobby.getWorld())) {
            player.teleport(Utils.getScatteredLobbyLocation(lobby, player));
        }
        player.setGameMode(GameMode.ADVENTURE);
        syncLobbyItem(player);
    }

    private void syncLobbyIdentity(@NotNull Player player) {
        boolean neutralLobbyIdentity = isDailyLobby()
                && sessionByPlayer.get(player.getUniqueId()) == null
                && plugin.getGameManager().getBasePlayerArea(player.getUniqueId()) == null;
        if (neutralLobbyIdentity) plugin.getTeamManager().applyDailyLobbyIdentity(player);
        else plugin.getTeamManager().clearDailyLobbyIdentity(player);
    }

    private void rebuildSnapshots() {
        Set<UUID> known = new HashSet<>(queueByPlayer.keySet());
        known.addAll(sessionByPlayer.keySet());
        for (Player player : Bukkit.getOnlinePlayers()) known.add(player.getUniqueId());
        for (UUID uuid : known) snapshots.put(uuid, buildSnapshot(uuid));
        snapshots.keySet().removeIf(uuid -> !known.contains(uuid));
    }

    private DailyPlayerSnapshot buildSnapshot(UUID uuid) {
        DailyParty party = partyManager.getParty(uuid);
        UUID leaderId = party == null ? uuid : party.leader();
        OfflinePlayer leader = Bukkit.getOfflinePlayer(leaderId);
        String leaderName = leader.getName() == null ? leaderId.toString() : leader.getName();
        DailySession session = sessionByPlayer.get(uuid);
        GameTypeEnum queued = queueByPlayer.get(uuid);
        int size = party == null ? 1 : party.size();
        String selected = party != null && party.selectedGame() != null ? party.selectedGame().toString()
                : queued == null ? "-" : queued.toString();
        boolean partyWaiting = party != null && party.selectedGame() != null;
        boolean allOnline = party == null || party.members().stream().allMatch(member -> Bukkit.getPlayer(member) != null);
        String state = session != null ? MessageConfig.DAILY_STATE_PLAYING
                : queued != null ? MessageConfig.DAILY_STATE_QUEUED
                : partyWaiting ? allOnline ? MessageConfig.DAILY_STATE_SELECTED : MessageConfig.DAILY_STATE_WAITING_MEMBER
                : MessageConfig.DAILY_STATE_IDLE;
        return new DailyPlayerSnapshot(modeDisplay(), leaderName, size, selected, state,
                queued == null ? 0 : queueSize(queued), queued == null ? -1 : queueCountdown(queued),
                session == null ? "-" : session.game().toString(), session == null ? "-" : session.map(),
                session == null ? "-" : session.matchId().toString());
    }

    private void refreshOpenPartyMenus(Set<UUID> players) {
        refreshOpenMenus();
    }

    void broadcastDaily(Set<UUID> players, String text) { broadcast(players, text); }

    private void broadcast(Set<UUID> players, String text) {
        for (UUID uuid : players) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) message(player, text);
        }
    }

    void message(UUID player, String text) {
        Player online = Bukkit.getPlayer(player);
        if (online != null) message(online, text);
    }

    private void message(Player player, String text) {
        player.sendMessage(Utils.translateColorCodes(MessageConfig.DAILY_PREFIX + text));
    }

    public String modeDisplay() {
        return serverMode == ServerMode.DAILY
                ? MessageConfig.DAILY_MODE_FREE_PLAY : MessageConfig.DAILY_MODE_CHAMPIONSHIP;
    }

    private void refreshWaitingBar(DailyQueue queue, @Nullable DailyRules rules) {
        if (queue.size() == 0 || rules == null || !isDailyLobby()) {
            clearWaitingBar(queue.game());
            return;
        }
        int countdown = queue.countdown();
        String title;
        double progress;
        if (countdown >= 0) {
            title = replace(MessageConfig.DAILY_BOSSBAR_COUNTDOWN,
                    "%game%", queue.game().toString(), "%time%", Integer.toString(countdown));
            progress = countdown / (double) Math.max(1, rules.countdownSeconds());
        } else {
            title = replace(MessageConfig.DAILY_BOSSBAR_WAITING,
                    "%game%", queue.game().toString(), "%players%", Integer.toString(queue.size()),
                    "%required%", Integer.toString(rules.minPlayers()));
            progress = queue.size() / (double) Math.max(1, rules.minPlayers());
            if (queue.groupCount() < 2) {
                title += " &#bababa• &#fff566还需另一个玩家或同行小队";
                progress = Math.min(progress, 0.95D);
            }
        }
        BossBar bar = waitingBars.computeIfAbsent(queue.game(), ignored ->
                Bukkit.createBossBar("", BarColor.YELLOW, BarStyle.SOLID));
        bar.setTitle(Utils.translateColorCodes(title));
        bar.setProgress(Math.max(0D, Math.min(1D, progress)));
        Set<UUID> viewers = queue.players();
        for (Player current : List.copyOf(bar.getPlayers()))
            if (!viewers.contains(current.getUniqueId())) bar.removePlayer(current);
        for (UUID viewer : viewers) {
            Player online = Bukkit.getPlayer(viewer);
            if (online != null) bar.addPlayer(online);
        }
    }

    private void clearWaitingBar(GameTypeEnum game) {
        BossBar removed = waitingBars.remove(game);
        if (removed != null) removed.removeAll();
    }

    private static String replace(String value, String... pairs) {
        String result = value == null ? "" : value;
        for (int index = 0; index + 1 < pairs.length; index += 2)
            result = result.replace(pairs[index], pairs[index + 1]);
        return result;
    }
}
