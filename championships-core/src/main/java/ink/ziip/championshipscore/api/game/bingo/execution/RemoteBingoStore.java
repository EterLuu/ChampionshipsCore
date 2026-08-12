package ink.ziip.championshipscore.api.game.bingo.execution;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.protocol.BinaryProtocolCodec;
import ink.ziip.championshipscore.protocol.MatchEvent;
import ink.ziip.championshipscore.protocol.MatchManifest;
import ink.ziip.championshipscore.protocol.MatchState;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** MariaDB persistence for Core ownership, fencing and the durable event inbox. */
final class RemoteBingoStore {
    private final ChampionshipsCore plugin;
    private final BinaryProtocolCodec codec = new BinaryProtocolCodec();

    RemoteBingoStore(ChampionshipsCore plugin) {
        this.plugin = plugin;
    }

    void create(MatchManifest manifest) throws SQLException {
        String sql = "INSERT INTO remote_bingo_matches "
                + "(matchId, epoch, workerId, ownerInstance, state, manifest, updatedAt) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE workerId=VALUES(workerId), ownerInstance=VALUES(ownerInstance), "
                + "state=VALUES(state), "
                + "manifest=VALUES(manifest), updatedAt=VALUES(updatedAt)";
        try (var connection = plugin.getDatabaseManager().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, manifest.matchId().toString());
            statement.setLong(2, manifest.epoch());
            statement.setString(3, manifest.workerId());
            statement.setString(4, ownerInstance());
            statement.setString(5, MatchState.CREATED.name());
            statement.setBytes(6, codec.encodeManifest(manifest));
            statement.setLong(7, System.currentTimeMillis());
            statement.executeUpdate();
        }
    }

    void updateState(UUID matchId, long epoch, MatchState state) throws SQLException {
        try (var connection = plugin.getDatabaseManager().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE remote_bingo_matches SET state=?, updatedAt=? "
                             + "WHERE matchId=? AND epoch=? AND ownerInstance=?")) {
            statement.setString(1, state.name());
            statement.setLong(2, System.currentTimeMillis());
            statement.setString(3, matchId.toString());
            statement.setLong(4, epoch);
            statement.setString(5, ownerInstance());
            statement.executeUpdate();
        }
    }

    boolean processed(UUID messageId) throws SQLException {
        try (var connection = plugin.getDatabaseManager().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT 1 FROM remote_bingo_inbox WHERE messageId=?")) {
            statement.setString(1, messageId.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    void recordProcessed(MatchEvent event, MatchState state) throws SQLException {
        String inboxSql = "INSERT IGNORE INTO remote_bingo_inbox "
                + "(messageId, matchId, epoch, eventSeq, eventType, processedAt) VALUES (?, ?, ?, ?, ?, ?)";
        String stateSql = "UPDATE remote_bingo_matches SET state=?, updatedAt=? "
                + "WHERE matchId=? AND epoch=? AND ownerInstance=?";
        try (var connection = plugin.getDatabaseManager().getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement inbox = connection.prepareStatement(inboxSql);
                 PreparedStatement match = connection.prepareStatement(stateSql)) {
                long now = System.currentTimeMillis();
                inbox.setString(1, event.messageId().toString());
                inbox.setString(2, event.matchId().toString());
                inbox.setLong(3, event.epoch());
                inbox.setLong(4, event.seq());
                inbox.setString(5, event.type().name());
                inbox.setLong(6, now);
                inbox.executeUpdate();

                match.setString(1, state.name());
                match.setLong(2, now);
                match.setString(3, event.matchId().toString());
                match.setLong(4, event.epoch());
                match.setString(5, ownerInstance());
                match.executeUpdate();
                connection.commit();
            } catch (SQLException failure) {
                connection.rollback();
                throw failure;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    List<MatchManifest> activeMatches() throws SQLException {
        List<MatchManifest> active = new ArrayList<>();
        String sql = "SELECT manifest FROM remote_bingo_matches "
                + "WHERE ownerInstance=? AND state NOT IN ('FINISHED','ABORTED')";
        try (var connection = plugin.getDatabaseManager().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, ownerInstance());
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) active.add(codec.decodeManifest(result.getBytes(1)));
            }
        }
        return List.copyOf(active);
    }

    private String ownerInstance() {
        String owner = plugin.getRedisManager().instanceId();
        if (owner == null || owner.isBlank()) throw new IllegalStateException("Redis instance id is not ready");
        return owner;
    }
}
