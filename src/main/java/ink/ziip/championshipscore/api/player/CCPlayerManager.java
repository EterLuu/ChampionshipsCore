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
        synchronized (this) {
            CCPlayer CCPlayer;
            if (!cachedPlayers.containsKey(uuid)) {
                CCPlayer = new CCPlayer(uuid);
                cachedPlayers.putIfAbsent(uuid, CCPlayer);
                return CCPlayer;
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

    @Nullable
    public CCPlayer addPlayer(@NotNull String name) {
        Player player = Bukkit.getPlayer(name);
        if (player == null)
            return null;
        return addPlayer(player.getUniqueId());
    }

    public CCPlayer getPlayer(@NotNull UUID uuid) {
        return cachedPlayers.getOrDefault(uuid, addPlayer(uuid));
    }

    public CCPlayer getPlayer(@NotNull Player player) {
        UUID uuid = player.getUniqueId();
        return cachedPlayers.getOrDefault(uuid, addPlayer(player));
    }

    @Nullable
    public CCPlayer getPlayer(@NotNull OfflinePlayer offlinePlayer) {
        UUID uuid = offlinePlayer.getUniqueId();
        return cachedPlayers.getOrDefault(uuid, addPlayer(offlinePlayer));
    }

    @Nullable
    public CCPlayer getPlayer(@NotNull String name) {
        Player player = Bukkit.getPlayer(name);
        if (player == null)
            return null;
        return cachedPlayers.getOrDefault(player.getUniqueId(), null);
    }

    public void deletePlayer(@NotNull UUID uuid) {
        cachedPlayers.remove(uuid);
    }
}
