package ink.ziip.championshipscore.api.team;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseManager;
import ink.ziip.championshipscore.api.object.status.TeamStatusEnum;
import ink.ziip.championshipscore.api.team.dao.TeamDaoImpl;
import ink.ziip.championshipscore.api.team.entry.TeamEntry;
import ink.ziip.championshipscore.api.team.entry.TeamMemberEntry;
import ink.ziip.championshipscore.configuration.config.CCConfig;
import ink.ziip.championshipscore.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TeamManager extends BaseManager {
    private static final ConcurrentHashMap<String, Team> cachedTeams = new ConcurrentHashMap<>();
    private static final TeamDaoImpl teamDaoImpl = new TeamDaoImpl();

    public TeamManager(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
    }

    private void addTeam(int id, @NotNull String name, @NotNull String colorName, @NotNull String colorCode, @NotNull Set<UUID> members) {
        synchronized (cachedTeams) {
            if (cachedTeams.containsKey(name)) return;
            Team team = new Team(id, name, colorName, colorCode, members);
            cachedTeams.put(name, team);
        }
    }

    @Override
    public void load() {
        for (TeamEntry teamEntry : teamDaoImpl.getTeamList()) {
            int teamId = teamEntry.getId();
            Set<UUID> uuids = new HashSet<>();
            for (TeamMemberEntry teamMemberEntry : teamDaoImpl.getTeamMembers(teamId)) {
                uuids.add(teamMemberEntry.getUuid());
            }
            addTeam(teamId, teamEntry.getName(), teamEntry.getColorName(), teamEntry.getColorCode(), uuids);
        }
    }

    @Override
    public void unload() {

    }

    public List<Team> getTeamList() {
        return cachedTeams.values().stream().toList();
    }

    public List<String> getTeamNameList() {
        return new java.util.ArrayList<>(cachedTeams.keySet());
    }

    @Nullable
    public Team getTeam(@NotNull String name) {
        return cachedTeams.getOrDefault(name, null);
    }

    public boolean addTeam(@NotNull String name, @NotNull String colorName, @NotNull String colorCode) {
        synchronized (cachedTeams) {
            if (cachedTeams.containsKey(name)) return false;
            int id = teamDaoImpl.addTeam(name, colorName, colorCode);
            Team team = new Team(id, name, colorName, colorCode);
            cachedTeams.put(name, team);
            return true;
        }
    }

    public boolean deleteTeam(@NotNull String name) {
        Team team = cachedTeams.get(name);
        if (team != null && team.getTeamStatusEnum() != TeamStatusEnum.NONE)
            return false;
        team = cachedTeams.remove(name);
        if (team == null) return false;
        int id = team.getId();
        teamDaoImpl.deleteTeam(id);
        teamDaoImpl.deleteTeamMembers(id);
        return true;
    }

    @Nullable
    public Team getTeamByPlayer(@NotNull UUID uuid) {
        for (Team team : cachedTeams.values()) {
            for (UUID playerUUID : team.getMembers()) {
                if (playerUUID == uuid) return team;
            }
        }
        return null;
    }

    @Nullable
    public Team getTeamByPlayer(@NotNull Player player) {
        UUID uuid = player.getUniqueId();
        return getTeamByPlayer(uuid);
    }

    @Nullable
    public Team getTeamByPlayer(@NotNull OfflinePlayer offlinePlayer) {
        return getTeamByPlayer(offlinePlayer.getUniqueId());
    }

    private boolean addTeamMember(@NotNull UUID uuid, @NotNull String username, String teamName) {
        Team team = getTeam(teamName);
        if (team == null) return false;

        if (team.getMembers().size() >= CCConfig.TEAM_MAX_MEMBERS) return false;

        for (Team cachedTeam : cachedTeams.values()) {
            for (UUID memberUUID : cachedTeam.getMembers()) {
                if (memberUUID.equals(uuid)) return false;
            }
        }

        team.addMember(uuid);
        teamDaoImpl.addTeamMember(team.getId(), uuid, username);
        return true;
    }

    public boolean addTeamMember(@NotNull String username, @NotNull String teamName) {
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(username);
        return addTeamMember(offlinePlayer.getUniqueId(), username, teamName);
    }

    public boolean addTeamMember(@NotNull String username, @NotNull Team team) {
        return addTeamMember(username, team.getName());
    }

    private boolean deleteTeamMember(@NotNull UUID uuid, @NotNull Team team) {
        if (team.deleteMember(uuid)) {
            teamDaoImpl.deleteTeamMember(uuid);
            return true;
        }
        return false;
    }

    public boolean deleteTeamMember(@NotNull String username, @NotNull String teamName) {
        Team team = getTeam(teamName);
        if (team == null) return false;
        for (TeamMemberEntry teamMemberEntry : teamDaoImpl.getTeamMembers(team.getId())) {
            if (teamMemberEntry.getUsername().equals(username)) {
                return deleteTeamMember(teamMemberEntry.getUuid(), team);
            }
        }
        return false;
    }

    public String getTeamInfo(Team team) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("&r========").append(team.getColorCode()).append(team.getName()).append("&r========").append("\n");

        for (TeamMemberEntry teamMemberEntry : teamDaoImpl.getTeamMembers(team.getId())) {
            stringBuilder.append(teamMemberEntry.getUsername()).append("\n");
        }

        return Utils.translateColorCodes(stringBuilder.toString());
    }
}
