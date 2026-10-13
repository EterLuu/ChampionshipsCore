package ink.ziip.championshipscore.api.game.lasershowdown.runtime;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.lasershowdown.config.LaserShowdownConfig;
import ink.ziip.championshipscore.api.game.manager.GameManager;
import ink.ziip.championshipscore.api.game.model.GameRunMode;
import ink.ziip.championshipscore.api.game.model.GameStageEnum;
import ink.ziip.championshipscore.api.game.spectate.SpectatorManager;
import ink.ziip.championshipscore.api.game.start.ArenaSelection;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

class LaserShowdownArenaSelectionTest {
    @Test
    void unassignedRespawnsUseOnlySelectedPhysicalArenasAndSpectatorsCannotRespawn()
            throws Exception {
        var sf = field(Bukkit.class, "server");
        Object previous = sf.get(null);
        try {
            var plugin = allocate(ChampionshipsCore.class);
            set(plugin, "loaded", true);
            var game = allocate(GameManager.class);
            set(game, "plugin", plugin);
            set(plugin, "gameManager", game);
            for (String name :
                    List.of(
                            "playerStatus",
                            "teamStatus",
                            "playerSpectatorStatus",
                            "roundTransitionHolds",
                            "spectatorTransitionHolds")) set(game, name, new ConcurrentHashMap<>());
            set(game, "spectatorManager", new SpectatorManager(plugin, game));
            var area = allocate(LaserShowdownTeamArea.class);
            set(area, "plugin", plugin);
            set(area, "gameStageEnum", GameStageEnum.WAITING);
            UUID id = UUID.randomUUID();
            var spectator = new AtomicBoolean(false);
            List<Location> teleports = new ArrayList<>();
            Player player =
                    proxy(
                            Player.class,
                            (p, m, a) ->
                                    switch (m.getName()) {
                                        case "getUniqueId" -> id;
                                        case "getGameMode" ->
                                                spectator.get()
                                                        ? GameMode.SPECTATOR
                                                        : GameMode.ADVENTURE;
                                        case "teleport" -> {
                                            teleports.add(((Location) a[0]).clone());
                                            yield true;
                                        }
                                        default -> throw new AssertionError(m.getName());
                                    });
            World world =
                    proxy(
                            World.class,
                            (p, m, a) ->
                                    switch (m.getName()) {
                                        case "getName" -> "snow";
                                        case "equals" -> p == a[0];
                                        case "hashCode" -> System.identityHashCode(p);
                                        default -> throw new AssertionError(m.getName());
                                    });
            List<List<Location>> arenas = new ArrayList<>();
            for (int i = 0; i < 4; i++)
                arenas.add(
                        new ArrayList<>(
                                List.of(
                                        new Location(world, i * 100, 64, 1),
                                        new Location(world, i * 100 + 1, 64, 1))));
            set(area, "areaLocations", arenas);
            set(area, "gamePlayers", new ArrayList<>(List.of(id)));
            set(area, "playerRespawnLocations", new HashMap<>());
            set(area, "locationIterators", new IdentityHashMap<>());
            set(game, "playerStatus", new ConcurrentHashMap<>(Map.of(id, area)));
            Server server =
                    proxy(
                            Server.class,
                            (p, m, a) ->
                                    switch (m.getName()) {
                                        case "getPlayer" -> player;
                                        default -> throw new AssertionError(m.getName());
                                    });
            sf.set(null, server);
            for (String selection : List.of("3", "2,4")) {
                area.prepareArenaSelection(ArenaSelection.parse(selection));
                for (int i = 0; i < 100; i++) area.teleportPlayerToSpawnLocation(player);
                Set<Integer> expected = selection.equals("3") ? Set.of(2) : Set.of(1, 3);
                assertTrue(
                        teleports.stream().allMatch(l -> expected.contains(l.getBlockX() / 100)));
                teleports.clear();
            }
            set(area, "runMode", GameRunMode.GAME);
            area.prepareRunMode(GameRunMode.DAILY);
            List<Integer> dailyArena = area.getSelectedArenaIndices(4);
            assertEquals(1, dailyArena.size());
            for (int i = 0; i < 100; i++) area.teleportPlayerToSpawnLocation(player);
            assertTrue(
                    teleports.stream().allMatch(l -> dailyArena.contains(l.getBlockX() / 100)),
                    "unassigned/reconnecting DAILY players stay in the same arena");
            teleports.clear();
            set(area, "playerRespawnLocations", Map.of(id, arenas.get(dailyArena.getFirst())));
            for (int i = 0; i < 50; i++) area.teleportPlayerToSpawnLocation(player);
            assertTrue(
                    teleports.stream().allMatch(l -> dailyArena.contains(l.getBlockX() / 100)),
                    "assigned DAILY respawns use only the match arena");
            teleports.clear();
            area.prepareRunMode(GameRunMode.GAME);
            assertEquals(
                    List.of(0, 1, 2, 3),
                    area.getSelectedArenaIndices(4),
                    "failed DAILY starts must restore normal arena selection");
            area.prepareArenaSelection(ArenaSelection.parse("2,4"));
            area.prepareRunMode(GameRunMode.EVENT);
            assertEquals(
                    List.of(1, 3),
                    area.getSelectedArenaIndices(4),
                    "formal events retain their explicit arena choice");
            set(area, "playerRespawnLocations", new HashMap<>());
            spectator.set(true);
            area.teleportPlayerToSpawnLocation(player);
            set(area, "gameStageEnum", GameStageEnum.PROGRESS);
            area.respawnPlayer(player);
            assertTrue(teleports.isEmpty(), "direct calls must not teleport or equip a spectator");
            spectator.set(false);
            set(area, "disposed", true);
            area.respawnPlayer(player);
            assertTrue(teleports.isEmpty(), "disposed callbacks must not act on the former roster");
            assertEquals(
                    4,
                    area.getArenaCount(),
                    "selection must not shuffle or shrink the configured physical list");
        } finally {
            sf.set(null, previous);
        }
    }

