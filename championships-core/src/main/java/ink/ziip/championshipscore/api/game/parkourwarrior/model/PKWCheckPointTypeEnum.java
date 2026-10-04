package ink.ziip.championshipscore.api.game.parkourwarrior.model;

public enum PKWCheckPointTypeEnum {
    main,
    sub,
    fin;

    @Override
    public String toString() {
        return switch (this) {
            case main -> "main";
            case sub -> "sub";
            case fin -> "fin";
        };
    }
}
