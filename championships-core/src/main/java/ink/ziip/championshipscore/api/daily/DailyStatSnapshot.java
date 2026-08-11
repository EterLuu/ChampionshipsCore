package ink.ziip.championshipscore.api.daily;

/** Cached aggregate used by UI/PAPI; database access never occurs during placeholder rendering. */
public record DailyStatSnapshot(long gamesPlayed, long wins, long lineCount,
                                long completedTasks, long maxCompletedTasks) {
    public static final DailyStatSnapshot EMPTY = new DailyStatSnapshot(0, 0, 0, 0, 0);

    DailyStatSnapshot add(boolean won) {
        return add(won, 0, 0);
    }

    DailyStatSnapshot add(boolean won, long lines, long completed) {
        return new DailyStatSnapshot(gamesPlayed + 1, wins + (won ? 1 : 0),
                lineCount + Math.max(0L, lines), completedTasks + Math.max(0L, completed),
                Math.max(maxCompletedTasks, Math.max(0L, completed)));
    }
}
