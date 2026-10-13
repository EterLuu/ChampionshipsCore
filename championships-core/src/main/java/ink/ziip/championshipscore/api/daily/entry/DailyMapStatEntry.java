package ink.ziip.championshipscore.api.daily.entry;

import ink.ziip.championshipscore.api.game.lasershowdown.model.LaserShowdownStats;
import ink.ziip.championshipscore.api.game.model.GameTypeEnum;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/** Persisted per-player per-map aggregate for one DAILY game. */
public record DailyMapStatEntry(
        @NotNull UUID uuid,
        @NotNull String username,
        @NotNull GameTypeEnum game,
        @NotNull String map,
        long gamesPlayed,
        long wins,
        long maxTasks,
        long maxLines,
        long maxFirstTasks,
        double maxDragonDamage,
        long firstLiberate,
        long firstNextGen,
        long firstGateway,
        long maxStars,
        long finishes,
        long updatedAt,
        LaserShowdownStats laser) {
    public DailyMapStatEntry(
            UUID uuid,
            String username,
            GameTypeEnum game,
            String map,
            long gamesPlayed,
            long wins,
            long maxTasks,
            long maxLines,
            long maxFirstTasks,
            double maxDragonDamage,
            long firstLiberate,
            long firstNextGen,
            long firstGateway,
            long maxStars,
            long finishes,
            long updatedAt) {
        this(
                uuid,
                username,
                game,
                map,
                gamesPlayed,
                wins,
                maxTasks,
                maxLines,
                maxFirstTasks,
                maxDragonDamage,
                firstLiberate,
                firstNextGen,
                firstGateway,
                maxStars,
                finishes,
                updatedAt,
                LaserShowdownStats.EMPTY);
    }
}
