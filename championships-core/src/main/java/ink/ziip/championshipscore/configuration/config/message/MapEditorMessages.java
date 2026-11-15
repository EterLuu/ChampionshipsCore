package ink.ziip.championshipscore.configuration.config.message;

import ink.ziip.championshipscore.configuration.ConfigOption;

/** Message fields owned by the MapEditor domain; persisted by MessageConfig. */
public final class MapEditorMessages {
    private MapEditorMessages() {}

    @ConfigOption(path = "map-editor-tntrun.elimination-height-set")
    public static String MAP_EDITOR_TNT_ELIMINATION_HEIGHT_SET;

    @ConfigOption(path = "map-editor-tntrun.elimination-height-invalid")
    public static String MAP_EDITOR_TNT_ELIMINATION_HEIGHT_INVALID;

    @ConfigOption(path = "map-editor-tntrun.spawn-set")
    public static String MAP_EDITOR_TNT_SPAWN_SET;

    @ConfigOption(path = "map-editor-skywars.map-boundaries-set")
    public static String MAP_EDITOR_SW_MAP_BOUNDARIES_SET;

    @ConfigOption(path = "map-editor-skywars.border-center-set")
    public static String MAP_EDITOR_SW_BORDER_CENTER_SET;

    @ConfigOption(path = "map-editor-tgttos.area-type-set")
    public static String MAP_EDITOR_TGT_AREA_TYPE_SET;

    @ConfigOption(path = "map-editor-tgttos.spawn-area-flat")
    public static String MAP_EDITOR_TGT_SPAWN_AREA_FLAT;

    @ConfigOption(path = "map-editor-tgttos.chicken-spawn-set")
    public static String MAP_EDITOR_TGT_CHICKEN_SPAWN_SET;

    @ConfigOption(path = "map-editor-tgttos.player-spawn-set")
    public static String MAP_EDITOR_TGT_PLAYER_SPAWN_SET;

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

    @ConfigOption(path = "map-editor-decarnival.world-environment")
    public static String MAP_EDITOR_DEC_WORLD_ENVIRONMENT;

    @ConfigOption(path = "map-editor-decarnival.fight-region")
    public static String MAP_EDITOR_DEC_FIGHT_REGION;

    @ConfigOption(path = "map-editor-decarnival.spectator-world")
    public static String MAP_EDITOR_DEC_SPECTATOR_WORLD;

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

    @ConfigOption(path = "map-editor-bingo-teleport.no-teammates")
    public static String MAP_EDITOR_BINGO_NO_TEAMMATES;

    @ConfigOption(path = "map-editor-bingo-teleport.unreachable")
    public static String MAP_EDITOR_BINGO_UNREACHABLE;

    @ConfigOption(path = "map-editor-bingo-teleport.sent-to")
    public static String MAP_EDITOR_BINGO_SENT_TO;

    @ConfigOption(path = "map-editor-spectator.unavailable")
    public static String SPECTATOR_UNAVAILABLE;

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

    @ConfigOption(path = "map-editor-buildmart.jump-pad-select-first")
    public static String MAP_EDITOR_BUILD_JUMP_PAD_SELECT_FIRST;

    @ConfigOption(path = "map-editor-buildmart.jump-pad-added")
    public static String MAP_EDITOR_BUILD_JUMP_PAD_ADDED;

    @ConfigOption(path = "map-editor-buildmart.jump-pad-cleared")
    public static String MAP_EDITOR_BUILD_JUMP_PAD_CLEARED;

    @ConfigOption(path = "map-editor-buildmart.jump-pad-updated")
    public static String MAP_EDITOR_BUILD_JUMP_PAD_UPDATED;

    @ConfigOption(path = "map-editor-buildmart.jump-pad-adjusted")
    public static String MAP_EDITOR_BUILD_JUMP_PAD_ADJUSTED;

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

    @ConfigOption(path = "map-editor-step.riptide-introduction-arena-unavailable")
    public static String MAP_EDITOR_RIPTIDE_INTRODUCTION_ARENA_UNAVAILABLE;

    @ConfigOption(path = "map-editor-step.riptide-introduction-arena-teleport-failed")
    public static String MAP_EDITOR_RIPTIDE_INTRODUCTION_ARENA_TELEPORT_FAILED;

    @ConfigOption(path = "map-editor-step.riptide-introduction-arena-teleported")
    public static String MAP_EDITOR_RIPTIDE_INTRODUCTION_ARENA_TELEPORTED;

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
}
