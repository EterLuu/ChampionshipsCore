package ink.ziip.championshipscore.api.daily.dao;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.daily.DailyRecordType;
import ink.ziip.championshipscore.api.daily.entry.DailyMatchResultEntry;
import ink.ziip.championshipscore.api.daily.entry.DailyRecordEntry;
import ink.ziip.championshipscore.api.daily.entry.DailyStatEntry;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.util.Utils;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/** JDBC implementation following the same DAO boundary as player, team and rank persistence. */
public final class DailyStatsDaoImpl implements DailyStatsDao {
    private final ChampionshipsCore plugin = ChampionshipsCore.getInstance();

    @Override
    public @NotNull List<DailyStatEntry> getPlayerStats() {
        try (Connection connection = plugin.getDatabaseManager().getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT `uuid`, `username`, `game`, `gamesPlayed`, `wins`, `lineCount`,
                            `completedTasks`, `maxCompletedTasks`, `updatedAt`
                     FROM `daily_player_stats`
                     """);
             ResultSet result = statement.executeQuery()) {
            List<DailyStatEntry> entries = new ArrayList<>();
            while (result.next()) {
                try {
                    entries.add(new DailyStatEntry(
                            UUID.fromString(result.getString("uuid")), result.getString("username"),
                            GameTypeEnum.valueOf(result.getString("game")), result.getLong("gamesPlayed"),
                            result.getLong("wins"), result.getLong("lineCount"),
                            result.getLong("completedTasks"), result.getLong("maxCompletedTasks"),
                            result.getLong("updatedAt")));
                } catch (IllegalArgumentException exception) {
                    logFailure("解析日常统计记录", exception);
                }
            }
            return entries;
        } catch (SQLException exception) {
            logFailure("查询日常统计", exception);
            return Collections.emptyList();
        }
    }

    @Override
    public @NotNull List<DailyRecordEntry> getPlayerRecords() {
        try (Connection connection = plugin.getDatabaseManager().getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT `uuid`, `username`, `game`, `map`, `mapRevision`, `rulesHash`,
                            `recordType`, `durationMs`, `matchId`, `achievedBy`, `achievedAt`
                     FROM `daily_player_records`
                     """);
             ResultSet result = statement.executeQuery()) {
            List<DailyRecordEntry> entries = new ArrayList<>();
            while (result.next()) {
                try {
                    String achievedBy = result.getString("achievedBy");
                    entries.add(new DailyRecordEntry(
                            UUID.fromString(result.getString("uuid")), result.getString("username"),
                            GameTypeEnum.valueOf(result.getString("game")), result.getString("map"),
                            result.getString("mapRevision"), result.getString("rulesHash"),
                            DailyRecordType.valueOf(result.getString("recordType")), result.getLong("durationMs"),
                            UUID.fromString(result.getString("matchId")),
                            achievedBy == null ? null : UUID.fromString(achievedBy),
                            result.getLong("achievedAt")));
                } catch (IllegalArgumentException exception) {
                    logFailure("解析日常纪录", exception);
                }
            }
            return entries;
        } catch (SQLException exception) {
            logFailure("查询日常纪录", exception);
            return Collections.emptyList();
        }
    }

    @Override
    public boolean saveMatch(@NotNull List<DailyMatchResultEntry> results) {
        if (results.isEmpty()) return true;
        try (Connection connection = plugin.getDatabaseManager().getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement result = connection.prepareStatement("""
                         INSERT IGNORE INTO `daily_match_results`
                         (`matchId`,`uuid`,`username`,`game`,`map`,`teamKey`,`points`,`won`,
                          `lineCount`,`completedTasks`,`finishedAt`)
                         VALUES (?,?,?,?,?,?,?,?,?,?,?)
                         """);
                 PreparedStatement aggregate = connection.prepareStatement("""
                         INSERT INTO `daily_player_stats`
                         (`uuid`,`username`,`game`,`gamesPlayed`,`wins`,`lineCount`,
                          `completedTasks`,`maxCompletedTasks`,`updatedAt`)
                         VALUES (?,?,?,1,?,?,?,?,?)
                         ON DUPLICATE KEY UPDATE `username`=VALUES(`username`),
                         `gamesPlayed`=`gamesPlayed`+1, `wins`=`wins`+VALUES(`wins`),
                         `lineCount`=`lineCount`+VALUES(`lineCount`),
                         `completedTasks`=`completedTasks`+VALUES(`completedTasks`),
                         `maxCompletedTasks`=GREATEST(`maxCompletedTasks`,VALUES(`maxCompletedTasks`)),
                         `updatedAt`=VALUES(`updatedAt`)
                         """)) {
                for (DailyMatchResultEntry entry : results) {
                    bindResult(result, entry);
                    if (result.executeUpdate() == 0) continue;
                    aggregate.setString(1, entry.uuid().toString());
                    aggregate.setString(2, entry.username());
                    aggregate.setString(3, entry.game().name());
                    aggregate.setLong(4, entry.won() ? 1L : 0L);
                    aggregate.setLong(5, entry.lineCount());
                    aggregate.setLong(6, entry.completedTasks());
                    aggregate.setLong(7, entry.completedTasks());
                    aggregate.setLong(8, entry.finishedAt());
                    aggregate.executeUpdate();
                }
                connection.commit();
                return true;
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException exception) {
            logFailure("保存日常比赛结果", exception);
            return false;
        }
    }

    @Override
    public boolean saveRecords(@NotNull List<DailyRecordEntry> records) {
        if (records.isEmpty()) return true;
        try (Connection connection = plugin.getDatabaseManager().getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO `daily_player_records`
                     (`uuid`,`username`,`game`,`map`,`mapRevision`,`rulesHash`,`recordType`,`durationMs`,
                      `matchId`,`achievedBy`,`achievedAt`)
                     VALUES (?,?,?,?,?,?,?,?,?,?,?)
                     ON DUPLICATE KEY UPDATE `username`=VALUES(`username`),
                     `matchId`=IF(VALUES(`durationMs`)<`durationMs`,VALUES(`matchId`),`matchId`),
                     `achievedBy`=IF(VALUES(`durationMs`)<`durationMs`,VALUES(`achievedBy`),`achievedBy`),
                     `achievedAt`=IF(VALUES(`durationMs`)<`durationMs`,VALUES(`achievedAt`),`achievedAt`),
                     `durationMs`=LEAST(`durationMs`,VALUES(`durationMs`))
                     """)) {
            for (DailyRecordEntry entry : records) {
                statement.setString(1, entry.uuid().toString());
                statement.setString(2, entry.username());
                statement.setString(3, entry.game().name());
                statement.setString(4, entry.map());
                statement.setString(5, entry.mapRevision());
                statement.setString(6, entry.rulesHash());
                statement.setString(7, entry.recordType().name());
                statement.setLong(8, entry.durationMs());
                statement.setString(9, entry.matchId().toString());
                statement.setString(10, entry.achievedBy() == null ? null : entry.achievedBy().toString());
                statement.setLong(11, entry.achievedAt());
                statement.addBatch();
            }
            statement.executeBatch();
            return true;
        } catch (SQLException exception) {
            logFailure("保存日常纪录", exception);
            return false;
        }
    }

    private void bindResult(PreparedStatement statement, DailyMatchResultEntry entry) throws SQLException {
        statement.setString(1, entry.matchId().toString());
        statement.setString(2, entry.uuid().toString());
        statement.setString(3, entry.username());
        statement.setString(4, entry.game().name());
        statement.setString(5, entry.map());
        statement.setString(6, entry.teamKey());
        statement.setDouble(7, entry.points());
        statement.setBoolean(8, entry.won());
        statement.setLong(9, entry.lineCount());
        statement.setLong(10, entry.completedTasks());
        statement.setLong(11, entry.finishedAt());
    }

    private void logFailure(String operation, Throwable exception) {
        plugin.getLogger().log(Level.SEVERE, Utils.formatModuleLog("Database", "日常",
                "操作=" + operation + " 失败"), exception);
    }
}
