package ink.ziip.championshipscore.api.daily;

import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.configuration.config.message.GuiConfig;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Metric registry shared by the DAILY stats and leaderboard menus, so both always list the same
 * per-game statistics. Values live either in the per-map stat table (count/damage/rate metrics)
 * or in the per-map time records (AceRace timing metrics).
 */
public enum DailyMetric {
    BINGO_MAX_TASKS(GameTypeEnum.Bingo, Format.COUNT, 1),
    BINGO_MAX_LINES(GameTypeEnum.Bingo, Format.COUNT, 1),
    BINGO_MAX_FIRSTS(GameTypeEnum.Bingo, Format.COUNT, 1),
    ACERACE_FASTEST_LAP(GameTypeEnum.AceRace, Format.TIME, 1),
    ACERACE_FASTEST_THREE_LAPS(GameTypeEnum.AceRace, Format.TIME, 1),
    DRAGON_MAX_DAMAGE(GameTypeEnum.DragonEggCarnival, Format.DAMAGE, 1),
    DRAGON_FIRST_LIBERATE_RATE(GameTypeEnum.DragonEggCarnival, Format.PERCENT, 5),
    DRAGON_FIRST_NEXT_GEN_RATE(GameTypeEnum.DragonEggCarnival, Format.PERCENT, 5),
    DRAGON_FIRST_GATEWAY_RATE(GameTypeEnum.DragonEggCarnival, Format.PERCENT, 5),
    PKW_STARS_TIME(GameTypeEnum.ParkourWarrior, Format.COMPOSITE, 1),
    PKW_POINTS_TIME(GameTypeEnum.ParkourWarrior, Format.COMPOSITE, 1);

    public enum Format { COUNT, TIME, DAMAGE, PERCENT, COMPOSITE }

    private final GameTypeEnum game;
    private final Format format;
    /** Minimum per-map games played before a player may appear on this metric's leaderboard. */
    private final int leaderboardMinGames;

    DailyMetric(GameTypeEnum game, Format format, int leaderboardMinGames) {
        this.game = game;
        this.format = format;
        this.leaderboardMinGames = leaderboardMinGames;
    }

    public @NotNull GameTypeEnum game() {
        return game;
    }

    public @NotNull Format format() {
        return format;
    }

    public int leaderboardMinGames() {
        return leaderboardMinGames;
    }

    /** Time records rank ascending; every other metric ranks descending. */
    public boolean lowerBetter() {
        return format == Format.TIME;
    }

    public boolean isRate() {
        return format == Format.PERCENT;
    }

    public boolean isComposite() {
        return format == Format.COMPOSITE;
    }

    /** Label lookup key inside gui.yml. */
    /** Label lookup key for one of three timed-record attempts. */
    public @NotNull String labelKey(int attempt) {
        return String.format(java.util.Locale.ROOT, "%s-best%d", labelKey(), attempt);
    }

    public @NotNull String labelKey() {
        return "daily.menus.statistics-screen.items.metric.states." + name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    /** Stable leaderboard id: per map when a map is given, otherwise the cross-map aggregate. */
    public @NotNull String boardId(@Nullable String map) {
        String suffix = map == null ? "overall" : "map_" + DailyStatsManager.mapSlug(map);
        return name().toLowerCase(Locale.ROOT) + "_" + suffix;
    }

    /** Every metric registered for a game, in display order; empty when the game has none yet. */
    public static @NotNull List<DailyMetric> forGame(@NotNull GameTypeEnum game) {
        return java.util.Arrays.stream(values()).filter(metric -> metric.game == game).toList();
    }

    /** Renders one metric value the same way in every menu. */
    public static @NotNull String format(@NotNull DailyMetric metric, double value) {
        return switch (metric.format()) {
            case TIME -> DailyLeaderboardMenu.formatDuration((long) value);
            case DAMAGE -> String.format(Locale.ROOT, "%.1f", value);
            case PERCENT -> String.format(Locale.ROOT, "%.1f%%", value);
            case COUNT -> Long.toString(Math.round(value));
            case COMPOSITE -> Long.toString(Math.round(value));
        };
    }

    /** Formats a Parkour Warrior result using the primary value and its same-run duration. */
    public static @NotNull String format(@NotNull DailyMetric metric, double value, long durationMs) {
        if (!metric.isComposite() || durationMs < 0L) return format(metric, value);
        long totalSeconds = durationMs / 1_000L;
        long hours = totalSeconds / 3_600L;
        long minutes = (totalSeconds % 3_600L) / 60L;
        long seconds = totalSeconds % 60L;
        String duration = "%02d:%02d:%02d".formatted(hours, minutes, seconds);
        return switch (metric) {
            case PKW_STARS_TIME -> GuiConfig.line(valueKey(metric), 0, Map.of(
                    "stars", Long.toString(Math.round(value)), "duration", duration));
            case PKW_POINTS_TIME -> GuiConfig.line(valueKey(metric), 0, Map.of(
                    "points", Long.toString(Math.round(value)), "duration", duration));
            default -> format(metric, value);
        };
    }

    private static @NotNull String valueKey(@NotNull DailyMetric metric) {
        return metric.labelKey() + ".lore";
    }
}
