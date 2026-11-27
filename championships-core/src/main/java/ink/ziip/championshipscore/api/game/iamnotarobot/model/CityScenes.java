package ink.ziip.championshipscore.api.game.iamnotarobot.model;

import ink.ziip.championshipscore.api.game.iamnotarobot.model.SliderArt.Scene;
import ink.ziip.championshipscore.api.game.iamnotarobot.model.SliderArt.Time;

import java.awt.Graphics2D;
import java.awt.geom.*;
import java.util.Random;

/** City postcards drawn from the platform, pavement, rooftops and coast. */
final class CityScenes extends CityCanvas {
    private static final int CORAL = 0xe78467, SAND = 0xd8af93, BLUE = 0x4884a4, PLUM = 0x705677;

    CityScenes(Graphics2D g, Time time, Random rand) {
        super(g, time, rand);
    }

    @Override
    void subject(Scene scene) {
        switch (scene) {
            case METRO_PLATFORM -> platform();
            case HSR_STATION -> terminal();
            case INTERCHANGE -> interchange();
            case SEA_BRIDGE -> seaBridge();
            case CBD_PLAZA -> plaza();
            case TV_TOWER -> broadcastTower();
            case PARK_LAKE -> lake();
            case BEACH -> beach();
            case MARINA -> marina();
            case ROOFTOP_GARDEN -> roof();
            case SNOW_TRAM -> winter();
            case RAIN_STREET -> rain();
            case ART_MUSEUM -> museum();
            case WIND_COAST -> wind();
            default -> throw new IllegalArgumentException(scene.name());
        }
    }

    void curve(
            int color,
            double width,
            double x,
            double y,
            double ax,
            double ay,
            double bx,
            double by,
            double u,
            double v) {
        stroke(color, width, new CubicCurve2D.Double(x, y, ax, ay, bx, by, u, v));
    }

    void platform() {
        // View down a platform under a tapering canopy. The train nose turns toward us.
        poly(DEEP, 0, 0, 457, 0, 255, 90, 0, 148);
        poly(TEAL, 0, 28, 380, 0, 353, 16, 0, 70);
        for (int k = 0; k < 4; k++) line(PAPER, 4, 35 + k * 100, 0, 70 + k * 51, 118 - k * 13);
        poly(SLATE, 0, 319, 0, 242, 250, 169, 640, 185, 640, 320);
        poly(INK, 200, 320, 250, 176, 640, 202, 640, 320);
        poly(MINT, 0, 263, 250, 173, 329, 177, 55, 320, 0, 320);
        line(GOLD, 7, 14, 320, 263, 177);
        for (int i = 0; i < 5; i++) {
            int x = i * 47, y = 228 - i * 10, h = 107 - i * 15;
            poly(glass, x, y - h, x + 43, y - h - 4, x + 43, y - 9, x, y);
            line(SILVER, 4, x, y - h, x, y);
            line(MINT, 4, x, y - 20, x + 40, y - 28);
        }
        int x = 400 + rand.nextInt(28);
        poly(PAPER, 246, 135, x, 99, x + 88, 108, x + 105, 211, x + 71, 235, 246, 181);
        poly(TEAL, 248, 148, x, 122, x + 2, 151, 247, 163);
        for (int i = 0; i < 7; i++) {
            int u = 256 + i * 20;
            line(PAPER, 3, u, 147 - i * 3, u, 162 - i * 2);
        }
        poly(DEEP, x - 8, 109, x + 67, 111, x + 84, 151, x + 1, 162);
        poly(BLUE, x + 2, 116, x + 60, 118, x + 70, 140, x + 11, 150);
        poly(RED, x, 178, x + 90, 164, x + 97, 192, x + 12, 212);
        line(SILVER, 6, x + 17, 184, x + 33, 181);
        line(SILVER, 6, x + 66, 175, x + 83, 173);
        poly(INK, x + 12, 213, x + 97, 193, x + 86, 210, x + 22, 230);
        rect(INK, 31, 10, 8, 43);
        rect(INK, 145, 9, 8, 36);
        rect(INK, 16, 41, 161, 36);
        rect(GOLD, 17, 73, 160, 5);
        oval(RED, 24, 48, 19, 19);
        rect(SILVER, 53, 49, 62, 5);
        rect(MINT, 53, 60, 41, 4);
        poly(PAPER, 140, 50, 150, 50, 150, 47, 160, 58, 150, 68, 150, 64, 140, 64);
        line(INK, 7, 3, 97, 221, 77);
        line(PAPER, 3, 4, 102, 218, 82);
    }

