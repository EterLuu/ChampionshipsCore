package ink.ziip.championshipscore.configuration.config.message;

import ink.ziip.championshipscore.configuration.ConfigOption;

/** Message fields owned by the AceRace domain; persisted by MessageConfig. */
public final class AceRaceMessages {
    private AceRaceMessages() {}

    @ConfigOption(path = "acerace.start-preparation")
    public static String ACE_RACE_START_PREPARATION;

    @ConfigOption(path = "acerace.start-preparation-title")
    public static String ACE_RACE_START_PREPARATION_TITLE;

    @ConfigOption(path = "acerace.start-preparation-subtitle")
    public static String ACE_RACE_START_PREPARATION_SUBTITLE;

    @ConfigOption(path = "acerace.game-start-title")
    public static String ACE_RACE_GAME_START_TITLE;

    @ConfigOption(path = "acerace.game-start-subtitle")
    public static String ACE_RACE_GAME_START_SUBTITLE;

    @ConfigOption(path = "acerace.game-end-title")
    public static String ACE_RACE_GAME_END_TITLE;

    @ConfigOption(path = "acerace.game-end-subtitle")
    public static String ACE_RACE_GAME_END_SUBTITLE;

    @ConfigOption(path = "acerace.action-bar-count-down")
    public static String ACE_RACE_ACTION_BAR_COUNT_DOWN;

    @ConfigOption(path = "acerace.received-elytra")
    public static String ACE_RACE_RECEIVED_ELYTRA;

    @ConfigOption(path = "acerace.received-trident")
    public static String ACE_RACE_RECEIVED_TRIDENT;

    @ConfigOption(path = "acerace.received-dolphins-grace")
    public static String ACE_RACE_RECEIVED_DOLPHINS_GRACE;

    @ConfigOption(path = "acerace.lap-completed")
    public static String ACE_RACE_LAP_COMPLETED;

    @ConfigOption(path = "acerace.finished")
    public static String ACE_RACE_FINISHED;

    @ConfigOption(path = "acerace.returned-to-respawn-point")
    public static String ACE_RACE_RETURNED_TO_RESPAWN_POINT;

    @ConfigOption(path = "acerace.launch-pad")
    public static String ACE_RACE_LAUNCH_PAD;

    @ConfigOption(path = "acerace.jump-pad")
    public static String ACE_RACE_JUMP_PAD;

    @ConfigOption(path = "acerace.speed-boost")
    public static String ACE_RACE_SPEED_BOOST;
}
