package ink.ziip.championshipscore.api.game.riptiderush;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RiptideColorFloorPlatformTest {
    @Nested
    class RiptideColorFloorPlatformCases {
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

    @Nested
    class RiptideColorFloorInventoryCases {
        @Test
        void everyStorageSlotAndOffhandChangeTogetherAndRestoreOriginalItems() {
            var state = new InventoryState();
            state.storage[0] = new TestItem(Material.STICK, 3);
            state.storage[35] = new TestItem(Material.APPLE, 2);
            state.offHand = new TestItem(Material.TORCH, 7);
            var saved = RiptideColorFloorInventory.capture(state.inventory());
            for (Material target : new Material[]{Material.RED_CONCRETE, Material.WAXED_OXIDIZED_CHISELED_COPPER}) {
                RiptideColorFloorInventory.show(state.inventory(), new TestItem(target, 64));
                for (ItemStack item : state.storage) {
                    assertEquals(target, item.getType());
                    assertEquals(64, item.getAmount());
                }
                assertNotSame(state.storage[0], state.storage[1]);
                assertEquals(target, state.offHand.getType());
                assertEquals(64, state.offHand.getAmount());
            }
            saved.restore(state.inventory());
            assertEquals(Material.STICK, state.storage[0].getType());
            assertEquals(3, state.storage[0].getAmount());
            assertNull(state.storage[1]);
            assertEquals(Material.APPLE, state.storage[35].getType());
            assertEquals(Material.TORCH, state.offHand.getType());
            assertEquals(7, state.offHand.getAmount());
        }

        // Paper creates real ItemStacks through the running server's registry. This fixture tests
        // snapshot ownership and slot updates without pretending to provide that registry.
        private static class TestItem extends ItemStack {
            private final Material material;
            private final int amount;
            TestItem(Material material, int amount) { this.material = material; this.amount = amount; }
            @Override public Material getType() { return material; }
            @Override public int getAmount() { return amount; }
            @Override public ItemStack clone() { return new TestItem(material, amount); }
        }

        private static class InventoryState {
            ItemStack[] storage = new ItemStack[36];
            ItemStack offHand;
            PlayerInventory inventory() {
                return (PlayerInventory) Proxy.newProxyInstance(PlayerInventory.class.getClassLoader(),
                        new Class<?>[]{PlayerInventory.class}, (proxy, method, args) -> switch (method.getName()) {
                            case "getStorageContents" -> storage.clone();
                            case "getItemInOffHand" -> offHand;
                            case "setStorageContents" -> { storage = ((ItemStack[]) args[0]).clone(); yield null; }
                            case "setItemInOffHand" -> { offHand = (ItemStack) args[0]; yield null; }
                            default -> throw new UnsupportedOperationException(method.getName());
                        });
            }
        }
    }
}
