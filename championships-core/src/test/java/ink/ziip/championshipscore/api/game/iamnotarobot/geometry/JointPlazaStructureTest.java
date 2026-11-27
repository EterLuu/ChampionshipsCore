package ink.ziip.championshipscore.api.game.iamnotarobot.geometry;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.api.game.iamnotarobot.geometry.JointPlaza.Side;

import org.bukkit.util.Vector;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Bundled-template contracts and outdoor construction site walkable geometry, piston faces, and
 * sightlines.
 */
class JointPlazaStructureTest {
    private static VerificationCourseTest.Blocks map;
    private static final boolean[][][] OPAQUE =
            new boolean[JointPlaza.WIDTH][JointPlaza.HEIGHT][JointPlaza.LENGTH];
    private static final Set<String> TRANSPARENT =
            Set.of(
                    "air",
                    "cave_air",
                    "void_air",
                    "light",
                    "barrier",
                    "glass",
                    "glass_pane",
                    "tinted_glass",
                    "iron_bars",
                    "lantern",
                    "water",
                    "heavy_weighted_pressure_plate",
                    "poppy",
                    "dandelion",
                    "scaffolding",
                    "grindstone");
    private static final Set<String> FULL =
            Set.of(
                    "polished_basalt",
                    "smooth_quartz",
                    "quartz_block",
                    "smooth_stone",
                    "stone",
                    "cobblestone",
                    "andesite",
                    "polished_andesite",
                    "tuff",
                    "polished_tuff",
                    "deepslate",
                    "cobbled_deepslate",
                    "polished_deepslate",
                    "deepslate_tiles",
                    "calcite",
                    "iron_block",
                    "redstone_lamp",
                    "moss_block",
                    "bricks",
                    "polished_blackstone",
                    "chiseled_quartz_block",
                    "dark_prismarine",
                    "prismarine",
                    "cut_sandstone",
                    "smooth_sandstone",
                    "lapis_block",
                    "gold_block",
                    "grass_block",
                    "polished_diorite",
                    "stripped_birch_wood",
                    "chiseled_stone_bricks",
                    "coarse_dirt",
                    "packed_mud",
                    "waxed_copper_block");

    private record Point(double x, double y, double z) {
        double distance(Point other) {
            return Math.sqrt(
                    Math.pow(x - other.x, 2) + Math.pow(y - other.y, 2) + Math.pow(z - other.z, 2));
        }
    }

    @BeforeAll
    static void read() throws Exception {
        map =
                new VerificationCourseTest()
                        .read(
                                JointPlaza.STRUCTURE,
                                JointPlaza.WIDTH,
                                JointPlaza.HEIGHT,
                                JointPlaza.LENGTH,
                                JointPlaza.MIN_X,
                                JointPlaza.MIN_Y,
                                JointPlaza.MIN_Z);
        map.data()
                .forEach(
                        (p, name) ->
                                OPAQUE[p.getBlockX() - JointPlaza.MIN_X][
                                                p.getBlockY() - JointPlaza.MIN_Y][
                                                p.getBlockZ() - JointPlaza.MIN_Z] =
                                        opaque(material(name)));
    }

    private static String material(String name) {
        return name.substring("minecraft:".length()).split("\\[")[0];
    }

    private static String at(int x, int y, int z) {
        return material(map.at(x, y, z));
    }

    private static boolean opaque(String b) {
        if (TRANSPARENT.contains(b)
                || endsWith(
                        b,
                        "_carpet",
                        "_button",
                        "_fence",
                        "_glass",
                        "_glass_pane",
                        "_grate",
                        "_slab",
                        "_stairs",
                        "_wall",
                        "_bars",
                        "_leaves",
                        "_trapdoor",
                        "_banner",
                        "_sign")) return false;
        if (FULL.contains(b)
                || endsWith(
                        b,
                        "_concrete",
                        "_wool",
                        "_terracotta",
                        "_planks",
                        "_log",
                        "_froglight",
                        "_copper",
                        "_bricks",
                        "_pillar")) return true;
        throw new AssertionError("Classify material before running LOS: " + b);
    }

    private static boolean endsWith(String value, String... suffixes) {
        for (String suffix : suffixes) if (value.endsWith(suffix)) return true;
        return false;
    }

