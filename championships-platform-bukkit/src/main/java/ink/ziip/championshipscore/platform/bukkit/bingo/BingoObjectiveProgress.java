package ink.ziip.championshipscore.platform.bukkit.bingo;

import java.util.UUID;

/** Mutable per-player counters consumed by shared Bingo objective rules. */
public interface BingoObjectiveProgress {
    void recordDistinct(UUID playerId, String bucket, String value);

    int distinctCount(UUID playerId, String bucket);

    void increment(UUID playerId, String bucket);

    int count(UUID playerId, String bucket);

    long observeElapsed(UUID playerId, String bucket, boolean active);
}
