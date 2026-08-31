package ink.ziip.championshipscore.api.rank;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.finale.FinaleGameRegistry;
import ink.ziip.championshipscore.api.event.EventStateStore;
import ink.ziip.championshipscore.api.BaseManager;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.player.dao.PlayerDaoImpl;
import ink.ziip.championshipscore.api.player.entry.PlayerEntry;
import ink.ziip.championshipscore.api.rank.dao.RankDaoImpl;
import ink.ziip.championshipscore.api.rank.entry.GameStatusEntry;
import ink.ziip.championshipscore.api.rank.entry.PlayerPointEntry;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.CCConfig;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.database.sync.DatabaseSyncDomain;
import ink.ziip.championshipscore.util.Utils;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;

public class RankManager extends BaseManager {
    public record PointSubmission(@NotNull UUID transactionId, @NotNull UUID playerId,
                                  @Nullable ChampionshipTeam rival, @NotNull GameTypeEnum game,
                                  @NotNull String area, @NotNull String round, double points) {
    }

    private record FrozenPointSubmission(UUID transactionId, UUID playerId,
                                         int teamId, String teamName, int rivalId, String rivalName,
                                         GameTypeEnum game, String area, String round, double points) {
    }
    private static final long JOIN_RECAP_WINDOW_MILLIS = 10 * 60 * 1000L;
    /** Registered finale games decide the champion and never alter regular-season ranking data. */
    private static final Set<GameTypeEnum> SCORING_GAMES =
            Collections.unmodifiableSet(EnumSet.complementOf(EnumSet.copyOf(FinaleGameRegistry.gameTypes())));
    private final Map<ChampionshipTeam, Double> teamPoints = new ConcurrentHashMap<>();
    private final Map<UUID, Double> playerPoints = new ConcurrentHashMap<>();
    private final Map<ChampionshipTeam, Integer> teamRank = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> playerRank = new ConcurrentHashMap<>();
    /**
     * Published as one immutable snapshot. Clearing and repopulating a shared map exposed a transient
     * round 0 to PlaceholderAPI on every periodic database refresh.
     */
    private volatile Map<GameTypeEnum, Integer> gameOrder = Map.of();
    /** The same immutable database snapshot backing all total and per-game presentation paths. */
    private volatile List<PlayerPointEntry> validPointSnapshot = List.of();
    private final Map<GameTypeEnum, BigDecimal> gameWeight = new ConcurrentHashMap<>();
    private final Map<GameTypeEnum, Double> gameTotalPoints = new ConcurrentHashMap<>();
    private final RankDaoImpl rankDao = new RankDaoImpl();
    private final PlayerDaoImpl playerDao = new PlayerDaoImpl();
    private final BukkitScheduler scheduler = Bukkit.getScheduler();
    private final Queue<Runnable> rankTaskQueue = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean rankTaskRunning = new AtomicBoolean();
    private final AtomicBoolean periodicRefreshPending = new AtomicBoolean();
    private final PendingPointTransactionStore pendingPointTransactions;
    private volatile EventStateStore.ActiveEvent activeEvent;
    @Getter
    private List<Map.Entry<ChampionshipTeam, Double>> teamLeaderboard = new ArrayList<>();
    @Getter
    private List<Map.Entry<UUID, Double>> playerLeaderboard = new ArrayList<>();
    @Getter
    private String teamRankString;
    @Getter
    private String playerRankString = "";
    private volatile String latestRankingSummary;
    private volatile long latestRankingSummaryAt;
    private BukkitTask updateTask;

    public RankManager(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
        pendingPointTransactions = new PendingPointTransactionStore(championshipsCore);
    }

    @Override
    public void load() {
        reloadActiveEventScoring();
        List<PlayerPointEntry> pendingEntries = pendingPointTransactions.load();
        if (!pendingEntries.isEmpty()) enqueueRankTask(() -> commitPointTransactions(pendingEntries));
        if (!pendingEntries.isEmpty()) {
            plugin.getLogger().info(Utils.formatModuleLog("Rank", "暂存事务",
                    "恢复待提交积分事务=" + pendingEntries.size()));
        }
        updateTask = scheduler.runTaskTimer(plugin, this::queuePeriodicRefresh, 0, 100L);
    }

    @Override
    public void unload() {
        if (updateTask != null)
            updateTask.cancel();
        validPointSnapshot = List.of();
    }

    public int getPlayerRank(Player player) {
        return getPlayerRank(player.getUniqueId());
    }