    @Test
    void dailySkipsEmptyArenasAndRejectsMapsWithNoSpawnPoints() throws Exception {
        var area = allocate(LaserShowdownTeamArea.class);
        set(area, "gameStageEnum", GameStageEnum.WAITING);
        set(area, "runMode", GameRunMode.GAME);
        set(
                area,
                "areaLocations",
                List.of(List.of(), List.of(new Location(null, 100, 64, 0)), List.of()));
        for (int attempt = 0; attempt < 20; attempt++) {
            area.prepareRunMode(GameRunMode.DAILY);
            assertEquals(List.of(1), area.getSelectedArenaIndices(3));
            assertEquals(100, area.getSpectatorSpawnLocation().getBlockX());
            area.prepareRunMode(GameRunMode.GAME);
        }
        set(area, "areaLocations", List.of(List.of()));
        assertThrows(IllegalStateException.class, () -> area.prepareRunMode(GameRunMode.DAILY));
        assertEquals(GameRunMode.GAME, area.getRunMode());
    }

    @Test
    void everyInitialSpawnUsesTheSingleDailyArenaEvenWhenSpawnPointsAreReused() throws Exception {
        var area = allocate(LaserShowdownTeamArea.class);
        set(area, "gameStageEnum", GameStageEnum.WAITING);
        set(area, "runMode", GameRunMode.GAME);
        List<List<Location>> arenas = new ArrayList<>();
        for (int arena = 0; arena < 4; arena++)
            arenas.add(
                    new ArrayList<>(
                            List.of(
                                    new Location(null, arena * 100, 64, 1),
                                    new Location(null, arena * 100 + 1, 64, 1))));
        set(area, "areaLocations", arenas);
        Map<UUID, List<Location>> respawns = new HashMap<>();
        Map<UUID, Location> spawns = new HashMap<>();
        set(area, "playerRespawnLocations", respawns);
        set(area, "playerSpawnLocation", spawns);
        set(area, "locationIterators", new IdentityHashMap<>());
        List<ChampionshipTeam> teams = new ArrayList<>();
        for (int team = 0; team < 16; team++) {
            var roster = new HashSet<UUID>();
            for (int member = 0; member < 1; member++) roster.add(UUID.randomUUID());
            teams.add(new SpawnTeam(team, roster));
        }
        set(area, "gameTeams", teams);
        area.prepareRunMode(GameRunMode.DAILY);
        int arena = area.getSelectedArenaIndices(4).getFirst();
        var assignment = LaserShowdownTeamArea.class.getDeclaredMethod("assignSpawnLocations");
        assignment.setAccessible(true);
        assignment.invoke(area);
        assertEquals(16, spawns.size());
        assertEquals(16, respawns.size());
        assertTrue(
                spawns.values().stream().allMatch(location -> location.getBlockX() / 100 == arena));
        assertTrue(
                respawns.values().stream().allMatch(locations -> locations == arenas.get(arena)));
        for (int i = 0; i < 20; i++)
            assertEquals(arena, area.getSpectatorSpawnLocation().getBlockX() / 100);
        // A later formal event must preserve its own explicit subset, including after an abort.
        area.prepareArenaSelection(ArenaSelection.parse("2,4"));
        area.prepareRunMode(GameRunMode.EVENT);
        assertEquals(List.of(1, 3), area.getSelectedArenaIndices(4));
    }

    @Test
    void dailyUsesFifteenMinutesAndAnIndividualFortyKillThreshold() throws Exception {
        var area = allocate(LaserShowdownTeamArea.class);
        var config = allocate(LaserShowdownConfig.class);
        config.setTimer(90);
        set(area, "gameConfig", config);
        set(area, "runMode", GameRunMode.DAILY);
        assertEquals(900, area.matchDurationSeconds());
        assertFalse(area.reachedKillTarget(39, 100));
        assertTrue(area.reachedKillTarget(40, 40));
        assertTrue(area.reachedKillTarget(41, 0));
        set(area, "runMode", GameRunMode.EVENT);
        assertEquals(90, area.matchDurationSeconds());
        assertFalse(area.reachedKillTarget(40, 99));
        assertTrue(area.reachedKillTarget(10, 100));
    }

    private static final class SpawnTeam extends ChampionshipTeam {
        SpawnTeam(int id, Set<UUID> members) {
            super(id, "team" + id, "RED", "#ff5555", members, null);
        }
    }

    private static <T> T proxy(Class<T> t, InvocationHandler h) {
        return t.cast(Proxy.newProxyInstance(t.getClassLoader(), new Class<?>[] {t}, h));
    }

    private static Field field(Class<?> t, String n) throws Exception {
        for (; t != null; t = t.getSuperclass())
            try {
                var f = t.getDeclaredField(n);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {
            }
        throw new NoSuchFieldException(n);
    }

    private static void set(Object o, String n, Object v) throws Exception {
        field(o.getClass(), n).set(o, v);
    }

    private static <T> T allocate(Class<T> t) throws Exception {
        return t.cast(
                ((sun.misc.Unsafe) field(sun.misc.Unsafe.class, "theUnsafe").get(null))
                        .allocateInstance(t));
    }
}
