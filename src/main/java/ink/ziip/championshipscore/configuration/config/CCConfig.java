package ink.ziip.championshipscore.configuration.config;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.configuration.ConfigOption;
import lombok.Getter;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;

import java.util.List;

@Getter
public class CCConfig extends BaseConfigurationFile {
    private final String fileName = "config.yml";
    private final String resourceName = "config.yml";

    public CCConfig(ChampionshipsCore plugin) {
        super(plugin);
    }

    @Override
    public int getLatestVersion() {
        return 3;
    }

    // Mode
    @ConfigOption(path = "mode")
    public static String MODE;

    // Players
    @ConfigOption(path = "max-players")
    public static int MAX_PLAYERS;

    @ConfigOption(path = "whitelist")
    public static List<String> WHITELIST;

    // Score
    @ConfigOption(path = "weighted-score")
    public static Boolean WEIGHTED_SCORE;

    // Chat
    @ConfigOption(path = "chat.refugee")
    public static String CHAT_REFUGEE;

    @ConfigOption(path = "chat.player")
    public static String CHAT_PLAYER;

    @ConfigOption(path = "chat.spectator")
    public static String CHAT_SPECTATOR;

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

    @ConfigOption(path = "parkourtag.rounds")
    public static ConfigurationSection PARKOUR_TAG_ROUNDS;

    // BattleBox
    @ConfigOption(path = "battlebox.rounds")
    public static ConfigurationSection BATTLE_BOX_ROUNDS;
}
