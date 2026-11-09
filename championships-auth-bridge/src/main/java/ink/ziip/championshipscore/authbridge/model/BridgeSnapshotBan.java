package ink.ziip.championshipscore.authbridge.model;

/** An active website ban in the binding snapshot; {@code expiresAt} is ISO-8601 or null. */
public record BridgeSnapshotBan(
        String accountId, String username, String reason, String expiresAt) {}
