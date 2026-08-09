package ink.ziip.championshipscore.protocol;

import java.util.UUID;

/** Immutable identity and role used by one match; it is not a live team-membership record. */
public record PlayerSnapshot(
        UUID uuid,
        String username,
        ParticipantRole role,
        Integer teamId,
        boolean requiredAtStart,
        double points
) {
    public PlayerSnapshot(UUID uuid, String username, ParticipantRole role, Integer teamId) {
        this(uuid, username, role, teamId, true, 0D);
    }

    public PlayerSnapshot(UUID uuid, String username, ParticipantRole role, Integer teamId,
                          boolean requiredAtStart) {
        this(uuid, username, role, teamId, requiredAtStart, 0D);
    }

    public PlayerSnapshot {
        ProtocolSupport.required(uuid, "uuid");
        username = ProtocolSupport.nonBlank(username, "username");
        ProtocolSupport.required(role, "role");
        if (role == ParticipantRole.PLAYER && teamId == null) {
            throw new IllegalArgumentException("A match player must have a teamId");
        }
        if (teamId != null && teamId < 0) {
            throw new IllegalArgumentException("teamId must be non-negative");
        }
        if (!Double.isFinite(points)) throw new IllegalArgumentException("points must be finite");
    }
}
