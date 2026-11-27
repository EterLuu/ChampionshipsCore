package ink.ziip.championshipscore.api.game.iamnotarobot.model;

import ink.ziip.championshipscore.api.game.iamnotarobot.model.SliderArt.Scene;
import ink.ziip.championshipscore.api.game.iamnotarobot.model.SliderArt.Time;

import java.awt.*;
import java.awt.geom.*;
import java.util.Random;

/** Shared colour, lighting and drawing primitives on the 640 by 320 design grid. */
abstract class CityCanvas {
    final Graphics2D g;
    final Time time;
    final Random rand;
    static final int INK = 0x293e51,
            DEEP = 0x285768,
            TEAL = 0x34878b,
            MINT = 0x7fa796,
            PAPER = 0xb4b1ac,
            SILVER = 0xb4b4b4,
            GOLD = 0xdc913a,
            RED = 0xc86545,
            PINE = 0x33685e,
            LEAF = 0x679a79,
            SAGE = 0xa4bb85,
            SLATE = 0x607b89;
    final int water, glass, lit;

    CityCanvas(Graphics2D g, Time time, Random rand) {
        this.g = g;
        this.time = time;
        this.rand = rand;
        boolean night = time == Time.NIGHT;
        water = night ? 0x416d84 : time == Time.DUSK ? 0x598793 : 0x4fb4b3;
        glass = night ? 0x50778a : 0x67a4ab;
        lit = night ? GOLD : PAPER;
    }

    void rect(int color, double x, double y, double w, double h) {
        fill(color, new Rectangle2D.Double(x, y, w, h));
    }

    void oval(int color, double x, double y, double w, double h) {
        fill(color, new Ellipse2D.Double(x, y, w, h));
    }

    void fill(int color, Shape shape) {
        g.setColor(new Color(color));
        g.fill(shape);
    }

