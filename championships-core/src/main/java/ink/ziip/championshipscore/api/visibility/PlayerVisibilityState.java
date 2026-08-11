package ink.ziip.championshipscore.api.visibility;

import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.UUID;

/**
 * Immutable visibility state. It deliberately contains no Bukkit Player references so it survives reconnects.
 */
public record PlayerVisibilityState(
        @NotNull PlayerVisibilityMode mode,
        @NotNull Set<Integer> teamIds,
        @NotNull Set<UUID> playerIds,
        @NotNull String owner,
        @NotNull String reason
) {
    public PlayerVisibilityState {
        teamIds = Set.copyOf(teamIds);
        playerIds = Set.copyOf(playerIds);
    }

    public static @NotNull PlayerVisibilityState all(@NotNull String owner, @NotNull String reason) {
        return new PlayerVisibilityState(PlayerVisibilityMode.ALL, Set.of(), Set.of(), owner, reason);
    }

    public static @NotNull PlayerVisibilityState teammates(@NotNull String owner, @NotNull String reason) {
        return new PlayerVisibilityState(PlayerVisibilityMode.TEAMMATES, Set.of(), Set.of(), owner, reason);
    }

    public static @NotNull PlayerVisibilityState self(@NotNull String owner, @NotNull String reason) {
        return new PlayerVisibilityState(PlayerVisibilityMode.SELF, Set.of(), Set.of(), owner, reason);
    }

    public static @NotNull PlayerVisibilityState teams(@NotNull Set<Integer> teamIds,
                                                        @NotNull String owner, @NotNull String reason) {
        return new PlayerVisibilityState(PlayerVisibilityMode.TEAMS, teamIds, Set.of(), owner, reason);
    }

    public static @NotNull PlayerVisibilityState players(@NotNull Set<UUID> playerIds,
                                                          @NotNull String owner, @NotNull String reason) {
        return new PlayerVisibilityState(PlayerVisibilityMode.PLAYERS, Set.of(), playerIds, owner, reason);
    }
}
