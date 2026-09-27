package ink.ziip.championshipscore.api.game.riptiderush;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RiptideColorFloorPlatformTest {
    @Test
    void sevenRoundsKeepTheSameFootprintAndRestoreOriginalPattern() {
        for (int[] direction : new int[][]{{1,0}, {-1,0}, {0,1}, {0,-1}}) {
            var fake = new FloorWorld();
            var geometry = RiptideCourseGeometry.resolve(new Location(fake.world, -.5, 80, -.5),
                    new Location(fake.world, -.5 + 100 * direction[0], 80, -.5 + 100 * direction[1]), 7, 9);
            int initial = 20;
            List<Pos> start = positions(geometry, initial);
            for (int i = 0; i < start.size(); i++) fake.blocks.put(start.get(i), data(i % 2 == 0 ? Material.OAK_PLANKS : Material.SPRUCE_PLANKS));
            List<BlockData> original = start.stream().map(fake.blocks::get).toList();
            var platform = new RiptideColorFloorPlatform(geometry, initial);
            for (int round = 0; round < 7; round++) {
                var color = round % 2 == 0 ? Material.RED_CONCRETE : Material.BLUE_CONCRETE;
                platform.paint(java.util.Collections.nCopies(63, color));
                for (Pos pos : start) assertEquals(color, fake.blocks.get(pos).getMaterial());
            }
            platform.restore();
            List<Pos> end = start;
            for (int i = 0; i < end.size(); i++) assertSame(original.get(i), fake.blocks.get(end.get(i)));
            assertEquals(new java.util.HashSet<>(start), fake.blocks.keySet());
        }
    }

    @Test
    void malformedLayoutIsRejectedBeforeChangingAnyBlocks() {
        var fake = new FloorWorld();
        var geometry = RiptideCourseGeometry.resolve(new Location(fake.world, .5, 80, .5),
                new Location(fake.world, .5, 80, 100.5), 7, 9);
        var platform = new RiptideColorFloorPlatform(geometry, 99);
        assertThrows(IllegalArgumentException.class, () -> platform.paint(List.of(Material.RED_CONCRETE)));
        assertTrue(fake.blocks.isEmpty());
    }

    private static List<Pos> positions(RiptideCourseGeometry g, int step) {
        var positions = new ArrayList<Pos>();
        for (int f = -g.halfLength(); f <= g.halfLength(); f++)
            for (int l = -g.halfWidth(); l <= g.halfWidth(); l++)
                positions.add(new Pos(g.blockX(step + f, l), g.floorY(), g.blockZ(step + f, l)));
        return positions;
    }

    private record Pos(int x, int y, int z) { }
    private static BlockData data(Material material) {
        return (BlockData) Proxy.newProxyInstance(BlockData.class.getClassLoader(), new Class<?>[]{BlockData.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getMaterial" -> material;
                    case "clone" -> proxy; // Immutable test block data.
                    case "toString" -> material.name();
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static class FloorWorld {
        final Map<Pos, BlockData> blocks = new HashMap<>();
        final World world = (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> "raft";
                    case "equals" -> proxy == args[0];
                    case "getBlockAt" -> block(new Pos((int) args[0], (int) args[1], (int) args[2]));
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        Block block(Pos pos) {
            return (Block) Proxy.newProxyInstance(Block.class.getClassLoader(), new Class<?>[]{Block.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getBlockData" -> blocks.getOrDefault(pos, data(Material.AIR));
                        case "setBlockData" -> { blocks.put(pos, (BlockData) args[0]); yield null; }
                        case "setType" -> { blocks.put(pos, data((Material) args[0])); yield null; }
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
        }
    }
}
