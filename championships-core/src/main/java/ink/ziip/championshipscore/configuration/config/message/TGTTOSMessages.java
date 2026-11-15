package ink.ziip.championshipscore.configuration.config.message;

import ink.ziip.championshipscore.configuration.ConfigOption;

/** Message fields owned by the TGTTOS domain; persisted by MessageConfig. */
public final class TGTTOSMessages {
    private TGTTOSMessages() {}

    @ConfigOption(path = "tgttos.start-preparation")
    public static String TGTTOS_START_PREPARATION;

    @ConfigOption(path = "tgttos.start-preparation-title")
    public static String TGTTOS_START_PREPARATION_TITLE;

    @ConfigOption(path = "tgttos.start-preparation-subtitle")
    public static String TGTTOS_START_PREPARATION_SUBTITLE;

    @ConfigOption(path = "tgttos.game-start-soon-title")
    public static String TGTTOS_GAME_START_SOON_TITLE;

    @ConfigOption(path = "tgttos.game-start-title")
    public static String TGTTOS_GAME_START_TITLE;

    @ConfigOption(path = "tgttos.game-start-subtitle")
    public static String TGTTOS_GAME_START_SUBTITLE;

    @ConfigOption(path = "tgttos.game-end-title")
    public static String TGTTOS_GAME_END_TITLE;

    @ConfigOption(path = "tgttos.game-end-subtitle")
    public static String TGTTOS_GAME_END_SUBTITLE;

    @ConfigOption(path = "tgttos.action-bar-count-down")
    public static String TGTTOS_ACTION_BAR_COUNT_DOWN;

    @ConfigOption(path = "tgttos.arrived-at-end-point")
    public static String TGTTOS_ARRIVED_AT_POINT;

    @ConfigOption(path = "tgttos.team-arrived-at-end-point")
    public static String TGTTOS_TEAM_ARRIVED_AT_POINT;

    @ConfigOption(path = "tgttos.return-reason.accident")
    public static String TGTTOS_RETURN_REASON_ACCIDENT;

    @ConfigOption(path = "tgttos.fall-into-void")
    public static String TGTTOS_FALL_INTO_VOID;

    @ConfigOption(path = "tgttos.return-to-start")
    public static String TGTTOS_RETURN_TO_START;
}