    void terminal() {
        // Three wave shells with a deep blue soffit and a bank of clerestory windows.
        rect(SLATE, 0, 171, 640, 149);
        rect(glass, 0, 94, 640, 100);
        for (int x = 0; x < 640; x += 32) rect(MINT, x, 83, 5, 109);
        Path2D p = new Path2D.Double();
        p.moveTo(-12, 57);
        p.curveTo(70, 124, 110, -13, 215, 45);
        p.curveTo(311, 109, 363, -17, 453, 40);
        p.curveTo(525, 95, 558, 37, 652, 24);
        p.lineTo(652, 52);
        p.curveTo(559, 64, 534, 122, 446, 70);
        p.curveTo(353, 24, 308, 144, 204, 76);
        p.curveTo(123, 28, 64, 159, -12, 86);
        p.closePath();
        fill(PAPER, p);
        curve(TEAL, 6, -6, 85, 80, 150, 122, 21, 206, 74);
        for (int x : new int[] {42, 229, 438, 609}) {
            line(DEEP, 12, x, 191, x + 10, 96);
            line(DEEP, 6, x + 10, 109, x - 12, 81);
        }
        poly(INK, 0, 269, 640, 208, 640, 244, 0, 317);
        poly(SAND, 0, 319, 640, 250, 640, 320);
        int n = 58 + rand.nextInt(41);
        Path2D body = new Path2D.Double();
        body.moveTo(n, 265);
        body.curveTo(n - 13, 256, n + 26, 238, n + 73, 216);
        body.curveTo(n + 126, 185, n + 145, 149, n + 212, 142);
        body.lineTo(650, 112);
        body.lineTo(650, 208);
        body.lineTo(n + 63, 273);
        body.quadTo(n + 10, 280, n, 265);
        body.closePath();
        fill(0x9bb3c4, body);
        poly(DEEP, n + 91, 213, n + 151, 160, n + 190, 152, n + 158, 190);
        poly(TEAL, n + 186, 164, 640, 125, 640, 151, n + 163, 190);
        for (int x = n + 225; x < 640; x += 33)
            line(PAPER, 5, x, 155 - (x - n - 225) * .08, x - 4, 182 - (x - n - 225) * .08);
        curve(RED, 7, n + 10, 261, n + 118, 256, n + 148, 227, 642, 178);
        line(INK, 5, n + 78, 270, 638, 206);
        line(GOLD, 6, n + 18, 251, n + 49, 240);
        line(SILVER, 4, 365, 314, 639, 281);
    }

    void interchange() {
        // Looking down at a clover-shaped greenbelt. All three decks continue past the crop.
        int park =
                switch (time) {
                    case NIGHT -> 0x456868;
                    case DUSK -> 0xad926e;
                    case NOON -> 0xa4bb85;
                    case SUNRISE -> 0xb5ac87;
                };
        rect(park, 0, 0, 640, 320);
        poly(water, 0, 9, 188, 49, 346, 0, 414, 0, 202, 77, 0, 41);
        for (int i = 0; i < 7; i++) {
            int x = rand.nextInt(640), y = rand.nextInt(300);
            oval(LEAF, x, y, 84, 32);
            oval(PINE, x + 9, y + 4, 51, 21);
        }
        for (int i = 0; i < 4; i++) {
            int x = 16 + i * 77, y = 64 - i % 2 * 23;
            poly(DEEP, x, y + 5, x + 43, y - 4, x + 52, y + 19, x + 8, y + 30);
            poly(time.band, x - 2, y, x + 41, y - 10, x + 50, y + 13, x + 6, y + 24);
        }
        deck(-35, 264, 177, 196, 321, 292, 671, 200, 24, 4);
        deck(-40, 105, 160, 207, 415, 217, 680, 130, 40, 9);
        deck(73, 345, 115, 227, 593, 212, 423, -37, 55, 8);
        rect(INK, 27, 205, 6, 29);
        rect(TEAL, 10, 185, 60, 23);
        line(PAPER, 4, 20, 196, 44, 196);
    }

