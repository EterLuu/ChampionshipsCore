package ink.ziip.championshipscore.worker.seedlab;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;

/** Embedded Minecraft 26.2 Overworld multi-noise predictor.
 * Data and formulas are derived from mc-worldgen-seed-lab (MIT licensed).
 */
public final class SeedLab26_2 {
    private static final String CLIMATE_RESOURCE = "/seedlab/data/climate-26.2.txt";
    private static final String BIOMES_RESOURCE = "/seedlab/data/overworld-26.2.tsv";
    private static final int QUANT = 10_000;
    private static final long INF = Long.MAX_VALUE;
    private static volatile SeedLab26_2 INSTANCE;

    private final Spec spec;
    private final BiomeTree tree;

    private SeedLab26_2() throws IOException {
        Parsed parsed = Parsed.load();
        this.spec = parsed.spec;
        this.tree = new BiomeTree(parsed.boxes, parsed.names);
    }

    public static SeedLab26_2 instance() throws IOException {
        SeedLab26_2 value = INSTANCE;
        if (value == null) {
            synchronized (SeedLab26_2.class) {
                value = INSTANCE;
                if (value == null) INSTANCE = value = new SeedLab26_2();
            }
        }
        return value;
    }

    /** Returns the number of distinct Overworld biomes sampled around spawn. */
    public int score(long seed, int radiusBlocks, int sampleStepBlocks, long deadlineNanos) {
        if (radiusBlocks < 4 || radiusBlocks % 4 != 0) throw new IllegalArgumentException("radiusBlocks must be divisible by 4");
        if (sampleStepBlocks < 4 || sampleStepBlocks % 4 != 0) throw new IllegalArgumentException("sampleStepBlocks must be divisible by 4");
        int qr = radiusBlocks / 4, qs = sampleStepBlocks / 4;
        int side = (qr * 2) / qs + 1;
        Climate climate = new Climate(spec, seed);
        Set<String> biomes = new HashSet<>();
        for (int z = -qr; z <= qr; z += qs) {
            for (int x = -qr; x <= qr; x += qs) {
                if (System.nanoTime() >= deadlineNanos) throw new SeedFilterTimeoutException();
                if ((long) x * x + (long) z * z > (long) qr * qr) continue;
                biomes.add(tree.find(climate.biome(x * 4, 64, z * 4)));
            }
        }
        return biomes.size();
    }

    /** Exception used internally to stop a candidate without a stack trace. */
    public static final class SeedFilterTimeoutException extends RuntimeException {
        public SeedFilterTimeoutException() { super(null, null, false, false); }
    }

    public String biome(long seed, int blockX, int blockY, int blockZ) {
        return tree.find(new Climate(spec, seed).biome(blockX, blockY, blockZ));
    }