    public int getPlayerRank(@NotNull UUID uuid) {
        return playerRank.getOrDefault(uuid, 0);
    }

    public double getPlayerPoints(Player player) {
        return getPlayerPoints(player.getUniqueId());
    }

    public double getPlayerPoints(@NotNull UUID uuid) {
        return playerPoints.getOrDefault(uuid, 0D);
    }

    public int getPlayerTeamRank(Player player) {
        return getPlayerTeamRank(player.getUniqueId());
    }

    public int getPlayerTeamRank(@NotNull UUID uuid) {
        ChampionshipTeam championshipTeam = plugin.getTeamManager().getFormalTeamByPlayer(uuid);
        if (championshipTeam != null) {
            return teamRank.getOrDefault(championshipTeam, Integer.MAX_VALUE);
        }
        return 0;
    }

    public int getRound() {
        Map<GameTypeEnum, Integer> snapshot = gameOrder;
        return (int) snapshot.keySet().stream().filter(RankManager::isScoringGame).count();
    }

    public double getPlayerTeamPoints(Player player) {
        return getPlayerTeamPoints(player.getUniqueId());
    }

    public double getPlayerTeamPoints(@NotNull UUID uuid) {
        ChampionshipTeam championshipTeam = plugin.getTeamManager().getFormalTeamByPlayer(uuid);
        if (championshipTeam != null) {
            return getTeamPoints(championshipTeam);
        }
        return 0D;
    }

    /** Current cached event total for a team, exposed for immutable cross-server match snapshots. */
    public double getTeamPoints(@NotNull ChampionshipTeam championshipTeam) {
        return teamPoints.getOrDefault(championshipTeam, -1D);
    }

    private void updateTeamPoints(@NotNull List<PlayerPointEntry> pointSnapshot) {
        EnumMap<GameTypeEnum, Double> refreshedGameTotals = new EnumMap<>(GameTypeEnum.class);
        for (GameTypeEnum gameTypeEnum : SCORING_GAMES) {
            refreshedGameTotals.put(gameTypeEnum, 0D);
        }

        Map<Integer, List<PlayerPointEntry>> pointsByTeamId = new HashMap<>();
        for (PlayerPointEntry point : pointSnapshot) {
            pointsByTeamId.computeIfAbsent(point.getTeamId(), ignored -> new ArrayList<>()).add(point);
        }
        Map<ChampionshipTeam, List<PlayerPointEntry>> entriesByTeam = new HashMap<>();
        for (ChampionshipTeam championshipTeam : plugin.getTeamManager().getTeamList()) {
            List<PlayerPointEntry> entries = pointsByTeamId.getOrDefault(championshipTeam.getId(), List.of());
            entriesByTeam.put(championshipTeam, entries);
            for (PlayerPointEntry entry : entries) {
                if (entry.getValid() == 1 && isScoringGame(entry.getGame())) {
                    refreshedGameTotals.merge(entry.getGame(), entry.getPoints(), Double::sum);
                }
            }
        }

        gameTotalPoints.clear();
        gameTotalPoints.putAll(refreshedGameTotals);
        updateGameWeights();

        for (Map.Entry<ChampionshipTeam, List<PlayerPointEntry>> entry : entriesByTeam.entrySet()) {
            teamPoints.put(entry.getKey(), calculateFinalPoints(entry.getValue()));
        }
        for (ChampionshipTeam championshipTeam : teamPoints.keySet()) {
            if (!plugin.getTeamManager().getTeamList().contains(championshipTeam))
                teamPoints.remove(championshipTeam);
        }

        ArrayList<Map.Entry<ChampionshipTeam, Double>> list;
        list = new ArrayList<>(teamPoints.entrySet());
        list.sort(Map.Entry.comparingByValue());

        Collections.reverse(list);

        StringBuilder stringBuilder = new StringBuilder();

        stringBuilder.append(MessageConfig.RANK_TEAM_BOARD_BAR).append("\n");

        int i = 1;
        for (Map.Entry<ChampionshipTeam, Double> entry : list) {
            String row = MessageConfig.RANK_TEAM_BOARD_ROW
                    .replace("%team_rank%", String.valueOf(i))
                    .replace("%team%", entry.getKey().getColoredName())
                    .replace("%team_point%", Utils.formatPoints(entry.getValue()));

            stringBuilder.append(row).append("\n");

            teamRank.put(entry.getKey(), i);
            i++;
        }

        teamLeaderboard = list;
        teamRankString = stringBuilder.toString();
    }