    void deck(
            double x,
            double y,
            double ax,
            double ay,
            double bx,
            double by,
            double u,
            double v,
            int width,
            int traffic) {
        curve(DEEP, width + 6, x, y + 10, ax, ay + 10, bx, by + 10, u, v + 10);
        curve(PAPER, width, x, y, ax, ay, bx, by, u, v);
        curve(SLATE, width - 7, x, y, ax, ay, bx, by, u, v);
        curve(GOLD, 3, x, y, ax, ay, bx, by, u, v);
        for (int i = 0; i < traffic; i++) {
            double t = (i + .2 + rand.nextDouble() * .45) / traffic, k = 1 - t;
            double px = k * k * k * x + 3 * k * k * t * ax + 3 * k * t * t * bx + t * t * t * u;
            double py = k * k * k * y + 3 * k * k * t * ay + 3 * k * t * t * by + t * t * t * v;
            double dx = k * k * (ax - x) + 2 * k * t * (bx - ax) + t * t * (u - bx);
            double dy = k * k * (ay - y) + 2 * k * t * (by - ay) + t * t * (v - by);
            var transform = g.getTransform();
            g.translate(px, py);
            g.rotate(Math.atan2(dy, dx));
            car(0, (i % 2 == 0 ? -1 : 1) * width / 4 - 4, i, width > 30 ? .92 : .68);
            g.setTransform(transform);
        }
    }

    void car(int x, int y, int variant, double scale) {
        var t = g.getTransform();
        g.translate(x, y);
        g.scale(scale, scale);
        rect(INK, 0, 0, 23, 10);
        rect(variant % 3 == 0 ? GOLD : variant % 3 == 1 ? CORAL : PAPER, 0, -2, 23, 9);
        rect(DEEP, 6, -1, 10, 7);
        g.setTransform(t);
    }

    void seaBridge() {
        int deck = 171 + rand.nextInt(12);
        river(199);
        poly(SAGE, 0, 197, 88, 170, 154, 180, 208, 193, 252, 199);
        for (int x = 0; x < 650; x += 46) {
            rect(SLATE, x, deck + 8, 8, 42);
            oval(DEEP, x - 5, deck + 46, 19, 5);
        }
        int a = 223, b = 502;
        int crown = 51;
        for (int x : new int[] {a, b}) {
            rect(DEEP, x - 12, crown, 9, 167);
            rect(DEEP, x + 11, crown + 3, 9, 164);
            rect(PAPER, x - 15, crown, 9, 163);
            rect(MINT, x + 7, crown, 7, 162);
            for (int y : new int[] {crown + 11, crown + 49, crown + 90})
                rect(SLATE, x - 7, y, 23, 7);
        }
        suspension(-14, deck - 3, 62, deck - 6, 152, 135, a, crown + 2, deck, INK, 8);
        suspension(
                a, crown + 2, a + 85, deck + 11, b - 85, deck + 11, b, crown + 2, deck, PAPER, 15);
        suspension(b, crown + 2, 548, 130, 607, deck - 10, 664, deck - 7, deck, PAPER, 6);
        rect(INK, 0, deck, 640, 13);
        rect(RED, 0, deck - 3, 640, 5);
        ferry(392, 270, 1.16);
        poly(DEEP, 0, 269, 81, 247, 132, 254, 181, 271, 206, 320, 0, 320);
        poly(SAGE, 0, 264, 76, 240, 126, 248, 150, 259, 78, 256, 0, 284);
    }

    void suspension(
            double x,
            double y,
            double ax,
            double ay,
            double bx,
            double by,
            double u,
            double v,
            int deck,
            int cable,
            int drops) {
        curve(cable, 5, x, y, ax, ay, bx, by, u, v);
        for (int i = 1; i <= drops; i++) {
            double t = (double) i / (drops + 1), k = 1 - t;
            double px = k * k * k * x + 3 * k * k * t * ax + 3 * k * t * t * bx + t * t * t * u;
            double py = k * k * k * y + 3 * k * k * t * ay + 3 * k * t * t * by + t * t * t * v;
            line(cable, 2.8, px, py, px, deck);
        }
    }

