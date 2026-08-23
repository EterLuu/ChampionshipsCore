package ink.ziip.championshipscore.authbridge.model;

import java.util.List;

public record BridgeChangeBatch(List<BridgeChange> changes, String nextCursor) {
}