    private void updatePlayerPoint(@NotNull List<PlayerPointEntry> pointSnapshot) {
        Map<UUID, List<PlayerPointEntry>> pointsByPlayer = new HashMap<>();
        for (PlayerPointEntry point : pointSnapshot) {
            pointsByPlayer.computeIfAbsent(point.getUuid(), ignored -> new ArrayList<>()).add(point);
        }
        Map<UUID, Double> refreshedPlayerPoints = new HashMap<>();
        for (ChampionshipTeam championshipTeam : plugin.getTeamManager().getTeamList()) {
            for (UUID uuid : championshipTeam.getMembers()) {
                refreshedPlayerPoints.put(uuid,
                        calculateFinalPoints(pointsByPlayer.getOrDefault(uuid, List.of())));
            }
        }
        playerPoints.keySet().removeIf(uuid -> !refreshedPlayerPoints.containsKey(uuid));
        playerPoints.putAll(refreshedPlayerPoints);
        playerRank.clear();

        ArrayList<Map.Entry<UUID, Double>> list;
        list = new ArrayList<>(playerPoints.entrySet());
        list.sort(Map.Entry.comparingByValue());

        Collections.reverse(list);

        StringBuilder stringBuilder = new StringBuilder();

        stringBuilder.append(MessageConfig.RANK_PLAYER_BOARD_BAR).append("\n");

        int i = 1;
        for (Map.Entry<UUID, Double> entry : list) {
            String row = MessageConfig.RANK_PLAYER_BOARD_ROW
                    .replace("%player_rank%", String.valueOf(i))
                    .replace("%player%", Utils.formatPlayerName(entry.getKey()))
                    .replace("%player_point%", Utils.formatPoints(entry.getValue()));

            stringBuilder.append(row).append("\n");

            playerRank.put(entry.getKey(), i);
            i++;
            if (i == 11) {
                playerRankString = stringBuilder.toString();
                break;
            }
        }
        if (i <= 11)
            playerRankString = stringBuilder.toString();

        playerLeaderboard = list;

    }

    /** Rebuilds game weights from the complete raw-score snapshot before weighted team totals are calculated. */
    private void updateGameWeights() {
        gameWeight.clear();
        for (GameTypeEnum gameTypeEnum : SCORING_GAMES) {
            try {
                BigDecimal totalNum = BigDecimal.valueOf(15000D).setScale(4, RoundingMode.HALF_UP);
                BigDecimal weight = totalNum.divide(BigDecimal.valueOf(gameTotalPoints.get(gameTypeEnum)), RoundingMode.HALF_UP);

                if (weight.compareTo(BigDecimal.ZERO) != 0)
                    gameWeight.put(gameTypeEnum, weight);
                else
                    gameWeight.put(gameTypeEnum, BigDecimal.ONE);
            } catch (Exception ignored) {
                gameWeight.put(gameTypeEnum, BigDecimal.ONE);
            }
        }
    }

    private void updateGameOrder() {
        List<GameStatusEntry> queried = rankDao.getGameStatusList()
                .orElseThrow(() -> new IllegalStateException("Unable to query game status"));
        EnumMap<GameTypeEnum, Integer> refreshed = new EnumMap<>(GameTypeEnum.class);
        for (GameStatusEntry gameStatusEntry : queried) {
            if (isScoringGame(gameStatusEntry.getGame()))
                refreshed.put(gameStatusEntry.getGame(), gameStatusEntry.getOrder());
        }
        gameOrder = Map.copyOf(refreshed);
    }

    public void addGameOrder(GameTypeEnum gameTypeEnum, int order) {
        if (!isScoringGame(gameTypeEnum)) return;
        enqueueRankTask(() -> {
            if (rankDao.getGameStatusOrder(gameTypeEnum) != -1)
                return;

            GameStatusEntry gameStatusEntry = GameStatusEntry.builder()
                    .game(gameTypeEnum)
                    .order(order)
                    .time(Utils.getCurrentTimeString())
                    .build();
            rankDao.addGameStatus(gameStatusEntry);
            publishRankChange("game-order-added");
        });
    }

    public int getGameOrder(GameTypeEnum gameTypeEnum) {
        Integer order = gameOrder.get(gameTypeEnum);
        if (order == null)
            return -1;
        return order;
    }

    public void resetGameOrder() {
        enqueueRankTask(() -> {
            for (GameTypeEnum gameTypeEnum : GameTypeEnum.values()) {
                rankDao.deleteGameStatus(gameTypeEnum);
            }
            gameOrder = Map.of();
            publishRankChange("game-order-reset");
        });
    }

