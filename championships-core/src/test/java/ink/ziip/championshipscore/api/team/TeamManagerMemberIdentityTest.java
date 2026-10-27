package ink.ziip.championshipscore.api.team;

import static ink.ziip.championshipscore.support.TestObjects.*;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.instance.BaseGameInstance;
import ink.ziip.championshipscore.api.game.manager.GameManager;
import ink.ziip.championshipscore.api.player.PlayerManager;
import ink.ziip.championshipscore.api.player.identity.PlayerUuidLookupException;
import ink.ziip.championshipscore.api.team.entry.TeamMemberEntry;
import ink.ziip.championshipscore.api.visibility.PlayerVisibilityManager;
import ink.ziip.championshipscore.configuration.ConfigurationStateExtension;
import ink.ziip.championshipscore.configuration.config.CCConfig;
import ink.ziip.championshipscore.database.team.TeamDaoImpl;
import ink.ziip.championshipscore.redis.RedisManager;
import ink.ziip.championshipscore.util.concurrent.LifecycleTasks;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scoreboard.Team;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@ExtendWith(ConfigurationStateExtension.class)
class TeamManagerMemberIdentityTest {
    private static class Players extends PlayerManager {
        CompletableFuture<UUID> result;
        UUID expected;
        int calls;

        Players() {
            super(null);
        }

        @Override
        public CompletionStage<UUID> resolvePlayerUUID(String name, UUID selected) {
            calls++;
            expected = selected;
            return result;
        }
    }

    private static class Games extends GameManager {
        Games() {
            super(null);
        }

        @Override
        public BaseGameInstance getTeamCurrenArea(ChampionshipTeam team) {
            return null;
        }

        @Override
        public boolean leaveSpectating(Player player) {
            return false;
        }

        @Override
        public void removeSpectatingPlayerFromList(UUID player) {}
    }

    private static class Dao extends TeamDaoImpl {
        UUID written;
        int teamId;
        int writes;
        boolean succeeds = true;

        @Override
        public Set<TeamMemberEntry> getTeamMembers(String name) {
            return Set.of();
        }

        private boolean write(int team, UUID uuid) {
            writes++;
            teamId = team;
            written = uuid;
            return succeeds;
        }

        @Override
        public boolean addTeamMember(int team, UUID uuid, String name) {
            return write(team, uuid);
        }

        @Override
        public boolean deleteTeamMember(int team, UUID uuid) {
            return write(team, uuid);
        }

        @Override
        public boolean moveTeamMember(int team, UUID uuid, String name) {
            return write(team, uuid);
        }
    }

    private static final class Fixture implements AutoCloseable {
        final Object previous = field(Bukkit.class, "server").get(null);
        final TeamManager manager = allocate(TeamManager.class);
        final Players players = allocate(Players.class);
        final Dao dao = new Dao();
        final Map<String, ChampionshipTeam> teams = new ConcurrentHashMap<>();
        final Map<UUID, Player> online = new ConcurrentHashMap<>();
        final Map<Integer, Set<String>> entries = new HashMap<>();
        final LifecycleTasks tasks = new LifecycleTasks("team-identity-test", 16);

        Fixture() throws Exception {
            CCConfig.TEAM_MAX_MEMBERS = 10;
            players.result = new CompletableFuture<>();
            var scheduler =
                    proxy(
                            BukkitScheduler.class,
                            (p, m, a) -> {
                                if (!m.getName().equals("runTask"))
                                    throw new AssertionError(m.getName());
                                ((Runnable) a[1]).run();
                                return null;
                            });
            var server =
                    proxy(
                            Server.class,
                            (p, m, a) ->
                                    switch (m.getName()) {
                                        case "getPlayer" -> online.get(a[0]);
                                        case "getOnlinePlayers" -> online.values();
                                        default -> throw new AssertionError(m.getName());
                                    });
            field(Bukkit.class, "server").set(null, server);
            var core = allocate(ChampionshipsCore.class);
            set(core, "server", server);
            set(core, "playerManager", players);
            set(core, "gameManager", allocate(Games.class));
            set(core, "visibilityManager", allocate(PlayerVisibilityManager.class));
            var redis = allocate(RedisManager.class);
            set(redis, "stopping", new AtomicBoolean(true));
            set(core, "redisManager", redis);
            set(manager, "plugin", core);
            set(manager, "cachedTeams", teams);
            set(manager, "transientTeamByPlayer", new ConcurrentHashMap<>());
            set(manager, "pendingMemberNames", ConcurrentHashMap.newKeySet());
            set(manager, "pendingMemberTeamIds", ConcurrentHashMap.newKeySet());
            set(manager, "pendingTeamDeletions", ConcurrentHashMap.newKeySet());
            set(manager, "teamTasks", tasks);
            set(manager, "scheduler", scheduler);
            set(manager, "teamDaoImpl", dao);
            tasks.start();
        }

