package ink.ziip.championshipscore.redis;

import java.time.Duration;
import java.util.Objects;

public record RedisTransportConfig(
        String uri,
        String namespace,
        String workerId,
        long approximateMaxStreamLength,
        Duration commandTimeout
) {
    public RedisTransportConfig {
        Objects.requireNonNull(uri, "uri");
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(workerId, "workerId");
        Objects.requireNonNull(commandTimeout, "commandTimeout");
        if (uri.isBlank() || namespace.isBlank() || workerId.isBlank()) {
            throw new IllegalArgumentException("uri, namespace and workerId must not be blank");
        }
        if (approximateMaxStreamLength < 1) {
            throw new IllegalArgumentException("approximateMaxStreamLength must be positive");
        }
        if (commandTimeout.isZero() || commandTimeout.isNegative()) {
            throw new IllegalArgumentException("commandTimeout must be positive");
        }
    }

    public String commandStream() {
        return namespace + ":bingo:commands:" + workerId;
    }

    public String eventStream() {
        return namespace + ":bingo:events";
    }

    public String manifestKey(java.util.UUID matchId, long epoch) {
        return namespace + ":bingo:manifest:" + matchId + ":" + epoch;
    }
}
