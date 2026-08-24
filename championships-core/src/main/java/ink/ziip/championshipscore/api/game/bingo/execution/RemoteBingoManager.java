package ink.ziip.championshipscore.api.game.bingo.execution;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseManager;
import ink.ziip.championshipscore.api.game.bingo.BingoConfig;
import ink.ziip.championshipscore.api.object.game.GameRunMode;
import ink.ziip.championshipscore.configuration.config.CCConfig;
import ink.ziip.championshipscore.platform.bukkit.proxy.PluginMessagePlayerRouter;
import ink.ziip.championshipscore.platform.bukkit.scheduler.PlatformScheduler;
import ink.ziip.championshipscore.protocol.MatchCommand;
import ink.ziip.championshipscore.protocol.MatchCommandType;
import ink.ziip.championshipscore.protocol.MatchEvent;
import ink.ziip.championshipscore.protocol.MatchManifest;
import ink.ziip.championshipscore.protocol.MatchMessages;
import ink.ziip.championshipscore.protocol.MatchRunMode;
import ink.ziip.championshipscore.protocol.MatchState;
import ink.ziip.championshipscore.protocol.transport.DeliveryDisposition;
import ink.ziip.championshipscore.protocol.transport.MatchInboundMessage;
import ink.ziip.championshipscore.redis.RedisMatchConsumer;
import ink.ziip.championshipscore.redis.RedisMatchTransport;

import java.sql.SQLException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/** Optional Core control plane. LOCAL mode never constructs Redis or proxy resources. */
public final class RemoteBingoManager extends BaseManager implements BingoExecutionGateway {
    private final Map<UUID, RemoteBingoMatch> matches = new ConcurrentHashMap<>();
    private final Map<UUID, PendingStart> pendingStarts = new ConcurrentHashMap<>();
    private final PlatformScheduler scheduler;
    private final BingoManifestFactory manifests;
    private final RemoteBingoStore store;
    private RedisMatchTransport transport;
    private RedisMatchConsumer consumer;
    private PluginMessagePlayerRouter router;
    private String configuredWorkerId;
    private String configuredProxyChannel;
    private ScheduledTask heartbeatWatchdog;
    private volatile boolean ready;
    private volatile boolean transportLifecycleActive;

    private record PendingStart(MatchManifest manifest, RemoteBingoInstance instance,
                                CompletableFuture<Boolean> result, AtomicBoolean cancelled) {
    }

    public RemoteBingoManager(ChampionshipsCore plugin) {
        super(plugin);
        this.scheduler = new PlatformScheduler(plugin);
        this.manifests = new BingoManifestFactory(plugin);
        this.store = new RemoteBingoStore(plugin);
    }

    @Override
    public BingoExecutionMode mode() {
        return BingoExecutionMode.REMOTE;
    }

    @Override
    public void load() {
        if (!plugin.getGameManager().getBingoManager().remoteExecutionConfigured()) return;
        configuredWorkerId = CCConfig.BINGO_WORKER_ID;
        configuredProxyChannel = CCConfig.BINGO_PROXY_CHANNEL;
        transportLifecycleActive = true;
        try {
            router = new PluginMessagePlayerRouter(plugin, configuredProxyChannel);
            plugin.getRedisManager().whenReady().thenCompose(ignored -> {
                        if (!transportLifecycleActive)
                            return CompletableFuture.failedFuture(
                                    new java.util.concurrent.CancellationException("Remote Bingo manager stopped"));
                        transport = plugin.getRedisManager().matchTransport(configuredWorkerId);
                        consumer = plugin.getRedisManager().createMatchEventConsumer(configuredWorkerId,
                                this::consume,
                                error -> plugin.getLogger().log(Level.SEVERE,
                                        "Remote Bingo event consumer failure", error));
                        return consumer.start();
                    })
                    .thenCompose(ignored -> recoverOrphans()).whenComplete((ignored, failure) -> {
                if (!transportLifecycleActive) {
                    closeResources();
                    return;
                }
                if (failure != null) {
                    plugin.getLogger().log(Level.SEVERE, "Remote Bingo transport did not become ready", failure);
                    closeResources();
                    return;
                }
                scheduler.runGlobal(() -> {
                    ready = true;
                    plugin.getGameManager().getBingoExecutionRouter().activateRemote(this);
                    heartbeatWatchdog = scheduler.runGlobalTimer(this::checkWorkerHeartbeats, 100L, 100L);
                    plugin.getLogger().info("Remote Bingo control plane ready; worker=" + configuredWorkerId);
                });
            });
        } catch (RuntimeException failure) {
            plugin.getLogger().log(Level.SEVERE, "Remote Bingo initialization failed; Bingo starts remain disabled", failure);
            closeResources();
        }
    }

