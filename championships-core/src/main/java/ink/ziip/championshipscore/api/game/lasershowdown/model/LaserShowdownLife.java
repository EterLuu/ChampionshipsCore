package ink.ziip.championshipscore.api.game.lasershowdown.model;

/** Weapon-selection ownership for one life; menu grace belongs to that same life only. */
public final class LaserShowdownLife {
    public static final int SELECTION_TICKS = 120;
    private final int deadline;
    private boolean locked;
    private boolean menuOpen;
    private boolean counted;

    public LaserShowdownLife(int startTick) {
        deadline = startTick + SELECTION_TICKS;
    }

    public boolean openMenu(int tick) {
        expire(tick);
        if (locked || tick >= deadline) return false;
        menuOpen = true;
        return true;
    }

    public boolean canSelect(int tick) {
        expire(tick);
        return !locked && (tick < deadline || menuOpen);
    }

    public void closeMenu(int tick) {
        menuOpen = false;
        expire(tick);
    }

    public void expire(int tick) {
        if (tick >= deadline && !menuOpen) locked = true;
    }

    /** A fired bullet, accepted enemy damage, death or match end ends selection immediately. */
    public void lock() {
        locked = true;
        menuOpen = false;
    }

    public boolean locked() {
        return locked;
    }

    /** A locked life contributes its final weapon exactly once. */
    public boolean takeUsage() {
        if (!locked || counted) return false;
        counted = true;
        return true;
    }
}
