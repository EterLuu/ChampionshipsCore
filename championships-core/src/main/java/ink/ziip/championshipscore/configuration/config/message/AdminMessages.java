package ink.ziip.championshipscore.configuration.config.message;

import ink.ziip.championshipscore.configuration.ConfigOption;

/** Message fields owned by the Admin domain; persisted by MessageConfig. */
public final class AdminMessages {
    private AdminMessages() {}

    @ConfigOption(path = "auth.identity-verification-failed")
    public static String IDENTITY_VERIFICATION_FAILED;

    @ConfigOption(path = "command.player-only")
    public static String COMMAND_PLAYER_ONLY;

    @ConfigOption(path = "command.unknown-game")
    public static String COMMAND_UNKNOWN_GAME;

    @ConfigOption(path = "command.daily-stats")
    public static String COMMAND_DAILY_STATS;

    @ConfigOption(path = "admin.mute.default-reason", nullable = true)
    public static String MUTE_DEFAULT_REASON;

    @ConfigOption(path = "admin.mute.set", nullable = true)
    public static String ADMIN_MUTE_SET;

    @ConfigOption(path = "admin.mute.temporary-set", nullable = true)
    public static String ADMIN_TEMP_MUTE_SET;

    @ConfigOption(path = "admin.mute.unmuted", nullable = true)
    public static String ADMIN_UNMUTE_SET;

    @ConfigOption(path = "admin.mute.not-muted", nullable = true)
    public static String ADMIN_MUTE_NOT_MUTED;

    @ConfigOption(path = "admin.mute.invalid-duration", nullable = true)
    public static String ADMIN_MUTE_INVALID_DURATION;

    @ConfigOption(path = "admin.mute.lookup-failed", nullable = true)
    public static String ADMIN_MUTE_LOOKUP_FAILED;

    @ConfigOption(path = "admin.mute.save-failed", nullable = true)
    public static String ADMIN_MUTE_SAVE_FAILED;

    @ConfigOption(path = "chat.muted-permanent", nullable = true)
    public static String CHAT_MUTED_PERMANENT;

    @ConfigOption(path = "chat.muted-temporary", nullable = true)
    public static String CHAT_MUTED_TEMPORARY;

    @ConfigOption(path = "chat.unmuted", nullable = true)
    public static String CHAT_UNMUTED;

    @ConfigOption(path = "spawn.in-game-or-spectating")
    public static String SPAWN_IN_GAME_OR_SPECTATING;

    @ConfigOption(path = "spawn.missing")
    public static String SPAWN_MISSING;

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

    @ConfigOption(path = "chat.team.prefix")
    public static String CHAT_TEAM_PREFIX;

    @ConfigOption(path = "chat.team.usage")
    public static String CHAT_TEAM_USAGE;

    @ConfigOption(path = "chat.team.unavailable")
    public static String CHAT_TEAM_UNAVAILABLE;

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

    @ConfigOption(path = "admin.team-missing")
    public static String ADMIN_TEAM_MISSING;

    @ConfigOption(path = "admin.teleported-players")
    public static String ADMIN_TELEPORTED_PLAYERS;

    @ConfigOption(path = "admin.max-players-positive-integer")
    public static String ADMIN_MAX_PLAYERS_POSITIVE_INTEGER;

    @ConfigOption(path = "admin.max-players-greater-than-zero")
    public static String ADMIN_MAX_PLAYERS_GREATER_THAN_ZERO;

    @ConfigOption(path = "admin.max-players-set")
    public static String ADMIN_MAX_PLAYERS_SET;

    @ConfigOption(path = "admin.player-offline")
    public static String ADMIN_PLAYER_OFFLINE;

    @ConfigOption(path = "admin.visibility-header")
    public static String ADMIN_VISIBILITY_HEADER;

    @ConfigOption(path = "admin.visibility-line")
    public static String ADMIN_VISIBILITY_LINE;

    @ConfigOption(path = "admin.reload.prepare-active")
    public static String ADMIN_RELOAD_PREPARE_ACTIVE;

    @ConfigOption(path = "admin.reload.already-running")
    public static String ADMIN_RELOAD_ALREADY_RUNNING;

    @ConfigOption(path = "admin.reload.config-failed")
    public static String ADMIN_RELOAD_CONFIG_FAILED;

    @ConfigOption(path = "admin.reload.read-done")
    public static String ADMIN_RELOAD_READ_DONE;

    @ConfigOption(path = "admin.reload.reset-failed")
    public static String ADMIN_RELOAD_RESET_FAILED;

    @ConfigOption(path = "admin.reload.restart-required")
    public static String ADMIN_RELOAD_RESTART_REQUIRED;

    @ConfigOption(path = "admin.reload.reset-failures")
    public static String ADMIN_RELOAD_RESET_FAILURES;

    @ConfigOption(path = "admin.reload.bingo-failure")
    public static String ADMIN_RELOAD_BINGO_FAILURE;

    @ConfigOption(path = "admin.reload.config-failures")
    public static String ADMIN_RELOAD_CONFIG_FAILURES;

    @ConfigOption(path = "admin.reload.remote-stopped")
    public static String ADMIN_RELOAD_REMOTE_STOPPED;

    @ConfigOption(path = "admin.reload.completed")
    public static String ADMIN_RELOAD_COMPLETED;

    @ConfigOption(path = "admin.sudo-all")
    public static String ADMIN_SUDO_ALL;

    @ConfigOption(path = "admin.sudo-team")
    public static String ADMIN_SUDO_TEAM;

    @ConfigOption(path = "admin.world.invalid-name")
    public static String ADMIN_WORLD_INVALID_NAME;

