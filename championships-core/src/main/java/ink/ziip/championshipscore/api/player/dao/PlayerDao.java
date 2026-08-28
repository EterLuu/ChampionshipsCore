package ink.ziip.championshipscore.api.player.dao;

import ink.ziip.championshipscore.api.player.entry.PlayerEntry;
import ink.ziip.championshipscore.api.player.entry.PlayerIdentityMigrationResult;
import ink.ziip.championshipscore.api.player.entry.PlayerUnknownRemovalResult;
import ink.ziip.championshipscore.api.player.entry.PlayerUuidMigration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface PlayerDao {
    @Nullable
    PlayerEntry getPlayer(UUID uuid);

    @Nullable
    PlayerEntry getPlayer(String name);

    /** Returns every persisted player identity for administrator selectors. */
    @NotNull
    List<PlayerEntry> getPlayerList();

    /** Reconciles every same-name persistent identity with the UUID received during login. */
    @NotNull
    PlayerIdentityMigrationResult synchronizeIdentity(@NotNull String name, @NotNull UUID currentUuid);

    /** Migrates an approved name change before the player logs in again. */
    @NotNull
    PlayerIdentityMigrationResult migrateNameChange(@NotNull String oldName, @NotNull String newName,
                                                    @Nullable UUID replacementUuid);

    /** Atomically rewrites every Core table that stores a player UUID. */
    int migrateIdentities(@NotNull List<PlayerUuidMigration> players);


    /** Atomically removes every Core UUID outside the authoritative allowlist. */
    @NotNull
    PlayerUnknownRemovalResult removeUnknown(@NotNull Set<UUID> allowedUuids);
}