    void plaza() {
        int shift = rand.nextInt(24);
        int[] xs = {-20, 84, 428, 540}, ys = {35, 64, 47, 13};
        for (int j = 0; j < 4; j++)
            tower(xs[j] + shift, ys[j], 80, 199, j % 2 == 0 ? glass : TEAL, DEEP);
        rect(SAND, 0, 192, 640, 128);
        for (int x = -240; x < 1000; x += 115) line(PAPER, 3, 328 + (x - 328) * .12, 194, x, 320);
        for (int y : new int[] {206, 227, 261, 309}) rect(PAPER, 0, y, 640, 3);
        for (int x : new int[] {30, 109, 531, 611}) tree(x, 216, 21);
        oval(SLATE, 101, 214, 420, 69);
        oval(PAPER, 103, 205, 415, 65);
        oval(water, 127, 211, 367, 45);
        oval(MINT, 173, 216, 273, 25);
        oval(DEEP, 266, 212, 88, 16);
        for (int j = 0; j < 3; j++) {
            int dx = 45 + j * 30;
            curve(TEAL, 7, 312, 218, 308 - dx / 2, 130 - j * 12, 312 - dx, 148, 312 - dx, 226);
            curve(TEAL, 7, 312, 218, 316 + dx / 2, 130 - j * 12, 312 + dx, 148, 312 + dx, 226);
            curve(SILVER, 5, 310, 216, 306 - dx / 2, 128 - j * 12, 310 - dx, 146, 310 - dx, 224);
            curve(SILVER, 5, 310, 216, 314 + dx / 2, 128 - j * 12, 310 + dx, 146, 310 + dx, 224);
        }
        poly(MINT, 300, 217, 304, 132, 310, 99, 316, 134, 322, 217);
        oval(SILVER, 305, 95, 10, 13);
        line(DEEP, 7, 43, 271, 109, 271);
        line(DEEP, 5, 53, 272, 53, 282);
        line(DEEP, 5, 100, 272, 100, 282);
    }

    void broadcastTower() {
        int x = 285 + rand.nextInt(51);
        poly(LEAF, 0, 250, 134, 209, 193, 224, 271, 199, 640, 237, 640, 320, 0, 320);
        for (int i = 0; i < 8; i++)
            tower(i * 95 - 22, 192 + rand.nextInt(30), 29 + rand.nextInt(26), 280, MINT, SLATE);
        poly(PAPER, x - 25, 276, x - 5, 102, x + 6, 102, x + 23, 276);
        poly(SLATE, x + 2, 111, x + 9, 111, x + 23, 276, x + 8, 276);
        line(INK, 5, x, 66, x, 12);
        rect(RED, x - 3, 22, 6, 14);
        oval(PAPER, x - 5, 8, 10, 9);
        oval(DEEP, x - 42, 56, 84, 72);
        oval(CORAL, x - 42, 54, 77, 65);
        // Ribbon of panoramic windows encircles a single suspended sphere.
        oval(PAPER, x - 49, 77, 98, 27);
        oval(glass, x - 48, 79, 96, 18);
        for (int i = -34; i < 40; i += 13) line(DEEP, 3, x + i, 82, x + i - 2, 94);
        rect(INK, x - 12, 123, 24, 6);
        poly(
                DEEP, 0, 290, 125, 253, 222, 265, 333, 244, 439, 265, 550, 248, 640, 258, 640, 320,
                0, 320);
        curve(MINT, 6, 19, 320, 206, 283, 337, 299, 512, 273);
    }

    void swan(int x, int y, double scale, int hull) {
        var t = g.getTransform();
        g.translate(x, y);
        g.scale(scale, scale);
        oval(DEEP, -2, 17, 107, 9);
        poly(hull, 0, 1, 93, 0, 104, -5, 94, 14, 76, 20, 25, 20, 7, 13);
        oval(PAPER, 9, -15, 79, 30);
        oval(TEAL, 27, -10, 43, 13);
        curve(SILVER, 12, 76, 0, 91, -12, 65, -46, 83, -54);
        oval(SILVER, 77, -61, 25, 15);
        poly(GOLD, 98, -57, 110, -52, 98, -49);
        oval(INK, 91, -58, 4, 4);
        rect(RED, 20, -26, 38, 7);
        line(SLATE, 3, 24, -24, 24, -6);
        line(SLATE, 3, 52, -24, 52, -6);
        g.setTransform(t);
    }

    void lake() {
        river(180);
        rect(LEAF, 0, 160, 640, 20);
        for (int x = -20; x < 660; x += 36) tree(x, 181, 17 + rand.nextInt(8));
        poly(TEAL, 0, 199, 81, 185, 224, 189, 155, 193, 72, 195, 0, 213);
        swan(217 + rand.nextInt(30), 240, 1.03, CORAL);
        swan(449, 205, .69, GOLD);
        poly(DEEP, 0, 246, 211, 320, 0, 320);
        poly(SAND, 0, 235, 251, 320, 0, 320);
        for (int i = 0; i < 5; i++) line(RED, 3, 0, 247 + i * 18, 135 - i * 22, 320);
        for (int x : new int[] {20, 129, 238}) {
            int y = 235 + x / 3;
            rect(INK, x, y - 30, 9, 37);
        }
        line(PAPER, 6, -4, 200, 251, 286);
    }

