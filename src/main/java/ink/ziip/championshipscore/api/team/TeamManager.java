package ink.ziip.championshipscore.api.team;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseManager;
import ink.ziip.championshipscore.api.team.dao.TeamDaoImpl;
import ink.ziip.championshipscore.api.team.entry.TeamEntry;
import ink.ziip.championshipscore.api.team.entry.TeamMemberEntry;
import ink.ziip.championshipscore.configuration.config.CCConfig;
import ink.ziip.championshipscore.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import ink.ziip.championshipscore.util.scheduler.FoliaScheduler;
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
    private final FoliaScheduler scheduler;
    private static Scoreboard scoreboard = null;

    public TeamManager(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
        ScoreboardManager scoreboardManager = Bukkit.getScoreboardManager();
        if (scoreboardManager != null)
            scoreboard = scoreboardManager.getMainScoreboard();
        scheduler = FoliaScheduler.global(championshipsCore);
    }

    private void addTeam(LoadedTeam loadedTeam) {
        synchronized (cachedTeams) {
            String name = loadedTeam.name();
            if (cachedTeams.containsKey(name)) return;
            Team team = registerScoreboardTeam(loadedTeam.colorName(), loadedTeam.memberNames().values());
            ChampionshipTeam championshipTeam = new ChampionshipTeam(loadedTeam.id(), name,
                    loadedTeam.colorName(), loadedTeam.colorCode(), loadedTeam.memberNames().keySet(), team);
            cachedTeams.put(name, championshipTeam);
        }
    }

    public synchronized boolean addTeam(@NotNull String name, @NotNull String colorName, @NotNull String colorCode) {
        synchronized (cachedTeams) {
            if (cachedTeams.containsKey(name)) return false;

            if (Arrays.stream(Utils.getColorNames()).noneMatch(colorName::equalsIgnoreCase)) return false;

            int id = teamDaoImpl.addTeam(name, colorName, colorCode);
            ChampionshipTeam championshipTeam = new ChampionshipTeam(id, name, colorName, colorCode, null);
            cachedTeams.put(name, championshipTeam);
            scheduler.runTask(() -> championshipTeam.setScoreboardTeam(
                    registerScoreboardTeam(colorName, List.of())));
            return true;
        }
    }

    @Override
    public void load() {
        scheduler.runTaskAsynchronously(() -> {
            List<LoadedTeam> loadedTeams = new ArrayList<>();
            for (TeamEntry teamEntry : teamDaoImpl.getTeamList()) {
                int teamId = teamEntry.getId();
                Map<UUID, String> members = new HashMap<>();
                for (TeamMemberEntry teamMemberEntry : teamDaoImpl.getTeamMembers(teamId)) {
                    members.put(teamMemberEntry.getUuid(), teamMemberEntry.getUsername());
                }
                loadedTeams.add(new LoadedTeam(teamId, teamEntry.getName(), teamEntry.getColorName(),
                        teamEntry.getColorCode(), Map.copyOf(members)));
            }
            scheduler.runTask(() -> {
                for (Team team : scoreboard.getTeams()) {
                    team.unregister();
                }
                loadedTeams.forEach(this::addTeam);
            });
        });
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

    public synchronized boolean deleteTeam(@NotNull String name) {
        ChampionshipTeam championshipTeam = cachedTeams.get(name);
        if (championshipTeam == null)
            return false;

        if (plugin.getGameManager().getTeamCurrenArea(championshipTeam) != null)
            return false;

        championshipTeam = cachedTeams.remove(name);
        if (championshipTeam == null) return false;
        Team scoreboardTeam = championshipTeam.getTeam();
        if (scoreboardTeam != null) {
            scheduler.runTask(scoreboardTeam::unregister);
        }
        int id = championshipTeam.getId();

        scheduler.runTaskAsynchronously(() -> {
            teamDaoImpl.deleteTeam(id);
            teamDaoImpl.deleteTeamMembers(id);
        });
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

    private synchronized boolean addTeamMember(@NotNull UUID uuid, @NotNull String username, String teamName) {
        ChampionshipTeam championshipTeam = getTeam(teamName);
        if (championshipTeam == null) return false;

        if (championshipTeam.getMembers().size() >= CCConfig.TEAM_MAX_MEMBERS) return false;

        for (ChampionshipTeam cachedChampionshipTeam : cachedTeams.values()) {
            for (UUID memberUUID : cachedChampionshipTeam.getMembers()) {
                if (memberUUID.equals(uuid)) return false;
            }
        }

        championshipTeam.addMember(uuid);
        scheduler.runTask(() -> {
            Team scoreboardTeam = championshipTeam.getTeam();
            if (scoreboardTeam != null) scoreboardTeam.addEntry(username);
        });

        scheduler.runTaskAsynchronously(() -> teamDaoImpl.addTeamMember(championshipTeam.getId(), uuid, username));

        Player player = Bukkit.getPlayer(uuid);
        if (player != null)
            plugin.getGameManager().leaveSpectating(player);
        else
            plugin.getGameManager().removeSpectatingPlayerFromList(uuid);
        return true;
    }

    public boolean addTeamMember(@NotNull String username, @NotNull String teamName) {
        return addTeamMember(plugin.getPlayerManager().getPlayerUUID(username), username, teamName);
    }

    public boolean addTeamMember(@NotNull String username, @NotNull ChampionshipTeam championshipTeam) {
        return addTeamMember(username, championshipTeam.getName());
    }

    private synchronized boolean deleteTeamMember(@NotNull UUID uuid, @NotNull ChampionshipTeam championshipTeam) {
        if (championshipTeam.deleteMember(uuid)) {
            String username = plugin.getPlayerManager().getPlayerName(uuid);
            if (username != null) {
                scheduler.runTask(() -> {
                    Team scoreboardTeam = championshipTeam.getTeam();
                    if (scoreboardTeam != null) scoreboardTeam.removeEntry(username);
                });
            }

            scheduler.runTaskAsynchronously(() -> teamDaoImpl.deleteTeamMember(uuid));
            return true;
        }
        return false;
    }

    public boolean deleteTeamMember(@NotNull String username, @NotNull String teamName) {
        ChampionshipTeam championshipTeam = getTeam(teamName);
        if (championshipTeam == null) return false;
        for (TeamMemberEntry teamMemberEntry : teamDaoImpl.getTeamMembers(championshipTeam.getId())) {
            if (teamMemberEntry.getUsername().equals(username)) {
                if (deleteTeamMember(teamMemberEntry.getUuid(), championshipTeam)) {
                    plugin.getPlayerManager().deletePlayer(teamMemberEntry.getUuid());
                    return true;
                }
                return false;
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

    public void setCollision(boolean collision) {
        scheduler.runTask(() -> {
            for (Team team : scoreboard.getTeams()) {
                if (collision) {
                    team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.ALWAYS);
                } else {
                    team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
                }
            }
        });
    }

    private Team registerScoreboardTeam(String colorName, Collection<String> entries) {
        Team team = scoreboard.getTeam(colorName);
        if (team != null) {
            team.unregister();
        }
        team = scoreboard.registerNewTeam(colorName);
        try {
            team.color(Utils.toNamedTextColor(colorName));
        } catch (Exception ignored) {
        }
        entries.forEach(team::addEntry);
        return team;
    }

    private record LoadedTeam(int id, String name, String colorName, String colorCode,
                              Map<UUID, String> memberNames) {
    }
}
