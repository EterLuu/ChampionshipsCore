package ink.ziip.championshipscore.api.daily;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/** Immutable row shared by menus and PlaceholderAPI. */
public record DailyLeaderboardEntry(@NotNull UUID player, @NotNull String name,
                                    double value, boolean duration) {
}
