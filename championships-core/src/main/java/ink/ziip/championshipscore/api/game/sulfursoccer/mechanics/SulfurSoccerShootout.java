package ink.ziip.championshipscore.api.game.sulfursoccer.mechanics;

import ink.ziip.championshipscore.api.game.sulfursoccer.model.SulfurSoccerSide;

import java.util.List;
import java.util.UUID;

/** Alternating attempts, with each roster rotating through both shooting and keeping. */
public final class SulfurSoccerShootout {
    static final int ATTEMPTS_PER_SIDE = 4;
    static final int PREPARATION_TICKS = 3 * 20;
    static final int AIM_TICKS = 15 * 20;
    static final int FLIGHT_TICKS = 8 * 20;
    private final List<UUID> right;
    private final List<UUID> left;
    private int completed;
    private int rightGoals;
    private int leftGoals;
    private int elapsed;
    private int flightTicks;
    private boolean struck;
    private SulfurSoccerSide winner;

    public SulfurSoccerShootout(List<UUID> right, List<UUID> left) {
        if (right == null
                || left == null
                || right.isEmpty()
                || left.isEmpty()
                || right.size() > 4
                || left.size() > 4) throw new IllegalArgumentException("点球双方必须各有 1–4 名球员");
        this.right = List.copyOf(right);
        this.left = List.copyOf(left);
    }

    public SulfurSoccerSide shootingSide() {
        return completed % 2 == 0 ? SulfurSoccerSide.RIGHT : SulfurSoccerSide.LEFT;
    }

    private List<UUID> roster(SulfurSoccerSide side) {
        return side == SulfurSoccerSide.RIGHT ? right : left;
    }

    public UUID shooter() {
        return player(shootingSide());
    }

    public UUID keeper() {
        return player(shootingSide().opposite());
    }

    private UUID player(SulfurSoccerSide side) {
        List<UUID> players = roster(side);
        return players.get((completed / 2) % players.size());
    }

    public int round() {
        return completed / 2 + 1;
    }

    int attempts(SulfurSoccerSide side) {
        return (completed + (side == SulfurSoccerSide.RIGHT ? 1 : 0)) / 2;
    }

    public int goals(SulfurSoccerSide side) {
        return side == SulfurSoccerSide.RIGHT ? rightGoals : leftGoals;
    }

    public SulfurSoccerSide winner() {
        return winner;
    }

    public boolean preparing() {
        return elapsed < PREPARATION_TICKS;
    }

    public boolean struck() {
        return struck;
    }

    public boolean canStrike(UUID player) {
        return winner == null
                && !preparing()
                && elapsed < PREPARATION_TICKS + AIM_TICKS
                && !struck
                && shooter().equals(player);
    }

    public boolean strike(UUID player) {
        if (!canStrike(player)) return false;
        struck = true;
        return true;
    }

    /** Returns true when an unattempted shot or a ball still in flight runs out of time. */
    public boolean tick() {
        if (winner != null) return false;
        elapsed++;
        if (struck) return ++flightTicks >= FLIGHT_TICKS;
        return elapsed >= PREPARATION_TICKS + AIM_TICKS;
    }

    public int secondsRemaining() {
        int ticks =
                preparing()
                        ? PREPARATION_TICKS - elapsed
                        : struck
                                ? FLIGHT_TICKS - flightTicks
                                : PREPARATION_TICKS + AIM_TICKS - elapsed;
        return (Math.max(0, ticks) + 19) / 20;
    }

    public void finishAttempt(boolean scored) {
        if (winner != null) throw new IllegalStateException("点球大战已结束");
        if (scored && !struck) throw new IllegalStateException("尚未射门不能记为进球");
        if (scored) {
            if (shootingSide() == SulfurSoccerSide.RIGHT) rightGoals++;
            else leftGoals++;
        }
        completed++;
        // All eight initial attempts are played. Sudden death is decided only after equal attempts.
        if (completed >= ATTEMPTS_PER_SIDE * 2 && completed % 2 == 0 && rightGoals != leftGoals)
            winner = rightGoals > leftGoals ? SulfurSoccerSide.RIGHT : SulfurSoccerSide.LEFT;
        elapsed = 0;
        flightTicks = 0;
        struck = false;
    }
}
