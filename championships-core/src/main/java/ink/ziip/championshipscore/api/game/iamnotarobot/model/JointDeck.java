package ink.ziip.championshipscore.api.game.iamnotarobot.model;

/** A match-wide, deterministic set of three excavation patterns. */
public final class JointDeck {
    private static final String[][] TENONS = {
        {".......", "..###..", "..###..", "..###..", "..###..", ".......", "......."},
        {"...#...", "...#...", "...#...", "#######", "...#...", "...#...", "...#..."},
        {".#####.", "...#...", "...#...", "...#...", "...#...", "...#...", "...#..."},
        {"..#....", "..#....", "..#....", "..#....", "..#....", "..#....", "..#####"},
        {".##.##.", ".##.##.", ".##.##.", ".#####.", ".......", ".......", "......."},
        {".#####.", ".#...#.", ".#...#.", ".#####.", ".......", ".......", "......."},
        {"##.....", ".##....", "..##...", "...##..", "....##.", ".....##", "......."},
        {"#...#..", "#...#..", "#####..", "#...#..", "#...#..", ".......", "......."}
    };
    private static final int[][] STEPS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    /** Every puzzle carves at least this many blocks off the white block's 6x7x7 region. */
    static final int MIN_REMOVED = 8;

    public static final int MAX_REMOVED = 40;

    /** Round-three carving styles. */
    enum Style {
        RECESSED,
        RELIEF,
        MIXED
    }

    record Carving(JointShape shape, Style style) {}

    /** A seeded stream of bounded draws. */
    private static final class Draws {
        private long state;

        Draws(long seed) {
            state = seed;
        }

        int next(int bound) {
            state = mix(state);
            return choose(state, bound);
        }
    }

    private final long seed;
    private final JointShape[] shapes = new JointShape[3];

    public JointDeck(long seed) {
        this.seed = seed;
        long first = mix(seed);
        long second = mix(first);
        long third = mix(second);
        shapes[0] = tenon(first);
        shapes[1] = pencil(second);
        shapes[2] = carving(third).shape();
    }

    public JointShape shape(int round) {
        if (round < 1 || round > shapes.length) throw new IllegalArgumentException("Invalid round");
        return shapes[round - 1];
    }

    public int[][] initialGrayCut(int round) {
        JointShape shape = shape(round);
        long draw = mix(seed + round);
        int manualBlocks =
                switch (round) {
                    case 1 -> 20 + choose(draw, 13);
                    case 2 -> 24 + choose(draw, 17);
                    default -> 32 + choose(draw, JointShape.MANUAL_GRAY_LIMIT - 32 + 1);
                };
        return shape.initialGrayCut(manualBlocks);
    }

    static long mix(long value) {
        value += 0x9e3779b97f4a7c15L;
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }

    private static int choose(long value, int bound) {
        return (int) Long.remainderUnsigned(value, bound);
    }

    private static JointShape tenon(long value) {
        var draws = new Draws(value);
        // Half of the tenons are random, connected cuts in the white face.
        String[] mask = draws.next(2) == 0 ? blob(draws) : TENONS[choose(value, TENONS.length)];
        int cells = 0;
        for (String line : mask) for (char c : line.toCharArray()) if (c == '#') cells++;
        int minDepth = Math.max(3, (36 + cells - 1) / cells);
        int maxDepth = Math.min(6, 72 / cells);
        int depth = minDepth + choose(value >>> 8, maxDepth - minDepth + 1);
        int rotation = choose(value >>> 12, 4);
        boolean flip = (value & 0x4000) != 0;
        int[][] result = new int[7][7];
        for (int row = 0; row < 7; row++) {
            for (int column = 0; column < 7; column++) {
                if (mask[row].charAt(column) != '#') continue;
                int r = row, c = flip ? 6 - column : column;
                for (int i = 0; i < rotation; i++) {
                    int next = c;
                    c = 6 - r;
                    r = next;
                }
                result[r][c] = depth;
            }
        }
        int minRow = 7, maxRow = 0, minColumn = 7, maxColumn = 0;
        for (int row = 0; row < 7; row++)
            for (int column = 0; column < 7; column++)
                if (result[row][column] != 0) {
                    minRow = Math.min(minRow, row);
                    maxRow = Math.max(maxRow, row);
                    minColumn = Math.min(minColumn, column);
                    maxColumn = Math.max(maxColumn, column);
                }
        int dr = choose(value >>> 20, 7 - (maxRow - minRow)) - minRow;
        int dc = choose(value >>> 28, 7 - (maxColumn - minColumn)) - minColumn;
        int[][] shifted = new int[7][7];
        for (int row = minRow; row <= maxRow; row++)
            for (int column = minColumn; column <= maxColumn; column++)
                shifted[row + dr][column + dc] = result[row][column];
        return cut(shifted, MAX_REMOVED);
    }