    @Override
    public boolean canStart(BingoStartRequest request) {
        if (!ready || !plugin.getGameManager().getBingoManager().isTaskPoolReady()) return false;
        BingoConfig config = plugin.getGameManager().getBingoManager().getRemoteConfig(request.area());
        return config != null
                && pendingStarts.isEmpty()
                && matches.values().stream().noneMatch(match -> !match.state().terminal())
                && plugin.getGameManager().canReserveRemoteBingo(
                        request.runMode(), request.showIntroduction(), teams(request));
    }

    @Override
    public CompletionStage<Boolean> start(BingoStartRequest request) {
        if (!canStart(request)) return CompletableFuture.completedFuture(false);
        BingoConfig config = plugin.getGameManager().getBingoManager().getRemoteConfig(request.area());
        if (config == null) return CompletableFuture.completedFuture(false);

        UUID matchId = UUID.randomUUID();
        long epoch = 1L;
        RemoteBingoInstance instance = new RemoteBingoInstance(plugin, config, matchId, epoch);
        if (!plugin.getGameManager().reserveRemoteBingo(
                instance, request.runMode(), request.showIntroduction(), teams(request))) {
            instance.dispose();
            return CompletableFuture.completedFuture(false);
        }
        Set<UUID> spectators = plugin.getGameManager().reserveRemoteBingoSpectators(instance);
        MatchManifest manifest;
        try {
            manifest = manifests.create(matchId, epoch, configuredWorkerId, config,
                    request.runMode(), teams(request), spectators,
                    request.showIntroduction(), request.variant());
        } catch (RuntimeException failure) {
            plugin.getLogger().log(Level.SEVERE, "Unable to freeze remote Bingo manifest", failure);
            plugin.getGameManager().abortRemoteBingo(instance);
            return CompletableFuture.completedFuture(false);
        }
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        PendingStart pending = new PendingStart(manifest, instance, result, new AtomicBoolean());
        pendingStarts.put(matchId, pending);
        CompletableFuture.runAsync(() -> {
            try {
                store.create(manifest);
            } catch (SQLException failure) {
                throw new java.util.concurrent.CompletionException(failure);
            }
        }).whenComplete((ignored, failure) -> {
            try {
                scheduler.runGlobal(() -> finishPendingStart(pending, failure));
            } catch (RuntimeException schedulingFailure) {
                pendingStarts.remove(matchId, pending);
                pending.result().complete(false);
                if (failure == null)
                    updateStateAsync(manifest.matchId(), manifest.epoch(), MatchState.ABORTED);
                plugin.getLogger().log(Level.WARNING,
                        "Remote Bingo start completed while the Core scheduler was unavailable", schedulingFailure);
            }
        });
        return result;
    }

