package ink.ziip.championshipscore.api.daily;

import ink.ziip.championshipscore.ChampionshipsCore;
import java.util.UUID;

/** Daily-session lifecycle adapter; the global visibility manager owns and applies the actual policy. */
public final class PlayerIsolationService {
    private final ChampionshipsCore plugin;

    PlayerIsolationService(ChampionshipsCore plugin) { this.plugin = plugin; }

    void register(DailySession session) {
        for (UUID player : session.players()) plugin.getVisibilityManager().assignSession(player, session.matchId());
    }

    void unregister(DailySession session) {
        for (UUID player : session.players())
            plugin.getVisibilityManager().clearSession(player, session.matchId());
    }

    void attach(UUID player, UUID session) {
        plugin.getVisibilityManager().assignSession(player, session);
    }

    void detach(UUID player) {
        plugin.getVisibilityManager().clearSession(player);
    }

    public boolean sameSession(UUID first, UUID second) {
        return plugin.getVisibilityManager().sameSession(first, second);
    }

    void clear() {
        plugin.getVisibilityManager().clearAllSessions();
    }
}
