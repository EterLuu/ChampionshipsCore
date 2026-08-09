package ink.ziip.championshipscore.api.player.entry;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.UUID;

/**
 * Result of reconciling the UUID received during login with the persistent identity records.
 * A null {@code resolvedTeamId} means that the player currently has no unambiguous team.
 */
public record PlayerIdentityMigrationResult(
        @NotNull String username,
        @NotNull UUID currentUuid,
        @NotNull Set<UUID> previousUuids,
        @Nullable Integer resolvedTeamId,
        @NotNull Set<Integer> conflictingTeamIds,
        int migratedPointRows,
        boolean changed,
        boolean successful,
        @Nullable String failureReason
) {
    public PlayerIdentityMigrationResult {
        previousUuids = Set.copyOf(previousUuids);
        conflictingTeamIds = Set.copyOf(conflictingTeamIds);
    }

    public boolean hasTeamConflict() {
        return !conflictingTeamIds.isEmpty();
    }

    public static PlayerIdentityMigrationResult failed(@NotNull String username,
                                                        @NotNull UUID currentUuid,
                                                        @NotNull String reason) {
        return new PlayerIdentityMigrationResult(username, currentUuid, Set.of(), null,
                Set.of(), 0, false, false, reason);
    }
}
