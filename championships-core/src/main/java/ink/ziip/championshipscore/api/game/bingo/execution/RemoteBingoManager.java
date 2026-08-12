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
import java.util.logging.Level;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.entity.Player;

/** Optional Core control plane. LOCAL mode never constructs Redis or proxy resources. */
public final class RemoteBingoManager extends BaseManager implements BingoExecutionGateway {
    private final Map<UUID, RemoteBingoMatch> matches = new ConcurrentHashMap<>();
    private final PlatformScheduler scheduler;
    private final BingoManifestFactory manifests;
    private final RemoteBingoStore store;
    private RedisMatchTransport transport;
    private RedisMatchConsumer consumer;
    private PluginMessagePlayerRouter router;
    private ScheduledTask heartbeatWatchdog;
    private volatile boolean ready;
    private volatile boolean transportLifecycleActive;

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
        transportLifecycleActive = true;
        try {
            router = new PluginMessagePlayerRouter(plugin, CCConfig.BINGO_PROXY_CHANNEL);
            plugin.getRedisManager().whenReady().thenCompose(ignored -> {
                        if (!transportLifecycleActive)
                            return CompletableFuture.failedFuture(
                                    new java.util.concurrent.CancellationException("Remote Bingo manager stopped"));
                        transport = plugin.getRedisManager().matchTransport(CCConfig.BINGO_WORKER_ID);
                        consumer = plugin.getRedisManager().createMatchEventConsumer(CCConfig.BINGO_WORKER_ID,
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
                    plugin.getLogger().info("Remote Bingo control plane ready; worker=" + CCConfig.BINGO_WORKER_ID);
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
                && matches.values().stream().noneMatch(match -> !match.state().terminal())
                && plugin.getGameManager().canReserveRemoteBingo(
                        request.runMode(), request.showIntroduction(), teams(request));
    }

    @Override
    public boolean start(BingoStartRequest request) {
        if (!canStart(request)) return false;
        BingoConfig config = plugin.getGameManager().getBingoManager().getRemoteConfig(request.area());
        if (config == null) return false;

        UUID matchId = UUID.randomUUID();
        long epoch = 1L;
        RemoteBingoInstance instance = new RemoteBingoInstance(plugin, config, matchId, epoch);
        if (!plugin.getGameManager().reserveRemoteBingo(
                instance, request.runMode(), request.showIntroduction(), teams(request))) {
            instance.dispose();
            return false;
        }
        Set<UUID> spectators = plugin.getGameManager().reserveRemoteBingoSpectators(instance);
        MatchManifest manifest;
        try {
            manifest = manifests.create(matchId, epoch, CCConfig.BINGO_WORKER_ID, config,
                    request.runMode(), teams(request), spectators,
                    request.showIntroduction());
            store.create(manifest);
        } catch (RuntimeException | SQLException failure) {
            plugin.getLogger().log(Level.SEVERE, "Unable to freeze remote Bingo manifest", failure);
            plugin.getGameManager().abortRemoteBingo(instance);
            return false;
        }

        RemoteBingoMatch match = new RemoteBingoMatch(plugin, manifest, instance, transport, router,
                CCConfig.BINGO_WORKER_SERVER);
        match.markPreparing();
        matches.put(matchId, match);
        updateState(match);
        MatchCommand prepare = MatchMessages.command(matchId, epoch, MatchCommandType.PREPARE);
        transport.publishManifest(manifest).thenCompose(ignored -> transport.publishCommand(prepare))
                .whenComplete((ignored, failure) -> {
                    if (failure != null) failMatch(match, "prepare-publish-failed", failure);
                });
        scheduler.runGlobalLater(() -> timeout(match, MatchState.PREPARING, "ready-timeout"),
                CCConfig.BINGO_READY_TIMEOUT_SECONDS * 20L);
        return true;
    }

    private java.util.List<ink.ziip.championshipscore.api.team.ChampionshipTeam> teams(BingoStartRequest request) {
        return request.teams().isEmpty() ? plugin.getTeamManager().getTeamList() : request.teams();
    }

    @Override
    public void forceEnd(String reason) {
        for (RemoteBingoMatch match : Set.copyOf(matches.values())) {
            if (match.state() != MatchState.RUNNING) {
                failMatch(match, reason, null);
                continue;
            }
            transport.publishCommand(match.forceEndCommand(reason)).whenComplete((ignored, failure) -> {
                if (failure != null) failMatch(match, reason + "-publish-failed", failure);
            });
        }
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
        if (!matches.remove(match.manifest().matchId(), match)) return;
        if (failure != null) plugin.getLogger().log(Level.SEVERE,
                "Remote Bingo failed match=" + match.manifest().matchId() + " reason=" + reason, failure);
        if (transport != null) transport.publishCommand(match.abortCommand(reason));
        scheduler.runGlobal(() -> plugin.getGameManager().abortRemoteBingo(match.instance()));
        try {
            store.updateState(match.manifest().matchId(), match.manifest().epoch(), MatchState.ABORTED);
        } catch (SQLException databaseFailure) {
            plugin.getLogger().log(Level.SEVERE, "Unable to persist aborted remote Bingo", databaseFailure);
        }
    }

    private void updateState(RemoteBingoMatch match) {
        CompletableFuture.runAsync(() -> {
            try {
                store.updateState(match.manifest().matchId(), match.manifest().epoch(), match.state());
            } catch (SQLException failure) {
                throw new java.util.concurrent.CompletionException(failure);
            }
        }).exceptionally(failure -> {
            failMatch(match, "state-persistence-failed", failure);
            return null;
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

    @Override
    public void unload() {
        transportLifecycleActive = false;
        ready = false;
        if (heartbeatWatchdog != null) heartbeatWatchdog.cancel();
        heartbeatWatchdog = null;
        plugin.getGameManager().getBingoExecutionRouter().activateLocal();
        for (RemoteBingoMatch match : Set.copyOf(matches.values())) {
            failMatch(match, "core-shutdown", null);
        }
        matches.clear();
        closeResources();
    }

    private void closeResources() {
        if (consumer != null) plugin.getRedisManager().releaseMatchConsumer(consumer);
        if (router != null) router.close();
        consumer = null;
        router = null;
        transport = null;
    }
}
