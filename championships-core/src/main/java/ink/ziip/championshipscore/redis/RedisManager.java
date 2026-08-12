package ink.ziip.championshipscore.redis;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseManager;
import ink.ziip.championshipscore.configuration.config.CCConfig;
import ink.ziip.championshipscore.database.sync.DatabaseSyncDomain;
import ink.ziip.championshipscore.database.sync.DatabaseSyncEvent;
import ink.ziip.championshipscore.protocol.transport.DeliveryDisposition;
import ink.ziip.championshipscore.protocol.transport.DeliveryHandler;
import ink.ziip.championshipscore.protocol.transport.MatchInboundMessage;
import ink.ziip.championshipscore.redis.RedisConsumerConfig;
import ink.ziip.championshipscore.redis.RedisGroupNames;
import ink.ziip.championshipscore.redis.RedisMatchConsumer;
import ink.ziip.championshipscore.redis.RedisMatchTransport;
import ink.ziip.championshipscore.redis.RedisTransportConfig;
import ink.ziip.championshipscore.util.Utils;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

/** One Core-owned Redis lifecycle for database invalidation and remote-game transports. */
public final class RedisManager extends BaseManager {
    private static final int DEDUPLICATION_LIMIT = 4096;
    private final Map<UUID, Boolean> processedEvents = java.util.Collections.synchronizedMap(
            new LinkedHashMap<>(DEDUPLICATION_LIMIT, .75F, true) {
                @Override protected boolean removeEldestEntry(Map.Entry<UUID, Boolean> eldest) {
                    return size() > DEDUPLICATION_LIMIT;
                }
            });
    private final ConcurrentLinkedQueue<DatabaseSyncEvent> pendingPublications = new ConcurrentLinkedQueue<>();
    private final Map<String, RedisMatchTransport> matchTransports = new ConcurrentHashMap<>();
    private final Set<RedisMatchConsumer> matchConsumers = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean ready = new AtomicBoolean();
    private final AtomicBoolean connecting = new AtomicBoolean();
    private final AtomicBoolean stopping = new AtomicBoolean();
    private final AtomicInteger connectionFailures = new AtomicInteger();
    private final CompletableFuture<Void> readyFuture = new CompletableFuture<>();
    private RedisConnectionConfig connectionConfig;
    private RedisStreamPublisher publisher;
    private RedisStreamConsumer syncConsumer;
    private BukkitTask reconciliationTask;
    private BukkitTask connectionRetryTask;
    private String instanceId;

    public RedisManager(ChampionshipsCore plugin) {
        super(plugin);
    }

