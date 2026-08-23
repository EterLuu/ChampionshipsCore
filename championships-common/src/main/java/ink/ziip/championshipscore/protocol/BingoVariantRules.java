package ink.ziip.championshipscore.protocol;

import java.util.List;

/** Frozen result of DAILY's votes and remix roll. */
public record BingoVariantRules(BingoMode mode, BingoDifficulty difficulty, int winLines,
                                BingoRemix remix, List<String> genesisItems) {
    public static final BingoVariantRules FIXED_POINTS =
            new BingoVariantRules(BingoMode.POINTS, BingoDifficulty.NORMAL, 1, BingoRemix.NONE, List.of());

    public BingoVariantRules(BingoMode mode, BingoDifficulty difficulty, int winLines,
                             BingoRemix remix) {
        this(mode, difficulty, winLines, remix, List.of());
    }

    public BingoVariantRules {
        ProtocolSupport.required(mode, "mode");
        ProtocolSupport.required(difficulty, "difficulty");
        ProtocolSupport.required(remix, "remix");
        genesisItems = ProtocolSupport.immutableList(genesisItems, "genesisItems");
        if (winLines < 1 || winLines > 5) throw new IllegalArgumentException("winLines must be 1..5");
        if (remix != BingoRemix.GENESIS && !genesisItems.isEmpty())
            throw new IllegalArgumentException("genesisItems require the GENESIS remix");
    }

    public int durationSeconds(int configuredFallback) {
        if (remix == BingoRemix.COOP) return 60 * 60;
        int base = difficulty == null ? configuredFallback : difficulty.durationSeconds();
        return base + (remix.modifiesCard() ? 10 * 60 : 0);
    }
}
