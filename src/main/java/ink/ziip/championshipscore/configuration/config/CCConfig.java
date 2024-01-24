package ink.ziip.championshipscore.configuration.config;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.configuration.ConfigOption;
import lombok.Getter;
import org.bukkit.Location;

@Getter
public class CCConfig extends BaseConfigurationFile {
    private final String fileName = "config.yml";
    private final String resourceName = "config.yml";

    public CCConfig(ChampionshipsCore plugin) {
        super(plugin);
    }

    @Override
    public int getLatestVersion() {
        return 1;
    }

    // Mode
    @ConfigOption(path = "mode")
    public static String MODE;

    // Database
    @ConfigOption(path = "database.type")
    public static String DATABASE_TYPE;

    @ConfigOption(path = "database.address")
    public static String DATABASE_ADDRESS;

    @ConfigOption(path = "database.port")
    public static int DATABASE_PORT;

    @ConfigOption(path = "database.name")
    public static String DATABASE_NAME;

    @ConfigOption(path = "database.username")
    public static String DATABASE_USERNAME;

    @ConfigOption(path = "database.password")
    public static String DATABASE_PASSWORD;

    // Team
    @ConfigOption(path = "team.max-members")
    public static int TEAM_MAX_MEMBERS;

    // Lobby
    @ConfigOption(path = "lobby.location")
    public static Location LOBBY_LOCATION;

    // Bingo
    @ConfigOption(path = "bingo.spawn")
    public static Location BINGO_SPAWN_LOCATION;

    // ParkourTag
    @ConfigOption(path = "parkourtag.max-chaser-times")
    public static Integer PARKOUR_TAG_MAX_CHASER_TIMES;
}
