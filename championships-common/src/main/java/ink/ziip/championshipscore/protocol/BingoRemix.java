package ink.ziip.championshipscore.protocol;

import java.util.concurrent.ThreadLocalRandom;

/** MineBingo-compatible one-round twists. NONE is the explicit no-remix wire value. */
public enum BingoRemix {
    NONE,
    NETHER,
    SCALE,
    DIFFERENTIAL,
    UPGRADE,
    BLIND,
    FEAST,
    COOP,
    GENESIS,
    COLORFUL,
    CHAIN,
    VARIATION,
    FINALE,
    ETERNAL_NIGHT,
    POLAR_DAY,
    PARALLAX,
    SPEEDRUN;

    public static BingoRemix random() {
        BingoRemix[] values = values();
        return values[ThreadLocalRandom.current().nextInt(1, values.length)];
    }

    public boolean modifiesCard() {
        return switch (this) {
            case NETHER, SCALE, DIFFERENTIAL, BLIND, FEAST, GENESIS, COLORFUL, CHAIN,
                    VARIATION, FINALE, PARALLAX, SPEEDRUN -> true;
            case NONE, UPGRADE, COOP, ETERNAL_NIGHT, POLAR_DAY -> false;
        };
    }
}
