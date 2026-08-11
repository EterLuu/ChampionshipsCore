package ink.ziip.championshipscore.configuration.config;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.configuration.ConfigOption;
import lombok.Getter;
import org.bukkit.Location;

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
        return 13;
    }

    // Games
    @ConfigOption(path = "enabled-games")
    public static List<String> ENABLED_GAMES;

    // Mode
    @ConfigOption(path = "mode")
    public static String MODE;

    // Daily public-play mode. Only games with a registered DailyGameAdapter are admitted.
    @ConfigOption(path = "daily.enabled-games")
    public static List<String> DAILY_ENABLED_GAMES;

    @ConfigOption(path = "daily.games.Bingo.min-players")
    public static int DAILY_BINGO_MIN_PLAYERS;

    @ConfigOption(path = "daily.games.Bingo.max-players")
    public static int DAILY_BINGO_MAX_PLAYERS;

    @ConfigOption(path = "daily.games.Bingo.team-size")
    public static int DAILY_BINGO_TEAM_SIZE;

    @ConfigOption(path = "daily.games.Bingo.teams")
    public static int DAILY_BINGO_TEAMS;

    @ConfigOption(path = "daily.games.Bingo.countdown-seconds")
    public static int DAILY_BINGO_COUNTDOWN_SECONDS;

    @ConfigOption(path = "daily.games.AceRace.min-players")
    public static int DAILY_ACERACE_MIN_PLAYERS;

    @ConfigOption(path = "daily.games.AceRace.max-players")
    public static int DAILY_ACERACE_MAX_PLAYERS;

    @ConfigOption(path = "daily.games.AceRace.team-size")
    public static int DAILY_ACERACE_TEAM_SIZE;

    @ConfigOption(path = "daily.games.AceRace.teams")
    public static int DAILY_ACERACE_TEAMS;

    @ConfigOption(path = "daily.games.AceRace.countdown-seconds")
    public static int DAILY_ACERACE_COUNTDOWN_SECONDS;

    @ConfigOption(path = "daily.games.AceRace.concurrent-instances")
    public static int DAILY_ACERACE_CONCURRENT_INSTANCES;

    // Players
    @ConfigOption(path = "max-players")
    public static int MAX_PLAYERS;

    @ConfigOption(path = "whitelist")
    public static List<String> WHITELIST;

    // Score
    @ConfigOption(path = "weighted-score")
    public static Boolean WEIGHTED_SCORE;

    //Spectator
    @ConfigOption(path = "strict-spectator-rule")
    public static Boolean STRICT_SPECTATOR_RULE;

    // Chat
    @ConfigOption(path = "chat.refugee")
    public static String CHAT_REFUGEE;

    @ConfigOption(path = "chat.player")
    public static String CHAT_PLAYER;

    @ConfigOption(path = "chat.spectator")
    public static String CHAT_SPECTATOR;

    @ConfigOption(path = "chat.daily")
    public static String CHAT_DAILY;

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

    // ParkourTag
    @ConfigOption(path = "parkourtag.max-chaser-times")
    public static Integer PARKOUR_TAG_MAX_CHASER_TIMES;

    // Remote Bingo execution
    @ConfigOption(path = "bingo.execution-mode")
    public static String BINGO_EXECUTION_MODE;

    @ConfigOption(path = "bingo.worker-id")
    public static String BINGO_WORKER_ID;

    @ConfigOption(path = "bingo.worker-server")
    public static String BINGO_WORKER_SERVER;

    @ConfigOption(path = "bingo.proxy-channel")
    public static String BINGO_PROXY_CHANNEL;

    @ConfigOption(path = "bingo.ready-timeout-seconds")
    public static int BINGO_READY_TIMEOUT_SECONDS;

    @ConfigOption(path = "bingo.arrival-timeout-seconds")
    public static int BINGO_ARRIVAL_TIMEOUT_SECONDS;

    @ConfigOption(path = "bingo.heartbeat-timeout-seconds")
    public static int BINGO_HEARTBEAT_TIMEOUT_SECONDS;

    @ConfigOption(path = "bingo.redis.uri")
    public static String BINGO_REDIS_URI;

    @ConfigOption(path = "bingo.redis.namespace")
    public static String BINGO_REDIS_NAMESPACE;

    @ConfigOption(path = "bingo.redis.consumer-group")
    public static String BINGO_REDIS_CONSUMER_GROUP;

    @ConfigOption(path = "bingo.redis.stream-max-length")
    public static long BINGO_REDIS_STREAM_MAX_LENGTH;

    @ConfigOption(path = "bingo.redis.block-timeout-ms")
    public static long BINGO_REDIS_BLOCK_TIMEOUT_MILLIS;

    @ConfigOption(path = "bingo.redis.reclaim-idle-ms")
    public static long BINGO_REDIS_RECLAIM_IDLE_MILLIS;

    @ConfigOption(path = "bingo.redis.max-deliveries")
    public static int BINGO_REDIS_MAX_DELIVERIES;
}
