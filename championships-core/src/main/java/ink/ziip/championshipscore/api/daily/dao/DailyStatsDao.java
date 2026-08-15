package ink.ziip.championshipscore.api.daily.dao;

import ink.ziip.championshipscore.api.daily.entry.DailyMapStatEntry;
import ink.ziip.championshipscore.api.daily.entry.DailyMatchAggregateEntry;
import ink.ziip.championshipscore.api.daily.entry.DailyMatchResultEntry;
import ink.ziip.championshipscore.api.daily.entry.DailyRecordEntry;
import ink.ziip.championshipscore.api.daily.entry.DailyStatEntry;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** Database boundary for DAILY statistics and records. */
public interface DailyStatsDao {
    @NotNull List<DailyStatEntry> getPlayerStats();

    @NotNull List<DailyRecordEntry> getPlayerRecords();

    @NotNull List<DailyMapStatEntry> getPlayerMapStats();

    /** Per-player per-map counts and Bingo maxima folded from every stored match result. */
    @NotNull List<DailyMatchAggregateEntry> getMatchResultMapAggregates();

    boolean saveMatch(@NotNull List<DailyMatchResultEntry> results);

    boolean saveRecords(@NotNull List<DailyRecordEntry> records);

    boolean saveMapStats(@NotNull List<DailyMapStatEntry> stats);
}
