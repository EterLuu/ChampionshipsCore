package ink.ziip.championshipscore.api.daily;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseManager;
import ink.ziip.championshipscore.api.daily.dao.DailyStatsDao;
import ink.ziip.championshipscore.api.daily.dao.DailyStatsDaoImpl;
import ink.ziip.championshipscore.api.daily.entry.DailyMapStatEntry;
import ink.ziip.championshipscore.api.daily.entry.DailyMatchAggregateEntry;
import ink.ziip.championshipscore.api.daily.entry.DailyMatchResultEntry;
import ink.ziip.championshipscore.api.daily.entry.DailyPkwRecordEntry;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Queue;
import java.util.Comparator;

/** DAILY result manager. Business state stays here; every database operation is delegated to its DAO. */
public final class DailyStatsManager extends BaseManager {
    private final DailyStatsDao statsDao = new DailyStatsDaoImpl();
    private final Map<StatKey, DailyStatSnapshot> stats = new ConcurrentHashMap<>();
    /** Up to three best attempts for each player/game/map/record identity, fastest first. */
    private final Map<RecordKey, List<DailyRecordEntry>> records = new ConcurrentHashMap<>();
    private final Map<PkwRecordKey, PkwRecordValue> pkwRecords = new ConcurrentHashMap<>();
    private final Map<MapStatKey, DailyMapStat> mapStats = new ConcurrentHashMap<>();
    /** Latest per-team in-match progress, copied into the immutable match result at game end. */
    private final Map<UUID, Map<UUID, MatchProgress>> matchProgress = new ConcurrentHashMap<>();
    private final Map<UUID, String> names = new ConcurrentHashMap<>();
    private volatile Map<String, List<DailyLeaderboardEntry>> leaderboards = Map.of();
    private final Set<MilestoneKey> emittedMilestones = ConcurrentHashMap.newKeySet();
    private final Set<UUID> recordedMatches = ConcurrentHashMap.newKeySet();
    private volatile boolean active;
    private final Queue<Runnable> databaseTasks = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean databaseTaskRunning = new AtomicBoolean();

    public DailyStatsManager(ChampionshipsCore plugin) {
        super(plugin);
    }

    @Override
    public void load() {
        active = true;
        runAsync(this::loadCaches);
    }

