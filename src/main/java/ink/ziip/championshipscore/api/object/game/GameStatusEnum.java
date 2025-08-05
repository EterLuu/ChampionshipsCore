package ink.ziip.championshipscore.api.object.game;

public enum GameStatusEnum {
    GAMING,
    VOTING,
    HALFING,
    SETTING;

    @Override
    public String toString() {
        switch (this) {
            case GAMING:
                return "gaming";
            case VOTING:
                return "voting";
            case HALFING:
                return "halfing";
            case SETTING:
                return "setting";
            default:
                return "unknown";
        }
    }
}