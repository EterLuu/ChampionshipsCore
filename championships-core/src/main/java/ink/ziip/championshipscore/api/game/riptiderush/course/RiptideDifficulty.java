package ink.ziip.championshipscore.api.game.riptiderush.course;

/** Difficulty uses the same 20/40/60/80 percent milestones as raft acceleration. */
public final class RiptideDifficulty {
    private static final java.util.List<java.util.List<Integer>> FLOOR_ROUNDS =
            java.util.List.of(
                    java.util.List.of(120, 100, 100, 80, 80, 80, 80),
                    java.util.List.of(100, 80, 80, 80, 80, 70, 70),
                    java.util.List.of(80, 70, 70, 70, 70, 60, 60),
                    java.util.List.of(60, 60, 60, 60, 60, 50, 50),
                    java.util.List.of(60, 50, 50, 40, 40, 40, 40));

    private RiptideDifficulty() {}

    public static int stage(int step, int totalSteps) {
        return Math.min(4, Math.max(0, (int) ((long) step * 5 / totalSteps)));
    }

    static boolean allowsPass(int difficulty, int step, int totalSteps) {
        int stage = stage(step, totalSteps);
        return stage == 0
                ? difficulty == 1
                : stage == 1 ? difficulty == 2 : difficulty >= 2 && difficulty <= 3;
    }

    static boolean allowsSide(int difficulty, int step, int totalSteps) {
        int stage = stage(step, totalSteps);
        return stage == 2
                ? difficulty >= 1 && difficulty <= 2
                : stage >= 3 && difficulty >= 2 && difficulty <= 3;
    }

    public static int floorTicks(int step, int totalSteps) {
        return floorRoundTicks(step, totalSteps).stream().mapToInt(Integer::intValue).sum();
    }

    public static java.util.List<Integer> floorRoundTicks(int step, int totalSteps) {
        return FLOOR_ROUNDS.get(stage(step, totalSteps));
    }

    public static int floorMaterials(int step, int totalSteps) {
        return 5 + Math.min(3, stage(step, totalSteps));
    }

    static boolean allowsMath(String variant, int step, int totalSteps) {
        int stage = stage(step, totalSteps);
        return switch (variant) {
            case "SUBTRACT" -> stage >= 1;
            case "DOUBLE" -> stage(step - 6, totalSteps) >= 1;
            case "MULTIPLY" -> stage >= 3;
            default -> true;
        };
    }
}
