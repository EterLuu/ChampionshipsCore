package ink.ziip.championshipscore.api.game.iamnotarobot.runtime;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.api.game.iamnotarobot.geometry.JointPlaza;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

class RobotPropsTest {
    private static final class FakeItem extends ItemStack {
        private String tag;
        private final Material material;

        FakeItem(Material material) {
            this.material = material;
        }

        @Override
        public Material getType() {
            return material;
        }

        @Override
        public boolean hasItemMeta() {
            return tag != null;
        }

        @Override
        public ItemStack clone() {
            return new FakeItem(material);
        }

        @Override
        public ItemMeta getItemMeta() {
            PersistentDataContainer pdc =
                    proxy(
                            PersistentDataContainer.class,
                            (o, method, args) -> {
                                if (method.getName().equals("get")) return tag;
                                if (method.getName().equals("set")) {
                                    tag = (String) args[2];
                                    return null;
                                }
                                throw new AssertionError(method.getName());
                            });
            return proxy(
                    ItemMeta.class,
                    (o, method, args) -> {
                        if (method.getName().equals("getPersistentDataContainer")) return pdc;
                        throw new AssertionError(method.getName());
                    });
        }

        @Override
        public boolean editMeta(Consumer<? super ItemMeta> consumer) {
            consumer.accept(getItemMeta());
            return true;
        }
    }

    @Test
    void tagKindOwnerAndRecovery() {
        RobotProps props = new RobotProps();
        ItemStack[] slots = new ItemStack[41];
        ItemStack displaced = new FakeItem(Material.DIAMOND);
        slots[4] = displaced;
        AtomicInteger reads = new AtomicInteger();
        var added = new ArrayList<ItemStack>();
        PlayerInventory inventory =
                proxy(
                        PlayerInventory.class,
                        (o, method, args) ->
                                switch (method.getName()) {
                                    case "getHeldItemSlot" -> 4;
                                    case "getItem" -> {
                                        reads.incrementAndGet();
                                        yield slots[(int) args[0]];
                                    }
                                    case "setItem" -> {
                                        slots[(int) args[0]] = (ItemStack) args[1];
                                        yield null;
                                    }
                                    case "getSize" -> slots.length;
                                    case "addItem" -> {
                                        added.addAll(java.util.List.of((ItemStack[]) args[0]));
                                        yield new HashMap<Integer, ItemStack>();
                                    }
                                    default -> throw new AssertionError(method.getName());
                                });
        UUID id = UUID.randomUUID();
        Player player =
                proxy(
                        Player.class,
                        (o, method, args) ->
                                switch (method.getName()) {
                                    case "getUniqueId" -> id;
                                    case "getInventory" -> inventory;
                                    case "getItemOnCursor" -> null;
                                    default -> throw new AssertionError(method.getName());
                                });
        props.give(
                player,
                RobotProps.Kind.JOINT_PICK,
                new FakeItem(Material.IRON_PICKAXE),
                RobotProps.SlotPolicy.HELD);
        ItemStack prop = slots[4];
        assertTrue(RobotProps.prop(prop));
        assertEquals(RobotProps.Kind.JOINT_PICK, RobotProps.kind(prop));
        String owner = RobotProps.tag(prop).split("/")[1];
        assertTrue(RobotProps.owned(prop, owner));
        assertFalse(RobotProps.owned(prop, "other"));
        assertFalse(RobotProps.prop(displaced));
        reads.set(0);
        props.give(
                player,
                RobotProps.Kind.JOINT_PICK,
                new FakeItem(Material.IRON_PICKAXE),
                RobotProps.SlotPolicy.HELD);
        assertEquals(1, reads.get());
        JointTool tool = new JointTool(props);
        assertFalse(tool.block(prop));
        assertFalse(tool.block(new FakeItem(Material.GRAY_CONCRETE)));
        props.give(
                player,
                RobotProps.Kind.JOINT_BLOCK,
                new FakeItem(Material.GRAY_CONCRETE),
                RobotProps.SlotPolicy.HOTBAR);
        assertTrue(tool.block(slots[0]));
        props.recover(player, RobotProps.Kind.JOINT_BLOCK);
        assertNull(slots[0]);
        props.recover(player, RobotProps.Kind.JOINT_PICK);
        assertSame(displaced, slots[4]);
        props.give(
                player,
                RobotProps.Kind.JOINT_PICK,
                new FakeItem(Material.IRON_PICKAXE),
                RobotProps.SlotPolicy.HELD);
        slots[4] = new FakeItem(Material.STONE);
        props.recover(player, RobotProps.Kind.JOINT_PICK);
        assertEquals(java.util.List.of(displaced), added);
    }

