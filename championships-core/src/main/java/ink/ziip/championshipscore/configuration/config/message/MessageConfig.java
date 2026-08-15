package ink.ziip.championshipscore.configuration.config.message;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.configuration.ConfigOption;
import ink.ziip.championshipscore.configuration.config.BaseConfigurationFile;
import lombok.Getter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.List;

@Getter
public class MessageConfig extends BaseConfigurationFile {
    private final String fileName = "message.yml";
    private final String resourceName = "message.yml";

    public MessageConfig(ChampionshipsCore plugin) {
        super(plugin);
    }

    @Override
    public int getLatestVersion() {
        return 32;
    }

    /** Replace every player-facing section so v30's terminology and spacing stay consistent at runtime. */
    @Override
    public void loadFromOutdatedConfiguration(@NotNull YamlConfiguration outdatedConfiguration) throws IOException {
        for (String section : List.of("command", "team", "member", "reason", "area", "rank", "spectator", "game",
                "battlebox", "parkourtag", "skywars", "tgttos", "bingo", "buildmart", "tntrun",
                "dragoneggcarnival", "snowball", "parkourwarrior", "hotycodydusky", "acerace", "daily", "vote"))
            outdatedConfiguration.set(section, null);
        outdatedConfiguration.set("no-permission", null);
        super.loadFromOutdatedConfiguration(outdatedConfiguration);
    }

    // Player
    @ConfigOption(path = "server-full")
    public static String SERVER_FULL;

    // Permission
    @ConfigOption(path = "no-permission")
    public static String NO_PERMISSION;

    // Command help / usage
    @ConfigOption(path = "command.help-header")
    public static String COMMAND_HELP_HEADER;

    @ConfigOption(path = "command.help-row")
    public static String COMMAND_HELP_ROW;

    @ConfigOption(path = "command.help-more")
    public static String COMMAND_HELP_MORE;

    @ConfigOption(path = "command.usage")
    public static String COMMAND_USAGE;

    @ConfigOption(path = "command.catalog-header")
    public static String COMMAND_CATALOG_HEADER;

    @ConfigOption(path = "command.catalog-player")
    public static String COMMAND_CATALOG_PLAYER;

    @ConfigOption(path = "command.catalog-admin")
    public static String COMMAND_CATALOG_ADMIN;

    @ConfigOption(path = "command.catalog-row")
    public static String COMMAND_CATALOG_ROW;

    // Free play
    @ConfigOption(path = "daily.prefix") public static String DAILY_PREFIX;
    @ConfigOption(path = "daily.mode.championship") public static String DAILY_MODE_CHAMPIONSHIP;
    @ConfigOption(path = "daily.mode.free-play") public static String DAILY_MODE_FREE_PLAY;
    @ConfigOption(path = "daily.state.idle") public static String DAILY_STATE_IDLE;
    @ConfigOption(path = "daily.state.selected") public static String DAILY_STATE_SELECTED;
    @ConfigOption(path = "daily.state.waiting-member") public static String DAILY_STATE_WAITING_MEMBER;
    @ConfigOption(path = "daily.state.queued") public static String DAILY_STATE_QUEUED;
    @ConfigOption(path = "daily.state.playing") public static String DAILY_STATE_PLAYING;
    @ConfigOption(path = "daily.team-name") public static String DAILY_TEAM_NAME;
    @ConfigOption(path = "daily.unavailable") public static String DAILY_UNAVAILABLE;
    @ConfigOption(path = "daily.already-playing") public static String DAILY_ALREADY_PLAYING;
    @ConfigOption(path = "daily.game-unavailable") public static String DAILY_GAME_UNAVAILABLE;
    @ConfigOption(path = "daily.party-too-large") public static String DAILY_PARTY_TOO_LARGE;
    @ConfigOption(path = "daily.party-member-unavailable") public static String DAILY_PARTY_MEMBER_UNAVAILABLE;
    @ConfigOption(path = "daily.already-queued") public static String DAILY_ALREADY_QUEUED;
    @ConfigOption(path = "daily.queue-unavailable") public static String DAILY_QUEUE_UNAVAILABLE;
    @ConfigOption(path = "daily.queue-migration-failed") public static String DAILY_QUEUE_MIGRATION_FAILED;
    @ConfigOption(path = "daily.queue-selected") public static String DAILY_QUEUE_SELECTED;
    @ConfigOption(path = "daily.queue-left") public static String DAILY_QUEUE_LEFT;
    @ConfigOption(path = "daily.not-in-play") public static String DAILY_NOT_IN_PLAY;
    @ConfigOption(path = "daily.play-left") public static String DAILY_PLAY_LEFT;
    @ConfigOption(path = "daily.queue-ready") public static String DAILY_QUEUE_READY;
    @ConfigOption(path = "daily.queue-countdown") public static String DAILY_QUEUE_COUNTDOWN;
    @ConfigOption(path = "daily.queue-composition-failed") public static String DAILY_QUEUE_COMPOSITION_FAILED;
    @ConfigOption(path = "daily.queue-no-arena") public static String DAILY_QUEUE_NO_ARENA;
    @ConfigOption(path = "daily.match-assigned") public static String DAILY_MATCH_ASSIGNED;
    @ConfigOption(path = "daily.match-aborted") public static String DAILY_MATCH_ABORTED;
    @ConfigOption(path = "daily.bossbar.waiting") public static String DAILY_BOSSBAR_WAITING;
    @ConfigOption(path = "daily.bossbar.countdown") public static String DAILY_BOSSBAR_COUNTDOWN;
    @ConfigOption(path = "daily.leaderboard.row-count") public static String DAILY_LEADERBOARD_ROW_COUNT;
    @ConfigOption(path = "daily.leaderboard.row-time") public static String DAILY_LEADERBOARD_ROW_TIME;
    @ConfigOption(path = "daily.leaderboard.empty") public static String DAILY_LEADERBOARD_EMPTY;

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

