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

public class PlayerManager extends BaseManager {
    private static final Map<UUID, ChampionshipPlayer> cachedPlayers = new ConcurrentHashMap<>();

    public PlayerManager(ChampionshipsCore championshipsCore) {
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
    public ChampionshipPlayer addPlayer(@NotNull UUID uuid) {
        ChampionshipPlayer championshipPlayer;
        synchronized (this) {
            if (!cachedPlayers.containsKey(uuid)) {
                championshipPlayer = new ChampionshipPlayer(uuid);
                cachedPlayers.putIfAbsent(uuid, championshipPlayer);
                return championshipPlayer;
            }
            return getPlayer(uuid);
        }
    }

    public ChampionshipPlayer addPlayer(@NotNull Player player) {
        UUID uuid = player.getUniqueId();
        return addPlayer(uuid);
    }

    @Nullable
    public ChampionshipPlayer addPlayer(@NotNull OfflinePlayer offlinePlayer) {
        UUID uuid = offlinePlayer.getUniqueId();
        return addPlayer(uuid);
    }

    public ChampionshipPlayer getPlayer(@NotNull UUID uuid) {
        ChampionshipPlayer championshipPlayer = cachedPlayers.get(uuid);
        if (championshipPlayer == null)
            return addPlayer(uuid);
        return championshipPlayer;
    }

    public ChampionshipPlayer getPlayer(@NotNull Player player) {
        return getPlayer(player.getUniqueId());
    }

    @Nullable
    public ChampionshipPlayer getPlayer(@NotNull OfflinePlayer offlinePlayer) {
        return getPlayer(offlinePlayer.getUniqueId());
    }

    @Nullable
    public ChampionshipPlayer getPlayer(@NotNull String name) {
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(name);
        return getPlayer(offlinePlayer.getUniqueId());
    }
}
