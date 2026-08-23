package ink.ziip.championshipscore.api.daily;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/** Immutable row shared by menus and PlaceholderAPI. */
public record DailyLeaderboardEntry(@NotNull UUID player, @NotNull String name,
                                    double value, boolean duration, long tieDurationMs) {
    public DailyLeaderboardEntry(@NotNull UUID player, @NotNull String name,
                                 double value, boolean duration) {
        this(player, name, value, duration, -1L);
    }
}