    // Area status
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

    // Rank
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

    @ConfigOption(path = "rank.game-weight-bar")
    public static String RANK_GAME_WEIGHT_BAR;

    @ConfigOption(path = "rank.game-weight-row")
    public static String RANK_GAME_WEIGHT_ROW;

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

    @ConfigOption(path = "game.parkourtag")
    public static String GAME_PARKOUR_TAG;

    @ConfigOption(path = "game.battlebox")
    public static String GAME_BATTLE_BOX;

    @ConfigOption(path = "game.tntrun")
    public static String GAME_TNT_RUN;

    @ConfigOption(path = "game.snowballsnowdown")
    public static String GAME_SNOWBALL_SNOW_DOWN;

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

    @ConfigOption(path = "game.buildmart", nullable = true)
    public static String GAME_BUILD_MART;

    @ConfigOption(path = "game.dodgebolt", nullable = true)
    public static String GAME_DODGEBOLT;

    @ConfigOption(path = "game.acerace", nullable = true)
    public static String GAME_ACE_RACE;

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

    // BattleBox
    @ConfigOption(path = "battlebox.start-preparation")
    public static String BATTLE_BOX_START_PREPARATION;

    @ConfigOption(path = "battlebox.start-preparation-title")
    public static String BATTLE_BOX_START_PREPARATION_TITLE;

    @ConfigOption(path = "battlebox.start-preparation-subtitle")
    public static String BATTLE_BOX_START_PREPARATION_SUBTITLE;

    @ConfigOption(path = "battlebox.game-start-soon-title")
    public static String BATTLE_BOX_GAME_START_SOON_TITLE;

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

    @ConfigOption(path = "battlebox.kits.armor")
    public static String BATTLE_BOX_KITS_ARMOR;

    @ConfigOption(path = "battlebox.kits.speed")
    public static String BATTLE_BOX_KITS_SPEED;

    @ConfigOption(path = "battlebox.kits.heal")
    public static String BATTLE_BOX_KITS_HEAL;

    @ConfigOption(path = "battlebox.kits.pull")
    public static String BATTLE_BOX_KITS_PULL;

    // ParkourTag
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

    // SkyWars
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

    @ConfigOption(path = "skywars.action-bar-count-down")
    public static String SKY_WARS_ACTION_BAR_COUNT_DOWN;

    @ConfigOption(path = "skywars.whole-team-was-killed")
    public static String SKY_WARS_WHOLE_TEAM_WAS_KILLED;

