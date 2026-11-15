package ink.ziip.championshipscore.configuration.config.message;

import ink.ziip.championshipscore.configuration.ConfigOption;

/** Message fields owned by the Dodgebolt domain; persisted by MessageConfig. */
public final class DodgeboltMessages {
    private DodgeboltMessages() {}

    @ConfigOption(path = "dodgebolt.start-preparation", nullable = true)
    public static String DODGEBOLT_START_PREPARATION;

    @ConfigOption(path = "dodgebolt.start-preparation-title", nullable = true)
    public static String DODGEBOLT_START_PREPARATION_TITLE;

    @ConfigOption(path = "dodgebolt.start-preparation-subtitle", nullable = true)
    public static String DODGEBOLT_START_PREPARATION_SUBTITLE;

    @ConfigOption(path = "dodgebolt.game-start-soon-title", nullable = true)
    public static String DODGEBOLT_GAME_START_SOON_TITLE;

    @ConfigOption(path = "dodgebolt.game-start-title", nullable = true)
    public static String DODGEBOLT_GAME_START_TITLE;

    @ConfigOption(path = "dodgebolt.game-start-subtitle", nullable = true)
    public static String DODGEBOLT_GAME_START_SUBTITLE;

    @ConfigOption(path = "dodgebolt.game-end-title", nullable = true)
    public static String DODGEBOLT_GAME_END_TITLE;

    @ConfigOption(path = "dodgebolt.game-end-subtitle", nullable = true)
    public static String DODGEBOLT_GAME_END_SUBTITLE;

    @ConfigOption(path = "dodgebolt.hit", nullable = true)
    public static String DODGEBOLT_HIT;

    @ConfigOption(path = "dodgebolt.eliminated", nullable = true)
    public static String DODGEBOLT_ELIMINATED;

    @ConfigOption(path = "dodgebolt.shrink", nullable = true)
    public static String DODGEBOLT_SHRINK;

    @ConfigOption(path = "dodgebolt.shrink-warning", nullable = true)
    public static String DODGEBOLT_SHRINK_WARNING;

    @ConfigOption(path = "dodgebolt.round-win", nullable = true)
    public static String DODGEBOLT_ROUND_WIN;

    @ConfigOption(path = "dodgebolt.round-win-title", nullable = true)
    public static String DODGEBOLT_ROUND_WIN_TITLE;

    @ConfigOption(path = "dodgebolt.round-win-subtitle", nullable = true)
    public static String DODGEBOLT_ROUND_WIN_SUBTITLE;

    @ConfigOption(path = "dodgebolt.next-round", nullable = true)
    public static String DODGEBOLT_NEXT_ROUND;

    @ConfigOption(path = "dodgebolt.score-bar", nullable = true)
    public static String DODGEBOLT_SCORE_BAR;

    @ConfigOption(path = "dodgebolt.state-live", nullable = true)
    public static String DODGEBOLT_STATE_LIVE;

    @ConfigOption(path = "dodgebolt.state-paused", nullable = true)
    public static String DODGEBOLT_STATE_PAUSED;

    @ConfigOption(path = "dodgebolt.paused", nullable = true)
    public static String DODGEBOLT_PAUSED;

    @ConfigOption(path = "dodgebolt.paused-title", nullable = true)
    public static String DODGEBOLT_PAUSED_TITLE;

    @ConfigOption(path = "dodgebolt.paused-subtitle", nullable = true)
    public static String DODGEBOLT_PAUSED_SUBTITLE;

    @ConfigOption(path = "dodgebolt.resumed", nullable = true)
    public static String DODGEBOLT_RESUMED;

    @ConfigOption(path = "dodgebolt.resumed-title", nullable = true)
    public static String DODGEBOLT_RESUMED_TITLE;

    @ConfigOption(path = "dodgebolt.resumed-subtitle", nullable = true)
    public static String DODGEBOLT_RESUMED_SUBTITLE;

    @ConfigOption(path = "dodgebolt.round-restarted", nullable = true)
    public static String DODGEBOLT_ROUND_RESTARTED;

    @ConfigOption(path = "dodgebolt.champion", nullable = true)
    public static String DODGEBOLT_CHAMPION;

    @ConfigOption(path = "dodgebolt.champion-title", nullable = true)
    public static String DODGEBOLT_CHAMPION_TITLE;

    @ConfigOption(path = "dodgebolt.champion-subtitle", nullable = true)
    public static String DODGEBOLT_CHAMPION_SUBTITLE;

    @ConfigOption(path = "dodgebolt.stopped", nullable = true)
    public static String DODGEBOLT_STOPPED;

    @ConfigOption(path = "dodgebolt.cant-shoot", nullable = true)
    public static String DODGEBOLT_CANT_SHOOT;

    @ConfigOption(path = "dodgebolt.cant-cross", nullable = true)
    public static String DODGEBOLT_CANT_CROSS;
}
