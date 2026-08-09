package ink.ziip.championshipscore.redis;

import ink.ziip.championshipscore.protocol.BinaryProtocolCodec;
import ink.ziip.championshipscore.protocol.DeterministicIds;
import ink.ziip.championshipscore.protocol.MatchCommand;
import ink.ziip.championshipscore.protocol.MatchEvent;
import ink.ziip.championshipscore.protocol.MatchManifest;
import ink.ziip.championshipscore.protocol.transport.DeliveryReceipt;
import ink.ziip.championshipscore.protocol.transport.MatchCommandPublisher;
import ink.ziip.championshipscore.protocol.transport.MatchEventPublisher;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.XAddArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;

import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;

/** Long-lived asynchronous Redis Streams publisher shared by SCC and worker plugins. */
public final class RedisMatchTransport implements MatchCommandPublisher, MatchEventPublisher {
    private final RedisTransportConfig config;
    private final BinaryProtocolCodec codec;
    private final RedisClient client;
    private final StatefulRedisConnection<String, String> connection;
    private final RedisAsyncCommands<String, String> commands;
    private final AtomicBoolean closed = new AtomicBoolean();

    public RedisMatchTransport(RedisTransportConfig config) {
        this(config, new BinaryProtocolCodec());
    }

    RedisMatchTransport(RedisTransportConfig config, BinaryProtocolCodec codec) {
        this.config = Objects.requireNonNull(config, "config");
        this.codec = Objects.requireNonNull(codec, "codec");
        RedisURI uri = RedisURI.create(config.uri());
        uri.setTimeout(config.commandTimeout());
        this.client = RedisClient.create(uri);
        this.connection = client.connect();
        this.commands = connection.async();
    }

    public CompletionStage<String> ping() {
        requireOpen();
        return commands.ping();
    }

    @Override
    public CompletionStage<DeliveryReceipt> publishManifest(MatchManifest manifest) {
        requireOpen();
        Objects.requireNonNull(manifest, "manifest");
        if (!config.workerId().equals(manifest.workerId())) {
            return CompletableFuture.failedFuture(new IllegalArgumentException(
                    "Manifest targets worker " + manifest.workerId() + " but transport targets " + config.workerId()));
        }
        UUID messageId = DeterministicIds.uuidV5(manifest.matchId(), "manifest:" + manifest.epoch());
        String payload = Base64.getEncoder().encodeToString(codec.encodeManifest(manifest));
        Map<String, String> fields = baseFields("manifest", messageId, manifest.matchId(), manifest.epoch());
        fields.put("payload", payload);

        // Keep the latest manifest addressable for reconnect/recovery and also append it to the worker's
        // ordered command stream. SET precedes XADD; a missing stream entry is detectable and retryable
        // through the deterministic message ID, while consumers never see an absent manifest value.
        return commands.set(config.manifestKey(manifest.matchId(), manifest.epoch()), payload)
                .thenCompose(ignored -> append(config.commandStream(), fields, messageId));
    }

    @Override
    public CompletionStage<DeliveryReceipt> publishCommand(MatchCommand command) {
        requireOpen();
        Objects.requireNonNull(command, "command");
        Map<String, String> fields = baseFields(
                "command", command.messageId(), command.matchId(), command.epoch());
        fields.put("commandType", command.type().name());
        fields.put("payload", Base64.getEncoder().encodeToString(codec.encodeCommand(command)));
        return append(config.commandStream(), fields, command.messageId());
    }

    @Override
    public CompletionStage<DeliveryReceipt> publishEvent(MatchEvent event) {
        requireOpen();
        Objects.requireNonNull(event, "event");
        Map<String, String> fields = baseFields("event", event.messageId(), event.matchId(), event.epoch());
        fields.put("eventType", event.type().name());
        fields.put("seq", Long.toString(event.seq()));
        fields.put("workerId", config.workerId());
        fields.put("payload", Base64.getEncoder().encodeToString(codec.encodeEvent(event)));
        return append(config.eventStream(), fields, event.messageId());
    }

    private CompletionStage<DeliveryReceipt> append(
            String stream, Map<String, String> fields, UUID messageId) {
        XAddArgs args = new XAddArgs().maxlen(config.approximateMaxStreamLength()).approximateTrimming();
        return commands.xadd(stream, args, fields).thenApply(position ->
                new DeliveryReceipt(messageId, stream, position, Instant.now().toEpochMilli()));
    }

    private static Map<String, String> baseFields(
            String kind, UUID messageId, UUID matchId, long epoch) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("kind", kind);
        fields.put("messageId", messageId.toString());
        fields.put("matchId", matchId.toString());
        fields.put("epoch", Long.toString(epoch));
        return fields;
    }

    private void requireOpen() {
        if (closed.get()) throw new IllegalStateException("Redis transport is closed");
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        connection.close();
        client.shutdown();
    }
}
