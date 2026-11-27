package ink.ziip.championshipscore.api.game.iamnotarobot.model;

/** Collision distance and remaining gaps for a proposed excavation. */
public final class JointFit {
    public enum Outcome {
        BLOCKED,
        GAP,
        PASS
    }

    public record Result(Outcome outcome, int distance, int gaps) {}

    private JointFit() {}

    /** The final dimension is depth 1 through 6, with true indicating a solid block. */
    public static Result evaluate(JointShape shape, boolean[][][] solid, int fullTravel) {
        if (solid.length != JointShape.SIZE)
            throw new IllegalArgumentException("Expected seven rows");
        int distance = fullTravel;
        int gaps = 0;
        for (int row = 0; row < JointShape.SIZE; row++) {
            if (solid[row].length != JointShape.SIZE)
                throw new IllegalArgumentException("Expected seven columns");
            for (int column = 0; column < JointShape.SIZE; column++) {
                if (solid[row][column].length != JointShape.MAX_DEPTH)
                    throw new IllegalArgumentException("Expected six depths");
                int depth = shape.depth(row, column);
                int firstSolid = 7;
                for (int d = 1; d <= JointShape.MAX_DEPTH; d++) {
                    if (solid[row][column][d - 1]) {
                        firstSolid = Math.min(firstSolid, d);
                    } else if (d > depth) {
                        gaps++;
                    }
                }
                distance = Math.min(distance, fullTravel - 1 + firstSolid - depth);
            }
        }
        if (distance < fullTravel) return new Result(Outcome.BLOCKED, distance, 0);
        return new Result(gaps == 0 ? Outcome.PASS : Outcome.GAP, fullTravel, gaps);
    }
}
