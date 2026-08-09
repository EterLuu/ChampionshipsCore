package ink.ziip.championshipscore.protocol;

/** Wire compatibility version shared by SCC, Bingo workers and proxy adapters. */
public final class ProtocolVersion {
    public static final int CURRENT = 6;

    private ProtocolVersion() {
    }

    public static void requireSupported(int version) {
        if (version != CURRENT) {
            throw new IllegalArgumentException(
                    "Unsupported championships protocol version " + version + ", expected " + CURRENT);
        }
    }
}
