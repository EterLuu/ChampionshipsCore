package ink.ziip.championshipscore.api.game.iamnotarobot.model;

import java.util.UUID;

/** Tick deadlines belong to the station, including while the holder is disconnected. */
public final class SliderStationState {
    public static final int ROUNDS = 3;

    public enum Result {
        IGNORE,
        LOCKED,
        FAIL,
        ALIGNED
    }

    private enum Phase {
        ALIGNING,
        WAITING,
        CONFIRMED,
        COMPLETE
    }

    private UUID holder;
    private int round = 1, attempt, column, firstGrip = -1, lockedUntil;
    private int solveDuration;
    private Phase phase = Phase.ALIGNING;

    public UUID holder() {
        return holder;
    }

    public int round() {
        return round;
    }

    /** Switch the displayed picture during pass feedback, before advancing the puzzle. */
    public int pictureRound() {
        int picture = phase == Phase.CONFIRMED && round < ROUNDS ? round + 1 : round;
        return picture == 2 ? 2 : 1;
    }

    public int attempt() {
        return attempt;
    }

    public int column() {
        return column;
    }

    public boolean complete() {
        return phase == Phase.COMPLETE;
    }

    public boolean waiting() {
        return phase == Phase.WAITING;
    }

    public boolean aligning() {
        return phase == Phase.ALIGNING;
    }

    public boolean locked(int tick) {
        return phase != Phase.ALIGNING || tick < lockedUntil;
    }

    public int solveDuration() {
        return solveDuration;
    }

    public boolean intruder(UUID id) {
        return holder != null && !holder.equals(id);
    }

    public boolean grip(UUID id, int tick) {
        if (holder != null || locked(tick)) return false;
        holder = id;
        column = 0;
        if (firstGrip < 0) firstGrip = tick;
        return true;
    }

    public boolean move(UUID id, int next, int tick) {
        if (!id.equals(holder) || locked(tick) || next < 0 || next > 22 || next == column)
            return false;
        column = next;
        return true;
    }

    public Result submit(UUID id, int current, int target, int tick) {
        if (!id.equals(holder) || current < 0 || current > 22) return Result.IGNORE;
        if (locked(tick)) return Result.LOCKED;
        column = current;
        if (current == target) {
            phase = Phase.WAITING;
            solveDuration = tick - firstGrip;
            return Result.ALIGNED;
        }
        lockedUntil = tick + 40;
        attempt++;
        column = 0;
        return Result.FAIL;
    }

    /** An unclaimed opposite rail plays solo. A late claimant catches up on earlier rounds. */
    public boolean confirm(SliderStationState opposite, int tick) {
        if (!waiting()) return false;
        if (opposite != null
                && (opposite.round < round
                        || (opposite.round == round && opposite.phase == Phase.ALIGNING))) {
            return false;
        }
        phase = Phase.CONFIRMED;
        lockedUntil = tick + 30;
        // A late partner may align while a previously solo pass is still on screen.
        if (opposite != null && opposite.round == round && opposite.phase == Phase.CONFIRMED)
            opposite.lockedUntil = lockedUntil;
        return true;
    }

    /** A holder who has met the personal quota is released and hands the next round back. */
    public boolean advance(int tick, boolean release) {
        if (phase != Phase.CONFIRMED || tick < lockedUntil) return false;
        column = 0;
        if (round == ROUNDS) {
            phase = Phase.COMPLETE;
            holder = null;
        } else {
            phase = Phase.ALIGNING;
            round++;
            attempt = 0;
            if (release) holder = null;
            firstGrip = holder == null ? -1 : tick;
        }
        return true;
    }

    public void leave(UUID id) {
        if (id.equals(holder)) {
            holder = null;
            column = 0;
        }
    }
}
