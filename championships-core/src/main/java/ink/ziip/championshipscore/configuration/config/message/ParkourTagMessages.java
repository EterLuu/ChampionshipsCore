package ink.ziip.championshipscore.configuration.config.message;

import ink.ziip.championshipscore.configuration.ConfigOption;

/** Message fields owned by the ParkourTag domain; persisted by MessageConfig. */
public final class ParkourTagMessages {
    private ParkourTagMessages() {}

    @ConfigOption(path = "parkourtag.start-preparation")
    public static String PARKOUR_TAG_START_PREPARATION;

    @ConfigOption(path = "parkourtag.start-preparation-title")
    public static String PARKOUR_TAG_START_PREPARATION_TITLE;

    @ConfigOption(path = "parkourtag.start-preparation-subtitle")
    public static String PARKOUR_TAG_START_PREPARATION_SUBTITLE;

    @ConfigOption(path = "parkourtag.game-start-soon-title")
    public static String PARKOUR_TAG_GAME_START_SOON_TITLE;

    @ConfigOption(path = "parkourtag.game-start-title")
    public static String PARKOUR_TAG_GAME_START_TITLE;

    @ConfigOption(path = "parkourtag.game-start-subtitle")
    public static String PARKOUR_TAG_GAME_START_SUBTITLE;

    @ConfigOption(path = "parkourtag.game-end-title")
    public static String PARKOUR_TAG_GAME_END_TITLE;

    @ConfigOption(path = "parkourtag.game-end-subtitle")
    public static String PARKOUR_TAG_GAME_END_SUBTITLE;

    @ConfigOption(path = "parkourtag.catch-player")
    public static String PARKOUR_TAG_CATCH_PLAYER;

    @ConfigOption(path = "parkourtag.player-leave")
    public static String PARKOUR_TAG_PLAYER_LEAVE;

    @ConfigOption(path = "parkourtag.become-chaser")
    public static String PARKOUR_TAG_BECOME_CHASER;

    @ConfigOption(path = "parkourtag.become-chaser-last")
    public static String PARKOUR_TAG_BECOME_CHASER_LAST;

    @ConfigOption(path = "parkourtag.chaser-selection-remaining")
    public static String PARKOUR_TAG_CHASER_SELECTION_REMAINING;

    @ConfigOption(path = "parkourtag.chaser-selection-last")
    public static String PARKOUR_TAG_CHASER_SELECTION_LAST;

    @ConfigOption(path = "parkourtag.become-chaser-failed")
    public static String PARKOUR_TAG_BECOME_CHASER_FAILED;

    @ConfigOption(path = "parkourtag.action-bar-count-down")
    public static String PARKOUR_TAG_ACTION_BAR_COUNT_DOWN;

    @ConfigOption(path = "parkourtag.show-points")
    public static String PARKOUR_TAG_SHOW_POINTS;

    @ConfigOption(path = "parkourtag.whole-team-was-killed")
    public static String PARKOUR_TAG_WHOLE_TEAM_WAS_KILLED;

    @ConfigOption(path = "parkourtag.kits.ender-eye")
    public static String PARKOUR_TAG_KITS_ENDER_EYE;

    @ConfigOption(path = "parkourtag.kits.feather")
    public static String PARKOUR_TAG_KITS_FEATHER;

    @ConfigOption(path = "parkourtag.kits.use-ender-eye")
    public static String PARKOUR_TAG_KITS_USE_ENDER_EYE;

    @ConfigOption(path = "parkourtag.kits.use-ender-eye-failed")
    public static String PARKOUR_TAG_KITS_USE_ENDER_EYE_FAILED;

    @ConfigOption(path = "parkourtag.kits.use-feather")
    public static String PARKOUR_TAG_KITS_USE_FEATHER;

    @ConfigOption(path = "parkourtag.kits.wind-charge")
    public static String PARKOUR_TAG_KITS_WIND_CHARGE;

    @ConfigOption(path = "parkourtag.kits.use-wind-charge")
    public static String PARKOUR_TAG_KITS_USE_WIND_CHARGE;

    @ConfigOption(path = "parkourtag.kits.use-wind-charge-failed")
    public static String PARKOUR_TAG_KITS_USE_WIND_CHARGE_FAILED;
}