    @Test
    void rigFillRequiresOwnedBlock() {
        RobotProps props = new RobotProps();
        World world =
                proxy(
                        World.class,
                        (o, method, args) -> {
                            if (method.getName().equals("equals")) return o == args[0];
                            throw new AssertionError(method.getName());
                        });
        Location location = new Location(world, 0, 0, 0);
        Block clicked =
                proxy(
                        Block.class,
                        (o, method, args) ->
                                switch (method.getName()) {
                                    case "getWorld" -> world;
                                    case "getLocation" -> location.clone();
                                    case "getRelative" ->
                                            throw new IllegalStateException("block accepted");
                                    default -> throw new AssertionError(method.getName());
                                });
        ItemStack[] held = {new FakeItem(Material.IRON_PICKAXE)};
        PlayerInventory inventory =
                proxy(
                        PlayerInventory.class,
                        (o, method, args) -> {
                            if (method.getName().equals("getItemInMainHand")) return held[0];
                            if (method.getName().equals("getHeldItemSlot")) return 0;
                            if (method.getName().equals("getItem")) return held[0];
                            if (method.getName().equals("setItem")) {
                                held[0] = (ItemStack) args[1];
                                return null;
                            }
                            throw new AssertionError(method.getName());
                        });
        UUID id = UUID.randomUUID();
        Player player =
                proxy(
                        Player.class,
                        (o, method, args) ->
                                switch (method.getName()) {
                                    case "getInventory" -> inventory;
                                    case "getUniqueId" -> id;
                                    case "getEyeLocation" -> location.clone();
                                    default -> throw new AssertionError(method.getName());
                                });
        JointRig rig = new JointRig(world, new Vector(), null, null, props, () -> 0);
        rig.bind(() -> true, () -> true, p -> true, () -> {}, text -> {}, pass -> {}, () -> {});
        assertFalse(rig.fill(player, clicked, BlockFace.UP));
        held[0] = new FakeItem(Material.GRAY_CONCRETE);
        assertFalse(rig.fill(player, clicked, BlockFace.UP));
        props.give(
                player,
                RobotProps.Kind.JOINT_BLOCK,
                new FakeItem(Material.GRAY_CONCRETE),
                RobotProps.SlotPolicy.HELD);
        assertThrows(IllegalStateException.class, () -> rig.fill(player, clicked, BlockFace.UP));
    }

    @Test
    void diggingRequiresOwnedPickAndReadyPhase() {
        RobotProps props = new RobotProps();
        World world =
                proxy(
                        World.class,
                        (o, method, args) -> {
                            if (method.getName().equals("equals")) return o == args[0];
                            throw new AssertionError(method.getName());
                        });
        Location location = new Location(world, 0, 0, 0);
        Material[] material = {Material.GRAY_CONCRETE};
        int[] x = {1};
        Block target =
                proxy(
                        Block.class,
                        (o, method, args) ->
                                switch (method.getName()) {
                                    case "getWorld" -> world;
                                    case "getLocation" -> location.clone();
                                    case "getX" -> x[0];
                                    case "getY" -> JointPlaza.BLOCK_MIN_Y;
                                    case "getZ" -> JointPlaza.BLOCK_MIN_Z;
                                    case "getType" -> material[0];
                                    default -> throw new AssertionError(method.getName());
                                });
        ItemStack[] held = {new FakeItem(Material.IRON_PICKAXE)};
        PlayerInventory inventory =
                proxy(
                        PlayerInventory.class,
                        (o, method, args) ->
                                switch (method.getName()) {
                                    case "getItemInMainHand", "getItem" -> held[0];
                                    case "getHeldItemSlot" -> 0;
                                    case "setItem" -> {
                                        held[0] = (ItemStack) args[1];
                                        yield null;
                                    }
                                    default -> throw new AssertionError(method.getName());
                                });
        UUID id = UUID.randomUUID();
        Player player =
                proxy(
                        Player.class,
                        (o, method, args) ->
                                switch (method.getName()) {
                                    case "getInventory" -> inventory;
                                    case "getUniqueId" -> id;
                                    case "getEyeLocation" -> location.clone();
                                    default -> throw new AssertionError(method.getName());
                                });
        JointRig rig = new JointRig(world, new Vector(), null, null, props, () -> 0);
        rig.bind(() -> true, () -> true, p -> true, () -> {}, text -> {}, pass -> {}, () -> {});
        assertTrue(rig.state.open());
        assertTrue(rig.state.ready(rig.state.epoch(), 0));
        assertFalse(rig.canDig(player, target));
        props.give(
                player,
                RobotProps.Kind.JOINT_PICK,
                new FakeItem(Material.IRON_PICKAXE),
                RobotProps.SlotPolicy.HELD);
        assertTrue(rig.canDig(player, target));
        material[0] = Material.STONE;
        assertFalse(rig.canDig(player, target));
        material[0] = Material.GRAY_CONCRETE;
        x[0] = -2;
        assertFalse(rig.canDig(player, target));
        x[0] = 1;
        assertTrue(rig.state.press());
        assertFalse(rig.canDig(player, target));
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler);
    }
}
