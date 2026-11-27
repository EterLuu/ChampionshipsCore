package ink.ziip.championshipscore.api.game.craftingfeast.model;

/**
 * A restaurant's word of mouth. Quick service raises it, lost customers lower it, and the star
 * rating scales every later tip: 1★ pays 50%, 3★ (the start) 100% and 5★ 150% of the tip.
 */
public final class FeastReputation {
    public static final int START = 50;
    public static final int MAXIMUM = 100;
    static final int SERVED = 3;
    static final int QUICK_BONUS = 3;
    static final int LOST = 12;

    private int value = START;

    public int value() {
        return value;
    }

    public void served(boolean quick) {
        value = Math.min(MAXIMUM, value + SERVED + (quick ? QUICK_BONUS : 0));
    }

    public void lost() {
        value = Math.max(0, value - LOST);
    }

    public int stars() {
        return Math.clamp(1 + value / 20, 1, 5);
    }

    public int tipPercent() {
        return 25 + stars() * 25;
    }
}