    @Override
    public void unload() {
        active = false;
        stats.clear();
        records.clear();
        pkwRecords.clear();
        mapStats.clear();
        matchProgress.clear();
        names.clear();
        leaderboards = Map.of();
        emittedMilestones.clear();
        recordedMatches.clear();
        databaseTasks.clear();
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

    /** Per-map aggregate, or the cross-map fold of every map the player touched when map is null. */
    public @NotNull DailyMapStat mapStat(@NotNull UUID player, @NotNull GameTypeEnum game, @Nullable String map) {
        if (map != null) return mapStats.getOrDefault(new MapStatKey(player, game, map), DailyMapStat.EMPTY);
        DailyMapStat total = DailyMapStat.EMPTY;
        for (Map.Entry<MapStatKey, DailyMapStat> entry : mapStats.entrySet()) {
            MapStatKey key = entry.getKey();
            if (key.player().equals(player) && key.game() == game) total = total.merge(entry.getValue());
        }
        return total;
    }

    public long bestRecord(UUID player, GameTypeEnum game, String map, DailyRecordType type) {
        List<Long> values = recordValues(player, game, map, type);
        return values.isEmpty() ? -1L : values.getFirst();
    }

    /** Best time of one record type across every map the player set it on. */
    public long bestRecordAcrossMaps(UUID player, GameTypeEnum game, DailyRecordType type) {
        List<Long> values = recordValuesAcrossMaps(player, game, type);
        return values.isEmpty() ? -1L : values.getFirst();
    }

    /** The player's top three attempts on one map, in ascending time order. */
    public @NotNull List<Long> recordValues(UUID player, GameTypeEnum game, String map,
                                             DailyRecordType type) {
        return records.getOrDefault(new RecordKey(player, game, map, type), List.of()).stream()
                .map(DailyRecordEntry::durationMs).toList();
    }

    /** The player's top three attempts across all maps, in ascending time order. */
    public @NotNull List<Long> recordValuesAcrossMaps(UUID player, GameTypeEnum game,
                                                       DailyRecordType type) {
        List<DailyRecordEntry> values = new ArrayList<>();
        for (Map.Entry<RecordKey, List<DailyRecordEntry>> entry : records.entrySet()) {
            RecordKey key = entry.getKey();
            if (key.player().equals(player) && key.game() == game && key.type() == type) {
                values.addAll(entry.getValue());
            }
        }
        return topRecords(values).stream().map(DailyRecordEntry::durationMs).toList();
    }

    /** Values shown in the personal-record menus; only repeatable time records have three rows. */
    public @NotNull List<Double> metricValues(@NotNull UUID player, @Nullable String map,
                                              @NotNull DailyMetric metric) {
        if (metric.format() == DailyMetric.Format.TIME) {
            DailyRecordType type = recordType(metric);
            List<Long> values = map == null
                    ? recordValuesAcrossMaps(player, metric.game(), type)
                    : recordValues(player, metric.game(), map, type);
            return values.stream().map(Long::doubleValue).toList();
        }
        double value = metricValue(player, map, metric);
        return Double.isNaN(value) ? List.of() : List.of(value);
    }

    /**
     * One unified metric value for the menus; map null folds every map of that game together
     * (max for peak metrics, sum-based rates, minimum for times). Returns NaN when absent.
     */
    public double metricValue(@NotNull UUID player, @Nullable String map, @NotNull DailyMetric metric) {
        if (metric.isComposite()) {
            PkwRecordValue record = pkwRecord(player, map, metric);
            return record == null ? Double.NaN : record.primaryValue();
        }
        if (metric.format() == DailyMetric.Format.TIME) {
            DailyRecordType type = recordType(metric);
            long value = map != null ? bestRecord(player, metric.game(), map, type)
                    : bestRecordAcrossMaps(player, metric.game(), type);
            return value < 0L ? Double.NaN : value;
        }
        DailyMapStat stat = mapStat(player, metric.game(), map);
        return switch (metric) {
            case BINGO_MAX_TASKS -> stat.maxTasks() > 0 ? stat.maxTasks() : Double.NaN;
            case BINGO_MAX_LINES -> stat.maxLines() > 0 ? stat.maxLines() : Double.NaN;
            case BINGO_MAX_FIRSTS -> stat.maxFirstTasks() > 0 ? stat.maxFirstTasks() : Double.NaN;
            case DRAGON_MAX_DAMAGE -> stat.maxDragonDamage() > 0D ? stat.maxDragonDamage() : Double.NaN;
            case DRAGON_FIRST_LIBERATE_RATE -> rate(stat.firstLiberate(), stat.trackedGames());
            case DRAGON_FIRST_NEXT_GEN_RATE -> rate(stat.firstNextGen(), stat.trackedGames());
            case DRAGON_FIRST_GATEWAY_RATE -> rate(stat.firstGateway(), stat.trackedGames());
            default -> Double.NaN;
        };
    }

    public long metricDuration(@NotNull UUID player, @Nullable String map, @NotNull DailyMetric metric) {
        PkwRecordValue record = pkwRecord(player, map, metric);
        return record == null ? -1L : record.durationMs();
    }

    public @NotNull String formatMetricValue(@NotNull UUID player, @Nullable String map,
                                             @NotNull DailyMetric metric) {
        double value = metricValue(player, map, metric);
        return Double.isNaN(value) ? "" : formatMetricValue(player, map, metric, value);
    }

    public @NotNull String formatMetricValue(@NotNull UUID player, @Nullable String map,
                                             @NotNull DailyMetric metric, double value) {
        return DailyMetric.format(metric, value, metricDuration(player, map, metric));
    }

    /** Formats a leaderboard row, including the duration tied to a composite PKW result. */
    public @NotNull String formatLeaderboardValue(@NotNull DailyMetric metric,
                                                  @NotNull DailyLeaderboardEntry entry) {
        return DailyMetric.format(metric, entry.value(), entry.tieDurationMs());
    }

    private PkwRecordValue pkwRecord(UUID player, @Nullable String map, DailyMetric metric) {
        DailyPkwRecordType type = metric == DailyMetric.PKW_STARS_TIME
                ? DailyPkwRecordType.STARS_TIME : DailyPkwRecordType.POINTS_TIME;
        PkwRecordValue best = null;
        for (Map.Entry<PkwRecordKey, PkwRecordValue> entry : pkwRecords.entrySet()) {
            PkwRecordKey key = entry.getKey();
            if (!key.player().equals(player) || key.type() != type
                    || (map != null && !key.map().equals(map))) continue;
            best = best == null ? entry.getValue() : betterPkwRecord(best, entry.getValue());
        }
        return best;
    }

    /** Rate in percent over tracked games, or NaN while none of this scope's games were tracked. */
    private static double rate(long numerator, long trackedGames) {
        return trackedGames <= 0L ? Double.NaN : 100D * numerator / trackedGames;
    }

    public @NotNull List<DailyLeaderboardEntry> leaderboard(@NotNull String boardId) {
        return leaderboards.getOrDefault(boardId.toLowerCase(Locale.ROOT), List.of());
    }

    public @NotNull Set<String> recordMaps(@NotNull GameTypeEnum game) {
        Set<String> maps = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        records.keySet().stream().filter(key -> key.game() == game).map(RecordKey::map).forEach(maps::add);
        mapStats.keySet().stream().filter(key -> key.game() == game).map(MapStatKey::map).forEach(maps::add);
        if (game == GameTypeEnum.ParkourWarrior) {
            pkwRecords.keySet().stream().map(PkwRecordKey::map).forEach(maps::add);
        }
        return Set.copyOf(maps);
    }

    /** Runs after all DAILY writes already submitted before this call. */
    public void runAfterPendingWrites(@NotNull Runnable task) {
        runAsync(task);
    }

    /** Keeps the live record/leaderboard cache aligned with a committed database map rename. */
    public void renameMap(@NotNull GameTypeEnum game, @NotNull String oldMap, @NotNull String newMap) {
        Map<RecordKey, List<DailyRecordEntry>> moved = new HashMap<>();
        for (Map.Entry<RecordKey, List<DailyRecordEntry>> entry : new ArrayList<>(records.entrySet())) {
            RecordKey key = entry.getKey();
            if (key.game() != game || !key.map().equalsIgnoreCase(oldMap)) continue;
            if (records.remove(key, entry.getValue())) {
                List<DailyRecordEntry> renamed = entry.getValue().stream()
                        .map(record -> new DailyRecordEntry(record.uuid(), record.username(), record.game(), newMap,
                                record.mapRevision(), record.rulesHash(), record.recordType(), record.durationMs(),
                                record.matchId(), record.achievedBy(), record.achievedAt(), record.recordRank()))
                        .toList();
                moved.merge(new RecordKey(key.player(), key.game(), newMap, key.type()), renamed,
                        (first, second) -> topRecords(concat(first, second)));
            }
        }
        moved.forEach((key, value) -> records.merge(key, value,
                (first, second) -> topRecords(concat(first, second))));
        Map<PkwRecordKey, PkwRecordValue> movedPkw = new HashMap<>();
        for (Map.Entry<PkwRecordKey, PkwRecordValue> entry : new ArrayList<>(pkwRecords.entrySet())) {
            PkwRecordKey key = entry.getKey();
            if (game != GameTypeEnum.ParkourWarrior || !key.map().equalsIgnoreCase(oldMap)) continue;
            if (pkwRecords.remove(key, entry.getValue())) {
                movedPkw.merge(new PkwRecordKey(key.player(), newMap, key.type()), entry.getValue(),
                        DailyStatsManager::betterPkwRecord);
            }
        }
        movedPkw.forEach((key, value) -> pkwRecords.merge(key, value, DailyStatsManager::betterPkwRecord));
        Map<MapStatKey, DailyMapStat> movedStats = new HashMap<>();
        for (Map.Entry<MapStatKey, DailyMapStat> entry : new ArrayList<>(mapStats.entrySet())) {
            MapStatKey key = entry.getKey();
            if (key.game() != game || !key.map().equalsIgnoreCase(oldMap)) continue;
            if (mapStats.remove(key, entry.getValue())) {
                movedStats.merge(new MapStatKey(key.player(), key.game(), newMap), entry.getValue(),
                        DailyMapStat::merge);
            }
        }
        movedStats.forEach((key, value) -> mapStats.merge(key, value, DailyMapStat::merge));
        rebuildLeaderboards();
    }

    public static @NotNull String mapSlug(@NotNull String map) {
        return map.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
    }

    /** Records the latest Bingo progress for every member of a team; every value is monotonic. */
    public void recordBingoProgress(@NotNull BaseGameInstance instance, @NotNull ChampionshipTeam team,
                                    long lineCount, long completedTasks, long firstTaskCount) {
        DailySession session = plugin.getDailyManager().session(instance);
        if (session == null || session.game() != GameTypeEnum.Bingo
                || lineCount < 0L || completedTasks < 0L || firstTaskCount < 0L) return;
        MatchProgress current = new MatchProgress(lineCount, completedTasks, firstTaskCount,
                0D, false, false, false, 0L, false, -1L);
        for (UUID player : team.getMembers()) {
            matchProgress.computeIfAbsent(session.matchId(), ignored -> new ConcurrentHashMap<>())
                    .merge(player, current, MatchProgress::merge);
        }
    }

    /** Accumulates one player's total dragon damage in the running match; the area reports totals. */
    public void recordDragonDamage(@NotNull BaseGameInstance instance, @NotNull UUID player,
                                   double totalDamage) {
        DailySession session = plugin.getDailyManager().session(instance);
        if (session == null || session.game() != GameTypeEnum.DragonEggCarnival || totalDamage < 0D) return;
        matchProgress.computeIfAbsent(session.matchId(), ignored -> new ConcurrentHashMap<>())
                .merge(player, new MatchProgress(0L, 0L, 0L, totalDamage, false, false, false, 0L, false, -1L),
                        MatchProgress::merge);
    }

    /** Credits every member of the team that first completed one of the three End advancements. */
    public void recordDragonFirstAdvancement(@NotNull BaseGameInstance instance,
                                              @NotNull ChampionshipTeam team, @NotNull String advancementKey) {
        DailySession session = plugin.getDailyManager().session(instance);
        if (session == null || session.game() != GameTypeEnum.DragonEggCarnival) return;
        boolean liberate = "end/kill_dragon".equals(advancementKey);
        boolean nextGen = "end/dragon_egg".equals(advancementKey);
        boolean gateway = "end/enter_end_gateway".equals(advancementKey);
        if (!liberate && !nextGen && !gateway) return;
        MatchProgress current = new MatchProgress(0L, 0L, 0L, 0D, liberate, nextGen, gateway, 0L, false, -1L);
        for (UUID player : team.getMembers()) {
            matchProgress.computeIfAbsent(session.matchId(), ignored -> new ConcurrentHashMap<>())
                    .merge(player, current, MatchProgress::merge);
        }
    }

    /** Records the latest Parkour Warrior star total for one player; stars keep the in-match peak. */
    public void recordParkourWarriorProgress(@NotNull BaseGameInstance instance, @NotNull UUID player,
                                             long stars, boolean finished, long durationMillis) {
        DailySession session = plugin.getDailyManager().session(instance);
        if (session == null || session.game() != GameTypeEnum.ParkourWarrior || stars < 0L) return;
        matchProgress.computeIfAbsent(session.matchId(), ignored -> new ConcurrentHashMap<>())
                .merge(player, new MatchProgress(0L, 0L, 0L, 0D, false, false, false, stars, finished,
                                finished ? durationMillis : -1L),
                        MatchProgress::merge);
    }

    void recordMatch(@NotNull DailySession session, @NotNull Map<UUID, Double> points) {
        if (!recordedMatches.add(session.matchId())) return;
        Map<UUID, MatchProgress> progress = matchProgress.remove(session.matchId());
        Map<ChampionshipTeam, Double> teamScores = new HashMap<>();
        for (ChampionshipTeam team : session.teams()) {
            double score = team.getMembers().stream().mapToDouble(uuid -> points.getOrDefault(uuid, 0D)).sum();
            teamScores.put(team, score);
        }
        double winningScore = teamScores.values().stream().mapToDouble(Double::doubleValue).max().orElse(0D);
        long now = System.currentTimeMillis();
        List<DailyMatchResultEntry> results = new ArrayList<>();
        List<DailyMapStatEntry> mapStatDeltas = new ArrayList<>();
        List<DailyPkwRecordEntry> pkwRecordDeltas = new ArrayList<>();
        for (ChampionshipTeam team : session.teams()) {
            boolean won = teamScores.getOrDefault(team, 0D) == winningScore;
            for (UUID player : team.getMembers()) {
                double playerPoints = points.getOrDefault(player, 0D);
                MatchProgress match = progress == null ? null : progress.get(player);
                long lineCount = match == null ? 0L : match.lines();
                long completedTasks = match == null ? 0L : match.tasks();
                results.add(new DailyMatchResultEntry(session.matchId(), player, safeName(player),
                        session.game(), session.map(), team.getName(), playerPoints, won,
                        lineCount, completedTasks, now));
                stats.compute(new StatKey(player, session.game()),
                        (ignored, previous) -> (previous == null ? DailyStatSnapshot.EMPTY : previous)
                                .add(won, lineCount, completedTasks));
                names.put(player, safeName(player));
                DailyMapStatEntry delta = mapStatDelta(player, session, won, match);
                mapStatDeltas.add(delta);
                mapStats.merge(new MapStatKey(player, session.game(), session.map()),
                        toMapStat(delta), DailyMapStat::merge);
                if (session.game() == GameTypeEnum.ParkourWarrior && match != null
                        && match.finished() && match.finishDurationMs() >= 0L) {
                    addPkwRecord(pkwRecordDeltas, player, session, DailyPkwRecordType.STARS_TIME,
                            match.stars(), match.finishDurationMs(), now);
                    addPkwRecord(pkwRecordDeltas, player, session, DailyPkwRecordType.POINTS_TIME,
                            playerPoints, match.finishDurationMs(), now);
                }
            }
        }
        rebuildLeaderboards();
        runAsync(() -> statsDao.saveMatch(results));
        runAsync(() -> statsDao.saveMapStats(mapStatDeltas));
        runAsync(() -> statsDao.savePkwRecords(pkwRecordDeltas));
    }

    private void addPkwRecord(List<DailyPkwRecordEntry> deltas, UUID player, DailySession session,
                              DailyPkwRecordType type, double primaryValue, long durationMs, long now) {
        DailyPkwRecordEntry entry = new DailyPkwRecordEntry(player, safeName(player), session.map(), type,
                primaryValue, durationMs, session.matchId(), now);
        deltas.add(entry);
        names.put(player, entry.username());
        pkwRecords.merge(new PkwRecordKey(player, session.map(), type),
                new PkwRecordValue(primaryValue, durationMs), DailyStatsManager::betterPkwRecord);
    }

    private DailyMapStatEntry mapStatDelta(UUID player, DailySession session, boolean won,
                                           @Nullable MatchProgress match) {
        return new DailyMapStatEntry(player, safeName(player), session.game(), session.map(),
                1L, won ? 1L : 0L,
                match == null ? 0L : match.tasks(),
                match == null ? 0L : match.lines(),
                match == null ? 0L : match.firsts(),
                match == null ? 0D : match.dragonDamage(),
                match != null && match.firstLiberate() ? 1L : 0L,
                match != null && match.firstNextGen() ? 1L : 0L,
                match != null && match.firstGateway() ? 1L : 0L,
                match == null ? 0L : match.stars(),
                match != null && match.finished() ? 1L : 0L,
                System.currentTimeMillis());
    }

    private static DailyMapStat toMapStat(DailyMapStatEntry entry) {
        return new DailyMapStat(entry.gamesPlayed(), entry.wins(), entry.gamesPlayed(),
                entry.maxTasks(), entry.maxLines(), entry.maxFirstTasks(), entry.maxDragonDamage(),
                entry.firstLiberate(), entry.firstNextGen(), entry.firstGateway(),
                entry.maxStars(), entry.finishes());
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
            DailyRecordEntry candidate = new DailyRecordEntry(player, safeName(player), session.game(), session.map(),
                    revision, rulesHash, type, durationMillis, session.matchId(), achievedBy, now);
            records.compute(key, (ignored, previous) -> topRecords(concat(previous, List.of(candidate))));
            entries.add(candidate);
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
            RecordKey key = new RecordKey(entry.uuid(), entry.game(), entry.map(), entry.recordType());
            records.compute(key, (ignored, previous) -> topRecords(concat(previous, List.of(entry))));
        }
        for (DailyPkwRecordEntry entry : statsDao.getPlayerPkwRecords()) {
            names.put(entry.uuid(), entry.username());
            pkwRecords.merge(new PkwRecordKey(entry.uuid(), entry.map(), entry.recordType()),
                    new PkwRecordValue(entry.primaryValue(), entry.durationMs()),
                    DailyStatsManager::betterPkwRecord);
        }
        for (DailyMapStatEntry entry : statsDao.getPlayerMapStats()) {
            names.put(entry.uuid(), entry.username());
            mapStats.merge(new MapStatKey(entry.uuid(), entry.game(), entry.map()),
                    toMapStat(entry), DailyMapStat::merge);
        }
        backfillMapStatsFromMatchResults();
        rebuildLeaderboards();
    }

