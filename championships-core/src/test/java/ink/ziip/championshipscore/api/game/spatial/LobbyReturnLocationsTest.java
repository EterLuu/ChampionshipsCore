package ink.ziip.championshipscore.api.game.spatial;

import static org.junit.jupiter.api.Assertions.*;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockType;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

class LobbyReturnLocationsTest {
    @Test
    void eachPlayerReturnsToTheirExactPositionAndRotationWithoutSharingMutableLocations() {
        World world = world("lobby");
        Location spawn = new Location(world, 0.5, 99, 0.5);
        var first = new LobbyPlayer(world);
        var second = new LobbyPlayer(world);
        Location firstPosition = new Location(world, 35.125, 99.5, -27.375, 123.5F, -42.25F);
        Location secondPosition = new Location(world, -72.875, 101.25, 14.625, -67.25F, 32.5F);
        LobbyReturnLocations.remember(first.player, firstPosition, spawn);
        LobbyReturnLocations.remember(second.player, secondPosition, spawn);

        Location expected = firstPosition.clone();
        firstPosition.add(500, 0, 500);
        assertEquals(expected, LobbyReturnLocations.resolve(first.player, spawn));
        assertEquals(secondPosition, LobbyReturnLocations.resolve(second.player, spawn));
        LobbyReturnLocations.resolve(first.player, spawn).add(500, 0, 500);
        assertEquals(expected, LobbyReturnLocations.resolve(first.player, spawn));
    }

    @Test
    void arenaTransfersAndDisconnectsDoNotReplaceTheLobbyRecordButTheNextDepartureDoes() {
        World lobby = world("lobby");
        Location spawn = new Location(lobby, 0, 99, 0);
        var player = new LobbyPlayer(lobby);
        Location previous = new Location(lobby, 21.5, 99, 16.75, 90, 10);
        LobbyReturnLocations.remember(player.player, previous, spawn);
        for (String name : new String[] {"arena_round_one", "arena_round_two", "bingo_worker"}) {
            LobbyReturnLocations.remember(
                    player.player, new Location(world(name), 8, 70, 8), spawn);
            assertEquals(previous, LobbyReturnLocations.resolve(player.player, spawn));
        }

        Location next = new Location(lobby, -16.25, 99.5, 56.875, -90, -20);
        LobbyReturnLocations.remember(player.player, next, spawn);
        assertEquals(next, LobbyReturnLocations.resolve(player.player, spawn));
    }

    @Test
    void reconnectReadsTheSavedPlayerDataWithoutNeedingTheOldPlayerConnection() {
        World lobby = world("lobby");
        Location spawn = new Location(lobby, 0, 99, 0);
        var oldConnection = new LobbyPlayer(lobby);
        Location departure = new Location(lobby, 52.625, 99.25, -18.375, 135, -35);
        LobbyReturnLocations.remember(oldConnection.player, departure, spawn);

        var reconnected =
                new LobbyPlayer(lobby, oldConnection.player.getUniqueId(), oldConnection.data);
        assertNotSame(oldConnection.player, reconnected.player);
        assertEquals(departure, LobbyReturnLocations.resolve(reconnected.player, spawn));
    }

