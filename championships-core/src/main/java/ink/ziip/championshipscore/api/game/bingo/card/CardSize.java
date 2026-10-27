package ink.ziip.championshipscore.api.game.bingo.card;

/** Logical square card dimensions. Inventory placement belongs to the card-menu configuration. */
public enum CardSize {
    X3(3),
    X4(4),
    X5(5);
    public final int size;
    public final int fullCardSize;

    CardSize(int size) {
        this.size = size;
        this.fullCardSize = size * size;
    }

    public static CardSize fromWidth(int width) {
        for (CardSize size : values()) if (size.size == width) return size;
        return X5;
    }
}
