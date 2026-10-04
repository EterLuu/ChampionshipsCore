package ink.ziip.championshipscore.api.game.riptiderush.runtime;

/** Shared post-challenge stop; advanced by the existing course tick, never a separate task. */
public final class RiptideDeparture {
    public static final int WAIT_TICKS = 40;
    private int remaining;

    public void begin() {
        remaining = WAIT_TICKS;
    }

    public boolean active() {
        return remaining > 0;
    }

    public boolean tick() {
        if (!active()) return false;
        remaining--;
        return true;
    }

    void clear() {
        remaining = 0;
    }
}
