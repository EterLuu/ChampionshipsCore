package ink.ziip.championshipscore.api.game.iamnotarobot.model;

import static org.junit.jupiter.api.Assertions.assertTrue;

import ink.ziip.championshipscore.api.game.iamnotarobot.geometry.SliderHall;
import ink.ziip.championshipscore.platform.bukkit.bingo.map.MapColorMatcher;

import org.junit.jupiter.api.Test;

import java.awt.Color;

/** Piece masks in map space. A pixel belongs to a rectangle if its centre is inside. */
class SliderContrastTest {
    private static final double FILL = luminance(new Color(0xcfd5d6));
    private static final double MIN_RATIO = 1.25;
    static final long[] SEEDS = {37, 902, -7919};

    static double luminance(Color color) {
        return .2126 * linear(color.getRed())
                + .7152 * linear(color.getGreen())
                + .0722 * linear(color.getBlue());
    }

    private static double linear(int channel) {
        double v = channel / 255.0;
        return v <= .04045 ? v / 12.92 : Math.pow((v + .055) / 1.055, 2.4);
    }

    record Result(
            double bandShare,
            double worstShare,
            double lowestRatio,
            int column,
            int row,
            SliderShape shape) {}

    private record Table(int stride, int[] near, int[] light) {
        int sum(int[] array, int left, int top, int right, int bottom) {
            return array[bottom * stride + right]
                    - array[top * stride + right]
                    - array[bottom * stride + left]
                    + array[top * stride + left];
        }
    }

    private static Table table(SliderArt.Postcard card, int width) {
        byte[] pixels = SliderArt.mapPixels(card.draw(width, width / 2));
        var t =
                new Table(
                        width + 1,
                        new int[(width + 1) * (width / 2 + 1)],
                        new int[(width + 1) * (width / 2 + 1)]);
        int[] light = new int[256], near = new int[256];
        for (int i = 0; i < 256; i++) {
            double y = luminance(MapColorMatcher.color((byte) i));
            light[i] = (int) Math.round(y * 1000);
            // Include anything brighter than the concrete as well as near-equal luminance.
            near[i] = (FILL + .05) / (y + .05) < MIN_RATIO ? 1 : 0;
        }
        for (int y = 0; y < width / 2; y++) {
            int hits = 0, sum = 0;
            for (int x = 0; x < width; x++) {
                int index = pixels[y * width + x] & 255;
                hits += near[index];
                sum += light[index];
                int pos = (y + 1) * t.stride + x + 1;
                t.near[pos] = t.near[pos - t.stride] + hits;
                t.light[pos] = t.light[pos - t.stride] + sum;
            }
        }
        return t;
    }

    private static int edge(double blocks, double scale) {
        return (int) Math.ceil(blocks * scale - .5);
    }

    static Result measure(SliderArt.Postcard card, int width) {
        var t = table(card, width);
        double scale = (double) width / SliderHall.MAP_COLUMNS;
        int first = edge(SliderHall.coreTop(1) - SliderHall.TAB_SIZE, scale);
        int end = edge(SliderHall.coreTop(7) + SliderHall.CORE_SIZE + SliderHall.TAB_SIZE, scale);
        double band = (double) t.sum(t.near, 0, first, width, end) / (width * (end - first));
        double worst = -1, lowest = Double.POSITIVE_INFINITY;
        int worstColumn = 0, worstRow = 0;
        SliderShape worstShape = null;
        for (int row = 1; row <= 7; row++)
            for (int column = 0; column <= SliderHall.LAST; column++) {
                double x = SliderHall.coreLeft(column), y = SliderHall.coreTop(row);
                for (var shape : SliderShape.STYLES) {
                    int area = 0, bright = 0, sum = 0;
                    for (var r : shape.fill) {
                        int l = edge(x + r.x(), scale), u = edge(y + r.y(), scale);
                        int right = edge(x + r.x() + r.w(), scale),
                                bottom = edge(y + r.y() + r.h(), scale);
                        area += (right - l) * (bottom - u);
                        bright += t.sum(t.near, l, u, right, bottom);
                        sum += t.sum(t.light, l, u, right, bottom);
                    }
                    double share = (double) bright / area;
                    lowest =
                            Math.min(lowest, (FILL + .05) / (.05 + (double) sum / (area * 1000.0)));
                    if (share > worst) {
                        worst = share;
                        worstColumn = column;
                        worstRow = row;
                        worstShape = shape;
                    }
                }
            }
        return new Result(band, worst, lowest, worstColumn, worstRow, worstShape);
    }

    @Test
    void thePieceHasABolderContourThanTheNotches() {
        for (var shape : SliderShape.STYLES) {
            assertTrue(SliderHall.PIECE_OUTLINE > SliderHall.OUTLINE);
            for (var edge : shape.pieceOutline) {
                org.junit.jupiter.api.Assertions.assertEquals(
                        .09, Math.min(edge.h(), edge.w()), 1e-9);
            }
        }
    }

    @Test
    void backdropStaysDarkerAcrossTheBandAndInsideEveryPieceMask() {
        for (var scene : SliderArt.Scene.values())
            for (var time : SliderArt.Time.values())
                for (long seed : SEEDS) {
                    var card = new SliderArt.Postcard(scene, time, seed);
                    var result = measure(card, 512);
                    assertTrue(
                            result.bandShare <= .03 && result.worstShare <= .03,
                            () -> card + " " + result);
                }
    }
}
