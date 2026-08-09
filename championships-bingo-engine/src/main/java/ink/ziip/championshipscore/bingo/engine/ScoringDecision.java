package ink.ziip.championshipscore.bingo.engine;

import ink.ziip.championshipscore.protocol.CompletionObservation;

import java.util.List;
import java.util.Objects;

/** Deterministic result of applying one ordered completion observation. */
public record ScoringDecision(
        CompletionObservation observation,
        boolean accepted,
        String rejectionReason,
        int claimRank,
        int cellPoints,
        int linePointsPerMember,
        int completedLines,
        int teamScore,
        List<PlayerAward> awards
) {
    public ScoringDecision {
        Objects.requireNonNull(observation, "observation");
        rejectionReason = rejectionReason == null ? "" : rejectionReason;
        awards = List.copyOf(awards);
        if (claimRank < -1 || cellPoints < 0 || linePointsPerMember < 0
                || completedLines < 0 || teamScore < 0) {
            throw new IllegalArgumentException("score fields must be non-negative (claimRank may be -1)");
        }
    }
}