    void stroke(int color, double width, Shape shape) {
        g.setColor(new Color(color));
        g.setStroke(
                new BasicStroke(
                        (float) Math.max(2, width), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(shape);
    }

    void line(int color, double width, double x, double y, double u, double v) {
        stroke(color, width, new Line2D.Double(x, y, u, v));
    }

    void poly(int color, double... points) {
        Path2D.Double p = new Path2D.Double();
        p.moveTo(points[0], points[1]);
        for (int i = 2; i < points.length; i += 2) p.lineTo(points[i], points[i + 1]);
        p.closePath();
        fill(color, p);
    }

    void draw(Scene scene) {
        int sunX =
                switch (scene) {
                    case SKYLINE, BRIDGE, WHEEL -> 450;
                    case AIRFIELD -> 345;
                    case BOULEVARD -> 315;
                    default -> 130;
                };
        sky(sunX + rand.nextInt(61) - 30);
        // The background has a quieter value range than the individually drawn landmarks.
        int horizon =
                switch (scene) {
                    case BOULEVARD, CBD_PLAZA -> 198;
                    case SEA_BRIDGE, WIND_COAST, MARINA, METRO_PLATFORM, HSR_STATION, BEACH -> 0;
                    case PARK_LAKE, ROOFTOP_GARDEN -> 174;
                    case TV_TOWER -> 252;
                    default -> 220;
                };
        if (horizon != 0) distantCity(horizon);
        subject(scene);
    }

    abstract void subject(Scene scene);

    void sky(int sx) {
        rect(time.sky, 0, 0, 640, 320);
        int y =
                switch (time) {
                    case SUNRISE -> 107;
                    case NOON -> 59;
                    case DUSK -> 108;
                    case NIGHT -> 62;
                };
        y += rand.nextInt(15) - 7;
        // Long low layers behind the sun, with gently uneven edges like printed cut paper.
        poly(time.band, 0, 111, 114, 107, 219, 108, 390, 90, 490, 95, 640, 80, 640, 320, 0, 320);
        poly(
                time.horizon,
                0,
                172,
                120,
                151,
                248,
                156,
                420,
                143,
                530,
                146,
                640,
                124,
                640,
                320,
                0,
                320);
        int r = time == Time.NOON ? 29 : time == Time.NIGHT ? 25 : 43;
        oval(time.sun, sx - r, y - r, 2 * r, 2 * r);
        if (time == Time.NIGHT) {
            oval(time.sky, sx - r - 7, y - r - 6, 2 * r - 4, 2 * r - 4);
            for (int i = 0; i < 17; i++) {
                int x = rand.nextInt(630), sy = 8 + rand.nextInt(64);
                if (Math.abs(x - sx) > 40)
                    poly(PAPER, x, sy - 3.5, x + 2.6, sy, x, sy + 3.5, x - 2.6, sy);
            }
        } else {
            for (int i = 0; i < 3; i++) {
                int x = i * 225 + rand.nextInt(58) - 15, cy = 22 + rand.nextInt(56);
                if (Math.abs(x + 40 - sx) < 62) continue;
                poly(
                        time == Time.DUSK ? 0xf0c8af : 0xb4b1ac,
                        x,
                        cy + 9,
                        x + 16,
                        cy + 9,
                        x + 27,
                        cy + 1,
                        x + 46,
                        cy + 1,
                        x + 52,
                        cy - 5,
                        x + 65,
                        cy - 5,
                        x + 78,
                        cy + 6,
                        x + 93,
                        cy + 7,
                        x + 96,
                        cy + 11,
                        x,
                        cy + 11);
            }
        }
    }

    void distantCity(int floor) {
        int x = -18;
        while (x < 650) {
            int w = 16 + rand.nextInt(24), h = 18 + rand.nextInt(55), y = floor - h;
            rect(time.distance, x, y, w, h);
            if (rand.nextInt(3) == 0) rect(time.distance, x + 5, y - 8, w - 10, 8);
            if (time == Time.NIGHT && h > 32 && w > 20) {
                for (int row = 0; row < 2; row++)
                    if (rand.nextBoolean()) rect(0xaea473, x + 5, y + 9 + row * 12, w - 11, 4);
            }
            x += w + rand.nextInt(9);
        }
    }

    void river(int y) {
        rect(water, 0, y, 640, 320 - y);
        rect(MINT, 0, y, 640, 5);
        for (int i = 0; i < 24; i++) {
            int x = rand.nextInt(668) - 28, ry = y + 8 + rand.nextInt(310 - y);
            poly(
                    i % 3 == 0 ? DEEP : MINT,
                    x,
                    ry,
                    x + 22,
                    ry,
                    x + 35 + rand.nextInt(45),
                    ry + 3,
                    x + 7,
                    ry + 4);
        }
    }

    void tower(int x, int y, int w, int base, int face, int side) {
        rect(face, x, y, w, base - y);
        poly(side, x + w, y, x + w + 14, y - 8, x + w + 14, base, x + w, base);
        poly(PAPER, x, y, x + 13, y - 8, x + w + 14, y - 8, x + w, y + 2);
        for (int v = y + 12; v < base - 8; v += 18) rect(glass, x + 5, v, w - 10, 6);
    }

    void ferry(int x, int y, double scale) {
        var transform = g.getTransform();
        g.translate(x, y);
        g.scale(scale, scale);
        poly(INK, 0, 0, 100, 0, 85, 11, 17, 11);
        poly(SILVER, 12, -5, 28, -19, 65, -19, 81, -5, 95, -5, 100, 0, 2, 0);
        poly(DEEP, 28, -14, 59, -14, 66, -6, 21, -6);
        rect(RED, 10, 0, 84, 4);
        g.setTransform(transform);
    }

    void tree(int x, int ground, int size) {
        rect(INK, x - size * .11, ground - size, size * .22, size);
        oval(PINE, x - size * .75, ground - size * 2.15, size * 1.5, size * 1.65);
        oval(LEAF, x - size * .72, ground - size * 2.23, size * 1.17, size * 1.20);
    }
}
