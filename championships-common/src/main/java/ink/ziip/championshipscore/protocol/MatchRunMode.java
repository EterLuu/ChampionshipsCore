package ink.ziip.championshipscore.protocol;

public enum MatchRunMode {
    GAME,
    EVENT,
    DAILY,
    TEST,
    TEST_EVENT;

    public boolean test() {
        return this == TEST || this == TEST_EVENT;
    }

    public boolean eventLike() {
        return this == EVENT || this == TEST_EVENT;
    }

    public boolean formalEvent() {
        return this == EVENT;
    }
}
