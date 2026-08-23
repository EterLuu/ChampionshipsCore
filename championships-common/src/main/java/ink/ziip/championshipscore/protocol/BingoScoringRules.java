package ink.ziip.championshipscore.protocol;

import java.util.List;

public record BingoScoringRules(
        int cardWidth,
        List<Integer> claimPoints,
        int lineBonus,
        int lineBonusMajorCount,
        int lineBonusMinor,
        BingoVariantRules variant
) {
    public BingoScoringRules(int cardWidth, List<Integer> claimPoints, int lineBonus,
                             int lineBonusMajorCount, int lineBonusMinor) {
        this(cardWidth, claimPoints, lineBonus, lineBonusMajorCount, lineBonusMinor,
                BingoVariantRules.FIXED_POINTS);
    }

    public BingoScoringRules {
        if (cardWidth < 1) throw new IllegalArgumentException("cardWidth must be positive");
        claimPoints = ProtocolSupport.immutableList(claimPoints, "claimPoints");
        if (claimPoints.isEmpty()) throw new IllegalArgumentException("claimPoints must not be empty");
        if (claimPoints.stream().anyMatch(value -> value < 0)) {
            throw new IllegalArgumentException("claimPoints must not contain negative values");
        }
        if (lineBonus < 0 || lineBonusMajorCount < 0 || lineBonusMinor < 0) {
            throw new IllegalArgumentException("line bonus values must be non-negative");
        }
        ProtocolSupport.required(variant, "variant");
    }

    public int pointsForClaimRank(int zeroBasedRank) {
        if (zeroBasedRank < 0) throw new IllegalArgumentException("rank must be non-negative");
        return claimPoints.get(Math.min(zeroBasedRank, claimPoints.size() - 1));
    }
}
