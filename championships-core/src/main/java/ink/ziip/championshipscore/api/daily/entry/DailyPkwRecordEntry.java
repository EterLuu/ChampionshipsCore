package ink.ziip.championshipscore.api.daily.entry;

import ink.ziip.championshipscore.api.daily.DailyPkwRecordType;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/** Persisted same-run composite result for one DAILY Parkour Warrior map. */
public record DailyPkwRecordEntry(@NotNull UUID uuid, @NotNull String username,
                                  @NotNull String map, @NotNull DailyPkwRecordType recordType,
                                  double primaryValue, long durationMs, @NotNull UUID matchId,
                                  long achievedAt) {
}
