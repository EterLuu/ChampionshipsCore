package ink.ziip.championshipscore.api.game.config.model;

/** Immutable outer lifecycle timings for one game variant. */
public record GameLifecycleSettings(int preparationSeconds, int countdownSeconds, int durationSeconds) {
    public GameLifecycleSettings {
        if (preparationSeconds < 0 || countdownSeconds < 0 || durationSeconds < 0)
            throw new IllegalArgumentException("Lifecycle durations must be non-negative");
    }
}
