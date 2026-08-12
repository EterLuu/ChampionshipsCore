package ink.ziip.championshipscore.api.rank.dao;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.rank.entry.GameStatusEntry;
import ink.ziip.championshipscore.api.rank.entry.PlayerPointEntry;
import ink.ziip.championshipscore.util.Utils;

import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

public class RankDaoImpl implements RankDao {
    private final ChampionshipsCore plugin = ChampionshipsCore.getInstance();

    @Override
    public List<PlayerPointEntry> getPlayerPoints(UUID uuid) {
        return getPlayerPointsIfAvailable(uuid).orElseGet(Collections::emptyList);
    }

    public Optional<List<PlayerPointEntry>> getPlayerPointsIfAvailable(UUID uuid) {
        try (Connection connection = plugin.getDatabaseManager().getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT `id`, `uuid`, `username`, `teamId`, `team`, `rivalId`, `rival`, `game`, `area`, `round`, `points`, `time`, `valid`
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
                            .rivalId(resultSet.getInt("rivalId"))
                            .rival(resultSet.getString("rival"))
                            .game(GameTypeEnum.valueOf(resultSet.getString("game")))
                            .area(resultSet.getString("area"))
                            .round(resultSet.getString("round"))
                            .points(resultSet.getDouble("points"))
                            .time(resultSet.getString("time"))
                            .valid(resultSet.getInt("valid"))
                            .build();
                    playerPointEntries.add(playerPointEntry);
                }
                return Optional.of(List.copyOf(playerPointEntries));
            }
        } catch (SQLException exception) {
            logFailure("查询玩家积分", exception);
            return Optional.empty();
        }
    }

    @Override
    public List<PlayerPointEntry> getTeamPlayerPoints(int teamId) {
        return getTeamPlayerPointsIfAvailable(teamId).orElseGet(Collections::emptyList);
    }

    public Optional<List<PlayerPointEntry>> getTeamPlayerPointsIfAvailable(int teamId) {
        try (Connection connection = plugin.getDatabaseManager().getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT `id`, `uuid`, `username`, `teamId`, `team`, `rivalId`, `rival`, `game`, `area`, `round`, `points`, `time`, `valid`
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
                            .rivalId(resultSet.getInt("rivalId"))
                            .rival(resultSet.getString("rival"))
                            .game(GameTypeEnum.valueOf(resultSet.getString("game")))
                            .area(resultSet.getString("area"))
                            .round(resultSet.getString("round"))
                            .points(resultSet.getDouble("points"))
                            .time(resultSet.getString("time"))
                            .valid(resultSet.getInt("valid"))
                            .build();
                    playerPointEntries.add(playerPointEntry);
                }
                return Optional.of(List.copyOf(playerPointEntries));
            }
        } catch (SQLException exception) {
            logFailure("查询队伍积分", exception);
            return Optional.empty();
        }
    }

    @Override
    public boolean addPlayerPoint(PlayerPointEntry playerPointEntry) {
        try (Connection connection = plugin.getDatabaseManager().getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO `player_points` (`transactionId`, `uuid`, `username`, `teamId`, `team`, `rivalId`, `rival`, `game`, `area`, `round`, `points`, `time`)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                    ON DUPLICATE KEY UPDATE `transactionId`=VALUES(`transactionId`);
                    """, Statement.RETURN_GENERATED_KEYS)) {
                int teamId = playerPointEntry.getTeamId();
                int rivalID = playerPointEntry.getRivalId();
                String username = playerPointEntry.getUsername();
                String teamName = playerPointEntry.getTeam();
                String rivalName = playerPointEntry.getRival();
                String gameName = playerPointEntry.getGame().name();
                String areaName = playerPointEntry.getArea();
                double points = playerPointEntry.getPoints();
                statement.setString(1, playerPointEntry.getTransactionId().toString());
                statement.setString(2, playerPointEntry.getUuid().toString());
                statement.setString(3, username);
                statement.setInt(4, teamId);
                statement.setString(5, teamName);
                statement.setInt(6, rivalID);
                statement.setString(7, rivalName);
                statement.setString(8, gameName);
                statement.setString(9, areaName);
                statement.setString(10, playerPointEntry.getRound());
                statement.setDouble(11, points);
                statement.setString(12, playerPointEntry.getTime());

                int affectedRows = statement.executeUpdate();
                if (affectedRows > 0) {
                    try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                        if (generatedKeys.next()) {
                            plugin.getLogger().log(Level.INFO, Utils.formatModuleLog("Database", "积分",
                                    "玩家=" + username + " 队伍=" + teamName + " 游戏=" + gameName
                                            + " 场地=" + areaName + " 变更=" + points));
                            generatedKeys.getInt(1);
                        } else {
                        }
                    }
                } else {
                }
                return true;
            }
        } catch (SQLException exception) {
            logFailure("写入玩家积分", exception);
            return false;
        }
    }

    @Override
    public Optional<List<GameStatusEntry>> getGameStatusList() {
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
                return Optional.of(gameStatusEntries);
            }
        } catch (SQLException exception) {
            logFailure("查询游戏状态", exception);
            return Optional.empty();
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
                return -1;
            }
        } catch (SQLException exception) {
            logFailure("查询游戏顺序", exception);
            return -1;
        }
    }

    @Override
    public void addGameStatus(GameStatusEntry gameStatusEntry) {
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
                            generatedKeys.getInt(1);
                        } else {
                        }
                    }
                } else {
                }
            }
        } catch (SQLException exception) {
            logFailure("新增游戏状态", exception);
        }
    }

    @Override
    public void deleteGameStatus(GameTypeEnum gameTypeEnum) {
        try (Connection connection = plugin.getDatabaseManager().getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    DELETE
                    FROM `game_status`
                    WHERE `game`=?
                    """)) {
                statement.setString(1, gameTypeEnum.name());
                statement.executeUpdate();
            }
        } catch (SQLException exception) {
            logFailure("删除游戏状态", exception);
        }
    }

    @Override
    public void deletePlayerPoints(UUID uuid, GameTypeEnum gameTypeEnum) {
        try (Connection connection = plugin.getDatabaseManager().getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE `player_points`
                    SET `valid`=0
                    WHERE `uuid`=? and `game`=?
                    """)) {
                statement.setString(1, uuid.toString());
                statement.setString(2, gameTypeEnum.name());
                statement.executeUpdate();
            }
        } catch (SQLException exception) {
            logFailure("作废玩家积分", exception);
        }
    }

    @Override
    public void deleteTeamPoints(int teamId, GameTypeEnum gameTypeEnum) {
        try (Connection connection = plugin.getDatabaseManager().getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE `player_points`
                    SET `valid`=0
                    WHERE `teamId`=? and `game`=?
                    """)) {
                statement.setInt(1, teamId);
                statement.setString(2, gameTypeEnum.name());
                statement.executeUpdate();
            }
        } catch (SQLException exception) {
            logFailure("作废队伍积分", exception);
        }
    }

    @Override
    public void deleteGamePoints(GameTypeEnum gameTypeEnum) {
        try (Connection connection = plugin.getDatabaseManager().getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE `player_points`
                    SET `valid`=0
                    WHERE `game`=?
                    """)) {
                statement.setString(1, gameTypeEnum.name());
                statement.executeUpdate();
            }
        } catch (SQLException exception) {
            logFailure("作废游戏积分", exception);
        }
    }

    private void logFailure(String operation, SQLException exception) {
        plugin.getLogger().log(Level.SEVERE, Utils.formatModuleLog("Database", "积分",
                "操作=" + operation + " 失败"), exception);
    }
}
