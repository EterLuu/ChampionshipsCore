package ink.ziip.championshipscore.api.schedule;

/** Operations owned by a formal game's existing schedule and lifecycle. */
public interface FormalSchedule {
    void load();

    void unload();

    /** Returns whether the schedule accepted the start and remains enabled. */
    boolean startGame();

    boolean isEnabled();

    boolean hasNextRound();

    void endSchedule();

    default String startFailureReason() {
        return null;
    }
}
