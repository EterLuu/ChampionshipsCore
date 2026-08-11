package ink.ziip.championshipscore.api.daily;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseManager;
import ink.ziip.championshipscore.api.daily.dao.DailyStatsDao;
import ink.ziip.championshipscore.api.daily.dao.DailyStatsDaoImpl;
import ink.ziip.championshipscore.api.daily.entry.DailyMatchResultEntry;
import ink.ziip.championshipscore.api.daily.entry.DailyRecordEntry;
import ink.ziip.championshipscore.api.daily.entry.DailyStatEntry;
import ink.ziip.championshipscore.api.game.instance.BaseGameInstance;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.function.ToLongFunction;

/** DAILY result manager. Business state stays here; every database operation is delegated to its DAO. */
public final class DailyStatsManager extends BaseManager {
    private final DailyStatsDao statsDao = new DailyStatsDaoImpl();
    private final Map<StatKey, DailyStatSnapshot> stats = new ConcurrentHashMap<>();
    private final Map<RecordKey, Long> records = new ConcurrentHashMap<>();
    /** Latest per-team Bingo progress, copied into the immutable match result at game end. */
    private final Map<UUID, Map<UUID, BingoProgress>> bingoProgress = new ConcurrentHashMap<>();
    private final Map<UUID, String> names = new ConcurrentHashMap<>();
    private volatile Map<String, List<DailyLeaderboardEntry>> leaderboards = Map.of();
    private final Set<MilestoneKey> emittedMilestones = ConcurrentHashMap.newKeySet();
    private final Set<UUID> recordedMatches = ConcurrentHashMap.newKeySet();
    private volatile boolean active;

    public DailyStatsManager(ChampionshipsCore plugin) {
        super(plugin);
    }

