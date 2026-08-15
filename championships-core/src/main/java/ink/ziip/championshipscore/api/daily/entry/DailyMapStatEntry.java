package ink.ziip.championshipscore.api.daily.entry;

import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/** Persisted per-player per-map aggregate for one DAILY game. */
public record DailyMapStatEntry(@NotNull UUID uuid, @NotNull String username,
                                 @NotNull GameTypeEnum game, @NotNull String map,
                                 long gamesPlayed, long wins,
                                 long maxTasks, long maxLines, long maxFirstTasks,
                                 double maxDragonDamage,
                                 long firstLiberate, long firstNextGen, long firstGateway,
                                 long updatedAt) {
}
