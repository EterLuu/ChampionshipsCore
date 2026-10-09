package ink.ziip.championshipscore.redis;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseManager;
import ink.ziip.championshipscore.configuration.config.CCConfig;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.database.sync.DatabaseSyncDomain;
import ink.ziip.championshipscore.database.sync.DatabaseSyncEvent;
import ink.ziip.championshipscore.logging.LogText;
import ink.ziip.championshipscore.protocol.CrossServerChatMessage;
import ink.ziip.championshipscore.protocol.transport.DeliveryDisposition;
import ink.ziip.championshipscore.protocol.transport.DeliveryHandler;
import ink.ziip.championshipscore.protocol.transport.MatchInboundMessage;

import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.logging.Level;

/** One Core-owned Redis lifecycle for database invalidation and remote-game transports. */
public final class RedisManager extends BaseManager {
    private static final int DEDUPLICATION_LIMIT = 4096;
    private static final int PENDING_PUBLICATION_LIMIT = 10_000;
    private final Map<UUID, Boolean> processedEvents =
            java.util.Collections.synchronizedMap(
                    new LinkedHashMap<>(DEDUPLICATION_LIMIT, .75F, true) {
                        @Override
                        protected boolean removeEldestEntry(Map.Entry<UUID, Boolean> eldest) {
                            return size() > DEDUPLICATION_LIMIT;
                        }
                    });
    private final BlockingQueue<DatabaseSyncEvent> pendingPublications =
            new ArrayBlockingQueue<>(PENDING_PUBLICATION_LIMIT);
    private final Map<String, RedisMatchTransport> matchTransports = new ConcurrentHashMap<>();
    private final Set<RedisMatchConsumer> matchConsumers = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean ready = new AtomicBoolean();
    private final AtomicBoolean connecting = new AtomicBoolean();
    private final AtomicBoolean reconciling = new AtomicBoolean();
    private final AtomicBoolean stopping = new AtomicBoolean();
    private final AtomicInteger connectionFailures = new AtomicInteger();
    private volatile CompletableFuture<Void> readyFuture = new CompletableFuture<>();
    private final AtomicLong lifecycleGeneration = new AtomicLong();
    private RedisConnectionConfig connectionConfig;
    private volatile RedisStreamPublisher publisher;
    private RedisStreamConsumer syncConsumer;
    private volatile RedisChatTransport chatTransport;
    private volatile Consumer<CrossServerChatMessage> chatReceiver = ignored -> {};
    private BukkitTask reconciliationTask;
    private BukkitTask connectionRetryTask;
    private String instanceId;
    private boolean configuredEnabled;
    private String configuredUri;
    private String configuredNamespace;
    private String configuredGroupPrefix;
    private long configuredStreamMaxLength;
    private long configuredBlockTimeoutMillis;
    private long configuredReclaimIdleMillis;
    private int configuredMaxDeliveries;

    public RedisManager(ChampionshipsCore plugin) {
        super(plugin);
    }

    @Override
    public synchronized void load() {
        if (reconciliationTask != null || connectionRetryTask != null || ready.get()) unload();
        long generation = lifecycleGeneration.incrementAndGet();
        stopping.set(false);
        ready.set(false);
        connecting.set(false);
        reconciling.set(false);
        connectionFailures.set(0);
        readyFuture = new CompletableFuture<>();
        configuredEnabled = Boolean.TRUE.equals(CCConfig.REDIS_ENABLED);
        configuredUri = CCConfig.REDIS_URI;
        configuredNamespace = CCConfig.REDIS_NAMESPACE;
        configuredGroupPrefix = CCConfig.REDIS_CONSUMER_GROUP_PREFIX;
        configuredStreamMaxLength = CCConfig.REDIS_STREAM_MAX_LENGTH;
        configuredBlockTimeoutMillis = CCConfig.REDIS_BLOCK_TIMEOUT_MILLIS;
        configuredReclaimIdleMillis = CCConfig.REDIS_RECLAIM_IDLE_MILLIS;
        configuredMaxDeliveries = CCConfig.REDIS_MAX_DELIVERIES;
        if (!configuredEnabled) {
            readyFuture.completeExceptionally(new IllegalStateException("Redis is disabled"));
            plugin.getLogger()
                    .info(
                            LogText.formatModuleLog(
                                    "Redis",
                                    MessageConfig.codeText("redis-manager.load-1"),
                                    MessageConfig.codeText("redis-manager.load-2")));
            return;
        }
        try {
            instanceId = resolveInstanceId();
            connectionConfig =
                    new RedisConnectionConfig(
                            configuredUri,
                            configuredNamespace,
                            instanceId,
                            configuredStreamMaxLength,
                            Duration.ofSeconds(5));
            long interval = Math.max(5L, CCConfig.REDIS_RECONCILIATION_SECONDS) * 20L;
            reconciliationTask =
                    plugin.getServer()
                            .getScheduler()
                            .runTaskTimer(
                                    plugin,
                                    () -> reconcileDatabaseCaches(generation),
                                    interval,
                                    interval);
            // Initial connection attempts run off the server thread. A Redis outage at boot leaves
            // database reconciliation active and is retried without requiring a Core restart.
            connectionRetryTask =
                    plugin.getServer()
                            .getScheduler()
                            .runTaskTimerAsynchronously(
                                    plugin, () -> ensureConnected(generation), 0L, 200L);
        } catch (RuntimeException failure) {
            readyFuture.completeExceptionally(failure);
            plugin.getLogger().log(Level.SEVERE, "Unified Redis initialization failed", failure);
            closeResources();
        }
    }

