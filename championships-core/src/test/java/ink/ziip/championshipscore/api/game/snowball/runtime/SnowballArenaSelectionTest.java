package ink.ziip.championshipscore.api.game.snowball.runtime;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.manager.GameManager;
import ink.ziip.championshipscore.api.game.model.GameStageEnum;
import ink.ziip.championshipscore.api.game.spectate.SpectatorManager;
import ink.ziip.championshipscore.api.game.start.ArenaSelection;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

class SnowballArenaSelectionTest {
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
            var area = allocate(SnowballShowdownTeamArea.class);
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
