package ink.ziip.championshipscore.api.object.game.parkourwarrior;

public enum PKWFinalCheckPointTypeEnum {
    none, easy, normal, hard;

    @Override
    public String toString() {
        return switch (this) {
            case none -> "none";
            case easy -> "easy";
            case normal -> "normal";
            case hard -> "hard";
        };
    }
}
