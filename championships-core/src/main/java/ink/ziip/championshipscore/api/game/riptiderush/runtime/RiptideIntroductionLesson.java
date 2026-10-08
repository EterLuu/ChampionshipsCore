package ink.ziip.championshipscore.api.game.riptiderush.runtime;

/** Fixed ninety-second lesson. Movement uses the normal opening and final raft speeds. */
final class RiptideIntroductionLesson {
    static final int DURATION_TICKS = 1800;
    static final int STOPPED_STEP = 70;
    static final int FINISH_STEP = 137;
    static final int COURSE_LENGTH = 140;

    enum Stage {
        WELCOME,
        FOLLOW,
        PASS,
        COLOR,
        DODGE,
        SPEED,
        FINISH
    }

    private RiptideIntroductionLesson() {}

    static Stage stageAt(int tick) {
        if (tick < 200) return Stage.WELCOME;
        if (tick < 520) return Stage.FOLLOW;
        if (tick < 840) return Stage.PASS;
        if (tick < 1240) return Stage.COLOR;
        if (tick < 1460) return Stage.DODGE;
        if (tick < 1720) return Stage.SPEED;
        return Stage.FINISH;
    }

    static int stepAt(int tick) {
        if (tick < 200) return 0;
        if (tick < 840) return Math.min(STOPPED_STEP, (tick - 200) * 22 / 200);
        if (tick < 1460) return STOPPED_STEP;
        return Math.min(FINISH_STEP, STOPPED_STEP + (tick - 1460) * 52 / 200);
    }
}
