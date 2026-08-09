package ink.ziip.championshipscore.protocol;

/** Core-owned map position translated by each worker onto its configured physical world names. */
public record BingoLocationSnapshot(
        BingoDimension dimension,
        double x,
        double y,
        double z,
        float yaw,
        float pitch
) {
    public BingoLocationSnapshot {
        ProtocolSupport.required(dimension, "dimension");
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || !Float.isFinite(yaw) || !Float.isFinite(pitch)) {
            throw new IllegalArgumentException("Bingo location coordinates must be finite");
        }
    }
}
