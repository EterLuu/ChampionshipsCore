package ink.ziip.championshipscore.api.daily;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseManager;
import ink.ziip.championshipscore.api.daily.adapter.AceRaceDailyGameAdapter;
import ink.ziip.championshipscore.api.daily.adapter.BingoDailyGameAdapter;
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
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Public-play orchestration. Existing game instances remain the authority for game rules. */
public final class DailyManager extends BaseManager {
    private static final String[] TEAM_COLORS = {
            "RED", "BLUE", "GREEN", "YELLOW", "CYAN", "PURPLE", "ORANGE", "WHITE",
            "LIME", "PINK", "LIGHT_BLUE", "MAGENTA", "GRAY", "BLACK", "BROWN", "LIGHT_GRAY"
    };
    private static final String[] TEAM_CODES = {
            "#ff5555", "#5555ff", "#55ff55", "#ffff55", "#55ffff", "#ff55ff", "#ffaa00", "#ffffff",
            "#55ff55", "#ff55ff", "#55ffff", "#ff55ff", "#aaaaaa", "#000000", "#aa5500", "#aaaaaa"
    };

    private final Map<GameTypeEnum, DailyGameAdapter> adapters = new EnumMap<>(GameTypeEnum.class);
    private final Map<GameTypeEnum, DailyQueue> queues = new EnumMap<>(GameTypeEnum.class);
    private final Map<UUID, GameTypeEnum> queueByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, DailySession> sessionByPlayer = new ConcurrentHashMap<>();
    private final Map<BaseGameInstance, DailySession> sessionByInstance = new ConcurrentHashMap<>();
    private final Set<BaseGameInstance> settlingInstances = ConcurrentHashMap.newKeySet();
    private final Map<UUID, DailyPlayerSnapshot> snapshots = new ConcurrentHashMap<>();
    private final Map<GameTypeEnum, BossBar> waitingBars = new EnumMap<>(GameTypeEnum.class);
    private final DailyPartyManager partyManager = new DailyPartyManager(this);
    private final DailyGameMenu menu;
    private final DailyLeaderboardMenu leaderboardMenu;
    private final DailyListener listener;
    private final DailyStatsManager statsManager;
    private final PlayerIsolationService isolationService;
    private volatile ServerMode serverMode = ServerMode.CHAMPIONSHIP;
    private BukkitTask tickTask;

    public DailyManager(ChampionshipsCore plugin, DailyStatsManager statsManager) {
        super(plugin);
        menu = new DailyGameMenu(plugin, this);
        leaderboardMenu = new DailyLeaderboardMenu(plugin, this, statsManager);
        listener = new DailyListener(plugin, this, menu);
        this.statsManager = statsManager;
        isolationService = new PlayerIsolationService(plugin);
    }

