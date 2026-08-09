package ink.ziip.championshipscore.protocol;

import java.util.UUID;

/** Proxy-side desired route. A newer epoch fences stale requests for the same player. */
public record PlayerRoute(
        UUID playerId,
        UUID matchId,
        long epoch,
        String serverName,
        ParticipantRole role,
        long expiresAtEpochMilli
) {
    public PlayerRoute {
        ProtocolSupport.required(playerId, "playerId");
        ProtocolSupport.required(matchId, "matchId");
        if (epoch < 1) throw new IllegalArgumentException("epoch must be positive");
        serverName = ProtocolSupport.nonBlank(serverName, "serverName");
        ProtocolSupport.required(role, "role");
        if (expiresAtEpochMilli < 1) throw new IllegalArgumentException("expiresAtEpochMilli must be positive");
    }

    public boolean expiredAt(long epochMilli) {
        return epochMilli >= expiresAtEpochMilli;
    }
}
