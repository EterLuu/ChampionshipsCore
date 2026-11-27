package ink.ziip.championshipscore.api.game.iamnotarobot.model;

import ink.ziip.championshipscore.api.game.iamnotarobot.geometry.SliderHall;
import ink.ziip.championshipscore.platform.bukkit.bingo.map.MapColorMatcher;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Random;

/**
 * Original vector postcards. All drawing and colour matching can run headless off the game thread.
 */
public final class SliderArt {
    public static final int WIDTH = 128 * SliderHall.MAP_COLUMNS,
            HEIGHT = 128 * SliderHall.MAP_ROWS;

    public enum Scene {
        SKYLINE,
        BRIDGE,
        WHEEL,
        HARBOUR,
        METRO,
        BOULEVARD,
        AIRFIELD,
        STADIUM,
        METRO_PLATFORM,
        HSR_STATION,
        INTERCHANGE,
        SEA_BRIDGE,
        CBD_PLAZA,
        TV_TOWER,
        PARK_LAKE,
        BEACH,
        MARINA,
        ROOFTOP_GARDEN,
        SNOW_TRAM,
        RAIN_STREET,
        ART_MUSEUM,
        WIND_COAST
    }

    public enum Time {
        SUNRISE(0x8ea5cb, 0xd6b4aa, 0xd1b1a1, 0xd87f33, 0xaba5a6),
        NOON(0x6699d8, 0x8db4cf, 0xb4b1ac, 0xe9aa48, 0x91a4ae),
        DUSK(0x95576c, 0xd77a88, 0xf3b38c, 0xecac57, 0x817487),
        NIGHT(0x344a72, 0x506c89, 0x8497ab, 0xd1b1a1, 0x617184);
        final int sky, band, horizon, sun, distance;

        Time(int sky, int band, int horizon, int sun, int distance) {
            this.sky = sky;
            this.band = band;
            this.horizon = horizon;
            this.sun = sun;
            this.distance = distance;
        }
    }

    public record Postcard(Scene scene, Time time, long seed) {
        public BufferedImage draw() {
            return draw(WIDTH, HEIGHT);
        }

        public BufferedImage draw(int width, int height) {
            if (width < 2 || height < 1 || (long) height * 2 != width)
                throw new IllegalArgumentException("A postcard has a 2:1 aspect ratio");
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = image.createGraphics();
            try {
                g.setRenderingHint(
                        RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.scale(width / 640.0, height / 320.0);
                if ((seed & 1) != 0) {
                    g.translate(640, 0);
                    g.scale(-1, 1);
                }
                var random = new Random(seed);
                CityCanvas canvas =
                        switch (scene) {
                            case SKYLINE,
                                    BRIDGE,
                                    WHEEL,
                                    HARBOUR,
                                    METRO,
                                    BOULEVARD,
                                    AIRFIELD,
                                    STADIUM ->
                                    new Illustration(g, time, random);
                            default -> new CityScenes(g, time, random);
                        };
                canvas.draw(scene);
                return image;
            } finally {
                g.dispose();
            }
        }
    }

    private SliderArt() {}

    public static Postcard postcard(long seed, SliderHall station, int round) {
        if (round < 1 || round > 2) throw new IllegalArgumentException("round must be 1 or 2");
        var scenes = Arrays.asList(Scene.values());
        Collections.shuffle(scenes, new Random(seed));
        long imageSeed = new SliderDeck(seed).imageSeed(station, round);
        return new Postcard(
                scenes.get(2 * station.ordinal() + round - 1),
                Time.values()[Math.floorMod(SliderDeck.mix(~imageSeed), Time.values().length)],
                imageSeed);
    }