    /**
     * Folds the historical {@code daily_match_results} rows into the per-map cache so games
     * played before the per-map stat table existed still show up as map-level 场次 and Bingo
     * maxima. Counts take the maximum because both sources cover overlapping match sets;
     * first-completion and dragon metrics stay table-only ({@code trackedGames} denominator).
     */
    private void backfillMapStatsFromMatchResults() {
        for (DailyMatchAggregateEntry history : statsDao.getMatchResultMapAggregates()) {
            MapStatKey key = new MapStatKey(history.uuid(), history.game(), history.map());
            DailyMapStat base = mapStats.getOrDefault(key, DailyMapStat.EMPTY);
            mapStats.put(key, new DailyMapStat(
                    Math.max(base.gamesPlayed(), history.gamesPlayed()),
                    Math.max(base.wins(), history.wins()),
                    base.trackedGames(),
                    Math.max(base.maxTasks(), history.maxCompletedTasks()),
                    Math.max(base.maxLines(), history.maxLines()),
                    base.maxFirstTasks(), base.maxDragonDamage(),
                    base.firstLiberate(), base.firstNextGen(), base.firstGateway(),
                    base.maxStars(), base.finishes()));
        }
    }

    private void rebuildLeaderboards() {
        Map<String, List<DailyLeaderboardEntry>> rebuilt = new LinkedHashMap<>();
        // Legacy boards kept for PlaceholderAPI compatibility; the menus use DailyMetric boards.
        rebuilt.putAll(legacyBoards());
        for (DailyMetric metric : DailyMetric.values()) {
            // Per-map boards.
            for (String map : mapsWithStats(metric.game())) {
                rebuilt.put(metric.boardId(map), metricBoard(metric, map));
            }
            // Cross-map aggregate board.
            rebuilt.put(metric.boardId(null), metricBoard(metric, null));
        }
        leaderboards = Map.copyOf(rebuilt);
    }

