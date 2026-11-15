package ink.ziip.championshipscore.api.game.tntrun.runtime;

import static ink.ziip.championshipscore.support.TestObjects.*;

import static org.junit.jupiter.api.Assertions.*;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.junit.jupiter.api.Test;

import java.util.*;

class TNTRunBlockProbeTest {
    @Test
    void duplicateCornersAreReadOncePerHeightAndAirAndLightAreSkipped() throws Exception {
        var f = new Fixture();
        f.uniform = Material.AIR;
        assertNull(f.area.getBlockUnderLocation(new Location(f.world, -0.5, 70, -0.5), .3));
        assertEquals(2, f.reads.size());
        f.reads.clear();
        f.uniform = Material.LIGHT;
        assertNull(f.area.getBlockUnderLocation(new Location(f.world, 0, 70, 0.5), .3));
        assertEquals(4, f.reads.size());
        assertEquals(f.reads.size(), new HashSet<>(f.reads).size());
    }

    @Test
    void probeDoesNotLoadChunksOrReadBlocksFromUnloadedNeighbours() throws Exception {
        var f = new Fixture();
        f.uniform = Material.STONE;
        f.unloaded = true;
        assertNull(f.area.getBlockUnderLocation(new Location(f.world, 15.9, 70, 15.9), .8));
        assertTrue(f.reads.isEmpty());
    }

    @Test
    void firstSolidCornerWinsInFixedOrderAndTheFeetLayerPrecedesTheLayerBelow() throws Exception {
        record Case(double x, double z, Set<Cell> solid, Cell expected) {}
        // Corners at (0.5, 0.5) with bias 0.8: (1,-1), (-1,1), (1,1), (-1,-1) per layer.
        Cell a = new Cell(1, 70, -1), b = new Cell(-1, 70, 1), c = new Cell(1, 70, 1);
        Cell d = new Cell(-1, 70, -1), below = new Cell(1, 69, -1);
        Cell negative = new Cell(-10, 70, -22);
        var cases =
                List.of(
                        new Case(.5, .5, Set.of(a, b, c, d, below), a),
                        new Case(.5, .5, Set.of(b, c, d, below), b),
                        new Case(.5, .5, Set.of(c, d, below), c),
                        new Case(.5, .5, Set.of(d, below), d),
                        new Case(.5, .5, Set.of(below), below),
                        new Case(.5, .5, Set.of(), null),
                        new Case(-10.5, -20.5, Set.of(negative, new Cell(-12, 70, -20)), negative));
        var f = new Fixture();
        for (var example : cases) {
            f.solid = example.solid();
            Block actual =
                    f.area.getBlockUnderLocation(
                            new Location(f.world, example.x(), 70.2, example.z()), .8);
            assertEquals(
                    example.expected(),
                    actual == null ? null : new Cell(actual.getX(), actual.getY(), actual.getZ()),
                    example.toString());
        }
    }

    private record Cell(int x, int y, int z) {}

    private static final class Fixture {
        final TNTRunTeamArea area = allocate(TNTRunTeamArea.class);
        final List<Cell> reads = new ArrayList<>();
        Material uniform;
        Set<Cell> solid = Set.of();
        boolean unloaded;
        final World world =
                proxy(
                        World.class,
                        (p, m, a) ->
                                switch (m.getName()) {
                                    case "getMinHeight" -> -64;
                                    case "getMaxHeight" -> 320;
                                    case "isChunkLoaded" -> !unloaded;
                                    case "getBlockAt" -> {
                                        Cell cell = new Cell((int) a[0], (int) a[1], (int) a[2]);
                                        reads.add(cell);
                                        yield proxy(
                                                Block.class,
                                                (b, method, args) ->
                                                        switch (method.getName()) {
                                                            case "getType" -> material(cell);
                                                            case "getX" -> cell.x;
                                                            case "getY" -> cell.y;
                                                            case "getZ" -> cell.z;
                                                            default ->
                                                                    throw new AssertionError(
                                                                            method.getName());
                                                        });
                                    }
                                    default -> throw new AssertionError(m.getName());
                                });

        Fixture() throws Exception {}

        Material material(Cell c) {
            if (uniform != null) return uniform;
            return solid.contains(c) ? Material.STONE : Material.AIR;
        }
    }
}