    /** Row-major, full screen. Cache distinct opaque drawing colours for this image's lifetime. */
    public static byte[] mapPixels(BufferedImage image) {
        int w = image.getWidth(), h = image.getHeight();
        byte[] indices = new byte[w * h];
        var colors = new HashMap<Integer, Byte>();
        int[] row = new int[w];
        int previous = 0xffffffff;
        byte index = MapColorMatcher.matchColor(255, 255, 255);
        for (int y = 0; y < h; y++) {
            image.getRGB(0, y, w, 1, row, 0, w);
            for (int x = 0; x < w; x++) {
                int rgb = row[x];
                if (rgb != previous) {
                    if ((rgb >>> 24) != 255)
                        throw new IllegalArgumentException("Postcard pixels must be opaque");
                    index =
                            colors.computeIfAbsent(
                                    rgb,
                                    c ->
                                            MapColorMatcher.matchColor(
                                                    (c >> 16) & 255, (c >> 8) & 255, c & 255));
                    previous = rgb;
                }
                indices[y * w + x] = index;
            }
        }
        return indices;
    }

    private static final class Illustration extends CityCanvas {
        Illustration(Graphics2D g, Time time, Random rand) {
            super(g, time, rand);
        }

        @Override
        void subject(Scene scene) {
            switch (scene) {
                case SKYLINE -> skyline();
                case BRIDGE -> bridge();
                case WHEEL -> wheel();
                case HARBOUR -> harbour();
                case METRO -> metro();
                case BOULEVARD -> boulevard();
                case AIRFIELD -> airfield();
                case STADIUM -> stadium();
                default -> throw new IllegalArgumentException(scene.name());
            }
        }

        void skyline() {
            river(256);
            // Stepped beacon, blue-glass slab and sail tower form three distinct crowns.
            int offset = rand.nextInt(30) - 12;
            var transform = g.getTransform();
            g.translate(offset, 0);
            tower(-18, 159, 54, 255, PAPER, SLATE);
            tower(43, 130, 44, 255, MINT, TEAL);
            tower(86, 183, 46, 255, PAPER, SLATE);
            poly(
                    DEEP, 118, 252, 118, 126, 133, 126, 133, 103, 143, 103, 143, 76, 153, 76, 159,
                    48, 165, 76, 176, 76, 176, 104, 188, 104, 188, 125, 203, 125, 203, 252);
            for (int x = 135; x <= 182; x += 14)
                rect(MINT, x, x < 145 || x > 171 ? 134 : 110, 5, 92);
            tower(202, 150, 38, 256, lit, SLATE);
            poly(TEAL, 246, 253, 246, 80, 298, 56, 298, 253);
            poly(DEEP, 298, 56, 313, 65, 313, 253, 298, 253);
            for (int i = 0; i < 4; i++) line(MINT, 4, 253 + i * 12, 83 - i * 5, 253 + i * 12, 238);
            tower(319, 178, 44, 257, PAPER, SLATE);
            Path2D sail = new Path2D.Double();
            sail.moveTo(392, 253);
            sail.quadTo(404, 139, 459, 106);
            sail.lineTo(448, 252);
            sail.closePath();
            fill(PAPER, sail);
            poly(glass, 410, 238, 420, 172, 449, 131, 439, 238);
            tower(500, 181, 51, 255, MINT, TEAL);
            tower(564, 148, 56, 255, PAPER, SLATE);
            g.setTransform(transform);
            rect(DEEP, 0, 248, 640, 9);
            for (int x = 0; x < 640; x += 27) {
                oval(PINE, x, 231 + rand.nextInt(4), 24, 20);
            }
            ferry(340, 284, 1.04);
        }