    private static boolean inside(int x, int y, int z) {
        return x >= JointPlaza.MIN_X
                && x < JointPlaza.MIN_X + JointPlaza.WIDTH
                && y >= JointPlaza.MIN_Y
                && y < JointPlaza.MIN_Y + JointPlaza.HEIGHT
                && z >= JointPlaza.MIN_Z
                && z < JointPlaza.MIN_Z + JointPlaza.LENGTH;
    }

    private static boolean solid(int x, int y, int z) {
        return inside(x, y, z)
                && OPAQUE[x - JointPlaza.MIN_X][y - JointPlaza.MIN_Y][z - JointPlaza.MIN_Z];
    }

    private static void region(
            int x0, int x1, int y0, int y1, int z0, int z1, Predicate<Point> predicate) {
        for (int x = x0; x <= x1; x++)
            for (int y = y0; y <= y1; y++)
                for (int z = z0; z <= z1; z++) {
                    Point p = new Point(x, y, z);
                    assertTrue(predicate.test(p), p.toString());
                }
    }

    private static String at(Point p) {
        return at((int) p.x, (int) p.y, (int) p.z);
    }

    @Test
    void everyGeometryTableRowAndMaterialBanHolds() {
        region(7, 11, 1, 4, 39, 42, p -> at(p).equals("air") || at(p).equals("light"));
        for (Side side : Side.values()) {
            int min = side == Side.WEST ? JointPlaza.GRAY_MIN_X : JointPlaza.WHITE_MIN_X;
            String concrete = side == Side.WEST ? "gray_concrete" : "white_concrete";
            region(
                    min,
                    min + 8,
                    JointPlaza.BLOCK_MIN_Y,
                    JointPlaza.BLOCK_MAX_Y,
                    JointPlaza.BLOCK_MIN_Z,
                    JointPlaza.BLOCK_MAX_Z,
                    p ->
                            at(p).equals(
                                            (side == Side.WEST ? p.x() <= -2 : p.x() >= 19)
                                                    ? "light_gray_concrete"
                                                    : concrete));
        }
        region(
                7,
                10,
                1,
                JointPlaza.BLOCK_MAX_Y + 4,
                JointPlaza.BLOCK_MIN_Z,
                JointPlaza.BLOCK_MAX_Z,
                p -> at(p).equals("air") || at(p).equals("light"));
        region(7, 10, 0, 0, JointPlaza.BLOCK_MIN_Z, JointPlaza.BLOCK_MAX_Z, p -> opaque(at(p)));
        var colors = List.of("cyan", "light_blue", "blue", "purple", "magenta", "orange");
        for (int rulerZ : new int[] {JointPlaza.BLOCK_MIN_Z - 1, JointPlaza.BLOCK_MAX_Z + 1}) {
            for (int d = 1; d <= 6; d++) {
                assertEquals(
                        colors.get(d - 1) + "_concrete",
                        at(JointPlaza.GRAY_MAX_X + 1 - d, 0, rulerZ));
                assertEquals(
                        at(JointPlaza.GRAY_MAX_X + 1 - d, 0, rulerZ),
                        at(JointPlaza.WHITE_BASE_X - d, 0, rulerZ));
            }
        }
        region(
                7,
                11,
                1,
                4,
                JointPlaza.EXIT_Z,
                JointPlaza.EXIT_Z,
                p -> at(p).equals("red_stained_glass"));
        map.data()
                .forEach(
                        (p, state) -> {
                            String name = material(state);
                            int x = p.getBlockX(), y = p.getBlockY(), z = p.getBlockZ();
                            if (name.equals("gray_concrete") || name.equals("white_concrete")) {
                                int min =
                                        name.equals("gray_concrete")
                                                ? JointPlaza.GRAY_MIN_X
                                                : JointPlaza.WHITE_MIN_X;
                                assertTrue(
                                        x >= min
                                                && x <= min + 8
                                                && y >= JointPlaza.BLOCK_MIN_Y
                                                && y <= JointPlaza.BLOCK_MAX_Y
                                                && z >= JointPlaza.BLOCK_MIN_Z
                                                && z <= JointPlaza.BLOCK_MAX_Z,
                                        p.toString());
                            }
                            assertFalse(name.equals("sea_lantern"));
                            boolean button =
                                    x >= 8
                                            && x <= 10
                                            && y >= 1
                                            && y <= 3
                                            && z == JointPlaza.BUTTON_Z;
                            assertEquals(button, JointPlaza.button(x, y, z));
                            if (button) assertEquals("red_concrete", name);
                        });
        assertEquals(3, JointPlaza.ROUND_LAMPS.size());
        for (int y = 1; y <= 3; y++) {
            var pair =
                    List.of(
                            new JointPlaza.Cell(11, y, JointPlaza.BUTTON_Z),
                            new JointPlaza.Cell(7, y, JointPlaza.BUTTON_Z));
            assertEquals(pair, JointPlaza.ROUND_LAMPS.get(y - 1));
            for (var lamp : pair)
                assertEquals("light_gray_concrete", at(lamp.x(), lamp.y(), lamp.z()));
        }
        assertTrue(map.blockEntities().isEmpty());
        assertEquals(0, map.entities());
    }