    private void finishPendingStart(PendingStart pending, Throwable failure) {
        MatchManifest manifest = pending.manifest();
        pendingStarts.remove(manifest.matchId(), pending);
        if (failure != null) {
            plugin.getLogger().log(Level.SEVERE, "Unable to persist remote Bingo manifest", failure);
            plugin.getGameManager().abortRemoteBingo(pending.instance());
            pending.result().complete(false);
            return;
        }
        if (pending.cancelled().get() || !ready || !transportLifecycleActive) {
            plugin.getGameManager().abortRemoteBingo(pending.instance());
            updateStateAsync(manifest.matchId(), manifest.epoch(), MatchState.ABORTED)
                    .whenComplete((ignored, stateFailure) -> pending.result().complete(false));
            return;
        }

        try {
            RemoteBingoMatch match = new RemoteBingoMatch(plugin, manifest, pending.instance(), transport, router,
                    CCConfig.BINGO_WORKER_SERVER);
            match.markPreparing();
            matches.put(manifest.matchId(), match);
            updateState(match);
            MatchCommand prepare = MatchMessages.command(manifest.matchId(), manifest.epoch(), MatchCommandType.PREPARE);
            transport.publishManifest(manifest).thenCompose(ignored -> transport.publishCommand(prepare))
                    .whenComplete((ignored, publishFailure) -> {
                        if (publishFailure != null) failMatch(match, "prepare-publish-failed", publishFailure);
                    });
            scheduler.runGlobalLater(() -> timeout(match, MatchState.PREPARING, "ready-timeout"),
                    CCConfig.BINGO_READY_TIMEOUT_SECONDS * 20L);
            pending.result().complete(true);
        } catch (RuntimeException activationFailure) {
            plugin.getLogger().log(Level.SEVERE, "Unable to activate persisted remote Bingo", activationFailure);
            plugin.getGameManager().abortRemoteBingo(pending.instance());
            updateStateAsync(manifest.matchId(), manifest.epoch(), MatchState.ABORTED)
                    .whenComplete((ignored, stateFailure) -> pending.result().complete(false));
        }
    }

    private java.util.List<ink.ziip.championshipscore.api.team.ChampionshipTeam> teams(BingoStartRequest request) {
        return request.teams().isEmpty() ? plugin.getTeamManager().getTeamList() : request.teams();
    }

    @Override
    public CompletionStage<Void> forceEnd(String reason) {
        java.util.List<CompletableFuture<?>> publications = new java.util.ArrayList<>();
        for (PendingStart pending : Set.copyOf(pendingStarts.values())) {
            pending.cancelled().set(true);
            scheduler.runGlobal(() -> plugin.getGameManager().abortRemoteBingo(pending.instance()));
            publications.add(pending.result().handle((ignored, failure) -> null));
        }
        for (RemoteBingoMatch match : Set.copyOf(matches.values())) {
            if (match.state() != MatchState.RUNNING) {
                failMatch(match, reason, null);
                publications.add(match.terminalFuture());
                continue;
            }
            publications.add(requestNormalStop(match, reason).handle((ignored, failure) -> null)
                    .toCompletableFuture());
        }
        return CompletableFuture.allOf(publications.toArray(CompletableFuture[]::new));
    }

    /**
     * Stops one UUID-selected match only. Running matches ask the worker to finish and publish its
     * authoritative result; pre-start reservations are aborted because no valid score exists yet.
     */
    public CompletionStage<Boolean> stopMatch(UUID matchId, String reason, boolean normalSettlement) {
        PendingStart pending = pendingStarts.get(matchId);
        if (pending != null) {
            if (normalSettlement || !pending.cancelled().compareAndSet(false, true))
                return CompletableFuture.completedFuture(false);
            scheduler.runGlobal(() -> plugin.getGameManager().abortRemoteBingo(pending.instance()));
            return CompletableFuture.completedFuture(true);
        }

        RemoteBingoMatch match = matches.get(matchId);
        if (match == null || match.state().terminal()) return CompletableFuture.completedFuture(false);
        if (normalSettlement) {
            if (match.state() != MatchState.RUNNING) return CompletableFuture.completedFuture(false);
            return requestNormalStop(match, reason);
        }

        failMatch(match, reason, null);
        return match.terminalFuture()
                .orTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .thenApply(ignored -> true)
                .exceptionally(failure -> false);
    }