    @ConfigOption(path = "admin.world.same-name")
    public static String ADMIN_WORLD_SAME_NAME;

    @ConfigOption(path = "admin.world.target-exists")
    public static String ADMIN_WORLD_TARGET_EXISTS;

    @ConfigOption(path = "admin.world.missing")
    public static String ADMIN_WORLD_MISSING;

    @ConfigOption(path = "admin.world.main-protected-delete")
    public static String ADMIN_WORLD_MAIN_PROTECTED_DELETE;

    @ConfigOption(path = "admin.world.main-protected-rename")
    public static String ADMIN_WORLD_MAIN_PROTECTED_RENAME;

    @ConfigOption(path = "admin.world.main-protected-unload")
    public static String ADMIN_WORLD_MAIN_PROTECTED_UNLOAD;

    @ConfigOption(path = "admin.world.bingo-protected-delete")
    public static String ADMIN_WORLD_BINGO_PROTECTED_DELETE;

    @ConfigOption(path = "admin.world.bingo-protected-rename")
    public static String ADMIN_WORLD_BINGO_PROTECTED_RENAME;

    @ConfigOption(path = "admin.world.map-owner-protected")
    public static String ADMIN_WORLD_MAP_OWNER_PROTECTED;

    @ConfigOption(path = "admin.world.delete-confirm")
    public static String ADMIN_WORLD_DELETE_CONFIRM;

    @ConfigOption(path = "admin.world.unload-failed")
    public static String ADMIN_WORLD_UNLOAD_FAILED;

    @ConfigOption(path = "admin.world.delete-failed")
    public static String ADMIN_WORLD_DELETE_FAILED;

    @ConfigOption(path = "admin.world.deleted")
    public static String ADMIN_WORLD_DELETED;

    @ConfigOption(path = "admin.world.moved-players")
    public static String ADMIN_WORLD_MOVED_PLAYERS;

    @ConfigOption(path = "admin.world.already-loaded")
    public static String ADMIN_WORLD_ALREADY_LOADED;

    @ConfigOption(path = "admin.world.bingo-environment-required")
    public static String ADMIN_WORLD_BINGO_ENVIRONMENT_REQUIRED;

    @ConfigOption(path = "admin.world.load-failed")
    public static String ADMIN_WORLD_LOAD_FAILED;

    @ConfigOption(path = "admin.world.load-failed-simple")
    public static String ADMIN_WORLD_LOAD_FAILED_SIMPLE;

    @ConfigOption(path = "admin.world.action-loaded")
    public static String ADMIN_WORLD_ACTION_LOADED;

    @ConfigOption(path = "admin.world.action-created")
    public static String ADMIN_WORLD_ACTION_CREATED;

    @ConfigOption(path = "admin.world.created")
    public static String ADMIN_WORLD_CREATED;

    @ConfigOption(path = "admin.world.prepare-active-rename")
    public static String ADMIN_WORLD_PREPARE_ACTIVE_RENAME;

    @ConfigOption(path = "admin.world.environment-required")
    public static String ADMIN_WORLD_ENVIRONMENT_REQUIRED;

    @ConfigOption(path = "admin.world.environment-mismatch")
    public static String ADMIN_WORLD_ENVIRONMENT_MISMATCH;

    @ConfigOption(path = "admin.world.template-exists")
    public static String ADMIN_WORLD_TEMPLATE_EXISTS;

    @ConfigOption(path = "admin.world.directory-rename-failed")
    public static String ADMIN_WORLD_DIRECTORY_RENAME_FAILED;

    @ConfigOption(path = "admin.world.rename-failed")
    public static String ADMIN_WORLD_RENAME_FAILED;

    @ConfigOption(path = "admin.world.config-migration-failed")
    public static String ADMIN_WORLD_CONFIG_MIGRATION_FAILED;

    @ConfigOption(path = "admin.world.renamed")
    public static String ADMIN_WORLD_RENAMED;

    @ConfigOption(path = "admin.world.game-in-use")
    public static String ADMIN_WORLD_GAME_IN_USE;

    @ConfigOption(path = "admin.world.name-derived")
    public static String ADMIN_WORLD_NAME_DERIVED;

    @ConfigOption(path = "admin.world.not-loaded")
    public static String ADMIN_WORLD_NOT_LOADED;

    @ConfigOption(path = "admin.world.not-loaded-create")
    public static String ADMIN_WORLD_NOT_LOADED_CREATE;

    @ConfigOption(path = "admin.world.teleport-failed")
    public static String ADMIN_WORLD_TELEPORT_FAILED;

    @ConfigOption(path = "admin.world.teleported")
    public static String ADMIN_WORLD_TELEPORTED;

    @ConfigOption(path = "admin.world.unloaded")
    public static String ADMIN_WORLD_UNLOADED;

    @ConfigOption(path = "admin.world.list-loaded")
    public static String ADMIN_WORLD_LIST_LOADED;

    @ConfigOption(path = "admin.world.main-suffix")
    public static String ADMIN_WORLD_MAIN_SUFFIX;

    @ConfigOption(path = "admin.world.row")
    public static String ADMIN_WORLD_ROW;

    @ConfigOption(path = "admin.world.list-unloaded")
    public static String ADMIN_WORLD_LIST_UNLOADED;

    @ConfigOption(path = "admin.world.none")
    public static String ADMIN_WORLD_NONE;

    @ConfigOption(path = "admin.world.unloaded-names")
    public static String ADMIN_WORLD_UNLOADED_NAMES;

    @ConfigOption(path = "admin.world.delete-command")
    public static String ADMIN_WORLD_DELETE_COMMAND;

    @ConfigOption(path = "admin.world.list-separator")
    public static String ADMIN_WORLD_LIST_SEPARATOR;
}
