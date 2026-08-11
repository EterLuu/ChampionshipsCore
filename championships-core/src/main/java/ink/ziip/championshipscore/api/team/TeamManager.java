package ink.ziip.championshipscore.api.team;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseManager;
import ink.ziip.championshipscore.api.player.entry.PlayerIdentityMigrationResult;
import ink.ziip.championshipscore.api.team.dao.TeamDaoImpl;
import ink.ziip.championshipscore.api.team.entry.TeamEntry;
import ink.ziip.championshipscore.api.team.entry.TeamMemberEntry;
import ink.ziip.championshipscore.configuration.config.CCConfig;
import ink.ziip.championshipscore.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TeamManager extends BaseManager {
    private static final String DAILY_LOBBY_TEAM_ID = "ccd_lobby";
    private static final String NO_SCOREBOARD_TEAM = "\u0000";
    private static final ConcurrentHashMap<String, ChampionshipTeam> cachedTeams = new ConcurrentHashMap<>();
    /** Match-scoped teams used by DAILY runs. They never enter {@link #cachedTeams} or the database. */
    private final ConcurrentHashMap<UUID, ChampionshipTeam> transientTeamByPlayer = new ConcurrentHashMap<>();
    // ChampionshipTeam equality is display-name based. DAILY deliberately reuses colour names such
    // as "红队" between matches, so this registry must distinguish the actual runtime objects.
    private final Set<ChampionshipTeam> transientTeams = newTransientTeamRegistry();
    private final ConcurrentHashMap<UUID, String> originalScoreboardTeamByPlayer = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, String> dailyLobbyOriginalTeamByPlayer = new ConcurrentHashMap<>();
    private static final TeamDaoImpl teamDaoImpl = new TeamDaoImpl();
    private final BukkitScheduler scheduler;
    private static Scoreboard scoreboard = null;

    static Set<ChampionshipTeam> newTransientTeamRegistry() {
        return Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    public TeamManager(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
        ScoreboardManager scoreboardManager = Bukkit.getScoreboardManager();
        if (scoreboardManager != null)
            scoreboard = scoreboardManager.getMainScoreboard();
        scheduler = championshipsCore.getServer().getScheduler();
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
                team.color(Utils.toNamedTextColor(colorName, colorCode));
            } catch (Exception ignored) {
            }

            for (UUID uuid : members) {
                String playerName = plugin.getPlayerManager().getPlayerName(uuid);
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
            if (name.isBlank() || name.length() > 64 || name.chars().anyMatch(Character::isISOControl)) return false;
            if (cachedTeams.values().stream().anyMatch(team -> team.getName().equalsIgnoreCase(name))) return false;

            if (Arrays.stream(Utils.getColorNames()).noneMatch(colorName::equalsIgnoreCase)) return false;
            if (cachedTeams.values().stream().anyMatch(team -> team.getColorName().equalsIgnoreCase(colorName))) return false;

            int id = teamDaoImpl.addTeam(name, colorName, colorCode);
            if (id < 0) return false;

            Team team = scoreboard.getTeam(colorName);
            if (team != null) {
                team.unregister();
            }
            team = scoreboard.registerNewTeam(colorName);

            try {
                team.color(Utils.toNamedTextColor(colorName, colorCode));
            } catch (Exception ignored) {
            }

            ChampionshipTeam championshipTeam = new ChampionshipTeam(id, name, colorName, colorCode, team);
            cachedTeams.put(name, championshipTeam);
            return true;
        }
    }

    @Override
    public void load() {
        scheduler.runTaskAsynchronously(plugin, () -> {
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
        });
    }

    @Override
    public void unload() {
        List<ChampionshipTeam> teams;
        synchronized (transientTeams) {
            teams = new ArrayList<>(transientTeams);
        }
        removeTransientTeams(teams);
        restoreDailyLobbyIdentities();
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
        if (championshipTeam == null) return false;

        if (plugin.getGameManager().getTeamCurrenArea(championshipTeam) != null)
            return false;

        championshipTeam = cachedTeams.remove(name);
        if (championshipTeam == null) return false;
        championshipTeam.getTeam().unregister();
        int id = championshipTeam.getId();

        scheduler.runTaskAsynchronously(plugin, () -> {
            teamDaoImpl.deleteTeam(id);
            teamDaoImpl.deleteTeamMembers(id);
        });
        return true;
    }

    @Nullable
    public ChampionshipTeam getTeamByPlayer(@NotNull UUID uuid) {
        ChampionshipTeam transientTeam = transientTeamByPlayer.get(uuid);
        if (transientTeam != null) return transientTeam;
        for (ChampionshipTeam championshipTeam : cachedTeams.values()) {
            for (UUID playerUUID : championshipTeam.getMembers()) {
                if (playerUUID.equals(uuid)) return championshipTeam;
            }
        }
        return null;
    }

    /**
     * Creates a scoreboard-backed runtime team for one DAILY session. The team is visible through
     * normal player-to-team lookup so existing game code needs no parallel roster implementation,
     * while formal team iteration and all DAO operations remain isolated.
     */
    public synchronized @NotNull ChampionshipTeam createTransientTeam(
            @NotNull String scoreboardId, @NotNull String displayName,
            @NotNull String colorName, @NotNull String colorCode, @NotNull Set<UUID> members) {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Transient teams must be created on the server thread");
        if (members.isEmpty()) throw new IllegalArgumentException("Transient team must have members");
        if (scoreboardId.length() > 16 || !scoreboardId.matches("[A-Za-z0-9_]+"))
            throw new IllegalArgumentException("Invalid transient scoreboard id: " + scoreboardId);
        for (UUID member : members) {
            if (transientTeamByPlayer.containsKey(member))
                throw new IllegalStateException("Player already belongs to a transient team: " + member);
        }

        Team scoreboardTeam = scoreboard.getTeam(scoreboardId);
        if (scoreboardTeam != null) scoreboardTeam.unregister();
        scoreboardTeam = scoreboard.registerNewTeam(scoreboardId);
        try {
            scoreboardTeam.color(Utils.toNamedTextColor(colorName, colorCode));
        } catch (RuntimeException ignored) {
        }
        scoreboardTeam.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);

        for (UUID member : members) {
            String playerName = plugin.getPlayerManager().getPlayerName(member);
            if (playerName == null) {
                OfflinePlayer offline = Bukkit.getOfflinePlayer(member);
                playerName = offline.getName();
            }
            if (playerName == null) continue;
            Team previous = scoreboard.getEntryTeam(playerName);
            if (previous != null) {
                String originalName = previous.getName();
                if (DAILY_LOBBY_TEAM_ID.equals(originalName)) {
                    originalName = dailyLobbyOriginalTeamByPlayer.getOrDefault(member, NO_SCOREBOARD_TEAM);
                }
                originalScoreboardTeamByPlayer.put(member, originalName);
            }
            scoreboardTeam.addEntry(playerName);
        }

        int id = scoreboardId.hashCode();
        if (id >= 0) id = -id - 1;
        ChampionshipTeam team = new ChampionshipTeam(id, displayName, colorName, colorCode,
                new LinkedHashSet<>(members), scoreboardTeam);
        transientTeams.add(team);
        for (UUID member : members) transientTeamByPlayer.put(member, team);
        return team;
    }

    /**
     * DAILY's lobby is presentation-neutral. This scoreboard-only team makes overhead and vanilla
     * system names explicitly white without turning lobby players into gameplay participants.
     */
    public synchronized void applyDailyLobbyIdentity(@NotNull Player player) {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Daily lobby identity must be updated on the server thread");
        Team lobbyTeam = scoreboard.getTeam(DAILY_LOBBY_TEAM_ID);
        if (lobbyTeam == null) lobbyTeam = scoreboard.registerNewTeam(DAILY_LOBBY_TEAM_ID);
        lobbyTeam.color(net.kyori.adventure.text.format.NamedTextColor.WHITE);

        String playerName = player.getName();
        Team current = scoreboard.getEntryTeam(playerName);
        if (current == lobbyTeam) return;
        if (current != null) {
            dailyLobbyOriginalTeamByPlayer.compute(player.getUniqueId(), (ignored, original) ->
                    original == null || NO_SCOREBOARD_TEAM.equals(original) ? current.getName() : original);
        } else {
            dailyLobbyOriginalTeamByPlayer.putIfAbsent(player.getUniqueId(), NO_SCOREBOARD_TEAM);
        }
        lobbyTeam.addEntry(playerName);
    }

    public synchronized void clearDailyLobbyIdentity(@NotNull Player player) {
        restoreDailyLobbyIdentity(player.getUniqueId(), player.getName());
    }

    public synchronized void restoreDailyLobbyIdentities() {
        for (UUID playerId : Set.copyOf(dailyLobbyOriginalTeamByPlayer.keySet())) {
            String playerName = plugin.getPlayerManager().getPlayerName(playerId);
            if (playerName == null) playerName = Bukkit.getOfflinePlayer(playerId).getName();
            if (playerName != null) restoreDailyLobbyIdentity(playerId, playerName);
            else dailyLobbyOriginalTeamByPlayer.remove(playerId);
        }
        Team lobbyTeam = scoreboard.getTeam(DAILY_LOBBY_TEAM_ID);
        if (lobbyTeam != null && dailyLobbyOriginalTeamByPlayer.isEmpty()) {
            try {
                lobbyTeam.unregister();
            } catch (IllegalStateException ignored) {
            }
        }
    }

    private void restoreDailyLobbyIdentity(UUID playerId, String playerName) {
        String originalName = dailyLobbyOriginalTeamByPlayer.remove(playerId);
        if (originalName == null) return;
        Team lobbyTeam = scoreboard.getTeam(DAILY_LOBBY_TEAM_ID);
        Team current = scoreboard.getEntryTeam(playerName);
        if (current != lobbyTeam) return;
        if (lobbyTeam != null) lobbyTeam.removeEntry(playerName);
        if (!NO_SCOREBOARD_TEAM.equals(originalName)) {
            Team original = scoreboard.getTeam(originalName);
            if (original != null) original.addEntry(playerName);
        }
    }

    /** Removes a DAILY team and restores each player's exact pre-session scoreboard team when possible. */
    public synchronized void removeTransientTeam(@NotNull ChampionshipTeam team) {
        boolean registered = transientTeams.remove(team);
        Set<UUID> indexedMembers = new LinkedHashSet<>();
        transientTeamByPlayer.forEach((member, indexedTeam) -> {
            if (indexedTeam == team) indexedMembers.add(member);
        });
        if (!registered && indexedMembers.isEmpty()) return;

        Set<UUID> members = new LinkedHashSet<>(team.getMembers());
        members.addAll(indexedMembers);
        Team temporary = team.getTeam();
        for (UUID member : indexedMembers) {
            ChampionshipTeam indexedTeam = transientTeamByPlayer.get(member);
            if (indexedTeam == team) transientTeamByPlayer.remove(member, indexedTeam);
        }
        if (temporary != null) {
            try {
                temporary.unregister();
            } catch (IllegalStateException ignored) {
            }
        }
        for (UUID member : members) {
            String originalName = originalScoreboardTeamByPlayer.remove(member);
            Team original = originalName == null ? null : scoreboard.getTeam(originalName);
            String playerName = plugin.getPlayerManager().getPlayerName(member);
            if (playerName == null) playerName = Bukkit.getOfflinePlayer(member).getName();
            if (original != null && playerName != null) {
                try {
                    original.addEntry(playerName);
                } catch (IllegalStateException ignored) {
                }
            }
        }
    }

    /** Cleans every transient team from one match even if an individual scoreboard restoration fails. */
    public synchronized void removeTransientTeams(@NotNull Collection<ChampionshipTeam> teams) {
        for (ChampionshipTeam team : List.copyOf(teams)) {
            try {
                removeTransientTeam(team);
            } catch (RuntimeException exception) {
                plugin.getLogger().warning(Utils.formatModuleLog("Daily", "临时队伍清理",
                        team.getName() + " | " + exception.getMessage()));
            }
        }
    }

    /** Shrinks a running transient team after voluntary departure and restores formal scoreboard entries. */
    public synchronized void removeTransientMembers(@NotNull ChampionshipTeam team, @NotNull Set<UUID> members) {
        if (!transientTeams.contains(team)) return;
        for (UUID member : members) {
            boolean rosterMember = team.deleteMember(member);
            ChampionshipTeam indexedTeam = transientTeamByPlayer.get(member);
            boolean indexedMember = indexedTeam == team;
            if (indexedMember) transientTeamByPlayer.remove(member, indexedTeam);
            if (!rosterMember && !indexedMember) continue;
            String playerName = plugin.getPlayerManager().getPlayerName(member);
            if (playerName == null) playerName = Bukkit.getOfflinePlayer(member).getName();
            if (team.getTeam() != null && playerName != null) team.getTeam().removeEntry(playerName);
            String originalName = originalScoreboardTeamByPlayer.remove(member);
            Team original = originalName == null ? null : scoreboard.getTeam(originalName);
            if (original != null && playerName != null) {
                try {
                    original.addEntry(playerName);
                } catch (IllegalStateException ignored) {
                }
            }
        }
    }

    public synchronized boolean isTransientTeam(@Nullable ChampionshipTeam team) {
        return team != null && transientTeams.contains(team);
    }

    /** Formal lookup that deliberately ignores DAILY's runtime overlay. */
    @Nullable
    public ChampionshipTeam getFormalTeamByPlayer(@NotNull UUID uuid) {
        for (ChampionshipTeam championshipTeam : cachedTeams.values()) {
            if (championshipTeam.isTeamMember(uuid)) return championshipTeam;
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

    public void applyIdentityMigration(@NotNull PlayerIdentityMigrationResult migration) {
        if (!migration.successful()) return;

        synchronized (cachedTeams) {
            Set<UUID> aliases = new HashSet<>(migration.previousUuids());
            aliases.add(migration.currentUuid());
            for (ChampionshipTeam team : cachedTeams.values()) {
                for (UUID alias : aliases) {
                    team.deleteMember(alias);
                }
                team.getTeam().removeEntry(migration.username());
            }

            if (migration.hasTeamConflict() || migration.resolvedTeamId() == null) return;

            ChampionshipTeam resolvedTeam = getTeamById(migration.resolvedTeamId());
            if (resolvedTeam == null) {
                plugin.getLogger().warning(Utils.formatModuleLog("Team", "UUIDMigration",
                        "玩家=" + migration.username() + " 已迁移至队伍ID=" + migration.resolvedTeamId()
                                + "，但当前缓存中不存在该队伍"));
                return;
            }
            resolvedTeam.addMember(migration.currentUuid());
            resolvedTeam.getTeam().addEntry(migration.username());
        }

        Player player = Bukkit.getPlayer(migration.currentUuid());
        if (player != null)
            plugin.getGameManager().leaveSpectating(player);
        else
            plugin.getGameManager().removeSpectatingPlayerFromList(migration.currentUuid());
    }

    @Nullable
    private ChampionshipTeam getTeamById(int teamId) {
        for (ChampionshipTeam team : cachedTeams.values()) {
            if (team.getId() == teamId) return team;
        }
        return null;
    }

    private boolean addTeamMember(@NotNull UUID uuid, @NotNull String username, String teamName) {
        synchronized (cachedTeams) {
            ChampionshipTeam championshipTeam = getTeam(teamName);
            if (championshipTeam == null) return false;

            if (championshipTeam.getMembers().size() >= CCConfig.TEAM_MAX_MEMBERS) return false;

            Set<TeamMemberEntry> sameNameMembers = teamDaoImpl.getTeamMembers(username);
            if (sameNameMembers == null) {
                plugin.getLogger().warning(Utils.formatModuleLog("Team", "MemberConflict",
                        "拒绝添加玩家=" + username + " UUID=" + uuid + "：无法完成同名冲突检查"));
                return false;
            }
            if (!sameNameMembers.isEmpty()) {
                plugin.getLogger().warning(Utils.formatModuleLog("Team", "MemberConflict",
                        "拒绝添加玩家=" + username + " UUID=" + uuid + "：数据库中已有同名队伍记录="
                                + sameNameMembers.stream().map(TeamMemberEntry::getTeamId).collect(java.util.stream.Collectors.toSet())));
                return false;
            }

            for (ChampionshipTeam cachedChampionshipTeam : cachedTeams.values()) {
                for (UUID memberUUID : cachedChampionshipTeam.getMembers()) {
                    if (memberUUID.equals(uuid)) return false;
                }
            }

            if (!teamDaoImpl.addTeamMember(championshipTeam.getId(), uuid, username)) return false;

            championshipTeam.getTeam().addEntry(username);
            championshipTeam.addMember(uuid);
            Player player = Bukkit.getPlayer(uuid);
            if (player != null)
                plugin.getGameManager().leaveSpectating(player);
            else
                plugin.getGameManager().removeSpectatingPlayerFromList(uuid);
            plugin.getVisibilityManager().reconcilePlayer(uuid);
            return true;
        }
    }

    public boolean addTeamMember(@NotNull String username, @NotNull String teamName) {
        if (!username.matches("[A-Za-z0-9_]{1,16}")) return false;
        return addTeamMember(plugin.getPlayerManager().getPlayerUUID(username), username, teamName);
    }

    public boolean addTeamMember(@NotNull String username, @NotNull ChampionshipTeam championshipTeam) {
        return addTeamMember(username, championshipTeam.getName());
    }

    public MemberMoveResult moveTeamMember(@NotNull UUID uuid, @NotNull String username,
                                           @NotNull ChampionshipTeam targetTeam) {
        if (!username.matches("[A-Za-z0-9_]{1,16}")) return MemberMoveResult.INVALID_PLAYER;
        synchronized (cachedTeams) {
            ChampionshipTeam currentTeam = getTeamByPlayer(uuid);
            if (currentTeam != null && currentTeam.equals(targetTeam)) return MemberMoveResult.SAME_TEAM;
            if (targetTeam.getMembers().size() >= CCConfig.TEAM_MAX_MEMBERS) return MemberMoveResult.TARGET_FULL;
            if (plugin.getGameManager().getTeamCurrenArea(targetTeam) != null
                    || currentTeam != null && plugin.getGameManager().getTeamCurrenArea(currentTeam) != null) {
                return MemberMoveResult.TEAM_ACTIVE;
            }
            if (!teamDaoImpl.moveTeamMember(targetTeam.getId(), uuid, username)) return MemberMoveResult.FAILED;

            for (ChampionshipTeam team : cachedTeams.values()) {
                team.deleteMember(uuid);
                team.getTeam().removeEntry(username);
            }
            targetTeam.addMember(uuid);
            targetTeam.getTeam().addEntry(username);
            Player player = Bukkit.getPlayer(uuid);
            if (player != null)
                plugin.getGameManager().leaveSpectating(player);
            else
                plugin.getGameManager().removeSpectatingPlayerFromList(uuid);
            plugin.getVisibilityManager().reconcilePlayer(uuid);
            return MemberMoveResult.SUCCESS;
        }
    }

    public enum MemberMoveResult {
        SUCCESS,
        SAME_TEAM,
        TARGET_FULL,
        TEAM_ACTIVE,
        INVALID_PLAYER,
        FAILED
    }

    public boolean deleteTeamMember(@NotNull String username, @NotNull String teamName) {
        ChampionshipTeam championshipTeam = getTeam(teamName);
        if (championshipTeam == null) return false;
        Set<TeamMemberEntry> matchingMembers = teamDaoImpl.getTeamMembers(championshipTeam.getId()).stream()
                .filter(member -> member.getUsername().equalsIgnoreCase(username))
                .collect(java.util.stream.Collectors.toSet());
        if (matchingMembers.isEmpty()) return false;

        if (!teamDaoImpl.deleteTeamMembers(championshipTeam.getId(), username)) return false;

        synchronized (cachedTeams) {
            for (TeamMemberEntry member : matchingMembers) {
                championshipTeam.deleteMember(member.getUuid());
            }
            championshipTeam.getTeam().removeEntry(username);
        }
        for (TeamMemberEntry member : matchingMembers)
            plugin.getVisibilityManager().reconcilePlayer(member.getUuid());
        return true;
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
        for (Team team : scoreboard.getTeams()) {
            if (collision) {
                team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.ALWAYS);
            } else {
                team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
            }
        }
    }
}
