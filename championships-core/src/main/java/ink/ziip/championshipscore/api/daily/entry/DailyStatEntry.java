package ink.ziip.championshipscore.api.daily.entry;

import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/** Persisted aggregate for one player and one DAILY game. */
public record DailyStatEntry(@NotNull UUID uuid, @NotNull String username,
                             @NotNull GameTypeEnum game, long gamesPlayed, long wins,
                             long lineCount, long completedTasks, long maxCompletedTasks,
                             long updatedAt) {
}
