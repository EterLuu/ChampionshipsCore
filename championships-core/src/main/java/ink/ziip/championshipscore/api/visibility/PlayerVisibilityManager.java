package ink.ziip.championshipscore.api.visibility;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseManager;
import ink.ziip.championshipscore.api.game.instance.BaseGameInstance;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Single lifecycle owner for player-entity visibility. Persistent decisions are UUID based; Bukkit Player
 * instances are resolved only while applying a decision to an online connection.
 */
public final class PlayerVisibilityManager extends BaseManager implements Listener {
    private static final String DEFAULT_OWNER = "system:default";
    private static final PlayerVisibilityState DEFAULT_STATE =
            PlayerVisibilityState.all(DEFAULT_OWNER, "无显隐限制");

    private final Map<UUID, PlayerVisibilityState> states = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> sessionByPlayer = new ConcurrentHashMap<>();
    private final Set<VisibilityPair> hiddenPairs = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Set<UUID>> manuallyHiddenPlayers = new ConcurrentHashMap<>();
    private final Map<UUID, Set<Integer>> manuallyHiddenTeams = new ConcurrentHashMap<>();

    public PlayerVisibilityManager(ChampionshipsCore plugin) {
        super(plugin);
    }

    @Override
    public void load() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        reconcileAll();
    }

    @Override
    public void unload() {
        HandlerList.unregisterAll(this);
        runOnServerThread(() -> {
            for (VisibilityPair pair : Set.copyOf(hiddenPairs)) apply(pair.viewer(), pair.target(), true, true);
            hiddenPairs.clear();
        });
        states.clear();
        sessionByPlayer.clear();
        manuallyHiddenPlayers.clear();
        manuallyHiddenTeams.clear();
    }

    public void setState(@NotNull UUID viewerId, @NotNull PlayerVisibilityState state) {
        PlayerVisibilityState previous = states.put(viewerId, state);
        if (!state.equals(previous)) reconcileViewer(viewerId);
    }

    public void seeAll(@NotNull UUID viewerId, @NotNull String owner, @NotNull String reason) {
        setState(viewerId, PlayerVisibilityState.all(owner, reason));
    }

    public void seeTeammates(@NotNull UUID viewerId, @NotNull String owner, @NotNull String reason) {
        setState(viewerId, PlayerVisibilityState.teammates(owner, reason));
    }

    public void seeSelf(@NotNull UUID viewerId, @NotNull String owner, @NotNull String reason) {
        setState(viewerId, PlayerVisibilityState.self(owner, reason));
    }

    public void seeTeams(@NotNull UUID viewerId, @NotNull Set<Integer> teamIds,
                         @NotNull String owner, @NotNull String reason) {
        setState(viewerId, PlayerVisibilityState.teams(teamIds, owner, reason));
    }

    public void seePlayers(@NotNull UUID viewerId, @NotNull Set<UUID> playerIds,
                           @NotNull String owner, @NotNull String reason) {
        setState(viewerId, PlayerVisibilityState.players(playerIds, owner, reason));
    }

    /** Adds/removes one UUID from a PLAYERS policy without retaining either live Player object. */
    public void setPlayerVisible(@NotNull UUID viewerId, @NotNull UUID targetId, boolean visible,
                                 @NotNull String owner, @NotNull String reason) {
        if (viewerId.equals(targetId)) return;
        states.compute(viewerId, (ignored, current) -> {
            if (current == null || current.mode() == PlayerVisibilityMode.ALL || !current.owner().equals(owner)) {
                if (visible) return current;
                Set<UUID> currentlyVisible = new HashSet<>();
                for (Player online : Bukkit.getOnlinePlayers()) {
                    UUID onlineId = online.getUniqueId();
                    if (!onlineId.equals(targetId) && isVisible(viewerId, onlineId)) currentlyVisible.add(onlineId);
                }
                return PlayerVisibilityState.players(currentlyVisible, owner, reason);
            }
            if (current.mode() != PlayerVisibilityMode.PLAYERS && current.mode() != PlayerVisibilityMode.SELF)
                return current;
            Set<UUID> allowed = new HashSet<>(current.playerIds());
            if (visible) allowed.add(targetId);
            else allowed.remove(targetId);
            return PlayerVisibilityState.players(allowed, owner, reason);
        });
        reconcilePair(viewerId, targetId);
    }

    /** Clears a policy only if it is still owned by the releasing game/module. */
    public void release(@NotNull UUID viewerId, @NotNull String owner) {
        PlayerVisibilityState current = states.get(viewerId);
        if (current != null && current.owner().equals(owner) && states.remove(viewerId, current)) reconcileViewer(viewerId);
    }

    public void releaseAll(@NotNull Iterable<UUID> viewerIds, @NotNull String owner) {
        for (UUID viewerId : viewerIds) release(viewerId, owner);
    }

    public void assignSession(@NotNull UUID playerId, @NotNull UUID sessionId) {
        UUID previous = sessionByPlayer.put(playerId, sessionId);
        if (!sessionId.equals(previous)) reconcilePlayer(playerId);
    }

    public void clearSession(@NotNull UUID playerId, @NotNull UUID expectedSession) {
        if (sessionByPlayer.remove(playerId, expectedSession)) reconcilePlayer(playerId);
    }

    public void clearSession(@NotNull UUID playerId) {
        if (sessionByPlayer.remove(playerId) != null) reconcilePlayer(playerId);
    }

    public void clearAllSessions() {
        Set<UUID> affected = Set.copyOf(sessionByPlayer.keySet());
        sessionByPlayer.clear();
        for (UUID playerId : affected) reconcilePlayer(playerId);
    }

    /** A player-facing override used by the spectator controls. It intentionally wins over game policy. */
    public void setPlayerHidden(@NotNull UUID viewerId, @NotNull UUID targetId, boolean hidden) {
        if (viewerId.equals(targetId)) return;
        manuallyHiddenPlayers.computeIfAbsent(viewerId, ignored -> ConcurrentHashMap.newKeySet());
        Set<UUID> hiddenPlayers = manuallyHiddenPlayers.get(viewerId);
        if (hidden) hiddenPlayers.add(targetId);
        else hiddenPlayers.remove(targetId);
        if (hiddenPlayers.isEmpty()) manuallyHiddenPlayers.remove(viewerId, hiddenPlayers);
        reconcilePair(viewerId, targetId);
    }

    public void setTeamHidden(@NotNull UUID viewerId, int teamId, boolean hidden) {
        manuallyHiddenTeams.computeIfAbsent(viewerId, ignored -> ConcurrentHashMap.newKeySet());
        Set<Integer> hiddenTeams = manuallyHiddenTeams.get(viewerId);
        if (hidden) hiddenTeams.add(teamId);
        else hiddenTeams.remove(teamId);
        if (hiddenTeams.isEmpty()) manuallyHiddenTeams.remove(viewerId, hiddenTeams);
        reconcileViewer(viewerId);
    }

    public boolean isPlayerHidden(@NotNull UUID viewerId, @NotNull UUID targetId) {
        return manuallyHiddenPlayers.getOrDefault(viewerId, Set.of()).contains(targetId);
    }

    public boolean isTeamHidden(@NotNull UUID viewerId, int teamId) {
        return manuallyHiddenTeams.getOrDefault(viewerId, Set.of()).contains(teamId);
    }

    public void clearManualOverrides(@NotNull UUID viewerId) {
        manuallyHiddenPlayers.remove(viewerId);
        manuallyHiddenTeams.remove(viewerId);
        reconcileViewer(viewerId);
    }

    public boolean sameSession(@NotNull UUID first, @NotNull UUID second) {
        UUID session = sessionByPlayer.get(first);
        return session != null && session.equals(sessionByPlayer.get(second));
    }

    public void reconcileAll() {
        runOnServerThread(() -> {
            for (Player viewer : Bukkit.getOnlinePlayers())
                for (Player target : Bukkit.getOnlinePlayers())
                    if (!viewer.equals(target)) reconcilePairNow(viewer.getUniqueId(), target.getUniqueId(), false);
        });
    }

    /** Re-applies both directions, which is required after a new Player instance joins. */
    public void reconcilePlayer(@NotNull UUID playerId) {
        runOnServerThread(() -> {
            hiddenPairs.removeIf(pair -> pair.contains(playerId));
            for (Player online : Bukkit.getOnlinePlayers()) {
                UUID otherId = online.getUniqueId();
                if (otherId.equals(playerId)) continue;
                reconcilePairNow(playerId, otherId, true);
                reconcilePairNow(otherId, playerId, true);
            }
        });
    }

    public void reconcileViewer(@NotNull UUID viewerId) {
        runOnServerThread(() -> {
            for (Player target : Bukkit.getOnlinePlayers()) {
                if (!target.getUniqueId().equals(viewerId))
                    reconcilePairNow(viewerId, target.getUniqueId(), false);
            }
        });
    }

    public @NotNull List<String> describe(@NotNull UUID playerId) {
        PlayerVisibilityState state = states.getOrDefault(playerId, DEFAULT_STATE);
        BaseGameInstance participant = plugin.getGameManager().getBasePlayerArea(playerId);
        BaseGameInstance spectator = plugin.getGameManager().getPlayerSpectatorStatus(playerId);
        Player online = Bukkit.getPlayer(playerId);
        boolean forcedAll = plugin.getGameManager().getSpectatorManager().isSpectatorLike(playerId)
                || spectator != null || participant == null
                || online != null && online.getGameMode() == GameMode.SPECTATOR;
        int visible = 0;
        int hidden = 0;
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (target.getUniqueId().equals(playerId)) continue;
            if (isVisible(playerId, target.getUniqueId())) visible++;
            else hidden++;
        }
        List<String> result = new ArrayList<>();
        result.add("模式=" + state.mode() + " | 来源=" + state.owner());
        result.add("原因=" + state.reason());
        result.add("身份=" + (spectator != null ? "旁观者" : participant != null ? "游戏玩家" : "未加入游戏")
                + (forcedAll ? "（最终规则：始终可见全部）" : ""));
        result.add("全局规则=游戏玩家看不到本局旁观者；旁观者与未加入游戏者始终可见全部");
        if (!state.teamIds().isEmpty()) result.add("允许队伍ID=" + state.teamIds());
        if (!state.playerIds().isEmpty()) result.add("允许玩家UUID=" + state.playerIds());
        UUID session = sessionByPlayer.get(playerId);
        if (session != null) result.add("隔离会话=" + session);
        result.add("当前在线目标：可见=" + visible + "，隐藏=" + hidden);
        return List.copyOf(result);
    }

    public @NotNull PlayerVisibilityState getState(@NotNull UUID playerId) {
        return states.getOrDefault(playerId, DEFAULT_STATE);
    }

    private void reconcilePair(@NotNull UUID viewerId, @NotNull UUID targetId) {
        runOnServerThread(() -> reconcilePairNow(viewerId, targetId, false));
    }

    private void reconcilePairNow(UUID viewerId, UUID targetId, boolean force) {
        apply(viewerId, targetId, isVisible(viewerId, targetId), force);
    }

    private boolean isVisible(UUID viewerId, UUID targetId) {
        Player viewer = Bukkit.getPlayer(viewerId);
        Player target = Bukkit.getPlayer(targetId);
        BaseGameInstance participant = plugin.getGameManager().getBasePlayerArea(viewerId);
        boolean viewerAlwaysSeesAll = plugin.getGameManager().getSpectatorManager().isSpectatorLike(viewerId)
                || plugin.getGameManager().getPlayerSpectatorStatus(viewerId) != null
                || participant == null || viewer != null && viewer.getGameMode() == GameMode.SPECTATOR;
        ChampionshipTeam viewerTeam = plugin.getTeamManager().getTeamByPlayer(viewerId);
        ChampionshipTeam targetTeam = plugin.getTeamManager().getTeamByPlayer(targetId);
        if (manuallyHiddenPlayers.getOrDefault(viewerId, Set.of()).contains(targetId)) return false;
        if (targetTeam != null && manuallyHiddenTeams.getOrDefault(viewerId, Set.of()).contains(targetTeam.getId()))
            return false;
        BaseGameInstance targetSpectatorArea = plugin.getGameManager().getPlayerSpectatorStatus(targetId);
        BaseGameInstance targetParticipantArea = plugin.getGameManager().getBasePlayerArea(targetId);
        boolean targetIsCorrespondingSpectator = participant != null && (targetSpectatorArea == participant
                || targetParticipantArea == participant
                && plugin.getGameManager().getSpectatorManager().isSpectatorLike(targetId));
        boolean sameTeam = viewerTeam != null && viewerTeam.equals(targetTeam);
        Integer targetTeamId = targetTeam == null ? null : targetTeam.getId();
        return PlayerVisibilityPolicy.allows(states.getOrDefault(viewerId, DEFAULT_STATE), viewerId, targetId,
                viewerAlwaysSeesAll, targetIsCorrespondingSpectator, sameTeam, targetTeamId,
                sessionByPlayer.get(viewerId), sessionByPlayer.get(targetId));
    }

    private void apply(UUID viewerId, UUID targetId, boolean visible, boolean force) {
        Player viewer = Bukkit.getPlayer(viewerId);
        Player target = Bukkit.getPlayer(targetId);
        if (viewer == null || target == null || viewer.equals(target)) return;
        VisibilityPair pair = new VisibilityPair(viewerId, targetId);
        if (visible) {
            boolean wasHidden = hiddenPairs.remove(pair);
            if (force || wasHidden) viewer.showEntity(plugin, target);
        } else {
            boolean newlyHidden = hiddenPairs.add(pair);
            if (force || newlyHidden) viewer.hideEntity(plugin, target);
        }
    }

    private void runOnServerThread(@NotNull Runnable task) {
        if (Bukkit.isPrimaryThread()) task.run();
        else Bukkit.getScheduler().runTask(plugin, task);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        Bukkit.getScheduler().runTask(plugin, () -> reconcilePlayer(playerId));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerGameModeChange(PlayerGameModeChangeEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        Bukkit.getScheduler().runTask(plugin, () -> reconcilePlayer(playerId));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        hiddenPairs.removeIf(pair -> pair.contains(playerId));
    }

    private record VisibilityPair(@NotNull UUID viewer, @NotNull UUID target) {
        private boolean contains(UUID playerId) {
            return viewer.equals(playerId) || target.equals(playerId);
        }
    }
}
