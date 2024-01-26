package ink.ziip.championshipscore.configuration.config.message;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.configuration.ConfigOption;
import ink.ziip.championshipscore.configuration.config.BaseConfigurationFile;
import lombok.Getter;

@Getter
public class MessageConfig extends BaseConfigurationFile {
    private final String fileName = "message.yml";
    private final String resourceName = "message.yml";

    public MessageConfig(ChampionshipsCore plugin) {
        super(plugin);
    }

    @Override
    public int getLatestVersion() {
        return 2;
    }

    // Permission
    @ConfigOption(path = "no-permission")
    public static String NO_PERMISSION;

    // Team
    @ConfigOption(path = "team.successfully-added")
    public static String TEAM_SUCCESSFULLY_ADDED;

    @ConfigOption(path = "team.added-failed")
    public static String TEAM_ADDED_FAILED;

    @ConfigOption(path = "team.successfully-deleted")
    public static String TEAM_SUCCESSFULLY_DELETED;

    @ConfigOption(path = "team.deleted-failed")
    public static String TEAM_DELETED_FAILED;

    // Member
    @ConfigOption(path = "member.successfully-added")
    public static String MEMBER_SUCCESSFULLY_ADDED;

    @ConfigOption(path = "member.added-failed")
    public static String MEMBER_ADDED_FAILED;

    @ConfigOption(path = "member.successfully-deleted")
    public static String MEMBER_SUCCESSFULLY_DELETED;

    @ConfigOption(path = "member.deleted-failed")
    public static String MEMBER_DELETED_FAILED;

    // Reason
    @ConfigOption(path = "reason.team-does-not-exist")
    public static String REASON_TEAM_DOES_NOT_EXIST;

    @ConfigOption(path = "reason.team-already-exist")
    public static String REASON_TEAM_ALREADY_EXIST;

    @ConfigOption(path = "reason.member-does-not-exist")
    public static String REASON_MEMBER_DOES_NOT_EXIST;

    @ConfigOption(path = "reason.member-already-exist")
    public static String REASON_MEMBER_ALREADY_EXIST;

    @ConfigOption(path = "reason.area-already-exist")
    public static String REASON_AREA_ALREADY_EXIST;

    @ConfigOption(path = "reason.area-not-in-waiting-status")
    public static String REASON_AREA_NOT_IN_WAITING_STATUS;

    // Area
    @ConfigOption(path = "area.successfully-added")
    public static String AREA_SUCCESSFULLY_ADDED;

    @ConfigOption(path = "area.added-failed")
    public static String AREA_ADDED_FAILED;

    @ConfigOption(path = "area.successfully-saved")
    public static String AREA_SUCCESSFULLY_SAVED;

    @ConfigOption(path = "area.saved-failed")
    public static String AREA_SAVED_FAILED;

    @ConfigOption(path = "area.setting-option-succeeded")
    public static String AREA_SETTING_OPTION_SUCCEEDED;

    @ConfigOption(path = "area.setting-option-failed")
    public static String AREA_SETTING_OPTION_FAILED;

    // Rank
    @ConfigOption(path = "rank.rank-info")
    public static String RANK_RANK_INFO;

    @ConfigOption(path = "rank.team-board-bar")
    public static String RANK_TEAM_BOARD_BAR;

    @ConfigOption(path = "rank.team-board-row")
    public static String RANK_TEAM_BOARD_ROW;

    @ConfigOption(path = "rank.player-board-bar")
    public static String RANK_PLAYER_BOARD_BAR;

    @ConfigOption(path = "rank.player-board-row")
    public static String RANK_PLAYER_BOARD_ROW;

    @ConfigOption(path = "rank.not-player")
    public static String RANK_NOT_PLAYER;

    @ConfigOption(path = "rank.no-record")
    public static String RANK_NO_RECORD;

    // Bingo
    @ConfigOption(path = "bingo.game-start")
    public static String BINGO_GAME_START;

    @ConfigOption(path = "bingo.game-end")
    public static String BINGO_GAME_END;

    @ConfigOption(path = "bingo.task-complete")
    public static String BINGO_TASK_COMPLETE;

