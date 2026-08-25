package ink.ziip.championshipscore.api.player;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseManager;
import ink.ziip.championshipscore.api.player.dao.PlayerDao;
import ink.ziip.championshipscore.api.player.dao.PlayerDaoImpl;
import ink.ziip.championshipscore.api.player.entry.PlayerEntry;
import ink.ziip.championshipscore.api.player.entry.PlayerIdentityMigrationResult;
import ink.ziip.championshipscore.api.player.entry.PlayerUuidMigration;
import ink.ziip.championshipscore.api.player.identity.PlayerUuidLookupException;
import ink.ziip.championshipscore.api.player.identity.PlayerUuidSource;
import ink.ziip.championshipscore.api.player.identity.ProfileUuidResolver;
import ink.ziip.championshipscore.configuration.config.CCConfig;
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
import java.time.Duration;

public class PlayerManager extends BaseManager {
    private final Map<UUID, ChampionshipPlayer> cachedPlayers = new ConcurrentHashMap<>();
    private final Map<String, UUID> cachedPlayerUUID = new ConcurrentHashMap<>();
    private final Map<UUID, String> cachedPlayerName = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerIdentityMigrationResult> pendingIdentityMigrations = new ConcurrentHashMap<>();
    private final Map<String, Object> identityLocks = new ConcurrentHashMap<>();
    private final PlayerDao playerDao;
    private final ProfileUuidResolver profileUuidResolver;
    private final PlayerUuidSource uuidSource;

