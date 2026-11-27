package ink.ziip.championshipscore.api.game.iamnotarobot.geometry;

import static org.junit.jupiter.api.Assertions.*;

import org.bukkit.util.Vector;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

class SliderHallTest {
    static VerificationCourseTest.Blocks map;
    static boolean[][][] solid = new boolean[89][29][20];

    @BeforeAll
    static void read() throws Exception {
        map = new VerificationCourseTest().read(SliderHall.STRUCTURE, 89, 29, 20, -35, -6, 19);
        map.data()
                .forEach(
                        (p, name) -> {
                            String b = name.split("\\[")[0];
                            solid[p.getBlockX() + 35][p.getBlockY() + 6][p.getBlockZ() - 19] =
                                    !b.equals("minecraft:air") && !b.equals("minecraft:light");
                        });
    }

    private record Rail(
            SliderHall hall, int zero, int run, int step, Vector start, Vector outside) {}

    private static final List<Rail> RAILS =
            List.of(
                    new Rail(
                            SliderHall.A,
                            5,
                            -1,
                            1,
                            new Vector(4.5, 1, 20.5),
                            new Vector(5.5, 1, 22.5)),
                    new Rail(
                            SliderHall.B,
                            13,
                            1,
                            1,
                            new Vector(14.5, 1, 20.5),
                            new Vector(13.5, 1, 22.5)),
                    new Rail(
                            SliderHall.C,
                            5,
                            -1,
                            -1,
                            new Vector(4.5, 1, 37.5),
                            new Vector(5.5, 1, 35.5)),
                    new Rail(
                            SliderHall.D,
                            13,
                            1,
                            -1,
                            new Vector(14.5, 1, 37.5),
                            new Vector(13.5, 1, 35.5)));

    @Test
    void nearDoorStartsAndHoldingColumnsMatchAllFourStations() {
        for (var rail : RAILS) {
            var s = rail.hall;
            int z = rail.start.getBlockZ();
            assertEquals(rail.run, s.run);
            assertEquals(rail.step, s.step);
            assertEquals(rail.start, s.start());
            assertEquals(rail.outside, s.outside());
            for (int i = -1; i <= 24; i++) {
                int x = rail.zero + rail.run * i;
                assertEquals(x, s.pixelX(i));
                assertEquals(i >= 1 && i <= 23 ? i - 1 : -1, s.holdingColumn(x, 1, z));
                assertEquals(i >= 0 && i <= 23 ? s : null, SliderHall.station(x, 1, z));
                assertEquals(-1, s.holdingColumn(x, 2, z));
                assertNull(SliderHall.station(x, 1, z + rail.step));
            }
        }
    }

    @Test
    void openRailsAndMapAnchorsMatchBundledStructure() {
        assertFalse(map.data().containsValue("minecraft:sea_lantern"));
        assertTrue(map.blockEntities().isEmpty());
        assertEquals(0, map.entities());
        for (int x = 7; x <= 11; x++)
            for (int y = 1; y <= 4; y++) {
                for (int z : new int[] {19, 20, 21, 36, 37}) assertTrue(map.air(x, y, z));
                assertEquals("minecraft:red_stained_glass", map.at(x, y, 38));
            }
        assertEquals(16, SliderHall.MAP_COLUMNS);
        assertEquals(8, SliderHall.MAP_ROWS);
        for (var rail : RAILS) {
            var s = rail.hall;
            int t = rail.start.getBlockZ(), wall = t - rail.step, front = t + rail.step;
            int left = rail.run < 0 ? -8 : 11;
            for (int c = 0; c < 16; c++) {
                int x = left + (rail.step == 1 ? c : 15 - c);
                assertEquals(x, s.mapX(c));
                assertTrue(
                        map.at(x, SliderHall.BAR_Y, wall)
                                .matches("minecraft:(smooth_stone|light_gray_concrete)"));
                for (int r = 0; r < 8; r++) {
                    assertEquals(13 - r, SliderHall.mapY(r));
                    assertTrue(map.air(x, 13 - r, t));
                    assertTrue(
                            map.at(x, 13 - r, wall)
                                    .matches("minecraft:(white_concrete|blue_concrete)"));
                }
            }
            for (int i = -1; i <= 24; i++) {
                int x = s.pixelX(i);
                if (i == -1) {
                    String connections =
                            rail.step == 1 ? "north=low,south=none" : "north=none,south=low";
                    assertEquals(
                            "minecraft:polished_deepslate_wall[east=none,"
                                    + connections
                                    + ",up=true,waterlogged=false,west=none]",
                            map.at(x, 1, t));
                } else if (i == 24) {
                    assertEquals("minecraft:polished_deepslate_wall", map.materialAt(x, 1, t));
                } else {
                    for (int y = 1; y <= 4; y++) assertTrue(map.air(x, y, t));
                    String tile =
                            switch (i) {
                                case 0 -> "waxed_cut_copper";
                                case 1 -> "ochre_froglight";
                                case 5, 10, 15, 20 -> "light_blue_concrete";
                                default -> "quartz_pillar[axis=x]";
                            };
                    assertEquals("minecraft:" + tile, map.at(x, 0, t));
                }
                if (i <= 0) assertTrue(map.air(x, 1, front));
                if (i == -1) {
                    assertEquals("minecraft:smooth_stone", map.at(x, 0, front));
                } else if (i == 0) {
                    assertEquals("minecraft:light_blue_concrete", map.at(x, 0, front));
                    assertTrue(map.air(x, 1, front + rail.step));
                } else
                    assertEquals("minecraft:polished_deepslate_wall", map.materialAt(x, 1, front));
                assertTrue(map.air(x, 2, front));
            }
            assertTrue(SliderHall.contains(s.pixelX(0), 1, t, false));
            assertTrue(SliderHall.contains(s.pixelX(0), 2, 28, true));
        }
        assertFalse(SliderHall.contains(9, 1, 38, false));
        assertTrue(SliderHall.contains(9, 1, 38, true));
        assertEquals(19, SliderHall.GRID.origin(0).getBlockZ());
        assertEquals(256, SliderHall.GRID.origin(1).getBlockX() - SliderHall.MIN_X);
    }

