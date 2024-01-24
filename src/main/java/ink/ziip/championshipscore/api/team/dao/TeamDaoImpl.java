package ink.ziip.championshipscore.api.team.dao;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.team.entry.TeamEntry;
import ink.ziip.championshipscore.api.team.entry.TeamMemberEntry;
import org.jetbrains.annotations.NotNull;

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

                connection.close();
                return teamEntries;
            }
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Database query failed", exception);
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
                            connection.close();
                            return -1;
                        }
                    }
                } else {
                    connection.close();
                    return -1;
                }
            }
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Database query failed", exception);
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
                connection.close();
            }
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Database query failed", exception);
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

                connection.close();
                return members;
            }
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Database query failed", exception);
            return Collections.emptySet();
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
                connection.close();
            }
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Database query failed", exception);
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
                connection.close();
            }
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Database query failed", exception);
        }
    }

    @Override
    public void addTeamMember(int teamId, @NotNull UUID uuid, @NotNull String username) {
        try (Connection connection = plugin.getDatabaseManager().getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO `team_members` (`uuid`, `username`, `teamId`)
                    VALUES (?,?,?);
                    """)) {
                statement.setString(1, uuid.toString());
                statement.setString(2, username);
                statement.setInt(3, teamId);

                statement.executeUpdate();
                connection.close();
            }
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Database query failed", exception);
        }
    }
}