    private boolean isCurrent(long generation) {
        return !stopping.get() && lifecycleGeneration.get() == generation && plugin.isEnabled();
    }

    private synchronized void ensureConnected(long generation) {
        if (!isCurrent(generation) || ready.get() || !connecting.compareAndSet(false, true)) return;
        RedisStreamPublisher candidatePublisher = null;
        RedisStreamConsumer candidateConsumer = null;
        RedisChatTransport candidateChat = null;
        try {
            candidatePublisher = new RedisStreamPublisher(connectionConfig);
            RedisConsumerConfig consumerConfig =
                    new RedisConsumerConfig(
                            syncGroup(),
                            instanceId,
                            64,
                            Duration.ofMillis(configuredBlockTimeoutMillis),
                            Duration.ofMillis(configuredReclaimIdleMillis),
                            configuredMaxDeliveries);
            // A brand-new Core has already loaded the authoritative DB snapshot, so its new group
            // starts at the current tail. Existing stable groups still resume their pending cursor.
            candidateConsumer =
                    new RedisStreamConsumer(
                            connectionConfig,
                            consumerConfig,
                            dataSyncStream(),
                            "$",
                            delivery -> consumeDatabaseSync(generation, delivery),
                            failure ->
                                    plugin.getLogger()
                                            .log(
                                                    Level.SEVERE,
                                                    "Redis database-sync consumer failure",
                                                    failure));
            RedisConsumerConfig chatConsumerConfig =
                    new RedisConsumerConfig(
                            RedisGroupNames.chat(configuredGroupPrefix, instanceId),
                            instanceId,
                            64,
                            Duration.ofMillis(configuredBlockTimeoutMillis),
                            Duration.ofMillis(configuredReclaimIdleMillis),
                            configuredMaxDeliveries);
            candidateChat =
                    new RedisChatTransport(
                            connectionConfig,
                            chatConsumerConfig,
                            message -> {
                                if (isCurrent(generation)) receiveChat(message);
                            },
                            failure ->
                                    plugin.getLogger()
                                            .log(
                                                    Level.WARNING,
                                                    "Redis cross-server chat consumer failure",
                                                    failure));
            RedisStreamPublisher connectedPublisher = candidatePublisher;
            RedisStreamConsumer connectedConsumer = candidateConsumer;
            RedisChatTransport connectedChat = candidateChat;
            candidatePublisher
                    .ping()
                    .thenCompose(
                            ignored ->
                                    isCurrent(generation)
                                            ? connectedConsumer.start()
                                            : CompletableFuture.failedFuture(
                                                    new java.util.concurrent
                                                            .CancellationException()))
                    .thenCompose(
                            ignored ->
                                    isCurrent(generation)
                                            ? connectedChat.start()
                                            : CompletableFuture.failedFuture(
                                                    new java.util.concurrent
                                                            .CancellationException()))
                    .whenComplete(
                            (ignored, failure) ->
                                    finishConnectionAttempt(
                                            generation,
                                            connectedPublisher,
                                            connectedConsumer,
                                            connectedChat,
                                            failure));
        } catch (RuntimeException failure) {
            close(candidateChat);
            close(candidateConsumer);
            close(candidatePublisher);
            connectionAttemptFailed(generation, failure);
            if (isCurrent(generation)) connecting.set(false);
        }
    }

