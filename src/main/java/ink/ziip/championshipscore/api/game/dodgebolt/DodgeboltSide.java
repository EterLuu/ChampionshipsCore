package ink.ziip.championshipscore.api.game.dodgebolt;

public enum DodgeboltSide {
    RIGHT,
    LEFT;

    public DodgeboltSide opposite() {
        return this == RIGHT ? LEFT : RIGHT;
    }
}
