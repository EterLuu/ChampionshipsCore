package ink.ziip.championshipscore.api.daily.dao;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.daily.DailyRecordType;
import ink.ziip.championshipscore.api.daily.DailyPkwRecordType;
import ink.ziip.championshipscore.api.daily.entry.DailyMapStatEntry;
import ink.ziip.championshipscore.api.daily.entry.DailyMatchAggregateEntry;
import ink.ziip.championshipscore.api.daily.entry.DailyMatchResultEntry;
import ink.ziip.championshipscore.api.daily.entry.DailyPkwRecordEntry;
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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * JDBC implementation following the same DAO boundary as player, team and rank persistence.
 *
 * <p><b>注意：</b>这里的语句只做数据读写。涉及结构变更（新表 / 新列 / 新索引）时，必须同时在
 * {@link ink.ziip.championshipscore.database.DatabaseMigrationController} 的 MIGRATIONS 中
 * 追加新版本迁移，并同步更新 {@code database/schema.sql}，否则已部署的旧库不会升级。
 */
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
                            `recordType`, `durationMs`, `matchId`, `achievedBy`, `achievedAt`, `recordRank`
                     FROM `daily_player_records`
                     """);
             ResultSet result = statement.executeQuery()) {
            List<DailyRecordEntry> entries = new ArrayList<>();
            while (result.next()) {
                try {
                    entries.add(readRecord(result));
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
    public @NotNull List<DailyMapStatEntry> getPlayerMapStats() {
        try (Connection connection = plugin.getDatabaseManager().getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT `uuid`, `username`, `game`, `map`, `gamesPlayed`, `wins`,
                            `maxTasks`, `maxLines`, `maxFirstTasks`, `maxDragonDamage`,
                            `firstLiberate`, `firstNextGen`, `firstGateway`,
                            `maxStars`, `finishes`, `updatedAt`
                     FROM `daily_map_player_stats`
                     """);
             ResultSet result = statement.executeQuery()) {
            List<DailyMapStatEntry> entries = new ArrayList<>();
            while (result.next()) {
                try {
                    entries.add(new DailyMapStatEntry(
                            UUID.fromString(result.getString("uuid")), result.getString("username"),
                            GameTypeEnum.valueOf(result.getString("game")), result.getString("map"),
                            result.getLong("gamesPlayed"), result.getLong("wins"),
                            result.getLong("maxTasks"), result.getLong("maxLines"),
                            result.getLong("maxFirstTasks"), result.getDouble("maxDragonDamage"),
                            result.getLong("firstLiberate"), result.getLong("firstNextGen"),
                            result.getLong("firstGateway"), result.getLong("maxStars"),
                            result.getLong("finishes"), result.getLong("updatedAt")));
                } catch (IllegalArgumentException exception) {
                    logFailure("解析日常地图统计", exception);
                }
            }
            return entries;
        } catch (SQLException exception) {
            logFailure("查询日常地图统计", exception);
            return Collections.emptyList();
        }
    }

    @Override
    public @NotNull List<DailyPkwRecordEntry> getPlayerPkwRecords() {
        try (Connection connection = plugin.getDatabaseManager().getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT `uuid`, `username`, `map`, `recordType`, `primaryValue`, `durationMs`,
                            `matchId`, `achievedAt`
                     FROM `daily_pkw_records`
                     """);
             ResultSet result = statement.executeQuery()) {
            List<DailyPkwRecordEntry> entries = new ArrayList<>();
            while (result.next()) {
                try {
                    entries.add(new DailyPkwRecordEntry(
                            UUID.fromString(result.getString("uuid")), result.getString("username"),
                            result.getString("map"), DailyPkwRecordType.valueOf(result.getString("recordType")),
                            result.getDouble("primaryValue"), result.getLong("durationMs"),
                            UUID.fromString(result.getString("matchId")), result.getLong("achievedAt")));
                } catch (IllegalArgumentException exception) {
                    logFailure("解析日常跑路战士复合纪录", exception);
                }
            }
            return entries;
        } catch (SQLException exception) {
            logFailure("查询日常跑路战士复合纪录", exception);
            return Collections.emptyList();
        }
    }

    @Override
    public @NotNull List<DailyMatchAggregateEntry> getMatchResultMapAggregates() {
        try (Connection connection = plugin.getDatabaseManager().getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT `uuid`, `game`, `map`, COUNT(*) AS `gamesPlayed`,
                            COALESCE(SUM(`won`), 0) AS `wins`,
                            MAX(`lineCount`) AS `maxLines`, MAX(`completedTasks`) AS `maxCompletedTasks`
                     FROM `daily_match_results`
                     GROUP BY `uuid`, `game`, `map`
                     """);
             ResultSet result = statement.executeQuery()) {
            List<DailyMatchAggregateEntry> entries = new ArrayList<>();
            while (result.next()) {
                try {
                    entries.add(new DailyMatchAggregateEntry(
                            UUID.fromString(result.getString("uuid")),
                            GameTypeEnum.valueOf(result.getString("game")), result.getString("map"),
                            result.getLong("gamesPlayed"), result.getLong("wins"),
                            result.getLong("maxLines"), result.getLong("maxCompletedTasks")));
                } catch (IllegalArgumentException exception) {
                    logFailure("解析日常历史地图聚合", exception);
                }
            }
            return entries;
        } catch (SQLException exception) {
            logFailure("查询日常历史地图聚合", exception);
            return Collections.emptyList();
        }
    }

    @Override
    public boolean saveMatch(@NotNull List<DailyMatchResultEntry> results) {        if (results.isEmpty()) return true;
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
        try (Connection connection = plugin.getDatabaseManager().getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement select = connection.prepareStatement("""
                         SELECT `uuid`, `username`, `game`, `map`, `mapRevision`, `rulesHash`,
                                `recordType`, `durationMs`, `matchId`, `achievedBy`, `achievedAt`, `recordRank`
                         FROM `daily_player_records`
                         WHERE `uuid`=? AND `game`=? AND `map`=? AND `mapRevision`=? AND `rulesHash`=?
                           AND `recordType`=?
                         FOR UPDATE
                         """ );
                 PreparedStatement delete = connection.prepareStatement("""
                         DELETE FROM `daily_player_records`
                         WHERE `uuid`=? AND `game`=? AND `map`=? AND `mapRevision`=? AND `rulesHash`=?
                           AND `recordType`=?
                         """ );
                 PreparedStatement insert = connection.prepareStatement("""
                         INSERT INTO `daily_player_records`
                         (`uuid`,`username`,`game`,`map`,`mapRevision`,`rulesHash`,`recordType`,`durationMs`,
                          `matchId`,`achievedBy`,`achievedAt`,`recordRank`)
                         VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                         """ )) {
                for (DailyRecordEntry candidate : records) {
                    Map<UUID, DailyRecordEntry> byMatch = new LinkedHashMap<>();
                    bindRecordIdentity(select, candidate);
                    try (ResultSet result = select.executeQuery()) {
                        while (result.next()) {
                            DailyRecordEntry existing = readRecord(result);
                            DailyRecordEntry previous = byMatch.get(existing.matchId());
                            if (previous == null || compareRecord(existing, previous) < 0) {
                                byMatch.put(existing.matchId(), existing);
                            }
                        }
                    }
                    DailyRecordEntry previous = byMatch.get(candidate.matchId());
                    if (previous == null || compareRecord(candidate, previous) < 0) {
                        byMatch.put(candidate.matchId(), candidate);
                    }
                    List<DailyRecordEntry> top = byMatch.values().stream()
                            .sorted(DailyStatsDaoImpl::compareRecord)
                            .limit(3)
                            .map(entry -> entry.withRank(0))
                            .toList();

                    bindRecordIdentity(delete, candidate);
                    delete.executeUpdate();
                    for (int index = 0; index < top.size(); index++) {
                        DailyRecordEntry entry = top.get(index).withRank(index + 1);
                        bindRecord(insert, entry);
                        insert.executeUpdate();
                    }
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
            logFailure("保存日常纪录", exception);
            return false;
        }
    }

    private static void bindRecordIdentity(PreparedStatement statement, DailyRecordEntry entry)
            throws SQLException {
        statement.setString(1, entry.uuid().toString());
        statement.setString(2, entry.game().name());
        statement.setString(3, entry.map());
        statement.setString(4, entry.mapRevision());
        statement.setString(5, entry.rulesHash());
        statement.setString(6, entry.recordType().name());
    }

    private static void bindRecord(PreparedStatement statement, DailyRecordEntry entry) throws SQLException {
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
        statement.setInt(12, entry.recordRank());
    }

    private static DailyRecordEntry readRecord(ResultSet result) throws SQLException {
        String achievedBy = result.getString("achievedBy");
        return new DailyRecordEntry(
                UUID.fromString(result.getString("uuid")), result.getString("username"),
                GameTypeEnum.valueOf(result.getString("game")), result.getString("map"),
                result.getString("mapRevision"), result.getString("rulesHash"),
                DailyRecordType.valueOf(result.getString("recordType")), result.getLong("durationMs"),
                UUID.fromString(result.getString("matchId")),
                achievedBy == null ? null : UUID.fromString(achievedBy),
                result.getLong("achievedAt"), result.getInt("recordRank"));
    }

    private static int compareRecord(DailyRecordEntry first, DailyRecordEntry second) {
        return Comparator.comparingLong(DailyRecordEntry::durationMs)
                .thenComparingLong(DailyRecordEntry::achievedAt)
                .thenComparing(entry -> entry.matchId().toString())
                .compare(first, second);
    }

    @Override
    public boolean saveMapStats(@NotNull List<DailyMapStatEntry> stats) {
        if (stats.isEmpty()) return true;
        try (Connection connection = plugin.getDatabaseManager().getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO `daily_map_player_stats`
                     (`uuid`,`username`,`game`,`map`,`gamesPlayed`,`wins`,`maxTasks`,`maxLines`,
                      `maxFirstTasks`,`maxDragonDamage`,`firstLiberate`,`firstNextGen`,`firstGateway`,
                      `maxStars`,`finishes`,`updatedAt`)
                     VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                     ON DUPLICATE KEY UPDATE `username`=VALUES(`username`),
                     `gamesPlayed`=`gamesPlayed`+VALUES(`gamesPlayed`),
                     `wins`=`wins`+VALUES(`wins`),
                     `maxTasks`=GREATEST(`maxTasks`,VALUES(`maxTasks`)),
                     `maxLines`=GREATEST(`maxLines`,VALUES(`maxLines`)),
                     `maxFirstTasks`=GREATEST(`maxFirstTasks`,VALUES(`maxFirstTasks`)),
                     `maxDragonDamage`=GREATEST(`maxDragonDamage`,VALUES(`maxDragonDamage`)),
                     `firstLiberate`=`firstLiberate`+VALUES(`firstLiberate`),
                     `firstNextGen`=`firstNextGen`+VALUES(`firstNextGen`),
                     `firstGateway`=`firstGateway`+VALUES(`firstGateway`),
                     `maxStars`=GREATEST(`maxStars`,VALUES(`maxStars`)),
                     `finishes`=`finishes`+VALUES(`finishes`),
                     `updatedAt`=VALUES(`updatedAt`)
                     """)) {
            for (DailyMapStatEntry entry : stats) {
                statement.setString(1, entry.uuid().toString());
                statement.setString(2, entry.username());
                statement.setString(3, entry.game().name());
                statement.setString(4, entry.map());
                statement.setLong(5, entry.gamesPlayed());
                statement.setLong(6, entry.wins());
                statement.setLong(7, entry.maxTasks());
                statement.setLong(8, entry.maxLines());
                statement.setLong(9, entry.maxFirstTasks());
                statement.setDouble(10, entry.maxDragonDamage());
                statement.setLong(11, entry.firstLiberate());
                statement.setLong(12, entry.firstNextGen());
                statement.setLong(13, entry.firstGateway());
                statement.setLong(14, entry.maxStars());
                statement.setLong(15, entry.finishes());
                statement.setLong(16, entry.updatedAt());
                statement.addBatch();
            }
            statement.executeBatch();
            return true;
        } catch (SQLException exception) {
            logFailure("保存日常地图统计", exception);
            return false;
        }
    }

    @Override
    public boolean savePkwRecords(@NotNull List<DailyPkwRecordEntry> records) {
        if (records.isEmpty()) return true;
        try (Connection connection = plugin.getDatabaseManager().getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO `daily_pkw_records`
                     (`uuid`,`username`,`map`,`recordType`,`primaryValue`,`durationMs`,`matchId`,`achievedAt`)
                     VALUES (?,?,?,?,?,?,?,?)
                     ON DUPLICATE KEY UPDATE
                     `username`=IF(VALUES(`primaryValue`)>`primaryValue`
                         OR (VALUES(`primaryValue`)=`primaryValue` AND VALUES(`durationMs`)<`durationMs`),
                         VALUES(`username`),`username`),
                     `matchId`=IF(VALUES(`primaryValue`)>`primaryValue`
                         OR (VALUES(`primaryValue`)=`primaryValue` AND VALUES(`durationMs`)<`durationMs`),
                         VALUES(`matchId`),`matchId`),
                     `achievedAt`=IF(VALUES(`primaryValue`)>`primaryValue`
                         OR (VALUES(`primaryValue`)=`primaryValue` AND VALUES(`durationMs`)<`durationMs`),
                         VALUES(`achievedAt`),`achievedAt`)
                     ,`durationMs`=IF(VALUES(`primaryValue`)>`primaryValue`
                         OR (VALUES(`primaryValue`)=`primaryValue` AND VALUES(`durationMs`)<`durationMs`),
                         VALUES(`durationMs`),`durationMs`)
                     ,`primaryValue`=IF(VALUES(`primaryValue`)>`primaryValue`
                         OR (VALUES(`primaryValue`)=`primaryValue` AND VALUES(`durationMs`)<`durationMs`),
                         VALUES(`primaryValue`),`primaryValue`)
                     """)) {
            for (DailyPkwRecordEntry entry : records) {
                statement.setString(1, entry.uuid().toString());
                statement.setString(2, entry.username());
                statement.setString(3, entry.map());
                statement.setString(4, entry.recordType().name());
                statement.setDouble(5, entry.primaryValue());
                statement.setLong(6, entry.durationMs());
                statement.setString(7, entry.matchId().toString());
                statement.setLong(8, entry.achievedAt());
                statement.addBatch();
            }
            statement.executeBatch();
            return true;
        } catch (SQLException exception) {
            logFailure("保存日常跑路战士复合纪录", exception);
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
