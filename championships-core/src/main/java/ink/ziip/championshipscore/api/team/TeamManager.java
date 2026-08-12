package ink.ziip.championshipscore.api.team;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseManager;
import ink.ziip.championshipscore.api.player.entry.PlayerIdentityMigrationResult;
import ink.ziip.championshipscore.api.team.dao.TeamDaoImpl;
import ink.ziip.championshipscore.api.team.entry.TeamEntry;
import ink.ziip.championshipscore.api.team.entry.TeamMemberEntry;
import ink.ziip.championshipscore.configuration.config.CCConfig;
import ink.ziip.championshipscore.database.sync.DatabaseSyncDomain;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

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

    private record FormalTeamSnapshot(int id, String name, String colorName, String colorCode,
                                      Map<UUID, String> members) {
    }

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
            publishTeamChange("team-created");
            return true;
        }
    }

    @Override
    public void load() {
        refreshFormalTeamsFromDatabase().exceptionally(failure -> {
            plugin.getLogger().log(Level.SEVERE, "Unable to load formal teams from database", failure);
            return null;
        });
    }

    /** Reloads the authoritative formal-team snapshot without touching match-scoped DAILY teams. */
    public CompletionStage<Void> refreshFormalTeamsFromDatabase() {
        CompletableFuture<List<FormalTeamSnapshot>> query = new CompletableFuture<>();
        scheduler.runTaskAsynchronously(plugin, () -> {
            try {
                List<FormalTeamSnapshot> snapshots = new ArrayList<>();
                List<TeamEntry> entries = teamDaoImpl.getTeamListIfAvailable()
                        .orElseThrow(() -> new IllegalStateException("Unable to query formal team list"));
                for (TeamEntry entry : entries) {
                    Map<UUID, String> members = new LinkedHashMap<>();
                    Set<TeamMemberEntry> queriedMembers = teamDaoImpl.getTeamMembersIfAvailable(entry.getId())
                            .orElseThrow(() -> new IllegalStateException(
                                    "Unable to query members for team " + entry.getId()));
                    for (TeamMemberEntry member : queriedMembers)
                        members.put(member.getUuid(), member.getUsername());
                    snapshots.add(new FormalTeamSnapshot(entry.getId(), entry.getName(), entry.getColorName(),
                            entry.getColorCode(), Map.copyOf(members)));
                }
                query.complete(List.copyOf(snapshots));
            } catch (RuntimeException failure) {
                query.completeExceptionally(failure);
            }
        });
        return query.thenCompose(this::applyFormalTeamSnapshots);
    }

    private CompletionStage<Void> applyFormalTeamSnapshots(List<FormalTeamSnapshot> snapshots) {
        CompletableFuture<Void> applied = new CompletableFuture<>();
        Runnable apply = () -> {
            try {
                Set<UUID> changedMembers = new HashSet<>();
                synchronized (cachedTeams) {
                    Map<Integer, FormalTeamSnapshot> snapshotById = new HashMap<>();
                    for (FormalTeamSnapshot snapshot : snapshots) snapshotById.put(snapshot.id(), snapshot);

                    for (ChampionshipTeam cached : List.copyOf(cachedTeams.values())) {
                        FormalTeamSnapshot snapshot = snapshotById.get(cached.getId());
                        boolean metadataMatches = snapshot != null
                                && cached.getName().equals(snapshot.name())
                                && cached.getColorName().equals(snapshot.colorName())
                                && cached.getColorCode().equals(snapshot.colorCode());
                        if (metadataMatches) continue;
                        // Running games retain their exact team object until the next reconciliation.
                        if (plugin.getGameManager().getTeamCurrenArea(cached) != null) continue;
                        cachedTeams.remove(cached.getName(), cached);
                        changedMembers.addAll(cached.getMembers());
                        unregister(cached.getTeam());
                    }

                    for (FormalTeamSnapshot snapshot : snapshots) {
                        ChampionshipTeam team = getTeamById(snapshot.id());
                        if (team == null) {
                            team = createFormalTeam(snapshot);
                            cachedTeams.put(snapshot.name(), team);
                            changedMembers.addAll(snapshot.members().keySet());
                        }
                        reconcileFormalMembers(team, snapshot.members(), changedMembers);
                    }
                }
                for (UUID playerId : changedMembers) plugin.getVisibilityManager().reconcilePlayer(playerId);
                applied.complete(null);
            } catch (RuntimeException failure) {
                applied.completeExceptionally(failure);
            }
        };
        if (Bukkit.isPrimaryThread()) apply.run();
        else scheduler.runTask(plugin, apply);
        return applied;
    }

    private ChampionshipTeam createFormalTeam(FormalTeamSnapshot snapshot) {
        Team scoreboardTeam = scoreboard.getTeam(snapshot.colorName());
        if (scoreboardTeam != null) unregister(scoreboardTeam);
        scoreboardTeam = scoreboard.registerNewTeam(snapshot.colorName());
        try {
            scoreboardTeam.color(Utils.toNamedTextColor(snapshot.colorName(), snapshot.colorCode()));
        } catch (RuntimeException ignored) {
        }
        return new ChampionshipTeam(snapshot.id(), snapshot.name(), snapshot.colorName(), snapshot.colorCode(),
                snapshot.members().keySet(), scoreboardTeam);
    }

    private void reconcileFormalMembers(ChampionshipTeam team, Map<UUID, String> authoritative,
                                        Set<UUID> changedMembers) {
        Set<UUID> cachedMembers = team.getMembers();
        for (UUID member : cachedMembers) {
            if (!authoritative.containsKey(member)) {
                team.deleteMember(member);
                changedMembers.add(member);
            }
        }
        for (UUID member : authoritative.keySet()) {
            if (team.addMember(member)) changedMembers.add(member);
        }

        Team scoreboardTeam = team.getTeam();
        for (String entry : Set.copyOf(scoreboardTeam.getEntries())) scoreboardTeam.removeEntry(entry);
        authoritative.forEach((uuid, username) -> {
            if (!transientTeamByPlayer.containsKey(uuid)) scoreboardTeam.addEntry(username);
        });
    }

    private static void unregister(Team team) {
        if (team == null) return;
        try {
            team.unregister();
        } catch (IllegalStateException ignored) {
        }
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
        ChampionshipTeam exact = cachedTeams.get(name);
        if (exact != null) return exact;
        return findTeam(cachedTeams.values(), name);
    }

    /**
     * Resolves administrator-facing team selectors by display name (case-insensitive) or numeric
     * database id. Most commands tab-complete the display name, while accepting the id keeps the
     * historical {@code <队伍ID>} command syntax truthful.
     */
    static @Nullable ChampionshipTeam findTeam(@NotNull Collection<ChampionshipTeam> teams,
                                                @NotNull String selector) {
        for (ChampionshipTeam team : teams) {
            if (team.getName().equalsIgnoreCase(selector)
                    || Integer.toString(team.getId()).equals(selector)) {
                return team;
            }
        }
        return null;
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
            publishTeamChange("team-deleted");
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
            publishTeamChange("team-member-added");
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
            publishTeamChange("team-member-moved");
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
        publishTeamChange("team-member-deleted");
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

    private void publishTeamChange(String reason) {
        plugin.getRedisManager().publishDatabaseChange(reason, DatabaseSyncDomain.TEAM);
    }
}