    @ConfigOption(path = "skywars.board-shrink")
    public static String SKY_WARS_BOARD_SHRINK;

    @ConfigOption(path = "skywars.board-shrink-count-down")
    public static String SKY_WARS_BOARD_SHRINK_COUNT_DOWN;

    @ConfigOption(path = "skywars.board-shrink-active")
    public static String SKY_WARS_BOARD_SHRINK_ACTIVE;

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

    @ConfigOption(path = "skywars.health-drain-active")
    public static String SKY_WARS_HEALTH_DRAIN_ACTIVE;

    @ConfigOption(path = "skywars.out-of-border")
    public static String SKY_WARS_OUT_OF_BORDER;

    // TGTTOS
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

    @ConfigOption(path = "tgttos.fall-into-void")
    public static String TGTTOS_FALL_INTO_VOID;

    // Ace Race
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

    // Bingo
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

    // Build Mart
    @ConfigOption(path = "buildmart.start-preparation", nullable = true)
    public static String BUILD_MART_START_PREPARATION;

    @ConfigOption(path = "buildmart.start-preparation-title", nullable = true)
    public static String BUILD_MART_START_PREPARATION_TITLE;

    @ConfigOption(path = "buildmart.start-preparation-subtitle", nullable = true)
    public static String BUILD_MART_START_PREPARATION_SUBTITLE;

    @ConfigOption(path = "buildmart.game-start-title", nullable = true)
    public static String BUILD_MART_GAME_START_TITLE;

    @ConfigOption(path = "buildmart.game-start-subtitle", nullable = true)
    public static String BUILD_MART_GAME_START_SUBTITLE;

    @ConfigOption(path = "buildmart.game-end-title", nullable = true)
    public static String BUILD_MART_GAME_END_TITLE;

    @ConfigOption(path = "buildmart.game-end-subtitle", nullable = true)
    public static String BUILD_MART_GAME_END_SUBTITLE;

    @ConfigOption(path = "buildmart.build-completed", nullable = true)
    public static String BUILD_MART_BUILD_COMPLETED;

    @ConfigOption(path = "buildmart.golden-build-completed", nullable = true)
    public static String BUILD_MART_GOLDEN_BUILD_COMPLETED;

    @ConfigOption(path = "buildmart.golden-refreshed", nullable = true)
    public static String BUILD_MART_GOLDEN_REFRESHED;

    @ConfigOption(path = "buildmart.golden-expired", nullable = true)
    public static String BUILD_MART_GOLDEN_EXPIRED;

    @ConfigOption(path = "buildmart.submit-incomplete", nullable = true)
    public static String BUILD_MART_SUBMIT_INCOMPLETE;

    @ConfigOption(path = "buildmart.submit-locked", nullable = true)
    public static String BUILD_MART_SUBMIT_LOCKED;

    @ConfigOption(path = "buildmart.golden-submit-failed", nullable = true)
    public static String BUILD_MART_GOLDEN_SUBMIT_FAILED;

    @ConfigOption(path = "buildmart.golden-submit-confirm", nullable = true)
    public static String BUILD_MART_GOLDEN_SUBMIT_CONFIRM;

    @ConfigOption(path = "buildmart.blueprint-auto-refreshed", nullable = true)
    public static String BUILD_MART_BLUEPRINT_AUTO_REFRESHED;

    @ConfigOption(path = "buildmart.award-entrepreneur", nullable = true)
    public static String BUILD_MART_AWARD_ENTREPRENEUR;

    @ConfigOption(path = "buildmart.award-chef", nullable = true)
    public static String BUILD_MART_AWARD_CHEF;

    @ConfigOption(path = "buildmart.award-quality", nullable = true)
    public static String BUILD_MART_AWARD_QUALITY;

