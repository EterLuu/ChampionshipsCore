package ink.ziip.championshipscore.api.game.iamnotarobot.geometry;

import static org.junit.jupiter.api.Assertions.*;

import com.sk89q.jnbt.CompoundTag;
import com.sk89q.jnbt.NBTInputStream;

import ink.ziip.championshipscore.api.game.iamnotarobot.geometry.VerificationCourse.Room;
import ink.ziip.championshipscore.support.TestObjects;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

class VerificationCourseTest {
    @Test
    void entryRequiresBothTheRoomFootprintAndTheEntireBoxBeyondTheGate() {
        for (var entry :
                Map.of(Room.WAREHOUSE, -4, Room.SLIDER, 20, Room.JOINT, 39, Room.SPRINT, 83)
                        .entrySet()) {
            Room room = entry.getKey();
            int z = entry.getValue();
            for (int slot = 0; slot < EntryTrack.COUNT; slot++) {
                double x = room == Room.WAREHOUSE ? EntryTrack.forSlot(slot).center() + .5 : 9.5;
                Vector feet = new Vector(x, 1, z + .3);
                assertFalse(room.entered(slot, feet, z - .001));
                assertTrue(room.entered(slot, feet, z));
                assertFalse(room.entered(slot, new Vector(x + 100, 1, z + .3), z));
                Vector restore = room.restoreSpawn(slot);
                assertTrue(room.entered(slot, restore, restore.getZ() - .3));
                assertTrue(room.allows(slot, restore, true));
            }
        }
    }

    @Test
    void onlyTheCurrentRoomAndItsOpenApproachAreAccessible() {
        var route =
                java.util.List.of(
                        EntryTrack.forSlot(1).finishSpawn(),
                        Room.WAREHOUSE.restoreSpawn(1),
                        Room.SLIDER.restoreSpawn(1),
                        Room.JOINT.restoreSpawn(1),
                        Room.SPRINT.restoreSpawn(1));
        for (Room room : Room.values()) {
            for (int i = 0; i < route.size(); i++) {
                var position = route.get(i);
                assertEquals(i == room.ordinal(), room.allows(1, position, true));
                assertEquals(
                        i == room.ordinal() || i == room.ordinal() - 1,
                        room.allows(1, position, false));
            }
        }
        var corridor = new Vector(9.5, 1, 16.5);
        assertTrue(Room.SLIDER.allows(1, corridor, false));
        assertFalse(Room.SLIDER.allows(1, corridor, true));
        assertFalse(Room.JOINT.allows(1, corridor, false));
    }

    @Test
    void collisionChecksCoverEveryClosingDoorAndAllowTouchingOutsideFaces() {
        for (int x : new int[] {-6, 4, 14, 24}) {
            assertTrue(
                    VerificationCourse.gateOccupied(
                            Room.ENTRY, new BoundingBox(x, 3, -6, x + 1, 4, -5)));
            assertTrue(
                    VerificationCourse.gateOccupied(
                            Room.ENTRY, new BoundingBox(x, 1, -4.01, x + 1, 2, -3)));
            assertFalse(
                    VerificationCourse.gateOccupied(
                            Room.ENTRY, new BoundingBox(x, 1, -4, x + 1, 2, -3)));
        }
        for (int x : new int[] {-7, 7, 11, 25})
            assertTrue(
                    VerificationCourse.gateOccupied(
                            Room.WAREHOUSE, new BoundingBox(x, 1, 14, x + 1, 2, 15)));
        for (var entry : Map.of(Room.WAREHOUSE, 19, Room.SLIDER, 38, Room.JOINT, 82).entrySet()) {
            int z = entry.getValue();
            for (int x = 7; x <= 11; x++)
                for (int y = 1; y <= 4; y++)
                    assertTrue(
                            VerificationCourse.gateOccupied(
                                    entry.getKey(), new BoundingBox(x, y, z, x + 1, y + 1, z + 1)));
            assertFalse(
                    VerificationCourse.gateOccupied(
                            entry.getKey(), new BoundingBox(12, 1, z, 13, 2, z + 1)));
            assertFalse(
                    VerificationCourse.gateOccupied(
                            entry.getKey(), new BoundingBox(8, 5, z, 9, 6, z + 1)));
            assertFalse(
                    VerificationCourse.gateOccupied(
                            entry.getKey(), new BoundingBox(8, 1, z + 1, 9, 2, z + 2)));
        }
    }