    void beach() {
        rect(water, 0, 172, 640, 148);
        poly(MINT, 0, 222, 116, 215, 231, 227, 331, 219, 448, 207, 640, 203, 640, 230, 0, 260);
        poly(SAND, 0, 237, 144, 222, 306, 235, 448, 216, 640, 217, 640, 320, 0, 320);
        for (int j = 0; j < 6; j++) {
            int x = 355 + j * 53;
            tower(x, 97 + rand.nextInt(48), 33, 176, PAPER, SLATE);
        }
        int x = 111 + rand.nextInt(25);
        line(INK, 9, x + 10, 205, x - 1, 272);
        line(INK, 9, x + 74, 206, x + 86, 264);
        line(PAPER, 4, x + 10, 215, x + 79, 258);
        line(PAPER, 4, x + 72, 215, x + 6, 259);
        poly(CORAL, x - 4, 154, x + 91, 151, x + 78, 211, x + 9, 214);
        poly(SILVER, x - 13, 145, x + 53, 125, x + 103, 140, x + 87, 152, x - 8, 156);
        poly(DEEP, x + 9, 163, x + 76, 160, x + 72, 184, x + 13, 187);
        rect(PAPER, x + 40, 187, 8, 22);
        rect(PAPER, x + 32, 194, 24, 7);
        line(PAPER, 5, x + 82, 208, x + 107, 260);
        umbrella(385, 242, 35, CORAL);
        umbrella(498, 281, 50, TEAL);
        umbrella(321, 296, 43, GOLD);
        rect(PAPER, 465, 294, 77, 9);
        rect(CORAL, 472, 294, 15, 9);
    }

    void umbrella(int x, int y, int r, int fabric) {
        line(INK, 3, x, y - r, x, y + 3);
        var dome = new Arc2D.Double(x - r, y - r * 1.55, 2 * r, r, 0, 180, Arc2D.PIE);
        fill(fabric, dome);
        poly(PAPER, x, y - r * 1.55, x + r * .52, y - r * 1.05, x + r * .16, y - r * 1.05);
        for (int i = 0; i < 4; i++)
            oval(i % 2 == 0 ? fabric : PAPER, x - r + i * r * .5, y - r * 1.13, r * .5, r * .20);
    }

    void marina() {
        river(203);
        poly(SAND, 0, 201, 104, 182, 206, 185, 255, 202);
        int x = 80 + rand.nextInt(20);
        poly(PAPER, x - 18, 192, x - 10, 100, x + 10, 100, x + 24, 192);
        poly(CORAL, x - 13, 131, x + 14, 131, x + 18, 153, x - 15, 153);
        rect(INK, x - 18, 92, 38, 8);
        rect(glass, x - 12, 73, 26, 19);
        poly(RED, x - 19, 74, x - 1, 57, x + 21, 74);
        line(INK, 3, x, 57, x, 49);
        for (int j = 0; j < 7; j++) {
            int u = 194 + j * 64 + rand.nextInt(16), base = 207 + j % 3 * 8, mast = 88 + j % 3 * 16;
            line(DEEP, 3, u, base, u, base - mast);
            poly(
                    j % 3 == 0 ? GOLD : PAPER,
                    u - 4,
                    base - mast + 9,
                    u - 4,
                    base - 11,
                    u - 34,
                    base - 12);
            poly(TEAL, u + 2, base - mast + 27, u + 24, base - 12, u + 2, base - 11);
            poly(INK, u - 38, base - 7, u + 28, base - 7, u + 15, base + 3, u - 28, base + 3);
        }
        int u = 281 + rand.nextInt(35);
        poly(
                SILVER, u - 113, 267, u - 84, 259, u - 46, 235, u + 31, 235, u + 58, 253, u + 119,
                249, u + 88, 269, u + 51, 280, u - 87, 280);
        poly(DEEP, u - 47, 244, u - 37, 236, u + 27, 236, u + 49, 251, u - 61, 251);
        poly(BLUE, u - 107, 268, u + 107, 256, u + 82, 272, u - 84, 281);
        line(PAPER, 5, u - 69, 261, u + 70, 254);
        poly(SAND, 0, 275, 178, 320, 0, 320);
        rect(INK, 34, 258, 10, 34);
        rect(GOLD, 27, 254, 24, 7);
    }

