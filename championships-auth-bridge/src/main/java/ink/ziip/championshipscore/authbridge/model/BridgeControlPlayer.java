package ink.ziip.championshipscore.authbridge.model;

public record BridgeControlPlayer(
        String accountId,
        String username,
        String passwordHash,
        String uuidSource,
        String minecraftUuid,
        String fromUuid,
        String toUuid
) {
}
