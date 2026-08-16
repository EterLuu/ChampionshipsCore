package ink.ziip.championshipscore.configuration.config;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.configuration.ConfigOption;
import lombok.Getter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
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
        return 16;
    }

    /** Migrates the Bingo-owned Redis connection into the shared Core infrastructure section. */
    @Override
    public void loadFromOutdatedConfiguration(@NotNull YamlConfiguration outdated) throws IOException {
        migrateLegacyRedisConfiguration(outdated);
        super.loadFromOutdatedConfiguration(outdated);
    }

    static void migrateLegacyRedisConfiguration(@NotNull YamlConfiguration outdated) {
        if (!outdated.contains("redis.uri") && outdated.contains("bingo.redis.uri")) {
            outdated.set("redis.enabled", "REMOTE".equalsIgnoreCase(
                    outdated.getString("bingo.execution-mode", "LOCAL")));
            outdated.set("redis.instance-id", "auto");
            outdated.set("redis.uri", outdated.get("bingo.redis.uri"));
            outdated.set("redis.namespace", outdated.get("bingo.redis.namespace"));
            outdated.set("redis.consumer-group-prefix", outdated.get("bingo.redis.consumer-group"));
            outdated.set("redis.stream-max-length", outdated.get("bingo.redis.stream-max-length"));
            outdated.set("redis.block-timeout-ms", outdated.get("bingo.redis.block-timeout-ms"));
            outdated.set("redis.reclaim-idle-ms", outdated.get("bingo.redis.reclaim-idle-ms"));
            outdated.set("redis.max-deliveries", outdated.get("bingo.redis.max-deliveries"));
            outdated.set("redis.reconciliation-seconds", 30);
        }
        outdated.set("bingo.redis", null);
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

    @ConfigOption(path = "daily.games.DragonEggCarnival.min-players")
    public static int DAILY_DRAGON_EGG_CARNIVAL_MIN_PLAYERS;

    @ConfigOption(path = "daily.games.DragonEggCarnival.max-players")
    public static int DAILY_DRAGON_EGG_CARNIVAL_MAX_PLAYERS;

    @ConfigOption(path = "daily.games.DragonEggCarnival.team-size")
    public static int DAILY_DRAGON_EGG_CARNIVAL_TEAM_SIZE;

    @ConfigOption(path = "daily.games.DragonEggCarnival.teams")
    public static int DAILY_DRAGON_EGG_CARNIVAL_TEAMS;

    @ConfigOption(path = "daily.games.DragonEggCarnival.countdown-seconds")
    public static int DAILY_DRAGON_EGG_CARNIVAL_COUNTDOWN_SECONDS;

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

    // Shared Redis infrastructure. Remote Bingo and cross-server database invalidation use one owner.
    @ConfigOption(path = "redis.enabled")
    public static Boolean REDIS_ENABLED;

    @ConfigOption(path = "redis.instance-id")
    public static String REDIS_INSTANCE_ID;

    @ConfigOption(path = "redis.uri")
    public static String REDIS_URI;

    @ConfigOption(path = "redis.namespace")
    public static String REDIS_NAMESPACE;

    @ConfigOption(path = "redis.consumer-group-prefix")
    public static String REDIS_CONSUMER_GROUP_PREFIX;

    @ConfigOption(path = "redis.stream-max-length")
    public static long REDIS_STREAM_MAX_LENGTH;

    @ConfigOption(path = "redis.block-timeout-ms")
    public static long REDIS_BLOCK_TIMEOUT_MILLIS;

    @ConfigOption(path = "redis.reclaim-idle-ms")
    public static long REDIS_RECLAIM_IDLE_MILLIS;

    @ConfigOption(path = "redis.max-deliveries")
    public static int REDIS_MAX_DELIVERIES;

    @ConfigOption(path = "redis.reconciliation-seconds")
    public static long REDIS_RECONCILIATION_SECONDS;

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

}
