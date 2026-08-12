package ink.ziip.championshipscore.redis;

import java.time.Duration;
import java.util.Objects;

/** Connection-level Redis settings shared by every Core feature. */
public record RedisConnectionConfig(
        String uri,
        String namespace,
        String instanceId,
        long approximateMaxStreamLength,
        Duration commandTimeout
) {
    public RedisConnectionConfig {
        Objects.requireNonNull(uri, "uri");
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(instanceId, "instanceId");
        Objects.requireNonNull(commandTimeout, "commandTimeout");
        if (uri.isBlank() || namespace.isBlank() || instanceId.isBlank())
            throw new IllegalArgumentException("uri, namespace and instanceId must not be blank");
        if (approximateMaxStreamLength < 1)
            throw new IllegalArgumentException("approximateMaxStreamLength must be positive");
        if (commandTimeout.isZero() || commandTimeout.isNegative())
            throw new IllegalArgumentException("commandTimeout must be positive");
    }

    public String key(String suffix) {
        Objects.requireNonNull(suffix, "suffix");
        if (suffix.isBlank()) throw new IllegalArgumentException("suffix must not be blank");
        return namespace + ":" + suffix;
    }
}
