package ink.ziip.championshipscore.configuration.config.message;

import ink.ziip.championshipscore.configuration.ConfigOption;

/** Message fields owned by the Daily domain; persisted by MessageConfig. */
public final class DailyMessages {
    private DailyMessages() {}

    @ConfigOption(path = "vote.unavailable-played")
    public static String VOTE_UNAVAILABLE_PLAYED;

    @ConfigOption(path = "vote.unavailable-unpublished")
    public static String VOTE_UNAVAILABLE_UNPUBLISHED;

    @ConfigOption(path = "vote.vote-failed-unpublished")
    public static String VOTE_VOTE_FAILED_UNPUBLISHED;

    @ConfigOption(path = "daily.prefix")
    public static String DAILY_PREFIX;

    @ConfigOption(path = "daily.prefixed")
    public static String DAILY_PREFIXED;

    @ConfigOption(path = "daily.mode.championship")
    public static String DAILY_MODE_CHAMPIONSHIP;

    @ConfigOption(path = "daily.mode.free-play")
    public static String DAILY_MODE_FREE_PLAY;

    @ConfigOption(path = "daily.state.idle")
    public static String DAILY_STATE_IDLE;

    @ConfigOption(path = "daily.state.selected")
    public static String DAILY_STATE_SELECTED;

    @ConfigOption(path = "daily.state.waiting-member")
    public static String DAILY_STATE_WAITING_MEMBER;

    @ConfigOption(path = "daily.state.queued")
    public static String DAILY_STATE_QUEUED;

    @ConfigOption(path = "daily.state.playing")
    public static String DAILY_STATE_PLAYING;

    @ConfigOption(path = "daily.team-name")
    public static String DAILY_TEAM_NAME;

    @ConfigOption(path = "daily.unavailable")
    public static String DAILY_UNAVAILABLE;

    @ConfigOption(path = "daily.already-playing")
    public static String DAILY_ALREADY_PLAYING;

    @ConfigOption(path = "daily.game-unavailable")
    public static String DAILY_GAME_UNAVAILABLE;

    @ConfigOption(path = "daily.team-names")
    public static java.util.List<String> DAILY_TEAM_NAMES;

    @ConfigOption(path = "daily.team-suffix")
    public static String DAILY_TEAM_SUFFIX;

    @ConfigOption(path = "daily.party-too-large")
    public static String DAILY_PARTY_TOO_LARGE;

    @ConfigOption(path = "daily.party-member-unavailable")
    public static String DAILY_PARTY_MEMBER_UNAVAILABLE;

    @ConfigOption(path = "daily.already-queued")
    public static String DAILY_ALREADY_QUEUED;

    @ConfigOption(path = "daily.queue-unavailable")
    public static String DAILY_QUEUE_UNAVAILABLE;

    @ConfigOption(path = "daily.queue-migration-failed")
    public static String DAILY_QUEUE_MIGRATION_FAILED;

    @ConfigOption(path = "daily.queue-selected")
    public static String DAILY_QUEUE_SELECTED;

    @ConfigOption(path = "daily.queue-left")
    public static String DAILY_QUEUE_LEFT;

    @ConfigOption(path = "daily.not-in-play")
    public static String DAILY_NOT_IN_PLAY;

    @ConfigOption(path = "daily.play-left")
    public static String DAILY_PLAY_LEFT;

    @ConfigOption(path = "daily.queue-ready")
    public static String DAILY_QUEUE_READY;

    @ConfigOption(path = "daily.queue-countdown")
    public static String DAILY_QUEUE_COUNTDOWN;

    @ConfigOption(path = "daily.queue-composition-failed")
    public static String DAILY_QUEUE_COMPOSITION_FAILED;

    @ConfigOption(path = "daily.queue-no-arena")
    public static String DAILY_QUEUE_NO_ARENA;

    @ConfigOption(path = "daily.match-assigned")
    public static String DAILY_MATCH_ASSIGNED;

    @ConfigOption(path = "daily.match-aborted")
    public static String DAILY_MATCH_ABORTED;

    @ConfigOption(path = "daily.bossbar.waiting")
    public static String DAILY_BOSSBAR_WAITING;

    @ConfigOption(path = "daily.bossbar.countdown")
    public static String DAILY_BOSSBAR_COUNTDOWN;

    @ConfigOption(path = "daily.bossbar.needs-group")
    public static String DAILY_BOSSBAR_NEEDS_GROUP;

    @ConfigOption(path = "daily.queue-clear.championship")
    public static String DAILY_QUEUE_CLEAR_CHAMPIONSHIP;

    @ConfigOption(path = "daily.queue-clear.reload")
    public static String DAILY_QUEUE_CLEAR_RELOAD;

    @ConfigOption(path = "daily.queue-paused-member")
    public static String DAILY_QUEUE_PAUSED_MEMBER;

    @ConfigOption(path = "daily.leaderboard.row-count")
    public static String DAILY_LEADERBOARD_ROW_COUNT;

    @ConfigOption(path = "daily.leaderboard.row-time")
    public static String DAILY_LEADERBOARD_ROW_TIME;

    @ConfigOption(path = "daily.leaderboard.empty")
    public static String DAILY_LEADERBOARD_EMPTY;

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

    @ConfigOption(path = "vote.vote-failed-admin")
    public static String VOTE_VOTE_FAILED_ADMIN;

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
}