    private static JointShape pencil(long value) {
        int centerRow = 2 + choose(value, 3);
        int centerColumn = 2 + choose(value >>> 8, 3);
        int[][] result = new int[7][7];
        int kind = choose(value >>> 16, 6);
        boolean variant = (value & 0x1000000) != 0;
        double apex = variant ? (kind == 2 ? 4 : 5) : 6;
        double slope = variant ? (kind == 0 ? 1.75 : kind == 1 ? 1.25 : 1) : (kind == 1 ? 1.5 : 2);
        for (int row = 0; row < 7; row++) {
            for (int column = 0; column < 7; column++) {
                if (kind >= 3) {
                    int coordinate = kind == 3 ? row : kind == 4 ? column : (row + column) / 2;
                    if ((value & 0x800000) != 0) coordinate = 6 - coordinate;
                    result[row][column] = new int[] {0, 0, 1, 1, 2, 3, 4}[coordinate];
                    continue;
                }
                int dr = Math.abs(row - centerRow), dc = Math.abs(column - centerColumn);
                double distance =
                        kind == 0 ? Math.max(dr, dc) : kind == 1 ? dr + dc : Math.hypot(dr, dc);
                result[row][column] = Math.clamp((int) Math.round(apex - slope * distance), 0, 6);
            }
        }
        return cut(result, variant && kind < 3 ? 24 : MAX_REMOVED);
    }

    private static String[] blob(Draws draws) {
        boolean[][] cells = grow(draws, 9 + draws.next(10), null);
        String[] mask = new String[7];
        for (int row = 0; row < 7; row++) {
            var line = new StringBuilder();
            for (int column = 0; column < 7; column++) line.append(cells[row][column] ? '#' : '.');
            mask[row] = line.toString();
        }
        return mask;
    }

    /**
     * Round three is a procedural carving: a recessed face cut by random pockets, grooves, steps,
     * dimples, chamfers and channels, or a relief left standing by cutting away its surroundings,
     * optionally bevelled and then grooved. Every cell stays one solid run from the fixed base, so
     * no carved part can float free of the block.
     */
    static Carving carving(long seed) {
        var draws = new Draws(seed);
        for (int attempt = 0; attempt < 1024; attempt++) {
            Style style = Style.values()[draws.next(3)];
            int[][] depth = style == Style.RECESSED ? recessed(draws) : relief(draws, style);
            for (int[] row : depth)
                for (int column = 0; column < 7; column++)
                    row[column] = Math.clamp(row[column], 0, 6);
            if (readable(depth)) {
                JointShape shape = cut(depth, MAX_REMOVED);
                if (shape.removed() >= MIN_REMOVED && asymmetric(shape))
                    return new Carving(shape, style);
            }
        }
        throw new IllegalStateException("No readable carving for seed " + seed);
    }

    private static int[][] filled(int level) {
        int[][] depth = new int[7][7];
        for (int[] row : depth) java.util.Arrays.fill(row, level);
        return depth;
    }

    private static int[][] recessed(Draws draws) {
        int surface = 3 + draws.next(2);
        int[][] depth = filled(surface);
        int[] uses = new int[6];
        int count = 3 + draws.next(2);
        for (int i = 0; i < count; i++) {
            int kind = draws.next(6);
            while (uses[kind] >= 2) kind = draws.next(6);
            uses[kind]++;
            int cut = 1 + draws.next(3);
            switch (kind) {
                case 0 -> lower(depth, pocket(draws), cut, 0);
                case 1 -> cap(depth, groove(draws), surface - cut);
                case 2 -> {
                    int side = draws.next(4), edge = 2 + draws.next(4), drop = 1 + draws.next(2);
                    for (int row = 0; row < 7; row++)
                        for (int column = 0; column < 7; column++)
                            if (switch (side) {
                                case 0 -> row >= edge;
                                case 1 -> row < edge;
                                case 2 -> column >= edge;
                                default -> column < edge;
                            }) depth[row][column] -= drop;
                }
                case 3 -> {
                    int centerRow = 1 + draws.next(5), centerColumn = 1 + draws.next(5);
                    int drop = 2 + draws.next(2);
                    for (int row = 0; row < 7; row++)
                        for (int column = 0; column < 7; column++)
                            depth[row][column] -=
                                    Math.max(
                                            0,
                                            drop
                                                    - Math.max(
                                                            Math.abs(row - centerRow),
                                                            Math.abs(column - centerColumn)));
                }
                case 4 -> {
                    int corner = draws.next(4), size = 2 + draws.next(2);
                    for (int row = 0; row < 7; row++)
                        for (int column = 0; column < 7; column++) {
                            int r = corner < 2 ? row : 6 - row;
                            int c = corner % 2 == 0 ? column : 6 - column;
                            if (r + c < size) depth[row][column] = 0;
                        }
                }
                default -> cap(depth, channel(draws), surface - 2);
            }
        }
        return depth;
    }

