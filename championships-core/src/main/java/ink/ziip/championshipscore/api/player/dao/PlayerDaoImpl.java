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

    @Override
    @NotNull
    public PlayerIdentityMigrationResult migrateNameChange(@NotNull String oldName,
                                                            @NotNull String newName,
                                                            @Nullable UUID replacementUuid) {
        try (Connection connection = plugin.getDatabaseManager().getConnection()) {
            connection.setAutoCommit(false);
            try {
                Set<UUID> previousUuids = collectNameChangeUuids(connection, oldName);
                UUID currentUuid = replacementUuid != null
                        ? replacementUuid
                        : previousUuids.stream().findFirst().orElse(null);
                if (currentUuid == null) {
                    connection.commit();
                    return PlayerIdentityMigrationResult.failed(newName, UUID.randomUUID(),
                            "没有找到旧名称对应的 UUID");
                }
                previousUuids.remove(currentUuid);

                Set<Integer> teamIds = collectNameChangeTeamIds(connection, oldName, previousUuids, currentUuid);
                Set<Integer> conflicts = teamIds.size() > 1 ? new LinkedHashSet<>(teamIds) : Set.of();
                Integer resolvedTeamId = teamIds.size() == 1 ? teamIds.iterator().next() : null;

                deleteIdentityRows(connection, "players", oldName, previousUuids, currentUuid);
                try (PreparedStatement insert = connection.prepareStatement("""
                        INSERT INTO `players` (`uuid`, `username`)
                        VALUES (?,?)
                        """)) {
                    insert.setString(1, currentUuid.toString());
                    insert.setString(2, newName);
                    insert.executeUpdate();
                }

                int migratedPointRows = migrateNameRows(connection, "player_points", oldName, newName,
                        currentUuid, previousUuids);
                for (String table : List.of(
                        "daily_player_stats",
                        "daily_match_results",
                        "daily_player_records",
                        "daily_map_player_stats",
                        "daily_pkw_records")) {
                    migrateNameRows(connection, table, oldName, newName, currentUuid, previousUuids);
                }

                if (conflicts.isEmpty()) {
                    deleteIdentityRows(connection, "team_members", oldName, previousUuids, currentUuid);
                    if (resolvedTeamId != null) {
                        try (PreparedStatement insert = connection.prepareStatement("""
                                INSERT INTO `team_members` (`uuid`, `username`, `teamId`)
                                VALUES (?,?,?)
                                """)) {
                            insert.setString(1, currentUuid.toString());
                            insert.setString(2, newName);
                            insert.setInt(3, resolvedTeamId);
                            insert.executeUpdate();
                        }
                    }
                }

                connection.commit();
                return new PlayerIdentityMigrationResult(newName, currentUuid, previousUuids,
                        resolvedTeamId, conflicts, migratedPointRows,
                        true, true, null);
            } catch (SQLException | RuntimeException exception) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackFailure) {
                    exception.addSuppressed(rollbackFailure);
                }
                throw exception;
            }
        } catch (SQLException | RuntimeException exception) {
            logFailure("审批改名迁移", exception);
            UUID fallback = replacementUuid != null ? replacementUuid : UUID.randomUUID();
            return PlayerIdentityMigrationResult.failed(newName, fallback,
                    exception.getClass().getSimpleName() + ": " + exception.getMessage());
        }
    }

    private Set<UUID> collectNameChangeUuids(@NotNull Connection connection,
                                             @NotNull String oldName) throws SQLException {
        Set<UUID> uuids = new LinkedHashSet<>();
        for (String table : List.of(
                "players", "team_members", "player_points",
                "daily_player_stats", "daily_match_results", "daily_player_records",
                "daily_map_player_stats", "daily_pkw_records")) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT DISTINCT `uuid` FROM `" + table + "` WHERE LOWER(`username`)=LOWER(?) FOR UPDATE")) {
                statement.setString(1, oldName);
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        try {
                            uuids.add(UUID.fromString(resultSet.getString(1)));
                        } catch (IllegalArgumentException ignored) {
                            // Ignore malformed legacy UUIDs; the name still gets migrated.
                        }
                    }
                }
            }
        }
        return uuids;
    }

    private Set<Integer> collectNameChangeTeamIds(@NotNull Connection connection,
                                                   @NotNull String oldName,
                                                   @NotNull Set<UUID> previousUuids,
                                                   @NotNull UUID currentUuid) throws SQLException {
        Set<Integer> teamIds = new LinkedHashSet<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT DISTINCT `teamId` FROM `team_members` WHERE LOWER(`username`)=LOWER(?) FOR UPDATE")) {
            statement.setString(1, oldName);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) teamIds.add(resultSet.getInt(1));
            }
        }
        for (UUID previousUuid : previousUuids) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT DISTINCT `teamId` FROM `team_members` WHERE `uuid`=? FOR UPDATE")) {
                statement.setString(1, previousUuid.toString());
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) teamIds.add(resultSet.getInt(1));
                }
            }
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT DISTINCT `teamId` FROM `team_members` WHERE `uuid`=? FOR UPDATE")) {
            statement.setString(1, currentUuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) teamIds.add(resultSet.getInt(1));
            }
        }
        return teamIds;
    }

    private int migrateNameRows(@NotNull Connection connection,
                                @NotNull String table,
                                @NotNull String oldName,
                                @NotNull String newName,
                                @NotNull UUID currentUuid,
                                @NotNull Set<UUID> previousUuids) throws SQLException {
        int changed = 0;
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE `" + table + "` SET `uuid`=?, `username`=? "
                        + "WHERE LOWER(`username`)=LOWER(?) AND (`uuid`<>? OR BINARY `username`<>BINARY ?)")) {
            statement.setString(1, currentUuid.toString());
            statement.setString(2, newName);
            statement.setString(3, oldName);
            statement.setString(4, currentUuid.toString());
            statement.setString(5, newName);
            changed += statement.executeUpdate();
        }
        for (UUID previousUuid : previousUuids) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE `" + table + "` SET `uuid`=?, `username`=? "
                            + "WHERE `uuid`=? AND (`uuid`<>? OR BINARY `username`<>BINARY ?)")) {
                statement.setString(1, currentUuid.toString());
                statement.setString(2, newName);
                statement.setString(3, previousUuid.toString());
                statement.setString(4, currentUuid.toString());
                statement.setString(5, newName);
                changed += statement.executeUpdate();
            }
        }
        return changed;
    }

    private void deleteIdentityRows(@NotNull Connection connection,
                                    @NotNull String table,
                                    @NotNull String oldName,
                                    @NotNull Set<UUID> previousUuids,
                                    @NotNull UUID currentUuid) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM `" + table + "` WHERE LOWER(`username`)=LOWER(?)")) {
            statement.setString(1, oldName);
            statement.executeUpdate();
        }
        for (UUID previousUuid : previousUuids) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM `" + table + "` WHERE `uuid`=?")) {
                statement.setString(1, previousUuid.toString());
                statement.executeUpdate();
            }
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM `" + table + "` WHERE `uuid`=?")) {
            statement.setString(1, currentUuid.toString());
            statement.executeUpdate();
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
                WHERE `uuid`=? OR LOWER(`username`)=LOWER(?)
                """)) {
            statement.setString(1, currentUuid.toString());
            statement.setString(2, name);
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
                WHERE (`uuid`=? OR LOWER(`username`)=LOWER(?))
                  AND (`uuid`<>? OR BINARY `username`<>BINARY ?)
                """)) {
            statement.setString(1, currentUuid.toString());
            statement.setString(2, name);
            statement.setString(3, currentUuid.toString());
            statement.setString(4, name);
            statement.setString(5, currentUuid.toString());
            statement.setString(6, name);
            migratedPointRows = statement.executeUpdate();
        }

        migrateDailyUsernames(connection, currentUuid, name);

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

    /**
     * Username is presentation data; UUID is the durable identity. Keep every
     * historical DAILY row aligned when a player reconnects after an approved
     * Minecraft name change.
     */
    private void migrateDailyUsernames(@NotNull Connection connection,
                                       @NotNull UUID currentUuid,
                                       @NotNull String username) throws SQLException {
        for (String table : List.of(
                "daily_player_stats",
                "daily_match_results",
                "daily_player_records",
                "daily_map_player_stats",
                "daily_pkw_records")) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE `" + table + "` SET `username`=? WHERE `uuid`=?")) {
                statement.setString(1, username);
                statement.setString(2, currentUuid.toString());
                statement.executeUpdate();
            }
        }
    }

    private void logFailure(String operation, Throwable exception) {
        plugin.getLogger().log(Level.SEVERE, Utils.formatModuleLog("Database", "玩家",
                "操作=" + operation + " 失败"), exception);
    }
}