    @Override
    public void load() {
        serverMode = ServerMode.parse(CCConfig.MODE);
        adapters.put(GameTypeEnum.Bingo, new BingoDailyGameAdapter(plugin));
        adapters.put(GameTypeEnum.AceRace, new AceRaceDailyGameAdapter(plugin));
        for (GameTypeEnum game : enabledGames()) queues.put(game, new DailyQueue(game));
        listener.register();
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, (Runnable) this::tick, 20L, 20L);
        rebuildSnapshots();
    }

    @Override
    public void unload() {
        if (tickTask != null) tickTask.cancel();
        tickTask = null;
        listener.unRegister();
        for (DailySession session : Set.copyOf(sessionByInstance.values())) cleanup(session);
        queues.clear();
        queueByPlayer.clear();
        snapshots.clear();
        partyManager.clear();
        isolationService.clear();
        waitingBars.values().forEach(BossBar::removeAll);
        waitingBars.clear();
    }

    public ServerMode serverMode() { return serverMode; }
    public boolean isDailyLobby() { return serverMode == ServerMode.DAILY; }
    public DailyPartyManager partyManager() { return partyManager; }
    public DailyStatsManager statsManager() { return statsManager; }
    DailyLeaderboardMenu leaderboardMenu() { return leaderboardMenu; }
    public PlayerIsolationService isolation() { return isolationService; }

    public synchronized void switchMode(@NotNull ServerMode next) {
        if (serverMode == next) return;
        serverMode = next;
        CCConfig.MODE = next.name();
        plugin.getConfigurationManager().getCCConfig().saveOptions();
        if (next == ServerMode.CHAMPIONSHIP) clearQueues("服务器已切换到正式比赛模式");
        rebuildSnapshots();
        if (plugin.getSidebarManager() != null) plugin.getSidebarManager().invalidateAll();
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

    public void openMenu(@NotNull Player player) {
        if (!isDailyLobby()) {
            message(player, MessageConfig.DAILY_UNAVAILABLE);
            return;
        }
        if (sessionByPlayer.containsKey(player.getUniqueId())
                || plugin.getGameManager().getBasePlayerArea(player.getUniqueId()) != null
                || plugin.getGameManager().isWaitingForNextRound(player.getUniqueId())) {
            message(player, MessageConfig.DAILY_ALREADY_PLAYING);
            return;
        }
        menu.open(player);
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
            isolationService.detach(uuid);
        }
        broadcast(leaving, replace(MessageConfig.DAILY_PLAY_LEFT,
                "%players%", Integer.toString(leaving.size())));
        rebuildSnapshots();

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
        if (session != null && session.instance() instanceof RemoteBingoInstance) return;
        if (partyManager.getParty(player) == null) leaveQueue(player, false);
        Bukkit.getScheduler().runTask(plugin, () -> partyManager.handleOffline(player));
    }

    public void handleJoin(UUID player) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            rebuildSnapshots();
            Player online = Bukkit.getPlayer(player);
            if (online != null && plugin.getSidebarManager() != null) plugin.getSidebarManager().invalidate(online);
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

    boolean isPartyInSession(@NotNull DailyParty party) {
        return party.members().stream().anyMatch(sessionByPlayer::containsKey);
    }

    private void tick() {
        for (DailyQueue queue : List.copyOf(queues.values())) tick(queue);
        for (BaseGameInstance instance : Set.copyOf(sessionByInstance.keySet())) {
            if (instance.getGameStageEnum() == GameStageEnum.WAITING) {
                DailySession session = sessionByInstance.get(instance);
                if (session != null) cleanup(session);
                settlingInstances.remove(instance);
            }
        }
        rebuildSnapshots();
        menu.refreshOpenMenus();
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
        if (rules == null || queue.size() < rules.minPlayers()) {
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
        if (playerCount < rules.minPlayers()) {
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
                teams.add(plugin.getTeamManager().createTransientTeam("ccd" + token + index,
                        replace(MessageConfig.DAILY_TEAM_NAME, "%number%", Integer.toString(index + 1)),
                        TEAM_COLORS[color], TEAM_CODES[color], allocations.get(index)));
            }
        } catch (RuntimeException exception) {
            teams.forEach(plugin.getTeamManager()::removeTransientTeam);
            queue.restore(selected, rules);
            plugin.getLogger().warning(Utils.formatModuleLog("Daily", "临时队伍", exception.getMessage()));
            return;
        }

        DailyGameAdapter adapter = adapters.get(queue.game());
        DailyGameAdapter.StartResult started = adapter == null ? null : adapter.start(teams);
        if (started == null || started.instance() == null) {
            teams.forEach(plugin.getTeamManager()::removeTransientTeam);
            queue.restore(selected, rules);
            queue.countdown(5);
            broadcast(queue.players(), MessageConfig.DAILY_QUEUE_NO_ARENA);
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
        }
        broadcast(players, replace(MessageConfig.DAILY_MATCH_ASSIGNED,
                "%map%", started.map(), "%instance%", Integer.toString(started.instance().getCopyIndex() + 1)));
        refreshWaitingBar(queue, rules);
        rebuildSnapshots();
    }

    private List<Set<UUID>> allocate(List<DailyQueue.Group> groups, DailyRules rules) {
        int players = groups.stream().mapToInt(group -> group.players().size()).sum();
        int teamCount = Math.min(rules.teams(), Math.max(1, (players + rules.teamSize() - 1) / rules.teamSize()));
        teamCount = Math.min(teamCount, groups.size());
        List<Set<UUID>> allocations = new ArrayList<>();
        for (int i = 0; i < teamCount; i++) allocations.add(new LinkedHashSet<>());

        List<DailyQueue.Group> ordered = new ArrayList<>(groups);
        Collections.shuffle(ordered);
        ordered.sort(Comparator.comparingInt((DailyQueue.Group group) -> group.players().size()).reversed());
        for (DailyQueue.Group group : ordered) {
            Set<UUID> target = allocations.stream()
                    .filter(team -> team.size() + group.players().size() <= rules.teamSize())
                    .min(Comparator.comparingInt(Set::size)).orElse(null);
            if (target == null) return List.of();
            target.addAll(group.players());
        }
        if (allocations.stream().anyMatch(Set::isEmpty)) return List.of();
        return allocations;
    }

    /** Receives the normal game end event; formal/GAME instances are ignored. */
    public void finish(@NotNull BaseGameInstance instance) {
        if (instance.getRunMode() != GameRunMode.DAILY) return;
        DailySession session = sessionByInstance.get(instance);
        if (session == null) return;
        statsManager.recordMatch(session, instance.getPlayerPointsSnapshot());
        settlingInstances.add(instance);
    }

    /** Releases a DAILY reservation that failed before it could emit the normal end event. */
    public synchronized void abort(@NotNull BaseGameInstance instance) {
        DailySession session = sessionByInstance.get(instance);
        if (session == null) return;
        broadcast(session.players(), MessageConfig.DAILY_MATCH_ABORTED);
        cleanup(session);
    }

    private void cleanup(DailySession session) {
        isolationService.unregister(session);
        sessionByInstance.remove(session.instance(), session);
        settlingInstances.remove(session.instance());
        for (UUID uuid : session.players()) sessionByPlayer.remove(uuid, session);
        for (ChampionshipTeam team : session.teams()) plugin.getTeamManager().removeTransientTeam(team);
        rebuildSnapshots();
    }

    private void clearQueues(String reason) {
        Set<UUID> players = new HashSet<>(queueByPlayer.keySet());
        for (UUID player : List.copyOf(players)) leaveQueue(player, false);
        broadcast(players, "&e" + reason + "。");
        queues.values().forEach(queue -> queue.countdown(-1));
    }

    private void prepareWaitingPlayer(Player player) {
        plugin.getGameManager().leaveSpectating(player);
        if (CCConfig.LOBBY_LOCATION != null && CCConfig.LOBBY_LOCATION.getWorld() != null) {
            player.teleport(CCConfig.LOBBY_LOCATION);
        }
        player.setGameMode(GameMode.ADVENTURE);
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
        for (UUID uuid : players) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.getOpenInventory().getTopInventory().getHolder() instanceof DailyGameMenu.MenuHolder)
                menu.open(player);
        }
    }

    private void broadcast(Set<UUID> players, String text) {
        for (UUID uuid : players) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) message(player, text);
        }
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