    @Test
    void fixedVehiclesHavePistonFacesAndTheRearViewingDeckStairsStayRemoved() {
        for (int plate : new int[] {JointPlaza.GRAY_MIN_X - 1, JointPlaza.FACE_X})
            for (int y = 2; y <= 8; y++)
                for (int z = 60; z <= 66; z++) {
                    boolean corner = (y == 2 || y == 8) && (z == 60 || z == 66);
                    assertEquals(corner ? "iron_block" : "oak_planks", at(plate, y, z));
                }
        region(
                JointPlaza.GRAY_MIN_X,
                JointPlaza.WHITE_MAX_X,
                1,
                1,
                60,
                66,
                p -> at(p).equals("air"));
        for (int x : new int[] {-18, -17, 35, 36})
            region(x, x, 1, 10, 72, 81, p -> at(p).equals("air") || at(p).equals("light"));
        for (int x : new int[] {-19, 37})
            for (int z = 40; z < 82; z++)
                assertTrue(Set.of("barrier", "polished_deepslate_wall").contains(at(x, 6, z)));
        assertTrue(map.at(-10, 12, 69).contains("east=true"));
        assertTrue(map.at(-10, 12, 69).contains("west=true"));
        assertTrue(
                map.data().values().stream()
                        .noneMatch(s -> s.contains("scaffolding[") && s.contains("distance=7")));
    }

    @Test
    void legalStandSurfacesHaveSupportAndHeadroom() {
        for (var box : JointPlaza.WALKS) {
            for (int x = (int) box.x0(); x < box.x1(); x++)
                for (int z = (int) box.z0(); z < box.z1(); z++) {
                    double y = box.y0();
                    assertTrue(JointPlaza.contains(x + .5, y, z + .25, false));
                    int support = (int) Math.ceil(y) - 1;
                    String below = at(x, support, z);
                    assertFalse(
                            Set.of("air", "light").contains(below),
                            box + " at (" + x + "," + support + "," + z + ")=" + below);
                    if (y % 1 == .5)
                        assertTrue(
                                below.endsWith("_stairs") || below.endsWith("_slab"),
                                box + " below=" + below);
                    if (y % 1 == .0625)
                        assertTrue(below.endsWith("_carpet"), box + " below=" + below);
                    for (int h = support + 1; h <= support + 2; h++)
                        assertTrue(
                                Set.of("air", "light").contains(at(x, h, z)),
                                box
                                        + " headroom at ("
                                        + x
                                        + ","
                                        + h
                                        + ","
                                        + z
                                        + ")="
                                        + at(x, h, z));
                }
        }
        for (int x = JointPlaza.WEST_TRUCK_MIN_X; x <= JointPlaza.EAST_TRUCK_MAX_X; x++)
            for (int z = JointPlaza.BLOCK_MIN_Z; z <= JointPlaza.BLOCK_MAX_Z; z++)
                for (int y = 1; y <= JointPlaza.BLOCK_MAX_Y; y++) {
                    if (JointPlaza.vehicleSide(x, y, z) != null)
                        assertFalse(JointPlaza.contains(x + .5, y, z + .5, false));
                }
        for (int x = -1; x <= 20; x++) {
            Vector e = JointPlaza.ejectTarget(x + .5);
            assertTrue(JointPlaza.contains(e.getX(), e.getY(), e.getZ(), false));
            assertTrue(opaque(at(e.getBlockX(), 0, e.getBlockZ())));
            assertTrue(
                    Set.of("air", "light").contains(at(e.getBlockX(), 1, e.getBlockZ()))
                            && Set.of("air", "light")
                                    .contains(at(e.getBlockX(), 2, e.getBlockZ())));
        }
    }

