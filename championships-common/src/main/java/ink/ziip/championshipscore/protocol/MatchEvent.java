package ink.ziip.championshipscore.protocol;

import java.util.Map;
import java.util.UUID;

/** Worker event. seq is monotonic within one (matchId, epoch), starting at one. */
public record MatchEvent(
        int protocolVersion,
        UUID messageId,
        UUID matchId,
        long epoch,
        long seq,
        long createdAtEpochMilli,
        MatchEventType type,
        Map<String, String> attributes
) {
    public MatchEvent {
        ProtocolVersion.requireSupported(protocolVersion);
        ProtocolSupport.required(messageId, "messageId");
        ProtocolSupport.required(matchId, "matchId");
        if (epoch < 1) throw new IllegalArgumentException("epoch must be positive");
        if (seq < 1) throw new IllegalArgumentException("seq must be positive");
        if (createdAtEpochMilli < 1) throw new IllegalArgumentException("createdAtEpochMilli must be positive");
        ProtocolSupport.required(type, "type");
        attributes = ProtocolSupport.immutableAttributes(attributes);
    }
}
