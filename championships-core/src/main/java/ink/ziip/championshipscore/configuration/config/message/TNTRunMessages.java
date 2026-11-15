package ink.ziip.championshipscore.configuration.config.message;

import ink.ziip.championshipscore.configuration.ConfigOption;

/** Message fields owned by the TNTRun domain; persisted by MessageConfig. */
public final class TNTRunMessages {
    private TNTRunMessages() {}

    @ConfigOption(path = "tntrun.start-preparation")
    public static String TNT_RUN_START_PREPARATION;

    @ConfigOption(path = "tntrun.start-preparation-title")
    public static String TNT_RUN_START_PREPARATION_TITLE;

    @ConfigOption(path = "tntrun.start-preparation-subtitle")
    public static String TNT_RUN_START_PREPARATION_SUBTITLE;

    @ConfigOption(path = "tntrun.game-start-title")
    public static String TNT_RUN_GAME_START_TITLE;

    @ConfigOption(path = "tntrun.game-start-subtitle")
    public static String TNT_RUN_GAME_START_SUBTITLE;

    @ConfigOption(path = "tntrun.boss-bar-tnt-rain")
    public static String TNT_RUN_BOSS_BAR_TNT_RAIN;

    @ConfigOption(path = "tntrun.boss-bar")
    public static String TNT_RUN_BOSS_BAR;

    @ConfigOption(path = "tntrun.game-end-title")
    public static String TNT_RUN_GAME_END_TITLE;

    @ConfigOption(path = "tntrun.game-end-subtitle")
    public static String TNT_RUN_GAME_END_SUBTITLE;

    @ConfigOption(path = "tntrun.fall-into-void")
    public static String TNT_RUN_FALL_INTO_VOID;

    @ConfigOption(path = "tntrun.tnt-rain")
    public static String TNT_RUN_TNT_RAIN;

    @ConfigOption(path = "tntrun.tnt-rain-count-down")
    public static String TNT_RUN_TNT_RAIN_COUNT_DOWN;
}
