package ink.ziip.championshipscore.api.visibility;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.UUID;

/** Spectator-owned whitelist applied before the normal game visibility policy. */
public record PlayerVisibilityFilter(@NotNull Set<UUID> playerIds, @NotNull Set<Integer> teamIds) {
    public PlayerVisibilityFilter {
        playerIds = Set.copyOf(playerIds);
        teamIds = Set.copyOf(teamIds);
        if (playerIds.isEmpty() == teamIds.isEmpty())
            throw new IllegalArgumentException("exactly one visibility whitelist must be populated");
    }

    public static @NotNull PlayerVisibilityFilter players(@NotNull Set<UUID> playerIds) {
        return new PlayerVisibilityFilter(playerIds, Set.of());
    }

    public static @NotNull PlayerVisibilityFilter teams(@NotNull Set<Integer> teamIds) {
        return new PlayerVisibilityFilter(Set.of(), teamIds);
    }

    public boolean allows(@NotNull UUID targetId, @Nullable Integer targetTeamId) {
        return !playerIds.isEmpty() ? playerIds.contains(targetId)
                : targetTeamId != null && teamIds.contains(targetTeamId);
    }
}
