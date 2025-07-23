package ink.ziip.championshipscore.api.object.game.parkourwarrior;

public enum PKWCheckPointTypeEnum {
    main, sub, fin;

    @Override
    public String toString() {
        return switch (this) {
            case main -> "main";
            case sub -> "sub";
            case fin -> "fin";
        };
    }
}