    @Test
    void closingOnlyTheStampedHallEntrancePreservesAllWarehouseExits() {
        Vector origin = VerificationCourse.GRID.origin(2);
        Set<Vector> changed = new HashSet<>();
        var block =
                TestObjects.proxy(
                        Block.class,
                        (self, method, args) -> {
                            assertEquals("setType", method.getName());
                            assertEquals(Material.RED_STAINED_GLASS, args[0]);
                            assertEquals(false, args[1]);
                            return null;
                        });
        var world =
                TestObjects.proxy(
                        World.class,
                        (self, method, args) -> {
                            assertEquals("getBlockAt", method.getName());
                            changed.add(
                                    new Vector((int) args[0], (int) args[1], (int) args[2])
                                            .subtract(origin));
                            return block;
                        });
        VerificationCourse.closeSliderEntrance(world, origin);
        assertEquals(20, changed.size());
        for (int x = 7; x <= 11; x++)
            for (int y = 1; y <= 4; y++) assertTrue(changed.contains(new Vector(x, y, 19)));
    }

    @Test
    void bundledLaneMatchesAllPlayableAnchors() throws Exception {
        var lane =
                read(
                        VerificationCourse.STRUCTURE,
                        VerificationCourse.WIDTH,
                        VerificationCourse.HEIGHT,
                        VerificationCourse.LENGTH,
                        0,
                        0,
                        0);
        for (var room : VerificationCourse.Room.values()) {
            if (room == VerificationCourse.Room.SPRINT
                    || room == VerificationCourse.Room.ENTRY
                    || room == VerificationCourse.Room.WAREHOUSE) continue;
            for (int x = 7; x <= 11; x++)
                for (int y = 1; y <= 4; y++)
                    assertEquals("minecraft:red_stained_glass", lane.at(x, y, room.end));
        }
        assertEquals("minecraft:white_concrete", lane.at(9, 0, VerificationCourse.FINISH));
        assertEquals(39, VerificationCourse.Room.JOINT.start());
        assertEquals(82, VerificationCourse.Room.JOINT.end);
        assertEquals(83, VerificationCourse.Room.SPRINT.start());
    }

