package ink.ziip.championshipscore.api.game.iamnotarobot.geometry;

import ink.ziip.championshipscore.api.game.arena.ArenaGrid;

import org.bukkit.util.Vector;

public enum SliderHall {
    A(-19, -8, true, -1),
    B(13, 11, true, 1),
    C(-19, -8, false, -1),
    D(13, 11, false, 1);
    public static final int MIN_X = -35,
            MIN_Y = -6,
            MIN_Z = 19,
            END_Z = 38,
            WIDTH = 89,
            HEIGHT = 29,
            LENGTH = 20;
    public static final int LAST = 22;
    public static final int MAP_COLUMNS = 16, MAP_ROWS = 8, MAP_TOP = 13, BAR_Y = 5;
    public static final double CORE_SIZE = MAP_COLUMNS * .14,
            TAB_SIZE = CORE_SIZE * .32,
            MARGIN = MAP_COLUMNS * .025,
            OUTLINE = MAP_COLUMNS * .005,
            PIECE_OUTLINE = .09;

    /** Frame backing blocks, ordered from the viewer's left on either wall. */
    public int mapX(int column) {
        return mapLeft + (step == 1 ? column : MAP_COLUMNS - 1 - column);
    }

    private static final SliderHall[] SCREENS = values();

    /** Horizontal distance to the nearest frame centre in any of the four tile grids. */
    public static boolean inFrameRange(double x, double z, int range) {
        for (var screen : SCREENS) {
            double column = Math.clamp(Math.rint(x - screen.mapLeft - .5), 0, MAP_COLUMNS - 1);
            double dx = x - (screen.mapLeft + column + .5);
            double dz = z - (screen.wall + (screen.step == 1 ? 1 : 0) + screen.step / 32.0);
            if (dx * dx + dz * dz <= range * range) return true;
        }
        return false;
    }

    public static int mapY(int row) {
        return MAP_TOP - row;
    }

    /** Left edge in slide coordinates, measured from the starting end of the image. */
    public static double coreLeft(int column) {
        return coreLeft((double) column);
    }

    /** Fractional columns are used during the failed-attempt rebound. */
    public static double coreLeft(double column) {
        return MARGIN + column * (MAP_COLUMNS - 2 * MARGIN - CORE_SIZE) / LAST;
    }

    /** Viewer-local left edge of a sliding rectangle, measured from the image's left edge. */
    public double viewerLeft(double slideX, double width) {
        return run == step ? slideX : MAP_COLUMNS - slideX - width;
    }

    public static double coreTop(int row) {
        // Leave room for the tab, the stroke and a small offset shadow at the extreme rows.
        double inset = TAB_SIZE + MAP_COLUMNS * .01;
        return inset + (row - 1) * (MAP_ROWS - 2 * inset - CORE_SIZE) / 6;
    }

    public static final String STRUCTURE = "iamnotarobot/verification-slider.nbt";
    public static final ArenaGrid GRID =
            i -> VerificationCourse.GRID.origin(i).add(new Vector(MIN_X, MIN_Y, MIN_Z));
    private final int left, mapLeft;

    /**
     * step is the wall normal in Z and the viewer's right in X. run is forward on the rail in X.
     */
    public final int step, run, wall, trench, window;

    SliderHall(int left, int mapLeft, boolean north, int run) {
        this.run = run;
        this.left = left;
        this.mapLeft = mapLeft;
        step = north ? 1 : -1;
        wall = north ? 19 : 38;
        trench = wall + step;
        window = trench + step;
    }

    public int pixelX(int col) {
        return left + (run == 1 ? col : 24 - col);
    }

    public int holdingColumn(int x, int y, int z) {
        int c = (x - pixelX(1)) * run;
        return y == 1 && z == trench && c >= 0 && c <= LAST ? c : -1;
    }

    public static SliderHall station(int x, int y, int z) {
        for (var s : values())
            if (y == 1 && z == s.trench && (x == s.pixelX(0) || s.holdingColumn(x, y, z) >= 0))
                return s;
        return null;
    }

    public Vector start() {
        return new Vector(pixelX(1) + .5, 1, trench + .5);
    }

    public Vector outside() {
        return new Vector(pixelX(0) + .5, 1, window + step + .5);
    }

    public static boolean contains(int x, int y, int z, boolean cleared) {
        if (y < 1 || y >= 22 || z < MIN_Z || z > END_Z) return false;
        if (z == MIN_Z || z == END_Z) return x >= 7 && x <= 11 && y <= 4 && (z == MIN_Z || cleared);
        return x >= -26 && x <= 44;
    }
}
