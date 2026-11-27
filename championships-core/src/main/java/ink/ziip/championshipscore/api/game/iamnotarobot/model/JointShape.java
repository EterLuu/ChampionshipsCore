package ink.ziip.championshipscore.api.game.iamnotarobot.model;

import java.util.Arrays;

/** Immutable depths of the 49 rows of the white block. */
public final class JointShape {
    public static final int SIZE = 7;
    public static final int MAX_DEPTH = 6;
    static final int MANUAL_GRAY_LIMIT = 48;
    private final int[][] depths;

    public JointShape(int[][] depths) {
        if (depths.length != SIZE) throw new IllegalArgumentException("Expected seven rows");
        this.depths = new int[SIZE][SIZE];
        for (int row = 0; row < SIZE; row++) {
            if (depths[row].length != SIZE)
                throw new IllegalArgumentException("Expected seven columns");
            for (int column = 0; column < SIZE; column++) {
                int depth = depths[row][column];
                if (depth < 0 || depth > MAX_DEPTH)
                    throw new IllegalArgumentException("Invalid depth");
                this.depths[row][column] = depth;
            }
        }
    }

    public int depth(int row, int column) {
        return depths[row][column];
    }

    int removed() {
        int count = 0;
        for (int[] row : depths) for (int depth : row) count += MAX_DEPTH - depth;
        return count;
    }

    /** Starting excavation depths from the exposed gray face, before players take over. */
    int[][] initialGrayCut(int manualBlocks) {
        int[][] cut = new int[SIZE][SIZE];
        int required = SIZE * SIZE * MAX_DEPTH - removed();
        if (required <= MANUAL_GRAY_LIMIT) return cut;
        int excess = required - manualBlocks;
        // Clear the deepest (flat background) columns first, one exposed layer at a time.
        // Leave the varying depths around white carving details for the players to match.
        for (int depth = MAX_DEPTH; depth > 0 && excess > 0; depth--) {
            for (int layer = 0; layer < depth && excess > 0; layer++) {
                for (int row = 0; row < SIZE; row++) {
                    for (int col = 0; col < SIZE && excess > 0; col++) {
                        if (depths[row][col] == depth) {
                            cut[row][col]++;
                            excess--;
                        }
                    }
                }
            }
        }
        return cut;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof JointShape shape && Arrays.deepEquals(depths, shape.depths);
    }

    @Override
    public int hashCode() {
        return Arrays.deepHashCode(depths);
    }
}
