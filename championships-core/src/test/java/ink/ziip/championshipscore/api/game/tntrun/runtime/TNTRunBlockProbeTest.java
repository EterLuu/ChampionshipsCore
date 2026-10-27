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
    void selectedBlockMatchesOriginalOrderAcrossRandomAndNegativeCoordinates() throws Exception {
        var f = new Fixture();
        Random random = new Random(46752);
        for (int i = 0; i < 10_000; i++) {
            double x = random.nextInt(100) - 50 + random.nextDouble();
            double z = random.nextInt(100) - 50 + random.nextDouble();
            double y = random.nextInt(100) - 40 + random.nextDouble();
            double bias = i % 2 == 0 ? .3 : .8;
            Cell expected = original(f, x, y, z, bias);
            Block actual = f.area.getBlockUnderLocation(new Location(f.world, x, y, z), bias);
            assertEquals(
                    expected,
                    actual == null ? null : new Cell(actual.getX(), actual.getY(), actual.getZ()));
            f.reads.clear();
        }
    }

    private static Cell original(Fixture f, double x, double y, double z, double bias) {
        for (int dy = 0; dy <= 1; dy++) {
            for (double[] offset :
                    new double[][] {{bias, -bias}, {-bias, bias}, {bias, bias}, {-bias, -bias}}) {
                Cell c =
                        new Cell(
                                (int) Math.floor(x + offset[0]),
                                (int) Math.floor(y - dy),
                                (int) Math.floor(z + offset[1]));
                Material m = f.material(c);
                if (m != Material.AIR && m != Material.LIGHT) return c;
            }
        }
        return null;
    }

    private record Cell(int x, int y, int z) {}

    private static final class Fixture {
        final TNTRunTeamArea area = allocate(TNTRunTeamArea.class);
        final List<Cell> reads = new ArrayList<>();
        Material uniform;
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
            return switch (Math.floorMod(c.x * 17 + c.y * 31 + c.z * 7, 5)) {
                case 0 -> Material.STONE;
                case 1 -> Material.LIGHT;
                default -> Material.AIR;
            };
        }
    }
}