    private Map<String, List<DailyLeaderboardEntry>> legacyBoards() {
        Map<String, List<DailyLeaderboardEntry>> rebuilt = new LinkedHashMap<>();
        Map<UUID, DailyStatSnapshot> bingoTotals = new HashMap<>();
        Map<UUID, DailyStatSnapshot> allGameTotals = new HashMap<>();
        for (Map.Entry<StatKey, DailyStatSnapshot> entry : stats.entrySet()) {
            DailyStatSnapshot value = entry.getValue();
            allGameTotals.merge(entry.getKey().player(), value,
                    (prior, next) -> new DailyStatSnapshot(prior.gamesPlayed() + next.gamesPlayed(),
                            prior.wins() + next.wins(), prior.lineCount() + next.lineCount(),
                            prior.completedTasks() + next.completedTasks(),
                            Math.max(prior.maxCompletedTasks(), next.maxCompletedTasks())));
            if (entry.getKey().game() == GameTypeEnum.Bingo) {
                bingoTotals.compute(entry.getKey().player(), (ignored, prior) -> prior == null ? value
                        : new DailyStatSnapshot(prior.gamesPlayed() + value.gamesPlayed(),
                        prior.wins() + value.wins(), prior.lineCount() + value.lineCount(),
                        prior.completedTasks() + value.completedTasks(),
                        Math.max(prior.maxCompletedTasks(), value.maxCompletedTasks())));
            }
        }
        // Hologram-facing boards: overall win counts across every game and Bingo-only wins.
        rebuilt.put("wins", rankMetric(allGameTotals, DailyStatSnapshot::wins));
        rebuilt.put("bingo_wins", rankMetric(bingoTotals, DailyStatSnapshot::wins));
        rebuilt.put("bingo_lines", rankMetric(bingoTotals, DailyStatSnapshot::lineCount));
        rebuilt.put("bingo_completed_tasks", rankMetric(bingoTotals, DailyStatSnapshot::completedTasks));
        rebuilt.put("bingo_max_completed", rankMetric(bingoTotals, DailyStatSnapshot::maxCompletedTasks));
        Map<String, Map<UUID, Long>> timed = new HashMap<>();
        records.forEach((key, values) -> {
            timed.computeIfAbsent(recordBoardId(key), ignored -> new HashMap<>())
                    .merge(key.player(), values.getFirst().durationMs(), Math::min);
        });
        timed.forEach((id, values) -> rebuilt.put(id, values.entrySet().stream()
                .sorted(Map.Entry.<UUID, Long>comparingByValue().thenComparing(entry -> displayName(entry.getKey())))
                .limit(100).map(entry -> new DailyLeaderboardEntry(entry.getKey(), displayName(entry.getKey()),
                        entry.getValue(), true)).toList()));
        return rebuilt;
    }

