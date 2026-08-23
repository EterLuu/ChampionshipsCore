package ink.ziip.championshipscore.api.daily.entry;

import ink.ziip.championshipscore.api.daily.DailyRecordType;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** Best timed achievement, scoped by game, map revision and DAILY rules revision. */
public record DailyRecordEntry(@NotNull UUID uuid, @NotNull String username,
                               @NotNull GameTypeEnum game, @NotNull String map,
                               @NotNull String mapRevision, @NotNull String rulesHash,
                               @NotNull DailyRecordType recordType, long durationMs,
                               @NotNull UUID matchId, @Nullable UUID achievedBy,
                               long achievedAt, int recordRank) {
    /**
     * Compatibility constructor for callers which only describe a newly achieved result. The DAO
     * recalculates the rank when it persists the candidate alongside the existing top three.
     */
    public DailyRecordEntry(@NotNull UUID uuid, @NotNull String username,
                            @NotNull GameTypeEnum game, @NotNull String map,
                            @NotNull String mapRevision, @NotNull String rulesHash,
                            @NotNull DailyRecordType recordType, long durationMs,
                            @NotNull UUID matchId, @Nullable UUID achievedBy, long achievedAt) {
        this(uuid, username, game, map, mapRevision, rulesHash, recordType, durationMs,
                matchId, achievedBy, achievedAt, 1);
    }

    /** Returns this record with the rank assigned by the top-three sorter. */
    public @NotNull DailyRecordEntry withRank(int rank) {
        return new DailyRecordEntry(uuid, username, game, map, mapRevision, rulesHash, recordType,
                durationMs, matchId, achievedBy, achievedAt, rank);
    }

    /** Short alias used by presentation code which treats this as a podium position. */
    public int rank() {
        return recordRank;
    }
}
