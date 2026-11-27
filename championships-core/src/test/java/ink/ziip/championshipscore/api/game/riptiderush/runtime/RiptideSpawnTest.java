package ink.ziip.championshipscore.api.game.riptiderush.runtime;

import static ink.ziip.championshipscore.support.TestObjects.allocate;
import static ink.ziip.championshipscore.support.TestObjects.set;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.api.game.riptiderush.support.RiptideTestFixtures;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Proxy;
import java.util.*;

class RiptideSpawnTest {
    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4})
    void teamsUseSeparateMemberPositionsAndRetainAssignmentsForReturningPlayers(int teamSize)
            throws Exception {

        var area = allocate(RiptideRushArea.class);
        var config = RiptideTestFixtures.config();
        var g = config.resolveGeometry();
        var players = new LinkedHashMap<UUID, Player>();
        var collision = new HashMap<UUID, Boolean>();
        var original = new HashMap<UUID, Boolean>();
        var targets = new HashMap<UUID, Location>();
        var scoreboard =
                (Scoreboard)
                        Proxy.newProxyInstance(
                                Scoreboard.class.getClassLoader(),
                                new Class<?>[] {Scoreboard.class},
                                (proxy, method, args) -> {
                                    if (method.getName().equals("getEntryTeam")) return null;
                                    throw new AssertionError(method.getName());
                                });
        for (int i = 0; i < teamSize * 2; i++) {
            UUID id = new UUID(0, i + 1);
            collision.put(id, i != 0);
            original.put(id, i != 0);
            players.put(
                    id,
                    (Player)
                            Proxy.newProxyInstance(
                                    Player.class.getClassLoader(),
                                    new Class<?>[] {Player.class},
                                    (proxy, method, args) ->
                                            switch (method.getName()) {
                                                case "getUniqueId" -> id;
                                                case "getName" -> id.toString();
                                                case "getScoreboard" -> scoreboard;
                                                case "isCollidable" -> collision.get(id);
                                                case "setCollidable" -> {
                                                    collision.put(id, (Boolean) args[0]);
                                                    yield null;
                                                }
                                                case "setVelocity" -> {
                                                    assertEquals(
                                                            new org.bukkit.util.Vector(), args[0]);
                                                    yield null;
                                                }
                                                case "setFallDistance" -> {
                                                    assertEquals(0F, args[0]);
                                                    yield null;
                                                }
                                                case "teleport" -> {
                                                    assertFalse(
                                                            collision.get(id),
                                                            "Collision must be disabled before"
                                                                    + " teleport");
                                                    targets.put(id, ((Location) args[0]).clone());
                                                    yield true;
                                                }
                                                default ->
                                                        throw new AssertionError(method.getName());
                                            }));
        }
        var roster = new ArrayList<>(players.keySet());
        var teams = new ArrayList<ChampionshipTeam>();
        var expected = new HashMap<UUID, Location>();
        var positions =
                List.of(
                        g.centerAt(0).add(-2D, 0D, -2D),
                        g.centerAt(0).add(2D, 0D, -2D),
                        g.centerAt(0).add(-2D, 0D, 2D),
                        g.centerAt(0).add(2D, 0D, 2D));
        for (int teamIndex = 0; teamIndex < 2; teamIndex++) {
            var members = new HashSet<UUID>();
            members.add(new UUID(0, -1 - teamIndex)); // Not selected for this round.
            for (int index = 0; index < teamSize; index++) {
                UUID id = roster.get(index * 2 + teamIndex);
                members.add(id);
                expected.put(id, positions.get(index));
            }
            var team = allocate(ChampionshipTeam.class);
            set(team, "members", members);
            teams.add(team);
        }
        set(area, "gamePlayers", roster);
        set(area, "gameTeams", teams);
        set(area, "gameConfig", config);
        set(area, "geometry", g);
        var spawns = new HashMap<UUID, Location>();
        set(area, "playerSpawnLocations", spawns);
        var saved = new HashMap<UUID, Boolean>();
        set(area, "spawnCollisionStates", saved);
        set(area, "teamCollisions", new RiptideTeamCollisions());
        var serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        Object previous = serverField.get(null);
        var online = new HashMap<>(players);
        UUID returning = roster.getFirst();
        online.remove(returning);
        serverField.set(
                null,
                Proxy.newProxyInstance(
                        Server.class.getClassLoader(),
                        new Class<?>[] {Server.class},
                        (proxy, method, args) -> {
                            if (method.getName().equals("getPlayer")) return online.get(args[0]);
                            throw new AssertionError(method.getName());
                        }));
        try {
            var assign = RiptideRushArea.class.getDeclaredMethod("assignAndTeleportSpawns");
            assign.setAccessible(true);
            assign.invoke(area);
            assertEquals(expected, spawns, "Offline participants also reserve a personal spawn");
            assertFalse(targets.containsKey(returning));
            online.put(returning, players.get(returning));
            area.teleportPlayerToSpawnPoint(players.get(returning));
            assertEquals(expected, targets);
            Collections.reverse(roster);
            Collections.reverse(teams);
            assign.invoke(area);
            assertEquals(expected, targets, "Roster iteration order must not change assignments");
            assertEquals(targets, spawns);
            assertEquals(
                    original,
                    saved,
                    "Repeated preparation must retain the original collision state");
            targets.clear();
            for (Player player : players.values()) area.teleportPlayerToSpawnPoint(player);
            assertEquals(expected, targets, "Respawns return to the assigned deck positions");
            var restore =
                    RiptideRushArea.class.getDeclaredMethod("restoreSpawnCollision", UUID.class);
            restore.setAccessible(true);
            for (UUID id : players.keySet()) restore.invoke(area, id);
            assertEquals(original, collision);
            assertTrue(saved.isEmpty());
        } finally {
            serverField.set(null, previous);
        }
    }
}