    @Override
    public void load() {
        active = true;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::loadCaches);
    }

    @Override
    public void unload() {
        active = false;
        stats.clear();
        records.clear();
        bingoProgress.clear();
        names.clear();
        leaderboards = Map.of();
        emittedMilestones.clear();
        recordedMatches.clear();
    }

    public DailyStatSnapshot stat(UUID player, @Nullable GameTypeEnum game) {
        if (game != null) return stats.getOrDefault(new StatKey(player, game), DailyStatSnapshot.EMPTY);
        long games = 0L;
        long wins = 0L;
        long lines = 0L;
        long completedTasks = 0L;
        long maxCompletedTasks = 0L;
        for (Map.Entry<StatKey, DailyStatSnapshot> entry : stats.entrySet()) {
            if (!entry.getKey().player().equals(player)) continue;
            DailyStatSnapshot value = entry.getValue();
            games += value.gamesPlayed();
            wins += value.wins();
            lines += value.lineCount();
            completedTasks += value.completedTasks();
            maxCompletedTasks = Math.max(maxCompletedTasks, value.maxCompletedTasks());
        }
        return new DailyStatSnapshot(games, wins, lines, completedTasks, maxCompletedTasks);
    }

    public long bestRecord(UUID player, GameTypeEnum game, String map, DailyRecordType type) {
        return records.getOrDefault(new RecordKey(player, game, map, type), -1L);
    }

    public @NotNull List<DailyLeaderboardEntry> leaderboard(@NotNull String boardId) {
        return leaderboards.getOrDefault(boardId.toLowerCase(Locale.ROOT), List.of());
    }

    public @NotNull Set<String> recordMaps(@NotNull GameTypeEnum game) {
        Set<String> maps = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        records.keySet().stream().filter(key -> key.game() == game).map(RecordKey::map).forEach(maps::add);
        return Set.copyOf(maps);
    }

    public static @NotNull String mapSlug(@NotNull String map) {
        return map.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
    }

    /** Records the latest Bingo progress for every member of a team; both values are monotonic. */
    public void recordBingoProgress(@NotNull BaseGameInstance instance, @NotNull ChampionshipTeam team,
                                    long lineCount, long completedTasks) {
        DailySession session = plugin.getDailyManager().session(instance);
        if (session == null || session.game() != GameTypeEnum.Bingo
                || lineCount < 0L || completedTasks < 0L) return;
        Map<UUID, BingoProgress> progress = bingoProgress.computeIfAbsent(session.matchId(),
                ignored -> new ConcurrentHashMap<>());
        BingoProgress current = new BingoProgress(lineCount, completedTasks);
        for (UUID player : team.getMembers()) {
            progress.merge(player, current, (previous, latest) -> new BingoProgress(
                    Math.max(previous.lineCount(), latest.lineCount()),
                    Math.max(previous.completedTasks(), latest.completedTasks())));
        }
    }

    void recordMatch(@NotNull DailySession session, @NotNull Map<UUID, Double> points) {
        if (!recordedMatches.add(session.matchId())) return;
        Map<UUID, BingoProgress> matchProgress = bingoProgress.remove(session.matchId());
        Map<ChampionshipTeam, Double> teamScores = new HashMap<>();
        for (ChampionshipTeam team : session.teams()) {
            double score = team.getMembers().stream().mapToDouble(uuid -> points.getOrDefault(uuid, 0D)).sum();
            teamScores.put(team, score);
        }
        double winningScore = teamScores.values().stream().mapToDouble(Double::doubleValue).max().orElse(0D);
        long now = System.currentTimeMillis();
        List<DailyMatchResultEntry> results = new ArrayList<>();
        for (ChampionshipTeam team : session.teams()) {
            boolean won = teamScores.getOrDefault(team, 0D) == winningScore;
            for (UUID player : team.getMembers()) {
                double playerPoints = points.getOrDefault(player, 0D);
                BingoProgress progress = matchProgress == null ? null : matchProgress.get(player);
                long lineCount = progress == null ? 0L : progress.lineCount();
                long completedTasks = progress == null ? 0L : progress.completedTasks();
                results.add(new DailyMatchResultEntry(session.matchId(), player, safeName(player),
                        session.game(), session.map(), team.getName(), playerPoints, won,
                        lineCount, completedTasks, now));
                stats.compute(new StatKey(player, session.game()),
                        (ignored, previous) -> (previous == null ? DailyStatSnapshot.EMPTY : previous)
                                .add(won, lineCount, completedTasks));
                names.put(player, safeName(player));
            }
        }
        rebuildLeaderboards();
        runAsync(() -> statsDao.saveMatch(results));
    }

    public void recordTeamMilestone(@NotNull BaseGameInstance instance, @NotNull ChampionshipTeam team,
                                    @NotNull DailyRecordType type, long durationMillis,
                                    @Nullable UUID achievedBy) {
        DailySession session = plugin.getDailyManager().session(instance);
        if (session == null || durationMillis < 0) return;
        MilestoneKey milestone = new MilestoneKey(session.matchId(), team.getId(), type);
        if (!emittedMilestones.add(milestone)) return;
        record(session, team.getMembers(), type, durationMillis, achievedBy);
    }

    public void recordPlayerTime(@NotNull BaseGameInstance instance, @NotNull UUID player,
                                 @NotNull DailyRecordType type, long durationMillis) {
        DailySession session = plugin.getDailyManager().session(instance);
        if (session == null || durationMillis < 0) return;
        record(session, Set.of(player), type, durationMillis, player);
    }

    private void record(DailySession session, Set<UUID> players, DailyRecordType type,
                        long durationMillis, UUID achievedBy) {
        String revision = Integer.toString(session.instance().getGameConfig().getLatestVersion());
        String rulesHash = "daily-v1";
        long now = System.currentTimeMillis();
        List<DailyRecordEntry> entries = new ArrayList<>();
        for (UUID player : players) {
            RecordKey key = new RecordKey(player, session.game(), session.map(), type);
            records.compute(key,
                    (ignored, previous) -> previous == null ? durationMillis : Math.min(previous, durationMillis));
            entries.add(new DailyRecordEntry(player, safeName(player), session.game(), session.map(),
                    revision, rulesHash, type, durationMillis, session.matchId(), achievedBy, now));
            names.put(player, safeName(player));
        }
        rebuildLeaderboards();
        runAsync(() -> statsDao.saveRecords(entries));
    }

    private void loadCaches() {
        for (DailyStatEntry entry : statsDao.getPlayerStats()) {
            names.put(entry.uuid(), entry.username());
            stats.put(new StatKey(entry.uuid(), entry.game()), new DailyStatSnapshot(entry.gamesPlayed(),
                    entry.wins(), entry.lineCount(), entry.completedTasks(), entry.maxCompletedTasks()));
        }
        for (DailyRecordEntry entry : statsDao.getPlayerRecords()) {
            names.put(entry.uuid(), entry.username());
            records.merge(new RecordKey(entry.uuid(), entry.game(), entry.map(), entry.recordType()),
                    entry.durationMs(), Math::min);
        }
        rebuildLeaderboards();
    }

    private void rebuildLeaderboards() {
        Map<String, List<DailyLeaderboardEntry>> rebuilt = new LinkedHashMap<>();
        Map<UUID, DailyStatSnapshot> totals = new HashMap<>();
        Map<UUID, DailyStatSnapshot> bingoTotals = new HashMap<>();
        for (Map.Entry<StatKey, DailyStatSnapshot> entry : stats.entrySet()) {
            DailyStatSnapshot value = entry.getValue();
            totals.compute(entry.getKey().player(), (ignored, prior) -> prior == null ? value
                    : new DailyStatSnapshot(prior.gamesPlayed() + value.gamesPlayed(),
                    prior.wins() + value.wins(), prior.lineCount() + value.lineCount(),
                    prior.completedTasks() + value.completedTasks(),
                    Math.max(prior.maxCompletedTasks(), value.maxCompletedTasks())));
            if (entry.getKey().game() == GameTypeEnum.Bingo) {
                bingoTotals.compute(entry.getKey().player(), (ignored, prior) -> prior == null ? value
                        : new DailyStatSnapshot(prior.gamesPlayed() + value.gamesPlayed(),
                        prior.wins() + value.wins(), prior.lineCount() + value.lineCount(),
                        prior.completedTasks() + value.completedTasks(),
                        Math.max(prior.maxCompletedTasks(), value.maxCompletedTasks())));
            }
        }
        rebuilt.put("wins", rankMetric(totals, DailyStatSnapshot::wins));
        rebuilt.put("bingo_wins", rankMetric(bingoTotals, DailyStatSnapshot::wins));
        rebuilt.put("bingo_lines", rankMetric(bingoTotals, DailyStatSnapshot::lineCount));
        rebuilt.put("bingo_completed_tasks", rankMetric(bingoTotals, DailyStatSnapshot::completedTasks));
        rebuilt.put("bingo_max_completed", rankMetric(bingoTotals, DailyStatSnapshot::maxCompletedTasks));
        Map<String, Map<UUID, Long>> timed = new HashMap<>();
        records.forEach((key, value) -> timed.computeIfAbsent(recordBoardId(key), ignored -> new HashMap<>())
                .merge(key.player(), value, Math::min));
        timed.forEach((id, values) -> rebuilt.put(id, values.entrySet().stream()
                .sorted(Map.Entry.<UUID, Long>comparingByValue().thenComparing(entry -> displayName(entry.getKey())))
                .limit(100).map(entry -> new DailyLeaderboardEntry(entry.getKey(), displayName(entry.getKey()),
                        entry.getValue(), true)).toList()));
        leaderboards = Map.copyOf(rebuilt);
    }

    private List<DailyLeaderboardEntry> rankMetric(Map<UUID, DailyStatSnapshot> values,
                                                   ToLongFunction<DailyStatSnapshot> metric) {
        return values.entrySet().stream()
                .map(entry -> new DailyLeaderboardEntry(entry.getKey(), displayName(entry.getKey()),
                        metric.applyAsLong(entry.getValue()), false))
                .filter(entry -> entry.value() > 0D)
                .sorted(Comparator.comparingDouble(DailyLeaderboardEntry::value).reversed()
                        .thenComparing(DailyLeaderboardEntry::name, String.CASE_INSENSITIVE_ORDER))
                .limit(100).toList();
    }

    private String recordBoardId(RecordKey key) {
        String prefix = switch (key.type()) {
            case BINGO_FIRST_LINE -> "bingo_first_line_";
            case BINGO_FULL_CARD -> "bingo_full_card_";
            case ACERACE_FASTEST_LAP -> "acerace_fastest_lap_";
            case ACERACE_FASTEST_THREE_LAPS -> "acerace_fastest_three_laps_";
        };
        return prefix + mapSlug(key.map());
    }

    private String displayName(UUID uuid) {
        return names.getOrDefault(uuid, uuid.toString().substring(0, 8));
    }

    private String safeName(UUID uuid) {
        String name = plugin.getPlayerManager().getPlayerName(uuid);
        return name == null || name.isBlank()
                ? uuid.toString().substring(0, 16)
                : name.substring(0, Math.min(16, name.length()));
    }

    private void runAsync(Runnable runnable) {
        if (!active || !plugin.isEnabled()) return;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable);
    }

    private record StatKey(UUID player, GameTypeEnum game) {}
    private record RecordKey(UUID player, GameTypeEnum game, String map, DailyRecordType type) {}
    private record MilestoneKey(UUID match, int teamId, DailyRecordType type) {}
    private record BingoProgress(long lineCount, long completedTasks) {}
}
