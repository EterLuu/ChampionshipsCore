package ink.ziip.championshipscore.api.game.area.rename;

import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/** Migrates every first-party database column which stores a map/area identity. */
public final class MapRecordRenameMigration {
    private MapRecordRenameMigration() {
    }

    public record Counts(int playerPoints, int dailyResults, int dailyRecords,
                         int dailyMapStats, int dailyPkwRecords) {
        public Counts(int playerPoints, int dailyResults, int dailyRecords, int dailyPkwRecords) {
            this(playerPoints, dailyResults, dailyRecords, 0, dailyPkwRecords);
        }

        public Counts(int playerPoints, int dailyResults, int dailyRecords) {
            this(playerPoints, dailyResults, dailyRecords, 0, 0);
        }

        public int total() {
            return playerPoints + dailyResults + dailyRecords + dailyMapStats + dailyPkwRecords;
        }
    }

    public static @NotNull Counts migrate(@NotNull Connection connection, @NotNull GameTypeEnum game,
                                           @NotNull String oldRegistration, @NotNull String newRegistration,
                                           @NotNull String oldDisplayName, @NotNull String newDisplayName)
            throws SQLException {
        int points = update(connection,
                "UPDATE `player_points` SET `area`=? WHERE `game`=? AND `area`=?",
                newDisplayName, game.name(), oldDisplayName);
        int results = update(connection,
                "UPDATE `daily_match_results` SET `map`=? WHERE `game`=? AND `map`=?",
                newRegistration, game.name(), oldRegistration);

        // A deleted map may previously have used the requested target name. Merge colliding best-record
        // rows under the new identity and retain the faster duration instead of failing or losing the best.
        try (PreparedStatement merge = connection.prepareStatement("""
                INSERT INTO `daily_player_records`
                (`uuid`,`username`,`game`,`map`,`mapRevision`,`rulesHash`,`recordType`,`durationMs`,
                 `matchId`,`achievedBy`,`achievedAt`,`recordRank`)
                SELECT source.`uuid`,source.`username`,source.`game`,?,source.`mapRevision`,source.`rulesHash`,
                       source.`recordType`,source.`durationMs`,source.`matchId`,source.`achievedBy`,
                       source.`achievedAt`,source.`recordRank`
                FROM `daily_player_records` AS source WHERE source.`game`=? AND source.`map`=?
                ON DUPLICATE KEY UPDATE
                  `username`=IF(VALUES(`durationMs`) < `daily_player_records`.`durationMs`,
                      VALUES(`username`),`daily_player_records`.`username`),
                  `matchId`=IF(VALUES(`durationMs`) < `daily_player_records`.`durationMs`,
                      VALUES(`matchId`),`daily_player_records`.`matchId`),
                  `achievedBy`=IF(VALUES(`durationMs`) < `daily_player_records`.`durationMs`,
                      VALUES(`achievedBy`),`daily_player_records`.`achievedBy`),
                  `achievedAt`=IF(VALUES(`durationMs`) < `daily_player_records`.`durationMs`,
                      VALUES(`achievedAt`),`daily_player_records`.`achievedAt`),
                  `durationMs`=LEAST(`daily_player_records`.`durationMs`,VALUES(`durationMs`))
                """)) {
            merge.setString(1, newRegistration);
            merge.setString(2, game.name());
            merge.setString(3, oldRegistration);
            merge.executeUpdate();
        }
        int records = update(connection,
                "DELETE FROM `daily_player_records` WHERE `game`=? AND `map`=?",
                game.name(), oldRegistration);
        try (PreparedStatement mergeStats = connection.prepareStatement("""
                INSERT INTO `daily_map_player_stats`
                (`uuid`,`username`,`game`,`map`,`gamesPlayed`,`wins`,`maxTasks`,`maxLines`,
                 `maxFirstTasks`,`maxDragonDamage`,`firstLiberate`,`firstNextGen`,`firstGateway`,
                 `maxStars`,`finishes`,`updatedAt`)
                SELECT source.`uuid`,source.`username`,source.`game`,?,source.`gamesPlayed`,source.`wins`,
                       source.`maxTasks`,source.`maxLines`,source.`maxFirstTasks`,source.`maxDragonDamage`,
                       source.`firstLiberate`,source.`firstNextGen`,source.`firstGateway`,source.`maxStars`,
                       source.`finishes`,source.`updatedAt`
                FROM `daily_map_player_stats` AS source WHERE source.`game`=? AND source.`map`=?
                ON DUPLICATE KEY UPDATE
                  `username`=IF(VALUES(`updatedAt`) >= `daily_map_player_stats`.`updatedAt`,
                      VALUES(`username`),`daily_map_player_stats`.`username`),
                  `gamesPlayed`=`daily_map_player_stats`.`gamesPlayed`+VALUES(`gamesPlayed`),
                  `wins`=`daily_map_player_stats`.`wins`+VALUES(`wins`),
                  `maxTasks`=GREATEST(`daily_map_player_stats`.`maxTasks`,VALUES(`maxTasks`)),
                  `maxLines`=GREATEST(`daily_map_player_stats`.`maxLines`,VALUES(`maxLines`)),
                  `maxFirstTasks`=GREATEST(`daily_map_player_stats`.`maxFirstTasks`,VALUES(`maxFirstTasks`)),
                  `maxDragonDamage`=GREATEST(`daily_map_player_stats`.`maxDragonDamage`,VALUES(`maxDragonDamage`)),
                  `firstLiberate`=`daily_map_player_stats`.`firstLiberate`+VALUES(`firstLiberate`),
                  `firstNextGen`=`daily_map_player_stats`.`firstNextGen`+VALUES(`firstNextGen`),
                  `firstGateway`=`daily_map_player_stats`.`firstGateway`+VALUES(`firstGateway`),
                  `maxStars`=GREATEST(`daily_map_player_stats`.`maxStars`,VALUES(`maxStars`)),
                  `finishes`=`daily_map_player_stats`.`finishes`+VALUES(`finishes`),
                  `updatedAt`=GREATEST(`daily_map_player_stats`.`updatedAt`,VALUES(`updatedAt`))
                """)) {
            mergeStats.setString(1, newRegistration);
            mergeStats.setString(2, game.name());
            mergeStats.setString(3, oldRegistration);
            mergeStats.executeUpdate();
        }
        int mapStats = update(connection,
                "DELETE FROM `daily_map_player_stats` WHERE `game`=? AND `map`=?",
                game.name(), oldRegistration);
        int pkwRecords = 0;
        if (game == GameTypeEnum.ParkourWarrior) {
            try (PreparedStatement mergePkw = connection.prepareStatement("""
                    INSERT INTO `daily_pkw_records`
                    (`uuid`,`username`,`map`,`recordType`,`primaryValue`,`durationMs`,`matchId`,`achievedAt`)
                    SELECT source.`uuid`,source.`username`,?,source.`recordType`,source.`primaryValue`,
                           source.`durationMs`,source.`matchId`,source.`achievedAt`
                    FROM `daily_pkw_records` AS source WHERE source.`map`=?
                    ON DUPLICATE KEY UPDATE
                      `username`=IF(VALUES(`primaryValue`) > `daily_pkw_records`.`primaryValue`
                          OR (VALUES(`primaryValue`) = `daily_pkw_records`.`primaryValue`
                              AND VALUES(`durationMs`) < `daily_pkw_records`.`durationMs`),
                          VALUES(`username`),`daily_pkw_records`.`username`),
                      `matchId`=IF(VALUES(`primaryValue`) > `daily_pkw_records`.`primaryValue`
                          OR (VALUES(`primaryValue`) = `daily_pkw_records`.`primaryValue`
                              AND VALUES(`durationMs`) < `daily_pkw_records`.`durationMs`),
                          VALUES(`matchId`),`daily_pkw_records`.`matchId`),
                      `achievedAt`=IF(VALUES(`primaryValue`) > `daily_pkw_records`.`primaryValue`
                          OR (VALUES(`primaryValue`) = `daily_pkw_records`.`primaryValue`
                              AND VALUES(`durationMs`) < `daily_pkw_records`.`durationMs`),
                          VALUES(`achievedAt`),`daily_pkw_records`.`achievedAt`),
                      `durationMs`=IF(VALUES(`primaryValue`) > `daily_pkw_records`.`primaryValue`
                          OR (VALUES(`primaryValue`) = `daily_pkw_records`.`primaryValue`
                              AND VALUES(`durationMs`) < `daily_pkw_records`.`durationMs`),
                          VALUES(`durationMs`),`daily_pkw_records`.`durationMs`),
                      `primaryValue`=IF(VALUES(`primaryValue`) > `daily_pkw_records`.`primaryValue`
                          OR (VALUES(`primaryValue`) = `daily_pkw_records`.`primaryValue`
                              AND VALUES(`durationMs`) < `daily_pkw_records`.`durationMs`),
                          VALUES(`primaryValue`),`daily_pkw_records`.`primaryValue`)
                    """)) {
                mergePkw.setString(1, newRegistration);
                mergePkw.setString(2, oldRegistration);
                mergePkw.executeUpdate();
            }
            pkwRecords = update(connection,
                    "DELETE FROM `daily_pkw_records` WHERE `map`=?", oldRegistration);
        }
        return new Counts(points, results, records, mapStats, pkwRecords);
    }

    private static int update(Connection connection, String sql, String... values) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < values.length; index++) statement.setString(index + 1, values[index]);
            return statement.executeUpdate();
        }
    }
}