    @Override
    public void load() {
        if (!Boolean.TRUE.equals(CCConfig.REDIS_ENABLED)) {
            readyFuture.completeExceptionally(new IllegalStateException("Redis is disabled"));
            plugin.getLogger().info(Utils.formatModuleLog("Redis", "启动", "统一Redis管理器未启用"));
            return;
        }
        try {
            instanceId = resolveInstanceId();
            connectionConfig = new RedisConnectionConfig(CCConfig.REDIS_URI, CCConfig.REDIS_NAMESPACE,
                    instanceId, CCConfig.REDIS_STREAM_MAX_LENGTH, Duration.ofSeconds(5));
            long interval = Math.max(5L, CCConfig.REDIS_RECONCILIATION_SECONDS) * 20L;
            reconciliationTask = plugin.getServer().getScheduler().runTaskTimer(plugin,
                    this::reconcileDatabaseCaches, interval, interval);
            // Initial connection attempts run off the server thread. A Redis outage at boot leaves
            // database reconciliation active and is retried without requiring a Core restart.
            connectionRetryTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin,
                    this::ensureConnected, 0L, 200L);
        } catch (RuntimeException failure) {
            readyFuture.completeExceptionally(failure);
            plugin.getLogger().log(Level.SEVERE, "Unified Redis initialization failed", failure);
            closeResources();
        }
    }

    private void ensureConnected() {
        if (stopping.get() || ready.get() || !connecting.compareAndSet(false, true)) return;
        RedisStreamPublisher candidatePublisher = null;
        RedisStreamConsumer candidateConsumer = null;
        try {
            candidatePublisher = new RedisStreamPublisher(connectionConfig);
            RedisConsumerConfig consumerConfig = new RedisConsumerConfig(
                    syncGroup(), instanceId, 64,
                    Duration.ofMillis(CCConfig.REDIS_BLOCK_TIMEOUT_MILLIS),
                    Duration.ofMillis(CCConfig.REDIS_RECLAIM_IDLE_MILLIS),
                    CCConfig.REDIS_MAX_DELIVERIES);
            // A brand-new Core has already loaded the authoritative DB snapshot, so its new group
            // starts at the current tail. Existing stable groups still resume their pending cursor.
            candidateConsumer = new RedisStreamConsumer(connectionConfig, consumerConfig,
                    dataSyncStream(), "$", this::consumeDatabaseSync,
                    failure -> plugin.getLogger().log(Level.SEVERE,
                            "Redis database-sync consumer failure", failure));
            RedisStreamPublisher connectedPublisher = candidatePublisher;
            RedisStreamConsumer connectedConsumer = candidateConsumer;
            candidatePublisher.ping().thenCompose(ignored -> connectedConsumer.start())
                    .whenComplete((ignored, failure) -> finishConnectionAttempt(
                            connectedPublisher, connectedConsumer, failure));
        } catch (RuntimeException failure) {
            close(candidateConsumer);
            close(candidatePublisher);
            connectionAttemptFailed(failure);
            connecting.set(false);
        }
    }

    private synchronized void finishConnectionAttempt(RedisStreamPublisher connectedPublisher,
                                                       RedisStreamConsumer connectedConsumer, Throwable failure) {
        if (failure != null || stopping.get()) {
            close(connectedConsumer);
            close(connectedPublisher);
            if (failure != null && !stopping.get()) connectionAttemptFailed(failure);
            connecting.set(false);
            return;
        }
        publisher = connectedPublisher;
        syncConsumer = connectedConsumer;
        connectionFailures.set(0);
        ready.set(true);
        connecting.set(false);
        readyFuture.complete(null);
        if (connectionRetryTask != null) connectionRetryTask.cancel();
        flushPendingPublications();
        plugin.getLogger().info(Utils.formatModuleLog("Redis", "启动",
                "实例=" + instanceId + " 数据同步流=" + dataSyncStream()));
    }

    private void connectionAttemptFailed(Throwable failure) {
        int attempts = connectionFailures.incrementAndGet();
        if (attempts == 1 || attempts % 6 == 0) {
            plugin.getLogger().log(Level.WARNING,
                    "Unified Redis connection unavailable; retrying (attempt " + attempts + ")", failure);
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

    public void publishDatabaseChange(@NotNull String reason, @NotNull DatabaseSyncDomain first,
                                      DatabaseSyncDomain... additional) {
        EnumSet<DatabaseSyncDomain> domains = EnumSet.of(first, additional);
        DatabaseSyncEvent event = new DatabaseSyncEvent(UUID.randomUUID(),
                instanceId == null ? "starting" : instanceId, System.currentTimeMillis(), domains, reason);
        if (!ready.get() || publisher == null) {
            if (Boolean.TRUE.equals(CCConfig.REDIS_ENABLED)) pendingPublications.add(event);
            return;
        }
        publish(event);
    }

    public synchronized RedisMatchTransport matchTransport(@NotNull String workerId) {
        requireReady();
        return matchTransports.computeIfAbsent(workerId,
                id -> new RedisMatchTransport(matchTransportConfig(id)));
    }

    public RedisMatchConsumer createMatchEventConsumer(@NotNull String workerId,
                                                        @NotNull DeliveryHandler<MatchInboundMessage> handler,
                                                        @NotNull java.util.function.Consumer<Throwable> errors) {
        requireReady();
        RedisTransportConfig transportConfig = matchTransportConfig(workerId);
        RedisConsumerConfig consumerConfig = new RedisConsumerConfig(
                RedisGroupNames.bingoEvents(CCConfig.REDIS_CONSUMER_GROUP_PREFIX, instanceId),
                instanceId, 64, Duration.ofMillis(CCConfig.REDIS_BLOCK_TIMEOUT_MILLIS),
                Duration.ofMillis(CCConfig.REDIS_RECLAIM_IDLE_MILLIS), CCConfig.REDIS_MAX_DELIVERIES);
        RedisMatchConsumer consumer = new RedisMatchConsumer(transportConfig, consumerConfig,
                transportConfig.eventStream(), handler, errors);
        matchConsumers.add(consumer);
        return consumer;
    }

    public void releaseMatchConsumer(RedisMatchConsumer consumer) {
        if (consumer != null && matchConsumers.remove(consumer)) consumer.close();
    }

    private CompletionStage<DeliveryDisposition> consumeDatabaseSync(
            ink.ziip.championshipscore.protocol.transport.InboundDelivery<Map<String, String>> delivery) {
        DatabaseSyncEvent event;
        try {
            event = DatabaseSyncEvent.parse(delivery.payload());
        } catch (RuntimeException malformed) {
            plugin.getLogger().warning(Utils.formatModuleLog("Redis", "同步",
                    "拒绝无效数据库同步事件=" + malformed.getMessage()));
            return CompletableFuture.completedFuture(DeliveryDisposition.DEAD_LETTER);
        }
        if (event.sourceInstance().equals(instanceId) || processedEvents.containsKey(event.eventId()))
            return CompletableFuture.completedFuture(DeliveryDisposition.ACK);

        CompletionStage<Void> refresh = CompletableFuture.completedFuture(null);
        if (event.domains().contains(DatabaseSyncDomain.PLAYER)) {
            plugin.getPlayerManager().invalidateDatabaseIdentityCache();
        }
        if (event.domains().contains(DatabaseSyncDomain.TEAM)
                || event.domains().contains(DatabaseSyncDomain.PLAYER)) {
            refresh = plugin.getTeamManager().refreshFormalTeamsFromDatabase();
        }
        if (event.domains().contains(DatabaseSyncDomain.RANK)
                || event.domains().contains(DatabaseSyncDomain.TEAM)
                || event.domains().contains(DatabaseSyncDomain.PLAYER)) {
            refresh = refresh.thenCompose(ignored -> plugin.getRankManager().refreshFromDatabase());
        }
        return refresh.handle((ignored, failure) -> {
            if (failure != null) {
                plugin.getLogger().log(Level.WARNING, "Database cache refresh failed for Redis event "
                        + event.eventId(), failure);
                return DeliveryDisposition.RETRY;
            }
            processedEvents.put(event.eventId(), Boolean.TRUE);
            return DeliveryDisposition.ACK;
        });
    }

    private void reconcileDatabaseCaches() {
        flushPendingPublications();
        plugin.getTeamManager().refreshFormalTeamsFromDatabase()
                .thenCompose(ignored -> plugin.getRankManager().refreshFromDatabase())
                .exceptionally(failure -> {
                    plugin.getLogger().log(Level.WARNING, "Periodic cross-server database reconciliation failed", failure);
                    return null;
                });
    }

    private void flushPendingPublications() {
        DatabaseSyncEvent event;
        while ((event = pendingPublications.poll()) != null) {
            if ("starting".equals(event.sourceInstance())) {
                event = new DatabaseSyncEvent(event.eventId(), instanceId, event.createdAt(),
                        event.domains(), event.reason());
            }
            publish(event);
        }
    }

    private void publish(DatabaseSyncEvent event) {
        publisher.append(dataSyncStream(), event.fields()).exceptionally(failure -> {
            pendingPublications.add(event);
            plugin.getLogger().log(Level.WARNING, "Unable to publish database sync event " + event.eventId(), failure);
            return null;
        });
    }

    private String dataSyncStream() { return connectionConfig.key("core:data-sync"); }
    private String syncGroup() {
        return RedisGroupNames.databaseSync(CCConfig.REDIS_CONSUMER_GROUP_PREFIX, instanceId);
    }

    private RedisTransportConfig matchTransportConfig(String workerId) {
        return new RedisTransportConfig(CCConfig.REDIS_URI, CCConfig.REDIS_NAMESPACE, workerId,
                CCConfig.REDIS_STREAM_MAX_LENGTH, Duration.ofSeconds(5));
    }

    private String resolveInstanceId() {
        String configured = CCConfig.REDIS_INSTANCE_ID == null ? "auto" : CCConfig.REDIS_INSTANCE_ID.trim();
        if (!configured.isEmpty() && !configured.equalsIgnoreCase("auto")) return sanitize(configured);
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
            throw new IllegalStateException("Unable to persist automatic Redis instance id", failure);
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
        stopping.set(true);
        ready.set(false);
        if (connectionRetryTask != null) connectionRetryTask.cancel();
        connectionRetryTask = null;
        if (reconciliationTask != null) reconciliationTask.cancel();
        reconciliationTask = null;
        closeResources();
    }

    private void closeResources() {
        for (RedisMatchConsumer consumer : Set.copyOf(matchConsumers)) consumer.close();
        matchConsumers.clear();
        for (RedisMatchTransport transport : matchTransports.values()) transport.close();
        matchTransports.clear();
        if (syncConsumer != null) syncConsumer.close();
        if (publisher != null) publisher.close();
        syncConsumer = null;
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
