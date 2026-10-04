package ink.ziip.championshipscore.api.game.sulfursoccer.model;

public enum SulfurSoccerSide {
    RIGHT,
    LEFT;

    public SulfurSoccerSide opposite() {
        return this == RIGHT ? LEFT : RIGHT;
    }
}
