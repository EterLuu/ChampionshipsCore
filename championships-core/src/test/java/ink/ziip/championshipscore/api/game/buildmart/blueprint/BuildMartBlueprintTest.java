package ink.ziip.championshipscore.api.game.buildmart.blueprint;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Fence;
import org.bukkit.block.data.type.Gate;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildMartBlueprintTest {

    @Test
    void ageableBlocksIgnoreGrowthAge() {
        assertTrue(BuildMartBlueprint.blockMatches(
                ageable(Material.CACTUS, 15, "same-state"),
                ageable(Material.CACTUS, 0, "same-state"),
                false));
    }

    @Test
    void ageableBlocksStillRequireEveryOtherStateToMatch() {
        assertFalse(BuildMartBlueprint.blockMatches(
                ageable(Material.CACTUS, 15, "reference-state"),
                ageable(Material.CACTUS, 0, "placed-state"),
                false));
    }

    @Test
    void ageableBlocksStillRequireTheSameMaterial() {
        assertFalse(BuildMartBlueprint.blockMatches(
                ageable(Material.CACTUS, 15, "same-state"),
                ageable(Material.SUGAR_CANE, 0, "same-state"),
                false));
    }

    @Test
    void fencesIgnoreNeighbourConnectionsButKeepMaterialAndWaterloggedState() {
        assertTrue(BuildMartBlueprint.blockMatches(
                fence(Material.OAK_FENCE, false, "north-east"),
                fence(Material.OAK_FENCE, false, "south-west"),
                false));
        assertFalse(BuildMartBlueprint.blockMatches(
                fence(Material.OAK_FENCE, false, "north-east"),
                fence(Material.SPRUCE_FENCE, false, "north-east"),
                false));
        assertFalse(BuildMartBlueprint.blockMatches(
                fence(Material.OAK_FENCE, false, "north-east"),
                fence(Material.OAK_FENCE, true, "north-east"),
                false));
    }

    @Test
    void fenceGatesCompareFacingByAxisAndKeepOtherStateStrict() {
        assertTrue(BuildMartBlueprint.blockMatches(
                gate(Material.OAK_FENCE_GATE, BlockFace.NORTH, false, false, false),
                gate(Material.OAK_FENCE_GATE, BlockFace.SOUTH, false, false, false),
                false));
        assertTrue(BuildMartBlueprint.blockMatches(
                gate(Material.OAK_FENCE_GATE, BlockFace.EAST, false, false, false),
                gate(Material.OAK_FENCE_GATE, BlockFace.WEST, false, false, false),
                false));
        assertFalse(BuildMartBlueprint.blockMatches(
                gate(Material.OAK_FENCE_GATE, BlockFace.NORTH, false, false, false),
                gate(Material.OAK_FENCE_GATE, BlockFace.EAST, false, false, false),
                false));
        assertFalse(BuildMartBlueprint.blockMatches(
                gate(Material.OAK_FENCE_GATE, BlockFace.NORTH, false, false, false),
                gate(Material.OAK_FENCE_GATE, BlockFace.SOUTH, true, false, false),
                false));
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