    public PlayerManager(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
        playerDao = new PlayerDaoImpl();
        uuidSource = PlayerUuidSource.parse(CCConfig.IDENTITY_MODE);
        uuidSource.validateConfiguration(CCConfig.IDENTITY_PROFILE_API_BASE_URL);
        profileUuidResolver = new ProfileUuidResolver(
                Duration.ofSeconds(Math.max(1L, CCConfig.IDENTITY_CONNECT_TIMEOUT_SECONDS)),
                Duration.ofSeconds(Math.max(1L, CCConfig.IDENTITY_REQUEST_TIMEOUT_SECONDS)));
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

    /** Applies an approved name change before the player logs in again. */
    @NotNull
    public CompletionStage<PlayerIdentityMigrationResult> migrateApprovedName(@NotNull String oldName,
                                                                                @NotNull String newName,
                                                                                @Nullable UUID replacementUuid) {
        return CompletableFuture.supplyAsync(() -> playerDao.migrateNameChange(oldName, newName, replacementUuid))
                .thenApply(migration -> {
                    if (!migration.successful()) return migration;
                    cacheIdentity(newName, migration.currentUuid(), migration.previousUuids());
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        plugin.getTeamManager().applyIdentityMigration(migration);
                        if (migration.migratedPointRows() > 0)
                            plugin.getRankManager().refreshAfterPendingPointWrites();
                        plugin.getRedisManager().publishDatabaseChange("player-name-changed",
                                DatabaseSyncDomain.PLAYER, DatabaseSyncDomain.TEAM, DatabaseSyncDomain.RANK);
                    });
                    return migration;
                });
    }

    /** Rewrites all durable Core identities in one database transaction. */
    public CompletionStage<Integer> migrateIdentities(@NotNull List<PlayerUuidMigration> players) {
        return CompletableFuture.supplyAsync(() -> playerDao.migrateIdentities(players))
                .thenCompose(changed -> {
                    invalidateDatabaseIdentityCache();
                    return plugin.getTeamManager().refreshFormalTeamsFromDatabase().thenCompose(ignored -> {
                        CompletableFuture<Integer> completed = new CompletableFuture<>();
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            try {
                                plugin.getRankManager().refreshAfterPendingPointWrites();
                                plugin.getRedisManager().publishDatabaseChange("player-identities-migrated",
                                        DatabaseSyncDomain.PLAYER, DatabaseSyncDomain.TEAM, DatabaseSyncDomain.RANK);
                                completed.complete(changed);
                            } catch (RuntimeException failure) {
                                completed.completeExceptionally(failure);
                            }
                        });
                        return completed;
                    });
                });
    }

    @NotNull
    public PlayerIdentityMigrationResult prepareIdentity(@NotNull String username, @NotNull UUID currentUuid) {
        PlayerIdentityMigrationResult modeFailure = validateOnlineUuid(username, currentUuid);
        if (modeFailure != null) {
            pendingIdentityMigrations.put(currentUuid, modeFailure);
            plugin.getLogger().severe(Utils.formatModuleLog("Player", "IdentitySync",
                    "玩家=" + username + " 当前UUID=" + currentUuid + " 模式校验失败=" + modeFailure.failureReason()));
            return modeFailure;
        }
        String normalizedName = normalizeName(username);
        PlayerIdentityMigrationResult result;
        Object identityLock = identityLocks.computeIfAbsent(normalizedName, ignored -> new Object());
        synchronized (identityLock) {
            result = playerDao.synchronizeIdentity(username, currentUuid);
            pendingIdentityMigrations.put(currentUuid, result);
        }

        if (!result.successful()) {
            plugin.getLogger().severe(Utils.formatModuleLog("Player", "IdentitySync",
                    "玩家=" + username + " 当前UUID=" + currentUuid + " 同步失败=" + result.failureReason()));
        } else if (result.hasTeamConflict()) {
            plugin.getLogger().warning(Utils.formatModuleLog("Player", "IdentitySync",
                    "玩家=" + username + " 当前UUID=" + currentUuid + " 旧UUID=" + result.previousUuids()
                            + " 队伍冲突=" + result.conflictingTeamIds() + "，未自动选择队伍"));
        } else if (result.changed()) {
            plugin.getLogger().info(Utils.formatModuleLog("Player", "IdentitySync",
                    "玩家=" + username + " 旧UUID=" + result.previousUuids() + " 新UUID=" + currentUuid
                            + " 队伍=" + result.resolvedTeamId() + " 迁移积分记录=" + result.migratedPointRows()));
        }
        return result;
    }

    /** Resolves the configured server identity without blocking the server thread. */
    public CompletionStage<UUID> resolvePlayerUUID(@NotNull String name) {
        if (!name.matches("[A-Za-z0-9_]{3,16}")) {
            return CompletableFuture.failedFuture(new PlayerUuidLookupException(
                    PlayerUuidLookupException.Reason.INVALID_USERNAME,
                    "Invalid Minecraft username: " + name));
        }
        String normalizedName = normalizeName(name);
        Player online = Bukkit.getOnlinePlayers().stream()
                .filter(player -> player.getName().equalsIgnoreCase(name)).findFirst().orElse(null);
        if (online != null) {
            PlayerIdentityMigrationResult modeFailure = validateOnlineUuid(online.getName(), online.getUniqueId());
            if (modeFailure != null) {
                return CompletableFuture.failedFuture(new PlayerUuidLookupException(
                        PlayerUuidLookupException.Reason.IDENTITY_CONFLICT, modeFailure.failureReason()));
            }
            cacheIdentity(online.getName(), online.getUniqueId(), java.util.Set.of());
            return CompletableFuture.completedFuture(online.getUniqueId());
        }
        CompletableFuture<UUID> resolved = new CompletableFuture<>();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                UUID uuid = resolveConfiguredUuid(name);
                PlayerIdentityMigrationResult migration = playerDao.synchronizeIdentity(name, uuid);
                if (!migration.successful() || migration.hasTeamConflict()) {
                    throw new PlayerUuidLookupException(PlayerUuidLookupException.Reason.IDENTITY_CONFLICT,
                            migration.failureReason() == null
                                    ? "Core identity conflict for " + name : migration.failureReason());
                }
                cachedPlayerUUID.put(normalizedName, uuid);
                cachedPlayerName.put(uuid, name);
                if (migration.changed()) {
                    plugin.getRedisManager().publishDatabaseChange("player-identity-resolved",
                            DatabaseSyncDomain.PLAYER, DatabaseSyncDomain.TEAM, DatabaseSyncDomain.RANK);
                }
                resolved.complete(uuid);
            } catch (Exception failure) {
                resolved.completeExceptionally(failure);
            }
        });
        return resolved;
    }

    /**
     * Resolves an offline player's UUID from the configured source without changing stored data.
     * This is never used for an online player: its UUID always comes from the proxy/Bukkit login profile.
     */
    public UUID resolveConfiguredUuid(@NotNull String name) throws PlayerUuidLookupException {
        if (!name.matches("[A-Za-z0-9_]{3,16}")) {
            throw new PlayerUuidLookupException(PlayerUuidLookupException.Reason.INVALID_USERNAME,
                    "Invalid Minecraft username: " + name);
        }
        if (uuidSource == PlayerUuidSource.PROFILE_UUID) {
            return profileUuidResolver.resolve(CCConfig.IDENTITY_PROFILE_API_BASE_URL, name);
        }
        return Utils.getPlayerUUID(name);
    }

    /** Returns a failed migration when an injected UUID contradicts OFFLINE mode. */
    private @Nullable PlayerIdentityMigrationResult validateOnlineUuid(@NotNull String username,
                                                                        @NotNull UUID currentUuid) {
        if (uuidSource != PlayerUuidSource.OFFLINE) return null;
        UUID expected = Utils.getPlayerUUID(username);
        if (expected.equals(currentUuid)) return null;
        return PlayerIdentityMigrationResult.failed(username, currentUuid,
                "identity.mode=OFFLINE requires UUID " + expected + " but login supplied " + currentUuid);
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
            } catch (Exception failure) {
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
