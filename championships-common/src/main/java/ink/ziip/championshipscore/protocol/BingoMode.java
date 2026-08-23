package ink.ziip.championshipscore.protocol;

/** Playable rule set selected by DAILY players before a Bingo match. */
public enum BingoMode {
    DOMINATION,
    SPEEDRUN,
    QUANTITY,
    POINTS;

    public boolean locksCells() { return this == DOMINATION; }
    public boolean linesWin() { return this == DOMINATION || this == SPEEDRUN; }
    public boolean fullCardWins() { return this == QUANTITY; }
    public boolean usesPoints() { return this == POINTS; }
}