    void roof() {
        // A high viewpoint: the parapet cuts off the street and panels dominate the near plane.
        for (int x = 20; x < 660; x += 92) tower(x, 100 + rand.nextInt(40), 37, 183, glass, SLATE);
        poly(PAPER, 0, 149, 341, 191, 640, 146, 640, 320, 0, 320);
        poly(TEAL, 0, 149, 343, 186, 640, 141, 640, 159, 343, 208, 0, 167);
        poly(SAND, 0, 284, 339, 209, 640, 268, 640, 320, 0, 320);
        for (int i = 0; i < 3; i++) {
            int x = 5 + i * 75, y = 196 + i * 5;
            poly(DEEP, x, y, x + 66, y + 6, x + 56, y + 51, x + 9, y + 44);
            poly(CORAL, x - 4, y - 1, x + 70, y + 4, x + 68, y + 14, x - 2, y + 9);
            for (int j = 0; j < 3; j++) {
                oval(PINE, x + 2 + j * 19, y - 22 - j % 2 * 14, 27, 30);
                oval(LEAF, x + j * 19, y - 24 - j % 2 * 14, 21, 23);
            }
        }
        poly(SLATE, 272, 216, 564, 180, 625, 241, 323, 297);
        poly(INK, 282, 213, 560, 182, 616, 237, 327, 285);
        poly(BLUE, 291, 215, 556, 187, 604, 235, 330, 276);
        for (int j = 1; j < 6; j++) {
            double t = j / 6.0;
            line(MINT, 3, 291 + 265 * t, 215 - 28 * t, 330 + 274 * t, 276 - 41 * t);
        }
        line(MINT, 3, 310, 243, 582, 211);
        line(DEEP, 8, 337, 290, 337, 307);
        line(DEEP, 7, 595, 246, 595, 261);
        rect(PAPER, 533, 114, 37, 37);
        rect(SLATE, 537, 119, 28, 6);
        rect(SLATE, 537, 131, 28, 5);
    }

    void winter() {
        int snow = time == Time.NIGHT ? 0x8d909e : 0xa4a8b8;
        rect(snow, 0, 194, 640, 126);
        // Blue shadows on scalloped roadside drifts distinguish snow from the paved track.
        for (int i = 0; i < 9; i++) {
            int x = i * 82 - 25;
            oval(0x64899e, x, 197, 96, 18);
            oval(SILVER, x - 3, 191, 94, 19);
        }
        poly(0x64899e, 0, 320, 335, 197, 400, 197, 188, 320);
        for (int j = 0; j < 6; j++) {
            int x = -28 + j * 115, roof = 121 + rand.nextInt(28);
            rect(j % 2 == 0 ? CORAL : SLATE, x, roof, 94, 204 - roof);
            poly(DEEP, x - 9, roof + 4, x + 46, roof - 40, x + 102, roof + 4);
            poly(
                    snow, x - 8, roof, x + 47, roof - 47, x + 100, roof, x + 87, roof - 4, x + 48,
                    roof - 33, x + 4, roof + 4);
            for (int k = 0; k < 3; k++) rect(lit, x + 9 + k * 29, roof + 12, 15, 22);
        }
        line(INK, 4, -10, 88, 651, 58);
        poly(DEEP, 0, 311, 311, 203, 319, 203, 16, 316);
        line(SLATE, 4, 151, 320, 356, 202);
        int u = 164 + rand.nextInt(32);
        poly(
                DEEP, u, 239, u + 13, 130, u + 55, 113, u + 253, 122, u + 254, 193, u + 67, 245,
                u + 19, 247);
        poly(CORAL, u + 1, 226, u + 10, 139, u + 63, 136, u + 72, 231, u + 54, 247, u + 13, 245);
        poly(RED, u + 64, 136, u + 251, 128, u + 251, 191, u + 72, 229);
        poly(SILVER, u + 7, 137, u + 14, 124, u + 53, 113, u + 256, 119, u + 250, 127, u + 68, 138);
        poly(DEEP, u + 16, 147, u + 52, 144, u + 56, 182, u + 10, 190);
        for (int k = 0; k < 6; k++) {
            int x = u + 74 + k * 30;
            poly(
                    glass,
                    x,
                    142 - k * .7,
                    x + 23,
                    140 - k * .7,
                    x + 24,
                    170 - k * 3,
                    x + 2,
                    175 - k * 3);
        }
        poly(PAPER, u + 71, 203, u + 252, 172, u + 252, 180, u + 72, 215);
        oval(GOLD, u + 12, 206, 12, 10);
        oval(GOLD, u + 47, 200, 13, 10);
        line(INK, 4, u + 90, 120, u + 119, 94);
        line(INK, 4, u + 119, 94, u + 101, 75);
        line(SLATE, 4, u + 90, 75, u + 115, 74);
        oval(SILVER, -54, 271, 232, 64);
        oval(snow, 466, 240, 242, 73);
        for (int i = 0; i < 22; i++) {
            int x = rand.nextInt(632), y = rand.nextInt(219);
            oval(SILVER, x, y, 3.5, 3.5);
        }
    }

