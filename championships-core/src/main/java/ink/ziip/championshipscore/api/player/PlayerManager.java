package ink.ziip.championshipscore.api.player;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseManager;
import ink.ziip.championshipscore.api.player.dao.PlayerDao;
import ink.ziip.championshipscore.api.player.dao.PlayerDaoImpl;
import ink.ziip.championshipscore.api.player.entry.PlayerEntry;
import ink.ziip.championshipscore.api.player.entry.PlayerIdentityMigrationResult;
import ink.ziip.championshipscore.database.sync.DatabaseSyncDomain;
import ink.ziip.championshipscore.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerManager extends BaseManager {
    private final Map<UUID, ChampionshipPlayer> cachedPlayers = new ConcurrentHashMap<>();
    private final Map<String, UUID> cachedPlayerUUID = new ConcurrentHashMap<>();
    private final Map<UUID, String> cachedPlayerName = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerIdentityMigrationResult> pendingIdentityMigrations = new ConcurrentHashMap<>();
    private final Map<String, Object> identityLocks = new ConcurrentHashMap<>();
    private final PlayerDao playerDao;

    public PlayerManager(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
        playerDao = new PlayerDaoImpl();
    }

    @Override
    public void load() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            addPlayer(player);
            cacheIdentity(player.getName(), player.getUniqueId(), java.util.Set.of());
        }
    }

    @Override
    public void unload() {
        cachedPlayers.clear();
        cachedPlayerUUID.clear();
        cachedPlayerName.clear();
        pendingIdentityMigrations.clear();
        identityLocks.clear();
    }

    public ChampionshipPlayer addPlayer(@NotNull UUID uuid) {
        return cachedPlayers.computeIfAbsent(uuid, ChampionshipPlayer::new);
    }

    public void addPlayer(@NotNull Player player) {
        UUID uuid = player.getUniqueId();
        addPlayer(uuid);
    }

    public void updatePlayer(@NotNull Player player) {
        String username = player.getName();
        UUID uuid = player.getUniqueId();
        PlayerIdentityMigrationResult migration = pendingIdentityMigrations.remove(uuid);
        if (migration == null || !migration.username().equalsIgnoreCase(username)) {
            migration = prepareIdentity(username, uuid);
            pendingIdentityMigrations.remove(uuid, migration);
        }

        cacheIdentity(username, uuid, migration.previousUuids());
        addPlayer(uuid);
        plugin.getTeamManager().applyIdentityMigration(migration);
        if (migration.successful() && migration.migratedPointRows() > 0) {
            plugin.getRankManager().refreshAfterPendingPointWrites();
        }
        if (migration.successful() && migration.changed()) {
            plugin.getRedisManager().publishDatabaseChange("player-identity-migrated",
                    DatabaseSyncDomain.PLAYER, DatabaseSyncDomain.TEAM, DatabaseSyncDomain.RANK);
        }
    }

    public void setPlayerUUID(@NotNull Player player) {
        cacheIdentity(player.getName(), player.getUniqueId(), java.util.Set.of());
    }

    @NotNull
    public PlayerIdentityMigrationResult prepareIdentity(@NotNull String username, @NotNull UUID currentUuid) {
        String normalizedName = normalizeName(username);
        PlayerIdentityMigrationResult result;
        Object identityLock = identityLocks.computeIfAbsent(normalizedName, ignored -> new Object());
        synchronized (identityLock) {
            result = playerDao.synchronizeIdentity(username, currentUuid);
            pendingIdentityMigrations.put(currentUuid, result);
        }

        if (!result.successful()) {
            plugin.getLogger().severe(Utils.formatModuleLog("Player", "UUIDMigration",
                    "玩家=" + username + " 当前UUID=" + currentUuid + " 同步失败=" + result.failureReason()));
        } else if (result.hasTeamConflict()) {
            plugin.getLogger().warning(Utils.formatModuleLog("Player", "UUIDMigration",
                    "玩家=" + username + " 当前UUID=" + currentUuid + " 旧UUID=" + result.previousUuids()
                            + " 队伍冲突=" + result.conflictingTeamIds() + "，未自动选择队伍"));
        } else if (result.changed()) {
            plugin.getLogger().info(Utils.formatModuleLog("Player", "UUIDMigration",
                    "玩家=" + username + " 旧UUID=" + result.previousUuids() + " 新UUID=" + currentUuid
                            + " 队伍=" + result.resolvedTeamId() + " 迁移积分记录=" + result.migratedPointRows()));
        }
        return result;
    }

    /** Resolves an offline identity without blocking the server thread on SQL or profile lookup. */
    public CompletionStage<UUID> resolvePlayerUUID(@NotNull String name) {
        String normalizedName = normalizeName(name);
        Player online = Bukkit.getOnlinePlayers().stream()
                .filter(player -> player.getName().equalsIgnoreCase(name)).findFirst().orElse(null);
        if (online != null) {
            cacheIdentity(online.getName(), online.getUniqueId(), java.util.Set.of());
            return CompletableFuture.completedFuture(online.getUniqueId());
        }
        UUID cached = cachedPlayerUUID.get(normalizedName);
        if (cached != null) return CompletableFuture.completedFuture(cached);

        CompletableFuture<UUID> resolved = new CompletableFuture<>();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                PlayerEntry playerEntry = playerDao.getPlayer(name);
                UUID uuid;
                if (playerEntry == null) {
                    uuid = Utils.getPlayerUUID(name);
                    playerDao.addPlayer(name, uuid);
                    plugin.getRedisManager().publishDatabaseChange("player-created", DatabaseSyncDomain.PLAYER);
                } else {
                    uuid = playerEntry.getUuid();
                }
                cachedPlayerUUID.put(normalizedName, uuid);
                cachedPlayerName.put(uuid, name);
                resolved.complete(uuid);
            } catch (Throwable failure) {
                resolved.completeExceptionally(failure);
            }
        });
        return resolved;
    }

    public String getPlayerName(@NotNull UUID uuid) {
        return getCachedPlayerName(uuid);
    }

    /** Non-blocking identity lookup for presentation paths which must never query the database. */
    public @NotNull String getCachedPlayerName(@NotNull UUID uuid) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) return online.getName();
        return cachedPlayerName.getOrDefault(uuid, "unknown");
    }

    /** Loads historical identities away from the server thread for GUI selectors. */
    public CompletionStage<List<PlayerEntry>> getKnownPlayersAsync() {
        CompletableFuture<List<PlayerEntry>> result = new CompletableFuture<>();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                result.complete(List.copyOf(playerDao.getPlayerList()));
            } catch (Throwable failure) {
                result.completeExceptionally(failure);
            }
        });
        return result;
    }

    /** Drops database-backed identity lookups after a change published by another Core instance. */
    public void invalidateDatabaseIdentityCache() {
        cachedPlayerUUID.clear();
        cachedPlayerName.clear();
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

    /** Seeds the non-blocking identity cache from an authoritative team/database snapshot. */
    public void cacheKnownIdentity(@NotNull String username, @NotNull UUID currentUuid) {
        cacheIdentity(username, currentUuid, java.util.Set.of());
    }

    private void cacheIdentity(@NotNull String username, @NotNull UUID currentUuid,
                               @NotNull java.util.Set<UUID> previousUuids) {
        String normalizedName = normalizeName(username);
        UUID replacedUuid = cachedPlayerUUID.put(normalizedName, currentUuid);
        if (replacedUuid != null && !replacedUuid.equals(currentUuid)) {
            cachedPlayerName.remove(replacedUuid);
            cachedPlayers.remove(replacedUuid);
        }
        for (UUID previousUuid : previousUuids) {
            if (previousUuid.equals(currentUuid)) continue;
            String previousName = cachedPlayerName.remove(previousUuid);
            if (previousName != null)
                cachedPlayerUUID.remove(normalizeName(previousName), previousUuid);
            cachedPlayers.remove(previousUuid);
        }
        cachedPlayerName.put(currentUuid, username);
    }

    private static String normalizeName(@NotNull String username) {
        return username.toLowerCase(Locale.ROOT);
    }
}