    @Test
    void fourPrivateCitySkywaysHaveMatchingCheckpointsObstaclesAndCleanVisuals() throws Exception {
        var entry =
                read(
                        EntryTrack.STRUCTURE,
                        EntryTrack.WIDTH,
                        EntryTrack.HEIGHT,
                        EntryTrack.LENGTH,
                        EntryTrack.MIN_X,
                        EntryTrack.MIN_Y,
                        EntryTrack.MIN_Z);
        // Zero sea lanterns in the outdoor modern city block (no harsh glowing floor/wall glare)
        assertFalse(entry.data().containsValue("minecraft:sea_lantern"));

        for (int slot = 0; slot < EntryTrack.COUNT; slot++) {
            var track = EntryTrack.forSlot(slot);
            int c = track.center();
            assertEquals(-6 + 10 * slot, c);
            assertEquals(1, track.spawn().getBlockY());

            // Prominent black/white checkered start line + red glass launch gate at rest
            for (int x = c - 2; x <= c + 2; x++) {
                String startFloor = entry.at(x, 0, EntryTrack.START_GATE_Z);
                assertTrue(
                        startFloor.equals("minecraft:black_concrete")
                                || startFloor.equals("minecraft:white_concrete"));
                for (int y = 1; y <= 4; y++)
                    assertEquals(
                            "minecraft:red_stained_glass", entry.at(x, y, EntryTrack.START_GATE_Z));
            }

            // All 4 sub-stage checkpoints have solid plazas, full headroom, and isolated triggers
            for (var cp : EntryTrack.CHECKPOINTS) {
                var spawn = track.checkpointSpawn(cp.index());
                int sx = spawn.getBlockX(), sz = spawn.getBlockZ();
                assertEquals(c, sx);
                assertEquals(1, spawn.getBlockY());
                assertEquals(cp.index(), track.checkpointAt(sx, 1, sz));
                assertEquals(cp.index(), track.checkpointAt(sx, 2, sz));
                assertEquals(-1, track.checkpointAt(sx, 0, sz));
                assertFalse(entry.air(sx, 0, sz));
                for (int y = 1; y <= 4; y++) assertTrue(entry.air(sx, y, sz));
                for (int z = cp.triggerMinZ(); z <= cp.triggerMaxZ(); z++) {
                    for (int x = c - 3; x <= c + 3; x++) {
                        assertFalse(entry.air(x, 0, z));
                        assertEquals(cp.index(), track.checkpointAt(x, 1, z));
                    }
                }
                for (int other = 0; other < EntryTrack.COUNT; other++)
                    if (other != slot)
                        assertEquals(-1, EntryTrack.forSlot(other).checkpointAt(sx, 1, sz));
            }

            // Sub-stage 1: staggered barriers + center vault + 3 crosswalk islands
            for (int x = c - 2; x <= c - 1; x++) assertFalse(entry.air(x, 2, -111));
            assertFalse(entry.air(c, 1, -111));
            assertTrue(entry.air(c, 2, -111));
            for (int x = c + 1; x <= c + 2; x++) assertTrue(entry.air(x, 1, -111));
            for (int x = c + 1; x <= c + 2; x++) assertFalse(entry.air(x, 2, -107));
            assertFalse(entry.air(c, 1, -107));
            assertTrue(entry.air(c, 2, -107));
            for (int x = c - 2; x <= c - 1; x++) assertTrue(entry.air(x, 1, -107));
            assertFalse(entry.air(c - 1, 0, -103));
            assertFalse(entry.air(c + 1, 0, -98));
            assertFalse(entry.air(c - 1, 0, -93));
            assertTrue(entry.air(c, 0, -105));
            assertTrue(entry.air(c, 0, -101));
            assertTrue(entry.air(c, 0, -95));
            assertTrue(entry.air(c, 0, -90));

            // Sub-stage 2: south-facing entry ramp + dual elevated rooftop terrace routes
            assertTrue(entry.at(c, 1, -81).contains("stairs[facing=south"));
            assertFalse(entry.air(c, 1, -80));
            assertFalse(entry.air(c - 2, 1, -77));
            assertFalse(entry.air(c - 2, 2, -73));
            assertFalse(entry.air(c - 2, 2, -69));
            assertFalse(entry.air(c - 2, 1, -64));
            assertFalse(entry.air(c + 2, 2, -76));
            assertFalse(entry.air(c + 2, 2, -70));
            assertFalse(entry.air(c + 2, 1, -64));

            // Sub-stage 3: metro beam hurdles + split beams + hub column with 2-wide bypasses
            for (int x = c - 1; x <= c + 1; x++) {
                assertFalse(entry.air(x, 1, -51));
                assertTrue(entry.air(x, 2, -51));
            }
            for (int x = c - 3; x <= c - 1; x++) assertFalse(entry.air(x, 1, -44));
            for (int x = c + 1; x <= c + 3; x++) assertFalse(entry.air(x, 1, -44));
            assertFalse(entry.air(c, 2, -37));
            assertTrue(entry.air(c - 1, 1, -37));
            assertTrue(entry.air(c + 1, 1, -37));

            // Sub-stage 4: blue-ice express (west) vs quartz ramp + 3x3 slime sky-bounce (east)
            for (int x = c - 3; x <= c - 1; x++) {
                for (int z = -26; z <= -21; z++)
                    assertEquals("minecraft:blue_ice", entry.at(x, 0, z));
                assertTrue(entry.air(x, 0, -20));
                assertEquals("minecraft:blue_ice", entry.at(x, 0, -17));
                assertTrue(entry.air(x, 0, -14));
            }
            for (int x = c + 1; x <= c + 3; x++) {
                assertTrue(entry.at(x, 1, -26).contains("stairs[facing=south"));
                assertTrue(entry.at(x, 2, -25).contains("stairs[facing=south"));
                assertFalse(entry.air(x, 2, -24));
                for (int z = -23; z <= -21; z++)
                    assertEquals("minecraft:slime_block", entry.at(x, 0, z));
                assertTrue(entry.air(x, 0, -20));
                assertFalse(entry.air(x, 0, -16));
                assertTrue(entry.air(x, 0, -14));
            }

            // Bold checkered finish plaza & isolated finish trigger + fall detection below deck
            var finish = track.finishSpawn();
            assertTrue(track.atFinish(finish.getBlockX(), finish.getBlockY(), finish.getBlockZ()));
            for (int x = c - 3; x <= c + 3; x++) {
                for (int z = -11; z <= -8; z++) {
                    String fin = entry.at(x, 0, z);
                    assertTrue(
                            fin.equals("minecraft:black_concrete")
                                    || fin.equals("minecraft:white_concrete"));
                }
                for (int z = -11; z <= -7; z++) {
                    assertFalse(entry.air(x, 0, z));
                    assertTrue(entry.air(x, 1, z));
                    assertTrue(track.atFinish(x, 1, z));
                    for (int other = 0; other < EntryTrack.COUNT; other++)
                        if (other != slot) assertFalse(EntryTrack.forSlot(other).atFinish(x, 1, z));
                }
            }
            assertTrue(track.contains(c, 1, -100));
            assertFalse(track.contains(c, -1, -100));
            assertFalse(track.contains(c + 5, 1, -100));
            assertEquals("minecraft:water", entry.materialAt(c, -4, -100));
        }
    }

