package ink.ziip.championshipscore.api.player.dao;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.player.entry.PlayerEntry;
import ink.ziip.championshipscore.api.player.entry.PlayerIdentityMigrationResult;
import ink.ziip.championshipscore.util.Utils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public class PlayerDaoImpl implements PlayerDao {
    private final ChampionshipsCore plugin = ChampionshipsCore.getInstance();

    @Override
    public void addPlayer(@NotNull String name, @NotNull UUID uuid) {
        try (Connection connection = plugin.getDatabaseManager().getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO `players` (`uuid`, `username`)
                    VALUES (?,?);
                    """)) {
                statement.setString(1, uuid.toString());
                statement.setString(2, name);

                statement.executeUpdate();
            }
        } catch (SQLException exception) {
            logFailure("新增玩家", exception);
        }
    }

    @Override
    @Nullable
    public PlayerEntry getPlayer(UUID uuid) {
        try (Connection connection = plugin.getDatabaseManager().getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT `id`, `uuid`, `username`
                    FROM `players`
                    WHERE uuid=?
                    """)) {
                statement.setString(1, uuid.toString());
                final ResultSet resultSet = statement.executeQuery();
                if (resultSet.next()) {
                    return PlayerEntry
                            .builder()
                            .id(resultSet.getInt("id"))
                            .uuid(UUID.fromString(resultSet.getString("uuid")))
                            .name(resultSet.getString("username"))
                            .build();
                }
                return null;
            }
        } catch (SQLException exception) {
            logFailure("按 UUID 查询玩家", exception);
            return null;
        }
    }

    @Override
    @Nullable
    public PlayerEntry getPlayer(String name) {
        try (Connection connection = plugin.getDatabaseManager().getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT `id`, `uuid`, `username`
                    FROM `players`
                    WHERE username=?
                    """)) {
                statement.setString(1, name);
                final ResultSet resultSet = statement.executeQuery();
                if (resultSet.next()) {
                    return PlayerEntry
                            .builder()
                            .id(resultSet.getInt("id"))
                            .uuid(UUID.fromString(resultSet.getString("uuid")))
                            .name(resultSet.getString("username"))
                            .build();
                }
                return null;
            }
        } catch (SQLException exception) {
            logFailure("按名称查询玩家", exception);
            return null;
        }
    }

    @Override
    public @NotNull List<PlayerEntry> getPlayerList() {
        try (Connection connection = plugin.getDatabaseManager().getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT `id`, `uuid`, `username`
                     FROM `players`
                     ORDER BY LOWER(`username`), `username`
                     """);
             ResultSet resultSet = statement.executeQuery()) {
            List<PlayerEntry> players = new ArrayList<>();
            while (resultSet.next()) {
                players.add(PlayerEntry.builder()
                        .id(resultSet.getInt("id"))
                        .uuid(UUID.fromString(resultSet.getString("uuid")))
                        .name(resultSet.getString("username"))
                        .build());
            }
            return players;
        } catch (SQLException | IllegalArgumentException exception) {
            logFailure("查询玩家列表", exception);
            return List.of();
        }
    }

    @Override
    public void deletePlayer(UUID uuid) {
        try (Connection connection = plugin.getDatabaseManager().getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    DELETE
                    FROM `players`
                    WHERE `uuid`=?
                    """)) {
                statement.setString(1, uuid.toString());
                statement.executeUpdate();
            }
        } catch (SQLException exception) {
            logFailure("删除玩家", exception);
        }
    }

    @Override
    @NotNull
    public PlayerIdentityMigrationResult synchronizeIdentity(@NotNull String name, @NotNull UUID currentUuid) {
        try (Connection connection = plugin.getDatabaseManager().getConnection()) {
            connection.setAutoCommit(false);
            try {
                PlayerIdentityMigrationResult result = synchronizeIdentity(connection, name, currentUuid);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException exception) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackFailure) {
                    exception.addSuppressed(rollbackFailure);
                }
                throw exception;
            }
        } catch (SQLException | RuntimeException exception) {
            logFailure("同步玩家 UUID", exception);
            return PlayerIdentityMigrationResult.failed(name, currentUuid,
                    exception.getClass().getSimpleName() + ": " + exception.getMessage());
        }
    }

    private PlayerIdentityMigrationResult synchronizeIdentity(@NotNull Connection connection,
                                                               @NotNull String name,
                                                               @NotNull UUID currentUuid) throws SQLException {
        Set<UUID> previousUuids = new LinkedHashSet<>();
        int matchingPlayerRows = 0;
        boolean canonicalPlayerRow = false;

        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT `uuid`, `username`
                FROM `players`
                WHERE LOWER(`username`)=LOWER(?) OR `uuid`=?
                FOR UPDATE
                """)) {
            statement.setString(1, name);
            statement.setString(2, currentUuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    UUID storedUuid = UUID.fromString(resultSet.getString("uuid"));
                    String storedName = resultSet.getString("username");
                    if (storedName.equalsIgnoreCase(name)) {
                        matchingPlayerRows++;
                        if (!storedUuid.equals(currentUuid)) previousUuids.add(storedUuid);
                    }
                    if (storedUuid.equals(currentUuid) && storedName.equals(name)) {
                        canonicalPlayerRow = true;
                    }
                }
            }
        }

        Set<Integer> allSameNameTeamIds = new LinkedHashSet<>();
        Set<Integer> currentUuidTeamIds = new LinkedHashSet<>();
        int matchingTeamRows = 0;
        boolean canonicalTeamRow = false;

        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT `uuid`, `username`, `teamId`
                FROM `team_members`
                WHERE LOWER(`username`)=LOWER(?) OR `uuid`=?
                FOR UPDATE
                """)) {
            statement.setString(1, name);
            statement.setString(2, currentUuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    UUID storedUuid = UUID.fromString(resultSet.getString("uuid"));
                    String storedName = resultSet.getString("username");
                    int teamId = resultSet.getInt("teamId");
                    if (storedName.equalsIgnoreCase(name)) {
                        matchingTeamRows++;
                        allSameNameTeamIds.add(teamId);
                        if (!storedUuid.equals(currentUuid)) previousUuids.add(storedUuid);
                    }
                    if (storedUuid.equals(currentUuid)) {
                        currentUuidTeamIds.add(teamId);
                        if (storedName.equals(name)) canonicalTeamRow = true;
                    }
                }
            }
        }

        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT DISTINCT `uuid`
                FROM `player_points`
                WHERE LOWER(`username`)=LOWER(?)
                """)) {
            statement.setString(1, name);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    UUID storedUuid = UUID.fromString(resultSet.getString("uuid"));
                    if (!storedUuid.equals(currentUuid)) previousUuids.add(storedUuid);
                }
            }
        }

        Integer resolvedTeamId = null;
        Set<Integer> conflictingTeamIds = new LinkedHashSet<>();
        if (currentUuidTeamIds.size() == 1) {
            resolvedTeamId = currentUuidTeamIds.iterator().next();
        } else if (currentUuidTeamIds.size() > 1) {
            conflictingTeamIds.addAll(currentUuidTeamIds);
        } else if (allSameNameTeamIds.size() == 1) {
            resolvedTeamId = allSameNameTeamIds.iterator().next();
        } else if (allSameNameTeamIds.size() > 1) {
            conflictingTeamIds.addAll(allSameNameTeamIds);
        }

        boolean playerRowsNeedRewrite = matchingPlayerRows != 1 || !canonicalPlayerRow;
        if (playerRowsNeedRewrite) {
            try (PreparedStatement delete = connection.prepareStatement("""
                    DELETE FROM `players`
                    WHERE LOWER(`username`)=LOWER(?) OR `uuid`=?
                    """)) {
                delete.setString(1, name);
                delete.setString(2, currentUuid.toString());
                delete.executeUpdate();
            }
            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT INTO `players` (`uuid`, `username`)
                    VALUES (?,?)
                    """)) {
                insert.setString(1, currentUuid.toString());
                insert.setString(2, name);
                insert.executeUpdate();
            }
        }

        int migratedPointRows;
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE `player_points`
                SET `uuid`=?, `username`=?
                WHERE LOWER(`username`)=LOWER(?)
                  AND (`uuid`<>? OR BINARY `username`<>BINARY ?)
                """)) {
            statement.setString(1, currentUuid.toString());
            statement.setString(2, name);
            statement.setString(3, name);
            statement.setString(4, currentUuid.toString());
            statement.setString(5, name);
            migratedPointRows = statement.executeUpdate();
        }

        boolean teamRowsChanged = false;
        if (conflictingTeamIds.isEmpty()) {
            boolean canonicalTeamState = resolvedTeamId == null
                    ? matchingTeamRows == 0 && currentUuidTeamIds.isEmpty()
                    : matchingTeamRows == 1 && currentUuidTeamIds.size() == 1 && canonicalTeamRow;
            if (!canonicalTeamState) {
                try (PreparedStatement delete = connection.prepareStatement("""
                        DELETE FROM `team_members`
                        WHERE LOWER(`username`)=LOWER(?) OR `uuid`=?
                        """)) {
                    delete.setString(1, name);
                    delete.setString(2, currentUuid.toString());
                    delete.executeUpdate();
                }
                if (resolvedTeamId != null) {
                    try (PreparedStatement insert = connection.prepareStatement("""
                            INSERT INTO `team_members` (`uuid`, `username`, `teamId`)
                            VALUES (?,?,?)
                            """)) {
                        insert.setString(1, currentUuid.toString());
                        insert.setString(2, name);
                        insert.setInt(3, resolvedTeamId);
                        insert.executeUpdate();
                    }
                }
                teamRowsChanged = true;
            }
        }

        boolean changed = playerRowsNeedRewrite || teamRowsChanged || migratedPointRows > 0;
        return new PlayerIdentityMigrationResult(name, currentUuid, previousUuids, resolvedTeamId,
                conflictingTeamIds, migratedPointRows, changed, true, null);
    }

    private void logFailure(String operation, Throwable exception) {
        plugin.getLogger().log(Level.SEVERE, Utils.formatModuleLog("Database", "玩家",
                "操作=" + operation + " 失败"), exception);
    }
}
