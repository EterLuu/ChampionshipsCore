package ink.ziip.championshipscore.protocol;

import java.util.UUID;

/** Accepted task-completion fact ordered by the worker's match coordinator. */
public record CompletionObservation(
        UUID matchId,
        long epoch,
        long seq,
        int teamId,
        UUID playerId,
        int cellIndex,
        long observedGameTick
) {
    public CompletionObservation {
        ProtocolSupport.required(matchId, "matchId");
        ProtocolSupport.required(playerId, "playerId");
        if (epoch < 1 || seq < 1) throw new IllegalArgumentException("epoch and seq must be positive");
        if (teamId < 0 || cellIndex < 0 || observedGameTick < 0) {
            throw new IllegalArgumentException("teamId, cellIndex and observedGameTick must be non-negative");
        }
    }
}