    private CompletionStage<Boolean> requestNormalStop(RemoteBingoMatch match, String reason) {
        if (transport == null) {
            failMatch(match, reason + "-normal-stop-transport-unavailable", null);
            return match.terminalFuture().handle((ignored, failure) -> false);
        }
        if (!match.markNormalStopRequested()) {
            return match.terminalFuture().handle((ignored, failure) ->
                    failure == null && match.state() == MatchState.FINISHED);
        }
        return transport.publishCommand(match.forceEndCommand(reason))
                .handle((ignored, publishFailure) -> {
                    if (publishFailure != null)
                        failMatch(match, reason + "-normal-stop-publish-failed", publishFailure);
                    return publishFailure == null;
                })
                .thenCompose(published -> match.terminalFuture()
                        .orTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                        .handle((ignored, terminalFailure) -> {
                            if (terminalFailure != null)
                                failMatch(match, reason + "-normal-stop-terminal-timeout", terminalFailure);
                            return published && terminalFailure == null
                                    && match.state() == MatchState.FINISHED;
                        }));
    }

    private CompletionStage<DeliveryDisposition> consume(
            ink.ziip.championshipscore.protocol.transport.InboundDelivery<MatchInboundMessage> delivery) {
        if (!(delivery.payload() instanceof MatchInboundMessage.Event inbound)) {
            return CompletableFuture.completedFuture(DeliveryDisposition.DEAD_LETTER);
        }
        MatchEvent event = inbound.event();
        RemoteBingoMatch match = matches.get(event.matchId());
        if (match == null || match.manifest().epoch() != event.epoch()) {
            return CompletableFuture.completedFuture(DeliveryDisposition.ACK);
        }
        return CompletableFuture.supplyAsync(() -> processed(event))
                .thenCompose(alreadyProcessed -> {
                    if (alreadyProcessed) return CompletableFuture.completedFuture(true);
                    return scheduler.supplyGlobal(() -> match.apply(event)).thenCompose(stage -> stage)
                            .thenCompose(success -> success
                                    ? CompletableFuture.supplyAsync(() -> record(event, match))
                                    : CompletableFuture.completedFuture(false));
                })
                .thenApply(success -> success ? DeliveryDisposition.ACK : DeliveryDisposition.RETRY);
    }

    private boolean processed(MatchEvent event) {
        try {
            return store.processed(event.messageId());
        } catch (SQLException failure) {
            throw new java.util.concurrent.CompletionException(failure);
        }
    }

    private boolean record(MatchEvent event, RemoteBingoMatch match) {
        try {
            store.recordProcessed(event, match.state());
            if (event.type() == ink.ziip.championshipscore.protocol.MatchEventType.READY) {
                scheduler.runGlobalLater(() -> timeout(match, MatchState.ROUTING, "arrival-timeout"),
                        CCConfig.BINGO_ARRIVAL_TIMEOUT_SECONDS * 20L);
            }
            if (event.type() == ink.ziip.championshipscore.protocol.MatchEventType.PREPARE_FAILED
                    || event.type() == ink.ziip.championshipscore.protocol.MatchEventType.FAILED
                    || event.type() == ink.ziip.championshipscore.protocol.MatchEventType.ABORTED) {
                matches.remove(event.matchId(), match);
                scheduler.runGlobal(() -> plugin.getGameManager().abortRemoteBingo(match.instance()));
            } else if (event.type() == ink.ziip.championshipscore.protocol.MatchEventType.FINISHED) {
                matches.remove(event.matchId(), match);
            }
            return true;
        } catch (SQLException failure) {
            throw new java.util.concurrent.CompletionException(failure);
        }
    }

    private void timeout(RemoteBingoMatch match, MatchState expected, String reason) {
        if (match.state() == expected) failMatch(match, reason, null);
    }

