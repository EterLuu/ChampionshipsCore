package ink.ziip.championshipscore.authbridge.model;

import java.util.List;

public record BridgeSnapshot(String identityMode, List<BridgeSnapshotPlayer> players) {
}
