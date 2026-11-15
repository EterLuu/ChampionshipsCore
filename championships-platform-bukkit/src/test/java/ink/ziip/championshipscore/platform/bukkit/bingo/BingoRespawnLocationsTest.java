package ink.ziip.championshipscore.platform.bukkit.bingo;

import static org.junit.jupiter.api.Assertions.*;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.List;

class BingoRespawnLocationsTest {
    private final World overworld = world("bingo"),
            nether = world("bingo_nether"),
            lobby = world("lobby");
    private final Location fallback = new Location(overworld, 0, 80, 0);

    @Test
    void nativeBedAndAnchorDestinationsSurviveButOutsideWorldsAndSpectatorsUseFallback() {
        for (boolean anchor : List.of(false, true)) {
            Location nativeLocation = new Location(anchor ? nether : overworld, 100, 64, 102);
            var event = event(nativeLocation, !anchor, anchor);
            assertEquals(
                    nativeLocation,
                    BingoRespawnLocations.eventDestination(
                            event, true, this::gameWorld, () -> fallback));
            assertSame(
                    fallback,
                    BingoRespawnLocations.eventDestination(
                            event, false, this::gameWorld, () -> fallback));
        }
        assertSame(
                fallback,
                BingoRespawnLocations.eventDestination(
                        event(new Location(lobby, 1, 64, 1), true, false),
                        true,
                        this::gameWorld,
                        () -> fallback));
        assertSame(
                fallback,
                BingoRespawnLocations.eventDestination(
                        event(new Location(overworld, 1, 64, 1), false, false),
                        true,
                        this::gameWorld,
                        () -> fallback));
    }

    @Test
    void recoveryKeepsNativePositionIncludingTheLastAnchorChargeWithoutReadingBlocks() {
        for (World world : List.of(overworld, nether)) {
            Location storedBlock = new Location(world, 100, 64, 100);
            Player player = player(storedBlock, world);
            assertNull(
                    BingoRespawnLocations.recoveryDestination(
                            player,
                            this::gameWorld,
                            () -> fail("Native spawn must survive delayed recovery")));
        }
    }

    @Test
    void clearedBrokenOrUnchargedSpawnsAndForeignWorldsUseTheConfiguredFallback() {
        for (Player player :
                List.of(
                        player(null, overworld),
                        player(new Location(lobby, 1, 64, 1), lobby),
                        player(new Location(nether, 1, 64, 1), lobby))) {
            assertSame(
                    fallback,
                    BingoRespawnLocations.recoveryDestination(
                            player, this::gameWorld, () -> fallback));
        }
        Location netherFallback = new Location(nether, 0, 64, 0);
        assertSame(
                netherFallback,
                BingoRespawnLocations.recoveryDestination(
                        player(null, overworld), this::gameWorld, () -> netherFallback));
    }

    private boolean gameWorld(World world) {
        return world == overworld || world == nether;
    }

    private PlayerRespawnEvent event(Location destination, boolean bed, boolean anchor) {
        return new PlayerRespawnEvent(
                player(null, overworld),
                destination,
                bed,
                anchor,
                false,
                PlayerRespawnEvent.RespawnReason.DEATH);
    }

    private static Player player(Location personal, World current) {
        return proxy(
                Player.class,
                (p, m, args) ->
                        switch (m.getName()) {
                            case "getRespawnLocation" -> {
                                assertArrayEquals(
                                        new Object[] {false},
                                        args,
                                        "Must not calculate spawn or access blocks across Folia"
                                                + " regions");
                                yield personal;
                            }
                            case "getWorld" -> current;
                            default -> throw new AssertionError(m.getName());
                        });
    }

    private static World world(String name) {
        return proxy(
                World.class,
                (p, m, args) -> {
                    if (m.getName().equals("getName")) return name;
                    throw new AssertionError(m.getName());
                });
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(
                Proxy.newProxyInstance(
                        type.getClassLoader(),
                        new Class<?>[] {type},
                        (p, m, args) ->
                                switch (m.getName()) {
                                    case "equals" -> p == args[0];
                                    case "hashCode" -> System.identityHashCode(p);
                                    case "toString" -> type.getSimpleName();
                                    default -> handler.invoke(p, m, args);
                                }));
    }
}