    @ConfigOption(path = "bingo.task-expired")
    public static String BINGO_TASK_EXPIRED;

    @ConfigOption(path = "bingo.rank-board-bar")
    public static String BINGO_RANK_BOARD_BAR;

    @ConfigOption(path = "bingo.rank-board-info")
    public static String BINGO_RANK_BOARD_INFO;

    // Spectator
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

    // Game
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

    // BattleBox
    @ConfigOption(path = "battlebox.start-preparation")
    public static String BATTLE_BOX_START_PREPARATION;

    @ConfigOption(path = "battlebox.start-preparation-title")
    public static String BATTLE_BOX_START_PREPARATION_TITLE;

    @ConfigOption(path = "battlebox.start-preparation-subtitle")
    public static String BATTLE_BOX_START_PREPARATION_SUBTITLE;

    @ConfigOption(path = "battlebox.game-start-soon")
    public static String BATTLE_BOX_GAME_START_SOON;

    @ConfigOption(path = "battlebox.game-start-soon-title")
    public static String BATTLE_BOX_GAME_START_SOON_TITLE;

    @ConfigOption(path = "battlebox.game-start-soon-subtitle")
    public static String BATTLE_BOX_GAME_START_SOON_SUBTITLE;

    @ConfigOption(path = "battlebox.count-down")
    public static String BATTLE_BOX_COUNT_DOWN;

    @ConfigOption(path = "battlebox.game-start")
    public static String BATTLE_BOX_GAME_START;

    @ConfigOption(path = "battlebox.game-start-title")
    public static String BATTLE_BOX_GAME_START_TITLE;

    @ConfigOption(path = "battlebox.game-start-subtitle")
    public static String BATTLE_BOX_GAME_START_SUBTITLE;

    @ConfigOption(path = "battlebox.game-end")
    public static String BATTLE_BOX_GAME_END;

    @ConfigOption(path = "battlebox.game-end-title")
    public static String BATTLE_BOX_GAME_END_TITLE;

    @ConfigOption(path = "battlebox.game-end-subtitle")
    public static String BATTLE_BOX_GAME_END_SUBTITLE;

    @ConfigOption(path = "battlebox.win")
    public static String BATTLE_BOX_WIN;

    @ConfigOption(path = "battlebox.draw")
    public static String BATTLE_BOX_DRAW;

    @ConfigOption(path = "battlebox.kill-player")
    public static String BATTLE_BOX_KILL_PLAYER;

    @ConfigOption(path = "battlebox.player-leave")
    public static String BATTLE_BOX_PLAYER_LEAVE;

    @ConfigOption(path = "battlebox.kit-choose")
    public static String BATTLE_BOX_KIT_CHOOSE;

    @ConfigOption(path = "battlebox.kit-already-choose")
    public static String BATTLE_BOX_KIT_ALREADY_CHOOSE;

    @ConfigOption(path = "battlebox.action-bar-count-down")
    public static String BATTLE_BOX_ACTION_BAR_COUNT_DOWN;

    @ConfigOption(path = "battlebox.show-points")
    public static String BATTLE_BOX_SHOW_POINTS;

    @ConfigOption(path = "battlebox.kits.punch")
    public static String BATTLE_BOX_KITS_PUNCH;

    @ConfigOption(path = "battlebox.kits.knock-back")
    public static String BATTLE_BOX_KITS_KNOCK_BACK;

    @ConfigOption(path = "battlebox.kits.jump")
    public static String BATTLE_BOX_KITS_JUMP;

    @ConfigOption(path = "battlebox.kits.pull")
    public static String BATTLE_BOX_KITS_PULL;
    
    // ParkourTag
    @ConfigOption(path = "parkourtag.start-preparation")
    public static String PARKOUR_TAG_START_PREPARATION;

    @ConfigOption(path = "parkourtag.start-preparation-title")
    public static String PARKOUR_TAG_START_PREPARATION_TITLE;

    @ConfigOption(path = "parkourtag.start-preparation-subtitle")
    public static String PARKOUR_TAG_START_PREPARATION_SUBTITLE;

    @ConfigOption(path = "parkourtag.game-start-soon")
    public static String PARKOUR_TAG_GAME_START_SOON;