    private void checkWorkerHeartbeats() {
        if (!ready) return;
        long now = System.currentTimeMillis();
        long timeoutMillis = Math.max(1L, CCConfig.BINGO_HEARTBEAT_TIMEOUT_SECONDS) * 1000L;
        for (RemoteBingoMatch match : Set.copyOf(matches.values())) {
            if (match.heartbeatExpired(now, timeoutMillis)) {
                failMatch(match, "worker-heartbeat-timeout", null);
            }
        }
    }

    private void failMatch(RemoteBingoMatch match, String reason, Throwable failure) {
        scheduler.runGlobal(() -> failMatchOnGlobal(match, reason, failure));
    }

    private void failMatchOnGlobal(RemoteBingoMatch match, String reason, Throwable failure) {
        if (!matches.remove(match.manifest().matchId(), match)) return;
        match.abortLocally();
        if (failure != null) plugin.getLogger().log(Level.SEVERE,
                "Remote Bingo failed match=" + match.manifest().matchId() + " reason=" + reason, failure);
        else plugin.getLogger().warning(
                "Remote Bingo aborted match=" + match.manifest().matchId() + " reason=" + reason);
        if (transport != null) transport.publishCommand(match.abortCommand(reason));
        plugin.getGameManager().abortRemoteBingo(match.instance());
        updateStateAsync(match.manifest().matchId(), match.manifest().epoch(), MatchState.ABORTED)
                .exceptionally(databaseFailure -> {
                    plugin.getLogger().log(Level.SEVERE, "Unable to persist aborted remote Bingo", databaseFailure);
                    return null;
                });
    }

    private void updateState(RemoteBingoMatch match) {
        updateStateAsync(match.manifest().matchId(), match.manifest().epoch(), match.state()).exceptionally(failure -> {
            failMatch(match, "state-persistence-failed", failure);
            return null;
        });
    }

    private CompletionStage<Void> updateStateAsync(UUID matchId, long epoch, MatchState state) {
        return CompletableFuture.runAsync(() -> {
            try {
                store.updateState(matchId, epoch, state);
            } catch (SQLException failure) {
                throw new java.util.concurrent.CompletionException(failure);
            }
        });
    }

