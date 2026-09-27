package ink.ziip.championshipscore.api.game.buildmart;

import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class BuildMartHandlerProtectionTest {
    private static final ChampionshipTeam TEAM = new TestTeam();
    private static final World WORLD = proxy(World.class, (p, m, a) -> switch (m.getName()) {
        case "equals" -> p == a[0];
        case "getName" -> "buildmart";
        default -> throw new UnsupportedOperationException(m.getName());
    });
    private static final Player PLAYER = proxy(Player.class, (p, m, a) -> null);
    private TestArea area;
    private BuildMartHandler handler;

    @BeforeEach void setup() throws Exception {
        var field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); field.setAccessible(true);
        area = (TestArea) ((sun.misc.Unsafe) field.get(null)).allocateInstance(TestArea.class);
        area.stage = GameStageEnum.PROGRESS;
        handler = new BuildMartHandler(null) {
            @Override protected ChampionshipTeam teamOf(Player player) { return TEAM; }
        };
        handler.setBuildMartArea(area);
    }

    @Test void onlyMainHandCanSubmitButBothHandsAreCancelled() {
        for (EquipmentSlot hand : List.of(EquipmentSlot.HAND, EquipmentSlot.OFF_HAND)) {
            var event = new PlayerInteractEvent(PLAYER, Action.RIGHT_CLICK_BLOCK, null, block(10, 1, 0), BlockFace.UP, hand);
            handler.onSubmitButton(event);
            assertTrue(event.isCancelled());
        }
        assertEquals(1, area.submissions);
    }

    @Test void referenceInteractionIncludesEmptyCellsFloorAndHubRegardlessOfHand() {
        for (Action action : List.of(Action.RIGHT_CLICK_BLOCK, Action.LEFT_CLICK_BLOCK, Action.PHYSICAL)) {
            var event = new PlayerInteractEvent(PLAYER, action, null, block(20, 0, 0), BlockFace.UP, EquipmentSlot.OFF_HAND);
            handler.onReferenceInteract(event);
            assertEquals(Event.Result.DENY, event.useInteractedBlock());
            assertEquals(Event.Result.DENY, event.useItemInHand());
        }
        var material = new PlayerInteractEvent(PLAYER, Action.RIGHT_CLICK_BLOCK, null, block(40, 1, 0), BlockFace.UP);
        handler.onReferenceInteract(material);
        assertNotEquals(Event.Result.DENY, material.useInteractedBlock());
    }

    @Test void multiPlaceChecksDoorTopAndBedSecondHalf() {
        for (List<BlockState> states : List.of(List.of(state(0, 7, 0), state(0, 8, 0)),
                List.of(state(6, 1, 0), state(7, 1, 0)), List.of(state(6, 1, 0), state(8, 1, 0)))) {
            var event = new BlockMultiPlaceEvent(states, block(0, 0, 0), null, PLAYER, true, EquipmentSlot.HAND);
            handler.onPlace(event);
            assertTrue(event.isCancelled());
        }
        var valid = new BlockMultiPlaceEvent(List.of(state(5, 1, 0), state(6, 1, 0)), block(5, 0, 0), null, PLAYER, true, EquipmentSlot.HAND);
        handler.onPlace(valid);
        assertFalse(valid.isCancelled());
    }

    @Test void bucketCannotChangeReferenceOrOutsideButCanWaterlogOwnBuild() {
        for (int x : List.of(7, 20)) {
            var event = bucket(x); handler.onBucketEmpty(event); assertTrue(event.isCancelled());
        }
        var event = bucket(5); handler.onBucketEmpty(event); assertFalse(event.isCancelled());
    }

    @Test void fluidAndPistonsStayWithinTheSamePlot() {
        var inside = new BlockFromToEvent(block(5, 1, 0), block(6, 1, 0));
        handler.onFluid(inside); assertFalse(inside.isCancelled());
        var outside = new BlockFromToEvent(block(6, 1, 0), block(7, 1, 0));
        handler.onFluid(outside); assertTrue(outside.isCancelled());
        var display = new BlockFromToEvent(block(19, 1, 0), block(20, 1, 0));
        handler.onFluid(display); assertTrue(display.isCancelled());
        var extend = new BlockPistonExtendEvent(block(5, 1, 0), List.of(block(6, 1, 0)), BlockFace.EAST);
        handler.onPistonExtend(extend); assertTrue(extend.isCancelled());
        var retract = new BlockPistonRetractEvent(block(5, 1, 0), List.of(block(7, 1, 0)), BlockFace.WEST);
        handler.onPistonRetract(retract); assertTrue(retract.isCancelled());
        var allowed = new BlockPistonRetractEvent(block(0, 1, 0), List.of(block(2, 1, 0)), BlockFace.WEST);
        handler.onPistonRetract(allowed); assertFalse(allowed.isCancelled());
    }

    @Test void physicsIsFrozenOnlyForReferencesDuringCountdownAndProgress() {
        area.stage = GameStageEnum.COUNTDOWN;
        var reference = new BlockPhysicsEvent(block(20, 1, 0), null);
        handler.onReferencePhysics(reference); assertTrue(reference.isCancelled());
        var build = new BlockPhysicsEvent(block(0, 1, 0), null);
        handler.onReferencePhysics(build); assertFalse(build.isCancelled());
        area.stage = GameStageEnum.WAITING;
        reference = new BlockPhysicsEvent(block(20, 1, 0), null);
        handler.onReferencePhysics(reference); assertFalse(reference.isCancelled());
    }

    private static PlayerBucketEmptyEvent bucket(int x) {
        return new PlayerBucketEmptyEvent(PLAYER, block(x, 1, 0), block(x, 0, 0), BlockFace.UP,
                Material.WATER_BUCKET, null, EquipmentSlot.HAND);
    }
    private static BlockState state(int x, int y, int z) {
        return proxy(BlockState.class, (p, m, a) -> switch (m.getName()) {
            case "getBlock" -> block(x, y, z);
            default -> throw new UnsupportedOperationException(m.getName());
        });
    }
    private static Block block(int x, int y, int z) {
        return proxy(Block.class, (p, m, a) -> switch (m.getName()) {
            case "getWorld" -> WORLD;
            case "getX" -> x;
            case "getY" -> y;
            case "getZ" -> z;
            case "getLocation" -> new Location(WORLD, x, y, z);
            case "getRelative" -> { BlockFace f = (BlockFace) a[0]; yield block(x + f.getModX(), y + f.getModY(), z + f.getModZ()); }
            case "getBlockData" -> proxy(Directional.class, (dp, dm, da) -> dm.getName().equals("getFacing") ? BlockFace.EAST : null);
            default -> throw new UnsupportedOperationException(m.getName());
        });
    }
    @SuppressWarnings("unchecked") private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }
    private static final class TestTeam extends ChampionshipTeam { TestTeam() { super(1, "test", "red", "", null); } }
    private static final class TestArea extends BuildMartArea {
        GameStageEnum stage; int submissions;
        TestArea() { super(null, null); }
        @Override public GameStageEnum getGameStageEnum() { return stage; }
        @Override public boolean notAreaPlayer(Player player) { return false; }
        @Override public String submitSlotIdAt(ChampionshipTeam team, Location clicked) { return "G"; }
        @Override public void handleSubmitClick(Player player, String slotId) { submissions++; }
        @Override public boolean notInArea(Location location) { return location.getBlockX() < -10 || location.getBlockX() > 50; }
        @Override public boolean isProtectedReferenceBlock(World w, int x, int y, int z) { return x >= 20 && x <= 26 && y >= 0 && y <= 7; }
        @Override public boolean isBuildZoneBlock(ChampionshipTeam team, World w, int x, int y, int z) {
            return team == TEAM && x >= 0 && x <= 6 && y >= 1 && y <= 7 && z >= 0 && z <= 6;
        }
        @Override public boolean allowsBlockTransfer(Block from, Block to) {
            return isBuildZoneBlock(TEAM, WORLD, from.getX(), from.getY(), from.getZ())
                    && isBuildZoneBlock(TEAM, WORLD, to.getX(), to.getY(), to.getZ());
        }
    }
}
