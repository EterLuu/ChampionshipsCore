package ink.ziip.championshipscore.api.game.spatial;

import static ink.ziip.championshipscore.support.TestObjects.proxy;

import static org.junit.jupiter.api.Assertions.*;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.Test;

import java.util.*;

class TeleportPositionsTest {
    private static final List<UUID> MEMBERS =
            List.of(new UUID(0, 4), new UUID(0, 2), new UUID(0, 1), new UUID(0, 3));

    @Test
    void kitchenSpawnsFitBetweenTheCounterAndWorkstations() {
        var floor = new Platform();
        floor.floor(-6, 6, -4, 4);
        for (int x = -6; x <= 6; x++) floor.put(x, 64, -2, Material.OAK_PLANKS);
        for (int x = 1; x <= 6; x++) floor.put(x, 64, 2, Material.FURNACE);
        Location anchor = new Location(floor.world, .5, 64, .5, 180, 0);
        var result = TeleportPositions.getTeamSpawnLocations(anchor, MEMBERS, p -> true);
        assertEquals(
                Set.of(
                        anchor.clone().add(-2, 0, -1),
                        anchor.clone().add(2, 0, -1),
                        anchor.clone().add(-2, 0, 1),
                        anchor.clone().add(2, 0, 1)),
                new HashSet<>(result.values()));
        var reversed = new ArrayList<>(MEMBERS);
        Collections.reverse(reversed);
        assertEquals(result, TeleportPositions.getTeamSpawnLocations(anchor, reversed, p -> true));
        assertEquals(new Location(floor.world, .5, 64, .5, 180, 0), anchor);
    }

    @Test
    void baseSpawnsShiftForwardOnThePlatformInsteadOfEnteringThePortalOrFallingOff() {
        var floor = new Platform();
        floor.floor(-3, 3, -3, 0);
        floor.floor(-2, 2, 1, 1);
        for (int x = -2; x <= 2; x++) floor.put(x, 64, 1, Material.NETHER_PORTAL);
        for (int x : new int[] {-3, 3}) floor.put(x, 64, 0, Material.STONE);
        Location anchor = new Location(floor.world, .5, 64, .5, -179.85F, 2.7F);
        var result = TeleportPositions.getTeamSpawnLocations(anchor, MEMBERS, p -> true);
        assertEquals(
                Set.of(
                        anchor.clone().add(-2, 0, -2),
                        anchor.clone().add(2, 0, -2),
                        anchor.clone().add(-2, 0, 0),
                        anchor.clone().add(2, 0, 0)),
                new HashSet<>(result.values()));
        for (int size = 1; size <= 4; size++) {
            List<UUID> roster = MEMBERS.stream().sorted().limit(size).toList();
            var partial = TeleportPositions.getTeamSpawnLocations(anchor, roster, p -> true);
            for (UUID member : roster) assertEquals(result.get(member), partial.get(member));
        }
    }

    @Test
    void customPlatformsAndLargerTeamsKeepDistinctPositionsWithinTheirOwnWorkspace() {
        var floor = new Platform();
        floor.floor(-5, 5, -5, 5);
        var members = new ArrayList<>(MEMBERS);
        members.add(new UUID(0, 5));
        for (float yaw : new float[] {0, 90, 180, 270}) {
            var result =
                    TeleportPositions.getTeamSpawnLocations(
                            new Location(floor.world, .5, 64, .5, yaw, 12),
                            members,
                            p -> p.getBlockX() == 0 && p.getBlockZ() >= 0);
            assertEquals(5, new HashSet<>(result.values()).size());
            for (Location point : result.values()) {
                assertEquals(0.5, point.getX());
                assertTrue(point.getZ() >= 0);
                assertEquals(yaw, point.getYaw());
                assertEquals(12, point.getPitch());
            }
        }
    }

    @Test
    void refusesUnsupportedObstructedHazardousAndOutOfBoundsPositions() {
        var floor = new Platform();
        floor.floor(0, 3, 0, 0);
        floor.put(0, 65, 0, Material.STONE);
        floor.put(1, 64, 0, Material.WATER);
        floor.put(2, 63, 0, Material.MAGMA_BLOCK);
        var anchor = new Location(floor.world, .5, 64, .5);
        assertThrows(
                IllegalStateException.class,
                () -> TeleportPositions.getTeamSpawnLocations(anchor, MEMBERS, p -> true));
        assertThrows(
                IllegalStateException.class,
                () ->
                        TeleportPositions.getTeamSpawnLocations(
                                anchor, List.of(MEMBERS.getFirst()), p -> p.getBlockX() < 3));
        assertEquals(
                List.of(anchor.clone().add(3, 0, 0)),
                new ArrayList<>(
                        TeleportPositions.getTeamSpawnLocations(
                                        anchor, List.of(MEMBERS.getFirst()), p -> true)
                                .values()));
        assertTrue(TeleportPositions.getTeamSpawnLocations(anchor, List.of(), p -> true).isEmpty());
    }

    private record Cell(int x, int y, int z) {}

    private static final class Platform {
        private final Map<Cell, Material> blocks = new HashMap<>();
        private final World world =
                proxy(
                        World.class,
                        (p, m, a) ->
                                switch (m.getName()) {
                                    case "getBlockAt" -> {
                                        if (a[0] instanceof Location location)
                                            yield block(
                                                    location.getBlockX(),
                                                    location.getBlockY(),
                                                    location.getBlockZ());
                                        yield block((Integer) a[0], (Integer) a[1], (Integer) a[2]);
                                    }
                                    case "equals" -> p == a[0];
                                    case "hashCode" -> System.identityHashCode(p);
                                    case "getName" -> "spawns";
                                    case "toString" -> "spawns";
                                    default -> throw new AssertionError(m.getName());
                                });

        void floor(int minX, int maxX, int minZ, int maxZ) {
            for (int z = minZ; z <= maxZ; z++)
                for (int x = minX; x <= maxX; x++) put(x, 63, z, Material.STONE);
        }

        void put(int x, int y, int z, Material type) {
            blocks.put(new Cell(x, y, z), type);
        }

        private Block block(int x, int y, int z) {
            Material type = blocks.getOrDefault(new Cell(x, y, z), Material.AIR);
            boolean empty =
                    type == Material.AIR
                            || type == Material.NETHER_PORTAL
                            || type == Material.WATER;
            return proxy(
                    Block.class,
                    (p, m, a) ->
                            switch (m.getName()) {
                                case "getRelative" ->
                                        block(
                                                x + (Integer) a[0],
                                                y + (Integer) a[1],
                                                z + (Integer) a[2]);
                                case "getType" -> type;
                                case "isSolid" -> !empty;
                                case "isPassable" -> empty;
                                case "isLiquid" -> type == Material.WATER;
                                case "getBoundingBox" ->
                                        new BoundingBox(x, y, z, x + 1, y + 1, z + 1);
                                default -> throw new AssertionError(m.getName());
                            });
        }
    }
}