    private static String mirrorZ(String state) {
        String north = state.contains("north=low") ? "low" : "none";
        String south = state.contains("south=low") ? "low" : "none";
        return state.replace("north=" + north, "north=" + south)
                .replace("south=" + south, "south=" + north);
    }

    @Test
    void oppositeRailWallsHaveMirroredConnections() {
        for (var s : List.of(SliderHall.A, SliderHall.B)) {
            for (int col = -1; col <= 24; col++) {
                int x = s.pixelX(col);
                for (int z : new int[] {20, 21})
                    assertEquals(mirrorZ(map.at(x, 1, z)), map.at(x, 1, 57 - z));
            }
            String east = s == SliderHall.A ? "low" : "none";
            String west = s == SliderHall.A ? "none" : "low";
            assertEquals(
                    "minecraft:polished_deepslate_wall[east="
                            + east
                            + ",north=low,south=none,up=true,waterlogged=false,west="
                            + west
                            + "]",
                    map.at(s.pixelX(24), 1, 21));
        }
    }

    @Test
    void doorJambsCarryTheFourReadableMosaicsAndColoredPosts() {
        List<String> colors = List.of("blue", "orange", "lime", "purple");
        List<List<String>> glyphs =
                List.of(
                        List.of(".##.", "#..#", "####", "#..#"),
                        List.of("###.", "#ss#", "#..#", "###."),
                        List.of(".###", "#...", "#...", ".###"),
                        List.of("###.", "#..#", "#..#", "###."));
        for (var rail : RAILS) {
            int i = rail.hall.ordinal(), edge = rail.run < 0 ? 2 : 13;
            int wall = rail.step == 1 ? 19 : 38;
            String color = "minecraft:" + colors.get(i) + "_concrete";
            assertEquals(color, map.at(rail.zero - rail.run, 2, wall + rail.step));
            for (int row = 0; row < 4; row++) {
                assertEquals(color, map.at(edge - 1, 4 - row, wall));
                assertEquals(color, map.at(edge + 4, 4 - row, wall));
                for (int col = 0; col < 4; col++) {
                    int x = edge + (rail.step == 1 ? col : 3 - col);
                    String tile =
                            switch (glyphs.get(i).get(row).charAt(col)) {
                                case '#' -> "minecraft:smooth_quartz";
                                case 's' ->
                                        "minecraft:waxed_cut_copper_slab[type=top,waterlogged=false]";
                                default -> color;
                            };
                    assertEquals(tile, map.at(x, 4 - row, wall));
                }
            }
        }
        for (var old : List.of(new Vector(-20, 2, 20), new Vector(38, 2, 37))) {
            assertTrue(map.air(old.getBlockX(), 1, old.getBlockZ()));
            assertTrue(map.air(old.getBlockX(), 2, old.getBlockZ()));
            assertEquals("minecraft:smooth_stone", map.at(old.getBlockX(), 0, old.getBlockZ()));
        }
    }

    static boolean opaque(int x, int y, int z) {
        return solid[x + 35][y + 6][z - 19];
    }

