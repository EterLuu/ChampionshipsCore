package ink.ziip.championshipscore.worker;

import ink.ziip.championshipscore.redis.RedisConsumerConfig;
import ink.ziip.championshipscore.redis.RedisTransportConfig;
import org.bukkit.configuration.file.FileConfiguration;

import java.time.Duration;

record WorkerConfig(
        boolean enabled,
        String workerId,
        RedisTransportConfig redis,
        RedisConsumerConfig consumer,
        String proxyChannel,
        String returnServer,
        String overworld,
        String nether,
        String end,
        boolean allowWorldReuseWithoutReset
) {
    static WorkerConfig load(FileConfiguration config) {
        String workerId = text(config, "worker-id");
        RedisTransportConfig redis = new RedisTransportConfig(
                text(config, "redis.uri"), text(config, "redis.namespace"), workerId,
                config.getLong("redis.stream-max-length", 100_000),
                Duration.ofSeconds(5));
        RedisConsumerConfig consumer = new RedisConsumerConfig(
                text(config, "redis.consumer-group"), workerId,
                32,
                Duration.ofMillis(positive(config, "redis.block-timeout-ms", 2_000)),
                Duration.ofMillis(positive(config, "redis.reclaim-idle-ms", 15_000)),
                positive(config, "redis.max-deliveries", 8));
        return new WorkerConfig(config.getBoolean("enabled", false), workerId, redis, consumer,
                text(config, "proxy.channel"), text(config, "proxy.return-server"),
                text(config, "worlds.overworld"), text(config, "worlds.nether"),
                text(config, "worlds.the-end"), config.getBoolean("worlds.allow-reuse-without-reset", false));
    }

    private static int positive(FileConfiguration config, String path, int fallback) {
        int value = config.getInt(path, fallback);
        if (value < 1) throw new IllegalArgumentException(path + " must be positive");
        return value;
    }

    private static String text(FileConfiguration config, String path) {
        String value = config.getString(path);
        if (value == null || value.isBlank()) throw new IllegalArgumentException(path + " must not be blank");
        return value.trim();
    }
}
