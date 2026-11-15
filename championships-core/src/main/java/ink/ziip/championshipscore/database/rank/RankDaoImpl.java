package ink.ziip.championshipscore.database.rank;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.model.GameTypeEnum;
import ink.ziip.championshipscore.api.rank.entry.GameStatusEntry;
import ink.ziip.championshipscore.api.rank.entry.PlayerPointEntry;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.logging.LogText;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RankDaoImpl implements RankDao {
    @FunctionalInterface
    interface Connections {
        Connection open() throws SQLException;
    }

    private final Connections connections;
    private final Logger logger;

    public RankDaoImpl(ChampionshipsCore plugin) {
        this(() -> plugin.getDatabaseManager().getConnection(), plugin.getLogger());
    }

    RankDaoImpl(Connections connections, Logger logger) {
        this.connections = Objects.requireNonNull(connections);
        this.logger = Objects.requireNonNull(logger);
    }

    @Override
    public Optional<List<PlayerPointEntry>> getAllValidPlayerPoints() {
        try (Connection connection = connections.open();
                PreparedStatement statement =
                        connection.prepareStatement(
                                """
                                SELECT `id`, `uuid`, `username`, `teamId`, `team`, `rivalId`, `rival`, `game`, `area`, `round`, `points`, `time`, `valid`
                                FROM `player_points`
                                WHERE `valid`=1
                                """);
                ResultSet resultSet = statement.executeQuery()) {
            List<PlayerPointEntry> entries = new ArrayList<>();
            while (resultSet.next()) entries.add(readPlayerPoint(resultSet));
            return Optional.of(List.copyOf(entries));
        } catch (SQLException exception) {
            logFailure(
                    MessageConfig.codeText("rank-dao-impl.get-all-valid-player-points-1"),
                    exception);
            return Optional.empty();
        }
    }

    private static PlayerPointEntry readPlayerPoint(ResultSet resultSet) throws SQLException {
        return PlayerPointEntry.builder()
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
    }

    @Override
    public boolean addPlayerPoint(PlayerPointEntry playerPointEntry) {
        return addPlayerPoints(List.of(playerPointEntry));
    }

    @Override
    public boolean addPlayerPoints(List<PlayerPointEntry> playerPointEntries) {
        if (playerPointEntries.isEmpty()) return true;
        try (Connection connection = connections.open()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement statement =
                        connection.prepareStatement(
                                """
                                INSERT INTO `player_points` (`transactionId`, `uuid`, `username`, `teamId`, `team`, `rivalId`, `rival`, `game`, `area`, `round`, `points`, `time`)
                                VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                                ON DUPLICATE KEY UPDATE `transactionId`=VALUES(`transactionId`);
                                """)) {
                    for (PlayerPointEntry entry : playerPointEntries) {
                        statement.setString(1, entry.getTransactionId().toString());
                        statement.setString(2, entry.getUuid().toString());
                        statement.setString(3, entry.getUsername());
                        statement.setInt(4, entry.getTeamId());
                        statement.setString(5, entry.getTeam());
                        statement.setInt(6, entry.getRivalId());
                        statement.setString(7, entry.getRival());
                        statement.setString(8, entry.getGame().name());
                        statement.setString(9, entry.getArea());
                        statement.setString(10, entry.getRound());
                        statement.setDouble(11, entry.getPoints());
                        statement.setString(12, entry.getTime());
                        statement.addBatch();
                    }
                    statement.executeBatch();
                }
                connection.commit();
                for (PlayerPointEntry entry : playerPointEntries) {
                    logger.log(
                            Level.INFO,
                            LogText.formatModuleLog(
                                    "Database",
                                    MessageConfig.codeText("rank-dao-impl.add-player-points-2"),
                                    MessageConfig.codeText(
                                            "rank-dao-impl.add-player-points-3",
                                            entry.getUsername(),
                                            entry.getTeam(),
                                            entry.getGame().name(),
                                            entry.getArea(),
                                            entry.getPoints())));
                }
            } catch (SQLException failure) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
                throw failure;
            }
            return true;
        } catch (SQLException exception) {
            logFailure(MessageConfig.codeText("rank-dao-impl.add-player-points-1"), exception);
            return false;
        }
    }

    @Override
    public Optional<List<GameStatusEntry>> getGameStatusList() {
        try (Connection connection = connections.open()) {
            try (PreparedStatement statement =
                    connection.prepareStatement(
                            """
                            SELECT `id`, `time`, `game`, `order`
                            FROM `game_status`
                            """)) {

                List<GameStatusEntry> gameStatusEntries = new ArrayList<>();

                final ResultSet resultSet = statement.executeQuery();
                while (resultSet.next()) {
                    GameStatusEntry gameStatusEntry =
                            GameStatusEntry.builder()
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
            logFailure(MessageConfig.codeText("rank-dao-impl.get-game-status-list-1"), exception);
            return Optional.empty();
        }
    }

    @Override
    public OptionalInt getGameStatusOrder(GameTypeEnum game) {
        try (Connection connection = connections.open();
                PreparedStatement statement =
                        connection.prepareStatement(
                                "SELECT `order` FROM `game_status` WHERE `game`=?")) {
            statement.setString(1, game.name());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? OptionalInt.of(result.getInt("order")) : OptionalInt.empty();
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("Unable to query game order", failure);
        }
    }

    @Override
    public void addGameStatus(GameStatusEntry entry) {
        try (Connection connection = connections.open();
                PreparedStatement statement =
                        connection.prepareStatement(
                                "INSERT INTO `game_status` (`time`, `game`, `order`) VALUES"
                                        + " (?,?,?)")) {
            statement.setString(1, entry.getTime());
            statement.setString(2, entry.getGame().name());
            statement.setInt(3, entry.getOrder());
            if (statement.executeUpdate() != 1)
                throw new SQLException("Game order was not inserted");
        } catch (SQLException failure) {
            throw new IllegalStateException("Unable to insert game order", failure);
        }
    }

    @Override
    public void resetGameStatus() {
        GameTypeEnum[] games = GameTypeEnum.values();
        String placeholders = String.join(",", java.util.Collections.nCopies(games.length, "?"));
        try (Connection connection = connections.open();
                PreparedStatement statement =
                        connection.prepareStatement(
                                "DELETE FROM `game_status` WHERE `game` IN ("
                                        + placeholders
                                        + ")")) {
            for (int i = 0; i < games.length; i++) statement.setString(i + 1, games[i].name());
            statement.executeUpdate();
        } catch (SQLException failure) {
            throw new IllegalStateException("Unable to reset game order", failure);
        }
    }

    @Override
    public void deleteGameRecords(GameTypeEnum game) {
        try (Connection connection = connections.open()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement status =
                                connection.prepareStatement(
                                        "DELETE FROM `game_status` WHERE `game`=?");
                        PreparedStatement points =
                                connection.prepareStatement(
                                        "UPDATE `player_points` SET `valid`=0 WHERE `game`=?")) {
                    status.setString(1, game.name());
                    points.setString(1, game.name());
                    status.executeUpdate();
                    points.executeUpdate();
                }
                connection.commit();
            } catch (SQLException failure) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
                throw failure;
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("Unable to delete game records", failure);
        }
    }

    @Override
    public void deletePlayerPoints(UUID uuid, GameTypeEnum gameTypeEnum) {
        try (Connection connection = connections.open()) {
            try (PreparedStatement statement =
                    connection.prepareStatement(
                            """
                            UPDATE `player_points`
                            SET `valid`=0
                            WHERE `uuid`=? and `game`=?
                            """)) {
                statement.setString(1, uuid.toString());
                statement.setString(2, gameTypeEnum.name());
                statement.executeUpdate();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to delete player points", exception);
        }
    }

    @Override
    public void deleteTeamPoints(int teamId, GameTypeEnum gameTypeEnum) {
        try (Connection connection = connections.open()) {
            try (PreparedStatement statement =
                    connection.prepareStatement(
                            """
                            UPDATE `player_points`
                            SET `valid`=0
                            WHERE `teamId`=? and `game`=?
                            """)) {
                statement.setInt(1, teamId);
                statement.setString(2, gameTypeEnum.name());
                statement.executeUpdate();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to delete team points", exception);
        }
    }

    private void logFailure(String operation, SQLException exception) {
        logger.log(
                Level.SEVERE,
                LogText.formatModuleLog(
                        "Database",
                        MessageConfig.codeText("rank-dao-impl.add-player-points-2"),
                        MessageConfig.codeText("rank-dao-impl.log-failure-1", operation)),
                exception);
    }
}