    @Test
    void warehouseConnectsEveryTrackAndHasOnePassWindowPerOpaquePartition() throws Exception {
        var room =
                read(
                        WarehouseRoom.STRUCTURE,
                        WarehouseRoom.STRUCT_WIDTH,
                        WarehouseRoom.STRUCT_HEIGHT,
                        WarehouseRoom.STRUCT_LENGTH,
                        WarehouseRoom.STRUCT_MIN_X,
                        WarehouseRoom.STRUCT_MIN_Y,
                        WarehouseRoom.MIN_Z);
        assertFalse(room.data().containsValue("minecraft:sea_lantern"));
        assertTrue(
                room.data().values().stream().noneMatch(v -> v.contains("sign")),
                "Stage 1 must not contain wooden signs");
        for (int slot = 0; slot < 4; slot++) {
            int x = EntryTrack.forSlot(slot).center();
            for (int z = -4; z <= -2; z++) {
                assertFalse(room.air(x, 0, z));
                assertTrue(room.air(x, 1, z) && room.air(x, 2, z));
                assertTrue(WarehouseRoom.contains(slot, x, 1, z, false));
            }
            for (int other : new int[] {-6, 4, 14, 24})
                assertEquals(
                        other == x || (WarehouseRoom.miner(slot) && (other == 4 || other == 14)),
                        WarehouseRoom.contains(slot, other, 1, 6, false));
        }
        for (int x : new int[] {-3, 21}) {
            for (int z = -3; z < 14; z++)
                for (int y = 1; y <= 7; y++)
                    assertEquals(y == 2 && z >= 5 && z <= 7, room.air(x, y, z));
            // Panoramic control-room glass overlooking the central warehouse floor
            assertEquals("minecraft:glass", room.at(x, 3, 6));
            assertEquals("minecraft:glass", room.at(x, 2, 2));
        }
        for (int c : new int[] {-6, 9, 24})
            for (int y = 1; y <= 3; y++)
                assertEquals("minecraft:red_stained_glass", room.at(c, y, 14));
        for (int x : new int[] {-8, 26}) {
            assertTrue(room.at(x, 2, 5).startsWith("minecraft:chest"));
            assertTrue(room.air(x, 3, 5));
            assertTrue(room.air(x, 1, 6));
            assertTrue(room.air(x, 1, 7) && room.air(x, 2, 7));
        }
        for (int i = 0; i < 4; i++) {
            int bx = WarehouseRoom.barrelX(i);
            assertTrue(room.at(bx, 1, 12).startsWith("minecraft:barrel"));
            assertTrue(room.air(bx, 2, 12) && room.air(bx, 3, 12));
            assertTrue(room.air(bx, 1, 10) && room.air(bx, 1, 11));
        }
        // Central aisle to exit gate and spectator spawn stay completely unobstructed
        for (int z = 12; z <= 13; z++)
            for (int x = 8; x <= 10; x++) assertTrue(room.air(x, 1, z) && room.air(x, 2, z));
        assertTrue(room.air(9, 10, 5) && room.air(9, 11, 5));
        // 8 double-sided 3-bay industrial shelves = 24 distinct showcase slots x 4 blocks each
        assertTrue(WarehouseRoom.STOCK_POOL.size() >= 100);
        assertEquals(
                WarehouseRoom.STOCK_POOL.size(),
                java.util.Set.copyOf(WarehouseRoom.STOCK_POOL).size());
        assertFalse(WarehouseRoom.STOCK_POOL.contains(org.bukkit.Material.WHITE_CONCRETE));
        assertFalse(WarehouseRoom.STOCK_POOL.contains(org.bukkit.Material.POLISHED_DEEPSLATE));
        for (var m : WarehouseRoom.STOCK_POOL) {
            assertNotNull(m);
            assertFalse(m.name().contains("SIGN"), m.name());
            assertFalse(m.name().contains("SLAB"), m.name());
            assertFalse(m.name().contains("STAIRS"), m.name());
            assertFalse(m.name().contains("POWDER"), m.name());
        }
        var amounts = new HashMap<Integer, Integer>();
        for (int x = -9; x <= 27; x++)
            for (int y = 1; y <= 4; y++)
                for (int z = -3; z <= 13; z++) {
                    int slot = WarehouseRoom.stockSlot(x, y, z);
                    if (slot >= 0) {
                        var expected = WarehouseRoom.DEFAULT_STOCK.get(slot);
                        assertEquals(
                                "minecraft:" + expected.name().toLowerCase(Locale.ROOT),
                                room.materialAt(x, y, z));
                        amounts.merge(slot, 1, Integer::sum);
                    }
                }
        assertEquals(WarehouseRoom.SLOT_COUNT, amounts.size());
        assertTrue(amounts.values().stream().allMatch(n -> n == 4));
        for (int cx : new int[] {0, 6, 12, 18})
            for (int cz : new int[] {0, 8}) {
                assertEquals("minecraft:polished_deepslate", room.at(cx - 1, 1, cz));
                assertEquals("minecraft:polished_deepslate", room.at(cx + 1, 1, cz));
                assertEquals("minecraft:iron_bars", room.materialAt(cx, 2, cz));
                assertEquals("minecraft:polished_deepslate_slab", room.materialAt(cx, 2, cz - 1));
                assertEquals("minecraft:polished_deepslate_slab", room.materialAt(cx, 4, cz + 1));
            }
        // Behind the three exits every player can walk along the gallery into the old IMAGE room.
        for (int z = 15; z <= 17; z++)
            for (int x = -6; x <= 24; x++)
                assertTrue(!room.air(x, 0, z) && room.air(x, 1, z) && room.air(x, 2, z));
        assertTrue(room.air(9, 1, 18) && room.air(9, 2, 18));
    }

