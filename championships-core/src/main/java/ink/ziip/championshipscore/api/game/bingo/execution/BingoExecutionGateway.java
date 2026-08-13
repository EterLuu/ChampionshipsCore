package ink.ziip.championshipscore.api.game.bingo.execution;

import java.util.concurrent.CompletionStage;

/**
 * Pluggable Bingo execution boundary.  Commands and formal schedules use this same surface so the
 * execution location does not leak into their lifecycle code.
 */
public interface BingoExecutionGateway {
    BingoExecutionMode mode();

    /** Cheap, non-mutating readiness check used before a formal-event countdown is committed. */
    boolean canStart(BingoStartRequest request);

    /** Completes true only after the implementation has durably accepted the run. */
    CompletionStage<Boolean> start(BingoStartRequest request);

    /** Force-ends every run owned by this execution plane. */
    CompletionStage<Void> forceEnd(String reason);
}
