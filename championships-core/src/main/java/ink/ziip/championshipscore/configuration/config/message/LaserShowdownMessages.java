package ink.ziip.championshipscore.configuration.config.message;

import ink.ziip.championshipscore.configuration.ConfigOption;

/** Message fields owned by the LaserShowdown domain; persisted by MessageConfig. */
public final class LaserShowdownMessages {
    private LaserShowdownMessages() {}

    @ConfigOption(path = "lasershowdown.start-preparation")
    public static String LASER_SHOWDOWN_START_PREPARATION;

    @ConfigOption(path = "lasershowdown.start-preparation-title")
    public static String LASER_SHOWDOWN_START_PREPARATION_TITLE;

    @ConfigOption(path = "lasershowdown.start-preparation-subtitle")
    public static String LASER_SHOWDOWN_START_PREPARATION_SUBTITLE;

    @ConfigOption(path = "lasershowdown.game-start-soon-title")
    public static String LASER_SHOWDOWN_GAME_START_SOON_TITLE;

    @ConfigOption(path = "lasershowdown.game-start-title")
    public static String LASER_SHOWDOWN_GAME_START_TITLE;

    @ConfigOption(path = "lasershowdown.game-start-subtitle")
    public static String LASER_SHOWDOWN_GAME_START_SUBTITLE;

    @ConfigOption(path = "lasershowdown.game-end-title")
    public static String LASER_SHOWDOWN_GAME_END_TITLE;

    @ConfigOption(path = "lasershowdown.game-end-subtitle")
    public static String LASER_SHOWDOWN_GAME_END_SUBTITLE;

    @ConfigOption(path = "lasershowdown.action-bar-count-down")
    public static String LASER_SHOWDOWN_ACTION_BAR_COUNT_DOWN;

    @ConfigOption(path = "lasershowdown.kill-player")
    public static String LASER_SHOWDOWN_KILL_PLAYER;

    @ConfigOption(path = "lasershowdown.player-death")
    public static String LASER_SHOWDOWN_PLAYER_DEATH;

    @ConfigOption(path = "lasershowdown.player-death-by-void")
    public static String LASER_SHOWDOWN_PLAYER_DEATH_BY_VOID;

    @ConfigOption(path = "lasershowdown.player-leave")
    public static String LASER_SHOWDOWN_PLAYER_LEAVE;
}