    @Test
    void standaloneVenuesAreSpaced256BlocksApart() {
        for (int i = 0; i < VerificationCourse.MAX_LANES; i++) {
            var venueOrigin = EntryTrack.origin(i);
            var nextVenueOrigin = EntryTrack.origin(i + 1);
            assertEquals(VerificationCourse.GRID.origin(i), venueOrigin);
            assertEquals(
                    VerificationCourse.GRID.origin(i).getBlockX(),
                    WarehouseRoom.GRID.origin(i).getBlockX() - WarehouseRoom.STRUCT_MIN_X);
            assertTrue(WarehouseRoom.STRUCT_MIN_X >= EntryTrack.MIN_X);
            assertTrue(
                    WarehouseRoom.STRUCT_MIN_X + WarehouseRoom.STRUCT_WIDTH
                            <= EntryTrack.MIN_X + EntryTrack.WIDTH);
            assertTrue(WarehouseRoom.STRUCT_MIN_Y >= EntryTrack.MIN_Y);
            assertTrue(
                    WarehouseRoom.STRUCT_MIN_Y + WarehouseRoom.STRUCT_HEIGHT
                            <= EntryTrack.MIN_Y + EntryTrack.HEIGHT);
            assertEquals(256, nextVenueOrigin.getBlockX() - venueOrigin.getBlockX());
            assertTrue(
                    VerificationCourse.VENUE_GRID.origin(i).getBlockX()
                                    + VerificationCourse.VENUE_WIDTH
                                    + 100
                            < VerificationCourse.VENUE_GRID.origin(i + 1).getBlockX());
        }
    }

