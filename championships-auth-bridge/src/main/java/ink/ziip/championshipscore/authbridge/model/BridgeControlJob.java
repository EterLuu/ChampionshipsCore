package ink.ziip.championshipscore.authbridge.model;

import java.util.List;

public record BridgeControlJob(
        String id,
        String operation,
        String identityMode,
        String fromMode,
        String toMode,
        List<BridgeControlPlayer> players
) {
}