    /** @return the game type with the highest round order (the most recently started game), or null if none. */
    public GameTypeEnum getLatestGame() {
        GameTypeEnum latest = null;
        int maxOrder = -1;
        Map<GameTypeEnum, Integer> snapshot = gameOrder;
        for (Map.Entry<GameTypeEnum, Integer> entry : snapshot.entrySet()) {
            if (isScoringGame(entry.getKey()) && entry.getValue() > maxOrder) {
                maxOrder = entry.getValue();
                latest = entry.getKey();
            }
        }
        return latest;
    }

    /** Deletes one game's status entry + soft-deletes (valid=0) all its point records, and drops it from the in-memory order map. */
    public void deleteGameRecords(GameTypeEnum gameTypeEnum) {
        enqueueRankTask(() -> {
            rankDao.deleteGameStatus(gameTypeEnum);
            rankDao.deleteGamePoints(gameTypeEnum);
            EnumMap<GameTypeEnum, Integer> updated = new EnumMap<>(GameTypeEnum.class);
            updated.putAll(gameOrder);
            updated.remove(gameTypeEnum);
            gameOrder = Map.copyOf(updated);
            publishRankChange("game-records-deleted");
        });
    }

    public void addPlayerPoints(UUID uuid, ChampionshipTeam rival, GameTypeEnum gameTypeEnum, String area, double points) {
        addPlayerPointsWithTransaction(UUID.randomUUID(), uuid, rival, gameTypeEnum, area, "scc", points)
                .exceptionally(failure -> {
                    plugin.getLogger().log(java.util.logging.Level.SEVERE,
                            Utils.formatModuleLog("Rank", "积分", "积分提交失败 玩家=" + uuid), failure);
                    return false;
                });
    }

    /**
     * Idempotent score entry point for remote game replay. Callers derive {@code transactionId} from
     * match/epoch/sequence/player/award-kind and may safely retry the same write after Redis redelivery.
     */
    public CompletionStage<Boolean> addPlayerPointsWithTransaction(
            @NotNull UUID transactionId, @NotNull UUID uuid, ChampionshipTeam rival,
            @NotNull GameTypeEnum gameTypeEnum, @NotNull String area,
            @NotNull String round, double points) {
        return addPlayerPointsBatch(List.of(new PointSubmission(transactionId, uuid, rival,
                gameTypeEnum, area, round, points)));
    }

    /** Freezes team ownership on the server thread, then performs DB lookup and durable IO in rank order. */
    public CompletionStage<Boolean> addPlayerPointsBatch(@NotNull Collection<PointSubmission> submissions) {
        List<FrozenPointSubmission> frozen = new ArrayList<>();
        for (PointSubmission submission : submissions) {
            if (!isScoringGame(submission.game())) continue;
            ChampionshipTeam team = plugin.getTeamManager().getFormalTeamByPlayer(submission.playerId());
            if (team == null) return CompletableFuture.completedFuture(false);
            ChampionshipTeam rival = submission.rival() == null ? team : submission.rival();
            frozen.add(new FrozenPointSubmission(submission.transactionId(), submission.playerId(),
                    team.getId(), team.getName(), rival.getId(), rival.getName(), submission.game(),
                    submission.area(), submission.round(), submission.points()));
        }
        if (frozen.isEmpty()) return CompletableFuture.completedFuture(true);

        CompletableFuture<Boolean> accepted = new CompletableFuture<>();
        enqueueRankTask(() -> {
            try {
                List<PlayerPointEntry> entries = new ArrayList<>(frozen.size());
                String timestamp = Utils.getCurrentTimeString();
                for (FrozenPointSubmission submission : frozen) {
                    PlayerEntry player = playerDao.getPlayer(submission.playerId());
                    if (player == null) {
                        accepted.complete(false);
                        return;
                    }
                    entries.add(PlayerPointEntry.builder()
                            .transactionId(submission.transactionId()).uuid(player.getUuid())
                            .username(player.getName()).teamId(submission.teamId())
                            .team(submission.teamName()).rivalId(submission.rivalId())
                            .rival(submission.rivalName()).game(submission.game()).area(submission.area())
                            .round(submission.round()).points(submission.points()).time(timestamp).build());
                }
                if (!pendingPointTransactions.stageAll(entries)) {
                    plugin.getLogger().severe(Utils.formatModuleLog("Rank", "暂存事务",
                            "整批积分未入队：无法持久化事务数=" + entries.size()));
                    accepted.complete(false);
                    return;
                }
                commitPointTransactions(entries);
                accepted.complete(true);
            } catch (Throwable failure) {
                accepted.completeExceptionally(failure);
                if (failure instanceof RuntimeException runtime) throw runtime;
                throw new RuntimeException(failure);
            }
        });
        return accepted;
    }

