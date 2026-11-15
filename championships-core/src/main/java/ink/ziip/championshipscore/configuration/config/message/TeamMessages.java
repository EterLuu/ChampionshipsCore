package ink.ziip.championshipscore.configuration.config.message;

import ink.ziip.championshipscore.configuration.ConfigOption;

/** Message fields owned by the Team domain; persisted by MessageConfig. */
public final class TeamMessages {
    private TeamMessages() {}

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

    @ConfigOption(
            path =
                    "team-gui.team-names-cannot-exceed-64-characters-and-cannot-contain-control-characters")
    public static String
            TEAM_GUI_TEAM_NAMES_CANNOT_EXCEED_64_CHARACTERS_AND_CANNOT_CONTAIN_CONTROL_CHARACTERS;

    @ConfigOption(path = "team-gui.team-not-found-feedback")
    public static String TEAM_GUI_TEAM_NOT_FOUND_FEEDBACK;

    @ConfigOption(path = "team-gui.team-transfer-failed-database-or-player-status-may-have-changed")
    public static String TEAM_GUI_TEAM_TRANSFER_FAILED_DATABASE_OR_PLAYER_STATUS_MAY_HAVE_CHANGED;

    @ConfigOption(path = "team-gui.the-number-of-people-in-the-team-has-reached-the-upper-limit")
    public static String TEAM_GUI_THE_NUMBER_OF_PEOPLE_IN_THE_TEAM_HAS_REACHED_THE_UPPER_LIMIT;

    @ConfigOption(path = "team-gui.the-player-is-already-on-the-team")
    public static String TEAM_GUI_THE_PLAYER_IS_ALREADY_ON_THE_TEAM;

    @ConfigOption(
            path =
                    "team-gui.the-player-s-current-team-or-target-team-is-currently-in-the-game-cannot-change-teams")
    public static String
            TEAM_GUI_THE_PLAYER_S_CURRENT_TEAM_OR_TARGET_TEAM_IS_CURRENTLY_IN_THE_GAME_CANNOT_CHANGE_TEAMS;

    @ConfigOption(path = "team-gui.the-target-team-is-full")
    public static String TEAM_GUI_THE_TARGET_TEAM_IS_FULL;

    @ConfigOption(path = "team-gui.the-target-team-no-longer-exists")
    public static String TEAM_GUI_THE_TARGET_TEAM_NO_LONGER_EXISTS;

    @ConfigOption(path = "team-gui.the-team-no-longer-exists")
    public static String TEAM_GUI_THE_TEAM_NO_LONGER_EXISTS;

    @ConfigOption(
            path = "team-gui.this-color-has-just-been-taken-by-another-team-please-choose-again")
    public static String
            TEAM_GUI_THIS_COLOR_HAS_JUST_BEEN_TAKEN_BY_ANOTHER_TEAM_PLEASE_CHOOSE_AGAIN;

    @ConfigOption(path = "team-gui.unable-to-delete-team-team-may-be-in-game")
    public static String TEAM_GUI_UNABLE_TO_DELETE_TEAM_TEAM_MAY_BE_IN_GAME;

    @ConfigOption(path = "team-gui.unable-to-read-player-history")
    public static String TEAM_GUI_UNABLE_TO_READ_PLAYER_HISTORY;

    @ConfigOption(
            path = "team-gui.you-no-longer-have-permission-to-use-the-team-management-interface")
    public static String
            TEAM_GUI_YOU_NO_LONGER_HAVE_PERMISSION_TO_USE_THE_TEAM_MANAGEMENT_INTERFACE;

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

    @ConfigOption(path = "member.successfully-added")
    public static String MEMBER_SUCCESSFULLY_ADDED;

    @ConfigOption(path = "member.added-failed")
    public static String MEMBER_ADDED_FAILED;

    @ConfigOption(path = "member.successfully-deleted")
    public static String MEMBER_SUCCESSFULLY_DELETED;

    @ConfigOption(path = "member.deleted-failed")
    public static String MEMBER_DELETED_FAILED;
}
