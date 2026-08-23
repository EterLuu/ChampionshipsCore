package ink.ziip.championshipscore.authbridge.model;

public record BridgeChange(
    String id,
    String accountId,
    String operation,
    int version,
    String authmeUsername,
    String oldAuthmeUsername,
    String passwordHash,
    String reason,
    String expiresAt
) {
}
