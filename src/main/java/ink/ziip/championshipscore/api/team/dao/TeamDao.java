package ink.ziip.championshipscore.api.team.dao;

import ink.ziip.championshipscore.api.team.entry.TeamEntry;
import ink.ziip.championshipscore.api.team.entry.TeamMemberEntry;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface TeamDao {
    List<TeamEntry> getTeamList();

    int addTeam(@NotNull String name, @NotNull String colorName, @NotNull String colorCode);

    void deleteTeam(int teamId);

    Set<TeamMemberEntry> getTeamMembers(int teamId);

    void deleteTeamMembers(int teamId);

    void deleteTeamMember(String username);

    void addTeamMember(int teamId, @NotNull UUID uuid, @NotNull String username);
}
