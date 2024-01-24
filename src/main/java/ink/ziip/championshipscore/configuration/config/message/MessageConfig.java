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

    // Area
    @ConfigOption(path = "area.successfully-added")
    public static String AREA_SUCCESSFULLY_ADDED;

    @ConfigOption(path = "area.added-failed")
    public static String AREA_ADDED_FAILED;

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
}
