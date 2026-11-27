package ink.ziip.championshipscore.api.game.iamnotarobot.model;

/** Per-lane state and generation token for rejecting stale animation work. */
public final class JointRigState {
    public enum Phase {
        DORMANT,
        CARVING,
        READY,
        TESTING,
        COMPLETE
    }

    private Phase phase = Phase.DORMANT;
    private int round = 1;
    private long epoch;
    private long readyTick;
    private boolean demoUsed, demo;

    public Phase phase() {
        return phase;
    }

    public int round() {
        return round;
    }

    public long epoch() {
        return epoch;
    }

    public boolean current(long token, Phase expected, int expectedRound) {
        return epoch == token && phase == expected && round == expectedRound;
    }

    public boolean canEdit() {
        return phase == Phase.READY;
    }

    public boolean canPress() {
        return phase == Phase.READY;
    }

    public long duration(long tick) {
        return Math.max(0, tick - readyTick);
    }

    public boolean open() {
        if (phase != Phase.DORMANT) return false;
        phase = Phase.CARVING;
        epoch++;
        return true;
    }

    public boolean ready(long token, long tick) {
        if (epoch != token || phase != Phase.CARVING) return false;
        phase = Phase.READY;
        readyTick = tick;
        return true;
    }

    public boolean press() {
        if (!canPress()) return false;
        phase = Phase.TESTING;
        epoch++;
        return true;
    }

    public boolean pressDemo() {
        if (demoUsed || round != 1 || !press()) return false;
        demo = demoUsed = true;
        return true;
    }

    public boolean demo() {
        return demo;
    }

    public boolean finish(long token, JointFit.Outcome outcome, long tick) {
        if (epoch != token || phase != Phase.TESTING) return false;
        if (demo) {
            demo = false;
            readyTick = tick;
            phase = Phase.READY;
        } else if (outcome != JointFit.Outcome.PASS) {
            phase = Phase.READY;
        } else if (round == 3) {
            phase = Phase.COMPLETE;
            epoch++;
        } else {
            round++;
            phase = Phase.CARVING;
            epoch++;
        }
        return true;
    }

    public void reset() {
        phase = Phase.DORMANT;
        round = 1;
        demoUsed = demo = false;
        readyTick = 0;
        epoch++;
    }
}