    @Test
    void stairsConnectBothCabRoofsToTheConcreteWithHeadroom() {
        for (Side side : Side.values()) {
            boolean west = side == Side.WEST;
            for (int z = 58; z < 60; z++) {
                for (int step = 0; step < 8; step++) {
                    int x = west ? -17 + step : 34 - step;
                    int y = 1 + step;
                    String stair = map.at(x, y, z);
                    assertTrue(
                            stair.contains(
                                    "stairs[facing="
                                            + (west ? "east" : "west")
                                            + ",half=bottom,shape=straight"));
                    assertTrue(JointPlaza.contains(x + (west ? .25 : .75), y + .5, z + .5, false));
                    assertTrue(JointPlaza.contains(x + (west ? .75 : .25), y + 1, z + .5, false));
                }
            }
            // From the landing onto the cab, across the mast, then along the top of the load.
            for (int z = 58; z <= 62; z++) assertRoof(west ? -8 : 25, z);
            for (int x = -8; x <= JointPlaza.GRAY_MAX_X; x++) assertRoof(west ? x : 17 - x, 62);
            for (int x = JointPlaza.GRAY_MIN_X; x <= JointPlaza.GRAY_MAX_X; x++)
                for (int z = JointPlaza.BLOCK_MIN_Z; z <= JointPlaza.BLOCK_MAX_Z; z++)
                    assertRoof(west ? x : 17 - x, z);
        }
    }

    private static void assertRoof(int x, int z) {
        assertTrue(solid(x, 8, z) || at(x, 8, z).equals("yellow_stained_glass"));
        assertTrue(Set.of("air", "light").contains(at(x, 9, z)));
        assertTrue(Set.of("air", "light").contains(at(x, 10, z)));
        assertTrue(JointPlaza.contains(x + .5, 9, z + .5, false));
        assertTrue(JointPlaza.contains(x + .5, 10.25, z + .5, false));
    }

    @Test
    void sideBarriersContinueAboveTheHighestWalkableRoofAndDeck() {
        for (int x : new int[] {-19, 37})
            for (int z = 40; z < 82; z++)
                for (int y = 7; y <= 14; y++)
                    assertTrue(
                            Set.of(
                                            "barrier",
                                            "iron_bars",
                                            "polished_deepslate_wall",
                                            "polished_deepslate")
                                    .contains(at(x, y, z)),
                            x + "," + y + "," + z);
    }

    /** Amanatides-Woo, with tied axes advanced together and an open target endpoint. */
    private static String ray(Point eye, Point target, boolean beyond) {
        return ray(eye, target, beyond, null);
    }

    private static String ray(Point eye, Point target, boolean beyond, JointPlaza.Cell own) {
        double[] start = {eye.x, eye.y, eye.z};
        double[] direction = {target.x - eye.x, target.y - eye.y, target.z - eye.z};
        int[] cell = new int[3], step = new int[3];
        double[] increment = new double[3], time = new double[3];
        for (int k = 0; k < 3; k++) {
            cell[k] = (int) Math.floor(start[k]);
            step[k] = direction[k] > 0 ? 1 : -1;
            increment[k] =
                    direction[k] == 0 ? Double.POSITIVE_INFINITY : 1 / Math.abs(direction[k]);
            time[k] =
                    direction[k] == 0
                            ? Double.POSITIVE_INFINITY
                            : (cell[k] + (step[k] > 0 ? 1 : 0) - start[k]) / direction[k];
        }
        while (true) {
            if (beyond && !inside(cell[0], cell[1], cell[2])) return "sky";
            if (solid(cell[0], cell[1], cell[2])
                    && (own == null
                            || own.x() != cell[0]
                            || own.y() != cell[1]
                            || own.z() != cell[2])) return at(cell[0], cell[1], cell[2]);
            double t = Math.min(time[0], Math.min(time[1], time[2]));
            if (!beyond && t >= 1 - 1e-10) return "sky";
            for (int k = 0; k < 3; k++)
                if (time[k] <= t + 1e-10) {
                    cell[k] += step[k];
                    time[k] += increment[k];
                }
        }
    }