    private List<DailyLeaderboardEntry> metricBoard(DailyMetric metric, @Nullable String map) {
        if (metric.isComposite()) return pkwMetricBoard(metric, map);
        Map<UUID, Double> values = new HashMap<>();
        if (metric.format() == DailyMetric.Format.TIME) {
            DailyRecordType type = recordType(metric);
            for (Map.Entry<RecordKey, List<DailyRecordEntry>> entry : records.entrySet()) {
                RecordKey key = entry.getKey();
                if (key.game() != metric.game() || key.type() != type) continue;
                if (map != null && !key.map().equals(map)) continue;
                values.merge(key.player(), (double) entry.getValue().getFirst().durationMs(), Math::min);
            }
        } else if (map != null) {
            for (Map.Entry<MapStatKey, DailyMapStat> entry : mapStats.entrySet()) {
                MapStatKey key = entry.getKey();
                if (key.game() != metric.game() || !key.map().equals(map)) continue;
                if ( gateGames(entry.getValue(), metric) < metric.leaderboardMinGames()) continue;
                double value = metricValue(key.player(), map, metric);
                if (!Double.isNaN(value)) values.put(key.player(), value);
            }
        } else {
            // Cross-map board: fold every player's maps once instead of once per map row.
            Set<UUID> players = new java.util.HashSet<>();
            for (MapStatKey key : mapStats.keySet()) {
                if (key.game() == metric.game()) players.add(key.player());
            }
            for (UUID player : players) {
                DailyMapStat stat = mapStat(player, metric.game(), null);
                if (gateGames(stat, metric) < metric.leaderboardMinGames()) continue;
                double value = metricValue(player, null, metric);
                if (!Double.isNaN(value)) values.put(player, value);
            }
        }
        Comparator<Map.Entry<UUID, Double>> ranking = metric.lowerBetter()
                ? Map.Entry.<UUID, Double>comparingByValue()
                : Map.Entry.<UUID, Double>comparingByValue().reversed();
        return values.entrySet().stream()
                .sorted(ranking.thenComparing(entry -> displayName(entry.getKey())))
                .limit(100)
                .map(entry -> new DailyLeaderboardEntry(entry.getKey(), displayName(entry.getKey()),
                        entry.getValue(), metric.format() == DailyMetric.Format.TIME))
                .toList();
    }