    private record Parsed(Spec spec, Box[] boxes, String[] names) {
        static Parsed load() throws IOException {
            Map<String, NoiseParams> noises = new HashMap<>();
            List<PresetLine> presets = new ArrayList<>();
            Map<String, float[]> depths = new HashMap<>();
            Map<String, Spline> splines = new HashMap<>();
            double[] folded = new double[3];
            try (BufferedReader reader = resource(CLIMATE_RESOURCE)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank() || line.startsWith("#")) continue;
                    String[] t = line.trim().split("\\s+");
                    switch (t[0]) {
                        case "noise" -> {
                            NoiseParams p = new NoiseParams(Integer.parseInt(t[3]), Integer.parseInt(t[4]));
                            for (int i = 0; i < p.amplitudes.length; i++) p.amplitudes[i] = Double.parseDouble(t[8 + i]);
                            noises.put(t[1], p);
                        }
                        case "preset" -> presets.add(new PresetLine(t[1], Arrays.copyOfRange(t, 2, 8)));
                        case "depth" -> depths.put(t[1], new float[]{Float.parseFloat(t[2]), Float.parseFloat(t[3]), Float.parseFloat(t[4]), Float.parseFloat(t[5]), Float.parseFloat(t[6])});
                        case "offset_spline" -> {
                            Tokenizer parser = new Tokenizer(line);
                            parser.next();
                            String presetName = parser.next();
                            splines.put(presetName, Spline.parse(parser));
                        }
                        case "ridges_folded" -> { for (int i = 0; i < 3; i++) folded[i] = Double.parseDouble(t[i + 1]); }
                        default -> { }
                    }
                }
            }
            PresetLine normal = presets.stream().filter(p -> p.name.equals("overworld")).findFirst().orElseThrow();
            float[] depth = depths.get(normal.name);
            NoiseSpec[] ns = new NoiseSpec[6];
            String[] names = {"offset", normal.noises[0], normal.noises[1], normal.noises[2], normal.noises[3], normal.noises[4]};
            for (int i = 0; i < ns.length; i++) ns[i] = new NoiseSpec(noises.get(names[i]), "minecraft:" + names[i]);
            BoxList boxList = readBoxes();
            return new Parsed(new Spec(ns, depth, folded, splines.get(normal.name)), boxList.boxes, boxList.names);
        }

        private static BufferedReader resource(String path) throws IOException {
            InputStream in = SeedLab26_2.class.getResourceAsStream(path);
            if (in == null) throw new IOException("Missing embedded SeedLab resource " + path);
            return new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        }

        private static BoxList readBoxes() throws IOException {
            List<Box> boxes = new ArrayList<>();
            List<String> names = new ArrayList<>();
            try (BufferedReader reader = resource(BIOMES_RESOURCE)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank() || line.startsWith("#")) continue;
                    String[] t = line.split("\\t", 14);
                    if (t.length < 14) t = line.trim().split("\\s+", 14);
                    if (t.length < 14) continue;
                    long[] v = new long[13];
                    for (int i = 0; i < 13; i++) v[i] = Long.parseLong(t[i]);
                    long[] lo = new long[7], hi = new long[7];
                    for (int i = 0; i < 6; i++) { lo[i] = v[i * 2]; hi[i] = v[i * 2 + 1]; }
                    lo[6] = hi[6] = v[12];
                    boxes.add(new Box(lo, hi));
                    names.add(t[13].trim());
                }
            }
            return new BoxList(boxes.toArray(Box[]::new), names.toArray(String[]::new));
        }
    }

    private record BoxList(Box[] boxes, String[] names) {}
    private record PresetLine(String name, String[] noises) {}
    private static final class NoiseParams {
        final int firstOctave;
        final double[] amplitudes;
        NoiseParams(int firstOctave, int count) { this.firstOctave = firstOctave; this.amplitudes = new double[count]; }
    }
    private static final class NoiseSpec {
        final int firstOctave, levels;
        final int[] indexes;
        final double[] freq, amp, valueFactor;
        final long[] hashLo, hashHi;
        NoiseSpec(NoiseParams p, String name) {
            if (p == null) throw new IllegalArgumentException("Missing noise " + name);
            this.firstOctave = p.firstOctave;
            int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
            for (int i = 0; i < p.amplitudes.length; i++) if (p.amplitudes[i] != 0) { min = Math.min(min, i); max = Math.max(max, i); }
            int span = max - min + 1;
            int count = 0;
            for (double amplitude : p.amplitudes) if (amplitude != 0) count++;
            this.levels = count;
            this.indexes = new int[levels]; this.freq = new double[levels]; this.amp = new double[levels]; this.valueFactor = new double[levels];
            this.hashLo = new long[levels]; this.hashHi = new long[levels];
            double f = Math.scalb(1.0, -(-firstOctave));
            double vf = Math.scalb(1.0, p.amplitudes.length - 1) / (Math.scalb(1.0, p.amplitudes.length) - 1.0);
            double factor = 0.16666666666666666 / (0.1 * (1.0 + 1.0 / span));
            int k = 0;
            for (int i = 0; i < p.amplitudes.length; i++) if (p.amplitudes[i] != 0) {
                indexes[k] = i; freq[k] = f; amp[k] = p.amplitudes[i]; valueFactor[k] = vf;
                long[] h = md5Seed("octave_" + (firstOctave + i)); hashLo[k] = h[0]; hashHi[k] = h[1]; k++;
                f *= Math.scalb(1.0, 1); vf *= 0.5;
            } else { f *= 2.0; vf *= 0.5; }
            this.factor = factor;
        }
        final double factor;
    }

    private record Spec(NoiseSpec[] noises, float[] depth, double[] folded, Spline spline) {}

    private static final class Climate {
        final Spec spec;
        final NormalNoise[] noises = new NormalNoise[6];
        Climate(Spec spec, long seed) {
            this.spec = spec;
            Xoro root = Xoro.fromSeed(seed);
            XoroPos positional = root.forkPositional();
            for (int i = 0; i < noises.length; i++) noises[i] = new NormalNoise(spec.noises[i], positional.fromHash("minecraft:" + noiseName(i)));
        }
        private static String noiseName(int i) { return new String[]{"offset", "temperature", "vegetation", "continentalness", "erosion", "ridge"}[i]; }
        Target biome(int bx, int by, int bz) {
            double sx = noises[0].get(bx * .25, 0, bz * .25) * 4.0;
            double sz = noises[0].get(bz * .25, bx * .25, 0) * 4.0;
            double x = bx * .25 + sx, z = bz * .25 + sz;
            float temp = (float) noises[1].get(x, 0, z), veg = (float) noises[2].get(x, 0, z);
            float cont = (float) noises[3].get(x, 0, z), eros = (float) noises[4].get(x, 0, z), ridge = (float) noises[5].get(x, 0, z);
            double folded = (Math.abs(Math.abs(ridge) + spec.folded[0]) + spec.folded[1]) * spec.folded[2];
            float offset = spec.depth[4] + spec.spline.eval(new float[]{cont, eros, ridge, (float) folded});
            double factor = (by - spec.depth[0]) / (spec.depth[1] - spec.depth[0]);
            double grad = factor < 0 ? spec.depth[2] : factor > 1 ? spec.depth[3] : spec.depth[2] + factor * (spec.depth[3] - spec.depth[2]);
            return new Target(q(temp), q(veg), q(cont), q(eros), q((float) (grad + offset)), q(ridge));
        }
    }
    private record Target(long t, long h, long c, long e, long d, long w) {}
    private static long q(float v) { return (long) (v * QUANT); }

    private static final class NormalNoise {
        final NoiseSpec spec;
        final Improved[] first, second;
        NormalNoise(NoiseSpec spec, Xoro random) {
            this.spec = spec; this.first = new Improved[spec.levels]; this.second = new Improved[spec.levels];
            XoroPos p1 = random.forkPositional(), p2 = random.forkPositional();
            for (int i = 0; i < spec.levels; i++) {
                Xoro a = p1.fromHash(spec.hashLo[i], spec.hashHi[i]);
                Xoro b = p2.fromHash(spec.hashLo[i], spec.hashHi[i]);
                first[i] = new Improved(a); second[i] = new Improved(b);
            }
        }
        double get(double x, double y, double z) {
            double x2 = x * 1.0181268882175227, y2 = y * 1.0181268882175227, z2 = z * 1.0181268882175227;
            double a = 0, b = 0;
            for (int i = 0; i < spec.levels; i++) a += spec.amp[i] * first[i].noise(wrap(x * spec.freq[i]), wrap(y * spec.freq[i]), wrap(z * spec.freq[i])) * spec.valueFactor[i];
            for (int i = 0; i < spec.levels; i++) b += spec.amp[i] * second[i].noise(wrap(x2 * spec.freq[i]), wrap(y2 * spec.freq[i]), wrap(z2 * spec.freq[i])) * spec.valueFactor[i];
            return (a + b) * spec.factor;
        }
        private static double wrap(double x) { return x - Math.floor(x / 33554432.0 + .5) * 33554432.0; }
    }

    private static final class Improved {
        final double xo, yo, zo; final byte[] p = new byte[256];
        Improved(Xoro random) {
            xo = random.nextDouble() * 256; yo = random.nextDouble() * 256; zo = random.nextDouble() * 256;
            for (int i = 0; i < 256; i++) p[i] = (byte) i;
            for (int i = 0; i < 256; i++) { int off = random.nextIntBound(256 - i); byte t = p[i]; p[i] = p[i + off]; p[i + off] = t; }
        }
        double noise(double xx, double yy, double zz) {
            double x = xx + xo, y = yy + yo, z = zz + zo;
            int xf = floor(x), yf = floor(y), zf = floor(z); double xr = x - xf, yr = y - yf, zr = z - zf;
            int x0 = at(xf), x1 = at(xf + 1), xy00 = at(x0 + yf), xy01 = at(x0 + yf + 1), xy10 = at(x1 + yf), xy11 = at(x1 + yf + 1);
            double d000 = dot(at(xy00 + zf), xr, yr, zr), d100 = dot(at(xy10 + zf), xr - 1, yr, zr);
            double d010 = dot(at(xy01 + zf), xr, yr - 1, zr), d110 = dot(at(xy11 + zf), xr - 1, yr - 1, zr);
            double d001 = dot(at(xy00 + zf + 1), xr, yr, zr - 1), d101 = dot(at(xy10 + zf + 1), xr - 1, yr, zr - 1);
            double d011 = dot(at(xy01 + zf + 1), xr, yr - 1, zr - 1), d111 = dot(at(xy11 + zf + 1), xr - 1, yr - 1, zr - 1);
            double xa = smooth(xr), ya = smooth(yr), za = smooth(zr);
            return lerp(za, lerp(ya, lerp(xa, d000, d100), lerp(xa, d010, d110)), lerp(ya, lerp(xa, d001, d101), lerp(xa, d011, d111)));
        }
        int at(int i) { return p[i & 255] & 255; }
        static int floor(double x) { int i = (int) x; return x < i ? i - 1 : i; }
        static double smooth(double x) { return x * x * x * (x * (x * 6 - 15) + 10); }
        static double lerp(double a, double x, double y) { return x + a * (y - x); }
        static double dot(int h, double x, double y, double z) {
            int k = h & 15;
            int[] gx = {1, -1, 1, -1, 1, -1, 1, -1, 0, 0, 0, 0, 1, 0, -1, 0};
            int[] gy = {1, 1, -1, -1, 0, 0, 0, 0, 1, -1, 1, -1, 1, -1, 1, -1};
            int[] gz = {0, 0, 0, 0, 1, 1, -1, -1, 1, 1, -1, -1, 0, 1, 0, -1};
            return gx[k] * x + gy[k] * y + gz[k] * z;
        }
    }

    private static final class Spline {
        final int coordinate; final float[] locations, derivatives; final Spline[] children; final Float constant;
        Spline(float constant) { this.constant = constant; coordinate = 0; locations = derivatives = null; children = null; }
        Spline(int coordinate, float[] locations, float[] derivatives, Spline[] children) { this.constant = null; this.coordinate = coordinate; this.locations = locations; this.derivatives = derivatives; this.children = children; }
        float eval(float[] coords) {
            if (constant != null) return constant;
            float input = coords[coordinate]; int n = locations.length, start = 0;
            while (start < n && !(input < locations[start])) start++;
            start--;
            if (start < 0) return extrapolate(input, 0, coords);
            if (start == n - 1) return extrapolate(input, start, coords);
            float x1 = locations[start], x2 = locations[start + 1], t = (input - x1) / (x2 - x1);
            float y1 = children[start].eval(coords), y2 = children[start + 1].eval(coords);
            float a = derivatives[start] * (x2 - x1) - (y2 - y1), b = -derivatives[start + 1] * (x2 - x1) + (y2 - y1);
            return lerp(t, y1, y2) + t * (1 - t) * lerp(t, a, b);
        }
        private float extrapolate(float input, int i, float[] coords) { float v = children[i].eval(coords); return derivatives[i] == 0 ? v : v + derivatives[i] * (input - locations[i]); }
        private static float lerp(float a, float x, float y) { return x + a * (y - x); }
        static Spline parse(Tokenizer t) {
            String kind = t.next();
            if (kind.equals("C")) return new Spline(Float.parseFloat(t.next()));
            int coordinate = Integer.parseInt(t.next()), n = Integer.parseInt(t.next());
            float[] loc = new float[n], der = new float[n]; Spline[] children = new Spline[n];
            for (int i = 0; i < n; i++) { loc[i] = Float.parseFloat(t.next()); der[i] = Float.parseFloat(t.next()); children[i] = parse(t); }
            return new Spline(coordinate, loc, der, children);
        }
    }
    private static final class Tokenizer {
        final String[] tokens; int index;
        Tokenizer(String line) { tokens = line.trim().split("\\s+"); }
        String next() { return tokens[index++]; }
    }

    private static final class BiomeTree {
        final Node root;
        BiomeTree(Box[] boxes, String[] names) { int[] indexes = new int[boxes.length]; for (int i = 0; i < indexes.length; i++) indexes[i] = i; root = build(boxes, names, indexes, 0, indexes.length); }
        String find(Target target) { long[] q = {target.t, target.h, target.c, target.e, target.d, target.w, 0}; Best best = new Best(); search(root, q, best); return best.name; }
        private static void search(Node node, long[] q, Best best) {
            if (distance(node.box, q) >= best.distance) return;
            if (node.leafIndexes != null) { for (int i : node.leafIndexes) { long d = distance(node.leafBoxes[i], q); if (d < best.distance) { best.distance = d; best.name = node.names[i]; } } return; }
            Node a = node.left, b = node.right; if (distance(a.box, q) > distance(b.box, q)) { a = node.right; b = node.left; }
            search(a, q, best); search(b, q, best);
        }
        private static long distance(Box box, long[] q) { long d = 0; for (int i = 0; i < 7; i++) { long delta = q[i] < box.lo[i] ? box.lo[i] - q[i] : q[i] > box.hi[i] ? q[i] - box.hi[i] : 0; d += delta * delta; } return d; }
        private static Node build(Box[] boxes, String[] names, int[] ids, int from, int to) {
            Node n = new Node(); n.box = bounds(boxes, ids, from, to);
            if (to - from <= 16) { n.leafBoxes = boxes; n.names = names; n.leafIndexes = Arrays.copyOfRange(ids, from, to); return n; }
            int dim = widest(n.box); Integer[] order = new Integer[to - from]; for (int i = 0; i < order.length; i++) order[i] = ids[from + i];
            Arrays.sort(order, (a, b) -> Long.compare(center(boxes[a], dim), center(boxes[b], dim)));
            for (int i = 0; i < order.length; i++) ids[from + i] = order[i]; int mid = (from + to) >>> 1;
            n.left = build(boxes, names, ids, from, mid); n.right = build(boxes, names, ids, mid, to); return n;
        }
        private static long center(Box b, int d) { return (b.lo[d] + b.hi[d]) / 2; }
        private static int widest(Box b) { int dim = 0; long width = b.hi[0] - b.lo[0]; for (int i = 1; i < 7; i++) if (b.hi[i] - b.lo[i] > width) { width = b.hi[i] - b.lo[i]; dim = i; } return dim; }
        private static Box bounds(Box[] boxes, int[] ids, int from, int to) { long[] lo = boxes[ids[from]].lo.clone(), hi = boxes[ids[from]].hi.clone(); for (int j = from + 1; j < to; j++) for (int d = 0; d < 7; d++) { lo[d] = Math.min(lo[d], boxes[ids[j]].lo[d]); hi[d] = Math.max(hi[d], boxes[ids[j]].hi[d]); } return new Box(lo, hi); }
        private static final class Node { Box box; Box[] leafBoxes; String[] names; int[] leafIndexes; Node left, right; }
        private static final class Best { long distance = INF; String name = "minecraft:plains"; }
    }
    private record Box(long[] lo, long[] hi) {}

    private static final class Xoro {
        long lo, hi;
        static Xoro fromSeed(long seed) { long lo = mix(seed ^ 0x6A09E667F3BCC909L), hi = mix((seed ^ 0x6A09E667F3BCC909L) + 0x9E3779B97F4A7C15L); return new Xoro(lo, hi); }
        Xoro(long lo, long hi) { if ((lo | hi) == 0) { lo = 0x9E3779B97F4A7C15L; hi = 0x6A09E667F3BCC909L; } this.lo = lo; this.hi = hi; }
        long nextLong() { long s0 = lo, s1 = hi, result = Long.rotateLeft(s0 + s1, 17) + s0; s1 ^= s0; lo = Long.rotateLeft(s0, 49) ^ s1 ^ (s1 << 21); hi = Long.rotateLeft(s1, 28); return result; }
        double nextDouble() { return (nextLong() >>> 11) * 0x1.0p-53; }
        int nextIntBound(int bound) { long bits = nextLong() & 0xffffffffL, product = bits * bound, fraction = product & 0xffffffffL; if (fraction < bound) { long threshold = (-bound) & 0xffffffffL; threshold %= bound; while (fraction < threshold) { bits = nextLong() & 0xffffffffL; product = bits * bound; fraction = product & 0xffffffffL; } } return (int) (product >>> 32); }
        XoroPos forkPositional() { return new XoroPos(nextLong(), nextLong()); }
        static long mix(long z) { z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L; z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL; return z ^ (z >>> 31); }
    }
    private record XoroPos(long lo, long hi) {
        Xoro fromHash(String name) { long[] h = md5Seed(name); return fromHash(h[0], h[1]); }
        Xoro fromHash(long lo, long hi) { return new Xoro(lo ^ this.lo, hi ^ this.hi); }
    }
    private static long[] md5Seed(String name) {
        try {
            byte[] h = MessageDigest.getInstance("MD5").digest(name.getBytes(StandardCharsets.UTF_8)); long a = 0, b = 0;
            for (int i = 0; i < 8; i++) { a = (a << 8) | (h[i] & 255L); b = (b << 8) | (h[i + 8] & 255L); }
            return new long[]{a, b};
        } catch (NoSuchAlgorithmException e) { throw new AssertionError(e); }
    }
}
