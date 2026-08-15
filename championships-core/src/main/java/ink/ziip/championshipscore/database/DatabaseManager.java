package ink.ziip.championshipscore.database;

import com.zaxxer.hikari.HikariDataSource;
import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseManager;
import ink.ziip.championshipscore.configuration.config.CCConfig;
import org.jetbrains.annotations.NotNull;

import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Connection pool plus the single entry point for schema provisioning.
 *
 * <p><b>数据库结构变更规范：</b>所有 schema 变更必须走
 * {@link DatabaseMigrationController}：在 {@code MIGRATIONS} 注册表中<b>追加</b>新版本迁移
 * （只追加，绝不修改或删除已有迁移），并同步更新 {@code database/schema.sql} 保持当前完整结构。
 * 禁止在本类或任何 DAO 里直接执行 ALTER/CREATE 等结构变更语句——旧库升级只能通过迁移控制器完成。
 */
public class DatabaseManager extends BaseManager {
    private static final String DATA_POOL_NAME = "ChampionshipsCoreHikariPool";
    private String driverClass;
    private volatile HikariDataSource dataSource;
    private volatile boolean shuttingDown;
    private final AtomicLong lifecycleGeneration = new AtomicLong();

    public DatabaseManager(@NotNull ChampionshipsCore championshipsCore) {
        super(championshipsCore);
    }

    @Override
    public void load() {
        lifecycleGeneration.incrementAndGet();
        shuttingDown = false;
        configureAndInitialize();
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
        shuttingDown = false;
        long generation = lifecycleGeneration.incrementAndGet();
        return CompletableFuture.runAsync(() -> {
            if (shuttingDown || generation != lifecycleGeneration.get())
                throw new CancellationException("Database bootstrap cancelled");
            configureAndInitialize();
            if (shuttingDown || generation != lifecycleGeneration.get()) {
                HikariDataSource current = dataSource;
                if (current != null && !current.isClosed()) current.close();
                throw new CancellationException("Database bootstrap cancelled");
            }
        });
    }

    private void preloadClass(String className) {
        try {
            Class.forName(className);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void unload() {
        lifecycleGeneration.incrementAndGet();
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
            // Schema upgrades are version-controlled; see DatabaseMigrationController.
            new DatabaseMigrationController(plugin).migrate(connection);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to establish a connection to the MySQL database.", e);
        } catch (IllegalStateException e) {
            throw e;
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