    private List<DailyLeaderboardEntry> pkwMetricBoard(DailyMetric metric, @Nullable String map) {
        DailyPkwRecordType type = metric == DailyMetric.PKW_STARS_TIME
                ? DailyPkwRecordType.STARS_TIME : DailyPkwRecordType.POINTS_TIME;
        Map<UUID, PkwRecordValue> values = new HashMap<>();
        for (Map.Entry<PkwRecordKey, PkwRecordValue> entry : pkwRecords.entrySet()) {
            PkwRecordKey key = entry.getKey();
            if (key.type() != type || (map != null && !key.map().equals(map))) continue;
            values.merge(key.player(), entry.getValue(), DailyStatsManager::betterPkwRecord);
        }
        return values.entrySet().stream()
                .sorted(Comparator.<Map.Entry<UUID, PkwRecordValue>>comparingDouble(entry -> entry.getValue().primaryValue())
                        .reversed()
                        .thenComparingLong(entry -> entry.getValue().durationMs())
                        .thenComparing(entry -> displayName(entry.getKey()), String.CASE_INSENSITIVE_ORDER))
                .limit(100)
                .map(entry -> new DailyLeaderboardEntry(entry.getKey(), displayName(entry.getKey()),
                        entry.getValue().primaryValue(), false, entry.getValue().durationMs()))
                .toList();
    }

