package ink.ziip.championshipscore.bingo.engine;

import java.util.Map;
import java.util.List;
import java.util.Comparator;

public record BingoResult(
        long finalSeq,
        boolean boardFullyClaimed,
        Map<Integer, Integer> teamScores,
        Map<Integer, Integer> completedCells,
        Map<Integer, Long> lastCompletionTicks,
        String resultHash
) {
    public BingoResult {
        teamScores = Map.copyOf(teamScores);
        completedCells = Map.copyOf(completedCells);
        lastCompletionTicks = Map.copyOf(lastCompletionTicks);
        if (resultHash == null || resultHash.isBlank()) {
            throw new IllegalArgumentException("resultHash must not be blank");
        }
    }

    /** Score-descending order with the same earliest-completion tie break used by Local Bingo. */
    public List<Integer> rankedTeamIds() {
        return teamScores.keySet().stream()
                .sorted(Comparator
                        .comparingInt((Integer teamId) -> -teamScores.getOrDefault(teamId, 0))
                        .thenComparingLong(teamId -> lastCompletionTicks.getOrDefault(teamId, Long.MAX_VALUE))
                        .thenComparingInt(Integer::intValue))
                .toList();
    }

    /** Winner under Local Bingo semantics; no team wins if every score is zero. */
    public Integer winnerTeamId() {
        return rankedTeamIds().stream()
                .filter(teamId -> teamScores.getOrDefault(teamId, 0) > 0)
                .findFirst().orElse(null);
    }
}
