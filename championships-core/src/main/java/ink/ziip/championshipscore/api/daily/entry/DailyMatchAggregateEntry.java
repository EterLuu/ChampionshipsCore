package ink.ziip.championshipscore.api.daily.entry;

import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/** Per-player per-map aggregate folded from the historical {@code daily_match_results} rows. */
public record DailyMatchAggregateEntry(@NotNull UUID uuid, @NotNull GameTypeEnum game,
                                       @NotNull String map, long gamesPlayed, long wins,
                                       long maxLines, long maxCompletedTasks) {
}
