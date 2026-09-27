package ink.ziip.championshipscore.api.game.buildmart;

/** Golden rotation driven exclusively by the round countdown; zero belongs to final settlement. */
public final class GoldenBlueprintScheduler {
    private final int durationSeconds;
    private final int intervalSeconds;
    private final Runnable onRotate;
    private int window;

    public GoldenBlueprintScheduler(int durationSeconds, int intervalSeconds, Runnable onRotate) {
        this.durationSeconds = durationSeconds;
        this.intervalSeconds = Math.max(1, intervalSeconds);
        this.onRotate = onRotate;
    }

    public void tick(int remainingSeconds) {
        if (remainingSeconds <= 0) return;
        int nextWindow = Math.max(0, durationSeconds - remainingSeconds) / intervalSeconds;
        if (nextWindow <= window) return;
        window = nextWindow;
        onRotate.run();
    }
}