    private synchronized void finishConnectionAttempt(
            long generation,
            RedisStreamPublisher connectedPublisher,
            RedisStreamConsumer connectedConsumer,
            RedisChatTransport connectedChat,
            Throwable failure) {
        if (failure != null || !isCurrent(generation)) {
            close(connectedChat);
            close(connectedConsumer);
            close(connectedPublisher);
            if (failure != null && isCurrent(generation))
                connectionAttemptFailed(generation, failure);
            if (isCurrent(generation)) connecting.set(false);
            return;
        }
        publisher = connectedPublisher;
        syncConsumer = connectedConsumer;
        chatTransport = connectedChat;
        connectionFailures.set(0);
        ready.set(true);
        connecting.set(false);
        readyFuture.complete(null);
        if (connectionRetryTask != null) connectionRetryTask.cancel();
        flushPendingPublications();
        plugin.getLogger()
                .info(
                        LogText.formatModuleLog(
                                "Redis",
                                MessageConfig.codeText("redis-manager.load-1"),
                                MessageConfig.codeText(
                                        "redis-manager.finish-connection-attempt-1",
                                        instanceId,
                                        dataSyncStream(),
                                        RedisChatTransport.stream(connectionConfig))));
    }

    private void connectionAttemptFailed(long generation, Throwable failure) {
        if (!isCurrent(generation)) return;
        int attempts = connectionFailures.incrementAndGet();
        if (attempts == 1 || attempts % 6 == 0) {
            plugin.getLogger()
                    .log(
                            Level.WARNING,
                            "Unified Redis connection unavailable; retrying (attempt "
                                    + attempts
                                    + ")",
                            failure);
        }
    }

    public CompletionStage<Void> whenReady() {
        return readyFuture;
    }

    public boolean isReady() {
        return ready.get();
    }

    public String instanceId() {
        return instanceId;
    }

    public void setChatReceiver(Consumer<CrossServerChatMessage> receiver) {
        chatReceiver = receiver == null ? ignored -> {} : receiver;
    }

    public void publishChat(CrossServerChatMessage message) {
        RedisChatTransport current = chatTransport;
        if (!ready.get() || current == null) return;
        current.publish(message)
                .exceptionally(
                        failure -> {
                            plugin.getLogger()
                                    .log(
                                            Level.WARNING,
                                            "Unable to publish cross-server chat message "
                                                    + message.messageId(),
                                            failure);
                            return null;
                        });
    }

    private void receiveChat(CrossServerChatMessage message) {
        chatReceiver.accept(message);
    }

    public void publishDatabaseChange(
            @NotNull String reason,
            @NotNull DatabaseSyncDomain first,
            DatabaseSyncDomain... additional) {
        if (stopping.get()) return;
        EnumSet<DatabaseSyncDomain> domains = EnumSet.of(first, additional);
        DatabaseSyncEvent event =
                new DatabaseSyncEvent(
                        UUID.randomUUID(),
                        instanceId == null ? "starting" : instanceId,
                        System.currentTimeMillis(),
                        domains,
                        reason);
        if (!ready.get() || publisher == null) {
            if (configuredEnabled) enqueuePendingPublication(event);
            return;
        }
        publish(event);
    }

    public synchronized RedisMatchTransport matchTransport(@NotNull String workerId) {
        requireReady();
        return matchTransports.computeIfAbsent(
                workerId, id -> new RedisMatchTransport(matchTransportConfig(id)));
    }

    public RedisMatchConsumer createMatchEventConsumer(
            @NotNull String workerId,
            @NotNull DeliveryHandler<MatchInboundMessage> handler,
            @NotNull java.util.function.Consumer<Throwable> errors) {
        requireReady();
        RedisTransportConfig transportConfig = matchTransportConfig(workerId);
        RedisConsumerConfig consumerConfig =
                new RedisConsumerConfig(
                        RedisGroupNames.bingoEvents(configuredGroupPrefix, instanceId),
                        instanceId,
                        64,
                        Duration.ofMillis(configuredBlockTimeoutMillis),
                        Duration.ofMillis(configuredReclaimIdleMillis),
                        configuredMaxDeliveries);
        RedisMatchConsumer consumer =
                new RedisMatchConsumer(
                        transportConfig,
                        consumerConfig,
                        transportConfig.eventStream(),
                        handler,
                        errors);
        matchConsumers.add(consumer);
        return consumer;
    }