    Blocks read(String file, int width, int height, int length, int minX, int minY, int minZ)
            throws Exception {
        try (var resource = getClass().getResourceAsStream("/" + file)) {
            assertNotNull(resource);
            try (var input = new NBTInputStream(new GZIPInputStream(resource))) {
                var root = (CompoundTag) input.readNamedTag().getTag();
                var size = root.getListTag("size");
                assertEquals(width, size.getInt(0));
                assertEquals(height, size.getInt(1));
                assertEquals(length, size.getInt(2));
                var palette = root.getList("palette", CompoundTag.class);
                // FAWE fills omitted native structure cells with structure_void.
                assertEquals(width * height * length, root.getList("blocks").size());
                List<String> states = new ArrayList<>();
                for (var state : palette) {
                    String name = state.getString("Name");
                    if (state.getValue().get("Properties") instanceof CompoundTag props
                            && !props.getValue().isEmpty())
                        name +=
                                props.getValue().keySet().stream()
                                        .sorted()
                                        .map(k -> k + "=" + props.getString(k))
                                        .collect(Collectors.joining(",", "[", "]"));
                    states.add(name);
                }
                Map<Vector, String> data = new HashMap<>();
                Set<Vector> blockEntities = new HashSet<>();
                for (var block : root.getList("blocks", CompoundTag.class)) {
                    var pos = block.getListTag("pos");
                    var at =
                            new Vector(
                                    pos.getInt(0) + minX,
                                    pos.getInt(1) + minY,
                                    pos.getInt(2) + minZ);
                    assertNull(
                            data.put(at, states.get(block.getInt("state"))),
                            "duplicate template cell");
                    if (block.containsKey("nbt")) blockEntities.add(at);
                }
                return new Blocks(data, blockEntities, root.getList("entities").size());
            }
        }
    }

    record Blocks(Map<Vector, String> data, Set<Vector> blockEntities, int entities) {
        boolean air(int x, int y, int z) {
            return "minecraft:air".equals(at(x, y, z));
        }

        String materialAt(int x, int y, int z) {
            String data = at(x, y, z);
            int properties = data.indexOf('[');
            return properties < 0 ? data : data.substring(0, properties);
        }

        String at(int x, int y, int z) {
            return data.getOrDefault(new Vector(x, y, z), "minecraft:air");
        }
    }
}