    private static int[][] relief(Draws draws, Style style) {
        int top = 5 + draws.next(2), back = draws.next(2);
        boolean[][] raised = grow(draws, 10 + draws.next(13), null);
        int[][] depth = filled(back);
        for (int row = 0; row < 7; row++)
            for (int column = 0; column < 7; column++)
                if (raised[row][column]) depth[row][column] = top - 1;
        if (draws.next(2) == 1) {
            // A bevelled rim: the cut-away edge steps down halfway around the relief.
            int rim = back + (top - 1 - back) / 2;
            for (int row = 0; row < 7; row++)
                for (int column = 0; column < 7; column++)
                    if (!raised[row][column] && touches(raised, row, column))
                        depth[row][column] = rim;
        }
        boolean[][] core = grow(draws, 3 + draws.next(5), raised);
        for (int row = 0; row < 7; row++)
            for (int column = 0; column < 7; column++)
                if (core[row][column]) depth[row][column] = top;
        if (style == Style.MIXED) {
            int count = 1 + draws.next(2);
            for (int i = 0; i < count; i++) {
                int kind = draws.next(3), cut = 1 + draws.next(3);
                boolean[][] mask =
                        kind == 0 ? pocket(draws) : kind == 1 ? groove(draws) : channel(draws);
                for (int row = 0; row < 7; row++)
                    for (int column = 0; column < 7; column++)
                        if (mask[row][column] && raised[row][column])
                            depth[row][column] = Math.max(back + 1, depth[row][column] - cut);
            }
        }
        boolean open = false;
        for (int[] row : depth) for (int value : row) open |= value == 0;
        if (!open) {
            int corner = draws.next(4);
            depth[corner < 2 ? 0 : 6][corner % 2 == 0 ? 0 : 6] = 0;
        }
        return depth;
    }

    private static boolean touches(boolean[][] mask, int row, int column) {
        for (int[] step : STEPS) {
            int r = row + step[0], c = column + step[1];
            if (r >= 0 && r < 7 && c >= 0 && c < 7 && mask[r][c]) return true;
        }
        return false;
    }

    private static void lower(int[][] depth, boolean[][] mask, int cut, int floor) {
        for (int row = 0; row < 7; row++)
            for (int column = 0; column < 7; column++)
                if (mask[row][column])
                    depth[row][column] = Math.max(floor, depth[row][column] - cut);
    }

    private static void cap(int[][] depth, boolean[][] mask, int level) {
        for (int row = 0; row < 7; row++)
            for (int column = 0; column < 7; column++)
                if (mask[row][column]) depth[row][column] = Math.min(depth[row][column], level);
    }

    private static boolean[][] pocket(Draws draws) {
        int width = 2 + draws.next(3), height = 2 + draws.next(3);
        int row0 = draws.next(8 - height), column0 = draws.next(8 - width);
        boolean[][] mask = new boolean[7][7];
        for (int row = row0; row < row0 + height; row++)
            for (int column = column0; column < column0 + width; column++) mask[row][column] = true;
        return mask;
    }

    private static boolean[][] groove(Draws draws) {
        int orientation = draws.next(3), length = 4 + draws.next(4);
        boolean[][] mask = new boolean[7][7];
        if (orientation < 2) {
            int line = draws.next(7), start = draws.next(8 - length);
            for (int i = 0; i < length; i++)
                if (orientation == 0) mask[line][start + i] = true;
                else mask[start + i][line] = true;
        } else {
            int row0 = draws.next(8 - length), column0 = draws.next(8 - length);
            boolean anti = draws.next(2) == 1;
            for (int i = 0; i < length; i++)
                mask[row0 + i][column0 + (anti ? length - 1 - i : i)] = true;
        }
        return mask;
    }

