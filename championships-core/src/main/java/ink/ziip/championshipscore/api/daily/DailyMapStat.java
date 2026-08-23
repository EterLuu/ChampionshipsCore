package ink.ziip.championshipscore.api.daily;

import org.jetbrains.annotations.NotNull;

/**
 * Cached per-player per-map aggregate used by the unified stats and leaderboard menus.
 * {@code gamesPlayed}/{@code wins} cover the full match-result history (backfilled at load),
 * while {@code trackedGames} counts only matches recorded by the per-map stat table itself -
 * the denominator for rate metrics, whose numerators were not tracked before that table existed.
 */
public record DailyMapStat(long gamesPlayed, long wins, long trackedGames,
                           long maxTasks, long maxLines, long maxFirstTasks,
                           double maxDragonDamage,
                           long firstLiberate, long firstNextGen, long firstGateway,
                           long maxStars, long finishes) {
    public static final DailyMapStat EMPTY = new DailyMapStat(0L, 0L, 0L, 0L, 0L, 0L, 0D, 0L, 0L, 0L, 0L, 0L);

    /** Peak values keep the maximum, counters accumulate - mirrors the SQL upsert semantics. */
    public DailyMapStat merge(@NotNull DailyMapStat other) {
        return new DailyMapStat(gamesPlayed + other.gamesPlayed, wins + other.wins,
                trackedGames + other.trackedGames,
                Math.max(maxTasks, other.maxTasks), Math.max(maxLines, other.maxLines),
                Math.max(maxFirstTasks, other.maxFirstTasks),
                Math.max(maxDragonDamage, other.maxDragonDamage),
                firstLiberate + other.firstLiberate, firstNextGen + other.firstNextGen,
                firstGateway + other.firstGateway,
                Math.max(maxStars, other.maxStars), finishes + other.finishes);
    }
}