        void bridge() {
            river(230);
            int mast = 208 + rand.nextInt(32), back = 539;
            for (int i = 0; i < 5; i++)
                line(SLATE, 3.6, back, 112 + i * 4, back - 20 - i * 18, 207);
            for (int i = 0; i < 4; i++)
                line(SLATE, 3.6, back, 114 + i * 5, back + 18 + i * 16, 208);
            poly(SLATE, back - 16, 231, back - 3, 103, back + 6, 103, back + 16, 231);
            for (int i = 0; i < 8; i++) {
                line(time == Time.NIGHT ? PAPER : SLATE, 4.2, mast, 46 + i * 5, 12 + i * 24, 199);
                line(SILVER, 4, mast + 8, 46 + i * 5, 489 - i * 30, 198);
            }
            poly(
                    DEEP, mast - 41, 267, mast - 4, 35, mast + 10, 35, mast + 39, 265, mast + 23,
                    265, mast + 2, 103, mast - 23, 267);
            poly(PAPER, mast - 42, 266, mast - 8, 34, mast + 1, 31, mast - 30, 266);
            poly(MINT, mast + 1, 31, mast + 12, 36, mast + 42, 262, mast + 29, 262);
            poly(INK, 0, 193, 640, 202, 640, 217, 0, 208);
            poly(RED, 0, 192, 640, 201, 640, 206, 0, 198);
            for (int x = 4; x < 640; x += 38) rect(INK, x, 185 + x * .014, 4, 9);
            poly(SLATE, 69, 207, 82, 207, 78, 243, 69, 243);
            poly(SLATE, 432, 213, 445, 213, 442, 237, 433, 237);
            int boat = 439 + rand.nextInt(38);
            poly(PAPER, boat, 280, boat + 42, 280, boat + 39, 237);
            poly(RED, boat + 46, 280, boat + 46, 251, boat + 65, 281);
            poly(INK, boat - 5, 285, boat + 72, 285, boat + 55, 294, boat + 9, 294);
        }

