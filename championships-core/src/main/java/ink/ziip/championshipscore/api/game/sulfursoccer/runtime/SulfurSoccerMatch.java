package ink.ziip.championshipscore.api.game.sulfursoccer.runtime;

import ink.ziip.championshipscore.api.game.sulfursoccer.model.SulfurSoccerSide;

/** Goal totals are local to the finale and never contribute leaderboard points. */
public final class SulfurSoccerMatch {
    public static final int REGULATION_SECONDS = 12 * 60;
    private final int goalsToWin;
    private int rightGoals;
    private int leftGoals;
    private boolean playing;
    private int warmupTicks;
    private int regulationTicks = REGULATION_SECONDS * 20;
    private boolean regulationStarted;
    private boolean shootout;
    private SulfurSoccerSide winner;

    public SulfurSoccerMatch(int goalsToWin) {
        if (goalsToWin < 1) throw new IllegalArgumentException("获胜球数必须为正数");
        this.goalsToWin = goalsToWin;
    }

    public void kickOff() {
        if (winner == null && !warmingUp() && regulationTicks > 0) {
            regulationStarted = true;
            playing = true;
        }
    }

    public void startWarmup(int seconds) {
        if (seconds < 1
                || playing
                || regulationStarted
                || winner != null
                || rightGoals != 0
                || leftGoals != 0) throw new IllegalStateException("试踢只能在比赛开始前启动");
        warmupTicks = Math.multiplyExact(seconds, 20);
        playing = true;
    }

    /**
     * Called only while everyone is online; the warmup ends after exactly seconds * 20 live ticks.
     */
    public boolean tickWarmup() {
        if (!warmingUp()) return false;
        if (--warmupTicks > 0) return false;
        playing = false;
        return true;
    }

    public boolean warmingUp() {
        return warmupTicks > 0;
    }

    public int warmupSeconds() {
        return (warmupTicks + 19) / 20;
    }

    /** Includes goal/restart countdowns. The area calls this only while everyone is online. */
    public boolean tickRegulation() {
        if (!regulationStarted || winner != null || regulationTicks == 0) return false;
        if (--regulationTicks > 0) return false;
        playing = false;
        if (rightGoals == leftGoals) shootout = true;
        else winner = rightGoals > leftGoals ? SulfurSoccerSide.RIGHT : SulfurSoccerSide.LEFT;
        return true;
    }

    public int regulationSeconds() {
        return (regulationTicks + 19) / 20;
    }

    public boolean shootout() {
        return shootout;
    }

    public void stopPlay() {
        playing = false;
    }

    /** The side identifies the goal's defending team, including own goals. */
    public boolean enterGoal(SulfurSoccerSide defendingSide) {
        if (!playing || warmingUp() || winner != null) return false;
        playing = false;
        SulfurSoccerSide scorer = defendingSide.opposite();
        if (scorer == SulfurSoccerSide.RIGHT) rightGoals++;
        else leftGoals++;
        if (goals(scorer) >= goalsToWin) winner = scorer;
        return true;
    }

    public int goals(SulfurSoccerSide side) {
        return side == SulfurSoccerSide.RIGHT ? rightGoals : leftGoals;
    }

    public SulfurSoccerSide winner() {
        return winner;
    }

    public boolean playing() {
        return playing;
    }
}
