package ink.ziip.championshipscore.api.team.dao;

import ink.ziip.championshipscore.api.team.entry.TeamEntry;
import ink.ziip.championshipscore.api.team.entry.TeamMemberEntry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface TeamDao {
    List<TeamEntry> getTeamList();

    int addTeam(@NotNull String name, @NotNull String colorName, @NotNull String colorCode);

    void deleteTeam(int teamId);

    Set<TeamMemberEntry> getTeamMembers(int teamId);

    @Nullable
    Set<TeamMemberEntry> getTeamMembers(@NotNull String username);

    void deleteTeamMembers(int teamId);

    void deleteTeamMember(UUID uuid);

    boolean deleteTeamMembers(int teamId, @NotNull String username);

    boolean addTeamMember(int teamId, @NotNull UUID uuid, @NotNull String username);

    /** Atomically removes every alias of this player from old teams and inserts the target membership. */
    boolean moveTeamMember(int teamId, @NotNull UUID uuid, @NotNull String username);
}