    public void releaseMatchConsumer(RedisMatchConsumer consumer) {
        if (consumer != null && matchConsumers.remove(consumer)) consumer.close();
    }

    private CompletionStage<DeliveryDisposition> consumeDatabaseSync(
            long generation,
            ink.ziip.championshipscore.protocol.transport.InboundDelivery<Map<String, String>>
                    delivery) {
        if (!isCurrent(generation))
            return CompletableFuture.completedFuture(DeliveryDisposition.RETRY);
        DatabaseSyncEvent event;
        try {
            event = DatabaseSyncEvent.parse(delivery.payload());
        } catch (RuntimeException malformed) {
            plugin.getLogger()
                    .warning(
                            LogText.formatModuleLog(
                                    "Redis",
                                    MessageConfig.codeText("redis-manager.consume-database-sync-1"),
                                    MessageConfig.codeText(
                                            "redis-manager.consume-database-sync-2",
                                            malformed.getMessage())));
            return CompletableFuture.completedFuture(DeliveryDisposition.DEAD_LETTER);
        }
        if (event.sourceInstance().equals(instanceId)
                || processedEvents.containsKey(event.eventId()))
            return CompletableFuture.completedFuture(DeliveryDisposition.ACK);

        CompletionStage<Void> refresh = CompletableFuture.completedFuture(null);
        CompletionStage<Void> dailyRefresh = CompletableFuture.completedFuture(null);
        if (event.domains().contains(DatabaseSyncDomain.PLAYER)) {
            plugin.getPlayerManager().invalidateDatabaseIdentityCache();
            if ("player-unknown-removed".equals(event.reason())) {
                dailyRefresh = plugin.getDailyStatsManager().reloadFromDatabase();
            }
        }
        if (event.domains().contains(DatabaseSyncDomain.TEAM)
                || event.domains().contains(DatabaseSyncDomain.PLAYER)) {
            refresh = plugin.getTeamManager().refreshFormalTeamsFromDatabase();
        }
        if (event.domains().contains(DatabaseSyncDomain.RANK)
                || event.domains().contains(DatabaseSyncDomain.TEAM)
                || event.domains().contains(DatabaseSyncDomain.PLAYER)) {
            refresh =
                    refresh.thenCompose(
                            ignored ->
                                    isCurrent(generation)
                                            ? plugin.getRankManager().refreshFromDatabase()
                                            : CompletableFuture.failedFuture(
                                                    new java.util.concurrent
                                                            .CancellationException()));
        }
        CompletionStage<Void> teamAndRankRefresh = refresh;
        return dailyRefresh
                .thenCompose(ignored -> teamAndRankRefresh)
                .handle(
                        (ignored, failure) -> {
                            if (!isCurrent(generation)) return DeliveryDisposition.RETRY;
                            if (failure != null) {
                                plugin.getLogger()
                                        .log(
                                                Level.WARNING,
                                                "Database cache refresh failed for Redis event "
                                                        + event.eventId(),
                                                failure);
                                return DeliveryDisposition.RETRY;
                            }
                            processedEvents.put(event.eventId(), Boolean.TRUE);
                            return DeliveryDisposition.ACK;
                        });
    }

    private void reconcileDatabaseCaches(long generation) {
        if (!isCurrent(generation) || !reconciling.compareAndSet(false, true)) return;
        flushPendingPublications();
        plugin.getTeamManager()
                .refreshFormalTeamsFromDatabase()
                .thenCompose(
                        ignored ->
                                isCurrent(generation)
                                        ? plugin.getRankManager().refreshFromDatabase()
                                        : CompletableFuture.failedFuture(
                                                new java.util.concurrent.CancellationException()))
                .whenComplete(
                        (ignored, failure) -> {
                            if (!isCurrent(generation)) return;
                            reconciling.set(false);
                            if (failure != null)
                                plugin.getLogger()
                                        .log(
                                                Level.WARNING,
                                                "Periodic cross-server database reconciliation"
                                                        + " failed",
                                                failure);
                        });
    }