    // Dodgebolt
    @ConfigOption(path = "dodgebolt.start-preparation", nullable = true)
    public static String DODGEBOLT_START_PREPARATION;
    @ConfigOption(path = "dodgebolt.start-preparation-title", nullable = true)
    public static String DODGEBOLT_START_PREPARATION_TITLE;
    @ConfigOption(path = "dodgebolt.start-preparation-subtitle", nullable = true)
    public static String DODGEBOLT_START_PREPARATION_SUBTITLE;
    @ConfigOption(path = "dodgebolt.game-start-soon-title", nullable = true)
    public static String DODGEBOLT_GAME_START_SOON_TITLE;
    @ConfigOption(path = "dodgebolt.game-start-title", nullable = true)
    public static String DODGEBOLT_GAME_START_TITLE;
    @ConfigOption(path = "dodgebolt.game-start-subtitle", nullable = true)
    public static String DODGEBOLT_GAME_START_SUBTITLE;
    @ConfigOption(path = "dodgebolt.game-end-title", nullable = true)
    public static String DODGEBOLT_GAME_END_TITLE;
    @ConfigOption(path = "dodgebolt.game-end-subtitle", nullable = true)
    public static String DODGEBOLT_GAME_END_SUBTITLE;
    @ConfigOption(path = "dodgebolt.hit", nullable = true)
    public static String DODGEBOLT_HIT;
    @ConfigOption(path = "dodgebolt.eliminated", nullable = true)
    public static String DODGEBOLT_ELIMINATED;
    @ConfigOption(path = "dodgebolt.shrink", nullable = true)
    public static String DODGEBOLT_SHRINK;
    @ConfigOption(path = "dodgebolt.shrink-warning", nullable = true)
    public static String DODGEBOLT_SHRINK_WARNING;
    @ConfigOption(path = "dodgebolt.round-win", nullable = true)
    public static String DODGEBOLT_ROUND_WIN;
    @ConfigOption(path = "dodgebolt.round-win-title", nullable = true)
    public static String DODGEBOLT_ROUND_WIN_TITLE;
    @ConfigOption(path = "dodgebolt.round-win-subtitle", nullable = true)
    public static String DODGEBOLT_ROUND_WIN_SUBTITLE;
    @ConfigOption(path = "dodgebolt.next-round", nullable = true)
    public static String DODGEBOLT_NEXT_ROUND;
    @ConfigOption(path = "dodgebolt.score-bar", nullable = true)
    public static String DODGEBOLT_SCORE_BAR;
    @ConfigOption(path = "dodgebolt.state-live", nullable = true)
    public static String DODGEBOLT_STATE_LIVE;
    @ConfigOption(path = "dodgebolt.state-paused", nullable = true)
    public static String DODGEBOLT_STATE_PAUSED;
    @ConfigOption(path = "dodgebolt.paused", nullable = true)
    public static String DODGEBOLT_PAUSED;
    @ConfigOption(path = "dodgebolt.paused-title", nullable = true)
    public static String DODGEBOLT_PAUSED_TITLE;
    @ConfigOption(path = "dodgebolt.paused-subtitle", nullable = true)
    public static String DODGEBOLT_PAUSED_SUBTITLE;
    @ConfigOption(path = "dodgebolt.resumed", nullable = true)
    public static String DODGEBOLT_RESUMED;
    @ConfigOption(path = "dodgebolt.resumed-title", nullable = true)
    public static String DODGEBOLT_RESUMED_TITLE;
    @ConfigOption(path = "dodgebolt.resumed-subtitle", nullable = true)
    public static String DODGEBOLT_RESUMED_SUBTITLE;
    @ConfigOption(path = "dodgebolt.round-restarted", nullable = true)
    public static String DODGEBOLT_ROUND_RESTARTED;
    @ConfigOption(path = "dodgebolt.champion", nullable = true)
    public static String DODGEBOLT_CHAMPION;
    @ConfigOption(path = "dodgebolt.champion-title", nullable = true)
    public static String DODGEBOLT_CHAMPION_TITLE;
    @ConfigOption(path = "dodgebolt.champion-subtitle", nullable = true)
    public static String DODGEBOLT_CHAMPION_SUBTITLE;
    @ConfigOption(path = "dodgebolt.stopped", nullable = true)
    public static String DODGEBOLT_STOPPED;
    @ConfigOption(path = "dodgebolt.cant-shoot", nullable = true)
    public static String DODGEBOLT_CANT_SHOOT;
    @ConfigOption(path = "dodgebolt.cant-cross", nullable = true)
    public static String DODGEBOLT_CANT_CROSS;

    // TNT Run
    @ConfigOption(path = "tntrun.start-preparation")
    public static String TNT_RUN_START_PREPARATION;

