package ink.ziip.championshipscore.protocol;

/** Wire compatibility version shared by SCC, Bingo workers and proxy adapters. */
public final class ProtocolVersion {
    /** Version 10 adds Worker-confirmed test resource release, including fresh-world recovery. */
    public static final int CURRENT = 10;

    private ProtocolVersion() {}

    public static void requireSupported(int version) {
        if (version != CURRENT) {
            throw new IllegalArgumentException(
                    "Unsupported championships protocol version "
                            + version
                            + ", expected "
                            + CURRENT);
        }
    }
}
