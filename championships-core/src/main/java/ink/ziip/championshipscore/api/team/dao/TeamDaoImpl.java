package ink.ziip.championshipscore.api.team.dao;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.team.entry.TeamEntry;
import ink.ziip.championshipscore.api.team.entry.TeamMemberEntry;
import ink.ziip.championshipscore.util.Utils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.*;
import java.util.*;
import java.util.logging.Level;

public class TeamDaoImpl implements TeamDao {
    private final ChampionshipsCore plugin = ChampionshipsCore.getInstance();

    @Override
    public List<TeamEntry> getTeamList() {
        try (Connection connection = plugin.getDatabaseManager().getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT `id`, `name`, `colorName`, `colorCode`
                    FROM `teams`
                    """)) {

                List<TeamEntry> teamEntries = new ArrayList<>();

                final ResultSet resultSet = statement.executeQuery();
                while (resultSet.next()) {
                    int id = resultSet.getInt("id");
                    String name = resultSet.getString("name");
                    String colorName = resultSet.getString("colorName");
                    String colorCode = resultSet.getString("colorCode");
                    teamEntries.add(new TeamEntry(id, name, colorName, colorCode));
                }
                return teamEntries;
            }
        } catch (SQLException exception) {
            logFailure("查询队伍列表", exception);
            return Collections.emptyList();
        }
    }

    @Override
    public int addTeam(@NotNull String name, @NotNull String colorName, @NotNull String colorCode) {
        try (Connection connection = plugin.getDatabaseManager().getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO `teams` (`name`, `colorName`, `colorCode`)
                    VALUES (?,?,?);
                    """, Statement.RETURN_GENERATED_KEYS)) {
                statement.setString(1, name);
                statement.setString(2, colorName);
                statement.setString(3, colorCode);

                int affectedRows = statement.executeUpdate();
                if (affectedRows > 0) {
                    try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                        if (generatedKeys.next()) {
                            return generatedKeys.getInt(1);
                        } else {
                            return -1;
                        }
                    }
                } else {
                    return -1;
                }
            }
        } catch (SQLException exception) {
            logFailure("新增队伍", exception);
            return -1;
        }
    }

    @Override
    public void deleteTeam(int teamId) {
        try (Connection connection = plugin.getDatabaseManager().getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    DELETE
                    FROM `teams`
                    WHERE `id`=?
                    """)) {
                statement.setInt(1, teamId);

                statement.executeUpdate();
            }
        } catch (SQLException exception) {
            logFailure("删除队伍", exception);
        }
    }

    @Override
    public Set<TeamMemberEntry> getTeamMembers(int teamId) {
        try (Connection connection = plugin.getDatabaseManager().getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT `id`, `uuid`, `username`, `teamId`
                    FROM `team_members`
                    WHERE `teamId`=?
                    """)) {
                statement.setInt(1, teamId);

                Set<TeamMemberEntry> members = new HashSet<>();

                final ResultSet resultSet = statement.executeQuery();
                while (resultSet.next()) {
                    int id = resultSet.getInt("id");
                    UUID uuid = UUID.fromString(resultSet.getString("uuid"));
                    String username = resultSet.getString("username");

                    TeamMemberEntry teamMemberEntry = new TeamMemberEntry(id, uuid, username, teamId);
                    members.add(teamMemberEntry);
                }

                return members;
            }
        } catch (SQLException exception) {
            logFailure("查询队伍成员", exception);
            return Collections.emptySet();
        }
    }

    @Override
    @Nullable
    public Set<TeamMemberEntry> getTeamMembers(@NotNull String username) {
        try (Connection connection = plugin.getDatabaseManager().getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT `id`, `uuid`, `username`, `teamId`
                    FROM `team_members`
                    WHERE LOWER(`username`)=LOWER(?)
                    """)) {
                statement.setString(1, username);
                Set<TeamMemberEntry> members = new HashSet<>();
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        members.add(new TeamMemberEntry(resultSet.getInt("id"),
                                UUID.fromString(resultSet.getString("uuid")),
                                resultSet.getString("username"), resultSet.getInt("teamId")));
                    }
                }
                return members;
            }
        } catch (SQLException | IllegalArgumentException exception) {
            logFailure("按名称查询队伍成员", exception);
            return null;
        }
    }

    @Override
    public void deleteTeamMembers(int teamId) {
        try (Connection connection = plugin.getDatabaseManager().getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    DELETE
                    FROM `team_members`
                    WHERE `teamId`=?
                    """)) {
                statement.setInt(1, teamId);

                statement.executeUpdate();
            }
        } catch (SQLException exception) {
            logFailure("清空队伍成员", exception);
        }
    }

    @Override
    public void deleteTeamMember(UUID uuid) {
        try (Connection connection = plugin.getDatabaseManager().getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    DELETE
                    FROM `team_members`
                    WHERE `uuid`=?
                    """)) {
                statement.setString(1, uuid.toString());

                statement.executeUpdate();
            }
        } catch (SQLException exception) {
            logFailure("删除队伍成员", exception);
        }
    }

    @Override
    public boolean deleteTeamMembers(int teamId, @NotNull String username) {
        try (Connection connection = plugin.getDatabaseManager().getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    DELETE FROM `team_members`
                    WHERE `teamId`=? AND LOWER(`username`)=LOWER(?)
                    """)) {
                statement.setInt(1, teamId);
                statement.setString(2, username);
                return statement.executeUpdate() > 0;
            }
        } catch (SQLException exception) {
            logFailure("按名称删除队伍成员", exception);
            return false;
        }
    }

    @Override
    public boolean addTeamMember(int teamId, @NotNull UUID uuid, @NotNull String username) {
        try (Connection connection = plugin.getDatabaseManager().getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO `team_members` (`uuid`, `username`, `teamId`)
                    VALUES (?,?,?);
                    """)) {
                statement.setString(1, uuid.toString());
                statement.setString(2, username);
                statement.setInt(3, teamId);

                return statement.executeUpdate() == 1;
            }
        } catch (SQLException exception) {
            logFailure("新增队伍成员", exception);
            return false;
        }
    }

    @Override
    public boolean moveTeamMember(int teamId, @NotNull UUID uuid, @NotNull String username) {
        try (Connection connection = plugin.getDatabaseManager().getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement lock = connection.prepareStatement("""
                        SELECT `id`
                        FROM `team_members`
                        WHERE `uuid`=? OR LOWER(`username`)=LOWER(?)
                        FOR UPDATE
                        """)) {
                    lock.setString(1, uuid.toString());
                    lock.setString(2, username);
                    try (ResultSet ignored = lock.executeQuery()) {
                        while (ignored.next()) {
                            // Lock every matching identity row until the replacement has committed.
                        }
                    }
                }
                try (PreparedStatement delete = connection.prepareStatement("""
                        DELETE FROM `team_members`
                        WHERE `uuid`=? OR LOWER(`username`)=LOWER(?)
                        """)) {
                    delete.setString(1, uuid.toString());
                    delete.setString(2, username);
                    delete.executeUpdate();
                }
                try (PreparedStatement insert = connection.prepareStatement("""
                        INSERT INTO `team_members` (`uuid`, `username`, `teamId`)
                        VALUES (?,?,?)
                        """)) {
                    insert.setString(1, uuid.toString());
                    insert.setString(2, username);
                    insert.setInt(3, teamId);
                    if (insert.executeUpdate() != 1) throw new SQLException("队伍成员迁移未插入唯一记录");
                }
                connection.commit();
                return true;
            } catch (SQLException | RuntimeException exception) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackFailure) {
                    exception.addSuppressed(rollbackFailure);
                }
                throw exception;
            } finally {
                try {
                    connection.setAutoCommit(true);
                } catch (SQLException ignored) {
                }
            }
        } catch (SQLException | RuntimeException exception) {
            logFailure("迁移队伍成员", exception);
            return false;
        }
    }

    private void logFailure(String operation, Throwable exception) {
        plugin.getLogger().log(Level.SEVERE, Utils.formatModuleLog("Database", "队伍",
                "操作=" + operation + " 失败"), exception);
    }
}