    void rain() {
        int road = time == Time.NIGHT ? 0x435363 : 0x637782;
        // Low layers of rain cloud cover the usual sun/stars; the shop lights reflect on wet
        // asphalt.
        rect(time.band, 0, 0, 640, 149);
        poly(time.sky, 0, 0, 640, 0, 640, 27, 504, 40, 435, 32, 301, 55, 193, 47, 83, 62, 0, 52);
        poly(
                time.distance,
                0,
                75,
                109,
                68,
                206,
                73,
                355,
                54,
                472,
                61,
                640,
                43,
                640,
                57,
                507,
                76,
                386,
                68,
                236,
                86,
                89,
                81,
                0,
                94);
        int[] lights = {0xf27fa5, 0xe7a248, 0x4fbcb7, 0xbc83cf, 0xdc913a};
        rect(road, 0, 171, 640, 149);
        rect(0x344d63, 0, 177, 640, 21);
        for (int i = 0; i < 5; i++) {
            int x = -7 + i * 134, top = 84 + rand.nextInt(26), neon = lights[i];
            rect(i % 2 == 0 ? INK : DEEP, x, top, 122, 180 - top);
            rect(neon, x + 5, top + 7, 111, 19);
            for (int j = 0; j < 3; j++) rect(INK, x + 13 + 21 * j, top + 13, 15, 6);
            rect(SLATE, x + 8, top + 32, 45, 144 - top);
            rect(neon, x + 13, top + 36, 35, 137 - top);
            rect(glass, x + 63, top + 32, 50, 150 - top);
            rect(TEAL, x + 68, top + 36, 18, 139 - top);
            rect(INK, x + 1, 170, 119, 8);
            // Three interrupted ribbons below their own shop, mirrored in the wet pavement.
            for (int k = 0; k < 3; k++) {
                int left = x + 5 + k * 6, y = 184 + k * 9;
                poly(
                        k == 1 ? BLUE : neon,
                        left,
                        y,
                        left + 95 - 15 * k,
                        y,
                        left + 81 - 15 * k,
                        y + 4,
                        left - 8,
                        y + 4);
            }
        }
        for (int i = 0; i < 7; i++) {
            int x = i * 109 - 35;
            poly(0xa4a8b8, x, 286, x + 53, 286, x + 31, 298, x - 27, 298);
        }
        int[] xs = {70, 203, 321, 464, 568},
                ys = {235, 213, 267, 216, 246},
                sizes = {47, 31, 66, 34, 48};
        int[] colors = {0xe48932, 0x4fbcb7, 0xdd4689, 0x9271bc, 0x369da7};
        for (int j : new int[] {1, 3, 0, 4, 2}) {
            int x = xs[j] + rand.nextInt(12), y = ys[j], r = sizes[j], fabric = colors[j];
            oval(INK, x - 5, y - r * .98, 12, r * .26);
            poly(INK, x - 10, y - r * .8, x + 8, y - r * .8, x + 15, y - 7, x - 17, y - 7);
            line(DEEP, 5, x - 8, y - 7, x - 13, y + 8);
            line(INK, 6, x + 4, y - 7, x + 12, y + 9);
            var canopy = new Arc2D.Double(x - r, y - r * 1.57, 2 * r, 1.20 * r, 0, 180, Arc2D.PIE);
            fill(fabric, canopy);
            poly(
                    j == 2 ? 0xff87ac : PAPER,
                    x,
                    y - r * 1.57,
                    x + r * .46,
                    y - r * .97,
                    x + r * .18,
                    y - r * .97);
            line(INK, 3, x - r, y - r * .97, x + r, y - r * .97);
            line(fabric, 5, x - 20, y + 15, x + 21, y + 15);
            line(fabric, 3, x - 8, y + 22, x + 10, y + 22);
        }
        for (int i = 0; i < 13; i++) {
            int x = i * 51 + rand.nextInt(21), y = 12 + rand.nextInt(125);
            line(time == Time.NIGHT ? MINT : BLUE, 3.3, x, y, x - 9, y + 17);
        }
    }

