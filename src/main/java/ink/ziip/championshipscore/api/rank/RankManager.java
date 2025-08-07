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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class RankManager extends BaseManager {
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
    @Getter
    private List<Map.Entry<ChampionshipTeam, Double>> teamLeaderboard = new ArrayList<>();
    @Getter
    private List<Map.Entry<UUID, Double>> playerLeaderboard = new ArrayList<>();
    @Getter
    private String teamRankString;
    @Getter
    private String playerRankString = "";
    private BukkitTask updateTask;

    public RankManager(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
    }

    @Override
    public void load() {
        updateTask = scheduler.runTaskTimerAsynchronously(plugin, () -> {
            updatePlayerPoint();
            updateTeamPoints();
            updateGameOrder();

            plugin.getGameApiClient().sendGlobalScore(teamPoints, playerPoints);
        }, 0, 100L);
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
        return gameOrder.keySet().size();
    }

    public double getPlayerTeamPoints(Player player) {
        ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(player);
        if (championshipTeam != null) {
            return teamPoints.getOrDefault(championshipTeam, -1D);
        }
        return 0D;
    }

    public void deleteTeamGamePoints(@NotNull ChampionshipTeam championshipTeam, GameTypeEnum gameType) {
        rankDao.deleteTeamPoints(championshipTeam.getId(), gameType);
    }

    private void updateTeamPoints() {
        scheduler.runTaskAsynchronously(plugin, () -> {
            for (ChampionshipTeam championshipTeam : plugin.getTeamManager().getTeamList()) {
                teamPoints.put(championshipTeam, plugin.getRankManager().getTeamPoints(championshipTeam));
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
                        .replace("%team_point%", String.valueOf(entry.getValue()));

                stringBuilder.append(row).append("\n");

                teamRank.put(entry.getKey(), i);
                i++;
            }

            teamLeaderboard = list;
            teamRankString = stringBuilder.toString();
        });
    }

    private void updatePlayerPoint() {
        scheduler.runTaskAsynchronously(plugin, () -> {
            for (GameTypeEnum gameTypeEnum : GameTypeEnum.values()) {
                gameTotalPoints.put(gameTypeEnum, 0D);
            }

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
                String username = plugin.getPlayerManager().getPlayerName(entry.getKey());
                if (username != null) {
                    String row = MessageConfig.RANK_PLAYER_BOARD_ROW
                            .replace("%player_rank%", String.valueOf(i))
                            .replace("%player%", username)
                            .replace("%player_point%", String.valueOf(entry.getValue()));

                    stringBuilder.append(row).append("\n");

                    playerRank.put(entry.getKey(), i);
                    i++;
                }
                if (i == 11)
                    playerRankString = stringBuilder.toString();
            }

            playerLeaderboard = list;

            for (GameTypeEnum gameTypeEnum : GameTypeEnum.values()) {
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
        });
    }

    private void updateGameOrder() {
        scheduler.runTaskAsynchronously(plugin, () -> {
            for (GameStatusEntry gameStatusEntry : rankDao.getGameStatusList()) {
                gameOrder.put(gameStatusEntry.getGame(), gameStatusEntry.getOrder());
            }
        });
    }

    public void addGameOrder(GameTypeEnum gameTypeEnum, int order) {
        scheduler.runTaskAsynchronously(plugin, () -> {
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
        scheduler.runTaskAsynchronously(plugin, () -> {
            for (GameTypeEnum gameTypeEnum : GameTypeEnum.values()) {
                rankDao.deleteGameStatus(gameTypeEnum);
            }
            gameOrder.clear();
        });
    }

    public void addPlayerPoints(UUID uuid, ChampionshipTeam rival, GameTypeEnum gameTypeEnum, String area, double points) {
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

            scheduler.runTaskAsynchronously(plugin, () -> rankDao.addPlayerPoint(playerPointEntry));
        }
    }

    private double getPlayerPoints(UUID uuid) {
        List<PlayerPointEntry> playerPointEntries = rankDao.getPlayerPoints(uuid);

        double points = 0d;
        for (PlayerPointEntry playerPointEntry : playerPointEntries) {
            if (playerPointEntry.getValid() == 1) {
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
        for (GameTypeEnum gameTypeEnum : GameTypeEnum.values()) {
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
        Map<ChampionshipTeam, Double> teamGamePoints = new ConcurrentHashMap<>();

        for (ChampionshipTeam championshipTeam : plugin.getTeamManager().getTeamList()) {
            teamGamePoints.put(championshipTeam, getTeamPoints(championshipTeam, gameTypeEnum));
        }

        ArrayList<Map.Entry<ChampionshipTeam, Double>> list;
        list = new ArrayList<>(teamGamePoints.entrySet());
        list.sort(Map.Entry.comparingByValue());

        Collections.reverse(list);

        StringBuilder stringBuilder = new StringBuilder();

        stringBuilder.append(MessageConfig.RANK_GAME_TEAM_BOARD_BAR.replace("%game%", gameTypeEnum.toString())).append("\n");

        int i = 1;
        for (Map.Entry<ChampionshipTeam, Double> entry : list) {
            String row = MessageConfig.RANK_TEAM_BOARD_ROW
                    .replace("%team_rank%", String.valueOf(i))
                    .replace("%team%", entry.getKey().getColoredName())
                    .replace("%team_point%", String.valueOf(entry.getValue()));

            stringBuilder.append(row).append("\n");

            teamRank.put(entry.getKey(), i);
            i++;
        }

        return stringBuilder.toString();
    }

    public double getGameWeight(GameTypeEnum gameTypeEnum) {
        BigDecimal weight = gameWeight.getOrDefault(gameTypeEnum, BigDecimal.ONE).setScale(4, RoundingMode.HALF_UP);
        return weight.doubleValue();
    }

    public String getGameWeightInfo() {
        StringBuilder stringBuilder = new StringBuilder();

        stringBuilder.append(MessageConfig.GAME_GAME_WEIGHT).append("\n");

        for (GameTypeEnum gameTypeEnum : GameTypeEnum.values()) {
            String row = MessageConfig.GAME_GAME_WEIGHT_INFO
                    .replace("%game%", gameTypeEnum.toString())
                    .replace("%weight%", String.valueOf(getGameWeight(gameTypeEnum)))
                    .replace("%total_point%", String.valueOf(gameTotalPoints.getOrDefault(gameTypeEnum, 0D)));

            stringBuilder.append(row).append("\n");
        }

        return stringBuilder.toString();
    }
}