    private CompletionStage<Void> recoverOrphans() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return store.activeMatches();
            } catch (SQLException failure) {
                throw new java.util.concurrent.CompletionException(failure);
            }
        }).thenCompose(orphans -> {
            CompletionStage<Void> chain = CompletableFuture.completedFuture(null);
            for (MatchManifest orphan : orphans) {
                MatchCommand abort = MatchMessages.command(orphan.matchId(), orphan.epoch(),
                        MatchCommandType.ABORT, Map.of("reason", "core-restart"), java.time.Clock.systemUTC());
                chain = chain.thenCompose(ignored -> transport.publishCommand(abort).thenCompose(receipt ->
                        CompletableFuture.runAsync(() -> {
                            try {
                                store.updateState(orphan.matchId(), orphan.epoch(), MatchState.ABORTED);
                            } catch (SQLException failure) {
                                throw new java.util.concurrent.CompletionException(failure);
                            }
                        })));
            }
            return chain;
        });
    }

    public void routeReconnect(Player player, RemoteBingoInstance instance) {
        RemoteBingoMatch match = matches.get(instance.matchId());
        if (match == null || match.state().terminal() || router == null) {
            instance.sanitizeParticipantForLobby(player, false);
            return;
        }
        if (match.state() == MatchState.PREPARING) {
            // The worker cannot admit anyone before READY, so keep reconnects in Core while its
            // worlds and card views are still being prepared.
            instance.sanitizeParticipantForLobby(player, false);
            return;
        }
        // READY is already safe for admission. Route immediately instead of relying only on the
        // onReady roster snapshot: a late player can join after that snapshot but before ROUTING.
        ink.ziip.championshipscore.protocol.ParticipantRole role = match.roleOf(player.getUniqueId());
        if (role == null) return;
        router.route(new ink.ziip.championshipscore.protocol.PlayerRoute(player.getUniqueId(),
                match.manifest().matchId(), match.manifest().epoch(), CCConfig.BINGO_WORKER_SERVER,
                role, System.currentTimeMillis() + 120_000L));
    }

    public void addSpectator(Player player, RemoteBingoInstance instance) {
        RemoteBingoMatch match = matches.get(instance.matchId());
        if (match == null || match.state().terminal()) {
            plugin.getGameManager().clearSpectatorStatus(player.getUniqueId(), instance);
            instance.onlyRemoveSpectatorFromList(player.getUniqueId());
            return;
        }
        double points = match.manifest().runMode() == MatchRunMode.EVENT
                ? plugin.getRankManager().getPlayerPoints(player.getUniqueId()) : 0D;
        match.addSpectator(player.getUniqueId(), player.getName(), points).thenAccept(accepted -> {
            if (!accepted) scheduler.runGlobal(() -> {
                plugin.getGameManager().clearSpectatorStatus(player.getUniqueId(), instance);
                instance.onlyRemoveSpectatorFromList(player.getUniqueId());
            });
        }).exceptionally(failure -> {
            plugin.getLogger().log(Level.WARNING, "Unable to add remote Bingo spectator", failure);
            return null;
        });
    }

    public void removeSpectator(UUID playerId, RemoteBingoInstance instance) {
        RemoteBingoMatch match = matches.get(instance.matchId());
        if (match == null) return;
        match.removeSpectator(playerId).exceptionally(failure -> {
            plugin.getLogger().log(Level.WARNING, "Unable to remove remote Bingo spectator " + playerId, failure);
            return false;
        });
    }

    public void removeDailyPlayers(RemoteBingoInstance instance, Set<UUID> players) {
        RemoteBingoMatch match = matches.get(instance.matchId());
        if (match == null || players.isEmpty()) return;
        match.removeParticipants(players).exceptionally(failure -> {
            plugin.getLogger().log(Level.WARNING,
                    "Unable to remove DAILY Bingo participants " + players, failure);
            return false;
        });
    }

    /** Returns true only after the worker has reported a heartbeat with no online participants. */
    public boolean allRemoteParticipantsOffline(@NotNull RemoteBingoInstance instance) {
        RemoteBingoMatch match = matches.get(instance.matchId());
        return match != null && match.allRemoteParticipantsOffline();
    }

    /** Aborts a DAILY match after its complete roster exceeded Core's reconnect grace period. */
    public void abortDailyDisconnected(@NotNull RemoteBingoInstance instance) {
        RemoteBingoMatch match = matches.get(instance.matchId());
        if (match == null || match.state().terminal()) {
            plugin.getGameManager().abortRemoteBingo(instance);
            return;
        }
        failMatch(match, "daily-all-players-disconnected", null);
    }

    @Override
    public void unload() {
        transportLifecycleActive = false;
        ready = false;
        if (heartbeatWatchdog != null) heartbeatWatchdog.cancel();
        heartbeatWatchdog = null;
        plugin.getGameManager().getBingoExecutionRouter().activateLocal();
        for (PendingStart pending : Set.copyOf(pendingStarts.values())) {
            pending.cancelled().set(true);
            plugin.getGameManager().abortRemoteBingo(pending.instance());
            pending.result().complete(false);
        }
        pendingStarts.clear();
        for (RemoteBingoMatch match : Set.copyOf(matches.values())) {
            failMatchOnGlobal(match, "core-shutdown", null);
        }
        matches.clear();
        closeResources();
        configuredWorkerId = null;
        configuredProxyChannel = null;
    }

    private void closeResources() {
        if (consumer != null) plugin.getRedisManager().releaseMatchConsumer(consumer);
        if (router != null) router.close();
        consumer = null;
        router = null;
        transport = null;
    }
}