    private static void visibility(
            String label, List<Point> eyes, List<Point> faces, double minimum) {
        int visible = 0, clear = 0;
        for (Point target : faces) {
            int successes = 0;
            for (Point eye : eyes) if (ray(eye, target, false).equals("sky")) successes++;
            if (successes > 0) visible++;
            clear += successes;
        }
        System.out.printf(
                Locale.ROOT,
                "LOS %s: faces %d/%d, clear rays %d/%d, eyes %d%n",
                label,
                visible,
                faces.size(),
                clear,
                eyes.size() * faces.size(),
                eyes.size());
        assertTrue((double) visible / faces.size() >= minimum, label);
    }

    private static List<Point> deckEyes() {
        List<Point> eyes = new ArrayList<>();
        for (int x = -14; x < 2; x++) eyes.add(new Point(x + .5, 13.62, 70.5));
        for (int x = 17; x < 33; x++) eyes.add(new Point(x + .5, 13.62, 70.5));
        return eyes;
    }

    private static List<Point> frontEyes() {
        List<Point> eyes = new ArrayList<>();
        for (int x = -2; x < 24; x++) eyes.add(new Point(x + .5, 2.62, 57.5));
        return eyes;
    }

    private static final List<Point> ENTRANCES =
            List.of(
                    new Point(7.3, 2.62, 41.5),
                    new Point(9.5, 2.62, 41.5),
                    new Point(11.7, 2.62, 41.5));

    private static List<Integer> loadXs() {
        List<Integer> xs = new ArrayList<>();
        for (int x = JointPlaza.GRAY_MIN_X; x <= JointPlaza.WHITE_MAX_X; x++)
            if (x <= JointPlaza.GRAY_MAX_X || x >= JointPlaza.WHITE_MIN_X) xs.add(x);
        return xs;
    }

    @Test
    void topFrontChannelAndRearFacesAreVisible() {
        List<Point> top = new ArrayList<>(),
                front = new ArrayList<>(),
                rear = new ArrayList<>(),
                rearEyes = new ArrayList<>();
        for (int x : loadXs()) {
            rearEyes.add(new Point(x + .5, 2.62, 68.5));
            for (int z = JointPlaza.BLOCK_MIN_Z; z <= JointPlaza.BLOCK_MAX_Z; z++)
                top.add(new Point(x + .5, 9, z + .5));
            for (int y = JointPlaza.BLOCK_MIN_Y; y <= JointPlaza.BLOCK_MAX_Y; y++) {
                front.add(new Point(x + .5, y + .5, JointPlaza.BLOCK_MIN_Z));
                rear.add(new Point(x + .5, y + .5, JointPlaza.BLOCK_MAX_Z + 1));
            }
        }
        visibility("1 deck", deckEyes(), top, 1);
        for (Point t : top)
            assertTrue(
                    deckEyes().stream()
                            .anyMatch(
                                    e ->
                                            e.distance(t) <= 14
                                                    && depression(e, t) >= 20
                                                    && ray(e, t, false).equals("sky")),
                    "top cell " + t);
        visibility("2 front", frontEyes(), front, 1);
        List<Point> channel = new ArrayList<>(), eyes = new ArrayList<>();
        for (double x : new double[] {7.3, 8.5, 9.7})
            for (double y : new double[] {1.62, 2.62})
                for (int z = JointPlaza.BLOCK_MIN_Z; z <= JointPlaza.BLOCK_MAX_Z; z++)
                    eyes.add(new Point(x, y, z + .5));
        for (int x : new int[] {JointPlaza.GRAY_MAX_X + 1, JointPlaza.WHITE_MIN_X})
            for (int y = JointPlaza.BLOCK_MIN_Y; y <= JointPlaza.BLOCK_MAX_Y; y++)
                for (int z = JointPlaza.BLOCK_MIN_Z; z <= JointPlaza.BLOCK_MAX_Z; z++)
                    channel.add(new Point(x, y + .5, z + .5));
        visibility("3 channel", eyes, channel, 1);
        visibility("4 rear", rearEyes, rear, 1);
    }

