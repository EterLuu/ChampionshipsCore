package ink.ziip.championshipscore.api.rank;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseManager;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.rank.dao.RankDaoImpl;
import ink.ziip.championshipscore.api.rank.entry.GameStatusEntry;
import ink.ziip.championshipscore.api.rank.entry.PlayerPointEntry;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.api.team.dao.TeamDaoImpl;
import ink.ziip.championshipscore.api.team.entry.TeamMemberEntry;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.util.Utils;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class RankManager extends BaseManager {
    private static final Map<ChampionshipTeam, Double> teamPoints = new ConcurrentHashMap<>();
    private static final Map<UUID, Double> playerPoints = new ConcurrentHashMap<>();
    private static final Map<ChampionshipTeam, Integer> teamRank = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> playerRank = new ConcurrentHashMap<>();
    private static final Map<GameTypeEnum, Integer> gameOrder = new ConcurrentHashMap<>();
    private final RankDaoImpl rankDao = new RankDaoImpl();
    private final TeamDaoImpl teamDao = new TeamDaoImpl();
    private final BukkitScheduler scheduler = Bukkit.getScheduler();
    @Getter
    private List<Map.Entry<ChampionshipTeam, Double>> teamLeaderboard = new ArrayList<>();
    @Getter
    private List<Map.Entry<UUID, Double>> playerLeaderboard = new ArrayList<>();
    @Getter
    private String teamRankString;
    @Getter
    private String playerRankString;
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

    private void updateTeamPoints() {
        scheduler.runTaskAsynchronously(plugin, () -> {
            for (ChampionshipTeam championshipTeam : plugin.getTeamManager().getTeamList()) {
                teamPoints.put(championshipTeam, plugin.getRankManager().getTeamPoints(championshipTeam));
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
            for (ChampionshipTeam championshipTeam : plugin.getTeamManager().getTeamList()) {
                for (TeamMemberEntry teamMemberEntry : teamDao.getTeamMembers(championshipTeam.getId())) {
                    UUID uuid = teamMemberEntry.getUuid();
                    playerPoints.put(uuid, getPlayerPoints(uuid));
                }
            }
            ArrayList<Map.Entry<UUID, Double>> list;
            list = new ArrayList<>(playerPoints.entrySet());
            list.sort(Map.Entry.comparingByValue());

            Collections.reverse(list);

            StringBuilder stringBuilder = new StringBuilder();

            stringBuilder.append(MessageConfig.RANK_PLAYER_BOARD_BAR).append("\n");

            int i = 1;
            for (Map.Entry<UUID, Double> entry : list) {
                // TODO changed method getting name
                String username = Bukkit.getOfflinePlayer(entry.getKey()).getName();
                if (username != null) {
                    String row = MessageConfig.RANK_PLAYER_BOARD_ROW
                            .replace("%player_rank%", String.valueOf(i))
                            .replace("%player%", username)
                            .replace("%player_point%", String.valueOf(entry.getValue()));

                    stringBuilder.append(row).append("\n");

                    playerRank.put(entry.getKey(), i);
                    i++;
                }
            }

            playerLeaderboard = list;
            playerRankString = stringBuilder.toString();
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
        });
    }

    public void addPlayerPoints(OfflinePlayer offlinePlayer, ChampionshipTeam rival, GameTypeEnum gameTypeEnum, String area, int points) {
        ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(offlinePlayer);
        if (rival == null) {
            rival = championshipTeam;
        }
        if (championshipTeam != null) {
            PlayerPointEntry playerPointEntry = PlayerPointEntry.builder()
                    .uuid(offlinePlayer.getUniqueId())
                    .username(offlinePlayer.getName())
                    .teamId(championshipTeam.getId())
                    .team(championshipTeam.getName())
                    .rivalId(rival.getId())
                    .rival(rival.getName())
                    .game(gameTypeEnum)
                    .area(area)
                    .round("wcc")
                    .points(points)
                    .time(Utils.getCurrentTimeString())
                    .build();

            scheduler.runTaskAsynchronously(plugin, () -> rankDao.addPlayerPoint(playerPointEntry));
        }
    }

    private double getPlayerPoints(UUID uuid) {
        List<PlayerPointEntry> playerPointEntries = rankDao.getPlayerPoints(uuid);

        int points = 0;
        for (PlayerPointEntry playerPointEntry : playerPointEntries) {
            points = points + playerPointEntry.getPoints();
        }

        return points;
    }

    private double getTeamPoints(ChampionshipTeam championshipTeam) {
        List<PlayerPointEntry> playerPointEntries = rankDao.getTeamPlayerPoints(championshipTeam.getId());

        double points = 0;
        for (GameTypeEnum gameTypeEnum : GameTypeEnum.values()) {
            int gameOrder = rankDao.getGameStatusOrder(gameTypeEnum);
            for (PlayerPointEntry playerPointEntry : playerPointEntries) {
                if (playerPointEntry.getGame() == gameTypeEnum) {
                    points += playerPointEntry.getPoints() * getPointMultiple(gameOrder);
                }
            }

        }

        return points;
    }

    public double getPointMultiple(int round) {
        if (round == 1 || round == 2) {
            return 1;
        }
        if (round == 3 || round == 4 || round == 5) {
            return 1.5;
        }
        if (round == 6) {
            return 2;
        }
        return 0;
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
}
