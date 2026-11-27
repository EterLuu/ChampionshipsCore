package ink.ziip.championshipscore.api.game.craftingfeast.model;

/** One reserved table, from entry to departure. Only seated customers accept a dish. */
public final class FeastOrder {
    public enum Phase {
        ENTERING,
        ORDERING,
        SEATING,
        WAITING,
        LEAVING,
        FINISHED
    }

    private final String recipe;
    private final int patience;
    private Phase phase = Phase.ENTERING;
    private int remaining;
    private int orderingTicks;

    public static final int ORDERING_TICKS = 8 * 20;

    public FeastOrder(String recipe, int patience) {
        if (recipe == null || recipe.isBlank() || patience < 1)
            throw new IllegalArgumentException("Invalid order");
        this.recipe = recipe;
        this.patience = patience;
        remaining = patience;
    }

    public String recipe() {
        return recipe;
    }

    public Phase phase() {
        return phase;
    }

    public int remaining() {
        return remaining;
    }

    public int patience() {
        return patience;
    }

    /** A player taking the order at the counter skips the rest of the ordering pause. */
    public boolean takeOrder() {
        if (phase != Phase.ORDERING || orderingTicks == 0) return false;
        orderingTicks = 0;
        return true;
    }

    public void seat() {
        if (phase == Phase.SEATING) phase = Phase.WAITING;
    }

    public void reachCounter() {
        if (phase == Phase.ENTERING) {
            phase = Phase.ORDERING;
            orderingTicks = ORDERING_TICKS;
        }
    }

    public int orderingSeconds() {
        return (orderingTicks + 19) / 20;
    }

    /** Uses the area's movement clock, so arriving between second boundaries still waits 8s. */
    public boolean advanceOrdering(int ticks) {
        if (ticks < 0) throw new IllegalArgumentException("Negative elapsed ticks");
        if (phase != Phase.ORDERING) return false;
        orderingTicks = Math.max(0, orderingTicks - ticks);
        if (orderingTicks != 0) return false;
        phase = Phase.SEATING;
        return true;
    }

    public boolean tick() {
        if (phase != Phase.WAITING) return false;
        if (--remaining > 0) return false;
        phase = Phase.LEAVING;
        return true;
    }

    /**
     * Returns -1 without mutation for wrong, expired, moving or already served orders. The tip
     * follows remaining patience and is scaled by the restaurant's reputation percentage.
     */
    public int serve(String dish, int basePoints, int maximumTip, int streak, int tipPercent) {
        if (phase != Phase.WAITING || !recipe.equals(dish)) return -1;
        if (tipPercent < 0) throw new IllegalArgumentException("Negative tip percentage");
        phase = Phase.LEAVING;
        return basePoints
                + maximumTip * remaining * tipPercent / (patience * 100)
                + Math.min(5, Math.max(0, streak)) * 5;
    }

    public void leave() {
        if (phase != Phase.FINISHED) phase = Phase.LEAVING;
    }

    public void finish() {
        phase = Phase.FINISHED;
    }
}