    private synchronized void flushPendingPublications() {
        // Reconciliation also runs during outages: leave the queue intact until a publisher exists.
        if (stopping.get() || !ready.get() || publisher == null) return;
        int pending = pendingPublications.size();
        for (int i = 0; i < pending; i++) {
            DatabaseSyncEvent event = pendingPublications.poll();
            if (event == null) break;
            if ("starting".equals(event.sourceInstance()))
                event =
                        new DatabaseSyncEvent(
                                event.eventId(),
                                instanceId,
                                event.createdAt(),
                                event.domains(),
                                event.reason());
            publish(event);
        }
    }

    private synchronized void publish(DatabaseSyncEvent event) {
        if (stopping.get()) return;
        RedisStreamPublisher current = publisher;
        if (!ready.get() || current == null) {
            enqueuePendingPublication(event);
            return;
        }
        long generation = lifecycleGeneration.get();
        current.append(dataSyncStream(), event.fields())
                .whenComplete(
                        (ignored, failure) -> {
                            synchronized (this) {
                                if (failure == null || !isCurrent(generation)) return;
                                enqueuePendingPublication(event);
                                plugin.getLogger()
                                        .log(
                                                Level.WARNING,
                                                "Unable to publish database sync event "
                                                        + event.eventId(),
                                                failure);
                            }
                        });
    }

    private void enqueuePendingPublication(DatabaseSyncEvent event) {
        if (pendingPublications.offer(event)) return;
        pendingPublications.poll();
        pendingPublications.offer(event);
    }

    private String dataSyncStream() {
        return connectionConfig.key("core:data-sync");
    }

    private String syncGroup() {
        return RedisGroupNames.databaseSync(configuredGroupPrefix, instanceId);
    }

    private RedisTransportConfig matchTransportConfig(String workerId) {
        return new RedisTransportConfig(
                configuredUri,
                configuredNamespace,
                workerId,
                configuredStreamMaxLength,
                Duration.ofSeconds(5));
    }

    private String resolveInstanceId() {
        String configured =
                CCConfig.REDIS_INSTANCE_ID == null ? "auto" : CCConfig.REDIS_INSTANCE_ID.trim();
        if (!configured.isEmpty() && !configured.equalsIgnoreCase("auto"))
            return sanitize(configured);
        Path file = plugin.getDataFolder().toPath().resolve("redis-instance-id");
        try {
            if (Files.isRegularFile(file)) {
                String existing = Files.readString(file, StandardCharsets.UTF_8).trim();
                if (!existing.isBlank()) return sanitize(existing);
            }
            Files.createDirectories(file.getParent());
            String generated = "core-" + UUID.randomUUID();
            Files.writeString(file, generated, StandardCharsets.UTF_8);
            return generated;
        } catch (IOException failure) {
            throw new IllegalStateException(
                    "Unable to persist automatic Redis instance id", failure);
        }
    }

    private static String sanitize(String value) {
        String sanitized = value.replaceAll("[^A-Za-z0-9_.-]", "-");
        if (sanitized.isBlank()) throw new IllegalArgumentException("Invalid Redis instance id");
        return sanitized;
    }

    private void requireReady() {
        if (!ready.get()) throw new IllegalStateException("Redis manager is not ready");
    }

    @Override
    public synchronized void unload() {
        lifecycleGeneration.incrementAndGet();
        stopping.set(true);
        ready.set(false);
        CompletableFuture<Void> currentReady = readyFuture;
        if (!currentReady.isDone())
            currentReady.completeExceptionally(new IllegalStateException("Redis manager unloaded"));
        if (connectionRetryTask != null) connectionRetryTask.cancel();
        connectionRetryTask = null;
        if (reconciliationTask != null) reconciliationTask.cancel();
        reconciliationTask = null;
        closeResources();
        pendingPublications.clear();
        processedEvents.clear();
        connecting.set(false);
        reconciling.set(false);
    }

    private void closeResources() {
        for (RedisMatchConsumer consumer : Set.copyOf(matchConsumers)) consumer.close();
        matchConsumers.clear();
        for (RedisMatchTransport transport : matchTransports.values()) transport.close();
        matchTransports.clear();
        if (chatTransport != null) chatTransport.close();
        if (syncConsumer != null) syncConsumer.close();
        if (publisher != null) publisher.close();
        syncConsumer = null;
        chatTransport = null;
        publisher = null;
    }

    private static void close(AutoCloseable resource) {
        if (resource == null) return;
        try {
            resource.close();
        } catch (Exception ignored) {
        }
    }
}