        ChampionshipTeam team(int id, String name, Map<UUID, String> members) {
            Set<String> nativeEntries = ConcurrentHashMap.newKeySet();
            nativeEntries.addAll(members.values());
            entries.put(id, nativeEntries);
            Team nativeTeam =
                    proxy(
                            Team.class,
                            (p, m, a) ->
                                    switch (m.getName()) {
                                        case "addEntry" -> nativeEntries.add((String) a[0]);
                                        case "removeEntry" -> nativeEntries.remove(a[0]);
                                        default -> throw new AssertionError(m.getName());
                                    });
            var team = new ChampionshipTeam(id, name, "red", "&c", members, nativeTeam);
            teams.put(name, team);
            return team;
        }

        void online(UUID id, String name) {
            online.put(
                    id,
                    proxy(
                            Player.class,
                            (p, m, a) ->
                                    switch (m.getName()) {
                                        case "getUniqueId" -> id;
                                        case "getName" -> name;
                                        default -> throw new AssertionError(m.getName());
                                    }));
        }

        @Override
        public void close() throws Exception {
            tasks.close();
            field(Bukkit.class, "server").set(null, previous);
        }
    }

    private static <T> T await(CompletionStage<T> result) throws Exception {
        return result.toCompletableFuture().get(5, TimeUnit.SECONDS);
    }

    @Test
    void selectedUuidSurvivesLookupAndAcceptedAdd() throws Exception {
        try (var f = new Fixture()) {
            UUID id = UUID.randomUUID();
            var team = f.team(1, "Target", Map.of());
            var result = f.manager.addTeamMember(id, "PlayerOne", team);
            assertEquals(id, f.players.expected);
            assertTrue(f.manager.isMutationPending(team));
            f.players.result.complete(id);
            assertEquals(TeamManager.MemberAddResult.ADDED, await(result));
            assertEquals(id, f.dao.written);
            assertEquals(1, f.dao.teamId);
            assertTrue(team.getMembers().contains(id));
            assertEquals(Set.of("PlayerOne"), f.entries.get(1));
            assertFalse(f.manager.isMutationPending(team));
        }
    }

    @Test
    void sameNameReplacementTeamIsRejectedBeforeLookupAndAfterLookup() throws Exception {
        try (var f = new Fixture()) {
            UUID id = UUID.randomUUID();
            var old = f.team(1, "Target", Map.of());
            var result = f.manager.addTeamMember(id, "PlayerOne", old);
            var replacement = f.team(2, "Target", Map.of());
            f.players.result.complete(id);
            assertEquals(TeamManager.MemberAddResult.TEAM_NOT_FOUND, await(result));
            assertEquals(
                    TeamManager.MemberAddResult.TEAM_NOT_FOUND,
                    await(f.manager.addTeamMember("PlayerTwo", old)));
            assertEquals(1, f.players.calls);
            assertEquals(0, f.dao.writes);
            assertTrue(replacement.getMembers().isEmpty());
            assertFalse(f.manager.isMutationPending(old));
        }
    }

    @Test
    void identityConflictReleasesReservationWithoutWriting() throws Exception {
        try (var f = new Fixture()) {
            var team = f.team(1, "Target", Map.of());
            UUID id = UUID.randomUUID();
            var result = f.manager.addTeamMember(id, "PlayerOne", team);
            f.players.result.completeExceptionally(
                    new PlayerUuidLookupException(
                            PlayerUuidLookupException.Reason.IDENTITY_CONFLICT, "changed"));
            assertEquals(TeamManager.MemberAddResult.IDENTITY_CONFLICT, await(result));
            assertEquals(0, f.dao.writes);
            assertFalse(f.manager.isMutationPending(team));
            f.players.result = CompletableFuture.completedFuture(id);
            assertEquals(
                    TeamManager.MemberAddResult.ADDED,
                    await(f.manager.addTeamMember(id, "PlayerOne", team)));
        }
    }

