package ink.ziip.championshipscore.api.game.sulfursoccer;

public enum SulfurSoccerSide {
    RIGHT, LEFT;

    public SulfurSoccerSide opposite() {
        return this == RIGHT ? LEFT : RIGHT;
    }
}