    // Traverse the open segment; do not count the destination pixel itself at t=1.
    static boolean blocked(double ox, double oy, double oz, double ex, double ey, double ez) {
        int x = (int) Math.floor(ox), y = (int) Math.floor(oy), z = (int) Math.floor(oz);
        double dx = ex - ox, dy = ey - oy, dz = ez - oz;
        int sx = dx > 0 ? 1 : -1, sy = dy > 0 ? 1 : -1, sz = dz > 0 ? 1 : -1;
        double ax = Math.abs(1 / dx), ay = Math.abs(1 / dy), az = Math.abs(1 / dz);
        double tx = dx == 0 ? Double.POSITIVE_INFINITY : (x + (sx > 0 ? 1 : 0) - ox) / dx;
        double ty = (y + (sy > 0 ? 1 : 0) - oy) / dy, tz = (z + (sz > 0 ? 1 : 0) - oz) / dz;
        while (!opaque(x, y, z)) {
            double next = Math.min(tx, Math.min(ty, tz));
            if (next >= 1 - 1e-10) return false;
            if (tx <= next + 1e-10) {
                x += sx;
                tx += ax;
            }
            if (ty <= next + 1e-10) {
                y += sy;
                ty += ay;
            }
            if (tz <= next + 1e-10) {
                z += sz;
                tz += az;
            }
        }
        return true;
    }

    @Test
    void railAndCentralEyesSeeTheEntireMapPlane() {
        for (var target : SliderHall.values()) {
            List<Vector> eyes = new ArrayList<>();
            for (double x : new double[] {-24.5, 9.5, 42.5})
                for (double z : new double[] {22.5, 29, 35.5}) eyes.add(new Vector(x, 2.62, z));
            for (var rail : SliderHall.values())
                if (rail.step != target.step)
                    for (int i = 0; i < 24; i++)
                        eyes.add(
                                new Vector(
                                        rail.pixelX(i) + .5,
                                        2.62,
                                        rail.trench + .5 + .49 * rail.step));
            double face = target.wall + (target.step == 1 ? 1 : 0) + target.step / 32.0;
            for (int c = 0; c < SliderHall.MAP_COLUMNS; c++)
                for (int row = 0; row < SliderHall.MAP_ROWS; row++)
                    for (double[] uv :
                            new double[][] {
                                {.5, .5}, {.001, .001}, {.001, .999}, {.999, .001}, {.999, .999}
                            })
                        for (var eye : eyes)
                            assertFalse(
                                    blocked(
                                            eye.getX(),
                                            eye.getY(),
                                            eye.getZ(),
                                            target.mapX(c) + uv[0],
                                            target.mapY(row) + uv[1],
                                            face));
        }
    }

    @Test
    void outsideAndGapHaveShortestWalksFromEntranceWithoutEnteringGripCells() {
        var seed = new Vector(9, 1, 20);
        var distance = new HashMap<Vector, Integer>();
        var queue = new ArrayDeque<Vector>();
        distance.put(seed, 0);
        queue.add(seed);
        while (!queue.isEmpty()) {
            Vector from = queue.removeFirst();
            for (int[] d : new int[][] {{-1, 0}, {1, 0}, {0, -1}, {0, 1}}) {
                int x = from.getBlockX() + d[0], z = from.getBlockZ() + d[1];
                if (x < -26
                        || x > 44
                        || z < 20
                        || z > 37
                        || map.air(x, 0, z)
                        || !map.air(x, 1, z)
                        || !map.air(x, 2, z)) continue;
                if (RAILS.stream().anyMatch(r -> r.hall.holdingColumn(x, 1, z) >= 0)) continue;
                var next = new Vector(x, 1, z);
                if (distance.putIfAbsent(next, distance.get(from) + 1) == null) queue.add(next);
            }
        }
        for (var rail : RAILS) {
            int x = rail.outside.getBlockX(), z = rail.outside.getBlockZ();
            int outside = distance.getOrDefault(new Vector(x, 1, z), Integer.MAX_VALUE);
            int gap = distance.getOrDefault(new Vector(x, 1, z - rail.step), Integer.MAX_VALUE);
            assertTrue(outside <= (rail.step == 1 ? 6 : 19), rail.hall + " outside: " + outside);
            assertTrue(gap <= (rail.step == 1 ? 5 : 20), rail.hall + " gap: " + gap);
            for (int y = 1; y <= 5; y++) assertTrue(map.air(x, y, z));
        }
    }

