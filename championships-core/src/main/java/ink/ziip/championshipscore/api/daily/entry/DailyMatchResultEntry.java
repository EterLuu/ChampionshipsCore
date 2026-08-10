package ink.ziip.championshipscore.api.daily.entry;

import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/** One player's immutable result row in a DAILY match. */
public record DailyMatchResultEntry(@NotNull UUID matchId, @NotNull UUID uuid,
                                    @NotNull String username, @NotNull GameTypeEnum game,
                                    @NotNull String map, @NotNull String teamKey,
                                    double points, boolean won, long finishedAt) {
}
