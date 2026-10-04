package ink.ziip.championshipscore.api.game.riptiderush.config;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.api.game.riptiderush.runtime.RiptideRushArea;
import ink.ziip.championshipscore.api.game.riptiderush.support.RiptideTestFixtures;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.*;

class RiptideSpawnTest {
    @Test
    void allPlayersSpawnAtCentreWithCollisionDisabledBeforeTeleportAndRestoreTheirOriginalState()
            throws Exception {
        var unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        var area =
                (RiptideRushArea)
                        ((sun.misc.Unsafe) unsafeField.get(null))
                                .allocateInstance(RiptideRushArea.class);
        var g = RiptideTestFixtures.config().resolveGeometry();
        var players = new LinkedHashMap<UUID, Player>();
        var collision = new HashMap<UUID, Boolean>();
        var original = new HashMap<UUID, Boolean>();
        var targets = new HashMap<UUID, Location>();
        for (int i = 0; i < 4; i++) {
            UUID id = UUID.randomUUID();
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
        set(area, "gamePlayers", new ArrayList<>(players.keySet()));
        set(area, "geometry", g);
        var spawns = new HashMap<UUID, Location>();
        set(area, "playerSpawnLocations", spawns);
        var saved = new HashMap<UUID, Boolean>();
        set(area, "spawnCollisionStates", saved);
        var serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        Object previous = serverField.get(null);
        serverField.set(
                null,
                Proxy.newProxyInstance(
                        Server.class.getClassLoader(),
                        new Class<?>[] {Server.class},
                        (proxy, method, args) -> {
                            if (method.getName().equals("getPlayer")) return players.get(args[0]);
                            throw new AssertionError(method.getName());
                        }));
        try {
            var assign = RiptideRushArea.class.getDeclaredMethod("assignAndTeleportSpawns");
            assign.setAccessible(true);
            assign.invoke(area);
            assign.invoke(area);
            assertEquals(4, targets.size());
            for (var location : targets.values()) assertEquals(g.centerAt(0), location);
            assertEquals(targets, spawns);
            assertEquals(
                    original,
                    saved,
                    "Repeated preparation must retain the original collision state");
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

    private static void set(Object target, String name, Object value) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                var field = type.getDeclaredField(name);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new NoSuchFieldException(name);
    }
}
