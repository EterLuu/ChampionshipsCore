package ink.ziip.championshipscore.redis;

import java.util.Objects;

/** Stable, per-application-instance consumer-group names used for Redis Stream fan-out. */
public final class RedisGroupNames {
    private RedisGroupNames() {
    }

    public static String databaseSync(String prefix, String instanceId) {
        return group(prefix, "data", instanceId);
    }

    public static String bingoEvents(String prefix, String instanceId) {
        return group(prefix, "bingo", instanceId);
    }

    public static String chat(String prefix, String instanceId) {
        return group(prefix, "chat", instanceId);
    }

    private static String group(String prefix, String purpose, String instanceId) {
        Objects.requireNonNull(prefix, "prefix");
        Objects.requireNonNull(instanceId, "instanceId");
        if (prefix.isBlank() || instanceId.isBlank())
            throw new IllegalArgumentException("prefix and instanceId must not be blank");
        return prefix + ":" + purpose + ":" + instanceId;
    }
}
