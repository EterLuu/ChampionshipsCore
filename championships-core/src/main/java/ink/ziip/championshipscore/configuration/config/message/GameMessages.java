package ink.ziip.championshipscore.configuration.config.message;

import ink.ziip.championshipscore.configuration.ConfigOption;

/** Message fields owned by the Game domain; persisted by MessageConfig. */
public final class GameMessages {
    private GameMessages() {}

    @ConfigOption(path = "game.stop.instance")
    public static String GAME_STOP_INSTANCE;

    @ConfigOption(path = "game.stop.unknown-game")
    public static String GAME_STOP_UNKNOWN_GAME;

    @ConfigOption(path = "game.stop.instance-missing")
    public static String GAME_STOP_INSTANCE_MISSING;

    @ConfigOption(path = "game.stop.instance-ambiguous")
    public static String GAME_STOP_INSTANCE_AMBIGUOUS;

    @ConfigOption(path = "game.stop.exception")
    public static String GAME_STOP_EXCEPTION;

    @ConfigOption(path = "game.stop.settled")
    public static String GAME_STOP_SETTLED;

    @ConfigOption(path = "game.stop.aborted")
    public static String GAME_STOP_ABORTED;

    @ConfigOption(path = "game.stop.not-active")
    public static String GAME_STOP_NOT_ACTIVE;

    @ConfigOption(path = "game.stop.replaced")
    public static String GAME_STOP_REPLACED;

    @ConfigOption(path = "game.stop.failed")
    public static String GAME_STOP_FAILED;

    @ConfigOption(path = "area-status.waiting")
    public static String AREA_STATUS_WAITING;

    @ConfigOption(path = "area-status.loading")
    public static String AREA_STATUS_LOADING;

    @ConfigOption(path = "area-status.preparation")
    public static String AREA_STATUS_PREPARATION;

    @ConfigOption(path = "area-status.countdown")
    public static String AREA_STATUS_COUNTDOWN;

    @ConfigOption(path = "area-status.progress")
    public static String AREA_STATUS_PROGRESS;

    @ConfigOption(path = "area-status.stopping")
    public static String AREA_STATUS_STOPPING;

    @ConfigOption(path = "area-status.end")
    public static String AREA_STATUS_END;

    @ConfigOption(path = "spectator.is-player")
    public static String SPECTATOR_IS_PLAYER;

    @ConfigOption(path = "spectator.leaving-area")
    public static String SPECTATOR_LEAVING_AREA;

    @ConfigOption(path = "spectator.cant-leaving-area")
    public static String SPECTATOR_CANT_LEAVING_AREA;

    @ConfigOption(path = "spectator.join-area")
    public static String SPECTATOR_JOIN_AREA;

    @ConfigOption(path = "spectator.cant-join-area")
    public static String SPECTATOR_CANT_JOIN_AREA;

    @ConfigOption(path = "game.team-game-start-successful")
    public static String GAME_TEAM_GAME_START_SUCCESSFUL;

    @ConfigOption(path = "game.team-game-start-failed")
    public static String GAME_TEAM_GAME_START_FAILED;

    @ConfigOption(path = "game.single-game-start-successful")
    public static String GAME_SINGLE_GAME_START_SUCCESSFUL;

    @ConfigOption(path = "game.single-game-start-failed")
    public static String GAME_SINGLE_GAME_START_FAILED;

    @ConfigOption(path = "game.board-bar")
    public static String GAME_BOARD_BAR;

    @ConfigOption(path = "game.board-row")
    public static String GAME_BOARD_RWO;

    @ConfigOption(path = "game.parkourtag")
    public static String GAME_PARKOUR_TAG;

    @ConfigOption(path = "game.battlebox")
    public static String GAME_BATTLE_BOX;

    @ConfigOption(path = "game.tntrun")
    public static String GAME_TNT_RUN;

    @ConfigOption(path = "game.lasershowdown")
    public static String GAME_LASER_SHOWDOWN;

    @ConfigOption(path = "game.skywars")
    public static String GAME_SKY_WARS;

    @ConfigOption(path = "game.tgttos")
    public static String GAME_TGTTOS;

    @ConfigOption(path = "game.dragoneggcarnival")
    public static String GAME_DRAGON_EGG_CARNIVAL;

    @ConfigOption(path = "game.parkourwarrior")
    public static String PARKOUR_WARRIOR;

    @ConfigOption(path = "game.hotycodydusky")
    public static String GAME_HOTY_CODY_DUSKY;

    @ConfigOption(path = "game.bingo")
    public static String GAME_BINGO;

    @ConfigOption(path = "game.craftingfeast")
    public static String GAME_CRAFTING_FEAST;

    @ConfigOption(path = "game.iamnotarobot")
    public static String GAME_ROBOT;

    @ConfigOption(path = "game.buildmart", nullable = true)
    public static String GAME_BUILD_MART;

    @ConfigOption(path = "game.dodgebolt", nullable = true)
    public static String GAME_DODGEBOLT;

    @ConfigOption(path = "game.acerace", nullable = true)
    public static String GAME_ACE_RACE;

    @ConfigOption(path = "game.riptiderush")
    public static String GAME_RIPTIDE_RUSH;

    @ConfigOption(path = "game.frostbite")
    public static String GAME_FROSTBITE;

    @ConfigOption(path = "game.laserbox")
    public static String GAME_LASER_BOX;

    @ConfigOption(path = "game.sulfursoccer")
    public static String GAME_SULFUR_SOCCER;

    @ConfigOption(path = "game.preparation-count-down")
    public static String GAME_PREPARATION_COUNT_DOWN;

    @ConfigOption(path = "game.introduction-title")
    public static String GAME_INTRODUCTION_TITLE;

    @ConfigOption(path = "game.round-preparation-action-bar")
    public static String GAME_ROUND_PREPARATION_ACTION_BAR;

    @ConfigOption(path = "game.start-count-down-title")
    public static String GAME_START_COUNT_DOWN_TITLE;

    @ConfigOption(path = "game.start-count-down-subtitle")
    public static String GAME_START_COUNT_DOWN_SUBTITLE;

    @ConfigOption(path = "game.start-action-bar")
    public static String GAME_START_ACTION_BAR;

    @ConfigOption(path = "game.end-action-bar")
    public static String GAME_END_ACTION_BAR;

    @ConfigOption(path = "game.round-end-title")
    public static String GAME_ROUND_END_TITLE;

    @ConfigOption(path = "game.round-complete-title")
    public static String GAME_ROUND_COMPLETE_TITLE;

    @ConfigOption(path = "game.round-end-subtitle")
    public static String GAME_ROUND_END_SUBTITLE;

    @ConfigOption(path = "spectator.display.remote")
    public static String SPECTATOR_DISPLAY_REMOTE;

    @ConfigOption(path = "spectator.display.copy")
    public static String SPECTATOR_DISPLAY_COPY;

    @ConfigOption(path = "spectator.display.instance")
    public static String SPECTATOR_DISPLAY_INSTANCE;

    @ConfigOption(path = "spectator.game-disabled")
    public static String SPECTATOR_GAME_DISABLED;

    @ConfigOption(path = "spectator.instance-missing")
    public static String SPECTATOR_INSTANCE_MISSING;

    @ConfigOption(path = "spectator.area-unavailable")
    public static String SPECTATOR_AREA_UNAVAILABLE;
}