    void museum() {
        rect(SAND, 0, 210, 640, 110);
        for (int i = 0; i < 4; i++) line(PAPER, 3, -100 + i * 180, 320, 266 + i * 44, 211);
        Path2D shell = new Path2D.Double();
        shell.moveTo(55, 211);
        shell.curveTo(114, 208, 108, 139, 206, 90);
        shell.curveTo(285, 49, 408, 38, 475, 66);
        shell.curveTo(384, 81, 336, 117, 305, 160);
        shell.curveTo(388, 142, 469, 134, 539, 164);
        shell.lineTo(517, 210);
        shell.closePath();
        stroke(SLATE, 6, shell);
        fill(0xd1b1a1, shell);
        Path2D fold = new Path2D.Double();
        fold.moveTo(111, 207);
        fold.curveTo(181, 190, 268, 83, 454, 66);
        fold.curveTo(368, 96, 322, 129, 305, 161);
        fold.curveTo(235, 189, 192, 211, 111, 207);
        fold.closePath();
        fill(MINT, fold);
        poly(DEEP, 77, 211, 306, 162, 516, 180, 502, 211);
        for (int i = 0; i < 11; i++) {
            int x = 127 + i * 32, y = (int) (203 - 32 * Math.sin(i * Math.PI / 11));
            line(glass, 5, x, y, x - 6, 209);
        }
        curve(PAPER, 7, 294, 170, 380, 144, 470, 145, 538, 164);
        // Interlocked freestanding ribbons on a granite plinth.
        int x = 438 + rand.nextInt(28);
        oval(0xb49b85, x - 51, 284, 128, 12);
        poly(SLATE, x - 48, 277, x + 40, 267, x + 74, 279, x - 13, 291);
        stroke(CORAL, 14, new Ellipse2D.Double(x - 30, 197, 61, 77));
        poly(GOLD, x - 40, 270, x + 26, 184, x + 40, 187, x - 14, 273);
        for (int j = 0; j < 2; j++) tree(36 + j * 552, 231, 23);
    }

    void turbine(int x, int y, int hub, double radius, double angle) {
        poly(SILVER, x - 5, y, x - 2, hub, x + 3, hub, x + 6, y);
        for (int j = 0; j < 3; j++) {
            double a = angle + 2 * Math.PI * j / 3, dx = Math.cos(a), dy = Math.sin(a);
            poly(
                    PAPER,
                    x - 2 * dy,
                    hub + 2 * dx,
                    x + radius * .32 * dx - 5 * dy,
                    hub + radius * .32 * dy + 5 * dx,
                    x + radius * dx - 1.2 * dy,
                    hub + radius * dy + 1.2 * dx,
                    x + radius * dx + 1.2 * dy,
                    hub + radius * dy - 1.2 * dx,
                    x + radius * .39 * dx + 2 * dy,
                    hub + radius * .39 * dy - 2 * dx);
        }
        oval(SLATE, x - 5, hub - 5, 10, 10);
    }

    void wind() {
        river(205);
        poly(SAGE, 0, 201, 96, 168, 182, 175, 271, 159, 370, 189, 437, 201);
        for (int j = 0; j < 5; j++)
            tower(20 + j * 51, 116 + rand.nextInt(45), 23, 192, MINT, SLATE);
        double angle = rand.nextDouble() * .42 - .5;
        turbine(463, 240, 108, 84, angle);
        turbine(329, 222, 145, 51, angle + .72);
        turbine(237, 212, 162, 35, angle - .68);
        poly(SAND, 0, 258, 109, 230, 181, 235, 236, 250, 315, 257, 372, 280, 457, 320, 0, 320);
        poly(SAGE, 0, 247, 99, 223, 176, 230, 242, 251, 183, 247, 113, 240, 0, 275);
        curve(PAPER, 6, -20, 301, 72, 230, 158, 247, 236, 273);
        for (int j = 0; j < 4; j++) {
            int x = 31 + j * 37;
            line(PINE, 4, x, 284 - j * 6, x - 6, 269 - j * 6);
            line(LEAF, 4, x, 284 - j * 6, x + 7, 265 - j * 6);
        }
    }
}
