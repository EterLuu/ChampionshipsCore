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
        return 39;
    }

    /** Replace every player-facing section so v30's terminology and spacing stay consistent at runtime. */
    @Override
    public void loadFromOutdatedConfiguration(@NotNull YamlConfiguration outdatedConfiguration) throws IOException {
        for (String section : List.of("command", "chat", "team", "member", "reason", "area", "rank", "spectator", "game",
                "battlebox", "parkourtag", "skywars", "tgttos", "bingo", "buildmart", "tntrun",
                "dragoneggcarnival", "snowball", "parkourwarrior", "hotycodydusky", "acerace", "daily", "vote"))
            outdatedConfiguration.set(section, null);
        outdatedConfiguration.set("no-permission", null);
        super.loadFromOutdatedConfiguration(outdatedConfiguration);
    }

    // Player
    @ConfigOption(path = "server-full")
    public static String SERVER_FULL;

    // Authentication / identity
    @ConfigOption(path = "auth.identity-verification-failed")
    public static String IDENTITY_VERIFICATION_FAILED;

    // Permission
    @ConfigOption(path = "no-permission")
    public static String NO_PERMISSION;

    // Shared command feedback
    @ConfigOption(path = "command.player-only")
    public static String COMMAND_PLAYER_ONLY;

    @ConfigOption(path = "command.unknown-game")
    public static String COMMAND_UNKNOWN_GAME;

    @ConfigOption(path = "command.daily-stats")
    public static String COMMAND_DAILY_STATS;

    // Spawn feedback
    @ConfigOption(path = "spawn.in-game-or-spectating")
    public static String SPAWN_IN_GAME_OR_SPECTATING;

    @ConfigOption(path = "spawn.missing")
    public static String SPAWN_MISSING;

    // TNT Run preparation
    @ConfigOption(path = "map-editor-tntrun.track-boundary-set")
    public static String MAP_EDITOR_TNT_TRACK_BOUNDARY_SET;

    @ConfigOption(path = "map-editor-tntrun.spawn-set")
    public static String MAP_EDITOR_TNT_SPAWN_SET;

    // SkyWars preparation
    @ConfigOption(path = "map-editor-skywars.map-boundaries-set")
    public static String MAP_EDITOR_SW_MAP_BOUNDARIES_SET;

    @ConfigOption(path = "map-editor-skywars.border-center-set")
    public static String MAP_EDITOR_SW_BORDER_CENTER_SET;

    // TGTTOS preparation
    @ConfigOption(path = "map-editor-tgttos.area-type-set")
    public static String MAP_EDITOR_TGT_AREA_TYPE_SET;

    @ConfigOption(path = "map-editor-tgttos.spawn-area-flat")
    public static String MAP_EDITOR_TGT_SPAWN_AREA_FLAT;

    @ConfigOption(path = "map-editor-tgttos.chicken-spawn-set")
    public static String MAP_EDITOR_TGT_CHICKEN_SPAWN_SET;

    @ConfigOption(path = "map-editor-tgttos.player-spawn-set")
    public static String MAP_EDITOR_TGT_PLAYER_SPAWN_SET;

    // Dodgebolt preparation
    @ConfigOption(path = "map-editor-dodgebolt.step-set")
    public static String MAP_EDITOR_DODGEBOLT_STEP_SET;

    @ConfigOption(path = "map-editor-dodgebolt.count-required")
    public static String MAP_EDITOR_DODGEBOLT_COUNT_REQUIRED;

    @ConfigOption(path = "map-editor-dodgebolt.point-unset")
    public static String MAP_EDITOR_DODGEBOLT_POINT_UNSET;

    @ConfigOption(path = "map-editor-dodgebolt.spectator-inside")
    public static String MAP_EDITOR_DODGEBOLT_SPECTATOR_INSIDE;

    @ConfigOption(path = "map-editor-dodgebolt.spectator-outside")
    public static String MAP_EDITOR_DODGEBOLT_SPECTATOR_OUTSIDE;

    // Parkour Tag preparation
    @ConfigOption(path = "map-editor-parkourtag.chaser-aim")
    public static String MAP_EDITOR_TAG_CHASER_AIM;

    @ConfigOption(path = "map-editor-parkourtag.world-not-loaded")
    public static String MAP_EDITOR_TAG_WORLD_NOT_LOADED;

    @ConfigOption(path = "map-editor-parkourtag.invalid-position")
    public static String MAP_EDITOR_TAG_INVALID_POSITION;

    @ConfigOption(path = "map-editor-parkourtag.missing-wall")
    public static String MAP_EDITOR_TAG_MISSING_WALL;

    @ConfigOption(path = "map-editor-parkourtag.missing-space")
    public static String MAP_EDITOR_TAG_MISSING_SPACE;

    @ConfigOption(path = "map-editor-parkourtag.missing-sign-wall")
    public static String MAP_EDITOR_TAG_MISSING_SIGN_WALL;

    @ConfigOption(path = "map-editor-parkourtag.chaser-buttons-set")
    public static String MAP_EDITOR_TAG_CHASER_BUTTONS_SET;

    @ConfigOption(path = "map-editor-parkourtag.sign-click")
    public static String MAP_EDITOR_TAG_SIGN_CLICK;

    @ConfigOption(path = "map-editor-parkourtag.sign-become")
    public static String MAP_EDITOR_TAG_SIGN_BECOME;

    @ConfigOption(path = "map-editor-parkourtag.prepare-a-set")
    public static String MAP_EDITOR_TAG_PREPARE_A_SET;

    @ConfigOption(path = "map-editor-parkourtag.prepare-b-set")
    public static String MAP_EDITOR_TAG_PREPARE_B_SET;

    @ConfigOption(path = "map-editor-parkourtag.track-1-boundary-set")
    public static String MAP_EDITOR_TAG_TRACK_1_BOUNDARY_SET;

    @ConfigOption(path = "map-editor-parkourtag.track-1-chaser-set")
    public static String MAP_EDITOR_TAG_TRACK_1_CHASER_SET;

    @ConfigOption(path = "map-editor-parkourtag.track-2-boundary-set")
    public static String MAP_EDITOR_TAG_TRACK_2_BOUNDARY_SET;

    @ConfigOption(path = "map-editor-parkourtag.track-2-chaser-set")
    public static String MAP_EDITOR_TAG_TRACK_2_CHASER_SET;

    // Battle Box preparation
    @ConfigOption(path = "map-editor-battlebox.spawn-right-set")
    public static String MAP_EDITOR_BB_SPAWN_RIGHT_SET;

    @ConfigOption(path = "map-editor-battlebox.spawn-left-set")
    public static String MAP_EDITOR_BB_SPAWN_LEFT_SET;

    @ConfigOption(path = "map-editor-battlebox.prepare-right-set")
    public static String MAP_EDITOR_BB_PREPARE_RIGHT_SET;

    @ConfigOption(path = "map-editor-battlebox.prepare-left-set")
    public static String MAP_EDITOR_BB_PREPARE_LEFT_SET;

    @ConfigOption(path = "map-editor-battlebox.wool-set")
    public static String MAP_EDITOR_BB_WOOL_SET;

    // Dragon Egg Carnival preparation
    @ConfigOption(path = "map-editor-decarnival.world-environment")
    public static String MAP_EDITOR_DEC_WORLD_ENVIRONMENT;

    @ConfigOption(path = "map-editor-decarnival.fight-region")
    public static String MAP_EDITOR_DEC_FIGHT_REGION;

    @ConfigOption(path = "map-editor-decarnival.spectator-world")
    public static String MAP_EDITOR_DEC_SPECTATOR_WORLD;

    // Daily party feedback
    @ConfigOption(path = "map-editor-daily-party.invitation-cannot-accept")
    public static String MAP_EDITOR_DAILY_PARTY_INVITATION_CANNOT_ACCEPT;

    @ConfigOption(path = "map-editor-daily-party.member-joined")
    public static String MAP_EDITOR_DAILY_PARTY_MEMBER_JOINED;

    @ConfigOption(path = "map-editor-daily-party.click-online-to-invite")
    public static String MAP_EDITOR_DAILY_PARTY_CLICK_ONLINE_TO_INVITE;

    @ConfigOption(path = "map-editor-daily-party.party-disbanded")
    public static String MAP_EDITOR_DAILY_PARTY_DISBANDED;

    @ConfigOption(path = "map-editor-daily-party.party-left")
    public static String MAP_EDITOR_DAILY_PARTY_LEFT;

    @ConfigOption(path = "map-editor-daily-party.party-cannot-modify")
    public static String MAP_EDITOR_DAILY_PARTY_CANNOT_MODIFY;

    @ConfigOption(path = "map-editor-daily-party.player-cannot-invite")
    public static String MAP_EDITOR_DAILY_PARTY_PLAYER_CANNOT_INVITE;

    @ConfigOption(path = "map-editor-daily-party.invitation-sent")
    public static String MAP_EDITOR_DAILY_PARTY_INVITATION_SENT;

    @ConfigOption(path = "map-editor-daily-party.invited-by")
    public static String MAP_EDITOR_DAILY_PARTY_INVITED_BY;

    // Bingo teammate teleport feedback
    @ConfigOption(path = "map-editor-bingo-teleport.no-teammates")
    public static String MAP_EDITOR_BINGO_NO_TEAMMATES;

    @ConfigOption(path = "map-editor-bingo-teleport.unreachable")
    public static String MAP_EDITOR_BINGO_UNREACHABLE;

    @ConfigOption(path = "map-editor-bingo-teleport.sent-to")
    public static String MAP_EDITOR_BINGO_SENT_TO;

    // Spectator feedback
    @ConfigOption(path = "map-editor-spectator.unavailable")
    public static String SPECTATOR_UNAVAILABLE;

    @ConfigOption(path = "map-editor-spectator.visibility-all")
    public static String SPECTATOR_VISIBILITY_ALL;

    @ConfigOption(path = "map-editor-spectator.visibility-player")
    public static String SPECTATOR_VISIBILITY_PLAYER;

    @ConfigOption(path = "map-editor-spectator.visibility-team")
    public static String SPECTATOR_VISIBILITY_TEAM;

    @ConfigOption(path = "map-editor-spectator.left")
    public static String SPECTATOR_LEFT;

    @ConfigOption(path = "map-editor-spectator.night-vision-enabled")
    public static String SPECTATOR_NIGHT_VISION_ENABLED;

    @ConfigOption(path = "map-editor-spectator.night-vision-disabled")
    public static String SPECTATOR_NIGHT_VISION_DISABLED;

    @ConfigOption(path = "map-editor-spectator.teleported-to-player")
    public static String SPECTATOR_TELEPORTED_TO_PLAYER;

    @ConfigOption(path = "map-editor-spectator.flight-speed")
    public static String SPECTATOR_FLIGHT_SPEED;

    // Build Mart preparation
    @ConfigOption(path = "map-editor-buildmart.save-failed")
    public static String MAP_EDITOR_BUILD_SAVE_FAILED;

    @ConfigOption(path = "map-editor-buildmart.zone-deleted")
    public static String MAP_EDITOR_BUILD_ZONE_DELETED;

    @ConfigOption(path = "map-editor-buildmart.zones-cleared")
    public static String MAP_EDITOR_BUILD_ZONES_CLEARED;

    @ConfigOption(path = "map-editor-buildmart.centers-incomplete")
    public static String MAP_EDITOR_BUILD_CENTERS_INCOMPLETE;

    @ConfigOption(path = "map-editor-buildmart.zone-volume-invalid")
    public static String MAP_EDITOR_BUILD_ZONE_VOLUME_INVALID;

    @ConfigOption(path = "map-editor-buildmart.zone-saved")
    public static String MAP_EDITOR_BUILD_ZONE_SAVED;

    @ConfigOption(path = "map-editor-buildmart.zone-updated")
    public static String MAP_EDITOR_BUILD_ZONE_UPDATED;

    @ConfigOption(path = "map-editor-buildmart.snapshot-failed")
    public static String MAP_EDITOR_BUILD_SNAPSHOT_FAILED;

    @ConfigOption(path = "map-editor-buildmart.base-count-positive")
    public static String MAP_EDITOR_BUILD_BASE_COUNT_POSITIVE;

    @ConfigOption(path = "map-editor-buildmart.base-template-missing")
    public static String MAP_EDITOR_BUILD_BASE_TEMPLATE_MISSING;

    @ConfigOption(path = "map-editor-buildmart.world-not-loaded")
    public static String MAP_EDITOR_BUILD_WORLD_NOT_LOADED;

    @ConfigOption(path = "map-editor-buildmart.instance-running")
    public static String MAP_EDITOR_BUILD_INSTANCE_RUNNING;

    @ConfigOption(path = "map-editor-buildmart.hub-missing")
    public static String MAP_EDITOR_BUILD_HUB_MISSING;

    @ConfigOption(path = "map-editor-buildmart.base-origin-missing")
    public static String MAP_EDITOR_BUILD_BASE_ORIGIN_MISSING;

    @ConfigOption(path = "map-editor-buildmart.generate-failed")
    public static String MAP_EDITOR_BUILD_GENERATE_FAILED;

    @ConfigOption(path = "map-editor-buildmart.base-generated")
    public static String MAP_EDITOR_BUILD_BASE_GENERATED;

    @ConfigOption(path = "map-editor-buildmart.floor-select-first")
    public static String MAP_EDITOR_BUILD_FLOOR_SELECT_FIRST;

    @ConfigOption(path = "map-editor-buildmart.floor-invalid")
    public static String MAP_EDITOR_BUILD_FLOOR_INVALID;

    @ConfigOption(path = "map-editor-buildmart.floor-recorded")
    public static String MAP_EDITOR_BUILD_FLOOR_RECORDED;

    @ConfigOption(path = "map-editor-buildmart.portal-set")
    public static String MAP_EDITOR_BUILD_PORTAL_SET;

    @ConfigOption(path = "map-editor-buildmart.wind-select-first")
    public static String MAP_EDITOR_BUILD_WIND_SELECT_FIRST;

    @ConfigOption(path = "map-editor-buildmart.wind-added")
    public static String MAP_EDITOR_BUILD_WIND_ADDED;

    @ConfigOption(path = "map-editor-buildmart.wind-cleared")
    public static String MAP_EDITOR_BUILD_WIND_CLEARED;

    @ConfigOption(path = "map-editor-buildmart.wind-updated")
    public static String MAP_EDITOR_BUILD_WIND_UPDATED;

    @ConfigOption(path = "map-editor-buildmart.wind-adjusted")
    public static String MAP_EDITOR_BUILD_WIND_ADJUSTED;

    // Ace Race preparation
    @ConfigOption(path = "map-editor-acerace.line-select-first")
    public static String MAP_EDITOR_ACE_LINE_SELECT_FIRST;

    @ConfigOption(path = "map-editor-acerace.line-invalid")
    public static String MAP_EDITOR_ACE_LINE_INVALID;

    @ConfigOption(path = "map-editor-acerace.start-fall-input")
    public static String MAP_EDITOR_ACE_START_FALL_INPUT;

    @ConfigOption(path = "map-editor-acerace.start-fall-set")
    public static String MAP_EDITOR_ACE_START_FALL_SET;

    @ConfigOption(path = "map-editor-acerace.start-line-set")
    public static String MAP_EDITOR_ACE_START_LINE_SET;

    @ConfigOption(path = "map-editor-acerace.finish-line-set")
    public static String MAP_EDITOR_ACE_FINISH_LINE_SET;

    @ConfigOption(path = "map-editor-acerace.preview-area-missing")
    public static String MAP_EDITOR_ACE_PREVIEW_AREA_MISSING;

    @ConfigOption(path = "map-editor-acerace.preview-enabled")
    public static String MAP_EDITOR_ACE_PREVIEW_ENABLED;

    @ConfigOption(path = "map-editor-acerace.preview-disabled")
    public static String MAP_EDITOR_ACE_PREVIEW_DISABLED;

    @ConfigOption(path = "map-editor-acerace.progress-fall-input")
    public static String MAP_EDITOR_ACE_PROGRESS_FALL_INPUT;

    @ConfigOption(path = "map-editor-acerace.progress-added")
    public static String MAP_EDITOR_ACE_PROGRESS_ADDED;

    @ConfigOption(path = "map-editor-acerace.progress-edit-input")
    public static String MAP_EDITOR_ACE_PROGRESS_EDIT_INPUT;

    @ConfigOption(path = "map-editor-acerace.progress-missing")
    public static String MAP_EDITOR_ACE_PROGRESS_MISSING;

    @ConfigOption(path = "map-editor-acerace.progress-updated")
    public static String MAP_EDITOR_ACE_PROGRESS_UPDATED;

    @ConfigOption(path = "map-editor-acerace.progress-equipment-updated")
    public static String MAP_EDITOR_ACE_PROGRESS_EQUIPMENT_UPDATED;

    @ConfigOption(path = "map-editor-acerace.progress-deleted")
    public static String MAP_EDITOR_ACE_PROGRESS_DELETED;

    @ConfigOption(path = "map-editor-acerace.progress-adjusted")
    public static String MAP_EDITOR_ACE_PROGRESS_ADJUSTED;

    @ConfigOption(path = "map-editor-acerace.progress-select-first")
    public static String MAP_EDITOR_ACE_PROGRESS_SELECT_FIRST;

    @ConfigOption(path = "map-editor-acerace.progress-line-invalid")
    public static String MAP_EDITOR_ACE_PROGRESS_LINE_INVALID;

    @ConfigOption(path = "map-editor-acerace.respawn-bound")
    public static String MAP_EDITOR_ACE_RESPAWN_BOUND;

    @ConfigOption(path = "map-editor-step.step-set")
    public static String MAP_EDITOR_STEP_SET;

    @ConfigOption(path = "map-editor-step.point-added-current")
    public static String MAP_EDITOR_STEP_POINT_ADDED_CURRENT;

    @ConfigOption(path = "map-editor-step.point-list-cleared")
    public static String MAP_EDITOR_STEP_POINT_LIST_CLEARED;

    @ConfigOption(path = "map-editor-step.introduction-spawn-set")
    public static String MAP_EDITOR_STEP_INTRODUCTION_SPAWN_SET;

    @ConfigOption(path = "map-editor-step.world-confirm-prompt")
    public static String MAP_EDITOR_STEP_WORLD_CONFIRM_PROMPT;

    // Map editor step feedback

    @ConfigOption(path = "map-editor-step.deleted")
    public static String MAP_EDITOR_STEP_DELETED;

    @ConfigOption(path = "map-editor-step.general-site-boundary-set")
    public static String MAP_EDITOR_STEP_GENERAL_SITE_BOUNDARY_SET;

    @ConfigOption(path = "map-editor-step.player-spawn-point-set")
    public static String MAP_EDITOR_STEP_PLAYER_SPAWN_POINT_SET;

    @ConfigOption(path = "map-editor-step.go-to-map-world-first")
    public static String MAP_EDITOR_STEP_GO_TO_MAP_WORLD_FIRST;

    @ConfigOption(path = "map-editor-step.select-two-worldedit-endpoints")
    public static String MAP_EDITOR_STEP_SELECT_TWO_WORLDEDIT_ENDPOINTS;

    @ConfigOption(path = "map-editor-step.site-boundary-set")
    public static String MAP_EDITOR_STEP_SITE_BOUNDARY_SET;

    @ConfigOption(path = "map-editor-step.spectator-spawn-point-set")
    public static String MAP_EDITOR_STEP_SPECTATOR_SPAWN_POINT_SET;

    @ConfigOption(path = "map-editor-step.point-adjusted-to")
    public static String MAP_EDITOR_STEP_POINT_ADJUSTED_TO;

    @ConfigOption(path = "map-editor-step.serial-number-between")
    public static String MAP_EDITOR_STEP_SERIAL_NUMBER_BETWEEN;

    @ConfigOption(path = "map-editor-step.track-boundary-set")
    public static String MAP_EDITOR_STEP_TRACK_BOUNDARY_SET;

    @ConfigOption(path = "map-editor-step.updated")
    public static String MAP_EDITOR_STEP_UPDATED;

    @ConfigOption(path = "map-editor-step.countdown-blocks-closed")
    public static String MAP_EDITOR_STEP_COUNTDOWN_BLOCKS_CLOSED;

    @ConfigOption(path = "map-editor-step.countdown-blocks-mode-set")
    public static String MAP_EDITOR_STEP_COUNTDOWN_BLOCKS_MODE_SET;

    @ConfigOption(path = "map-editor-step.countdown-blocks-volume-between")
    public static String MAP_EDITOR_STEP_COUNTDOWN_BLOCKS_VOLUME_BETWEEN;

    @ConfigOption(path = "map-editor-step.countdown-blocks-set")
    public static String MAP_EDITOR_STEP_COUNTDOWN_BLOCKS_SET;

    @ConfigOption(path = "map-editor-step.checkpoint-select-first")
    public static String MAP_EDITOR_STEP_CHECKPOINT_SELECT_FIRST;

    @ConfigOption(path = "map-editor-step.checkpoint-added-current")
    public static String MAP_EDITOR_STEP_CHECKPOINT_ADDED_CURRENT;
    @ConfigOption(path = "map-editor-step.checkpoint-updated")
    public static String MAP_EDITOR_STEP_CHECKPOINT_UPDATED;
    @ConfigOption(path = "map-editor-step.checkpoint-deleted")
    public static String MAP_EDITOR_STEP_CHECKPOINT_DELETED;

    @ConfigOption(path = "map-editor-step.checkpoint-reselect")
    public static String MAP_EDITOR_STEP_CHECKPOINT_RESELECT;

    @ConfigOption(path = "map-editor-step.checkpoint-adjusted-to")
    public static String MAP_EDITOR_STEP_CHECKPOINT_ADJUSTED_TO;

    @ConfigOption(path = "map-editor-step.world-in-use")
    public static String MAP_EDITOR_STEP_WORLD_IN_USE;

    @ConfigOption(path = "map-editor-step.world-bound")
    public static String MAP_EDITOR_STEP_WORLD_BOUND;

    @ConfigOption(path = "map-editor-step.world-confirmed")
    public static String MAP_EDITOR_STEP_WORLD_CONFIRMED;

    @ConfigOption(path = "map-editor-step.go-to-editing-site")
    public static String MAP_EDITOR_STEP_GO_TO_EDITING_SITE;

    @ConfigOption(path = "map-editor-step.schematic-save-failed")
    public static String MAP_EDITOR_STEP_SCHEMATIC_SAVE_FAILED;

    @ConfigOption(path = "map-editor-step.schematic-saved")
    public static String MAP_EDITOR_STEP_SCHEMATIC_SAVED;

    @ConfigOption(path = "map-editor-step.selected-block-recorded")
    public static String MAP_EDITOR_STEP_SELECTED_BLOCK_RECORDED;

    @ConfigOption(path = "map-editor-step.select-one-block-first")
    public static String MAP_EDITOR_STEP_SELECT_ONE_BLOCK_FIRST;

    @ConfigOption(path = "map-editor-step.selection-must-be-one-block")
    public static String MAP_EDITOR_STEP_SELECTION_MUST_BE_ONE_BLOCK;

    @ConfigOption(path = "map-editor-step.arena-generate-failed")
    public static String MAP_EDITOR_STEP_ARENA_GENERATE_FAILED;

    @ConfigOption(path = "map-editor-step.arena-generated")
    public static String MAP_EDITOR_STEP_ARENA_GENERATED;

    @ConfigOption(path = "map-editor-step.arena-template-missing")
    public static String MAP_EDITOR_STEP_ARENA_TEMPLATE_MISSING;

    @ConfigOption(path = "map-editor-step.world-not-loaded")
    public static String MAP_EDITOR_STEP_WORLD_NOT_LOADED;

    @ConfigOption(path = "map-editor-step.arena-max-count")
    public static String MAP_EDITOR_STEP_ARENA_MAX_COUNT;

    @ConfigOption(path = "map-editor-step.arena-count-positive")
    public static String MAP_EDITOR_STEP_ARENA_COUNT_POSITIVE;

    @ConfigOption(path = "map-editor-step.arena-total-set")
    public static String MAP_EDITOR_STEP_ARENA_TOTAL_SET;

    @ConfigOption(path = "map-editor-step.arena-instance-running")
    public static String MAP_EDITOR_STEP_ARENA_INSTANCE_RUNNING;
    @ConfigOption(path = "map-editor-area.delete-confirmation")
    public static String MAP_EDITOR_AREA_DELETE_CONFIRMATION;
    @ConfigOption(path = "map-editor-input.invalid-number")
    public static String MAP_EDITOR_INPUT_INVALID_NUMBER;

    @ConfigOption(path = "map-editor-input.number-too-small")
    public static String MAP_EDITOR_INPUT_NUMBER_TOO_SMALL;

    @ConfigOption(path = "map-editor-input.invalid-integer")
    public static String MAP_EDITOR_INPUT_INVALID_INTEGER;

    @ConfigOption(path = "map-editor-input.name-empty")
    public static String MAP_EDITOR_INPUT_NAME_EMPTY;

    @ConfigOption(path = "map-editor-input.name-too-long")
    public static String MAP_EDITOR_INPUT_NAME_TOO_LONG;

    @ConfigOption(path = "map-editor-input.name-invalid")
    public static String MAP_EDITOR_INPUT_NAME_INVALID;

    @ConfigOption(path = "map-editor-input.name-already-exists")
    public static String MAP_EDITOR_INPUT_NAME_ALREADY_EXISTS;

    @ConfigOption(path = "map-editor-session.game-unavailable")
    public static String MAP_EDITOR_SESSION_GAME_UNAVAILABLE;
    @ConfigOption(path = "map-editor-session.create-conflict")
    public static String MAP_EDITOR_SESSION_CREATE_CONFLICT;
    @ConfigOption(path = "map-editor-session.delete-unavailable")
    public static String MAP_EDITOR_SESSION_DELETE_UNAVAILABLE;
    @ConfigOption(path = "map-editor-session.delete-failed")
    public static String MAP_EDITOR_SESSION_DELETE_FAILED;
    @ConfigOption(path = "map-editor-session.deleted")
    public static String MAP_EDITOR_SESSION_DELETED;
    @ConfigOption(path = "map-editor-session.unsupported-game")
    public static String MAP_EDITOR_SESSION_UNSUPPORTED_GAME;
    @ConfigOption(path = "map-editor-session.map-missing")
    public static String MAP_EDITOR_SESSION_MAP_MISSING;
    @ConfigOption(path = "map-editor-session.locked-by-editor")
    public static String MAP_EDITOR_SESSION_LOCKED_BY_EDITOR;
    @ConfigOption(path = "map-editor-session.entered")
    public static String MAP_EDITOR_SESSION_ENTERED;
    @ConfigOption(path = "map-editor-session.usage-hint")
    public static String MAP_EDITOR_SESSION_USAGE_HINT;
    @ConfigOption(path = "map-editor-session.exited")
    public static String MAP_EDITOR_SESSION_EXITED;
    @ConfigOption(path = "map-editor-session.go-to-world")
    public static String MAP_EDITOR_SESSION_GO_TO_WORLD;
    @ConfigOption(path = "map-editor-session.publish-instance-running")
    public static String MAP_EDITOR_SESSION_PUBLISH_INSTANCE_RUNNING;
    @ConfigOption(path = "map-editor-session.publish-started")
    public static String MAP_EDITOR_SESSION_PUBLISH_STARTED;
    @ConfigOption(path = "map-editor-session.save-instance-running")
    public static String MAP_EDITOR_SESSION_SAVE_INSTANCE_RUNNING;
    @ConfigOption(path = "map-editor-session.save-started")
    public static String MAP_EDITOR_SESSION_SAVE_STARTED;
    @ConfigOption(path = "map-editor-session.save-failed")
    public static String MAP_EDITOR_SESSION_SAVE_FAILED;
    @ConfigOption(path = "map-editor-session.saved")
    public static String MAP_EDITOR_SESSION_SAVED;
    @ConfigOption(path = "map-editor-session.publish-failed")
    public static String MAP_EDITOR_SESSION_PUBLISH_FAILED;
    @ConfigOption(path = "map-editor-session.published")
    public static String MAP_EDITOR_SESSION_PUBLISHED;
    @ConfigOption(path = "map-editor-session.validation-passed-publish")
    public static String MAP_EDITOR_SESSION_VALIDATION_PASSED_PUBLISH;
    @ConfigOption(path = "map-editor-session.validation-passed")
    public static String MAP_EDITOR_SESSION_VALIDATION_PASSED;
    @ConfigOption(path = "map-editor-session.validation-failed")
    public static String MAP_EDITOR_SESSION_VALIDATION_FAILED;
    @ConfigOption(path = "map-editor-session.validation-error")
    public static String MAP_EDITOR_SESSION_VALIDATION_ERROR;
    @ConfigOption(path = "map-editor-session.world-not-bound")
    public static String MAP_EDITOR_SESSION_WORLD_NOT_BOUND;
    @ConfigOption(path = "map-editor-session.world-not-loaded")
    public static String MAP_EDITOR_SESSION_WORLD_NOT_LOADED;
    @ConfigOption(path = "map-editor-session.location-missing")
    public static String MAP_EDITOR_SESSION_LOCATION_MISSING;
    @ConfigOption(path = "map-editor-session.teleport-failed")
    public static String MAP_EDITOR_SESSION_TELEPORT_FAILED;
    @ConfigOption(path = "map-editor-session.teleported")
    public static String MAP_EDITOR_SESSION_TELEPORTED;
    @ConfigOption(path = "map-editor-session.snapshot-restored")
    public static String MAP_EDITOR_SESSION_SNAPSHOT_RESTORED;

    @ConfigOption(path = "map-editor-rename.already-running")
    public static String MAP_EDITOR_RENAME_ALREADY_RUNNING;
    @ConfigOption(path = "map-editor-rename.no-manager")
    public static String MAP_EDITOR_RENAME_NO_MANAGER;
    @ConfigOption(path = "map-editor-rename.source-missing")
    public static String MAP_EDITOR_RENAME_SOURCE_MISSING;
    @ConfigOption(path = "map-editor-rename.source-not-loaded")
    public static String MAP_EDITOR_RENAME_SOURCE_NOT_LOADED;
    @ConfigOption(path = "map-editor-rename.invalid-name")
    public static String MAP_EDITOR_RENAME_INVALID_NAME;
    @ConfigOption(path = "map-editor-rename.invalid-relative")
    public static String MAP_EDITOR_RENAME_INVALID_RELATIVE;
    @ConfigOption(path = "map-editor-rename.same-name")
    public static String MAP_EDITOR_RENAME_SAME_NAME;
    @ConfigOption(path = "map-editor-rename.target-exists")
    public static String MAP_EDITOR_RENAME_TARGET_EXISTS;
    @ConfigOption(path = "map-editor-rename.prepare-active")
    public static String MAP_EDITOR_RENAME_PREPARE_ACTIVE;
    @ConfigOption(path = "map-editor-rename.event-running")
    public static String MAP_EDITOR_RENAME_EVENT_RUNNING;
    @ConfigOption(path = "map-editor-rename.area-running")
    public static String MAP_EDITOR_RENAME_AREA_RUNNING;
    @ConfigOption(path = "map-editor-rename.target-config-exists")
    public static String MAP_EDITOR_RENAME_TARGET_CONFIG_EXISTS;
    @ConfigOption(path = "map-editor-rename.detach-failed")
    public static String MAP_EDITOR_RENAME_DETACH_FAILED;
    @ConfigOption(path = "map-editor-rename.detach-waiting")
    public static String MAP_EDITOR_RENAME_DETACH_WAITING;
    @ConfigOption(path = "map-editor-rename.pending-sync-failed")
    public static String MAP_EDITOR_RENAME_PENDING_SYNC_FAILED;
    @ConfigOption(path = "map-editor-rename.new-area-load-failed")
    public static String MAP_EDITOR_RENAME_NEW_AREA_LOAD_FAILED;
    @ConfigOption(path = "map-editor-rename.completed")
    public static String MAP_EDITOR_RENAME_COMPLETED;
    @ConfigOption(path = "map-editor-rename.failed-restored")
    public static String MAP_EDITOR_RENAME_FAILED_RESTORED;

    @ConfigOption(path = "finale.direct-start-invalid")
    public static String FINALE_DIRECT_START_INVALID;
    @ConfigOption(path = "finale.direct-start-started")
    public static String FINALE_DIRECT_START_STARTED;
    @ConfigOption(path = "finale.direct-start-started-forced")
    public static String FINALE_DIRECT_START_STARTED_FORCED;
    @ConfigOption(path = "finale.direct-start-failed")
    public static String FINALE_DIRECT_START_FAILED;
    @ConfigOption(path = "finale.direct-start-forced-failed")
    public static String FINALE_DIRECT_START_FORCED_FAILED;
    @ConfigOption(path = "finale.start-team-required")
    public static String FINALE_START_TEAM_REQUIRED;
    @ConfigOption(path = "finale.cancelled")
    public static String FINALE_CANCELLED;
    @ConfigOption(path = "finale.not-running")
    public static String FINALE_NOT_RUNNING;
    @ConfigOption(path = "finale.dodgebolt.area-missing")
    public static String FINALE_DODGEBOLT_AREA_MISSING;
    @ConfigOption(path = "finale.dodgebolt.control-executed")
    public static String FINALE_DODGEBOLT_CONTROL_EXECUTED;
    @ConfigOption(path = "finale.dodgebolt.control-state-denied")
    public static String FINALE_DODGEBOLT_CONTROL_STATE_DENIED;
    @ConfigOption(path = "finale.dodgebolt.elimination-invalid")
    public static String FINALE_DODGEBOLT_ELIMINATION_INVALID;
    @ConfigOption(path = "finale.dodgebolt.eliminated")
    public static String FINALE_DODGEBOLT_ELIMINATED;
    @ConfigOption(path = "finale.dodgebolt.force-win-invalid")
    public static String FINALE_DODGEBOLT_FORCE_WIN_INVALID;
    @ConfigOption(path = "finale.dodgebolt.force-win-set")
    public static String FINALE_DODGEBOLT_FORCE_WIN_SET;

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

    @ConfigOption(path = "schedule-finale.game-not-registered")
    public static String SCHEDULE_FINALE_GAME_NOT_REGISTERED;
    @ConfigOption(path = "schedule-finale.game-disabled")
    public static String SCHEDULE_FINALE_GAME_DISABLED;
    @ConfigOption(path = "schedule-finale.partial-roster-unsupported")
    public static String SCHEDULE_FINALE_PARTIAL_ROSTER_UNSUPPORTED;
    @ConfigOption(path = "schedule-finale.emergency-stopped")
    public static String SCHEDULE_FINALE_EMERGENCY_STOPPED;
    @ConfigOption(path = "schedule-finale.other-running")
    public static String SCHEDULE_FINALE_OTHER_RUNNING;
    @ConfigOption(path = "schedule-finale.map-unavailable")
    public static String SCHEDULE_FINALE_MAP_UNAVAILABLE;
    @ConfigOption(path = "schedule-finale.start-not-implemented")
    public static String SCHEDULE_FINALE_START_NOT_IMPLEMENTED;
    @ConfigOption(path = "schedule-finale.auto-finalists-unavailable")
    public static String SCHEDULE_FINALE_AUTO_FINALISTS_UNAVAILABLE;
    @ConfigOption(path = "schedule-finale.tie-break-required")
    public static String SCHEDULE_FINALE_TIE_BREAK_REQUIRED;
    @ConfigOption(path = "schedule-finale.teams-must-differ")
    public static String SCHEDULE_FINALE_TEAMS_MUST_DIFFER;
    @ConfigOption(path = "schedule-finale.dragon-egg.scheduled")
    public static String SCHEDULE_FINALE_DRAGON_EGG_SCHEDULED;
    @ConfigOption(path = "schedule-finale.dragon-egg.start-failed")
    public static String SCHEDULE_FINALE_DRAGON_EGG_START_FAILED;
    @ConfigOption(path = "schedule-finale.dodgebolt.tie-seed")
    public static String SCHEDULE_FINALE_DODGEBOLT_TIE_SEED;
    @ConfigOption(path = "schedule-finale.dodgebolt.scheduled")
    public static String SCHEDULE_FINALE_DODGEBOLT_SCHEDULED;
    @ConfigOption(path = "schedule-finale.dodgebolt.scheduled-forced")
    public static String SCHEDULE_FINALE_DODGEBOLT_SCHEDULED_FORCED;
    @ConfigOption(path = "schedule-finale.dodgebolt.announcement")
    public static String SCHEDULE_FINALE_DODGEBOLT_ANNOUNCEMENT;
    @ConfigOption(path = "schedule-finale.dodgebolt.start-failed")
    public static String SCHEDULE_FINALE_DODGEBOLT_START_FAILED;
    @ConfigOption(path = "schedule-finale.dodgebolt.forced-start-failed")
    public static String SCHEDULE_FINALE_DODGEBOLT_FORCED_START_FAILED;

    @ConfigOption(path = "map-editor-command.unknown-game")
    public static String MAP_EDITOR_COMMAND_UNKNOWN_GAME;
    @ConfigOption(path = "map-editor-command.unsupported-game")
    public static String MAP_EDITOR_COMMAND_UNSUPPORTED_GAME;
    @ConfigOption(path = "map-editor-command.no-manager")
    public static String MAP_EDITOR_COMMAND_NO_MANAGER;
    @ConfigOption(path = "map-editor-command.loading-disabled")
    public static String MAP_EDITOR_COMMAND_LOADING_DISABLED;
    @ConfigOption(path = "map-editor-command.loading-for-rename")
    public static String MAP_EDITOR_COMMAND_LOADING_FOR_RENAME;
    @ConfigOption(path = "map-editor-command.manager-load-failed")
    public static String MAP_EDITOR_COMMAND_MANAGER_LOAD_FAILED;
    @ConfigOption(path = "buildmart-blueprint.stars-range")
    public static String BUILD_MART_BLUEPRINT_STARS_RANGE;
    @ConfigOption(path = "buildmart-blueprint.selection-read-failed")
    public static String BUILD_MART_BLUEPRINT_SELECTION_READ_FAILED;
    @ConfigOption(path = "buildmart-blueprint.height-limit")
    public static String BUILD_MART_BLUEPRINT_HEIGHT_LIMIT;
    @ConfigOption(path = "buildmart-blueprint.footprint-limit")
    public static String BUILD_MART_BLUEPRINT_FOOTPRINT_LIMIT;
    @ConfigOption(path = "buildmart-blueprint.block-limit")
    public static String BUILD_MART_BLUEPRINT_BLOCK_LIMIT;
    @ConfigOption(path = "buildmart-blueprint.empty-selection")
    public static String BUILD_MART_BLUEPRINT_EMPTY_SELECTION;
    @ConfigOption(path = "buildmart-blueprint.save-failed")
    public static String BUILD_MART_BLUEPRINT_SAVE_FAILED;
    @ConfigOption(path = "buildmart-blueprint.exported-auto")
    public static String BUILD_MART_BLUEPRINT_EXPORTED_AUTO;
    @ConfigOption(path = "buildmart-blueprint.exported-manual")
    public static String BUILD_MART_BLUEPRINT_EXPORTED_MANUAL;
    @ConfigOption(path = "buildmart-blueprint.override-not-suggested")
    public static String BUILD_MART_BLUEPRINT_OVERRIDE_NOT_SUGGESTED;
    @ConfigOption(path = "buildmart-blueprint.map-missing")
    public static String BUILD_MART_BLUEPRINT_MAP_MISSING;
    @ConfigOption(path = "buildmart-blueprint.missing")
    public static String BUILD_MART_BLUEPRINT_MISSING;
    @ConfigOption(path = "buildmart-blueprint.page-out-of-range")
    public static String BUILD_MART_BLUEPRINT_PAGE_OUT_OF_RANGE;
    @ConfigOption(path = "buildmart-blueprint.stars-same")
    public static String BUILD_MART_BLUEPRINT_STARS_SAME;
    @ConfigOption(path = "buildmart-blueprint.stars-changed")
    public static String BUILD_MART_BLUEPRINT_STARS_CHANGED;
    @ConfigOption(path = "buildmart-blueprint.audit-header")
    public static String BUILD_MART_BLUEPRINT_AUDIT_HEADER;
    @ConfigOption(path = "buildmart-blueprint.audit-summary")
    public static String BUILD_MART_BLUEPRINT_AUDIT_SUMMARY;
    @ConfigOption(path = "buildmart-blueprint.audit-row")
    public static String BUILD_MART_BLUEPRINT_AUDIT_ROW;
    @ConfigOption(path = "buildmart-blueprint.audit-title")
    public static String BUILD_MART_BLUEPRINT_AUDIT_TITLE;
    @ConfigOption(path = "buildmart-blueprint.audit-structure")
    public static String BUILD_MART_BLUEPRINT_AUDIT_STRUCTURE;
    @ConfigOption(path = "buildmart-blueprint.audit-state")
    public static String BUILD_MART_BLUEPRINT_AUDIT_STATE;
    @ConfigOption(path = "buildmart-blueprint.coverage-no-map")
    public static String BUILD_MART_BLUEPRINT_COVERAGE_NO_MAP;
    @ConfigOption(path = "buildmart-blueprint.coverage-no-zones")
    public static String BUILD_MART_BLUEPRINT_COVERAGE_NO_ZONES;
    @ConfigOption(path = "buildmart-blueprint.audit-materials")
    public static String BUILD_MART_BLUEPRINT_AUDIT_MATERIALS;
    @ConfigOption(path = "buildmart-blueprint.coverage-full")
    public static String BUILD_MART_BLUEPRINT_COVERAGE_FULL;
    @ConfigOption(path = "buildmart-blueprint.coverage-uncovered")
    public static String BUILD_MART_BLUEPRINT_COVERAGE_UNCOVERED;
    @ConfigOption(path = "buildmart-blueprint.audit-warning")
    public static String BUILD_MART_BLUEPRINT_AUDIT_WARNING;

    // Team management feedback
    @ConfigOption(path = "team-gui.add-member-database-error")
    public static String TEAM_GUI_ADD_MEMBER_DATABASE_ERROR;

    @ConfigOption(path = "team-gui.add-member-identity-conflict")
    public static String TEAM_GUI_ADD_MEMBER_IDENTITY_CONFLICT;

    @ConfigOption(path = "team-gui.add-member-invalid-player-name")
    public static String TEAM_GUI_ADD_MEMBER_INVALID_PLAYER_NAME;

    @ConfigOption(path = "team-gui.add-member-operation-in-progress")
    public static String TEAM_GUI_ADD_MEMBER_OPERATION_IN_PROGRESS;

    @ConfigOption(path = "team-gui.add-member-player-not-registered")
    public static String TEAM_GUI_ADD_MEMBER_PLAYER_NOT_REGISTERED;

    @ConfigOption(path = "team-gui.add-member-profile-service-unavailable")
    public static String TEAM_GUI_ADD_MEMBER_PROFILE_SERVICE_UNAVAILABLE;

    @ConfigOption(path = "team-gui.add-member-team-full")
    public static String TEAM_GUI_ADD_MEMBER_TEAM_FULL;

    @ConfigOption(path = "team-gui.add-member-team-not-found")
    public static String TEAM_GUI_ADD_MEMBER_TEAM_NOT_FOUND;

    @ConfigOption(path = "team-gui.failed-to-create-team-name-or-color-may-already-be-taken")
    public static String TEAM_GUI_FAILED_TO_CREATE_TEAM_NAME_OR_COLOR_MAY_ALREADY_BE_TAKEN;

    @ConfigOption(path = "team-gui.please-enter-a-valid-minecraft-player-name")
    public static String TEAM_GUI_PLEASE_ENTER_A_VALID_MINECRAFT_PLAYER_NAME;

    @ConfigOption(path = "team-gui.removal-failed-team-or-member-status-may-have-changed")
    public static String TEAM_GUI_REMOVAL_FAILED_TEAM_OR_MEMBER_STATUS_MAY_HAVE_CHANGED;

    @ConfigOption(path = "team-gui.team-created")
    public static String TEAM_GUI_TEAM_CREATED;

    @ConfigOption(path = "team-gui.team-deleted")
    public static String TEAM_GUI_TEAM_DELETED;

    @ConfigOption(path = "team-gui.all-teams-teleported")
    public static String TEAM_GUI_ALL_TEAMS_TELEPORTED;

    @ConfigOption(path = "team-gui.member-removed")
    public static String TEAM_GUI_MEMBER_REMOVED;

    @ConfigOption(path = "team-gui.offline-player-joined")
    public static String TEAM_GUI_OFFLINE_PLAYER_JOINED;

    @ConfigOption(path = "team-gui.player-joined")
    public static String TEAM_GUI_PLAYER_JOINED;

    @ConfigOption(path = "team-gui.player-moved")
    public static String TEAM_GUI_PLAYER_MOVED;

    @ConfigOption(path = "team-gui.team-teleported")
    public static String TEAM_GUI_TEAM_TELEPORTED;
    @ConfigOption(path = "team-gui.team-name-already-exists")
    public static String TEAM_GUI_TEAM_NAME_ALREADY_EXISTS;

    @ConfigOption(path = "team-gui.team-name-cannot-be-empty")
    public static String TEAM_GUI_TEAM_NAME_CANNOT_BE_EMPTY;

    @ConfigOption(path = "team-gui.team-names-cannot-exceed-64-characters-and-cannot-contain-control-characters")
    public static String TEAM_GUI_TEAM_NAMES_CANNOT_EXCEED_64_CHARACTERS_AND_CANNOT_CONTAIN_CONTROL_CHARACTERS;

    @ConfigOption(path = "team-gui.team-not-found-feedback")
    public static String TEAM_GUI_TEAM_NOT_FOUND_FEEDBACK;

    @ConfigOption(path = "team-gui.team-transfer-failed-database-or-player-status-may-have-changed")
    public static String TEAM_GUI_TEAM_TRANSFER_FAILED_DATABASE_OR_PLAYER_STATUS_MAY_HAVE_CHANGED;

    @ConfigOption(path = "team-gui.the-number-of-people-in-the-team-has-reached-the-upper-limit")
    public static String TEAM_GUI_THE_NUMBER_OF_PEOPLE_IN_THE_TEAM_HAS_REACHED_THE_UPPER_LIMIT;

    @ConfigOption(path = "team-gui.the-player-is-already-on-the-team")
    public static String TEAM_GUI_THE_PLAYER_IS_ALREADY_ON_THE_TEAM;

    @ConfigOption(path = "team-gui.the-player-s-current-team-or-target-team-is-currently-in-the-game-cannot-change-teams")
    public static String TEAM_GUI_THE_PLAYER_S_CURRENT_TEAM_OR_TARGET_TEAM_IS_CURRENTLY_IN_THE_GAME_CANNOT_CHANGE_TEAMS;

    @ConfigOption(path = "team-gui.the-target-team-is-full")
    public static String TEAM_GUI_THE_TARGET_TEAM_IS_FULL;

    @ConfigOption(path = "team-gui.the-target-team-no-longer-exists")
    public static String TEAM_GUI_THE_TARGET_TEAM_NO_LONGER_EXISTS;

    @ConfigOption(path = "team-gui.the-team-no-longer-exists")
    public static String TEAM_GUI_THE_TEAM_NO_LONGER_EXISTS;

    @ConfigOption(path = "team-gui.this-color-has-just-been-taken-by-another-team-please-choose-again")
    public static String TEAM_GUI_THIS_COLOR_HAS_JUST_BEEN_TAKEN_BY_ANOTHER_TEAM_PLEASE_CHOOSE_AGAIN;

    @ConfigOption(path = "team-gui.unable-to-delete-team-team-may-be-in-game")
    public static String TEAM_GUI_UNABLE_TO_DELETE_TEAM_TEAM_MAY_BE_IN_GAME;

    @ConfigOption(path = "team-gui.unable-to-read-player-history")
    public static String TEAM_GUI_UNABLE_TO_READ_PLAYER_HISTORY;

    @ConfigOption(path = "team-gui.you-no-longer-have-permission-to-use-the-team-management-interface")
    public static String TEAM_GUI_YOU_NO_LONGER_HAVE_PERMISSION_TO_USE_THE_TEAM_MANAGEMENT_INTERFACE;

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

    // Chat
    @ConfigOption(path = "chat.team.prefix")
    public static String CHAT_TEAM_PREFIX;

    @ConfigOption(path = "chat.team.usage")
    public static String CHAT_TEAM_USAGE;

    @ConfigOption(path = "chat.team.unavailable")
    public static String CHAT_TEAM_UNAVAILABLE;

    // Free play
    @ConfigOption(path = "daily.prefix") public static String DAILY_PREFIX;
    @ConfigOption(path = "daily.prefixed")
    public static String DAILY_PREFIXED;
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
    @ConfigOption(path = "daily.team-names") public static java.util.List<String> DAILY_TEAM_NAMES;
    @ConfigOption(path = "daily.team-suffix") public static String DAILY_TEAM_SUFFIX;
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
    @ConfigOption(path = "daily.bossbar.needs-group") public static String DAILY_BOSSBAR_NEEDS_GROUP;
    @ConfigOption(path = "daily.queue-clear.championship") public static String DAILY_QUEUE_CLEAR_CHAMPIONSHIP;
    @ConfigOption(path = "daily.queue-clear.reload") public static String DAILY_QUEUE_CLEAR_RELOAD;
    @ConfigOption(path = "daily.queue-paused-member") public static String DAILY_QUEUE_PAUSED_MEMBER;
    @ConfigOption(path = "daily.leaderboard.row-count") public static String DAILY_LEADERBOARD_ROW_COUNT;
    @ConfigOption(path = "daily.leaderboard.row-time") public static String DAILY_LEADERBOARD_ROW_TIME;
    @ConfigOption(path = "daily.leaderboard.empty") public static String DAILY_LEADERBOARD_EMPTY;

    // Team
    @ConfigOption(path = "team.no-teams")
    public static String TEAM_NO_TEAMS;

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

    @ConfigOption(path = "reason.invalid-player-name")
    public static String REASON_INVALID_PLAYER_NAME;

    @ConfigOption(path = "reason.team-full")
    public static String REASON_TEAM_FULL;

    @ConfigOption(path = "reason.operation-in-progress")
    public static String REASON_OPERATION_IN_PROGRESS;

    @ConfigOption(path = "reason.player-not-registered")
    public static String REASON_PLAYER_NOT_REGISTERED;

    @ConfigOption(path = "reason.profile-service-unavailable")
    public static String REASON_PROFILE_SERVICE_UNAVAILABLE;

    @ConfigOption(path = "reason.identity-conflict")
    public static String REASON_IDENTITY_CONFLICT;

    @ConfigOption(path = "reason.database-error")
    public static String REASON_DATABASE_ERROR;

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

    @ConfigOption(path = "tgttos.return-reason.accident")
    public static String TGTTOS_RETURN_REASON_ACCIDENT;

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

    @ConfigOption(path = "buildmart.bossbar.title", nullable = true)
    public static String BUILD_MART_BOSSBAR_TITLE;

    @ConfigOption(path = "buildmart.bossbar.golden-none", nullable = true)
    public static String BUILD_MART_BOSSBAR_GOLDEN_NONE;

    @ConfigOption(path = "buildmart.bossbar.golden-active", nullable = true)
    public static String BUILD_MART_BOSSBAR_GOLDEN_ACTIVE;

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

    @ConfigOption(path = "tntrun.boss-bar-tnt-rain")
    public static String TNT_RUN_BOSS_BAR_TNT_RAIN;
    @ConfigOption(path = "tntrun.boss-bar")
    public static String TNT_RUN_BOSS_BAR;

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

    // SkyWars / TGTTOS player feedback
    @ConfigOption(path = "skywars.wrong-team-ghast")
    public static String SKYWARS_WRONG_TEAM_GHAST;

    @ConfigOption(path = "tgttos.return-to-start")
    public static String TGTTOS_RETURN_TO_START;

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

    @ConfigOption(path = "vote.no-valid-votes")
    public static String VOTE_NO_VALID_VOTES;

    @ConfigOption(path = "vote.tied-winner")
    public static String VOTE_TIED_WINNER;

    @ConfigOption(path = "daily.spectate-blocked-queued")
    public static String DAILY_SPECTATE_BLOCKED_QUEUED;

    @ConfigOption(path = "daily.lobby-item-no-space")
    public static String DAILY_LOBBY_ITEM_NO_SPACE;

    @ConfigOption(path = "daily.party-invite-failed")
    public static String DAILY_PARTY_INVITE_FAILED;

    @ConfigOption(path = "daily.party-invite-sent")
    public static String DAILY_PARTY_INVITE_SENT;

    @ConfigOption(path = "daily.party-invite-received")
    public static String DAILY_PARTY_INVITE_RECEIVED;

    @ConfigOption(path = "daily.party-no-invite")
    public static String DAILY_PARTY_NO_INVITE;

    @ConfigOption(path = "daily.party-member-joined")
    public static String DAILY_PARTY_MEMBER_JOINED_BROADCAST;

    @ConfigOption(path = "daily.party-not-member-or-playing")
    public static String DAILY_PARTY_NOT_MEMBER_OR_PLAYING;

    @ConfigOption(path = "daily.party-left")
    public static String DAILY_PARTY_LEFT_COMMAND;

    @ConfigOption(path = "daily.party-member-left")
    public static String DAILY_PARTY_MEMBER_LEFT_BROADCAST;

    @ConfigOption(path = "daily.party-disband-denied")
    public static String DAILY_PARTY_DISBAND_DENIED;

    @ConfigOption(path = "daily.party-disbanded")
    public static String DAILY_PARTY_DISBANDED_COMMAND;

    @ConfigOption(path = "daily.party-none")
    public static String DAILY_PARTY_NONE;

    @ConfigOption(path = "daily.party-info")
    public static String DAILY_PARTY_INFO;

    @ConfigOption(path = "event.unknown-error") public static String EVENT_UNKNOWN_ERROR;
    @ConfigOption(path = "event.import.incomplete") public static String EVENT_IMPORT_INCOMPLETE;
    @ConfigOption(path = "event.import.not-ready") public static String EVENT_IMPORT_NOT_READY;
    @ConfigOption(path = "event.import.games-empty") public static String EVENT_IMPORT_GAMES_EMPTY;
    @ConfigOption(path = "event.import.game-invalid") public static String EVENT_IMPORT_GAME_INVALID;
    @ConfigOption(path = "event.import.variant-invalid") public static String EVENT_IMPORT_VARIANT_INVALID;
    @ConfigOption(path = "event.import.multiplier-count-invalid") public static String EVENT_IMPORT_MULTIPLIER_COUNT_INVALID;
    @ConfigOption(path = "event.import.multiplier-invalid") public static String EVENT_IMPORT_MULTIPLIER_INVALID;
    @ConfigOption(path = "event.import.team-count-invalid") public static String EVENT_IMPORT_TEAM_COUNT_INVALID;
    @ConfigOption(path = "event.import.team-name-invalid") public static String EVENT_IMPORT_TEAM_NAME_INVALID;
    @ConfigOption(path = "event.import.team-name-duplicate") public static String EVENT_IMPORT_TEAM_NAME_DUPLICATE;
    @ConfigOption(path = "event.import.team-color-duplicate") public static String EVENT_IMPORT_TEAM_COLOR_DUPLICATE;
    @ConfigOption(path = "event.import.team-color-fixed") public static String EVENT_IMPORT_TEAM_COLOR_FIXED;
    @ConfigOption(path = "event.import.team-size-invalid") public static String EVENT_IMPORT_TEAM_SIZE_INVALID;
    @ConfigOption(path = "event.import.username-invalid") public static String EVENT_IMPORT_USERNAME_INVALID;
    @ConfigOption(path = "event.import.uuid-invalid") public static String EVENT_IMPORT_UUID_INVALID;
    @ConfigOption(path = "event.import.player-duplicate") public static String EVENT_IMPORT_PLAYER_DUPLICATE;
    @ConfigOption(path = "event.import.running") public static String EVENT_IMPORT_RUNNING;
    @ConfigOption(path = "event.import.validating") public static String EVENT_IMPORT_VALIDATING;
    @ConfigOption(path = "event.import.database-failed") public static String EVENT_IMPORT_DATABASE_FAILED;
    @ConfigOption(path = "event.import.state-save-failed") public static String EVENT_IMPORT_STATE_SAVE_FAILED;
    @ConfigOption(path = "event.import.completed") public static String EVENT_IMPORT_COMPLETED;
    @ConfigOption(path = "event.import.failed") public static String EVENT_IMPORT_FAILED;
    @ConfigOption(path = "event.start.game-invalid") public static String EVENT_START_GAME_INVALID;
    @ConfigOption(path = "event.start.no-event") public static String EVENT_START_NO_EVENT;
    @ConfigOption(path = "event.start.archived") public static String EVENT_START_ARCHIVED;
    @ConfigOption(path = "event.start.game-not-in-event") public static String EVENT_START_GAME_NOT_IN_EVENT;
    @ConfigOption(path = "event.start.started") public static String EVENT_START_STARTED;
    @ConfigOption(path = "event.start.emergency-stopped") public static String EVENT_START_EMERGENCY_STOPPED;
    @ConfigOption(path = "event.start.unavailable") public static String EVENT_START_UNAVAILABLE;
    @ConfigOption(path = "event.start.no-schedule") public static String EVENT_START_NO_SCHEDULE;
    @ConfigOption(path = "event.export.no-event") public static String EVENT_EXPORT_NO_EVENT;
    @ConfigOption(path = "event.export.still-running") public static String EVENT_EXPORT_STILL_RUNNING;
    @ConfigOption(path = "event.export.no-points") public static String EVENT_EXPORT_NO_POINTS;
    @ConfigOption(path = "event.export.summarizing") public static String EVENT_EXPORT_SUMMARIZING;
    @ConfigOption(path = "event.export.completed") public static String EVENT_EXPORT_COMPLETED;
    @ConfigOption(path = "event.export.failed") public static String EVENT_EXPORT_FAILED;
    @ConfigOption(path = "event.reset.done") public static String EVENT_RESET_DONE;
    @ConfigOption(path = "event.stop.unavailable") public static String EVENT_STOP_UNAVAILABLE;
    @ConfigOption(path = "event.stopped") public static String EVENT_STOPPED;
    @ConfigOption(path = "event.not-running") public static String EVENT_NOT_RUNNING;
    @ConfigOption(path = "event.undo.unavailable") public static String EVENT_UNDO_UNAVAILABLE;
    @ConfigOption(path = "event.undo.started") public static String EVENT_UNDO_STARTED;

    @ConfigOption(path = "admin.team-missing") public static String ADMIN_TEAM_MISSING;
    @ConfigOption(path = "admin.teleported-players") public static String ADMIN_TELEPORTED_PLAYERS;
    @ConfigOption(path = "admin.max-players-positive-integer") public static String ADMIN_MAX_PLAYERS_POSITIVE_INTEGER;
    @ConfigOption(path = "admin.max-players-greater-than-zero") public static String ADMIN_MAX_PLAYERS_GREATER_THAN_ZERO;
    @ConfigOption(path = "admin.max-players-set") public static String ADMIN_MAX_PLAYERS_SET;
    @ConfigOption(path = "admin.player-offline") public static String ADMIN_PLAYER_OFFLINE;
    @ConfigOption(path = "admin.visibility-header") public static String ADMIN_VISIBILITY_HEADER;
    @ConfigOption(path = "admin.visibility-line") public static String ADMIN_VISIBILITY_LINE;
    @ConfigOption(path = "admin.reload.prepare-active") public static String ADMIN_RELOAD_PREPARE_ACTIVE;
    @ConfigOption(path = "admin.reload.already-running") public static String ADMIN_RELOAD_ALREADY_RUNNING;
    @ConfigOption(path = "admin.reload.config-failed") public static String ADMIN_RELOAD_CONFIG_FAILED;
    @ConfigOption(path = "admin.reload.read-done") public static String ADMIN_RELOAD_READ_DONE;
    @ConfigOption(path = "admin.reload.reset-failed") public static String ADMIN_RELOAD_RESET_FAILED;
    @ConfigOption(path = "admin.reload.restart-required") public static String ADMIN_RELOAD_RESTART_REQUIRED;
    @ConfigOption(path = "admin.reload.reset-failures") public static String ADMIN_RELOAD_RESET_FAILURES;
    @ConfigOption(path = "admin.reload.bingo-failure") public static String ADMIN_RELOAD_BINGO_FAILURE;
    @ConfigOption(path = "admin.reload.config-failures") public static String ADMIN_RELOAD_CONFIG_FAILURES;
    @ConfigOption(path = "admin.reload.remote-stopped") public static String ADMIN_RELOAD_REMOTE_STOPPED;
    @ConfigOption(path = "admin.reload.completed") public static String ADMIN_RELOAD_COMPLETED;
    @ConfigOption(path = "admin.sudo-all") public static String ADMIN_SUDO_ALL;
    @ConfigOption(path = "admin.sudo-team") public static String ADMIN_SUDO_TEAM;

    @ConfigOption(path = "admin.world.invalid-name") public static String ADMIN_WORLD_INVALID_NAME;
    @ConfigOption(path = "admin.world.same-name") public static String ADMIN_WORLD_SAME_NAME;
    @ConfigOption(path = "admin.world.target-exists") public static String ADMIN_WORLD_TARGET_EXISTS;
    @ConfigOption(path = "admin.world.missing") public static String ADMIN_WORLD_MISSING;
    @ConfigOption(path = "admin.world.main-protected-delete") public static String ADMIN_WORLD_MAIN_PROTECTED_DELETE;
    @ConfigOption(path = "admin.world.main-protected-rename") public static String ADMIN_WORLD_MAIN_PROTECTED_RENAME;
    @ConfigOption(path = "admin.world.main-protected-unload") public static String ADMIN_WORLD_MAIN_PROTECTED_UNLOAD;
    @ConfigOption(path = "admin.world.bingo-protected-delete") public static String ADMIN_WORLD_BINGO_PROTECTED_DELETE;
    @ConfigOption(path = "admin.world.bingo-protected-rename") public static String ADMIN_WORLD_BINGO_PROTECTED_RENAME;
    @ConfigOption(path = "admin.world.map-owner-protected") public static String ADMIN_WORLD_MAP_OWNER_PROTECTED;
    @ConfigOption(path = "admin.world.delete-confirm") public static String ADMIN_WORLD_DELETE_CONFIRM;
    @ConfigOption(path = "admin.world.unload-failed") public static String ADMIN_WORLD_UNLOAD_FAILED;
    @ConfigOption(path = "admin.world.delete-failed") public static String ADMIN_WORLD_DELETE_FAILED;
    @ConfigOption(path = "admin.world.deleted") public static String ADMIN_WORLD_DELETED;
    @ConfigOption(path = "admin.world.moved-players") public static String ADMIN_WORLD_MOVED_PLAYERS;
    @ConfigOption(path = "admin.world.already-loaded") public static String ADMIN_WORLD_ALREADY_LOADED;
    @ConfigOption(path = "admin.world.bingo-environment-required") public static String ADMIN_WORLD_BINGO_ENVIRONMENT_REQUIRED;
    @ConfigOption(path = "admin.world.load-failed") public static String ADMIN_WORLD_LOAD_FAILED;
    @ConfigOption(path = "admin.world.load-failed-simple") public static String ADMIN_WORLD_LOAD_FAILED_SIMPLE;
    @ConfigOption(path = "admin.world.action-loaded") public static String ADMIN_WORLD_ACTION_LOADED;
    @ConfigOption(path = "admin.world.action-created") public static String ADMIN_WORLD_ACTION_CREATED;
    @ConfigOption(path = "admin.world.created") public static String ADMIN_WORLD_CREATED;
    @ConfigOption(path = "admin.world.prepare-active-rename") public static String ADMIN_WORLD_PREPARE_ACTIVE_RENAME;
    @ConfigOption(path = "admin.world.environment-required") public static String ADMIN_WORLD_ENVIRONMENT_REQUIRED;
    @ConfigOption(path = "admin.world.environment-mismatch") public static String ADMIN_WORLD_ENVIRONMENT_MISMATCH;
    @ConfigOption(path = "admin.world.template-exists") public static String ADMIN_WORLD_TEMPLATE_EXISTS;
    @ConfigOption(path = "admin.world.directory-rename-failed") public static String ADMIN_WORLD_DIRECTORY_RENAME_FAILED;
    @ConfigOption(path = "admin.world.rename-failed") public static String ADMIN_WORLD_RENAME_FAILED;
    @ConfigOption(path = "admin.world.config-migration-failed") public static String ADMIN_WORLD_CONFIG_MIGRATION_FAILED;
    @ConfigOption(path = "admin.world.renamed") public static String ADMIN_WORLD_RENAMED;
    @ConfigOption(path = "admin.world.game-in-use") public static String ADMIN_WORLD_GAME_IN_USE;
    @ConfigOption(path = "admin.world.name-derived") public static String ADMIN_WORLD_NAME_DERIVED;
    @ConfigOption(path = "admin.world.not-loaded") public static String ADMIN_WORLD_NOT_LOADED;
    @ConfigOption(path = "admin.world.not-loaded-create") public static String ADMIN_WORLD_NOT_LOADED_CREATE;
    @ConfigOption(path = "admin.world.teleport-failed") public static String ADMIN_WORLD_TELEPORT_FAILED;
    @ConfigOption(path = "admin.world.teleported") public static String ADMIN_WORLD_TELEPORTED;
    @ConfigOption(path = "admin.world.unloaded") public static String ADMIN_WORLD_UNLOADED;
    @ConfigOption(path = "admin.world.list-loaded") public static String ADMIN_WORLD_LIST_LOADED;
    @ConfigOption(path = "admin.world.main-suffix") public static String ADMIN_WORLD_MAIN_SUFFIX;
    @ConfigOption(path = "admin.world.row") public static String ADMIN_WORLD_ROW;
    @ConfigOption(path = "admin.world.list-unloaded") public static String ADMIN_WORLD_LIST_UNLOADED;
    @ConfigOption(path = "admin.world.none") public static String ADMIN_WORLD_NONE;
    @ConfigOption(path = "admin.world.unloaded-names") public static String ADMIN_WORLD_UNLOADED_NAMES;

    @ConfigOption(path = "server-mode-switched") public static String SERVER_MODE_SWITCHED;
    @ConfigOption(path = "spectator.display.remote") public static String SPECTATOR_DISPLAY_REMOTE;
    @ConfigOption(path = "spectator.display.copy") public static String SPECTATOR_DISPLAY_COPY;
    @ConfigOption(path = "spectator.display.instance") public static String SPECTATOR_DISPLAY_INSTANCE;
    @ConfigOption(path = "presentation.daily-lobby") public static String PRESENTATION_DAILY_LOBBY;
    @ConfigOption(path = "presentation.daily-game") public static String PRESENTATION_DAILY_GAME;
    @ConfigOption(path = "presentation.tab.team-footer") public static String PRESENTATION_TAB_TEAM_FOOTER;
    @ConfigOption(path = "presentation.tab.daily-team-footer") public static String PRESENTATION_TAB_DAILY_TEAM_FOOTER;
    @ConfigOption(path = "presentation.tab.current-game-footer") public static String PRESENTATION_TAB_CURRENT_GAME_FOOTER;
    @ConfigOption(path = "spectator.game-disabled") public static String SPECTATOR_GAME_DISABLED;
    @ConfigOption(path = "spectator.instance-missing") public static String SPECTATOR_INSTANCE_MISSING;
    @ConfigOption(path = "spectator.area-unavailable") public static String SPECTATOR_AREA_UNAVAILABLE;
    @ConfigOption(path = "admin.world.delete-command") public static String ADMIN_WORLD_DELETE_COMMAND;
    @ConfigOption(path = "admin.world.list-separator") public static String ADMIN_WORLD_LIST_SEPARATOR;
}
