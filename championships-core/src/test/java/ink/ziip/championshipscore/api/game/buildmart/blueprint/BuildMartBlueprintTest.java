package ink.ziip.championshipscore.api.game.buildmart.blueprint;

import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import ink.ziip.championshipscore.api.game.buildmart.reference.ReferenceBuilder;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Fence;
import org.bukkit.block.data.type.Gate;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildMartBlueprintTest {

    @Test
    void terrainMaterialsAreNeverInterchangeableInEitherDirection() {
        for (Material[] pair : List.of(new Material[]{Material.GRASS_BLOCK, Material.DIRT},
                new Material[]{Material.WARPED_NYLIUM, Material.NETHERRACK},
                new Material[]{Material.CRIMSON_NYLIUM, Material.NETHERRACK},
                new Material[]{Material.WARPED_NYLIUM, Material.CRIMSON_NYLIUM})) {
            assertFalse(BuildMartBlueprint.blockMatches(plain(pair[0]), plain(pair[1])));
            assertFalse(BuildMartBlueprint.blockMatches(plain(pair[1]), plain(pair[0])));
            assertTrue(BuildMartBlueprint.blockMatches(plain(pair[0]), plain(pair[0])));
        }
    }

    @Test
    void aPlayerAddedCoverCannotMakeWrongTerrainCountTowardsCompletion() {
        World world = world(Map.of("10,74,20", Material.DIRT, "10,75,20", Material.STONE));
        BuildMartBlueprint blueprint = new BuildMartBlueprint("grass", "Grass", 1,
                List.of(new BlueprintBlock(0, 0, 0, plain(Material.GRASS_BLOCK))));
        Location origin = ReferenceBuilder.buildOrigin(new Location(world, 10, 73, 20));
        assertEquals(0, blueprint.countMatching(origin));
        assertEquals(0.0, blueprint.completionRatio(origin));
    }

    @Test
    void matchingUsesTheFloorPlusOneOriginAndDoesNotCountTheFloorOrExtraBlocks() {
        World world = world(Map.of("-10,73,-20", Material.DIRT, "-10,74,-20", Material.STONE,
                "-4,80,-14", Material.GOLD_BLOCK, "-9,74,-20", Material.DIAMOND_BLOCK));
        BuildMartBlueprint blueprint = new BuildMartBlueprint("corners", "Corners", 1, List.of(
                new BlueprintBlock(0, 0, 0, plain(Material.STONE)),
                new BlueprintBlock(6, 6, 6, plain(Material.GOLD_BLOCK))));
        Location origin = ReferenceBuilder.buildOrigin(new Location(world, -10, 73, -20));
        assertEquals(2, blueprint.countMatching(origin));
        assertEquals(1.0, blueprint.completionRatio(origin));
    }

    @Test
    void malformedRowsRejectTheWholeBlueprintInsteadOfReducingItsDenominator() {
        var yaml = new org.bukkit.configuration.file.YamlConfiguration();
        yaml.set("blocks", List.of("valid", "bad"));
        var error = org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> BuildMartBlueprint.fromYaml("bad", yaml, raw -> raw.equals("valid")
                        ? new BlueprintBlock(0, 0, 0, plain(Material.STONE)) : null));
        assertTrue(error.getMessage().contains("2"));
        yaml.set("blocks", List.of("valid", 42));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> BuildMartBlueprint.fromYaml("bad", yaml, raw -> new BlueprintBlock(0, 0, 0, plain(Material.STONE))));
    }

    @Test
    void duplicateOutOfBoundsAndEmptyBlueprintsAreRejected() {
        BlueprintBlock b = new BlueprintBlock(0, 0, 0, plain(Material.STONE));
        for (List<BlueprintBlock> blocks : List.of(List.<BlueprintBlock>of(), List.of(b, b),
                List.of(new BlueprintBlock(-1, 0, 0, plain(Material.STONE))),
                List.of(new BlueprintBlock(0, 7, 0, plain(Material.STONE))),
                List.of(new BlueprintBlock(0, 0, 7, plain(Material.STONE))))) {
            org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                    () -> new BuildMartBlueprint("bad", "bad", 1, blocks));
        }
    }

    @Test
    void comparisonCategorizesMissingMaterialAndStateAndLimitsExamples() {
        BlockData wrongState = (BlockData) Proxy.newProxyInstance(BlockData.class.getClassLoader(),
                new Class<?>[]{BlockData.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getMaterial" -> Material.STONE;
                    case "matches" -> false;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        var bp = new BuildMartBlueprint("a", "a", 1, List.of(
                new BlueprintBlock(0, 0, 0, plain(Material.STONE)),
                new BlueprintBlock(1, 0, 0, plain(Material.STONE)),
                new BlueprintBlock(2, 0, 0, wrongState),
                new BlueprintBlock(3, 0, 0, plain(Material.STONE)),
                new BlueprintBlock(4, 0, 0, plain(Material.STONE))));
        var result = bp.compare(new Location(world(Map.of("1,0,0", Material.DIRT,
                "2,0,0", Material.STONE, "3,0,0", Material.STONE)), 0, 0, 0));
        assertEquals(1, result.matched());
        assertEquals(2, result.missing());
        assertEquals(1, result.wrongMaterial());
        assertEquals(1, result.wrongState());
        assertEquals(List.of("(0,0,0)", "(1,0,0)", "(2,0,0)"), result.positions());
    }

    @Test
    void leafDistanceIsIgnoredButPersistenceWaterloggingAndSpeciesRemainStrict() {
        var ref = leaves(Material.CHERRY_LEAVES, 7, true, false);
        assertTrue(BuildMartBlueprint.blockMatches(ref, leaves(Material.CHERRY_LEAVES, 1, true, false)));
        assertFalse(BuildMartBlueprint.blockMatches(ref, leaves(Material.CHERRY_LEAVES, 1, false, false)));
        assertFalse(BuildMartBlueprint.blockMatches(ref, leaves(Material.CHERRY_LEAVES, 1, true, true)));
        assertFalse(BuildMartBlueprint.blockMatches(ref, leaves(Material.OAK_LEAVES, 1, true, false)));
        assertEquals(7, ref.getDistance());
    }

    private static org.bukkit.block.data.type.Leaves leaves(Material material, int distance, boolean persistent, boolean wet) {
        int[] value = {distance};
        return (org.bukkit.block.data.type.Leaves) Proxy.newProxyInstance(BlockData.class.getClassLoader(),
                new Class<?>[]{org.bukkit.block.data.type.Leaves.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getMaterial" -> material;
                    case "getDistance" -> value[0];
                    case "setDistance" -> { value[0] = (int) args[0]; yield null; }
                    case "isPersistent" -> persistent;
                    case "isWaterlogged" -> wet;
                    case "clone" -> leaves(material, value[0], persistent, wet);
                    case "matches" -> args[0] instanceof org.bukkit.block.data.type.Leaves other
                            && other.getMaterial() == material && other.getDistance() == value[0]
                            && other.isPersistent() == persistent && other.isWaterlogged() == wet;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static BlockData plain(Material material) {
        return (BlockData) Proxy.newProxyInstance(BlockData.class.getClassLoader(),
                new Class<?>[]{BlockData.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getMaterial" -> material;
                    case "getAsString" -> material.getKey().toString();
                    case "matches" -> ((BlockData) args[0]).getMaterial() == material;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static World world(Map<String, Material> blocks) {
        return (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getBlockAt")) {
                        Material material = blocks.getOrDefault(args[0] + "," + args[1] + "," + args[2], Material.AIR);
                        return Proxy.newProxyInstance(Block.class.getClassLoader(), new Class<?>[]{Block.class},
                                (block, blockMethod, blockArgs) -> switch (blockMethod.getName()) {
                                    case "getBlockData" -> plain(material);
                                    case "getType" -> material;
                                    default -> throw new UnsupportedOperationException(blockMethod.getName());
                                });
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    @Test
    void ageableBlocksIgnoreGrowthAge() {
        assertTrue(BuildMartBlueprint.blockMatches(
                ageable(Material.CACTUS, 15, "same-state"),
                ageable(Material.CACTUS, 0, "same-state")));
    }

    @Test
    void ageableBlocksStillRequireEveryOtherStateToMatch() {
        assertFalse(BuildMartBlueprint.blockMatches(
                ageable(Material.CACTUS, 15, "reference-state"),
                ageable(Material.CACTUS, 0, "placed-state")));
    }

    @Test
    void ageableBlocksStillRequireTheSameMaterial() {
        assertFalse(BuildMartBlueprint.blockMatches(
                ageable(Material.CACTUS, 15, "same-state"),
                ageable(Material.SUGAR_CANE, 0, "same-state")));
    }

    @Test
    void fencesIgnoreNeighbourConnectionsButKeepMaterialAndWaterloggedState() {
        assertTrue(BuildMartBlueprint.blockMatches(
                fence(Material.OAK_FENCE, false, "north-east"),
                fence(Material.OAK_FENCE, false, "south-west")));
        assertFalse(BuildMartBlueprint.blockMatches(
                fence(Material.OAK_FENCE, false, "north-east"),
                fence(Material.SPRUCE_FENCE, false, "north-east")));
        assertFalse(BuildMartBlueprint.blockMatches(
                fence(Material.OAK_FENCE, false, "north-east"),
                fence(Material.OAK_FENCE, true, "north-east")));
    }

    @Test
    void fenceGatesCompareFacingByAxisAndKeepOtherStateStrict() {
        assertTrue(BuildMartBlueprint.blockMatches(
                gate(Material.OAK_FENCE_GATE, BlockFace.NORTH, false, false, false),
                gate(Material.OAK_FENCE_GATE, BlockFace.SOUTH, false, false, false)));
        assertTrue(BuildMartBlueprint.blockMatches(
                gate(Material.OAK_FENCE_GATE, BlockFace.EAST, false, false, false),
                gate(Material.OAK_FENCE_GATE, BlockFace.WEST, false, false, false)));
        assertFalse(BuildMartBlueprint.blockMatches(
                gate(Material.OAK_FENCE_GATE, BlockFace.NORTH, false, false, false),
                gate(Material.OAK_FENCE_GATE, BlockFace.EAST, false, false, false)));
        assertFalse(BuildMartBlueprint.blockMatches(
                gate(Material.OAK_FENCE_GATE, BlockFace.NORTH, false, false, false),
                gate(Material.OAK_FENCE_GATE, BlockFace.SOUTH, true, false, false)));
    }

    private static Ageable ageable(Material material, int age, String otherState) {
        return (Ageable) Proxy.newProxyInstance(
                Ageable.class.getClassLoader(),
                new Class<?>[]{Ageable.class},
                new AgeableHandler(material, age, otherState));
    }

    private static Fence fence(Material material, boolean waterlogged, String connections) {
        return (Fence) Proxy.newProxyInstance(
                Fence.class.getClassLoader(),
                new Class<?>[]{Fence.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getMaterial" -> material;
                    case "isWaterlogged" -> waterlogged;
                    case "getAsString", "toString" -> material + "[connections=" + connections
                            + ",waterlogged=" + waterlogged + "]";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static Gate gate(Material material, BlockFace facing, boolean open,
                             boolean inWall, boolean powered) {
        return (Gate) Proxy.newProxyInstance(
                Gate.class.getClassLoader(),
                new Class<?>[]{Gate.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getMaterial" -> material;
                    case "getFacing" -> facing;
                    case "isOpen" -> open;
                    case "isInWall" -> inWall;
                    case "isPowered" -> powered;
                    case "getAsString", "toString" -> material + "[facing=" + facing + ",open=" + open
                            + ",inWall=" + inWall + ",powered=" + powered + "]";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static final class AgeableHandler implements InvocationHandler {
        private final Material material;
        private final String otherState;
        private int age;

        private AgeableHandler(Material material, int age, String otherState) {
            this.material = material;
            this.age = age;
            this.otherState = otherState;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            return switch (method.getName()) {
                case "getAge" -> age;
                case "setAge" -> {
                    age = (int) args[0];
                    yield null;
                }
                case "getMaximumAge" -> 15;
                case "getMaterial" -> material;
                case "clone" -> ageable(material, age, otherState);
                case "matches" -> matches((BlockData) args[0]);
                case "getAsString" -> material.getKey() + "[age=" + age + ",other=" + otherState + "]";
                case "toString" -> material + "[age=" + age + ",other=" + otherState + "]";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> throw new UnsupportedOperationException(method.getName());
            };
        }

        private boolean matches(BlockData other) {
            if (!(other instanceof Ageable) || !Proxy.isProxyClass(other.getClass())) return false;
            InvocationHandler handler = Proxy.getInvocationHandler(other);
            if (!(handler instanceof AgeableHandler that)) return false;
            return material == that.material && age == that.age && otherState.equals(that.otherState);
        }
    }
}
