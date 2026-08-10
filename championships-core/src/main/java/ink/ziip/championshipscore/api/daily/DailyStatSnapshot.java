package ink.ziip.championshipscore.api.daily;

/** Cached aggregate used by UI/PAPI; database access never occurs during placeholder rendering. */
public record DailyStatSnapshot(long gamesPlayed, long wins, double totalPoints, double bestPoints) {
    public static final DailyStatSnapshot EMPTY = new DailyStatSnapshot(0, 0, 0D, 0D);

    DailyStatSnapshot add(boolean won, double points) {
        return new DailyStatSnapshot(gamesPlayed + 1, wins + (won ? 1 : 0),
                totalPoints + points, Math.max(bestPoints, points));
    }
}
