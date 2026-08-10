package ink.ziip.championshipscore.api.daily;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.instance.BaseGameInstance;
import ink.ziip.championshipscore.api.object.game.GameRunMode;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Authoritative visibility policy for independently running sessions that share one physical arena. */
public final class PlayerIsolationService {
    private final ChampionshipsCore plugin;
    private final Map<UUID, UUID> sessionByPlayer = new ConcurrentHashMap<>();

    PlayerIsolationService(ChampionshipsCore plugin) { this.plugin = plugin; }

    void register(DailySession session) {
        for (UUID player : session.players()) sessionByPlayer.put(player, session.matchId());
        reconcile();
    }

    void unregister(DailySession session) {
        Set<UUID> removed = ConcurrentHashMap.newKeySet();
        sessionByPlayer.entrySet().removeIf(entry -> {
            boolean matches = entry.getValue().equals(session.matchId());
            if (matches) removed.add(entry.getKey());
            return matches;
        });
        restoreUntracked(removed);
    }

    void attach(UUID player, UUID session) {
        sessionByPlayer.put(player, session);
        reconcile();
    }

    void detach(UUID player) {
        sessionByPlayer.remove(player);
        restoreUntracked(Set.of(player));
    }

    public void setVisible(@NotNull BaseGameInstance owner, @NotNull Player viewer,
                           @NotNull Player target, boolean requestedVisible) {
        if (viewer.equals(target)) return;
        boolean visible = requestedVisible;
        if (owner.getRunMode() == GameRunMode.DAILY) {
            UUID viewerSession = sessionByPlayer.get(viewer.getUniqueId());
            UUID targetSession = sessionByPlayer.get(target.getUniqueId());
            visible = requestedVisible && viewerSession != null && viewerSession.equals(targetSession);
        }
        if (visible) viewer.showPlayer(plugin, target);
        else viewer.hidePlayer(plugin, target);
    }

    void reconcile() {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            UUID viewerSession = sessionByPlayer.get(viewer.getUniqueId());
            for (Player target : Bukkit.getOnlinePlayers()) {
                if (viewer.equals(target)) continue;
                UUID targetSession = sessionByPlayer.get(target.getUniqueId());
                if (viewerSession != null && targetSession != null && !viewerSession.equals(targetSession))
                    viewer.hidePlayer(plugin, target);
            }
        }
    }

    private void restoreUntracked(Set<UUID> removed) {
        for (UUID removedId : removed) {
            Player removedPlayer = Bukkit.getPlayer(removedId);
            if (removedPlayer == null) continue;
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (removedPlayer.equals(other)) continue;
                removedPlayer.showPlayer(plugin, other);
                if (sessionByPlayer.containsKey(other.getUniqueId())) other.hidePlayer(plugin, removedPlayer);
                else other.showPlayer(plugin, removedPlayer);
            }
        }
        reconcile();
    }

    public boolean sameSession(UUID first, UUID second) {
        UUID session = sessionByPlayer.get(first);
        return session != null && session.equals(sessionByPlayer.get(second));
    }

    void clear() {
        Set<UUID> tracked = Set.copyOf(sessionByPlayer.keySet());
        sessionByPlayer.clear();
        for (UUID viewerId : tracked) {
            Player viewer = Bukkit.getPlayer(viewerId);
            if (viewer == null) continue;
            for (Player target : Bukkit.getOnlinePlayers()) if (!viewer.equals(target)) viewer.showPlayer(plugin, target);
        }
    }
}
