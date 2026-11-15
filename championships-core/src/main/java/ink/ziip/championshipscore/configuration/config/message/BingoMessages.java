package ink.ziip.championshipscore.configuration.config.message;

import ink.ziip.championshipscore.configuration.ConfigOption;

/** Message fields owned by the Bingo domain; persisted by MessageConfig. */
public final class BingoMessages {
    private BingoMessages() {}

    @ConfigOption(path = "bingo.start-preparation")
    public static String BINGO_START_PREPARATION;

    @ConfigOption(path = "bingo.start-preparation-title")
    public static String BINGO_START_PREPARATION_TITLE;

    @ConfigOption(path = "bingo.start-preparation-subtitle")
    public static String BINGO_START_PREPARATION_SUBTITLE;

    @ConfigOption(path = "bingo.game-start")
    public static String BINGO_GAME_START;

    @ConfigOption(path = "bingo.game-start-title")
    public static String BINGO_GAME_START_TITLE;

    @ConfigOption(path = "bingo.game-start-subtitle")
    public static String BINGO_GAME_START_SUBTITLE;

    @ConfigOption(path = "bingo.game-end")
    public static String BINGO_GAME_END;

    @ConfigOption(path = "bingo.game-end-title")
    public static String BINGO_GAME_END_TITLE;

    @ConfigOption(path = "bingo.game-end-subtitle")
    public static String BINGO_GAME_END_SUBTITLE;

    @ConfigOption(path = "bingo.action-bar-count-down")
    public static String BINGO_ACTION_BAR_COUNT_DOWN;

    @ConfigOption(path = "bingo.action-bar-count-down-pvp")
    public static String BINGO_ACTION_BAR_COUNT_DOWN_PVP;

    @ConfigOption(path = "bingo.action-bar-count-down-protection")
    public static String BINGO_ACTION_BAR_COUNT_DOWN_PROTECTION;

    @ConfigOption(path = "bingo.pvp-protection")
    public static String BINGO_PVP_PROTECTION;

    @ConfigOption(path = "bingo.pvp-active")
    public static String BINGO_PVP_ACTIVE;

    @ConfigOption(path = "bingo.pvp-start-count-down")
    public static String BINGO_PVP_START_COUNT_DOWN;

    @ConfigOption(path = "bingo.pvp-started")
    public static String BINGO_PVP_STARTED;

    @ConfigOption(path = "bingo.task-completed")
    public static String BINGO_TASK_COMPLETED;

    @ConfigOption(path = "bingo.game-winner")
    public static String BINGO_GAME_WINNER;
}