    @Test
    void entranceFacesLampsAndBoardMeetTheApprovedSightlines() {
        List<Point> focus = new ArrayList<>();
        for (int x : loadXs())
            for (int y = JointPlaza.BLOCK_MIN_Y; y <= JointPlaza.BLOCK_MAX_Y; y++)
                focus.add(new Point(x + .5, y + .5, JointPlaza.BLOCK_MIN_Z));
        for (var cell : JointPlaza.BUTTON_CELLS)
            focus.add(new Point(cell.x() + .5, cell.y() + .5, cell.z()));
        for (Point eye : ENTRANCES) visibility("7 entrance focal faces", List.of(eye), focus, .90);
        List<Point> panelEyes = new ArrayList<>(ENTRANCES);
        panelEyes.add(new Point(9.5, 2.62, JointPlaza.BUTTON_Z - 3.5));
        var samples = new ArrayList<Point>();
        List<JointPlaza.Cell> faces = new ArrayList<>(JointPlaza.BUTTON_CELLS);
        JointPlaza.ROUND_LAMPS.forEach(faces::addAll);
        for (var cell : faces) {
            samples.add(new Point(cell.x() + .5, cell.y() + .5, JointPlaza.BUTTON_Z));
            for (double dx : new double[] {.001, .999})
                for (double dy : new double[] {.001, .999})
                    samples.add(new Point(cell.x() + dx, cell.y() + dy, JointPlaza.BUTTON_Z));
        }
        for (Point eye : panelEyes) visibility("2 panel and lamps", List.of(eye), samples, 1);
        var label = JointPlaza.BUTTON_LABEL;
        assertEquals(new JointPlaza.Face(9.5, 2.5, JointPlaza.BUTTON_Z - .04, 2.6, .6, 180), label);
        for (Point eye :
                List.of(
                        new Point(9.5, 2.62, 46.5),
                        new Point(9.5, 2.62, JointPlaza.BUTTON_Z - 3.5))) {
            for (var face : List.of(JointPlaza.TITLE, JointPlaza.STATUS, JointPlaza.TRACK)) {
                assertEquals(180, face.yaw());
                List<Point> area = new ArrayList<>();
                for (int i = 0; i <= 32; i++)
                    for (int row = 0; row <= 4; row++) {
                        Point t =
                                new Point(
                                        face.x() + face.width() * (i / 32d - .5),
                                        face.y() + face.height() * (row / 4d - .5),
                                        face.z());
                        area.add(t);
                        double u = (label.z() - eye.z) / (t.z - eye.z);
                        double lx = eye.x + u * (t.x - eye.x), ly = eye.y + u * (t.y - eye.y);
                        assertTrue(
                                Math.abs(lx - label.x()) > label.width() / 2
                                        || Math.abs(ly - label.y()) > label.height() / 2);
                    }
                visibility("5 board", List.of(eye), area, .95);
            }
        }
        assertTrue(
                -depression(
                                new Point(9.5, 2.62, 49.5),
                                new Point(
                                        JointPlaza.TITLE.x(),
                                        JointPlaza.TITLE.y(),
                                        JointPlaza.TITLE.z()))
                        <= 38);
        silhouette(
                "white",
                JointPlaza.WHITE_MIN_X,
                JointPlaza.WHITE_MAX_X + 1,
                List.of(
                        "quartz",
                        "white",
                        "calcite",
                        "diorite",
                        "birch",
                        "snow",
                        "bone",
                        "light_gray"));
        silhouette(
                "gray",
                JointPlaza.GRAY_MIN_X,
                JointPlaza.GRAY_MAX_X + 1,
                List.of("gray", "deepslate", "black", "basalt", "cyan_terracotta"));
    }

    private static Projection project(Point eye, Point target, double yaw) {
        double dx = target.x - eye.x, dz = target.z - eye.z, a = Math.toRadians(yaw);
        double depth = dx * Math.sin(a) + dz * Math.cos(a);
        double width = depth * Math.tan(Math.toRadians(51));
        return new Projection(
                (-dx * Math.cos(a) + dz * Math.sin(a)) / width,
                (target.y - eye.y) / width * 16 / 9);
    }

    private static boolean inFrame(Point eye, Point target) {
        Projection p = project(eye, target, 0);
        return Math.abs(p.x) <= 1 && Math.abs(p.y) <= 1;
    }

