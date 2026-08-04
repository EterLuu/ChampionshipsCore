package ink.ziip.championshipscore.api.rank;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseManager;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.player.dao.PlayerDaoImpl;
import ink.ziip.championshipscore.api.player.entry.PlayerEntry;
import ink.ziip.championshipscore.api.rank.dao.RankDaoImpl;
import ink.ziip.championshipscore.api.rank.entry.GameStatusEntry;
import ink.ziip.championshipscore.api.rank.entry.PlayerPointEntry;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.api.team.dao.TeamDaoImpl;
import ink.ziip.championshipscore.api.team.entry.TeamMemberEntry;
import ink.ziip.championshipscore.configuration.config.CCConfig;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
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
import java.util.concurrent.atomic.AtomicBoolean;

public class RankManager extends BaseManager {
    private static final long JOIN_RECAP_WINDOW_MILLIS = 10 * 60 * 1000L;
    /** Dodgebolt is a non-scoring final and must never participate in regular-season ranking data. */
    private static final Set<GameTypeEnum> SCORING_GAMES =
            Collections.unmodifiableSet(EnumSet.complementOf(EnumSet.of(GameTypeEnum.Dodgebolt)));
    private static final Map<ChampionshipTeam, Double> teamPoints = new ConcurrentHashMap<>();
    private static final Map<UUID, Double> playerPoints = new ConcurrentHashMap<>();
    private static final Map<ChampionshipTeam, Integer> teamRank = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> playerRank = new ConcurrentHashMap<>();
    private static final Map<GameTypeEnum, Integer> gameOrder = new ConcurrentHashMap<>();
    private static final Map<GameTypeEnum, BigDecimal> gameWeight = new ConcurrentHashMap<>();
    private static final Map<GameTypeEnum, Double> gameTotalPoints = new ConcurrentHashMap<>();
    private final RankDaoImpl rankDao = new RankDaoImpl();
    private final TeamDaoImpl teamDao = new TeamDaoImpl();
    private final PlayerDaoImpl playerDao = new PlayerDaoImpl();
    private final BukkitScheduler scheduler = Bukkit.getScheduler();
    private final Queue<Runnable> rankTaskQueue = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean rankTaskRunning = new AtomicBoolean();
    private final AtomicBoolean periodicRefreshPending = new AtomicBoolean();
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
    }

    @Override
    public void load() {
        updateTask = scheduler.runTaskTimer(plugin, this::queuePeriodicRefresh, 0, 100L);
    }

    @Override
    public void unload() {
        if (updateTask != null)
            updateTask.cancel();
    }

    public int getPlayerRank(Player player) {
        return playerRank.getOrDefault(player.getUniqueId(), 0);
    }

    public double getPlayerPoints(Player player) {
        return playerPoints.getOrDefault(player.getUniqueId(), 0D);
    }

    public int getPlayerTeamRank(Player player) {
        ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(player);
        if (championshipTeam != null) {
            return teamRank.getOrDefault(championshipTeam, Integer.MAX_VALUE);
        }
        return 0;
    }

    public int getRound() {
        return (int) gameOrder.keySet().stream().filter(RankManager::isScoringGame).count();
    }

    public double getPlayerTeamPoints(Player player) {
        ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(player);
        if (championshipTeam != null) {
            return teamPoints.getOrDefault(championshipTeam, -1D);
        }
        return 0D;
    }

    public void deleteTeamGamePoints(@NotNull ChampionshipTeam championshipTeam, GameTypeEnum gameType) {
        enqueueRankTask(() -> rankDao.deleteTeamPoints(championshipTeam.getId(), gameType));
    }

    private void updateTeamPoints() {
        gameTotalPoints.clear();
        for (GameTypeEnum gameTypeEnum : SCORING_GAMES) {
            gameTotalPoints.put(gameTypeEnum, 0D);
        }

        for (ChampionshipTeam championshipTeam : plugin.getTeamManager().getTeamList()) {
            teamPoints.put(championshipTeam, getTeamPoints(championshipTeam));
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

    private void updatePlayerPoint() {
        for (ChampionshipTeam championshipTeam : plugin.getTeamManager().getTeamList()) {
            for (TeamMemberEntry teamMemberEntry : teamDao.getTeamMembers(championshipTeam.getId())) {
                UUID uuid = teamMemberEntry.getUuid();
                playerPoints.put(uuid, getPlayerPoints(uuid));
            }
        }
        for (UUID uuid : playerPoints.keySet()) {
            ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(uuid);
            if (championshipTeam == null)
                playerPoints.remove(uuid);
        }

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
        gameOrder.clear();
        for (GameStatusEntry gameStatusEntry : rankDao.getGameStatusList()) {
            if (isScoringGame(gameStatusEntry.getGame()))
                gameOrder.put(gameStatusEntry.getGame(), gameStatusEntry.getOrder());
        }
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
            gameOrder.clear();
        });
    }

    /** @return the game type with the highest round order (the most recently started game), or null if none. */
    public GameTypeEnum getLatestGame() {
        GameTypeEnum latest = null;
        int maxOrder = -1;
        for (Map.Entry<GameTypeEnum, Integer> entry : gameOrder.entrySet()) {
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
            gameOrder.remove(gameTypeEnum);
        });
    }

    public void addPlayerPoints(UUID uuid, ChampionshipTeam rival, GameTypeEnum gameTypeEnum, String area, double points) {
        if (!isScoringGame(gameTypeEnum)) return;
        ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(uuid);
        if (rival == null) {
            rival = championshipTeam;
        }
        PlayerEntry playerEntry = playerDao.getPlayer(uuid);
        if (playerEntry == null)
            return;
        if (championshipTeam != null) {
            PlayerPointEntry playerPointEntry = PlayerPointEntry.builder()
                    .uuid(playerEntry.getUuid())
                    .username(playerEntry.getName())
                    .teamId(championshipTeam.getId())
                    .team(championshipTeam.getName())
                    .rivalId(rival.getId())
                    .rival(rival.getName())
                    .game(gameTypeEnum)
                    .area(area)
                    .round("scc")
                    .points(points)
                    .time(Utils.getCurrentTimeString())
                    .build();

            if (plugin.isLoaded()) {
                enqueueRankTask(() -> rankDao.addPlayerPoint(playerPointEntry));
            } else {
                rankDao.addPlayerPoint(playerPointEntry);
            }
        }
    }

    /** Queues a full cache refresh after all score writes submitted before this call. */
    public void refreshAfterPendingPointWrites() {
        enqueueRankTask(this::refreshRankingsNow);
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
        updateTeamPoints();
        updatePlayerPoint();
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

    private double getPlayerPoints(UUID uuid) {
        List<PlayerPointEntry> playerPointEntries = rankDao.getPlayerPoints(uuid);

        double points = 0d;
        for (PlayerPointEntry playerPointEntry : playerPointEntries) {
            if (playerPointEntry.getValid() == 1 && isScoringGame(playerPointEntry.getGame())) {
                points = points + playerPointEntry.getPoints();
            }
        }

        return points;
    }

    private synchronized void addTeamTotalPoints(GameTypeEnum gameTypeEnum, double points) {
        double prevPoints = gameTotalPoints.getOrDefault(gameTypeEnum, 0D);
        gameTotalPoints.put(gameTypeEnum, prevPoints + points);
    }

    private double getTeamPoints(ChampionshipTeam championshipTeam) {
        List<PlayerPointEntry> playerPointEntries = rankDao.getTeamPlayerPoints(championshipTeam.getId());

        double points = 0;
        for (GameTypeEnum gameTypeEnum : SCORING_GAMES) {
            int gameOrder = rankDao.getGameStatusOrder(gameTypeEnum);
            for (PlayerPointEntry playerPointEntry : playerPointEntries) {
                if (playerPointEntry.getValid() == 1 && playerPointEntry.getGame() == gameTypeEnum) {
                    if (CCConfig.WEIGHTED_SCORE) {
                        addTeamTotalPoints(playerPointEntry.getGame(), playerPointEntry.getPoints());
                        points += playerPointEntry.getPoints() * getPointMultiple(gameOrder) * getGameWeight(gameTypeEnum);
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
        if (round == 1 || round == 2) {
            return 1d;
        }
        if (round == 3 || round == 4 || round == 5) {
            return 1.5d;
        }
        if (round == 6) {
            return 2d;
        }
        return 0d;
    }

    private double getTeamPoints(ChampionshipTeam championshipTeam, GameTypeEnum gameTypeEnum) {
        if (!isScoringGame(gameTypeEnum)) return 0D;
        List<PlayerPointEntry> playerPointEntries = rankDao.getTeamPlayerPoints(championshipTeam.getId());

        double points = 0;
        for (PlayerPointEntry playerPointEntry : playerPointEntries) {
            if (playerPointEntry.getGame() == gameTypeEnum) {
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
}
