package ink.ziip.championshipscore.configuration.config.message;

import ink.ziip.championshipscore.configuration.ConfigOption;

/** Message fields owned by the SulfurSoccer domain; persisted by MessageConfig. */
public final class SulfurSoccerMessages {
    private SulfurSoccerMessages() {}

    @ConfigOption(path = "sulfursoccer.score")
    public static String SULFUR_SOCCER_SCORE;

    @ConfigOption(path = "sulfursoccer.goal")
    public static String SULFUR_SOCCER_GOAL;

    @ConfigOption(path = "sulfursoccer.goal-title")
    public static String SULFUR_SOCCER_GOAL_TITLE;

    @ConfigOption(path = "sulfursoccer.goal-subtitle")
    public static String SULFUR_SOCCER_GOAL_SUBTITLE;

    @ConfigOption(path = "sulfursoccer.upcoming-title", nullable = true)
    public static String SULFUR_SOCCER_UPCOMING_TITLE;

    @ConfigOption(path = "sulfursoccer.opening-title", nullable = true)
    public static String SULFUR_SOCCER_OPENING_TITLE;

    @ConfigOption(path = "sulfursoccer.restart-title", nullable = true)
    public static String SULFUR_SOCCER_RESTART_TITLE;

    @ConfigOption(path = "sulfursoccer.warmup-start", nullable = true)
    public static String SULFUR_SOCCER_WARMUP_START;

    @ConfigOption(path = "sulfursoccer.warmup-time", nullable = true)
    public static String SULFUR_SOCCER_WARMUP_TIME;

    @ConfigOption(path = "sulfursoccer.time-up", nullable = true)
    public static String SULFUR_SOCCER_TIME_UP;

    @ConfigOption(path = "sulfursoccer.shootout-start", nullable = true)
    public static String SULFUR_SOCCER_SHOOTOUT_START;

    @ConfigOption(path = "sulfursoccer.penalty-turn", nullable = true)
    public static String SULFUR_SOCCER_PENALTY_TURN;

    @ConfigOption(path = "sulfursoccer.penalty-goal", nullable = true)
    public static String SULFUR_SOCCER_PENALTY_GOAL;

    @ConfigOption(path = "sulfursoccer.penalty-miss", nullable = true)
    public static String SULFUR_SOCCER_PENALTY_MISS;

    @ConfigOption(path = "sulfursoccer.penalty-score", nullable = true)
    public static String SULFUR_SOCCER_PENALTY_SCORE;

    @ConfigOption(path = "sulfursoccer.state-end")
    public static String SULFUR_SOCCER_STATE_END;

    @ConfigOption(path = "sulfursoccer.state-paused")
    public static String SULFUR_SOCCER_STATE_PAUSED;

    @ConfigOption(path = "sulfursoccer.state-warmup")
    public static String SULFUR_SOCCER_STATE_WARMUP;

    @ConfigOption(path = "sulfursoccer.state-shootout")
    public static String SULFUR_SOCCER_STATE_SHOOTOUT;

    @ConfigOption(path = "sulfursoccer.state-regulation")
    public static String SULFUR_SOCCER_STATE_REGULATION;

    @ConfigOption(path = "sulfursoccer.pearl-rejected", nullable = true)
    public static String SULFUR_SOCCER_PEARL_REJECTED;

    @ConfigOption(path = "sulfursoccer.ball-reset")
    public static String SULFUR_SOCCER_BALL_RESET;

    @ConfigOption(path = "sulfursoccer.paused")
    public static String SULFUR_SOCCER_PAUSED;

    @ConfigOption(path = "sulfursoccer.resumed")
    public static String SULFUR_SOCCER_RESUMED;

    @ConfigOption(path = "sulfursoccer.champion")
    public static String SULFUR_SOCCER_CHAMPION;

    @ConfigOption(path = "sulfursoccer.champion-subtitle")
    public static String SULFUR_SOCCER_CHAMPION_SUBTITLE;

    @ConfigOption(path = "sulfursoccer.stopped")
    public static String SULFUR_SOCCER_STOPPED;

    @ConfigOption(path = "sulfursoccer.end-title")
    public static String SULFUR_SOCCER_END_TITLE;

    @ConfigOption(path = "sulfursoccer.scheduled")
    public static String SULFUR_SOCCER_SCHEDULED;

    @ConfigOption(path = "sulfursoccer.finalists")
    public static String SULFUR_SOCCER_FINALISTS;

    @ConfigOption(path = "sulfursoccer.start-failed")
    public static String SULFUR_SOCCER_START_FAILED;
}
