package ink.ziip.championshipscore.api.daily;

import ink.ziip.championshipscore.api.game.model.GameTypeEnum;
import ink.ziip.championshipscore.configuration.config.CCConfig;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Immutable DAILY leaderboard snapshot sent to cc-web. Empty boards are omitted, while every
 * enabled, web-supported game is announced so the web side can replace its previous submissions.
 */
public record WebLeaderboardSnapshot(
        @NotNull List<String> games, @NotNull List<WebLeaderboardBoard> boards, long generatedAt) {
    public static @NotNull WebLeaderboardSnapshot from(
            @NotNull DailyManager daily, @NotNull DailyStatsManager stats) {
        List<String> games = new ArrayList<>();
        List<WebLeaderboardBoard> boards = new ArrayList<>();
        for (GameTypeEnum game : daily.enabledGames()) {
            if (!canPublish(game)) continue;
            games.add(game.name());
            Set<String> maps = daily.knownMaps(game);
            for (DailyMetric metric : DailyMetric.rankedForGame(game)) {
                appendBoard(boards, stats, game, metric, null);
                for (String map : maps) appendBoard(boards, stats, game, metric, map);
            }
        }
        return new WebLeaderboardSnapshot(
                List.copyOf(games), List.copyOf(boards), System.currentTimeMillis());
    }

    /** Older web deployments reject an entire snapshot containing an unrecognized game. */
    static boolean canPublish(GameTypeEnum game) {
        List<String> supported = CCConfig.WEB_LEADERBOARD_SYNC_ENABLED_GAMES;
        if (supported == null)
            supported = List.of("Bingo", "AceRace", "DragonEggCarnival", "ParkourWarrior");
        return supported.stream()
                .anyMatch(name -> name != null && name.trim().equalsIgnoreCase(game.name()));
    }

    private static void appendBoard(
            @NotNull List<WebLeaderboardBoard> boards,
            @NotNull DailyStatsManager stats,
            @NotNull GameTypeEnum game,
            @NotNull DailyMetric metric,
            @Nullable String map) {
        List<WebLeaderboardEntry> entries =
                stats.leaderboard(metric.boardId(map)).stream()
                        .filter(entry -> Double.isFinite(entry.value()) && entry.value() > 0D)
                        .map(
                                entry ->
                                        new WebLeaderboardEntry(
                                                entry.player().toString(),
                                                entry.name(),
                                                entry.value(),
                                                entry.tieDurationMs()))
                        .toList();
        if (entries.isEmpty()) return;
        boards.add(
                new WebLeaderboardBoard(
                        game.name(),
                        metric.name(),
                        map,
                        metric.format() == DailyMetric.Format.DECIMAL
                                ? "DAMAGE"
                                : metric.format().name(),
                        metric.lowerBetter(),
                        entries));
    }

    public record WebLeaderboardBoard(
            @NotNull String game,
            @NotNull String metric,
            @Nullable String map,
            @NotNull String format,
            boolean lowerBetter,
            @NotNull List<WebLeaderboardEntry> entries) {
        public WebLeaderboardBoard {
            entries = List.copyOf(entries);
        }
    }

    public record WebLeaderboardEntry(
            @NotNull String uuid, @NotNull String username, double value, long tieDurationMs) {}
}
