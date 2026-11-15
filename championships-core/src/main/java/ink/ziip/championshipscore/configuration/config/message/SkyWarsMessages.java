package ink.ziip.championshipscore.configuration.config.message;

import ink.ziip.championshipscore.configuration.ConfigOption;

/** Message fields owned by the SkyWars domain; persisted by MessageConfig. */
public final class SkyWarsMessages {
    private SkyWarsMessages() {}

    @ConfigOption(path = "skywars.start-preparation")
    public static String SKY_WARS_START_PREPARATION;

    @ConfigOption(path = "skywars.start-preparation-title")
    public static String SKY_WARS_START_PREPARATION_TITLE;

    @ConfigOption(path = "skywars.start-preparation-subtitle")
    public static String SKY_WARS_START_PREPARATION_SUBTITLE;

    @ConfigOption(path = "skywars.game-start-soon-title")
    public static String SKY_WARS_GAME_START_SOON_TITLE;

    @ConfigOption(path = "skywars.game-start-title")
    public static String SKY_WARS_GAME_START_TITLE;

    @ConfigOption(path = "skywars.game-start-subtitle")
    public static String SKY_WARS_GAME_START_SUBTITLE;

    @ConfigOption(path = "skywars.game-end-title")
    public static String SKY_WARS_GAME_END_TITLE;

    @ConfigOption(path = "skywars.game-end-subtitle")
    public static String SKY_WARS_GAME_END_SUBTITLE;

    @ConfigOption(path = "skywars.kill-player")
    public static String SKY_WARS_KILL_PLAYER;

    @ConfigOption(path = "skywars.kill-player-by-creeper")
    public static String SKY_WARS_KILL_PLAYER_BY_CREEPER;

    @ConfigOption(path = "skywars.kill-team-player")
    public static String SKY_WARS_KILL_TEAM_PLAYER;

    @ConfigOption(path = "skywars.kill-player-by-void")
    public static String SKY_WARS_KILL_PLAYER_BY_VOID;

    @ConfigOption(path = "skywars.player-death")
    public static String SKY_WARS_PLAYER_DEATH;

    @ConfigOption(path = "skywars.player-death-by-void")
    public static String SKY_WARS_PLAYER_DEATH_BY_VOID;

    @ConfigOption(path = "skywars.player-create-portal")
    public static String SKY_WARS_PLAYER_CREATE_PORTAL;

    @ConfigOption(path = "skywars.player-leave")
    public static String SKY_WARS_PLAYER_LEAVE;

    @ConfigOption(path = "skywars.boss-bar-shrink-drain")
    public static String SKY_WARS_BOSS_BAR_SHRINK_DRAIN;

    @ConfigOption(path = "skywars.boss-bar-drain")
    public static String SKY_WARS_BOSS_BAR_DRAIN;

    @ConfigOption(path = "skywars.boss-bar-shrink")
    public static String SKY_WARS_BOSS_BAR_SHRINK;

    @ConfigOption(path = "skywars.boss-bar")
    public static String SKY_WARS_BOSS_BAR;

    @ConfigOption(path = "skywars.whole-team-was-killed")
    public static String SKY_WARS_WHOLE_TEAM_WAS_KILLED;

    @ConfigOption(path = "skywars.board-shrink")
    public static String SKY_WARS_BOARD_SHRINK;

    @ConfigOption(path = "skywars.board-shrink-count-down")
    public static String SKY_WARS_BOARD_SHRINK_COUNT_DOWN;

    @ConfigOption(path = "skywars.stop-board-shrink")
    public static String SKY_WARS_STOP_BOARD_SHRINK;

    @ConfigOption(path = "skywars.happy-ghast-spawned")
    public static String SKY_WARS_HAPPY_GHAST_SPAWNED;

    @ConfigOption(path = "skywars.happy-ghast-count-down")
    public static String SKY_WARS_HAPPY_GHAST_COUNT_DOWN;

    @ConfigOption(path = "skywars.happy-ghast-spawned-action-bar")
    public static String SKY_WARS_HAPPY_GHAST_SPAWNED_ACTION_BAR;

    @ConfigOption(path = "skywars.deduct-food-level")
    public static String SKY_WARS_DEDUCT_FOOD_LEVEL;

    @ConfigOption(path = "skywars.health-drain-count-down")
    public static String SKY_WARS_HEALTH_DRAIN_COUNT_DOWN;

    @ConfigOption(path = "skywars.out-of-border")
    public static String SKY_WARS_OUT_OF_BORDER;

    @ConfigOption(path = "skywars.wrong-team-ghast")
    public static String SKYWARS_WRONG_TEAM_GHAST;
}