    @Test
    void missingChangedOrMalformedRecordsUseTheExistingSpawnFallback() throws Exception {
        var stoneType = Material.class.getDeclaredField("blockType");
        stoneType.setAccessible(true);
        Object original = stoneType.get(Material.STONE);
        BlockType stone =
                proxy(
                        BlockType.class,
                        (object, method, args) -> {
                            if (method.getName().equals("isSolid")) return true;
                            throw new UnsupportedOperationException(method.getName());
                        });
        stoneType.set(Material.STONE, (Supplier<BlockType>) () -> stone);
        try {
            World lobby = world("lobby");
            Location spawn = new Location(lobby, 0.5, 99, 0.5, 45, 15);
            var player = new LobbyPlayer(lobby);
            Location fallback = TeleportPositions.getScatteredLobbyLocation(spawn, player.player);
            assertEquals(fallback, LobbyReturnLocations.resolve(player.player, spawn));

            LobbyReturnLocations.remember(player.player, new Location(lobby, 80, 99, 80), spawn);
            World replacement = world("new_lobby");
            Location newSpawn = new Location(replacement, 20.5, 99, 20.5);
            assertEquals(
                    TeleportPositions.getScatteredLobbyLocation(newSpawn, player.player),
                    LobbyReturnLocations.resolve(player.player, newSpawn));

            NamespacedKey key = player.data.keySet().iterator().next();
            for (String malformed :
                    new String[] {"bad record", "lobby:NaN:99:0:0:0", "lobby:0:99:0:Infinity:0"}) {
                player.data.put(key, malformed);
                assertEquals(fallback, LobbyReturnLocations.resolve(player.player, spawn));
                assertFalse(player.data.containsKey(key));
            }
        } finally {
            stoneType.set(Material.STONE, original);
        }
    }

    @Test
    void unavailableLobbyDoesNotRecordOrResolveAnUnusableWorld() {
        World world = world("lobby");
        var player = new LobbyPlayer(world);
        Location unavailable = new Location(null, 0, 99, 0);
        LobbyReturnLocations.remember(player.player, new Location(world, 0, 99, 0), null);
        LobbyReturnLocations.remember(player.player, new Location(world, 0, 99, 0), unavailable);
        LobbyReturnLocations.remember(player.player, null, new Location(world, 0, 99, 0));
        assertTrue(player.data.isEmpty());
        assertNull(LobbyReturnLocations.resolve(player.player, null));
        assertSame(unavailable, LobbyReturnLocations.resolve(player.player, unavailable));
    }

    private static World world(String name) {
        Block block =
                proxy(
                        Block.class,
                        (object, method, args) ->
                                switch (method.getName()) {
                                    case "isPassable" -> true;
                                    case "getType" -> Material.STONE;
                                    default ->
                                            throw new UnsupportedOperationException(
                                                    method.getName());
                                });
        return proxy(
                World.class,
                (object, method, args) ->
                        switch (method.getName()) {
                            case "getName" -> name;
                            case "equals" -> object == args[0];
                            case "hashCode" -> System.identityHashCode(object);
                            case "getBlockAt" -> block;
                            default -> throw new UnsupportedOperationException(method.getName());
                        });
    }

    private static final class LobbyPlayer {
        final Map<NamespacedKey, String> data;
        final Player player;

        LobbyPlayer(World world) {
            this(world, UUID.randomUUID(), Map.of());
        }

        LobbyPlayer(World world, UUID uuid, Map<NamespacedKey, String> savedData) {
            data = new HashMap<>(savedData);
            PersistentDataContainer container =
                    proxy(
                            PersistentDataContainer.class,
                            (object, method, args) ->
                                    switch (method.getName()) {
                                        case "set" -> {
                                            data.put((NamespacedKey) args[0], (String) args[2]);
                                            yield null;
                                        }
                                        case "get" -> data.get(args[0]);
                                        case "remove" -> {
                                            data.remove(args[0]);
                                            yield null;
                                        }
                                        default ->
                                                throw new UnsupportedOperationException(
                                                        method.getName());
                                    });
            Server server =
                    proxy(
                            Server.class,
                            (object, method, args) -> {
                                if (method.getName().equals("getWorld"))
                                    return world.getName().equals(args[0]) ? world : null;
                                throw new UnsupportedOperationException(method.getName());
                            });
            player =
                    proxy(
                            Player.class,
                            (object, method, args) ->
                                    switch (method.getName()) {
                                        case "getUniqueId" -> uuid;
                                        case "getPersistentDataContainer" -> container;
                                        case "getServer" -> server;
                                        default ->
                                                throw new UnsupportedOperationException(
                                                        method.getName());
                                    });
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler);
    }
}
