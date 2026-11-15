package ink.ziip.championshipscore.configuration.config.message;

import ink.ziip.championshipscore.configuration.ConfigOption;

/** Message fields owned by the Event domain; persisted by MessageConfig. */
public final class EventMessages {
    private EventMessages() {}

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

    @ConfigOption(path = "event.unknown-error")
    public static String EVENT_UNKNOWN_ERROR;

    @ConfigOption(path = "event.import.incomplete")
    public static String EVENT_IMPORT_INCOMPLETE;

    @ConfigOption(path = "event.import.not-ready")
    public static String EVENT_IMPORT_NOT_READY;

    @ConfigOption(path = "event.import.games-empty")
    public static String EVENT_IMPORT_GAMES_EMPTY;

    @ConfigOption(path = "event.import.game-invalid")
    public static String EVENT_IMPORT_GAME_INVALID;

    @ConfigOption(path = "event.import.variant-invalid")
    public static String EVENT_IMPORT_VARIANT_INVALID;

    @ConfigOption(path = "event.import.multiplier-count-invalid")
    public static String EVENT_IMPORT_MULTIPLIER_COUNT_INVALID;

    @ConfigOption(path = "event.import.multiplier-invalid")
    public static String EVENT_IMPORT_MULTIPLIER_INVALID;

    @ConfigOption(path = "event.import.team-count-invalid")
    public static String EVENT_IMPORT_TEAM_COUNT_INVALID;

    @ConfigOption(path = "event.import.team-name-invalid")
    public static String EVENT_IMPORT_TEAM_NAME_INVALID;

    @ConfigOption(path = "event.import.team-name-duplicate")
    public static String EVENT_IMPORT_TEAM_NAME_DUPLICATE;

    @ConfigOption(path = "event.import.team-color-duplicate")
    public static String EVENT_IMPORT_TEAM_COLOR_DUPLICATE;

    @ConfigOption(path = "event.import.team-color-fixed")
    public static String EVENT_IMPORT_TEAM_COLOR_FIXED;

    @ConfigOption(path = "event.import.team-size-invalid")
    public static String EVENT_IMPORT_TEAM_SIZE_INVALID;

    @ConfigOption(path = "event.import.username-invalid")
    public static String EVENT_IMPORT_USERNAME_INVALID;

    @ConfigOption(path = "event.import.uuid-invalid")
    public static String EVENT_IMPORT_UUID_INVALID;

    @ConfigOption(path = "event.import.player-duplicate")
    public static String EVENT_IMPORT_PLAYER_DUPLICATE;

    @ConfigOption(path = "event.import.running")
    public static String EVENT_IMPORT_RUNNING;

    @ConfigOption(path = "event.import.validating")
    public static String EVENT_IMPORT_VALIDATING;

    @ConfigOption(path = "event.import.database-failed")
    public static String EVENT_IMPORT_DATABASE_FAILED;

    @ConfigOption(path = "event.import.state-save-failed")
    public static String EVENT_IMPORT_STATE_SAVE_FAILED;

    @ConfigOption(path = "event.import.completed")
    public static String EVENT_IMPORT_COMPLETED;

    @ConfigOption(path = "event.import.failed")
    public static String EVENT_IMPORT_FAILED;

    @ConfigOption(path = "event.start.game-invalid")
    public static String EVENT_START_GAME_INVALID;

    @ConfigOption(path = "event.start.no-event")
    public static String EVENT_START_NO_EVENT;

    @ConfigOption(path = "event.start.archived")
    public static String EVENT_START_ARCHIVED;

    @ConfigOption(path = "event.start.game-not-in-event")
    public static String EVENT_START_GAME_NOT_IN_EVENT;

    @ConfigOption(path = "event.start.started")
    public static String EVENT_START_STARTED;

    @ConfigOption(path = "event.start.emergency-stopped")
    public static String EVENT_START_EMERGENCY_STOPPED;

    @ConfigOption(path = "event.start.unavailable")
    public static String EVENT_START_UNAVAILABLE;

    @ConfigOption(path = "event.start.no-schedule")
    public static String EVENT_START_NO_SCHEDULE;

    @ConfigOption(path = "event.export.no-event")
    public static String EVENT_EXPORT_NO_EVENT;

    @ConfigOption(path = "event.export.still-running")
    public static String EVENT_EXPORT_STILL_RUNNING;

    @ConfigOption(path = "event.export.no-points")
    public static String EVENT_EXPORT_NO_POINTS;

    @ConfigOption(path = "event.export.summarizing")
    public static String EVENT_EXPORT_SUMMARIZING;

    @ConfigOption(path = "event.export.completed")
    public static String EVENT_EXPORT_COMPLETED;

    @ConfigOption(path = "event.export.failed")
    public static String EVENT_EXPORT_FAILED;

    @ConfigOption(path = "event.reset.done")
    public static String EVENT_RESET_DONE;

    @ConfigOption(path = "event.stop.unavailable")
    public static String EVENT_STOP_UNAVAILABLE;

    @ConfigOption(path = "event.stopped")
    public static String EVENT_STOPPED;

    @ConfigOption(path = "event.not-running")
    public static String EVENT_NOT_RUNNING;

    @ConfigOption(path = "event.undo.unavailable")
    public static String EVENT_UNDO_UNAVAILABLE;

    @ConfigOption(path = "event.undo.started")
    public static String EVENT_UNDO_STARTED;
}