    private void commitPointTransactions(@NotNull List<PlayerPointEntry> entries) {
        if (rankDao.addPlayerPoints(entries)) {
            pendingPointTransactions.completeAll(entries.stream()
                    .map(PlayerPointEntry::getTransactionId).toList());
            publishRankChange("player-points-added");
        }
    }

    /** Queues a full cache refresh after all score writes submitted before this call. */
    public void refreshAfterPendingPointWrites() {
        enqueueRankTask(this::refreshRankingsNow);
    }

    /** Queues a complete authoritative cache rebuild after all database work already submitted here. */
    public CompletionStage<Void> refreshFromDatabase() {
        CompletableFuture<Void> refreshed = new CompletableFuture<>();
        enqueueRankTask(() -> {
            try {
                refreshRankingsNow();
                refreshed.complete(null);
            } catch (RuntimeException failure) {
                refreshed.completeExceptionally(failure);
                throw failure;
            }
        });
        return refreshed;
    }

    /** Builds the complete current formal-event score table after all pending point writes. */
    public CompletionStage<ChampionshipArchiveSnapshot> createChampionshipArchiveSnapshot() {
        return refreshFromDatabase().thenApply(ignored -> buildChampionshipArchiveSnapshot());
    }

    private ChampionshipArchiveSnapshot buildChampionshipArchiveSnapshot() {
        List<Map.Entry<GameTypeEnum, Integer>> games = gameOrder.entrySet().stream()
                .filter(entry -> isScoringGame(entry.getKey()))
                .sorted(Map.Entry.comparingByValue()).toList();
        List<ChampionshipTeam> teams = new ArrayList<>(plugin.getTeamManager().getTeamList());
        Map<Integer, ChampionshipTeam> teamById = new HashMap<>();
        Map<Integer, Map<GameTypeEnum, Double>> teamScores = new HashMap<>();
        Map<UUID, Map<GameTypeEnum, Double>> playerScores = new HashMap<>();
        for (ChampionshipTeam team : teams) {
            teamById.put(team.getId(), team);
            teamScores.put(team.getId(), new EnumMap<>(GameTypeEnum.class));
            for (UUID member : team.getMembers()) playerScores.put(member, new EnumMap<>(GameTypeEnum.class));
        }
        for (PlayerPointEntry entry : validPointSnapshot) {
            ChampionshipTeam team = teamById.get(entry.getTeamId());
            if (team == null || entry.getValid() != 1 || !isScoringGame(entry.getGame())
                    || !playerScores.containsKey(entry.getUuid())) continue;
            int order = getGameOrder(entry.getGame());
            if (order < 1) continue;
            double contribution = Boolean.TRUE.equals(CCConfig.WEIGHTED_SCORE)
                    ? entry.getPoints() * getPointMultiple(order) * getGameWeight(entry.getGame())
                    : entry.getPoints();
            teamScores.get(team.getId()).merge(entry.getGame(), contribution, Double::sum);
            playerScores.get(entry.getUuid()).merge(entry.getGame(), contribution, Double::sum);
        }

        List<ChampionshipArchiveSnapshot.TeamScore> rankedTeams = teams.stream().map(team -> {
            Map<GameTypeEnum, Double> scores = teamScores.get(team.getId());
            List<ChampionshipArchiveSnapshot.GameScore> perGame = archiveGameScores(games, scores);
            return new ChampionshipArchiveSnapshot.TeamScore(team.getName(), 0,
                    rounded(scores.values().stream().mapToDouble(Double::doubleValue).sum()), perGame);
        }).sorted(Comparator.comparingDouble(ChampionshipArchiveSnapshot.TeamScore::totalScore).reversed()
                .thenComparing(ChampionshipArchiveSnapshot.TeamScore::name, String.CASE_INSENSITIVE_ORDER)).toList();
        List<ChampionshipArchiveSnapshot.TeamScore> withRanks = new ArrayList<>(rankedTeams.size());
        for (int index = 0; index < rankedTeams.size(); index++) {
            ChampionshipArchiveSnapshot.TeamScore team = rankedTeams.get(index);
            withRanks.add(new ChampionshipArchiveSnapshot.TeamScore(team.name(), index + 1,
                    team.totalScore(), team.gameScores()));
        }

        List<ChampionshipArchiveSnapshot.PlayerScore> players = new ArrayList<>();
        teams.stream().sorted(Comparator.comparing(ChampionshipTeam::getName, String.CASE_INSENSITIVE_ORDER))
                .forEach(team -> team.getTeamMemberEntries().forEach(member -> {
                    Map<GameTypeEnum, Double> scores = playerScores.getOrDefault(member.getUuid(), Map.of());
                    players.add(new ChampionshipArchiveSnapshot.PlayerScore(member.getUsername(), team.getName(),
                            rounded(scores.values().stream().mapToDouble(Double::doubleValue).sum()), false,
                            archiveGameScores(games, scores)));
                }));
        return new ChampionshipArchiveSnapshot(List.copyOf(withRanks), List.copyOf(players));
    }