    private static boolean visibleCell(Point eye, JointPlaza.Cell cell) {
        int x = cell.x(), y = cell.y(), z = cell.z();
        Point middle = new Point(x + .5, y + .5, z + .5);
        if (inFrame(eye, middle) && ray(eye, middle, false, cell).equals("sky")) return true;
        for (int[] normal : NORMALS) {
            int dx = normal[0], dy = normal[1], dz = normal[2];
            if (solid(x + dx, y + dy, z + dz)) continue;
            Point face = new Point(middle.x + dx * .5, middle.y + dy * .5, middle.z + dz * .5);
            if ((eye.x - face.x) * dx + (eye.y - face.y) * dy + (eye.z - face.z) * dz > 0
                    && inFrame(eye, face)
                    && ray(eye, face, false, cell).equals("sky")) return true;
        }
        return false;
    }

    private static final int[][] NORMALS = {
        {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}
    };

    @Test
    void vehiclesAndLoadTopsFitThePlayerFrustum() {
        Point forecourt = new Point(9.5, 2.62, 48.5);
        for (Side side : Side.values()) {
            int lo = side == Side.WEST ? JointPlaza.WEST_TRUCK_MIN_X : JointPlaza.WHITE_MAX_X + 1;
            int hi = side == Side.WEST ? JointPlaza.GRAY_MIN_X : JointPlaza.EAST_TRUCK_MAX_X + 1;
            List<JointPlaza.Cell> truck = new ArrayList<>();
            for (int x = lo; x < hi; x++)
                for (int y = JointPlaza.TRUCK_MIN_Y; y <= JointPlaza.TRUCK_TOP_Y; y++)
                    for (int z = JointPlaza.BLOCK_MIN_Z; z <= JointPlaza.BLOCK_MAX_Z; z++) {
                        if (at(x, y, z).equals("air")) continue;
                        boolean exposed = false;
                        for (int[] normal : NORMALS)
                            if (!solid(x + normal[0], y + normal[1], z + normal[2])) exposed = true;
                        if (!exposed) continue;
                        truck.add(new JointPlaza.Cell(x, y, z));
                        for (int xx = x; xx <= x + 1; xx++)
                            for (int zz = z; zz <= z + 1; zz++)
                                assertTrue(
                                        Math.abs(
                                                        project(
                                                                        forecourt,
                                                                        new Point(xx, y, zz),
                                                                        side == Side.WEST
                                                                                ? -45
                                                                                : 45)
                                                                .x)
                                                <= 1);
                    }
            for (Point eye : ENTRANCES) {
                var visible = truck.stream().filter(cell -> visibleCell(eye, cell)).toList();
                assertTrue(
                        visible.size() * 6 >= truck.size(),
                        side + " " + eye + " " + visible.size() + "/" + truck.size());
                System.out.printf(
                        "LOS 7 %s from %.1f: %d/%d truck cells%n",
                        side, eye.x, visible.size(), truck.size());
                var features =
                        List.<Predicate<JointPlaza.Cell>>of(
                                c -> c.y() == 8 && at(c.x(), c.y(), c.z()).equals("iron_block"),
                                c -> c.y() == 8 && at(c.x(), 8, c.z()).equals("yellow_concrete"),
                                c -> at(c.x(), c.y(), c.z()).equals("orange_concrete"),
                                c ->
                                        c.y() <= 2
                                                && c.z() == JointPlaza.BLOCK_MIN_Z
                                                && at(c.x(), c.y(), c.z()).equals("black_concrete"),
                                c ->
                                        counterweight(side, c)
                                                && at(c.x(), c.y(), c.z())
                                                        .equals("yellow_concrete"),
                                c ->
                                        c.y() == 2
                                                && c.z() == JointPlaza.BLOCK_MIN_Z
                                                && at(c.x(), c.y(), c.z()).equals("iron_block"));
                for (int i = 0; i < features.size(); i++)
                    assertTrue(
                            truck.stream()
                                    .filter(features.get(i))
                                    .anyMatch(c -> visibleDetail(eye, c)),
                            side
                                    + " "
                                    + eye
                                    + " "
                                    + List.of(
                                                    "piston face",
                                                    "cab roof",
                                                    "beacon",
                                                    "wheel",
                                                    "yellow counterweight",
                                                    "wheel hub")
                                            .get(i));
                assertTrue(project(eye, new Point(lo, 9, JointPlaza.BLOCK_MIN_Z), 0).y < 1);
            }
        }
    }