        void wheel() {
            river(244);
            int cx = 233 + rand.nextInt(33), cy = 139, r = 95;
            // Cream outer hoops silhouette against both the sky and the far quay.
            line(INK, 14, cx, cy, cx - 42, 247);
            line(DEEP, 14, cx, cy, cx + 45, 247);
            for (int i = 0; i < 16; i++) {
                double a = i * Math.PI / 8, ex = cx + r * Math.cos(a), ey = cy + r * Math.sin(a);
                line(i % 2 == 0 ? PAPER : MINT, 4, cx, cy, ex, ey);
            }
            stroke(TEAL, 9, new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
            stroke(PAPER, 4, new Ellipse2D.Double(cx - r + 6, cy - r + 6, r * 2 - 12, r * 2 - 12));
            for (int i = 0; i < 12; i++) {
                double a = i * Math.PI / 6, x = cx + r * Math.cos(a), y = cy + r * Math.sin(a);
                line(INK, 4, x, y, x, y + 7);
                rect(INK, x - 8, y + 6, 16, 10);
                rect(i % 3 == 0 ? GOLD : RED, x - 9, y + 11, 18, 7);
            }
            oval(INK, cx - 13, cy - 13, 26, 26);
            oval(GOLD, cx - 7, cy - 7, 14, 14);
            for (int x = 7; x < 640; x += 41) rect(DEEP, x, 245, 7, 18);
            rect(INK, 0, 241, 640, 7);
            for (int x : new int[] {35, 394, 468}) {
                rect(PAPER, x, 215, 39, 26);
                poly(RED, x - 5, 215, x + 18, 192, x + 44, 215);
                rect(DEEP, x + 9, 221, 21, 16);
            }
            ferry(500, 281, .83);
        }

        void crane(int x, int y, double scale, boolean reversed) {
            var transform = g.getTransform();
            g.translate(x, y);
            g.scale(reversed ? -scale : scale, scale);
            poly(RED, -6, 150, -6, 0, 7, 0, 13, 150);
            poly(GOLD, -9, 0, 0, -14, 16, -5, 16, 4, -9, 4);
            line(RED, 7, -34, 19, 93, -42);
            line(RED, 7, -32, 29, 96, -30);
            for (int i = 0; i < 6; i++)
                line(RED, 4, -25 + i * 20, 24 - i * 9.5, -17 + i * 20, 10 - i * 9.5);
            line(INK, 4, 88, -28, 88, 39);
            rect(GOLD, 78, 40, 22, 10);
            rect(PAPER, -15, 10, 22, 15);
            rect(DEEP, -12, 13, 14, 6);
            g.setTransform(transform);
        }

        void container(int x, int y, int col) {
            rect(INK, x, y, 44, 19);
            rect(col, x + 1, y + 1, 42, 15);
            for (int i = 7; i < 38; i += 9) rect(DEEP, x + i, y + 4, 3, 9);
        }

        void harbour() {
            river(235);
            crane(65, 117, .85, false);
            crane(402, 82, 1.13, true);
            crane(537, 107, .91, false);
            rect(DEEP, 0, 219, 640, 17);
            int x = 95 + rand.nextInt(26);
            poly(INK, x - 26, 225, x + 325, 225, x + 292, 263, x + 13, 263);
            poly(RED, x + 7, 255, x + 302, 251, x + 292, 263, x + 13, 263);
            rect(PAPER, x + 16, 183, 50, 42);
            rect(DEEP, x + 12, 177, 55, 11);
            rect(RED, x + 27, 160, 13, 18);
            int[] cargo = {GOLD, TEAL, RED, MINT};
            for (int i = 0; i < 5; i++) {
                int tiers = i == 0 || i == 4 ? 2 : 3;
                for (int j = 0; j < tiers; j++)
                    container(x + 70 + i * 45, 206 - j * 19, cargo[(i + j + rand.nextInt(2)) % 4]);
            }
            rect(PAPER, x - 15, 224, 321, 4);
            for (int i = 0; i < 4; i++) oval(PAPER, x + 28 + i * 18, 233, 5, 5);
            // Foreground mooring fender and timber quay.
            poly(DEEP, 0, 288, 72, 280, 153, 320, 0, 320);
            rect(INK, 24, 269, 11, 26);
            rect(GOLD, 17, 267, 25, 6);
        }

        void metro() {
            int y = 136 + rand.nextInt(16);
            // The arcades are genuine openings onto the distant skyline and parkland.
            rect(LEAF, 0, 219, 640, 101);
            poly(
                    MINT, 0, 286, 166, 237, 390, 232, 640, 201, 640, 217, 369, 249, 131, 265, 47,
                    320, 0, 320);
            var transform = g.getTransform();
            g.translate(0, y);
            g.shear(0, -.057);
            for (int x = -65; x < 700; x += 130) {
                Path2D arch = new Path2D.Double();
                arch.moveTo(x, 24);
                arch.lineTo(x + 130, 24);
                arch.lineTo(x + 130, 167);
                arch.lineTo(x + 116, 167);
                arch.lineTo(x + 116, 103);
                arch.curveTo(x + 116, 38, x + 16, 38, x + 16, 103);
                arch.lineTo(x + 16, 167);
                arch.lineTo(x, 167);
                arch.closePath();
                fill(PAPER, arch);
                rect(SLATE, x, 65, 7, 101);
            }
            rect(INK, 0, 25, 660, 13);
            rect(MINT, 0, 34, 660, 6);
            int nose = 569 + rand.nextInt(42);
            Path2D body = new Path2D.Double();
            body.moveTo(-8, -17);
            body.lineTo(nose - 70, -17);
            body.quadTo(nose - 42, -17, nose - 21, 2);
            body.lineTo(nose + 4, 19);
            body.quadTo(nose + 7, 25, nose - 8, 26);
            body.lineTo(-8, 26);
            body.closePath();
            fill(SILVER, body);
            poly(DEEP, nose - 66, -11, nose - 48, -10, nose - 17, 10, nose - 42, 9);
            for (int x = 1; x < nose - 77; x += 34) rect(DEEP, x, -8, 25, 15);
            for (int x = 115; x < nose - 77; x += 136) {
                rect(SLATE, x, -14, 5, 37);
                rect(MINT, x - 12, 3, 7, 12);
            }
            poly(RED, 0, 10, nose - 54, 10, nose - 5, 20, nose - 52, 15, 0, 17);
            rect(INK, 0, 23, nose - 18, 4);
            line(INK, 3.8, 156, -18, 173, -30);
            line(INK, 3.8, 173, -30, 194, -22);
            line(SLATE, 3.8, 0, -31, 640, -31);
            for (int x : new int[] {30, 385}) {
                rect(DEEP, x, -44, 5, 28);
                line(DEEP, 4, x, -41, x + 24, -31);
            }
            g.setTransform(transform);
            for (int x : new int[] {24, 445, 565}) tree(x, 304, 22);
        }

        void boulevard() {
            int van = 302 + rand.nextInt(43);
            poly(SLATE, 0, 230, van - 19, 194, van + 22, 194, 640, 220, 640, 320, 0, 320);
            poly(PAPER, 0, 215, van - 18, 194, 74, 320, 0, 320);
            poly(PAPER, van + 20, 194, 640, 205, 640, 320, 583, 320);
            // Chamfered streetwall and alternating sunlit windows recede towards the vanishing
            // point.
            int[] left = {-65, 66, 156, 216, 264},
                    heights = {24, 59, 96, 125, 150},
                    widths = {113, 76, 49, 38, 25};
            for (int i = 4; i >= 0; i--) {
                int x = left[i], h = heights[i], w = widths[i], base = 263 - i * 15;
                tower(x, h, w, base, PAPER, SLATE);
                poly(
                        i % 2 == 0 ? RED : TEAL,
                        x,
                        base - 30,
                        x + w,
                        base - 30,
                        x + w + 8,
                        base - 20,
                        x - 4,
                        base - 20);
                rect(DEEP, x + 5, base - 18, w - 10, 15);
                int right = 642 - x - w;
                tower(right, h - 6, w, base, MINT, DEEP);
            }
            for (int i = 3; i >= 0; i--) {
                int x = 59 + i * 60, base = 291 - i * 24;
                tree(x, base, 41 - i * 9);
                tree(647 - x, base, 38 - i * 8);
            }
            for (int i = 0; i < 6; i++)
                poly(
                        SILVER,
                        109 + i * 66,
                        298,
                        145 + i * 65,
                        298,
                        128 + i * 74,
                        312,
                        87 + i * 74,
                        312);
            for (int i = 0; i < 3; i++) rect(PAPER, van - 2, 200 + i * 24, 4 + i, 7 + i * 2);
            // A city taxi, three-quarter rear, with a roof light and a separate bumper.
            int x = van + 36;
            poly(
                    GOLD, x, 246, x + 6, 227, x + 16, 217, x + 43, 217, x + 57, 232, x + 62, 237,
                    x + 62, 250, x, 250);
            poly(DEEP, x + 11, 230, x + 19, 220, x + 40, 220, x + 49, 230);
            rect(SILVER, x + 24, 211, 13, 5);
            rect(INK, x + 5, 246, 55, 5);
            rect(RED, x + 4, 237, 9, 5);
            rect(RED, x + 51, 237, 8, 5);
        }

        void airfield() {
            rect(SAGE, 0, 210, 640, 110);
            poly(SLATE, -70, 320, 206, 216, 319, 216, 586, 320);
            for (int i = 0; i < 3; i++)
                poly(
                        PAPER,
                        255 - i * 13,
                        226 + i * 27,
                        260 - i * 11,
                        226 + i * 27,
                        260 - i * 13,
                        233 + i * 31,
                        251 - i * 15,
                        233 + i * 31);
            poly(PAPER, 0, 198, 35, 176, 117, 176, 157, 198);
            rect(DEEP, 0, 198, 157, 19);
            int x = 452 + rand.nextInt(25);
            poly(PAPER, x - 22, 244, x - 14, 115, x + 10, 115, x + 23, 244);
            poly(SLATE, x + 5, 240, x + 4, 114, x + 14, 114, x + 24, 240);
            rect(RED, x - 28, 117, 58, 9);
            poly(INK, x - 41, 77, x + 40, 77, x + 25, 115, x - 26, 115);
            poly(glass, x - 34, 84, x + 33, 84, x + 23, 106, x - 22, 106);
            for (int i = -18; i <= 18; i += 18) line(PAPER, 4, x + i, 83, x + i * .7, 107);
            poly(PAPER, x - 46, 78, x - 31, 65, x + 27, 65, x + 46, 78);
            line(INK, 4, x - 6, 64, x - 6, 42);
            oval(SILVER, x + 8, 50, 16, 16);
            // Ascending airliner, swept wings and a distinct tail fin, viewed side-on.
            var transform = g.getTransform();
            g.translate(137 + rand.nextInt(21), 114);
            g.rotate(-.13);
            g.scale(1.27, 1.27);
            poly(SLATE, 59, 5, 28, -33, 45, -31, 103, 2);
            poly(RED, 1, 4, -13, -27, -1, -24, 26, 2);
            Path2D body = new Path2D.Double();
            body.moveTo(-9, 2);
            body.lineTo(128, 2);
            body.quadTo(147, 1, 165, 13);
            body.quadTo(157, 20, 116, 21);
            body.lineTo(39, 19);
            body.closePath();
            fill(SILVER, body);
            poly(DEEP, 133, 6, 145, 7, 153, 12, 132, 12);
            for (int i = 42; i < 125; i += 13) rect(TEAL, i, 7, 6, 5);
            poly(PAPER, 79, 13, 108, 14, 41, 53, 27, 52);
            poly(DEEP, 75, 30, 95, 28, 86, 36, 66, 37);
            g.setTransform(transform);
            poly(DEEP, 500, 231, 640, 207, 640, 245, 500, 260);
            poly(SILVER, 493, 230, 640, 204, 640, 210, 493, 237);
            for (int i = 0; i < 5; i++)
                poly(
                        glass,
                        505 + i * 28,
                        237 - i * 5,
                        524 + i * 28,
                        234 - i * 5,
                        524 + i * 28,
                        243 - i * 5,
                        505 + i * 28,
                        247 - i * 5);
            rect(INK, 449, 275, 61, 14);
            rect(GOLD, 452, 272, 55, 13);
            line(INK, 3.5, 461, 278, 478, 278);
            poly(INK, 476, 274, 482, 278, 476, 282);
        }

        void stadium() {
            rect(SAGE, 0, 228, 640, 92);
            poly(PAPER, 0, 320, 203, 241, 449, 239, 640, 306, 640, 320);
            for (int x : new int[] {66, 559}) {
                line(SLATE, 6, x, 203, x + (x < 100 ? -9 : 9), 107);
                rect(INK, x - 25, 98, 43, 12);
                for (int j = 0; j < 3; j++) rect(lit, x - 21 + j * 12, 100, 8, 6);
            }
            int x = rand.nextInt(25) - 12;
            var transform = g.getTransform();
            g.translate(x, 0);
            poly(DEEP, 62, 165, 568, 165, 518, 238, 135, 243);
            for (int i = 0; i < 13; i++) {
                int u = 80 + i * 36;
                poly(
                        i % 2 == 0 ? MINT : glass,
                        u,
                        175,
                        u + 24,
                        175,
                        u + 43 - (i * 2),
                        224,
                        u + 26 - (i * 2),
                        225);
            }
            oval(PAPER, 45, 104, 545, 99);
            oval(TEAL, 75, 118, 485, 66);
            oval(INK, 98, 129, 439, 43);
            oval(RED, 146, 143, 344, 26);
            oval(SAGE, 173, 149, 292, 15);
            // Crown blades rise off the concourse and give the bowl a radial silhouette.
            for (int i = 0; i < 12; i++) {
                double a = i * Math.PI / 11,
                        u = 316 + 265 * Math.cos(a),
                        v = 153 + 46 * Math.sin(a);
                poly(SILVER, u, v - 5, u + 14 * Math.cos(a), v - 24, u - 33 * Math.cos(a), v + 5);
            }
            for (int i = 0; i < 3; i++) {
                int u = 232 + i * 64;
                line(SLATE, 4, u, 109, u, 78 - i % 2 * 11);
                poly(
                        i == 1 ? RED : GOLD,
                        u,
                        78 - i % 2 * 11,
                        u + 23,
                        84 - i % 2 * 11,
                        u,
                        92 - i % 2 * 11);
            }
            poly(PAPER, 123, 232, 524, 228, 515, 239, 133, 244);
            g.setTransform(transform);
            for (int u : new int[] {13, 592}) tree(u, 294, 30);
            for (int i = 0; i < 4; i++)
                poly(
                        MINT,
                        76 + i * 138,
                        320,
                        82 + i * 138,
                        320,
                        232 + i * 40,
                        247,
                        228 + i * 40,
                        247);
        }
    }
}
