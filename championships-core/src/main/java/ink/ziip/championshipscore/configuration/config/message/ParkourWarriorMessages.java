package ink.ziip.championshipscore.configuration.config.message;

import ink.ziip.championshipscore.configuration.ConfigOption;

/** Message fields owned by the ParkourWarrior domain; persisted by MessageConfig. */
public final class ParkourWarriorMessages {
    private ParkourWarriorMessages() {}

    @ConfigOption(path = "parkourwarrior.start-preparation")
    public static String PARKOUR_WARRIOR_START_PREPARATION;

    @ConfigOption(path = "parkourwarrior.start-preparation-title")
    public static String PARKOUR_WARRIOR_START_PREPARATION_TITLE;

    @ConfigOption(path = "parkourwarrior.start-preparation-subtitle")
    public static String PARKOUR_WARRIOR_START_PREPARATION_SUBTITLE;

    @ConfigOption(path = "parkourwarrior.game-start-soon-title")
    public static String PARKOUR_WARRIOR_GAME_START_SOON_TITLE;

    @ConfigOption(path = "parkourwarrior.game-start-title")
    public static String PARKOUR_WARRIOR_GAME_START_TITLE;

    @ConfigOption(path = "parkourwarrior.game-start-subtitle")
    public static String PARKOUR_WARRIOR_GAME_START_SUBTITLE;

    @ConfigOption(path = "parkourwarrior.game-end-title")
    public static String PARKOUR_WARRIOR_GAME_END_TITLE;

    @ConfigOption(path = "parkourwarrior.game-end-subtitle")
    public static String PARKOUR_WARRIOR_GAME_END_SUBTITLE;

    @ConfigOption(path = "parkourwarrior.action-bar-count-down")
    public static String PARKOUR_WARRIOR_ACTION_BAR_COUNT_DOWN;

    @ConfigOption(path = "parkourwarrior.sudden-death-count-down", nullable = true)
    public static String PARKOUR_WARRIOR_SUDDEN_DEATH_COUNT_DOWN;

    @ConfigOption(path = "parkourwarrior.sudden-death-start-count-down")
    public static String PARKOUR_WARRIOR_SUDDEN_DEATH_START_COUNT_DOWN;

    @ConfigOption(path = "parkourwarrior.arrived-at-checkpoint")
    public static String PARKOUR_WARRIOR_SUB_CHECKPOINT_ENTERED;

    @ConfigOption(path = "parkourwarrior.arrived-at-sub-checkpoint")
    public static String PARKOUR_WARRIOR_SUB_CHECKPOINT_ARRIVED;

    @ConfigOption(path = "parkourwarrior.complete-sub-checkpoint")
    public static String PARKOUR_WARRIOR_SUB_CHECKPOINT_COMPLETED;

    @ConfigOption(path = "parkourwarrior.arrived-at-end-point")
    public static String PARKOUR_WARRIOR_END_CHECKPOINT_COMPLETED;

    @ConfigOption(path = "parkourwarrior.fall-into-void")
    public static String PARKOUR_WARRIOR_FALL_INTO_VOID;

    @ConfigOption(path = "parkourwarrior.kits.back-tool")
    public static String PARKOUR_WARRIOR_KITS_BACK_TOOL_NAME;

    @ConfigOption(path = "parkourwarrior.start-sudden-death")
    public static String PARKOUR_WARRIOR_START_SUDDEN_DEATH;

    @ConfigOption(path = "parkourwarrior.start-sudden-death-title", nullable = true)
    public static String PARKOUR_WARRIOR_START_SUDDEN_DEATH_TITLE;

    @ConfigOption(path = "parkourwarrior.start-sudden-death-subtitle", nullable = true)
    public static String PARKOUR_WARRIOR_START_SUDDEN_DEATH_SUBTITLE;
}