    /** A meandering channel that enters from the north or south edge of the face. */
    private static boolean[][] channel(Draws draws) {
        int row = draws.next(7), column = draws.next(2) == 0 ? 0 : 6;
        int steps = 5 + draws.next(6);
        boolean[][] mask = new boolean[7][7];
        for (int i = 0; i < steps; i++) {
            mask[row][column] = true;
            int[] step = STEPS[draws.next(4)];
            row = Math.clamp(row + step[0], 0, 6);
            column = Math.clamp(column + step[1], 0, 6);
        }
        return mask;
    }

    /** Grows a 4-connected random blob, optionally confined to another mask. */
    private static boolean[][] grow(Draws draws, int size, boolean[][] inside) {
        var cells = new java.util.ArrayList<int[]>();
        if (inside == null) cells.add(new int[] {1 + draws.next(5), 1 + draws.next(5)});
        else {
            var pool = new java.util.ArrayList<int[]>();
            for (int row = 0; row < 7; row++)
                for (int column = 0; column < 7; column++)
                    if (inside[row][column]) pool.add(new int[] {row, column});
            cells.add(pool.get(draws.next(pool.size())));
        }
        boolean[][] mask = new boolean[7][7];
        mask[cells.get(0)[0]][cells.get(0)[1]] = true;
        for (int stalls = 0; cells.size() < size && stalls < 64; ) {
            int[] from = cells.get(draws.next(cells.size()));
            int[] step = STEPS[draws.next(4)];
            int row = from[0] + step[0], column = from[1] + step[1];
            if (row < 0
                    || row > 6
                    || column < 0
                    || column > 6
                    || mask[row][column]
                    || (inside != null && !inside[row][column])) {
                stalls++;
                continue;
            }
            mask[row][column] = true;
            cells.add(new int[] {row, column});
            stalls = 0;
        }
        return mask;
    }

    /** Difficulty and legibility bounds shared by every round-three style. */
    static boolean readable(int[][] depth) {
        int volume = 0, maximum = 0;
        var levels = new java.util.HashSet<Integer>();
        for (int[] row : depth)
            for (int value : row) {
                volume += value;
                maximum = Math.max(maximum, value);
                levels.add(value);
            }
        if (volume < 72
                || volume > 140
                || 6 * 49 - volume < MIN_REMOVED
                || levels.size() < 4
                || !levels.contains(0)
                || maximum < 3) return false;
        return readableRegions(depth);
    }

    // Raise the cutting plane until the whole relief fits the white-block budget. Each
    // column stays attached to its base, and judging uses this exact same finished shape.
    private static JointShape cut(int[][] profile, int budget) {
        for (int plane = 0; ; plane++) {
            int removed = 0;
            for (int[] row : profile) for (int value : row) removed += Math.max(0, value - plane);
            if (removed > budget) continue;
            int[][] remaining = new int[JointShape.SIZE][JointShape.SIZE];
            for (int row = 0; row < remaining.length; row++)
                for (int col = 0; col < remaining[row].length; col++)
                    remaining[row][col] =
                            JointShape.MAX_DEPTH - Math.max(0, profile[row][col] - plane);
            return new JointShape(remaining);
        }
    }

    private static boolean asymmetric(JointShape shape) {
        for (int symmetry = 1; symmetry < 8; symmetry++) {
            boolean different = false;
            for (int row = 0; row < 7 && !different; row++)
                for (int column = 0; column < 7 && !different; column++) {
                    int r = row, c = symmetry >= 4 ? 6 - column : column;
                    for (int turn = 0; turn < symmetry % 4; turn++) {
                        int next = c;
                        c = 6 - r;
                        r = next;
                    }
                    different = shape.depth(row, column) != shape.depth(r, c);
                }
            if (!different) return false;
        }
        return true;
    }

    private static boolean readableRegions(int[][] depth) {
        int regions = 0, singles = 0;
        boolean[][] seen = new boolean[7][7];
        for (int row = 0; row < 7; row++)
            for (int column = 0; column < 7; column++) {
                if (seen[row][column]) continue;
                regions++;
                int size = 0;
                var stack = new java.util.ArrayDeque<int[]>();
                stack.push(new int[] {row, column});
                seen[row][column] = true;
                while (!stack.isEmpty()) {
                    int[] cell = stack.pop();
                    size++;
                    for (int[] step : STEPS) {
                        int r = cell[0] + step[0], c = cell[1] + step[1];
                        if (r >= 0
                                && r < 7
                                && c >= 0
                                && c < 7
                                && !seen[r][c]
                                && depth[r][c] == depth[cell[0]][cell[1]]) {
                            seen[r][c] = true;
                            stack.push(new int[] {r, c});
                        }
                    }
                }
                if (size == 1) singles++;
            }
        return regions <= 14 && singles <= 5;
    }
}
