package ink.ziip.championshipscore.configuration.config.message;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.configuration.ConfigOption;
import ink.ziip.championshipscore.configuration.config.BaseConfigurationFile;
import lombok.Getter;

import java.util.List;

@Getter
public class ScheduleMessageConfig extends BaseConfigurationFile {
    private final String fileName = "schedule-message.yml";
    private final String resourceName = "schedule-message.yml";

    public ScheduleMessageConfig(ChampionshipsCore plugin) {
        super(plugin);
    }

    @Override
    public int getLatestVersion() {
        return 2;
    }

    @ConfigOption(path = "parkour-tag")
    public static List<String> PARKOUR_TAG;

    @ConfigOption(path = "parkour-tag-points")
    public static List<String> PARKOUR_TAG_POINTS;

    @ConfigOption(path = "battle-box")
    public static List<String> BATTLE_BOX;

    @ConfigOption(path = "battle-box-points")
    public static List<String> BATTLE_BOX_POINTS;

    @ConfigOption(path = "tnt-run")
    public static List<String> TNT_RUN;

    @ConfigOption(path = "tnt-run-points")
    public static List<String> TNT_RUN_POINTS;

    @ConfigOption(path = "sky-wars")
    public static List<String> SKY_WARS;

    @ConfigOption(path = "sky-wars-points")
    public static List<String> SKY_WARS_POINTS;

    @ConfigOption(path = "tgttos")
    public static List<String> TGTTOS;

    @ConfigOption(path = "tgttos-points")
    public static List<String> TGTTOS_POINTS;

    @ConfigOption(path = "snowball")
    public static List<String> SNOWBALL;

    @ConfigOption(path = "snowball-points")
    public static List<String> SNOWBALL_POINTS;

    @ConfigOption(path = "dragon-egg-carnival")
    public static List<String> DRAGON_EGG_CARNIVAL;

    @ConfigOption(path = "dragon-egg-carnival-points")
    public static List<String> DRAGON_EGG_CARNIVAL_POINTS;

    @ConfigOption(path = "parkour-warrior")
    public static List<String> PARKOUR_WARRIOR;

    @ConfigOption(path = "parkour-warrior-points")
    public static List<String> PARKOUR_WARRIOR_POINTS;

    @ConfigOption(path = "hoty-cody-dusky")
    public static List<String> HOTY_CODY_DUSKY;

    @ConfigOption(path = "hoty-cody-dusky-points")
    public static List<String> HOTY_CODY_DUSKY_POINTS;

    @ConfigOption(path = "next-round-soon")
    public static List<String> NEXT_ROUND_SOON;

    @ConfigOption(path = "round-end")
    public static List<String> ROUND_END;
}
