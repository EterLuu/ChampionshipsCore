package ink.ziip.championshipscore.authbridge.model;

import java.util.List;

/** Current website state: admitted players, and active bans (absent from older cc-web). */
public record BridgeSnapshot(List<BridgeSnapshotPlayer> players, List<BridgeSnapshotBan> bans) {}
