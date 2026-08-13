package ink.ziip.championshipscore.api.rank.dao;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.rank.entry.GameStatusEntry;
import ink.ziip.championshipscore.api.rank.entry.PlayerPointEntry;
import ink.ziip.championshipscore.util.Utils;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

public class RankDaoImpl implements RankDao {
    private final ChampionshipsCore plugin = ChampionshipsCore.getInstance();

    @Override
    public Optional<List<PlayerPointEntry>> getAllValidPlayerPoints() {
        try (Connection connection = plugin.getDatabaseManager().getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT `id`, `uuid`, `username`, `teamId`, `team`, `rivalId`, `rival`, `game`, `area`, `round`, `points`, `time`, `valid`
                     FROM `player_points`
                     WHERE `valid`=1
                     """);
             ResultSet resultSet = statement.executeQuery()) {
            List<PlayerPointEntry> entries = new ArrayList<>();
            while (resultSet.next()) entries.add(readPlayerPoint(resultSet));
            return Optional.of(List.copyOf(entries));
        } catch (SQLException exception) {
            logFailure("查询有效积分快照", exception);
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
        try (Connection connection = plugin.getDatabaseManager().getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement statement = connection.prepareStatement("""
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
                    plugin.getLogger().log(Level.INFO, Utils.formatModuleLog("Database", "积分",
                            "玩家=" + entry.getUsername() + " 队伍=" + entry.getTeam()
                                    + " 游戏=" + entry.getGame().name() + " 场地=" + entry.getArea()
                                    + " 变更=" + entry.getPoints()));
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
            logFailure("批量写入玩家积分", exception);
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
