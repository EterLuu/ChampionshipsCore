package ink.ziip.championshipscore.api.game.riptiderush;

/** Shared post-challenge stop; advanced by the existing course tick, never a separate task. */
final class RiptideDeparture {
    static final int WAIT_TICKS = 40;
    private int remaining;

    void begin() { remaining = WAIT_TICKS; }
    boolean active() { return remaining > 0; }
    boolean tick() {
        if (!active()) return false;
        remaining--;
        return true;
    }
    void clear() { remaining = 0; }
}
