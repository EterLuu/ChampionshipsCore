package ink.ziip.championshipscore.database.map;

import ink.ziip.championshipscore.api.game.model.GameTypeEnum;
import ink.ziip.championshipscore.database.DatabaseManager;

import java.sql.Connection;
import java.sql.SQLException;

/** Holds the database side of a map rename until its file and runtime changes succeed. */
public final class MapRecordRenameTransaction implements AutoCloseable {
    private final Connection connection;
    private boolean finished;

    public MapRecordRenameTransaction(DatabaseManager database) throws SQLException {
        this(database.getConnection());
    }

    MapRecordRenameTransaction(Connection connection) throws SQLException {
        this.connection = connection;
        try {
            connection.setAutoCommit(false);
        } catch (SQLException failure) {
            try {
                connection.close();
            } catch (SQLException closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            throw failure;
        }
    }

    public MapRecordRenameMigration.Counts migrate(
            GameTypeEnum game,
            String oldRegistration,
            String newRegistration,
            String oldDisplayName,
            String newDisplayName)
            throws SQLException {
        if (finished) throw new IllegalStateException("Map rename transaction is already finished");
        return MapRecordRenameMigration.migrate(
                connection, game, oldRegistration, newRegistration, oldDisplayName, newDisplayName);
    }

    public void commit() throws SQLException {
        if (finished) throw new IllegalStateException("Map rename transaction is already finished");
        connection.commit();
        finished = true;
    }

    public void rollback() throws SQLException {
        if (finished) return;
        connection.rollback();
        finished = true;
    }

    @Override
    public void close() throws SQLException {
        try {
            rollback();
        } finally {
            connection.close();
        }
    }
}
