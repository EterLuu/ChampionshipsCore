package ink.ziip.championshipscore.redis;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.XAddArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;

/** Generic long-lived Redis Stream publisher; application protocols own their field schema. */
public final class RedisStreamPublisher implements AutoCloseable {
    private final RedisConnectionConfig config;
    private final RedisClient client;
    private final StatefulRedisConnection<String, String> connection;
    private final RedisAsyncCommands<String, String> commands;
    private final AtomicBoolean closed = new AtomicBoolean();

    public RedisStreamPublisher(RedisConnectionConfig config) {
        this.config = Objects.requireNonNull(config, "config");
        RedisURI uri = RedisURI.create(config.uri());
        uri.setTimeout(config.commandTimeout());
        client = RedisClient.create(uri);
        connection = client.connect();
        commands = connection.async();
    }

    public CompletionStage<String> ping() {
        requireOpen();
        return commands.ping();
    }

    public CompletionStage<String> append(String stream, Map<String, String> fields) {
        requireOpen();
        if (stream == null || stream.isBlank()) throw new IllegalArgumentException("stream must not be blank");
        Objects.requireNonNull(fields, "fields");
        if (fields.isEmpty()) throw new IllegalArgumentException("fields must not be empty");
        Map<String, String> copy = new LinkedHashMap<>(fields);
        if (copy.entrySet().stream().anyMatch(entry -> entry.getKey() == null || entry.getKey().isBlank()
                || entry.getValue() == null)) throw new IllegalArgumentException("fields contain null/blank keys");
        XAddArgs args = new XAddArgs().maxlen(config.approximateMaxStreamLength()).approximateTrimming();
        return commands.xadd(stream, args, copy);
    }

    private void requireOpen() {
        if (closed.get()) throw new IllegalStateException("Redis publisher is closed");
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        connection.close();
        client.shutdown();
    }
}
