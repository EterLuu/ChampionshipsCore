package ink.ziip.championshipscore.api.game.bingo.card;

/**
 * A square bingo card size, and the mapping from a task index to an inventory slot so the grid sits
 * centered in a chest GUI.
 */
public enum CardSize {
    X3(3, 3),
    X4(4, 2),
    X5(5, 2);

    public final int size;
    public final int leftSpacing;
    public final int rightSpacing;
    public final int fullCardSize;

    CardSize(int size, int leftSpacing) {
        this.size = size;
        this.leftSpacing = leftSpacing;
        this.rightSpacing = 9 - size - leftSpacing;
        this.fullCardSize = (int) Math.pow(size, 2);
    }

    public int getCardInventorySlot(int itemIndex) {
        int row;
        if (itemIndex == fullCardSize - 1) {
            row = size - 1;
        } else {
            row = (int) Math.floor(itemIndex / (double) size);
        }
        return itemIndex + leftSpacing + row * (leftSpacing + rightSpacing);
    }

    public static CardSize fromWidth(int width) {
        for (CardSize size : CardSize.values()) {
            if (size.size == width) {
                return size;
            }
        }
        return CardSize.X5;
    }
}
