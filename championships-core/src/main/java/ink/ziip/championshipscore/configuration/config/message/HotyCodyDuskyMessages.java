package ink.ziip.championshipscore.configuration.config.message;

import ink.ziip.championshipscore.configuration.ConfigOption;

/** Message fields owned by the HotyCodyDusky domain; persisted by MessageConfig. */
public final class HotyCodyDuskyMessages {
    private HotyCodyDuskyMessages() {}

    @ConfigOption(path = "hotycodydusky.start-preparation")
    public static String HOTY_CODY_DUSKY_START_PREPARATION;

    @ConfigOption(path = "hotycodydusky.start-preparation-title")
    public static String HOTY_CODY_DUSKY_START_PREPARATION_TITLE;

    @ConfigOption(path = "hotycodydusky.start-preparation-subtitle")
    public static String HOTY_CODY_DUSKY_START_PREPARATION_SUBTITLE;

    @ConfigOption(path = "hotycodydusky.game-start-soon-title")
    public static String HOTY_CODY_DUSKY_GAME_START_SOON_TITLE;

    @ConfigOption(path = "hotycodydusky.game-start-title")
    public static String HOTY_CODY_DUSKY_GAME_START_TITLE;

    @ConfigOption(path = "hotycodydusky.game-start-subtitle")
    public static String HOTY_CODY_DUSKY_GAME_START_SUBTITLE;

    @ConfigOption(path = "hotycodydusky.game-end-title")
    public static String HOTY_CODY_DUSKY_GAME_END_TITLE;

    @ConfigOption(path = "hotycodydusky.game-end-subtitle")
    public static String HOTY_CODY_DUSKY_GAME_END_SUBTITLE;

    @ConfigOption(path = "hotycodydusky.action-bar-count-down")
    public static String HOTY_CODY_DUSKY_ACTION_BAR_COUNT_DOWN;

    @ConfigOption(path = "hotycodydusky.give-cody-to-player")
    public static String HOTY_CODY_DUSKY_GIVE_CODY_TO_PLAYER;

    @ConfigOption(path = "hotycodydusky.player-death")
    public static String HOTY_CODY_DUSKY_PLAYER_DEATH;

    @ConfigOption(path = "hotycodydusky.player-received-cody")
    public static String HOTY_CODY_DUSKY_PLAYER_RECEIVED_CODY;

    @ConfigOption(path = "hotycodydusky.player-leave")
    public static String HOTY_CODY_DUSKY_PLAYER_LEAVE;

    @ConfigOption(path = "hotycodydusky.whole-team-was-killed")
    public static String HOTY_CODY_DUSKY_WHOLE_TEAM_WAS_KILLED;

    @ConfigOption(path = "hotycodydusky.intermission")
    public static String INTERMISSION;

    @ConfigOption(path = "hotycodydusky.immunity")
    public static String IMMUNITY;

    @ConfigOption(path = "hotycodydusky.scatter")
    public static String SCATTER;
}
