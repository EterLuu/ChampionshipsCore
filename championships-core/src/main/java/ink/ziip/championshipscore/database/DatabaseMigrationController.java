package ink.ziip.championshipscore.database;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.util.Utils;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Version-controlled schema upgrades. The applied version set lives in {@code cc_schema_migrations};
 * on every boot the controller applies pending migrations in ascending version order, each inside
 * its own transaction together with its version record. Never edit or reorder an existing
 * migration: append a new one with the next version number.
 */
public final class DatabaseMigrationController {
    private static final String VERSION_TABLE = "cc_schema_migrations";

    /** Ordered registry; versions must stay gapless and unique (asserted by unit test). */
    static final List<DatabaseMigration> MIGRATIONS = List.of(
            new DatabaseMigration(1, "baseline-schema", DatabaseMigrationController::applyBaselineSchema),
            new DatabaseMigration(2, "player-points-transaction-id",
                    connection -> {
                        ensureColumn(connection, "player_points", "transactionId", "VARCHAR(36) NULL");
                        ensureUniqueIndex(connection, "player_points", "transactionId",
                                "uq_player_points_transaction_id", false);
                    }),
            new DatabaseMigration(3, "identity-unique-indexes",
                    connection -> {
                        ensureUniqueIndex(connection, "players", "uuid", "uq_players_uuid", true);
                        ensureUniqueIndex(connection, "players", "username", "uq_players_username", true);
                        ensureUniqueIndex(connection, "team_members", "uuid", "uq_team_members_uuid", true);
                        ensureUniqueIndex(connection, "team_members", "username",
                                "uq_team_members_username", true);
                    }),
            new DatabaseMigration(4, "shared-data-unique-indexes",
                    connection -> {
                        ensureUniqueIndex(connection, "teams", "name", "uq_teams_name", true);
                        ensureUniqueIndex(connection, "teams", "colorName", "uq_teams_color_name", true);
                        ensureUniqueIndex(connection, "game_status", "game", "uq_game_status_game", true);
                    }),
            new DatabaseMigration(5, "daily-stats-columns",
                    connection -> {
                        ensureColumn(connection, "daily_player_stats", "lineCount", "BIGINT NOT NULL DEFAULT 0");
                        ensureColumn(connection, "daily_player_stats", "completedTasks",
                                "BIGINT NOT NULL DEFAULT 0");
                        ensureColumn(connection, "daily_player_stats", "maxCompletedTasks",
                                "BIGINT NOT NULL DEFAULT 0");
                        ensureColumn(connection, "daily_match_results", "lineCount", "BIGINT NOT NULL DEFAULT 0");
                        ensureColumn(connection, "daily_match_results", "completedTasks",
                                "BIGINT NOT NULL DEFAULT 0");
                    }),
            new DatabaseMigration(6, "remote-bingo-owner-instance",
                    connection -> ensureColumn(connection, "remote_bingo_matches", "ownerInstance",
                            "VARCHAR(128) NULL")),
            new DatabaseMigration(7, "daily-map-player-stats",
                    connection -> connection.createStatement().execute("""
                            CREATE TABLE IF NOT EXISTS `daily_map_player_stats`
                            (
                                `uuid`            VARCHAR(36)  NOT NULL,
                                `username`        VARCHAR(16)  NOT NULL,
                                `game`            VARCHAR(64)  NOT NULL,
                                `map`             VARCHAR(128) NOT NULL,
                                `gamesPlayed`     BIGINT       NOT NULL DEFAULT 0,
                                `wins`            BIGINT       NOT NULL DEFAULT 0,
                                `maxTasks`        BIGINT       NOT NULL DEFAULT 0,
                                `maxLines`        BIGINT       NOT NULL DEFAULT 0,
                                `maxFirstTasks`   BIGINT       NOT NULL DEFAULT 0,
                                `maxDragonDamage` DOUBLE       NOT NULL DEFAULT 0,
                                `firstLiberate`   BIGINT       NOT NULL DEFAULT 0,
                                `firstNextGen`    BIGINT       NOT NULL DEFAULT 0,
                                `firstGateway`    BIGINT       NOT NULL DEFAULT 0,
                                `updatedAt`       BIGINT       NOT NULL,

                                PRIMARY KEY (`uuid`, `game`, `map`),
                                INDEX `idx_daily_map_stats_board` (`game`, `map`)
                            ) ENGINE = InnoDB
                              DEFAULT CHARSET = utf8mb4
                              COLLATE = utf8mb4_unicode_ci
                            """)),
            new DatabaseMigration(8, "daily-parkour-warrior-columns",
                    connection -> {
                        ensureColumn(connection, "daily_map_player_stats", "maxStars", "BIGINT NOT NULL DEFAULT 0");
                        ensureColumn(connection, "daily_map_player_stats", "finishes", "BIGINT NOT NULL DEFAULT 0");
                    }),
            new DatabaseMigration(9, "daily-parkour-warrior-composite-records",
                    connection -> connection.createStatement().execute("""
                            CREATE TABLE IF NOT EXISTS `daily_pkw_records`
                            (
                                `uuid`         VARCHAR(36)  NOT NULL,
                                `username`     VARCHAR(16)  NOT NULL,
                                `map`          VARCHAR(128) NOT NULL,
                                `recordType`   VARCHAR(32)  NOT NULL,
                                `primaryValue` DOUBLE       NOT NULL,
                                `durationMs`   BIGINT       NOT NULL,
                                `matchId`      VARCHAR(36)  NOT NULL,
                                `achievedAt`   BIGINT       NOT NULL,

                                PRIMARY KEY (`uuid`, `map`, `recordType`),
                                INDEX `idx_daily_pkw_records_board` (`map`, `recordType`, `primaryValue`, `durationMs`)
                            ) ENGINE = InnoDB
                              DEFAULT CHARSET = utf8mb4
                              COLLATE = utf8mb4_unicode_ci
                            """)),
            new DatabaseMigration(10, "daily-remove-legacy-pkw-fastest-finish",
                    connection -> connection.createStatement().executeUpdate("""
                            DELETE FROM `daily_player_records`
                            WHERE `game` = 'ParkourWarrior' AND `recordType` = 'PKW_FASTEST_FINISH'
                            """)),
            new DatabaseMigration(11, "daily-record-top-three",
                    connection -> {
                        ensureColumn(connection, "daily_player_records", "recordRank",
                                "TINYINT NOT NULL DEFAULT 1");
                        ensurePrimaryKeyContains(connection, "daily_player_records", "recordRank",
                                "`uuid`, `game`, `map`, `mapRevision`, `rulesHash`, `recordType`, `recordRank`");
                    }));

