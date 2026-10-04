package ink.ziip.championshipscore.api.game.dodgebolt.model;

public enum DodgeboltSide {
    RIGHT,
    LEFT;

    public DodgeboltSide opposite() {
        return this == RIGHT ? LEFT : RIGHT;
    }
}
