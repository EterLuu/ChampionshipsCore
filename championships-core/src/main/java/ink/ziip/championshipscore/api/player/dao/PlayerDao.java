package ink.ziip.championshipscore.api.player.dao;

import ink.ziip.championshipscore.api.player.entry.PlayerEntry;
import ink.ziip.championshipscore.api.player.entry.PlayerIdentityMigrationResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public interface PlayerDao {
    void addPlayer(@NotNull String name, @NotNull UUID uuid);

    @Nullable
    PlayerEntry getPlayer(UUID uuid);

    @Nullable
    PlayerEntry getPlayer(String name);

    /** Returns every persisted player identity for administrator selectors. */
    @NotNull
    List<PlayerEntry> getPlayerList();

    void deletePlayer(UUID uuid);

    /** Reconciles every same-name persistent identity with the UUID received during login. */
    @NotNull
    PlayerIdentityMigrationResult synchronizeIdentity(@NotNull String name, @NotNull UUID currentUuid);
}
