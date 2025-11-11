package ink.ziip.championshipscore.api.player;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseManager;
import ink.ziip.championshipscore.api.player.dao.PlayerDao;
import ink.ziip.championshipscore.api.player.dao.PlayerDaoImpl;
import ink.ziip.championshipscore.api.player.entry.PlayerEntry;
import ink.ziip.championshipscore.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerManager extends BaseManager {
    private static final Map<UUID, ChampionshipPlayer> cachedPlayers = new ConcurrentHashMap<>();
    private static final Map<String, UUID> cachedPlayerUUID = new ConcurrentHashMap<>();
    private static final Map<UUID, String> cachedPlayerName = new ConcurrentHashMap<>();
    private final PlayerDao playerDao;

    public PlayerManager(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
        playerDao = new PlayerDaoImpl();
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
        synchronized (cachedPlayers) {
            if (!cachedPlayers.containsKey(uuid)) {
                championshipPlayer = new ChampionshipPlayer(uuid);
                cachedPlayers.putIfAbsent(uuid, championshipPlayer);
                return championshipPlayer;
            }
            return getPlayer(uuid);
        }
    }

    public void addPlayer(@NotNull Player player) {
        UUID uuid = player.getUniqueId();
        addPlayer(uuid);
    }

    public void deletePlayer(@NotNull UUID uuid) {
        if (!cachedPlayers.containsKey(uuid))
            return;
        String name = cachedPlayerName.get(uuid);
        cachedPlayerUUID.remove(name);
        cachedPlayers.remove(uuid);
        cachedPlayerName.remove(uuid);
        playerDao.deletePlayer(uuid);
    }

    public void updatePlayer(@NotNull Player player) {
        setPlayerUUID(player);
    }

    public void setPlayerUUID(@NotNull Player player) {
        if (cachedPlayerUUID.containsKey(player.getName()))
            return;

        String username = player.getName();
        UUID uuid = player.getUniqueId();

        cachedPlayerUUID.put(username, uuid);
        cachedPlayerName.put(uuid, username);

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            PlayerEntry playerEntry = playerDao.getPlayer(username);
            if (playerEntry == null) {
                playerDao.addPlayer(username, uuid);
            }
        });
    }

    public UUID getPlayerUUID(@NotNull String name) {
        if (cachedPlayerUUID.containsKey(name))
            return cachedPlayerUUID.get(name);

        UUID uuid = null;

        PlayerEntry playerEntry = playerDao.getPlayer(name);
        if (playerEntry == null) {
            uuid = Utils.getPlayerUUID(name);
            playerDao.addPlayer(name, uuid);
        } else {
            uuid = playerEntry.getUuid();
        }

        cachedPlayerUUID.put(name, uuid);
        cachedPlayerName.put(uuid, name);

        return uuid;
    }

    public String getPlayerName(@NotNull UUID uuid) {
        if (cachedPlayerName.containsKey(uuid))
            return cachedPlayerName.get(uuid);

        String name = null;

        PlayerEntry playerEntry = playerDao.getPlayer(uuid);
        if (playerEntry != null) {
            name = playerEntry.getName();
        }

        if (name == null) {
            return "unknown";
        }

        cachedPlayerName.put(uuid, name);

        return name;
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
    public ChampionshipPlayer getPlayer(@NotNull String name) {
        return getPlayer(getPlayerUUID(name));
    }
}