    @ConfigOption(path = "tntrun.start-preparation-title")
    public static String TNT_RUN_START_PREPARATION_TITLE;

    @ConfigOption(path = "tntrun.start-preparation-subtitle")
    public static String TNT_RUN_START_PREPARATION_SUBTITLE;

    @ConfigOption(path = "tntrun.game-start-title")
    public static String TNT_RUN_GAME_START_TITLE;

    @ConfigOption(path = "tntrun.game-start-subtitle")
    public static String TNT_RUN_GAME_START_SUBTITLE;

    @ConfigOption(path = "tntrun.action-bar-count-down")
    public static String TNT_RUN_ACTION_BAR_COUNT_DOWN;

    @ConfigOption(path = "tntrun.game-end-title")
    public static String TNT_RUN_GAME_END_TITLE;

    @ConfigOption(path = "tntrun.game-end-subtitle")
    public static String TNT_RUN_GAME_END_SUBTITLE;

    @ConfigOption(path = "tntrun.fall-into-void")
    public static String TNT_RUN_FALL_INTO_VOID;

    @ConfigOption(path = "tntrun.tnt-rain")
    public static String TNT_RUN_TNT_RAIN;

    @ConfigOption(path = "tntrun.tnt-rain-count-down")
    public static String TNT_RUN_TNT_RAIN_COUNT_DOWN;

    @ConfigOption(path = "tntrun.tnt-rain-active")
    public static String TNT_RUN_TNT_RAIN_ACTIVE;

    // Dragon Egg Carnival
    @ConfigOption(path = "dragoneggcarnival.start-preparation")
    public static String DRAGON_EGG_CARNIVAL_START_PREPARATION;

    @ConfigOption(path = "dragoneggcarnival.start-preparation-title")
    public static String DRAGON_EGG_CARNIVAL_START_PREPARATION_TITLE;

    @ConfigOption(path = "dragoneggcarnival.start-preparation-subtitle")
    public static String DRAGON_EGG_CARNIVAL_START_PREPARATION_SUBTITLE;

    @ConfigOption(path = "dragoneggcarnival.game-start-soon-title")
    public static String DRAGON_EGG_CARNIVAL_GAME_START_SOON_TITLE;

    @ConfigOption(path = "dragoneggcarnival.game-start-title")
    public static String DRAGON_EGG_CARNIVAL_GAME_START_TITLE;

    @ConfigOption(path = "dragoneggcarnival.game-start-subtitle")
    public static String DRAGON_EGG_CARNIVAL_GAME_START_SUBTITLE;

    @ConfigOption(path = "dragoneggcarnival.action-bar-count-down")
    public static String DRAGON_EGG_CARNIVAL_ACTION_BAR_COUNT_DOWN;

    @ConfigOption(path = "dragoneggcarnival.game-end-title")
    public static String DRAGON_EGG_CARNIVAL_GAME_END_TITLE;

    @ConfigOption(path = "dragoneggcarnival.game-end-subtitle")
    public static String DRAGON_EGG_CARNIVAL_GAME_END_SUBTITLE;

    @ConfigOption(path = "dragoneggcarnival.kill-player")
    public static String DRAGON_EGG_CARNIVAL_KILL_PLAYER;

    @ConfigOption(path = "dragoneggcarnival.kill-player-by-void")
    public static String DRAGON_EGG_CARNIVAL_KILL_PLAYER_BY_VOID;

    @ConfigOption(path = "dragoneggcarnival.player-death")
    public static String DRAGON_EGG_CARNIVAL_PLAYER_DEATH;

    @ConfigOption(path = "dragoneggcarnival.player-death-by-void")
    public static String DRAGON_EGG_CARNIVAL_PLAYER_DEATH_BY_VOID;

    @ConfigOption(path = "dragoneggcarnival.player-leave")
    public static String DRAGON_EGG_CARNIVAL_PLAYER_LEAVE;

    @ConfigOption(path = "dragoneggcarnival.out-of-border")
    public static String DRAGON_EGG_CARNIVAL_OUT_OF_BORDER;

    @ConfigOption(path = "dragoneggcarnival.win")
    public static String DRAGON_EGG_CARNIVAL_WIN;

