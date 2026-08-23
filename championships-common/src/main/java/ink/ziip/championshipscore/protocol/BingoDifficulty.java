package ink.ziip.championshipscore.protocol;

/** Card difficulty selected by DAILY players. */
public enum BingoDifficulty {
    EASY(new int[]{14, 5, 1, 0, 0}, 15 * 60, false, -1),
    LITE(new int[]{8, 8, 3, 1, 0}, 30 * 60, false, -1),
    NORMAL(new int[]{3, 6, 6, 3, 1}, 45 * 60, false, 0),
    HARD(new int[]{2, 4, 5, 6, 3}, 60 * 60, true, 1),
    EXTREME(new int[]{1, 2, 3, 6, 6}, 90 * 60, true, 2);

    private final int[] tierWeights;
    private final int durationSeconds;
    private final boolean clearsInventoryOnDeath;
    private final int maxEndTasks;

    BingoDifficulty(int[] tierWeights, int durationSeconds, boolean clearsInventoryOnDeath, int maxEndTasks) {
        this.tierWeights = tierWeights;
        this.durationSeconds = durationSeconds;
        this.clearsInventoryOnDeath = clearsInventoryOnDeath;
        this.maxEndTasks = maxEndTasks;
    }

    public int[] tierWeights() { return tierWeights.clone(); }
    public int durationSeconds() { return durationSeconds; }
    public boolean clearsInventoryOnDeath() { return clearsInventoryOnDeath; }
    /** -1 means unlimited; 0 disables End tasks. */
    public int maxEndTasks() { return maxEndTasks; }
}