    private static boolean counterweight(Side side, JointPlaza.Cell cell) {
        int x = side == Side.WEST ? cell.x() : 17 - cell.x();
        return x <= -11 && cell.y() >= 2 && cell.y() <= 5;
    }

    private static boolean visibleDetail(Point eye, JointPlaza.Cell cell) {
        int[] base = {cell.x(), cell.y(), cell.z()};
        double[] origin = {eye.x, eye.y, eye.z};
        for (int axis = 0; axis < 3; axis++)
            for (int sign : new int[] {-1, 1}) {
                double face = base[axis] + (sign > 0 ? 1 : 0);
                if ((origin[axis] - face) * sign <= 0) continue;
                int u = (axis + 1) % 3, v = (axis + 2) % 3;
                double[] p = new double[3];
                p[axis] = face;
                for (int i = 0; i < 32; i++)
                    for (int j = 0; j < 32; j++) {
                        p[u] = base[u] + (i + .5) / 32;
                        p[v] = base[v] + (j + .5) / 32;
                        Point target = new Point(p[0], p[1], p[2]);
                        if (inFrame(eye, target) && ray(eye, target, false, cell).equals("sky"))
                            return true;
                    }
            }
        return false;
    }

    private static double depression(Point eye, Point target) {
        return Math.toDegrees(
                Math.atan2(eye.y - target.y, Math.hypot(eye.x - target.x, eye.z - target.z)));
    }

    private record Projection(double x, double y) {}

    private static double cross(Projection a, Projection b, Projection c) {
        return (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x);
    }

    private static List<Projection> chain(List<Projection> ordered) {
        List<Projection> result = new ArrayList<>();
        for (Projection p : ordered) {
            while (result.size() >= 2
                    && cross(result.get(result.size() - 2), result.getLast(), p) <= 0)
                result.removeLast();
            result.add(p);
        }
        result.removeLast();
        return result;
    }

    private static List<Point> projectedOutline(Point eye, int lo, int hi) {
        Set<Projection> points = new HashSet<>();
        for (int x : new int[] {lo, hi})
            for (int y : new int[] {JointPlaza.BLOCK_MIN_Y, JointPlaza.BLOCK_MAX_Y + 1})
                for (int z : new int[] {JointPlaza.BLOCK_MIN_Z, JointPlaza.BLOCK_MAX_Z + 1})
                    points.add(
                            new Projection(
                                    eye.x + (x - eye.x) * 2.5 / (z - eye.z),
                                    eye.y + (y - eye.y) * 2.5 / (z - eye.z)));
        List<Projection> sorted =
                points.stream()
                        .sorted(
                                Comparator.comparingDouble(Projection::x)
                                        .thenComparingDouble(Projection::y))
                        .toList();
        List<Projection> hull = chain(sorted);
        hull.addAll(chain(sorted.reversed()));
        List<Point> outline = new ArrayList<>();
        for (int edge = 0; edge < hull.size(); edge++) {
            Projection a = hull.get(edge), b = hull.get((edge + 1) % hull.size());
            double dx = b.x - a.x, dy = b.y - a.y, length = Math.hypot(dx, dy);
            int count = (int) Math.ceil(length / .5);
            for (int i = 0; i < count; i++) {
                double t = (i + .5) / count;
                outline.add(
                        new Point(
                                a.x + t * dx + .3 * dy / length,
                                a.y + t * dy - .3 * dx / length,
                                JointPlaza.BLOCK_MIN_Z));
            }
        }
        return outline;
    }

    private static void silhouette(String label, int lo, int hi, List<String> banned) {
        int total = 0, passed = 0;
        for (Point eye : frontEyes())
            for (Point point : projectedOutline(eye, lo, hi)) {
                String hit = ray(eye, point, true);
                total++;
                if (banned.stream().noneMatch(hit::contains)) passed++;
            }
        System.out.printf(
                Locale.ROOT,
                "LOS 6 %s silhouette: %d/%d = %.2f%%%n",
                label,
                passed,
                total,
                100.0 * passed / total);
        assertTrue((double) passed / total >= .9, label);
    }
}