    @ConfigOption(path = "parkourtag.game-start-soon-title")
    public static String PARKOUR_TAG_GAME_START_SOON_TITLE;

    @ConfigOption(path = "parkourtag.game-start-soon-subtitle")
    public static String PARKOUR_TAG_GAME_START_SOON_SUBTITLE;

    @ConfigOption(path = "parkourtag.count-down")
    public static String PARKOUR_TAG_COUNT_DOWN;

    @ConfigOption(path = "parkourtag.game-start")
    public static String PARKOUR_TAG_GAME_START;

    @ConfigOption(path = "parkourtag.game-start-title")
    public static String PARKOUR_TAG_GAME_START_TITLE;

    @ConfigOption(path = "parkourtag.game-start-subtitle")
    public static String PARKOUR_TAG_GAME_START_SUBTITLE;

    @ConfigOption(path = "parkourtag.game-end")
    public static String PARKOUR_TAG_GAME_END;

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

    @ConfigOption(path = "parkourtag.become-chaser-failed")
    public static String PARKOUR_TAG_BECOME_CHASER_FAILED;

    @ConfigOption(path = "parkourtag.action-bar-count-down")
    public static String PARKOUR_TAG_ACTION_BAR_COUNT_DOWN;

    @ConfigOption(path = "parkourtag.show-points")
    public static String PARKOUR_TAG_SHOW_POINTS;

    @ConfigOption(path = "parkourtag.whole-team-was-killed")
    public static String PARKOUR_TAG_WHOLE_TEAM_WAS_KILLED;

    @ConfigOption(path = "parkourtag.kits.clock")
    public static String PARKOUR_TAG_KITS_CLOCK;

    @ConfigOption(path = "parkourtag.kits.feather")
    public static String PARKOUR_TAG_KITS_FEATHER;

    @ConfigOption(path = "parkourtag.kits.use-clock")
    public static String PARKOUR_TAG_KITS_USE_CLOCK;

    @ConfigOption(path = "parkourtag.kits.use-clock-failed")
    public static String PARKOUR_TAG_KITS_USE_CLOCK_FAILED;

    @ConfigOption(path = "parkourtag.kits.use-feather")
    public static String PARKOUR_TAG_KITS_USE_FEATHER;
    
    // SkyWars
    @ConfigOption(path = "skywars.start-preparation")
    public static String SKY_WARS_START_PREPARATION;

    @ConfigOption(path = "skywars.start-preparation-title")
    public static String SKY_WARS_START_PREPARATION_TITLE;

    @ConfigOption(path = "skywars.start-preparation-subtitle")
    public static String SKY_WARS_START_PREPARATION_SUBTITLE;

    @ConfigOption(path = "skywars.game-start-soon")
    public static String SKY_WARS_GAME_START_SOON;

    @ConfigOption(path = "skywars.game-start-soon-title")
    public static String SKY_WARS_GAME_START_SOON_TITLE;

    @ConfigOption(path = "skywars.game-start-soon-subtitle")
    public static String SKY_WARS_GAME_START_SOON_SUBTITLE;

    @ConfigOption(path = "skywars.count-down")
    public static String SKY_WARS_COUNT_DOWN;

    @ConfigOption(path = "skywars.game-start")
    public static String SKY_WARS_GAME_START;

    @ConfigOption(path = "skywars.game-start-title")
    public static String SKY_WARS_GAME_START_TITLE;

    @ConfigOption(path = "skywars.game-start-subtitle")
    public static String SKY_WARS_GAME_START_SUBTITLE;

    @ConfigOption(path = "skywars.game-end")
    public static String SKY_WARS_GAME_END;

    @ConfigOption(path = "skywars.game-end-title")
    public static String SKY_WARS_GAME_END_TITLE;

    @ConfigOption(path = "skywars.game-end-subtitle")
    public static String SKY_WARS_GAME_END_SUBTITLE;

    @ConfigOption(path = "skywars.kill-player")
    public static String SKY_WARS_KILL_PLAYER;

    @ConfigOption(path = "skywars.kill-player-by-void")
    public static String SKY_WARS_KILL_PLAYER_BY_VOID;

