package ink.ziip.championshipscore.api.rank.dao;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.rank.entry.GameStatusEntry;
import ink.ziip.championshipscore.api.rank.entry.PlayerPointEntry;
import ink.ziip.championshipscore.api.rank.entry.TeamPointEntry;

import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

public class RankDaoImpl implements RankDao {
    private final ChampionshipsCore plugin = ChampionshipsCore.getInstance();

    @Override
    public List<TeamPointEntry> getTeamPoints(int teamId) {
        try (Connection connection = plugin.getDatabaseManager().getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT `id`, `teamId`, `rivalId`, `team`, `rival`, `game`, `area`, `round`, `points`, `time`
                    FROM `team_points`
                    WHERE `teamId`=?
                    """)) {

                statement.setInt(1, teamId);

                List<TeamPointEntry> teamPointEntries = new ArrayList<>();

                final ResultSet resultSet = statement.executeQuery();
                while (resultSet.next()) {
                    TeamPointEntry teamPointEntry = TeamPointEntry.builder()
                            .id(resultSet.getInt("id"))
                            .teamId(resultSet.getInt("teamId"))
                            .rivalId(resultSet.getInt("rivalId"))
                            .team(resultSet.getString("team"))
                            .rival(resultSet.getString("rival"))
                            .game(GameTypeEnum.valueOf(resultSet.getString("game")))
                            .area(resultSet.getString("area"))
                            .round(resultSet.getString("round"))
                            .points(resultSet.getInt("points"))
                            .time(resultSet.getString("time"))
                            .build();
                    teamPointEntries.add(teamPointEntry);
                }

                connection.close();
                return teamPointEntries;
            }
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Database query failed", exception);
            return Collections.emptyList();
        }
    }

    @Override
    public List<PlayerPointEntry> getPlayerPoints(UUID uuid) {
        try (Connection connection = plugin.getDatabaseManager().getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT `id`, `uuid`, `username`, `teamId`, `team`, `game`, `area`, `round`, `points`, `time`
                    FROM `player_points`
                    WHERE `uuid`=?
                    """)) {

                statement.setString(1, uuid.toString());

                List<PlayerPointEntry> playerPointEntries = new ArrayList<>();

                final ResultSet resultSet = statement.executeQuery();
                while (resultSet.next()) {
                    PlayerPointEntry playerPointEntry = PlayerPointEntry.builder()
                            .id(resultSet.getInt("id"))
                            .uuid(UUID.fromString(resultSet.getString("uuid")))
                            .username(resultSet.getString("username"))
                            .teamId(resultSet.getInt("teamId"))
                            .team(resultSet.getString("team"))
                            .game(GameTypeEnum.valueOf(resultSet.getString("game")))
                            .area(resultSet.getString("area"))
                            .round(resultSet.getString("round"))
                            .points(resultSet.getInt("points"))
                            .time(resultSet.getString("time"))
                            .build();
                    playerPointEntries.add(playerPointEntry);
                }

                connection.close();
                return playerPointEntries;
            }
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Database query failed", exception);
            return Collections.emptyList();
        }
    }

    @Override
    public List<PlayerPointEntry> getTeamPlayerPoints(int teamId) {
        try (Connection connection = plugin.getDatabaseManager().getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT `id`, `uuid`, `username`, `teamId`, `team`, `game`, `area`, `round`, `points`, `time`
                    FROM `player_points`
                    WHERE `teamId`=?
                    """)) {

                statement.setInt(1, teamId);

                List<PlayerPointEntry> playerPointEntries = new ArrayList<>();

                final ResultSet resultSet = statement.executeQuery();
                while (resultSet.next()) {
                    PlayerPointEntry playerPointEntry = PlayerPointEntry.builder()
                            .id(resultSet.getInt("id"))
                            .uuid(UUID.fromString(resultSet.getString("uuid")))
                            .username(resultSet.getString("username"))
                            .teamId(resultSet.getInt("teamId"))
                            .team(resultSet.getString("team"))
                            .game(GameTypeEnum.valueOf(resultSet.getString("game")))
                            .area(resultSet.getString("area"))
                            .round(resultSet.getString("round"))
                            .points(resultSet.getInt("points"))
                            .time(resultSet.getString("time"))
                            .build();
                    playerPointEntries.add(playerPointEntry);
                }

                connection.close();
                return playerPointEntries;
            }
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Database query failed", exception);
            return Collections.emptyList();
        }
    }

    @Override
    public int addTeamPoint(TeamPointEntry teamPointEntry) {
        try (Connection connection = plugin.getDatabaseManager().getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO `team_points` (`teamId`, `rivalId`, `team`, `rival`, `game`, `area`, `round`, `points`, `time`)
                    VALUES (?,?,?,?,?,?,?,?,?);
                    """, Statement.RETURN_GENERATED_KEYS)) {
                statement.setInt(1, teamPointEntry.getTeamId());
                statement.setInt(2, teamPointEntry.getRivalId());
                statement.setString(3, teamPointEntry.getTeam());
                statement.setString(4, teamPointEntry.getRival());
                statement.setString(5, teamPointEntry.getGame().name());
                statement.setString(6, teamPointEntry.getArea());
                statement.setString(7, teamPointEntry.getRound());
                statement.setInt(8, teamPointEntry.getPoints());
                statement.setString(9, teamPointEntry.getTime());

                int affectedRows = statement.executeUpdate();
                if (affectedRows > 0) {
                    try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                        if (generatedKeys.next()) {
                            return generatedKeys.getInt(1);
                        } else {
                            connection.close();
                            return -1;
                        }
                    }
                } else {
                    connection.close();
                    return -1;
                }
            }
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Database query failed", exception);
            return -1;
        }
    }

    @Override
    public int addPlayerPoint(PlayerPointEntry playerPointEntry) {
        try (Connection connection = plugin.getDatabaseManager().getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO `player_points` (`uuid`, `username`, `teamId`, `team`, `game`, `area`, `round`, `points`, `time`)
                    VALUES (?,?,?,?,?,?,?,?,?);
                    """, Statement.RETURN_GENERATED_KEYS)) {
                statement.setString(1, playerPointEntry.getUuid().toString());
                statement.setString(2, playerPointEntry.getUsername());
                statement.setInt(3, playerPointEntry.getTeamId());
                statement.setString(4, playerPointEntry.getTeam());
                statement.setString(5, playerPointEntry.getGame().name());
                statement.setString(6, playerPointEntry.getArea());
                statement.setString(7, playerPointEntry.getRound());
                statement.setInt(8, playerPointEntry.getPoints());
                statement.setString(9, playerPointEntry.getTime());

                int affectedRows = statement.executeUpdate();
                if (affectedRows > 0) {
                    try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                        if (generatedKeys.next()) {
                            return generatedKeys.getInt(1);
                        } else {
                            connection.close();
                            return -1;
                        }
                    }
                } else {
                    connection.close();
                    return -1;
                }
            }
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Database query failed", exception);
            return -1;
        }
    }

    @Override
    public List<GameStatusEntry> getGameStatusList() {
        try (Connection connection = plugin.getDatabaseManager().getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT `id`, `time`, `game`, `order`
                    FROM `game_status`
                    """)) {

                List<GameStatusEntry> gameStatusEntries = new ArrayList<>();

                final ResultSet resultSet = statement.executeQuery();
                while (resultSet.next()) {
                    GameStatusEntry gameStatusEntry = GameStatusEntry.builder()
                            .id(resultSet.getInt("id"))
                            .time(resultSet.getString("time"))
                            .game(GameTypeEnum.valueOf(resultSet.getString("game")))
                            .order(resultSet.getInt("order"))
                            .build();
                    gameStatusEntries.add(gameStatusEntry);
                }

                connection.close();
                return gameStatusEntries;
            }
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Database query failed", exception);
            return Collections.emptyList();
        }
    }

    @Override
    public int getGameStatusOrder(GameTypeEnum gameTypeEnum) {
        try (Connection connection = plugin.getDatabaseManager().getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT `id`, `time`, `game`, `order`
                    FROM `game_status`
                    WHERE game=?
                    """)) {
                statement.setString(1, gameTypeEnum.name());

                final ResultSet resultSet = statement.executeQuery();
                if (resultSet.next()) {
                    return resultSet.getInt("order");
                }

                connection.close();
                return -1;
            }
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Database query failed", exception);
            return -1;
        }
    }

    @Override
    public int addGameStatus(GameStatusEntry gameStatusEntry) {
        try (Connection connection = plugin.getDatabaseManager().getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO `game_status` (`time`, `game`, `order`)
                    VALUES (?,?,?);
                    """, Statement.RETURN_GENERATED_KEYS)) {
                statement.setString(1, gameStatusEntry.getTime());
                statement.setString(2, gameStatusEntry.getGame().name());
                statement.setInt(3, gameStatusEntry.getOrder());

                int affectedRows = statement.executeUpdate();
                if (affectedRows > 0) {
                    try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                        if (generatedKeys.next()) {
                            return generatedKeys.getInt(1);
                        } else {
                            connection.close();
                            return -1;
                        }
                    }
                } else {
                    connection.close();
                    return -1;
                }
            }
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Database query failed", exception);
            return -1;
        }
    }

    @Override
    public void deleteGameStatus(int id) {
        try (Connection connection = plugin.getDatabaseManager().getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    DELETE
                    FROM `game_status`
                    WHERE `id`=?
                    """)) {
                statement.setInt(1, id);

                statement.executeUpdate();
                connection.close();
            }
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Database query failed", exception);
        }
    }
}