    private static List<ChampionshipArchiveSnapshot.GameScore> archiveGameScores(
            List<Map.Entry<GameTypeEnum, Integer>> games, Map<GameTypeEnum, Double> scores) {
        return games.stream().map(entry -> new ChampionshipArchiveSnapshot.GameScore(
                entry.getKey().name(), entry.getKey().toString(), entry.getKey().name(),
                entry.getValue(), rounded(scores.getOrDefault(entry.getKey(), 0D)))).toList();
    }

    private static double rounded(double value) {
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP).doubleValue();
    }

    /** Runs database-sensitive administration after all score writes submitted before this call. */
    public void runAfterPendingPointWrites(@NotNull Runnable task) {
        enqueueRankTask(task);
    }

    /** Rewrites durable score transactions which could be retried after a map rename. */
    public boolean renamePendingAreaRecords(@NotNull GameTypeEnum game,
                                            @NotNull String oldArea, @NotNull String newArea) {
        return pendingPointTransactions.renameArea(game, oldArea, newArea);
    }

    /** Resolves finalists only after every score write submitted before this call has reached the cache. */
    public void withFreshTeamLeaderboard(java.util.function.Consumer<List<Map.Entry<ChampionshipTeam, Double>>> callback) {
        enqueueRankTask(() -> {
            refreshRankingsNow();
            List<Map.Entry<ChampionshipTeam, Double>> snapshot = List.copyOf(teamLeaderboard);
            scheduler.runTask(plugin, () -> callback.accept(snapshot));
        });
    }

    public double getCachedTeamPoints(@NotNull ChampionshipTeam team) {
        for (Map.Entry<ChampionshipTeam, Double> entry : teamLeaderboard) {
            if (entry.getKey().equals(team)) return entry.getValue();
        }
        return 0D;
    }

    /** Refreshes caches after all prior score writes and broadcasts a six-line round summary. */
    public void broadcastFinalRankings(GameTypeEnum gameTypeEnum) {
        broadcastFinalRankings(gameTypeEnum, () -> { });
    }

    /** Runs the callback on the main thread after the final ranking has actually been shown. */
    public void broadcastFinalRankings(GameTypeEnum gameTypeEnum, @NotNull Runnable afterBroadcast) {
        if (!isScoringGame(gameTypeEnum)) {
            scheduler.runTask(plugin, afterBroadcast);
            return;
        }
        enqueueRankTask(() -> {
            refreshRankingsNow();
            List<Map.Entry<ChampionshipTeam, Double>> gameLeaderboard = getGameTeamLeaderboard(gameTypeEnum);
            String summary = buildFinalRankingSummary(gameTypeEnum, gameLeaderboard);
            latestRankingSummary = summary;
            latestRankingSummaryAt = System.currentTimeMillis();

            String winner = MessageConfig.PLACEHOLDER_NONE;
            String winnerPoints = "0";
            if (!gameLeaderboard.isEmpty()) {
                winner = gameLeaderboard.get(0).getKey().getColoredName();
                winnerPoints = Utils.formatPoints(gameLeaderboard.get(0).getValue());
            }
            String subtitle = "&#ededed#1 " + winner + " &#bababa• &#ff6b26" + winnerPoints + " 分";
            scheduler.runTask(plugin, () -> {
                Utils.sendMessageToAllPlayers(summary);
                Utils.sendTitleToAllPlayers("&#fff566&l本轮结算", subtitle, 60);
                Utils.playSoundToAllPlayers(org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 0.8F, 1.1F);
                scheduler.runTaskLater(plugin, () -> Utils.sendActionBarToAllPlayers(
                        "&#bababa聊天被顶掉？ &#ededed/cc rank recap &#bababa可重看本次结算"), 60L);
                afterBroadcast.run();
            });
        });
    }

    private String buildFinalRankingSummary(GameTypeEnum gameTypeEnum,
                                            List<Map.Entry<ChampionshipTeam, Double>> gameLeaderboard) {
        StringBuilder result = new StringBuilder(MessageConfig.RANK_FINAL_BOARD_BAR
                .replace("%game%", gameTypeEnum.toString())).append("\n");

        int rows = Math.min(4, gameLeaderboard.size());
        for (int i = 0; i < rows; i++) {
            Map.Entry<ChampionshipTeam, Double> entry = gameLeaderboard.get(i);
            result.append(MessageConfig.RANK_TEAM_BOARD_ROW
                            .replace("%team_rank%", String.valueOf(i + 1))
                            .replace("%team%", entry.getKey().getColoredName())
                            .replace("%team_point%", Utils.formatPoints(entry.getValue())))
                    .append("\n");
        }

        result.append(MessageConfig.RANK_FINAL_RECAP_HINT);
        return result.toString();
    }

    /** Replays the latest round summary on demand, even after the automatic join window. */
    public void sendLatestRankingSummary(Player player) {
        String summary = latestRankingSummary;
        if (summary == null || summary.isBlank()) {
            player.sendMessage(MessageConfig.RANK_NO_RECAP);
            return;
        }
        player.sendMessage(summary);
        Utils.sendActionBar(player, "&#ededed已重新显示最近一次结算");
    }

    /** Replays a recent result to players who disconnected while it was announced. */
    public void replayRecentRankingSummary(Player player) {
        String summary = latestRankingSummary;
        if (summary == null || System.currentTimeMillis() - latestRankingSummaryAt > JOIN_RECAP_WINDOW_MILLIS)
            return;
        player.sendMessage(summary);
        Utils.sendActionBar(player, "&#bababa完整总榜 &#ededed/cc rank teamboard");
    }

    private void queuePeriodicRefresh() {
        if (!periodicRefreshPending.compareAndSet(false, true))
            return;
        enqueueRankTask(() -> {
            try {
                refreshRankingsNow();
            } finally {
                periodicRefreshPending.set(false);
            }
        });
    }

    private void refreshRankingsNow() {
        updateGameOrder();
        List<PlayerPointEntry> pointSnapshot = rankDao.getAllValidPlayerPoints()
                .orElseThrow(() -> new IllegalStateException("Unable to query valid point snapshot"));
        validPointSnapshot = pointSnapshot;
        updateTeamPoints(pointSnapshot);
        updatePlayerPoint(pointSnapshot);
    }

    /** Runs rank database work in submission order on one Bukkit async worker. */
    private void enqueueRankTask(Runnable task) {
        rankTaskQueue.add(task);
        drainRankTaskQueue();
    }

    private void drainRankTaskQueue() {
        if (!rankTaskRunning.compareAndSet(false, true))
            return;
        scheduler.runTaskAsynchronously(plugin, () -> {
            try {
                Runnable task;
                while ((task = rankTaskQueue.poll()) != null) {
                    try {
                        task.run();
                    } catch (Exception exception) {
                        plugin.getLogger().log(java.util.logging.Level.SEVERE,
                                Utils.formatModuleLog("Rank", "异步任务", "积分数据库任务失败"), exception);
                    }
                }
            } finally {
                rankTaskRunning.set(false);
                if (!rankTaskQueue.isEmpty())
                    drainRankTaskQueue();
            }
        });
    }

    /** Applies the same game-normalization weight and round multiplier used by the spreadsheet's K column. */
    private double calculateFinalPoints(List<PlayerPointEntry> playerPointEntries) {
        double points = 0;
        for (GameTypeEnum gameTypeEnum : SCORING_GAMES) {
            int gameOrder = getGameOrder(gameTypeEnum);
            for (PlayerPointEntry playerPointEntry : playerPointEntries) {
                if (playerPointEntry.getValid() == 1 && playerPointEntry.getGame() == gameTypeEnum) {
                    if (Boolean.TRUE.equals(CCConfig.WEIGHTED_SCORE)) {
                        points += playerPointEntry.getPoints() * getPointMultiple(gameOrder)
                                * getGameWeight(gameTypeEnum);
                    } else
                        points += playerPointEntry.getPoints();
                }
            }

        }

        BigDecimal finalPoints = BigDecimal.valueOf(points).setScale(4, RoundingMode.HALF_UP);
        finalPoints = finalPoints.setScale(4, RoundingMode.HALF_UP);

        return finalPoints.doubleValue();
    }

    public double getPointMultiple(int round) {
        EventStateStore.ActiveEvent event = activeEvent;
        List<Double> multipliers = event == null
                ? CCConfig.WEIGHTED_SCORE_ROUND_MULTIPLIERS : event.roundMultipliers();
        return configuredPointMultiple(round, multipliers);
    }

    public void reloadActiveEventScoring() {
        activeEvent = new EventStateStore(plugin).load();
    }


    static double configuredPointMultiple(int round, List<Double> multipliers) {
        if (round < 1 || multipliers == null || round > multipliers.size()) return 0D;
        Double multiplier = multipliers.get(round - 1);
        return multiplier == null ? 0D : multiplier;
    }

    private double getTeamPoints(ChampionshipTeam championshipTeam, GameTypeEnum gameTypeEnum) {
        if (!isScoringGame(gameTypeEnum)) return 0D;
        double points = 0;
        for (PlayerPointEntry playerPointEntry : validPointSnapshot) {
            if (playerPointEntry.getTeamId() == championshipTeam.getId()
                    && playerPointEntry.getGame() == gameTypeEnum) {
                points += playerPointEntry.getPoints();
            }
        }
        return points;
    }

    public String getGameTeamPoints(GameTypeEnum gameTypeEnum) {
        List<Map.Entry<ChampionshipTeam, Double>> list = getGameTeamLeaderboard(gameTypeEnum);

        StringBuilder stringBuilder = new StringBuilder();

        stringBuilder.append(MessageConfig.RANK_GAME_TEAM_BOARD_BAR.replace("%game%", gameTypeEnum.toString())).append("\n");

        int i = 1;
        for (Map.Entry<ChampionshipTeam, Double> entry : list) {
            String row = MessageConfig.RANK_TEAM_BOARD_ROW
                    .replace("%team_rank%", String.valueOf(i))
                    .replace("%team%", entry.getKey().getColoredName())
                    .replace("%team_point%", Utils.formatPoints(entry.getValue()));

            stringBuilder.append(row).append("\n");

            i++;
        }

        return stringBuilder.toString();
    }

    private List<Map.Entry<ChampionshipTeam, Double>> getGameTeamLeaderboard(GameTypeEnum gameTypeEnum) {
        Map<ChampionshipTeam, Double> teamGamePoints = new HashMap<>();
        for (ChampionshipTeam championshipTeam : plugin.getTeamManager().getTeamList()) {
            teamGamePoints.put(championshipTeam, getTeamPoints(championshipTeam, gameTypeEnum));
        }
        List<Map.Entry<ChampionshipTeam, Double>> list = new ArrayList<>(teamGamePoints.entrySet());
        list.sort(Map.Entry.<ChampionshipTeam, Double>comparingByValue().reversed());
        return list;
    }

    public double getGameWeight(GameTypeEnum gameTypeEnum) {
        if (!isScoringGame(gameTypeEnum)) return 1D;
        BigDecimal weight = gameWeight.getOrDefault(gameTypeEnum, BigDecimal.ONE).setScale(4, RoundingMode.HALF_UP);
        return weight.doubleValue();
    }

    public String getGameWeightInfo() {
        StringBuilder stringBuilder = new StringBuilder();

        stringBuilder.append(MessageConfig.RANK_GAME_WEIGHT_BAR).append("\n");

        for (GameTypeEnum gameTypeEnum : SCORING_GAMES) {
            String row = MessageConfig.RANK_GAME_WEIGHT_ROW
                    .replace("%game%", gameTypeEnum.toString())
                    .replace("%weight%", String.valueOf(getGameWeight(gameTypeEnum)))
                    .replace("%total_point%", Utils.formatPoints(gameTotalPoints.getOrDefault(gameTypeEnum, 0D)));

            stringBuilder.append(row).append("\n");
        }

        return stringBuilder.toString();
    }

    private static boolean isScoringGame(@Nullable GameTypeEnum gameTypeEnum) {
        return gameTypeEnum != null && SCORING_GAMES.contains(gameTypeEnum);
    }

    private void publishRankChange(String reason) {
        plugin.getRedisManager().publishDatabaseChange(reason, DatabaseSyncDomain.RANK);
    }
}
