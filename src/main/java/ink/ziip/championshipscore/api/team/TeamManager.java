package ink.ziip.championshipscore.api.team;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseManager;
import ink.ziip.championshipscore.api.team.dao.TeamDaoImpl;
import ink.ziip.championshipscore.api.team.entry.TeamEntry;
import ink.ziip.championshipscore.api.team.entry.TeamMemberEntry;
import ink.ziip.championshipscore.configuration.config.CCConfig;
import ink.ziip.championshipscore.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TeamManager extends BaseManager {
    private static final ConcurrentHashMap<String, ChampionshipTeam> cachedTeams = new ConcurrentHashMap<>();
    private static final TeamDaoImpl teamDaoImpl = new TeamDaoImpl();
    private static Scoreboard scoreboard = null;

    public TeamManager(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
        ScoreboardManager scoreboardManager = Bukkit.getScoreboardManager();
        if (scoreboardManager != null)
            scoreboard = scoreboardManager.getMainScoreboard();
    }

    private void addTeam(int id, @NotNull String name, @NotNull String colorName, @NotNull String colorCode, @NotNull Set<UUID> members) {
        synchronized (cachedTeams) {
            if (cachedTeams.containsKey(name)) return;

            Team team = scoreboard.getTeam(colorName);
            if (team != null) {
                team.unregister();
            }
            team = scoreboard.registerNewTeam(colorName);

            try {
                team.setColor(ChatColor.valueOf(colorName));
            } catch (Exception ignored) {
            }

            for (UUID uuid : members) {
                OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
                String playerName = offlinePlayer.getName();
                if (playerName != null) {
                    team.addEntry(playerName);
                }
            }

            ChampionshipTeam championshipTeam = new ChampionshipTeam(id, name, colorName, colorCode, members, team);
            cachedTeams.put(name, championshipTeam);
        }
    }

    public boolean addTeam(@NotNull String name, @NotNull String colorName, @NotNull String colorCode) {
        synchronized (cachedTeams) {
            if (cachedTeams.containsKey(name)) return false;
            int id = teamDaoImpl.addTeam(name, colorName, colorCode);

            Team team = scoreboard.getTeam(colorName);
            if (team != null) {
                team.unregister();
            }
            team = scoreboard.registerNewTeam(colorName);

            ChampionshipTeam championshipTeam = new ChampionshipTeam(id, name, colorName, colorCode, team);
            cachedTeams.put(name, championshipTeam);
            return true;
        }
    }

    @Override
    public void load() {
        for (Team team : scoreboard.getTeams()) {
            team.unregister();
        }
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

    public List<ChampionshipTeam> getTeamList() {
        return cachedTeams.values().stream().toList();
    }

    public List<String> getTeamNameList() {
        return new ArrayList<>(cachedTeams.keySet());
    }

    @Nullable
    public ChampionshipTeam getTeam(@NotNull String name) {
        return cachedTeams.getOrDefault(name, null);
    }

    public boolean deleteTeam(@NotNull String name) {
        ChampionshipTeam championshipTeam = cachedTeams.get(name);

        if (plugin.getGameManager().getTeamCurrenArea(championshipTeam) != null)
            return false;

        championshipTeam = cachedTeams.remove(name);
        if (championshipTeam == null) return false;
        championshipTeam.getTeam().unregister();
        int id = championshipTeam.getId();
        teamDaoImpl.deleteTeam(id);
        teamDaoImpl.deleteTeamMembers(id);
        return true;
    }

    @Nullable
    public ChampionshipTeam getTeamByPlayer(@NotNull UUID uuid) {
        for (ChampionshipTeam championshipTeam : cachedTeams.values()) {
            for (UUID playerUUID : championshipTeam.getMembers()) {
                if (playerUUID.equals(uuid)) return championshipTeam;
            }
        }
        return null;
    }

    @Nullable
    public ChampionshipTeam getTeamByPlayer(@NotNull Player player) {
        UUID uuid = player.getUniqueId();
        return getTeamByPlayer(uuid);
    }

    @Nullable
    public ChampionshipTeam getTeamByPlayer(@NotNull OfflinePlayer offlinePlayer) {
        return getTeamByPlayer(offlinePlayer.getUniqueId());
    }

    private boolean addTeamMember(@NotNull UUID uuid, @NotNull String username, String teamName) {
        ChampionshipTeam championshipTeam = getTeam(teamName);
        if (championshipTeam == null) return false;

        if (championshipTeam.getMembers().size() >= CCConfig.TEAM_MAX_MEMBERS) return false;

        for (ChampionshipTeam cachedChampionshipTeam : cachedTeams.values()) {
            for (UUID memberUUID : cachedChampionshipTeam.getMembers()) {
                if (memberUUID.equals(uuid)) return false;
            }
        }

        championshipTeam.getTeam().addEntry(username);
        championshipTeam.addMember(uuid);
        teamDaoImpl.addTeamMember(championshipTeam.getId(), uuid, username);
        Player player = Bukkit.getPlayer(uuid);
        if (player != null)
            plugin.getGameManager().leaveSpectating(player);
        else
            plugin.getGameManager().removeSpectatingPlayerFromList(uuid);
        return true;
    }

    public boolean addTeamMember(@NotNull String username, @NotNull String teamName) {
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(username);
        return addTeamMember(offlinePlayer.getUniqueId(), username, teamName);
    }

    public boolean addTeamMember(@NotNull String username, @NotNull ChampionshipTeam championshipTeam) {
        return addTeamMember(username, championshipTeam.getName());
    }

    private boolean deleteTeamMember(@NotNull UUID uuid, @NotNull ChampionshipTeam championshipTeam) {
        if (championshipTeam.deleteMember(uuid)) {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
            String username = offlinePlayer.getName();
            if (username != null)
                championshipTeam.getTeam().removeEntry(username);
            teamDaoImpl.deleteTeamMember(uuid);
            return true;
        }
        return false;
    }

    public boolean deleteTeamMember(@NotNull String username, @NotNull String teamName) {
        ChampionshipTeam championshipTeam = getTeam(teamName);
        if (championshipTeam == null) return false;
        for (TeamMemberEntry teamMemberEntry : teamDaoImpl.getTeamMembers(championshipTeam.getId())) {
            if (teamMemberEntry.getUsername().equals(username)) {
                return deleteTeamMember(teamMemberEntry.getUuid(), championshipTeam);
            }
        }
        return false;
    }

    public String getTeamInfo(ChampionshipTeam championshipTeam) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("&r========").append(championshipTeam.getColorCode()).append(championshipTeam.getName()).append("&r========").append("\n");

        for (TeamMemberEntry teamMemberEntry : teamDaoImpl.getTeamMembers(championshipTeam.getId())) {
            stringBuilder.append(teamMemberEntry.getUsername()).append("\n");
        }

        return Utils.translateColorCodes(stringBuilder.toString());
    }
}
