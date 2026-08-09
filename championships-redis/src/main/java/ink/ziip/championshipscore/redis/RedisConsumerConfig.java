package ink.ziip.championshipscore.redis;

import java.time.Duration;
import java.util.Objects;

/** Consumer-group tuning shared by command, event and route consumers. */
public record RedisConsumerConfig(
        String group,
        String consumer,
        int batchSize,
        Duration blockTimeout,
        Duration reclaimIdle,
        int maxDeliveries
) {
    public RedisConsumerConfig {
        group = requireText(group, "group");
        consumer = requireText(consumer, "consumer");
        Objects.requireNonNull(blockTimeout, "blockTimeout");
        Objects.requireNonNull(reclaimIdle, "reclaimIdle");
        if (batchSize < 1 || batchSize > 1_000) {
            throw new IllegalArgumentException("batchSize must be between 1 and 1000");
        }
        if (blockTimeout.isNegative() || blockTimeout.isZero()) {
            throw new IllegalArgumentException("blockTimeout must be positive");
        }
        if (reclaimIdle.isNegative() || reclaimIdle.isZero()) {
            throw new IllegalArgumentException("reclaimIdle must be positive");
        }
        if (maxDeliveries < 1) throw new IllegalArgumentException("maxDeliveries must be positive");
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }
}
