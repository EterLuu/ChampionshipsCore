package ink.ziip.championshipscore.database;

import com.zaxxer.hikari.HikariDataSource;
import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseManager;
import ink.ziip.championshipscore.configuration.config.CCConfig;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;

public class DatabaseManager extends BaseManager {
    private static final String DATA_POOL_NAME = "ChampionshipsCoreHikariPool";
    private String driverClass;
    private HikariDataSource dataSource;
    private volatile boolean shuttingDown;

    public DatabaseManager(@NotNull ChampionshipsCore championshipsCore) {
        super(championshipsCore);
    }

    @Override
    public void load() {
        shuttingDown = false;
        if (CCConfig.DATABASE_TYPE.equals("MARIADB")) {
            this.driverClass = "org.mariadb.jdbc.Driver";
            // The pool holds connections open for their whole lifetime, so the driver's
            // connection-close packet class is never loaded during normal operation.
            // HikariCP closes pooled connections asynchronously on its "connection-closer"
            // thread; at server shutdown that thread outlives the plugin classloader and
            // would throw NoClassDefFoundError the first time it loads QuitPacket. Preload
            // it now while the classloader is alive so the cached class is used at shutdown.
            preloadClass("org.mariadb.jdbc.message.client.QuitPacket");
        } else {
            this.driverClass = "com.mysql.cj.jdbc.Driver";
        }
        initialize();
    }

    private void preloadClass(String className) {
        try {
            Class.forName(className);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void unload() {
        shuttingDown = true;
        if (dataSource != null) {
            if (!dataSource.isClosed()) {
                dataSource.close();
            }
        }
    }

    public void initialize() throws IllegalStateException {
        if (shuttingDown)
            throw new IllegalStateException("Database is shutting down");
        dataSource = new HikariDataSource();

        dataSource.setPoolName(DATA_POOL_NAME);
        dataSource.setDriverClassName(driverClass);

        if (CCConfig.DATABASE_TYPE.equals("MARIADB"))
            dataSource.setJdbcUrl("jdbc:mariadb://" + CCConfig.DATABASE_ADDRESS + ":" + CCConfig.DATABASE_PORT + "/" + CCConfig.DATABASE_NAME + "?autoReconnect=true&useSSL=false&useUnicode=true&characterEncoding=UTF-8");
        else
            dataSource.setJdbcUrl("jdbc:mysql://" + CCConfig.DATABASE_ADDRESS + ":" + CCConfig.DATABASE_PORT + "/" + CCConfig.DATABASE_NAME + "?autoReconnect=true&useSSL=false&useUnicode=true&characterEncoding=UTF-8");
        dataSource.setUsername(CCConfig.DATABASE_USERNAME);
        dataSource.setPassword(CCConfig.DATABASE_PASSWORD);

        dataSource.setMaximumPoolSize(12);
        dataSource.setMinimumIdle(12);

        dataSource.setMaxLifetime(1800000);
        dataSource.setKeepaliveTime(30000);
        dataSource.setConnectionTimeout(20000);

        Properties properties = getProperties();
        dataSource.setDataSourceProperties(properties);

        try (Connection connection = dataSource.getConnection()) {
            try (Statement statement = connection.createStatement()) {
                InputStream schema = plugin.getResource("database/schema.sql");
                if (schema != null) {
                    for (String executeStatement : new String(schema.readAllBytes(), StandardCharsets.UTF_8).split(";")) {
                        statement.execute(executeStatement);
                    }
                }
                ensurePointTransactionSchema(connection);
            } catch (SQLException | IOException e) {
                throw new IllegalStateException("Failed to create database tables.", e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to establish a connection to the MySQL database.", e);
        }
    }

    private void ensurePointTransactionSchema(@NotNull Connection connection) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        boolean hasColumn;
        try (ResultSet columns = metadata.getColumns(connection.getCatalog(), null,
                "player_points", "transactionId")) {
            hasColumn = columns.next();
        }
        if (!hasColumn) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE `player_points` ADD COLUMN `transactionId` VARCHAR(36) NULL AFTER `id`");
            }
        }

        boolean hasUniqueIndex = false;
        try (ResultSet indexes = metadata.getIndexInfo(connection.getCatalog(), null,
                "player_points", true, false)) {
            while (indexes.next()) {
                if ("uq_player_points_transaction_id".equalsIgnoreCase(indexes.getString("INDEX_NAME"))) {
                    hasUniqueIndex = true;
                    break;
                }
            }
        }
        if (!hasUniqueIndex) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE `player_points` ADD UNIQUE INDEX "
                        + "`uq_player_points_transaction_id` (`transactionId`)");
            }
        }
    }

    @NotNull
    private static Properties getProperties() {
        Properties properties = new Properties();

        properties.put("cachePrepStmts", "true");
        properties.put("prepStmtCacheSize", "250");
        properties.put("prepStmtCacheSqlLimit", "2048");
        properties.put("useServerPrepStmts", "true");
        properties.put("useLocalSessionState", "true");
        properties.put("useLocalTransactionState", "true");

        properties.put("rewriteBatchedStatements", "true");
        properties.put("cacheResultSetMetadata", "true");
        properties.put("cacheServerConfiguration", "true");
        properties.put("elideSetAutoCommits", "true");
        properties.put("maintainTimeStats", "false");
        return properties;
    }

    public Connection getConnection() throws SQLException {
        if (shuttingDown)
            throw new SQLException("ChampionshipsCore database is shutting down");
        if (dataSource == null)
            throw new SQLException("ChampionshipsCore database has not been initialized");
        if (!dataSource.isClosed())
            return dataSource.getConnection();

        initialize();
        return dataSource.getConnection();
    }
}
