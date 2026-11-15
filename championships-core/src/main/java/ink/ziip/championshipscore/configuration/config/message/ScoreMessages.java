package ink.ziip.championshipscore.configuration.config.message;

import ink.ziip.championshipscore.configuration.ConfigOption;

/** Message fields owned by the Score domain; persisted by MessageConfig. */
public final class ScoreMessages {
    private ScoreMessages() {}

    @ConfigOption(path = "rank.rank-info")
    public static String RANK_RANK_INFO;

    @ConfigOption(path = "rank.team-board-bar")
    public static String RANK_TEAM_BOARD_BAR;

    @ConfigOption(path = "rank.game-team-board-bar")
    public static String RANK_GAME_TEAM_BOARD_BAR;

    @ConfigOption(path = "rank.team-board-row")
    public static String RANK_TEAM_BOARD_ROW;

    @ConfigOption(path = "rank.team-board-entry")
    public static String RANK_TEAM_BOARD_ENTRY;

    @ConfigOption(path = "rank.player-board-bar")
    public static String RANK_PLAYER_BOARD_BAR;

    @ConfigOption(path = "rank.player-board-row")
    public static String RANK_PLAYER_BOARD_ROW;

    @ConfigOption(path = "rank.player-board-entry")
    public static String RANK_PLAYER_BOARD_ENTRY;

    @ConfigOption(path = "rank.not-player")
    public static String RANK_NOT_PLAYER;

    @ConfigOption(path = "rank.final-board-bar")
    public static String RANK_FINAL_BOARD_BAR;

    @ConfigOption(path = "rank.final-recap-hint")
    public static String RANK_FINAL_RECAP_HINT;

    @ConfigOption(path = "rank.no-recap")
    public static String RANK_NO_RECAP;

    @ConfigOption(path = "rank.final-title")
    public static String RANK_FINAL_TITLE;

    @ConfigOption(path = "rank.final-subtitle")
    public static String RANK_FINAL_SUBTITLE;

    @ConfigOption(path = "rank.recap-hint-actionbar")
    public static String RANK_RECAP_HINT_ACTIONBAR;

    @ConfigOption(path = "rank.recap-shown")
    public static String RANK_RECAP_SHOWN;

    @ConfigOption(path = "rank.teamboard-hint")
    public static String RANK_TEAMBOARD_HINT;

    @ConfigOption(path = "rank.game-weight-bar")
    public static String RANK_GAME_WEIGHT_BAR;

    @ConfigOption(path = "rank.game-weight-row")
    public static String RANK_GAME_WEIGHT_ROW;

    @ConfigOption(path = "placeholder.none")
    public static String PLACEHOLDER_NONE;

    @ConfigOption(path = "placeholder.spectator")
    public static String PLACEHOLDER_SPECTATOR;

    @ConfigOption(path = "placeholder.parkourtag-spectator")
    public static String PLACEHOLDER_PARKOUR_TAG_SPECTATOR;

    @ConfigOption(path = "placeholder.parkourtag-chaser")
    public static String PLACEHOLDER_PARKOUR_TAG_CHASER;

    @ConfigOption(path = "placeholder.parkourtag-escapee")
    public static String PLACEHOLDER_PARKOUR_TAG_ESCAPEE;

    @ConfigOption(path = "presentation.daily-lobby")
    public static String PRESENTATION_DAILY_LOBBY;

    @ConfigOption(path = "presentation.daily-game")
    public static String PRESENTATION_DAILY_GAME;

    @ConfigOption(path = "presentation.tab.team-footer")
    public static String PRESENTATION_TAB_TEAM_FOOTER;

    @ConfigOption(path = "presentation.tab.daily-team-footer")
    public static String PRESENTATION_TAB_DAILY_TEAM_FOOTER;

    @ConfigOption(path = "presentation.tab.current-game-footer")
    public static String PRESENTATION_TAB_CURRENT_GAME_FOOTER;
}