    @ConfigOption(path = "dragoneggcarnival.respawn-countdown")
    public static String DRAGON_EGG_CARNIVAL_RESPAWN_COUNTDOWN;

    @ConfigOption(path = "dragoneggcarnival.respawned")
    public static String DRAGON_EGG_CARNIVAL_RESPAWNED;

    @ConfigOption(path = "dragoneggcarnival.advancement")
    public static String DRAGON_EGG_CARNIVAL_ADVANCEMENT;

    @ConfigOption(path = "dragoneggcarnival.crystal-reward")
    public static String DRAGON_EGG_CARNIVAL_CRYSTAL_REWARD;

    @ConfigOption(path = "dragoneggcarnival.dragon-pressure")
    public static String DRAGON_EGG_CARNIVAL_DRAGON_PRESSURE;

    // Snowball
    @ConfigOption(path = "snowball.start-preparation")
    public static String SNOWBALL_START_PREPARATION;

    @ConfigOption(path = "snowball.start-preparation-title")
    public static String SNOWBALL_START_PREPARATION_TITLE;

    @ConfigOption(path = "snowball.start-preparation-subtitle")
    public static String SNOWBALL_START_PREPARATION_SUBTITLE;

    @ConfigOption(path = "snowball.game-start-soon-title")
    public static String SNOWBALL_GAME_START_SOON_TITLE;

    @ConfigOption(path = "snowball.game-start-title")
    public static String SNOWBALL_GAME_START_TITLE;

    @ConfigOption(path = "snowball.game-start-subtitle")
    public static String SNOWBALL_GAME_START_SUBTITLE;

    @ConfigOption(path = "snowball.game-end-title")
    public static String SNOWBALL_GAME_END_TITLE;

    @ConfigOption(path = "snowball.game-end-subtitle")
    public static String SNOWBALL_GAME_END_SUBTITLE;

    @ConfigOption(path = "snowball.action-bar-count-down")
    public static String SNOWBALL_ACTION_BAR_COUNT_DOWN;

    @ConfigOption(path = "snowball.kill-player")
    public static String SNOWBALL_KILL_PLAYER;

    @ConfigOption(path = "snowball.player-death")
    public static String SNOWBALL_PLAYER_DEATH;

    @ConfigOption(path = "snowball.player-death-by-void")
    public static String SNOWBALL_PLAYER_DEATH_BY_VOID;

    @ConfigOption(path = "snowball.player-leave")
    public static String SNOWBALL_PLAYER_LEAVE;

    // Parkour Warrior
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

    // Hoty Cody Dusky
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

    // Placeholder
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

    // Vote
    @ConfigOption(path = "vote.start-vote")
    public static String VOTE_START_VOTE;

    @ConfigOption(path = "vote.start-vote-title")
    public static String VOTE_START_VOTE_TITLE;

    @ConfigOption(path = "vote.start-vote-subtitle")
    public static String VOTE_START_VOTE_SUBTITLE;

    @ConfigOption(path = "vote.end-vote")
    public static String VOTE_END_VOTE;

    @ConfigOption(path = "vote.player-vote")
    public static String VOTE_PLAYER_VOTE;

    @ConfigOption(path = "vote.vote-failed-not-time")
    public static String VOTE_VOTE_FAILED_NOT_TIME;

    @ConfigOption(path = "vote.vote-failed-not-game")
    public static String VOTE_VOTE_FAILED_NOT_GAME;

    @ConfigOption(path = "vote.vote-failed-already-played")
    public static String VOTE_VOTE_FAILED_ALREADY_PLAYED;

    @ConfigOption(path = "vote.vote-failed-not-player")
    public static String VOTE_VOTE_FAILED_NOT_PLAYER;

    @ConfigOption(path = "vote.vote-board-row")
    public static String VOTE_VOTE_BOARD_ROW;

    @ConfigOption(path = "vote.boss-bar")
    public static String VOTE_BOSS_BAR;

    @ConfigOption(path = "vote.not-voted")
    public static String VOTE_NOT_VOTED;

    @ConfigOption(path = "vote.end-vote-title")
    public static String VOTE_END_VOTE_TITLE;

    @ConfigOption(path = "vote.end-vote-subtitle")
    public static String VOTE_END_VOTE_SUBTITLE;
}
