package ink.ziip.championshipscore.database;

import com.zaxxer.hikari.HikariDataSource;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseManager;
import ink.ziip.championshipscore.configuration.config.CCConfig;
import ink.ziip.championshipscore.util.concurrent.LifecycleTasks;

import org.jetbrains.annotations.NotNull;

import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletionStage;
import java.util.logging.Level;

/**
 * Connection pool plus the single entry point for schema provisioning.
 *
 * <p>Schema changes must append a new version to DatabaseMigrationController.MIGRATIONS and update
 * database/schema.sql. Never modify or remove historical migrations, or execute schema changes
 * directly in this manager or a DAO.
 */
public class DatabaseManager extends BaseManager {
    private static final String DATA_POOL_NAME = "ChampionshipsCoreHikariPool";
    private String driverClass;
    private volatile HikariDataSource dataSource;
    private volatile boolean shuttingDown;
    private volatile boolean identityConstraints;
    private final LifecycleTasks bootstrapTasks = new LifecycleTasks("cc-database-bootstrap", 1);

    private final LifecycleTasks queries = new LifecycleTasks("cc-database-queries", 512);

    public <T> java.util.concurrent.CompletableFuture<T> query(
            java.util.function.Supplier<T> action) {
        return queries.supply(action);
    }

    public java.util.concurrent.CompletableFuture<Void> execute(Runnable action) {
        return queries.submit(action);
    }

    public DatabaseManager(@NotNull ChampionshipsCore championshipsCore) {
        super(championshipsCore);
    }

    @Override
    public void load() {
        loadAsync()
                .whenComplete(
                        (ignored, failure) -> {
                            if (failure != null)
                                plugin.getLogger()
                                        .log(Level.SEVERE, "Database bootstrap failed", failure);
                        });
    }

    private void configureAndInitialize() {
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

    /** Establishes the pool and applies schema migrations without blocking the server thread. */
    public CompletionStage<Void> loadAsync() {
        bootstrapTasks.start();
        queries.start();
        shuttingDown = false;
        return bootstrapTasks.submit(this::configureAndInitialize);
    }

    private void preloadClass(String className) {
        try {
            Class.forName(className);
        } catch (ClassNotFoundException | LinkageError failure) {
            plugin.getLogger()
                    .log(
                            Level.WARNING,
                            "Unable to preload optional database class "
                                    + className
                                    + "; shutdown may report a driver class-loading error.",
                            failure);
        } catch (SecurityException failure) {
            plugin.getLogger()
                    .log(
                            Level.WARNING,
                            "Database class preloading was blocked for " + className + ".",
                            failure);
        }
    }

    @Override
    public void unload() {
        shuttingDown = true;
        queries.close();
        bootstrapTasks.close();
        HikariDataSource current = dataSource;
        dataSource = null;
        if (current != null) current.close();
    }

    private void initialize() throws IllegalStateException {
        var token = bootstrapTasks.token();
        if (shuttingDown) throw new IllegalStateException("Database is shutting down");
        HikariDataSource candidate = new HikariDataSource();
        try {
            candidate.setPoolName(DATA_POOL_NAME);
            candidate.setDriverClassName(driverClass);

            boolean maria = CCConfig.DATABASE_TYPE.equals("MARIADB");
            candidate.setJdbcUrl(
                    "jdbc:"
                            + (maria ? "mariadb" : "mysql")
                            + "://"
                            + CCConfig.DATABASE_ADDRESS
                            + ":"
                            + CCConfig.DATABASE_PORT
                            + "/"
                            + CCConfig.DATABASE_NAME);
            candidate.setUsername(CCConfig.DATABASE_USERNAME);
            candidate.setPassword(CCConfig.DATABASE_PASSWORD);

            candidate.setMaximumPoolSize(CCConfig.DATABASE_POOL_SIZE);
            candidate.setMaxLifetime(1800000);
            candidate.setKeepaliveTime(30000);
            candidate.setConnectionTimeout(20000);
            candidate.setDataSourceProperties(getProperties(maria));

            boolean indexedIdentities;
            try (Connection connection = candidate.getConnection()) {
                // Schema upgrades are version-controlled; see DatabaseMigrationController.
                new DatabaseMigrationController(plugin).migrate(connection);
                indexedIdentities = DatabaseMigrationController.hasIdentityConstraints(connection);
            }
            token.publish(
                    () -> {
                        HikariDataSource previous = dataSource;
                        identityConstraints = indexedIdentities;
                        dataSource = candidate;
                        if (previous != null) previous.close();
                    });
        } catch (SQLException | RuntimeException failure) {
            try {
                candidate.close();
            } catch (RuntimeException closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            if (failure instanceof IllegalStateException state) throw state;
            throw new IllegalStateException(
                    "Failed to establish a connection to the MySQL database.", failure);
        }
    }

    @NotNull
    private static Properties getProperties(boolean maria) {
        Properties properties = new Properties();

        properties.setProperty(
                "sslMode",
                maria
                        ? (CCConfig.DATABASE_TLS ? "verify-full" : "disable")
                        : (CCConfig.DATABASE_TLS ? "VERIFY_IDENTITY" : "DISABLED"));
        properties.setProperty("useServerPrepStmts", "true");
        if (maria) return properties;
        properties.put("cachePrepStmts", "true");
        properties.put("prepStmtCacheSize", "250");
        properties.put("prepStmtCacheSqlLimit", "2048");
        properties.put("useLocalSessionState", "true");
        properties.put("useLocalTransactionState", "true");

        properties.put("rewriteBatchedStatements", "true");
        properties.put("cacheResultSetMetadata", "true");
        properties.put("cacheServerConfiguration", "true");
        properties.put("elideSetAutoCommits", "true");
        properties.put("maintainTimeStats", "false");
        return properties;
    }

    public int identityTransactionIsolation() {
        // Unique keys arbitrate concurrent first inserts. Older schemas still need gap locks.
        return identityConstraints
                ? Connection.TRANSACTION_READ_COMMITTED
                : Connection.TRANSACTION_REPEATABLE_READ;
    }

    public Connection getConnection() throws SQLException {
        if (shuttingDown) throw new SQLException("ChampionshipsCore database is shutting down");
        HikariDataSource current = dataSource;
        if (current == null)
            throw new SQLException("ChampionshipsCore database has not been initialized");
        if (current.isClosed())
            throw new SQLException("ChampionshipsCore database connection pool is closed");
        return current.getConnection();
    }
}