    /**
     * Rate boards gate on tracked games only (their numerators were not recorded before the
     * per-map table existed); every other metric accepts the backfilled match-result history.
     */
    private static long gateGames(DailyMapStat stat, DailyMetric metric) {
        return metric.isRate() ? stat.trackedGames() : stat.gamesPlayed();
    }

    private Set<String> mapsWithStats(GameTypeEnum game) {
        Set<String> maps = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        if (game == GameTypeEnum.AceRace || game == GameTypeEnum.ParkourWarrior) {
            records.keySet().stream().filter(key -> key.game() == game).map(RecordKey::map).forEach(maps::add);
            pkwRecords.keySet().stream().map(PkwRecordKey::map).forEach(maps::add);
        }
        mapStats.keySet().stream().filter(key -> key.game() == game).map(MapStatKey::map).forEach(maps::add);
        return maps;
    }

    private static DailyRecordType recordType(DailyMetric metric) {
        return switch (metric) {
            case ACERACE_FASTEST_LAP -> DailyRecordType.ACERACE_FASTEST_LAP;
            case ACERACE_FASTEST_THREE_LAPS -> DailyRecordType.ACERACE_FASTEST_THREE_LAPS;
            default -> throw new IllegalArgumentException(metric + " is not backed by time records");
        };
    }

