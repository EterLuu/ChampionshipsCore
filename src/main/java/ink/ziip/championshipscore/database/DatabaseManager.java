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

    public DatabaseManager(@NotNull ChampionshipsCore championshipsCore) {
        super(championshipsCore);
    }

    @Override
    public void load() {
        if (CCConfig.DATABASE_TYPE.equals("MARIADB"))
            this.driverClass = "org.mariadb.jdbc.Driver";
        else
            this.driverClass = "com.mysql.cj.jdbc.Driver";
        initialize();
    }

    @Override
    public void unload() {
        if (dataSource != null) {
            if (!dataSource.isClosed()) {
                dataSource.close();
            }
        }
    }

    public void initialize() throws IllegalStateException {
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
            } catch (SQLException | IOException e) {
                throw new IllegalStateException("Failed to create database tables.", e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to establish a connection to the MySQL database.", e);
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
        if (!dataSource.isClosed())
            return dataSource.getConnection();

        initialize();
        return dataSource.getConnection();
    }
}
