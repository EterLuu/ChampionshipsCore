package ink.ziip.championshipscore.api.game.bingo.gui;

import net.kyori.adventure.text.format.TextColor;
import org.bukkit.map.MapPalette;
import org.jetbrains.annotations.Nullable;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Converts true-colour sprites to Minecraft map-palette indices with a perceptual nearest-colour
 * search (CIELAB / CIE94 ΔE94), then lets the renderer write those indices directly via
 * {@link org.bukkit.map.MapCanvas#setPixel}. Palette colours are pre-converted to Lab once; per-sprite
 * results are cached so the per-pixel work happens once on load rather than every map tick.
 */
final class MapColorMatcher {
    /** A palette colour in CIELAB plus its chroma C = hypot(a, b), precomputed for ΔE94. */
    private record Entry(byte index, double l, double a, double b, double chroma) {
    }

    /** Map index for a fully-transparent pixel. */
    static final byte TRANSPARENT = 0;

    /**
     * Card slot shade the sprites are painted over (from {@link TaskImageAtlas#SLOT_SHADE_KEEP}).
     * Translucent sprite pixels (stained glass, slime, honey, ice…) are alpha-composited onto this
     * before matching so they read as translucent rather than as a flat opaque block — otherwise
     * stained glass renders identically to dyed concrete.
     */
    private static final int BG_R = (TaskImageAtlas.SLOT_SHADE_KEEP >> 16) & 0xff;
    private static final int BG_G = (TaskImageAtlas.SLOT_SHADE_KEEP >> 8) & 0xff;
    private static final int BG_B = TaskImageAtlas.SLOT_SHADE_KEEP & 0xff;

    private static volatile Entry[] palette;
    private static final Map<BufferedImage, byte[]> CACHE = new ConcurrentHashMap<>();

    private MapColorMatcher() {
    }

    @SuppressWarnings("removal")
    private static Entry[] palette() {
        Entry[] p = palette;
        if (p != null) return p;
        synchronized (MapColorMatcher.class) {
            if (palette != null) return palette;
            List<Entry> entries = new ArrayList<>(256);
            for (int i = 4; i < 256; i++) { // 0..3 are the transparent slots
                Color c;
                try {
                    c = MapPalette.getColor((byte) i);
                } catch (IndexOutOfBoundsException ex) {
                    break;
                }
                if (c == null || c.getAlpha() < 128) continue;
                double[] lab = rgbToLab(c.getRed(), c.getGreen(), c.getBlue());
                double chroma = Math.hypot(lab[1], lab[2]);
                entries.add(new Entry((byte) i, lab[0], lab[1], lab[2], chroma));
            }
            return palette = entries.toArray(new Entry[0]);
        }
    }

    static byte matchColor(int r, int g, int b) {
        return nearest(palette(), r, g, b);
    }

    static byte[] indices(BufferedImage image) {
        return CACHE.computeIfAbsent(image, MapColorMatcher::compute);
    }

    static byte[] indices(BufferedImage image, @Nullable TextColor modulate) {
        if (modulate == null) return indices(image);
        return compute(image, modulate.red(), modulate.green(), modulate.blue());
    }

    private static byte[] compute(BufferedImage image) {
        return compute(image, -1, -1, -1);
    }

    private static byte[] compute(BufferedImage image, int mr, int mg, int mb) {
        Entry[] pal = palette();
        int w = image.getWidth(), h = image.getHeight();
        byte[] out = new byte[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = image.getRGB(x, y);
                int a = (argb >> 24) & 0xff;
                if (a == 0) {
                    out[y * w + x] = TRANSPARENT;
                    continue;
                }
                int r = (argb >> 16) & 0xff, g = (argb >> 8) & 0xff, b = argb & 0xff;
                // Composite semi-transparent pixels over the slot shade so translucent textures
                // (stained glass, slime, honey…) read as glassy instead of as a flat opaque block.
                // Opaque pixels (a == 255) pass through unchanged.
                if (a < 255) {
                    r = (r * a + BG_R * (255 - a)) / 255;
                    g = (g * a + BG_G * (255 - a)) / 255;
                    b = (b * a + BG_B * (255 - a)) / 255;
                }
                if (mr >= 0) {
                    r = r * mr / 255;
                    g = g * mg / 255;
                    b = b * mb / 255;
                }
                out[y * w + x] = nearest(pal, r, g, b);
            }
        }
        return out;
    }

    private static byte nearest(Entry[] pal, int r, int g, int b) {
        double[] lab = rgbToLab(r, g, b);
        double l1 = lab[0], a1 = lab[1], b1 = lab[2];
        double c1 = Math.hypot(a1, b1);
        double sc = 1.0 + 0.045 * c1;
        double sh = 1.0 + 0.015 * c1;
        double best = Double.MAX_VALUE;
        byte bestIdx = TRANSPARENT;
        for (Entry e : pal) {
            double dl = l1 - e.l;
            double dc = c1 - e.chroma;
            double da = a1 - e.a, db = b1 - e.b;
            double dh2 = da * da + db * db - dc * dc;
            if (dh2 < 0) dh2 = 0;
            double dist = dl * dl + (dc / sc) * (dc / sc) + dh2 / (sh * sh);
            if (dist < best) {
                best = dist;
                bestIdx = e.index;
                if (dist == 0) break;
            }
        }
        return bestIdx;
    }

    private static double[] rgbToLab(int r, int g, int b) {
        double rl = srgbToLinear(r), gl = srgbToLinear(g), bl = srgbToLinear(b);
        double x = (rl * 0.4124 + gl * 0.3576 + bl * 0.1805) / 0.95047;
        double y = rl * 0.2126 + gl * 0.7152 + bl * 0.0722;
        double z = (rl * 0.0193 + gl * 0.1192 + bl * 0.9505) / 1.08883;
        double fx = labF(x), fy = labF(y), fz = labF(z);
        return new double[] {116.0 * fy - 16.0, 500.0 * (fx - fy), 200.0 * (fy - fz)};
    }

    private static double srgbToLinear(int c) {
        double v = c / 255.0;
        return v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    }

    private static double labF(double t) {
        return t > 0.008856 ? Math.cbrt(t) : 7.787 * t + 16.0 / 116.0;
    }
}
