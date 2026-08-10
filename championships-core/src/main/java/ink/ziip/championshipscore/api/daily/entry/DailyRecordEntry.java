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
                               long achievedAt) {
}
