package ink.ziip.championshipscore.protocol.transport;

import java.util.UUID;

public record RouteReceipt(UUID playerId, String serverName, boolean accepted, String detail) {
    public RouteReceipt {
        if (playerId == null) throw new IllegalArgumentException("playerId is required");
        if (serverName == null || serverName.isBlank()) throw new IllegalArgumentException("serverName is required");
        detail = detail == null ? "" : detail;
    }
}