    @ConfigOption(path = "skywars.player-death")
    public static String SKY_WARS_PLAYER_DEATH;

    @ConfigOption(path = "skywars.player-death-by-void")
    public static String SKY_WARS_PLAYER_DEATH_BY_VOID;
    
    @ConfigOption(path = "skywars.player-leave")
    public static String SKY_WARS_PLAYER_LEAVE;
    
    @ConfigOption(path = "skywars.action-bar-count-down")
    public static String SKY_WARS_ACTION_BAR_COUNT_DOWN;

    @ConfigOption(path = "skywars.whole-team-was-killed")
    public static String SKY_WARS_WHOLE_TEAM_WAS_KILLED;

    @ConfigOption(path = "skywars.board-shrink")
    public static String SKY_WARS_BOARD_SHRINK;

    @ConfigOption(path = "skywars.deduct-food-level")
    public static String SKY_WARS_DEDUCT_FOOD_LEVEL;

    // TGTTOS
    @ConfigOption(path = "tgttos.start-preparation")
    public static String TGTTOS_START_PREPARATION;

    @ConfigOption(path = "tgttos.start-preparation-title")
    public static String TGTTOS_START_PREPARATION_TITLE;

    @ConfigOption(path = "tgttos.start-preparation-subtitle")
    public static String TGTTOS_START_PREPARATION_SUBTITLE;

    @ConfigOption(path = "tgttos.game-start-soon")
    public static String TGTTOS_GAME_START_SOON;

    @ConfigOption(path = "tgttos.game-start-soon-title")
    public static String TGTTOS_GAME_START_SOON_TITLE;

    @ConfigOption(path = "tgttos.game-start-soon-subtitle")
    public static String TGTTOS_GAME_START_SOON_SUBTITLE;

    @ConfigOption(path = "tgttos.count-down")
    public static String TGTTOS_COUNT_DOWN;

    @ConfigOption(path = "tgttos.game-start")
    public static String TGTTOS_GAME_START;

    @ConfigOption(path = "tgttos.game-start-title")
    public static String TGTTOS_GAME_START_TITLE;

    @ConfigOption(path = "tgttos.game-start-subtitle")
    public static String TGTTOS_GAME_START_SUBTITLE;

    @ConfigOption(path = "tgttos.game-end")
    public static String TGTTOS_GAME_END;

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

    @ConfigOption(path = "tgttos.fall-into-void")
    public static String TGTTOS_FALL_INTO_VOID;

    // TNT Run
    @ConfigOption(path = "tntrun.start-preparation")
    public static String TNT_RUN_START_PREPARATION;

    @ConfigOption(path = "tntrun.start-preparation-title")
    public static String TNT_RUN_START_PREPARATION_TITLE;

    @ConfigOption(path = "tntrun.start-preparation-subtitle")
    public static String TNT_RUN_START_PREPARATION_SUBTITLE;

    @ConfigOption(path = "tntrun.game-start")
    public static String TNT_RUN_GAME_START;

    @ConfigOption(path = "tntrun.game-start-title")
    public static String TNT_RUN_GAME_START_TITLE;

    @ConfigOption(path = "tntrun.game-start-subtitle")
    public static String TNT_RUN_GAME_START_SUBTITLE;

    @ConfigOption(path = "tntrun.count-down")
    public static String TNT_RUN_COUNT_DOWN;

    @ConfigOption(path = "tntrun.action-bar-count-down")
    public static String TNT_RUN_ACTION_BAR_COUNT_DOWN;

    @ConfigOption(path = "tntrun.game-end")
    public static String TNT_RUN_GAME_END;

    @ConfigOption(path = "tntrun.game-end-title")
    public static String TNT_RUN_GAME_END_TITLE;

    @ConfigOption(path = "tntrun.game-end-subtitle")
    public static String TNT_RUN_GAME_END_SUBTITLE;
    
    @ConfigOption(path = "tntrun.fall-into-void")
    public static String TNT_RUN_FALL_INTO_VOID;

    @ConfigOption(path = "tntrun.tnt-rain")
    public static String TNT_RUN_TNT_RAIN;
}