    private List<DailyLeaderboardEntry> rankMetric(Map<UUID, DailyStatSnapshot> values,
                                                   java.util.function.ToLongFunction<DailyStatSnapshot> metric) {
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
        databaseTasks.add(runnable);
        drainDatabaseTasks();
    }

    private void drainDatabaseTasks() {
        if (!databaseTaskRunning.compareAndSet(false, true)) return;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                Runnable task;
                while (active && (task = databaseTasks.poll()) != null) {
                    try {
                        task.run();
                    } catch (Exception exception) {
                        plugin.getLogger().log(java.util.logging.Level.SEVERE,
                                "DAILY 数据库队列任务失败", exception);
                    }
                }
            } finally {
                databaseTaskRunning.set(false);
                if (active && !databaseTasks.isEmpty()) drainDatabaseTasks();
            }
        });
    }

    private record StatKey(UUID player, GameTypeEnum game) {}
    private record RecordKey(UUID player, GameTypeEnum game, String map, DailyRecordType type) {}
    private record MapStatKey(UUID player, GameTypeEnum game, String map) {}
    private record MilestoneKey(UUID match, int teamId, DailyRecordType type) {}

    private record PkwRecordKey(UUID player, String map, DailyPkwRecordType type) {}

    private record PkwRecordValue(double primaryValue, long durationMs) {}

    private static List<DailyRecordEntry> concat(List<DailyRecordEntry> first,
                                                  List<DailyRecordEntry> second) {
        List<DailyRecordEntry> combined = new ArrayList<>();
        if (first != null) combined.addAll(first);
        if (second != null) combined.addAll(second);
        return combined;
    }

    /** Sorts attempts fastest-first, collapses a repeated write of the same match, and keeps three. */
    static @NotNull List<DailyRecordEntry> topRecords(@NotNull List<DailyRecordEntry> candidates) {
        Map<UUID, DailyRecordEntry> byMatch = new HashMap<>();
        for (DailyRecordEntry candidate : candidates) {
            DailyRecordEntry previous = byMatch.get(candidate.matchId());
            if (previous == null || compareRecords(candidate, previous) < 0) {
                byMatch.put(candidate.matchId(), candidate);
            }
        }
        List<DailyRecordEntry> sorted = byMatch.values().stream()
                .sorted(DailyStatsManager::compareRecords)
                .limit(3)
                .toList();
        List<DailyRecordEntry> ranked = new ArrayList<>(sorted.size());
        for (int index = 0; index < sorted.size(); index++) ranked.add(sorted.get(index).withRank(index + 1));
        return List.copyOf(ranked);
    }

    private static int compareRecords(DailyRecordEntry first, DailyRecordEntry second) {
        return Comparator.comparingLong(DailyRecordEntry::durationMs)
                .thenComparingLong(DailyRecordEntry::achievedAt)
                .thenComparing(entry -> entry.matchId().toString())
                .compare(first, second);
    }

    private static PkwRecordValue betterPkwRecord(PkwRecordValue first, PkwRecordValue second) {
        return isBetterPkwRecord(second.primaryValue(), second.durationMs(),
                first.primaryValue(), first.durationMs()) ? second : first;
    }

    static boolean isBetterPkwRecord(double candidatePrimary, long candidateDuration,
                                     double currentPrimary, long currentDuration) {
        return candidatePrimary > currentPrimary
                || (candidatePrimary == currentPrimary && candidateDuration < currentDuration);
    }

    /** Monotonic per-player in-match progress; merge keeps the peak counts and ORs first flags. */
    private record MatchProgress(long lines, long tasks, long firsts, double dragonDamage,
                                 boolean firstLiberate, boolean firstNextGen, boolean firstGateway,
                                 long stars, boolean finished, long finishDurationMs) {
        MatchProgress merge(MatchProgress other) {
            return new MatchProgress(Math.max(lines, other.lines), Math.max(tasks, other.tasks),
                    Math.max(firsts, other.firsts), Math.max(dragonDamage, other.dragonDamage),
                    firstLiberate || other.firstLiberate, firstNextGen || other.firstNextGen,
                    firstGateway || other.firstGateway,
                    Math.max(stars, other.stars), finished || other.finished,
                    other.finishDurationMs() >= 0L ? other.finishDurationMs() : finishDurationMs);
        }
    }
}