    private final ChampionshipsCore plugin;

    public DatabaseMigrationController(@NotNull ChampionshipsCore plugin) {
        this.plugin = plugin;
    }

    /** Applies every pending migration; throws so a failed schema blocks startup, as before. */
    public void migrate(@NotNull Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS `" + VERSION_TABLE + "` (" +
                    "`version` INT NOT NULL, `name` VARCHAR(128) NOT NULL, `appliedAt` BIGINT NOT NULL," +
                    "PRIMARY KEY (`version`)) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4");
        }
        Set<Integer> applied = new HashSet<>();
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT `version` FROM `" + VERSION_TABLE + "`")) {
            while (result.next()) applied.add(result.getInt(1));
        }
        for (DatabaseMigration migration : MIGRATIONS) {
            if (applied.contains(migration.version())) continue;
            apply(connection, migration);
        }
    }

    private void apply(@NotNull Connection connection, @NotNull DatabaseMigration migration) throws SQLException {
        long startedAt = System.currentTimeMillis();
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            migration.body().apply(connection);
            // INSERT IGNORE: a second Core instance booting concurrently may record the same
            // migration first; both may run the idempotent body, but only one version row wins.
            try (Statement statement = connection.createStatement()) {
                statement.execute("INSERT IGNORE INTO `" + VERSION_TABLE + "` VALUES ("
                        + migration.version() + ", '" + migration.name().replace("'", "''") + "', "
                        + System.currentTimeMillis() + ")");
            }
            connection.commit();
            plugin.getLogger().info(Utils.formatModuleLog("Database", "迁移",
                    String.format("版本=%d 名称=%s 完成，耗时=%dms", migration.version(),
                            migration.name(), System.currentTimeMillis() - startedAt)));
        } catch (Exception exception) {
            connection.rollback();
            throw new IllegalStateException("Schema migration v" + migration.version()
                    + " (" + migration.name() + ") failed", exception);
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    /** Fresh installs get the full current schema; existing installs only see the no-op IF NOT EXISTS. */
    private static void applyBaselineSchema(@NotNull Connection connection) throws SQLException {
        ChampionshipsCore plugin = ChampionshipsCore.getInstance();
        try (InputStream schema = plugin.getResource("database/schema.sql")) {
            if (schema == null) throw new IOException("Missing database/schema.sql");
            String raw = new String(schema.readAllBytes(), StandardCharsets.UTF_8);
            try (Statement statement = connection.createStatement()) {
                for (String sqlStatement : raw.split(";")) {
                    String trimmed = sqlStatement.trim();
                    if (!trimmed.isEmpty()) statement.execute(trimmed);
                }
            }
        } catch (IOException exception) {
            throw new SQLException("Cannot read database/schema.sql", exception);
        }
    }

    private static void ensureColumn(@NotNull Connection connection, @NotNull String table,
                                     @NotNull String column, @NotNull String definition) throws SQLException {
        try (ResultSet columns = connection.getMetaData()
                .getColumns(connection.getCatalog(), null, table, column)) {
            if (columns.next()) return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE `" + table + "` ADD COLUMN `" + column + "` " + definition);
        }
    }

    private static void ensureUniqueIndex(@NotNull Connection connection, @NotNull String table,
                                          @NotNull String column, @NotNull String indexName,
                                          boolean skipOnDuplicateValues) throws SQLException {
        if (hasIndex(connection, table, indexName)) return;
        if (skipOnDuplicateValues) {
            int duplicateGroups;
            String duplicateQuery = "SELECT COUNT(*) FROM (SELECT 1 FROM `" + table + "` GROUP BY `"
                    + column + "` HAVING COUNT(*) > 1) duplicate_values";
            try (Statement statement = connection.createStatement();
                 ResultSet resultSet = statement.executeQuery(duplicateQuery)) {
                resultSet.next();
                duplicateGroups = resultSet.getInt(1);
            }
            if (duplicateGroups > 0) {
                // Deliberately not fatal: the migration still records as applied because the
                // conflicting rows need human cleanup and the rest of the schema must proceed.
                ChampionshipsCore.getInstance().getLogger().warning(Utils.formatModuleLog("Database",
                        "IdentityIndex", "暂未创建唯一索引=" + indexName + "，表=" + table
                                + " 字段=" + column + " 存在冲突组数=" + duplicateGroups + "；人工消歧后需手工补建"));
                return;
            }
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE `" + table + "` ADD UNIQUE INDEX `" + indexName
                    + "` (`" + column + "`)");
        }
    }

    private static boolean hasIndex(@NotNull Connection connection, @NotNull String table,
                                    @NotNull String indexName) throws SQLException {
        try (ResultSet indexes = connection.getMetaData()
                .getIndexInfo(connection.getCatalog(), null, table, true, false)) {
            while (indexes.next()) {
                if (indexName.equalsIgnoreCase(indexes.getString("INDEX_NAME"))) return true;
            }
        }
        return false;
    }

    private static void ensurePrimaryKeyContains(@NotNull Connection connection, @NotNull String table,
                                                 @NotNull String requiredColumn,
                                                 @NotNull String primaryKeyColumns) throws SQLException {
        boolean present = false;
        try (ResultSet keys = connection.getMetaData().getPrimaryKeys(connection.getCatalog(), null, table)) {
            while (keys.next()) {
                if (requiredColumn.equalsIgnoreCase(keys.getString("COLUMN_NAME"))) {
                    present = true;
                    break;
                }
            }
        }
        if (present) return;
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE `" + table + "` DROP PRIMARY KEY, ADD PRIMARY KEY ("
                    + primaryKeyColumns + ")");
        }
    }

    /** Introspection hook used by tests and future admin tooling. */
    public static @NotNull List<DatabaseMigration> registry() {
        return new ArrayList<>(MIGRATIONS);
    }
}
