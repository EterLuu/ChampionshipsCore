package ink.ziip.championshipscore.api.game.bingo.task.pool;

/**
 * Difficulty tier of a pooled task. The {@code weight} drives weighted-random card generation:
 * higher weight means the task is more likely to be picked, so easy tasks dominate a card while
 * hard ones are sprinkled in. Ratio is EASY:MEDIUM:ADVANCED:HARD:VERY_HARD = 5:4:3:2:1.
 */
public enum Difficulty {
    EASY(5),
    MEDIUM(4),
    ADVANCED(3),
    HARD(2),
    VERY_HARD(1);

    /** Relative selection weight used by the generator's weighted sampling. */
    public final int weight;

    Difficulty(int weight) {
        this.weight = weight;
    }
}
