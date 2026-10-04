package ink.ziip.championshipscore.api.visibility;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** Pure policy function kept separate from Bukkit packet application for deterministic tests. */
public final class PlayerVisibilityPolicy {
    private PlayerVisibilityPolicy() {}

    public static boolean allows(
            PlayerVisibilityState state,
            UUID viewerId,
            UUID targetId,
            boolean viewerAlwaysSeesAll,
            boolean targetIsCorrespondingSpectator,
            boolean sameTeam,
            @Nullable Integer targetTeamId,
            @Nullable UUID viewerSession,
            @Nullable UUID targetSession) {
        return allows(
                state,
                viewerId,
                targetId,
                viewerAlwaysSeesAll,
                false,
                false,
                targetIsCorrespondingSpectator,
                sameTeam,
                targetTeamId,
                viewerSession,
                targetSession);
    }

    /**
     * Applies the common spectator rule before game-specific visibility policies. Spectators see
     * all entities; participants never see spectators.
     */
    public static boolean allows(
            PlayerVisibilityState state,
            UUID viewerId,
            UUID targetId,
            boolean viewerAlwaysSeesAll,
            boolean viewerIsSpectator,
            boolean targetIsSpectator,
            boolean targetIsCorrespondingSpectator,
            boolean sameTeam,
            @Nullable Integer targetTeamId,
            @Nullable UUID viewerSession,
            @Nullable UUID targetSession) {
        if (viewerId.equals(targetId)) return true;
        if (viewerIsSpectator) return true;
        // A live participant must never receive a spectator entity, even when its game policy is
        // ALL.
        // Unjoined viewers retain the normal unrestricted lobby visibility.
        if (!viewerIsSpectator && targetIsSpectator && !viewerAlwaysSeesAll) return false;
        if (viewerAlwaysSeesAll) return true;
        if (targetIsCorrespondingSpectator) return false;
        if (viewerSession != null && targetSession != null && !viewerSession.equals(targetSession))
            return false;
        return switch (state.mode()) {
            case ALL -> true;
            case TEAMMATES -> sameTeam;
            case SELF -> false;
            case TEAMS -> targetTeamId != null && state.teamIds().contains(targetTeamId);
            case PLAYERS -> state.playerIds().contains(targetId);
        };
    }
}
