package ink.ziip.championshipscore.api.game.area.prepare;

import static ink.ziip.championshipscore.support.TestObjects.allocate;
import static ink.ziip.championshipscore.support.TestObjects.proxy;
import static ink.ziip.championshipscore.support.TestObjects.set;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.api.game.area.prepare.buildmart.BuildMartBlueprintWorkshop;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

class PrepareListenerBuildMartTest {
    private static final World WORLD =
            proxy(World.class, (p, m, a) -> m.getName().equals("equals") ? p == a[0] : null);
    private static final Player PLAYER = proxy(Player.class, (p, m, a) -> null);
    private TestManager manager;
    private BuildMartBlueprintWorkshop workshop;
    private PrepareListener listener;

    @BeforeEach
    void setup() throws Exception {
        workshop = allocate(BuildMartBlueprintWorkshop.class);
        set(workshop, "floor", new Location(WORLD, 10, 0, 20));
        set(workshop, "button", new Location(WORLD, 13, 1, 27));
        var session = allocate(PrepareSession.class);
        session.setBlueprintWorkshop(workshop);
        manager = allocate(TestManager.class);
        manager.session = session;
        manager.workshop = workshop;
        listener = new PrepareListener(null, manager);
    }

    @Test
    void emptyHandSubmitButtonCancelsBothHandsAndRoutesOnlyTheMainHand() throws Exception {
        int[] lookups = {0};
        set(
                workshop,
                "editor",
                proxy(
                        Player.class,
                        (p, m, a) -> {
                            if (m.getName().equals("isOnline")) {
                                lookups[0]++;
                                return false;
                            }
                            throw new UnsupportedOperationException(m.getName());
                        }));
        for (var hand : List.of(EquipmentSlot.OFF_HAND, EquipmentSlot.HAND)) {
            var event =
                    new PlayerInteractEvent(
                            PLAYER,
                            Action.RIGHT_CLICK_BLOCK,
                            null,
                            block(13, 1, 27),
                            BlockFace.UP,
                            hand);
            listener.onInteract(event);
            assertTrue(event.isCancelled());
            assertEquals(hand == EquipmentSlot.HAND ? 1 : 0, lookups[0]);
        }
    }

    @Test
    void fluidStaysInsideTheWorkshopAndStopsWhileSubmissionIsPending() throws Exception {
        var inside = new BlockFromToEvent(block(15, 1, 20), block(16, 1, 20));
        listener.onWorkshopFluid(inside);
        assertFalse(inside.isCancelled());
        for (var event :
                List.of(
                        new BlockFromToEvent(block(16, 1, 20), block(17, 1, 20)),
                        new BlockFromToEvent(block(17, 1, 20), block(16, 1, 20)),
                        new BlockFromToEvent(block(10, 1, 20), block(10, 0, 20)))) {
            listener.onWorkshopFluid(event);
            assertTrue(event.isCancelled());
        }
        set(workshop, "busy", true);
        listener.onWorkshopFluid(inside);
        assertTrue(inside.isCancelled());
    }

    @Test
    void bucketChecksAffectedBlockRatherThanTheClickedFloor() throws Exception {
        var inside =
                new PlayerBucketEmptyEvent(
                        PLAYER,
                        block(10, 1, 20),
                        block(10, 0, 20),
                        BlockFace.UP,
                        Material.WATER_BUCKET,
                        null,
                        EquipmentSlot.HAND);
        listener.onWorkshopBucketEmpty(inside);
        assertFalse(inside.isCancelled());
        var outside =
                new PlayerBucketEmptyEvent(
                        PLAYER,
                        block(17, 1, 20),
                        block(17, 0, 20),
                        BlockFace.UP,
                        Material.WATER_BUCKET,
                        null,
                        EquipmentSlot.HAND);
        listener.onWorkshopBucketEmpty(outside);
        assertTrue(outside.isCancelled());
        set(workshop, "busy", true);
        listener.onWorkshopBucketEmpty(inside);
        assertTrue(inside.isCancelled());
    }

    private static class TestManager extends PrepareSessionManager {
        PrepareSession session;
        BuildMartBlueprintWorkshop workshop;

        private TestManager() {
            super(null);
        }

        @Override
        public PrepareSession getSession(Player player) {
            return session;
        }

        @Override
        List<BuildMartBlueprintWorkshop> blueprintWorkshops() {
            return List.of(workshop);
        }
    }

    private static Block block(int x, int y, int z) {
        return proxy(
                Block.class,
                (p, m, a) ->
                        m.getName().equals("getLocation") ? new Location(WORLD, x, y, z) : null);
    }
}