    @Test
    void hallFloorHasNoRailColorsBeyondRailsAndGaps() {
        var colors =
                Set.of("blue", "orange", "lime", "purple", "light_blue", "yellow", "green", "pink");
        for (int x = 7; x <= 11; x++) {
            assertEquals("minecraft:white_concrete", map.at(x, 0, 20));
            assertEquals("minecraft:smooth_quartz", map.at(x, 0, 21));
        }
        // The hall floor lies between the two gate planes at z=19 and z=38.
        for (int z = 20; z <= 37; z++) {
            for (int x = -35; x <= 53; x++) {
                String tile = map.at(x, 0, z);
                if (z >= 22 && z <= 35 && x >= -25 && x <= 43)
                    assertEquals(
                            z >= 26 && z <= 31
                                    ? "minecraft:smooth_quartz"
                                    : "minecraft:smooth_stone",
                            tile);
                if (SliderHall.station(x, 1, z) != null) continue;
                boolean gap = (x == 5 || x == 13) && (z == 21 || z == 36);
                if (!gap)
                    for (String color : colors)
                        assertNotEquals("minecraft:" + color + "_concrete", tile, x + "," + z);
            }
        }
    }

    @Test
    void farthestFrameIsInTrackingRangeAcrossTheEntireHallFloor() {
        assertEquals(1024, 4 * 2 * SliderHall.MAP_COLUMNS * SliderHall.MAP_ROWS);
        // Include all four corners of every cell, even solid walls and the closed exit gate.
        for (int x = -26; x <= 45; x++)
            for (int z = 19; z <= 39; z++)
                for (var s : SliderHall.values())
                    for (int c = 0; c < SliderHall.MAP_COLUMNS; c++) {
                        double frameZ = s.wall + (s.step == 1 ? 1 : 0) + s.step / 32.0;
                        assertTrue(Math.hypot(s.mapX(c) + .5 - x, frameZ - z) <= 56);
                    }
        // Player-eye distance to the top row of each screen on every standable ground cell.
        for (int x = -26; x <= 44; x++)
            for (int z = 20; z <= 37; z++) {
                if (!map.air(x, 1, z) || !map.air(x, 2, z)) continue;
                for (var screen : SliderHall.values())
                    for (int col : new int[] {0, SliderHall.MAP_COLUMNS - 1}) {
                        double dx = screen.mapX(col) - x, dy = screen.mapY(0) + .5 - 2.62;
                        double dz =
                                screen.wall
                                        + (screen.step == 1 ? 1 : 0)
                                        + screen.step / 32.0
                                        - (z + .5);
                        assertTrue(dx * dx + dy * dy + dz * dz <= 56 * 56);
                    }
            }
        for (var s : new SliderHall[] {SliderHall.A, SliderHall.B}) {
            double eyeX = s.pixelX(12) + .5;
            double left = Math.min(s.mapX(0), s.mapX(15)) - eyeX, right = left + 16;
            double yaw = Math.atan2((left + right) / 2, 17.01);
            double pitch = Math.atan2(7, Math.hypot((left + right) / 2, 17.01));
            for (double dx : new double[] {left, right})
                for (double dy : new double[] {6 - 2.62, 14 - 2.62}) {
                    double dz = 38 - 1 / 16.0 - 20.99;
                    double level = dx * Math.sin(yaw) + dz * Math.cos(yaw);
                    double forward = level * Math.cos(pitch) + dy * Math.sin(pitch);
                    double horizontal = dz * Math.sin(yaw) - dx * Math.cos(yaw);
                    double vertical = dy * Math.cos(pitch) - level * Math.sin(pitch);
                    assertTrue(forward > 0);
                    assertTrue(
                            Math.toDegrees(Math.atan2(Math.abs(horizontal), forward)) <= 55 / 2.0);
                    assertTrue(Math.toDegrees(Math.atan2(Math.abs(vertical), forward)) <= 35 / 2.0);
                }
        }
    }

    @Test
    void proportionalPuzzleFitsOnScreenWithSpaceBetweenRoundTwoNotches() {
        assertEquals(.4, SliderHall.coreLeft(0), 1e-9);
        assertEquals(15.6, SliderHall.coreLeft(22) + SliderHall.CORE_SIZE, 1e-9);
        for (int c = 0; c <= 17; c++)
            assertTrue(
                    SliderHall.coreLeft(c + 5) - SliderHall.coreLeft(c)
                            > SliderHall.CORE_SIZE + SliderHall.OUTLINE);
        for (int row = 1; row <= 7; row++) {
            assertTrue(SliderHall.coreTop(row) - SliderHall.TAB_SIZE - SliderHall.OUTLINE / 2 >= 0);
            assertTrue(
                    SliderHall.coreTop(row)
                                    + SliderHall.CORE_SIZE
                                    + SliderHall.TAB_SIZE
                                    + SliderHall.OUTLINE / 2
                            <= SliderHall.MAP_ROWS);
        }
    }
}
