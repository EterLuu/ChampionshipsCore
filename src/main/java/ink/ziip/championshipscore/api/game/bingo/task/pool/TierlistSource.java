package ink.ziip.championshipscore.api.game.bingo.task.pool;

/**
 * Static holder for the active {@link Tierlist} — the source of objective difficulty. Installed at
 * startup. When empty, objectives fall back to MEDIUM.
 */
public final class TierlistSource {
    private static volatile Tierlist active = Tierlist.EMPTY;
    private static volatile String activeName = "";

    private TierlistSource() {
    }

    public static void set(Tierlist tierlist, String name) {
        active = tierlist == null ? Tierlist.EMPTY : tierlist;
        activeName = name == null ? "" : name;
    }

    public static Tierlist active() {
        return active;
    }

    public static String activeName() {
        return activeName;
    }
}
