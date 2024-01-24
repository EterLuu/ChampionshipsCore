package ink.ziip.championshipscore.api.player;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CCPlayerManager extends BaseManager {
    private static final Map<UUID, CCPlayer> cachedPlayers = new ConcurrentHashMap<>();

    public CCPlayerManager(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
    }

    @Override
    public void load() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            addPlayer(player);
        }
    }

    @Override
    public void unload() {

    }

    // TODO check dead lock
    public CCPlayer addPlayer(@NotNull UUID uuid) {
        CCPlayer ccPlayer;
        synchronized (this) {
            if (!cachedPlayers.containsKey(uuid)) {
                ccPlayer = new CCPlayer(uuid);
                cachedPlayers.putIfAbsent(uuid, ccPlayer);
                return ccPlayer;
            }
            return getPlayer(uuid);
        }
    }

    public CCPlayer addPlayer(@NotNull Player player) {
        UUID uuid = player.getUniqueId();
        return addPlayer(uuid);
    }

    @Nullable
    public CCPlayer addPlayer(@NotNull OfflinePlayer offlinePlayer) {
        UUID uuid = offlinePlayer.getUniqueId();
        return addPlayer(uuid);
    }

    public CCPlayer getPlayer(@NotNull UUID uuid) {
        CCPlayer ccPlayer = cachedPlayers.get(uuid);
        if (ccPlayer == null)
            return addPlayer(uuid);
        return ccPlayer;
    }

    public CCPlayer getPlayer(@NotNull Player player) {
        return getPlayer(player.getUniqueId());
    }

    @Nullable
    public CCPlayer getPlayer(@NotNull OfflinePlayer offlinePlayer) {
        return getPlayer(offlinePlayer.getUniqueId());
    }

    @Nullable
    public CCPlayer getPlayer(@NotNull String name) {
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(name);
        return getPlayer(offlinePlayer.getUniqueId());
    }
}
