package ink.ziip.championshipscore.api.game.bingo.execution;

/**
 * Pluggable Bingo execution boundary.  Commands and formal schedules use this same surface so the
 * execution location does not leak into their lifecycle code.
 */
public interface BingoExecutionGateway {
    BingoExecutionMode mode();

    /** Cheap, non-mutating readiness check used before a formal-event countdown is committed. */
    boolean canStart(BingoStartRequest request);

    /** Implementations must durably accept the run before returning {@code true}. */
    boolean start(BingoStartRequest request);

    /** Force-ends every run owned by this execution plane. */
    void forceEnd(String reason);
}
