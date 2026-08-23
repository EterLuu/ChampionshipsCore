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

    public record Counts(int playerPoints, int dailyResults, int dailyRecords, int dailyPkwRecords) {
        public Counts(int playerPoints, int dailyResults, int dailyRecords) {
            this(playerPoints, dailyResults, dailyRecords, 0);
        }

        public int total() {
            return playerPoints + dailyResults + dailyRecords + dailyPkwRecords;
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
                SELECT `uuid`,`username`,`game`,?,`mapRevision`,`rulesHash`,`recordType`,`durationMs`,
                       `matchId`,`achievedBy`,`achievedAt`,`recordRank`
                FROM `daily_player_records` WHERE `game`=? AND `map`=?
                ON DUPLICATE KEY UPDATE
                  `username`=IF(VALUES(`durationMs`)<`durationMs`,VALUES(`username`),`username`),
                  `matchId`=IF(VALUES(`durationMs`)<`durationMs`,VALUES(`matchId`),`matchId`),
                  `achievedBy`=IF(VALUES(`durationMs`)<`durationMs`,VALUES(`achievedBy`),`achievedBy`),
                  `achievedAt`=IF(VALUES(`durationMs`)<`durationMs`,VALUES(`achievedAt`),`achievedAt`),
                  `durationMs`=LEAST(`durationMs`,VALUES(`durationMs`))
                """)) {
            merge.setString(1, newRegistration);
            merge.setString(2, game.name());
            merge.setString(3, oldRegistration);
            merge.executeUpdate();
        }
        int records = update(connection,
                "DELETE FROM `daily_player_records` WHERE `game`=? AND `map`=?",
                game.name(), oldRegistration);
        int pkwRecords = 0;
        if (game == GameTypeEnum.ParkourWarrior) {
            try (PreparedStatement mergePkw = connection.prepareStatement("""
                    INSERT INTO `daily_pkw_records`
                    (`uuid`,`username`,`map`,`recordType`,`primaryValue`,`durationMs`,`matchId`,`achievedAt`)
                    SELECT `uuid`,`username`,?,`recordType`,`primaryValue`,`durationMs`,`matchId`,`achievedAt`
                    FROM `daily_pkw_records` WHERE `map`=?
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
                mergePkw.setString(1, newRegistration);
                mergePkw.setString(2, oldRegistration);
                mergePkw.executeUpdate();
            }
            pkwRecords = update(connection,
                    "DELETE FROM `daily_pkw_records` WHERE `map`=?", oldRegistration);
        }
        return new Counts(points, results, records, pkwRecords);
    }

    private static int update(Connection connection, String sql, String... values) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < values.length; index++) statement.setString(index + 1, values[index]);
            return statement.executeUpdate();
        }
    }
}