    @Test
    void lateLookupCannotWriteAfterLifetimeEnds() throws Exception {
        try (var f = new Fixture()) {
            UUID id = UUID.randomUUID();
            var team = f.team(1, "Target", Map.of());
            var result = f.manager.addTeamMember(id, "PlayerOne", team).toCompletableFuture();
            f.tasks.close();
            f.players.result.complete(id);
            assertTrue(result.isCancelled());
            assertEquals(0, f.dao.writes);
            assertTrue(team.getMembers().isEmpty());
        }
    }

    @Test
    void removalUsesOriginalTeamAndUuidEvenAfterNameChanges() throws Exception {
        try (var f = new Fixture()) {
            UUID selected = UUID.randomUUID(), other = UUID.randomUUID();
            var team = f.team(1, "Target", Map.of(selected, "Renamed", other, "PlayerOne"));
            assertTrue(await(f.manager.deleteTeamMember(selected, team)));
            assertEquals(selected, f.dao.written);
            assertEquals(1, f.dao.teamId);
            assertEquals(Set.of(other), team.getMembers());
            assertEquals(Set.of("PlayerOne"), f.entries.get(1));
            assertFalse(await(f.manager.deleteTeamMember(selected, team)));
            assertEquals(1, f.dao.writes);
            f.team(2, "Target", Map.of(other, "PlayerOne"));
            assertFalse(await(f.manager.deleteTeamMember(other, team)));
            assertEquals(1, f.dao.writes);
        }
    }

    @Test
    void ambiguousNameRemovalCannotChooseAnArbitraryUuid() throws Exception {
        try (var f = new Fixture()) {
            var team =
                    f.team(
                            1,
                            "Target",
                            Map.of(UUID.randomUUID(), "PlayerOne", UUID.randomUUID(), "playerone"));
            assertFalse(await(f.manager.deleteTeamMember("PLAYERONE", "Target")));
            assertEquals(0, f.dao.writes);
            assertEquals(2, team.getMembers().size());
        }
    }

    @Test
    void moveRequiresCurrentOnlineSelectionAndOriginalTargetTeam() throws Exception {
        try (var f = new Fixture()) {
            UUID id = UUID.randomUUID();
            var old = f.team(1, "Target", Map.of());
            assertEquals(
                    TeamManager.MemberMoveResult.INVALID_PLAYER,
                    await(f.manager.moveTeamMember(id, "PlayerOne", old)));
            f.online(id, "Renamed");
            assertEquals(
                    TeamManager.MemberMoveResult.INVALID_PLAYER,
                    await(f.manager.moveTeamMember(id, "PlayerOne", old)));
            f.team(2, "Target", Map.of());
            assertEquals(
                    TeamManager.MemberMoveResult.FAILED,
                    await(f.manager.moveTeamMember(id, "Renamed", old)));
            assertEquals(0, f.dao.writes);
        }
    }

    @Test
    void moveRemovesStoredOldNameAndPublishesOnlyAfterDatabaseSuccess() throws Exception {
        try (var f = new Fixture()) {
            UUID id = UUID.randomUUID();
            var source = f.team(1, "Source", Map.of(id, "OldName"));
            var target = f.team(2, "Target", Map.of());
            f.online(id, "NewName");
            f.dao.succeeds = false;
            assertEquals(
                    TeamManager.MemberMoveResult.FAILED,
                    await(f.manager.moveTeamMember(id, "NewName", target)));
            assertEquals(Set.of(id), source.getMembers());
            assertEquals(Set.of("OldName"), f.entries.get(1));
            assertTrue(target.getMembers().isEmpty());
            f.dao.succeeds = true;
            assertEquals(
                    TeamManager.MemberMoveResult.SUCCESS,
                    await(f.manager.moveTeamMember(id, "NewName", target)));
            assertTrue(source.getMembers().isEmpty());
            assertTrue(f.entries.get(1).isEmpty());
            assertEquals(Set.of(id), target.getMembers());
            assertEquals(Set.of("NewName"), f.entries.get(2));
            assertEquals(id, f.dao.written);
            assertEquals(2, f.dao.teamId);
            assertFalse(f.manager.isMutationPending(source));
            assertFalse(f.manager.isMutationPending(target));
        }
    }
}
