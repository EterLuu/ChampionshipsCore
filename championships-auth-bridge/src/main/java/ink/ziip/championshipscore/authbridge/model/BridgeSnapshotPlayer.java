package ink.ziip.championshipscore.authbridge.model;

public record BridgeSnapshotPlayer(String accountId, String username, String uuidSource, String minecraftUuid,
                                   String passwordHash, int version) {
}
