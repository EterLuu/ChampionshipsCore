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

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class RankManager extends BaseManager {
    private static final Map<ChampionshipTeam, Double> teamPoints = new ConcurrentHashMap<>();
    private static final Map<UUID, Double> playerPoints = new ConcurrentHashMap<>();
    private static final Map<ChampionshipTeam, Integer> teamRank = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> playerRank = new ConcurrentHashMap<>();
    private final RankDaoImpl rankDao = new RankDaoImpl();
    private final TeamDaoImpl teamDao = new TeamDaoImpl();
    private final BukkitScheduler scheduler = Bukkit.getScheduler();
    @Getter
    private String teamRankString;
    @Getter
    private String playerRankString;
    private int updateTaskId;

    public RankManager(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
    }

    @Override
    public void load() {
        updateTaskId = scheduler.scheduleSyncRepeatingTask(plugin, () -> {
            updatePlayerPoint();
            updateTeamPoints();
        }, 0, 100L);
    }

    @Override
    public void unload() {
        scheduler.cancelTask(updateTaskId);
    }

    public int getPlayerRank(Player player) {
        return playerRank.getOrDefault(player.getUniqueId(), Integer.MAX_VALUE);
    }

    public double getPlayerPoints(Player player) {
        return playerPoints.getOrDefault(player.getUniqueId(), -1D);
    }

    public int getPlayerTeamRank(Player player) {
        ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(player);
        if (championshipTeam != null) {
            return teamRank.getOrDefault(championshipTeam, Integer.MAX_VALUE);
        }
        return -1;
    }

    public double getPlayerTeamPoints(Player player) {
        ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(player);
        if (championshipTeam != null) {
            return teamPoints.getOrDefault(championshipTeam, -1D);
        }
        return -1D;
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

            playerRankString = stringBuilder.toString();
        });
    }

    public void addGameOrder(GameTypeEnum gameTypeEnum, int order) {
        GameStatusEntry gameStatusEntry = GameStatusEntry.builder()
                .game(gameTypeEnum)
                .order(order)
                .time(Utils.getCurrentTimeString())
                .build();

        rankDao.addGameStatus(gameStatusEntry);
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

            rankDao.addPlayerPoint(playerPointEntry);
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
            if (gameOrder == 1 || gameOrder == 2) {
                for (PlayerPointEntry playerPointEntry : playerPointEntries) {
                    if (playerPointEntry.getGame() == gameTypeEnum) {
                        points += playerPointEntry.getPoints();
                    }
                }
            }
            if (gameOrder == 3 || gameOrder == 4 || gameOrder == 5) {
                for (PlayerPointEntry playerPointEntry : playerPointEntries) {
                    if (playerPointEntry.getGame() == gameTypeEnum) {
                        points += playerPointEntry.getPoints() * 1.5;
                    }
                }
            }
            if (gameOrder == 6) {
                for (PlayerPointEntry playerPointEntry : playerPointEntries) {
                    if (playerPointEntry.getGame() == gameTypeEnum) {
                        points += playerPointEntry.getPoints() * 2;
                    }
                }
            }
        }

        return points;
    }
}
