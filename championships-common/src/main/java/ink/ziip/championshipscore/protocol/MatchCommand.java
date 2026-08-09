package ink.ziip.championshipscore.protocol;

import java.util.Map;
import java.util.UUID;

public record MatchCommand(
        int protocolVersion,
        UUID messageId,
        UUID matchId,
        long epoch,
        long createdAtEpochMilli,
        MatchCommandType type,
        Map<String, String> attributes
) {
    public MatchCommand {
        ProtocolVersion.requireSupported(protocolVersion);
        ProtocolSupport.required(messageId, "messageId");
        ProtocolSupport.required(matchId, "matchId");
        if (epoch < 1) throw new IllegalArgumentException("epoch must be positive");
        if (createdAtEpochMilli < 1) throw new IllegalArgumentException("createdAtEpochMilli must be positive");
        ProtocolSupport.required(type, "type");
        attributes = ProtocolSupport.immutableAttributes(attributes);
    }
}
