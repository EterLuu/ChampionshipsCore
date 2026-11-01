package ink.ziip.championshipscore.api.game.bingo.execution;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.daily.DailyRecordType;
import ink.ziip.championshipscore.api.game.model.GameRunMode;
import ink.ziip.championshipscore.api.game.model.GameTypeEnum;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.bingo.engine.BingoResult;
import ink.ziip.championshipscore.bingo.engine.BingoScoringEngine;
import ink.ziip.championshipscore.bingo.engine.PlayerAward;
import ink.ziip.championshipscore.bingo.engine.ScoringDecision;
import ink.ziip.championshipscore.platform.bukkit.scheduler.PlatformScheduler;
import ink.ziip.championshipscore.protocol.DeterministicIds;
import ink.ziip.championshipscore.protocol.MatchCommand;
import ink.ziip.championshipscore.protocol.MatchCommandType;
import ink.ziip.championshipscore.protocol.MatchEvent;
import ink.ziip.championshipscore.protocol.MatchManifest;
import ink.ziip.championshipscore.protocol.MatchMessages;
import ink.ziip.championshipscore.protocol.MatchState;
import ink.ziip.championshipscore.protocol.MatchStateMachine;
import ink.ziip.championshipscore.protocol.ParticipantRole;
import ink.ziip.championshipscore.protocol.PlayerRoute;
import ink.ziip.championshipscore.protocol.transport.MatchCommandPublisher;
import ink.ziip.championshipscore.protocol.transport.PlayerRoutingGateway;
import ink.ziip.championshipscore.protocol.transport.RouteReceipt;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/** Core-side authoritative replay and lifecycle for one remote execution. */
final class RemoteBingoMatch {
    /** Keep a full Event roster from opening all proxy backend handshakes in one tick. */
    private static final int ROUTE_BATCH_SIZE = 1;

    private static final long ROUTE_BATCH_DELAY_TICKS = 10L;

    private final ChampionshipsCore plugin;
    private final MatchManifest manifest;
    private final RemoteBingoInstance instance;
    private final MatchCommandPublisher commands;
    private final PlayerRoutingGateway router;
    private final String workerServer;
    private final BingoScoringEngine scoring;
    private final PlatformScheduler scheduler;
    private final MatchStateMachine lifecycle = new MatchStateMachine();
    private final Set<UUID> arrivedPlayers = new HashSet<>();
    private final Set<UUID> addedSpectators = new HashSet<>();
    private final Set<UUID> removedSpectators = new HashSet<>();
    private final List<PendingAward> pendingAwards = new ArrayList<>();
    private final Map<UUID, CompletableFuture<Boolean>> spectatorAddAcks =
            new ConcurrentHashMap<>();
    private final Map<UUID, CompletableFuture<Boolean>> spectatorRemoveAcks =
            new ConcurrentHashMap<>();

    /** Cells already claimed by any team, used to credit "first completion of the cell". */
    private final Set<Integer> claimedCells = new HashSet<>();

    private final Map<Integer, Integer> firstCompletionsByTeam = new ConcurrentHashMap<>();
    private final CompletableFuture<Void> terminal = new CompletableFuture<>();
    private final CompletableFuture<Void> resourcesReleased = new CompletableFuture<>();
    private boolean testAbortRequested;
    private boolean releaseAcknowledged;
    private boolean workerReadyObserved;
    private boolean testAdmissionRejected;
    private boolean normalStopRequested;
    private long lastEventSeq;
    private long lastActivityMillis = System.currentTimeMillis();

    /** Latest worker heartbeat online count; -1 means no valid heartbeat has arrived yet. */
    private int remoteOnlineParticipantCount = -1;

    RemoteBingoMatch(
            ChampionshipsCore plugin,
            MatchManifest manifest,
            RemoteBingoInstance instance,
            MatchCommandPublisher commands,
            PlayerRoutingGateway router,
            String workerServer) {
        this.plugin = plugin;
        this.manifest = manifest;
        this.instance = instance;
        this.commands = commands;
        this.router = router;
        this.workerServer = workerServer;
        this.scoring = new BingoScoringEngine(manifest);
        this.scheduler = new PlatformScheduler(plugin);
    }

    MatchManifest manifest() {
        return manifest;
    }

    RemoteBingoInstance instance() {
        return instance;
    }

    synchronized MatchState state() {
        return lifecycle.state();
    }

    CompletableFuture<Void> terminalFuture() {
        return terminal;
    }

    CompletableFuture<Void> resourcesReleasedFuture() {
        return resourcesReleased;
    }

    boolean resourcesReleased() {
        return resourcesReleased.isDone() && !resourcesReleased.isCompletedExceptionally();
    }

    synchronized boolean markTestAbortRequested() {
        if (testAbortRequested) return false;
        testAbortRequested = true;
        return true;
    }

    synchronized void resetTestAbortRequest() {
        testAbortRequested = false;
    }

    void confirmResourcesReleased() {
        synchronized (this) {
            if (!releaseAcknowledged)
                throw new IllegalStateException("Worker release was not acknowledged");
        }
        resourcesReleased.complete(null);
    }

    synchronized boolean markNormalStopRequested() {
        if (normalStopRequested) return false;
        normalStopRequested = true;
        return true;
    }

    synchronized void markPreparing() {
        if (lifecycle.state() == MatchState.CREATED) lifecycle.transitionTo(MatchState.PREPARING);
    }

    CompletionStage<Boolean> apply(MatchEvent event) {
        synchronized (this) {
            if (event.seq() <= lastEventSeq) return CompletableFuture.completedFuture(true);
            if (event.seq() != lastEventSeq + 1) return CompletableFuture.completedFuture(false);
            if (event.type() == ink.ziip.championshipscore.protocol.MatchEventType.READY)
                workerReadyObserved = true;
        }

        CompletionStage<Boolean> result =
                manifest.runMode().test()
                                && state().terminal()
                                && event.type()
                                        != ink.ziip.championshipscore.protocol.MatchEventType
                                                .RELEASED
                                && event.type()
                                        != ink.ziip.championshipscore.protocol.MatchEventType
                                                .PREPARE_FAILED
                        ? CompletableFuture.completedFuture(true)
                        : switch (event.type()) {
                            case READY -> onReady(event);
                            case PLAYER_ARRIVED -> onPlayerArrived(event);
                            case SPECTATOR_ADDED ->
                                    completed(() -> acknowledgeSpectator(event, spectatorAddAcks));
                            case SPECTATOR_REMOVED ->
                                    completed(
                                            () -> acknowledgeSpectator(event, spectatorRemoveAcks));
                            case STARTED -> completed(this::markStarted);
                            case TASK_COMPLETED -> completed(() -> applyCompletion(event));
                            case FINISHED -> finish(event);
                            case PREPARE_FAILED -> completed(() -> rejectPreparation(event));
                            case FAILED, ABORTED -> completed(this::abort);
                            case PLAYER_LEFT -> onPlayerLeft(event);
                            case HEARTBEAT -> completed(() -> applyHeartbeat(event));
                            case RELEASED -> completed(() -> acknowledgeRelease(event));
                        };
        return result.thenApply(
                success -> {
                    if (success) {
                        synchronized (this) {
                            lastEventSeq = event.seq();
                            lastActivityMillis = System.currentTimeMillis();
                        }
                    }
                    return success;
                });
    }

    private synchronized boolean acknowledgeRelease(MatchEvent event) {
        String worlds = event.attributes().getOrDefault("worlds", "");
        boolean unclaimed =
                "unclaimed".equals(worlds) && testAdmissionRejected && !workerReadyObserved;
        if (!manifest.runMode().test()
                || !state().terminal()
                || (!Set.of("reused", "reset").contains(worlds) && !unclaimed)) return false;
        releaseAcknowledged = true;
        return true;
    }

    private synchronized boolean rejectPreparation(MatchEvent event) {
        testAdmissionRejected =
                manifest.runMode().test()
                        && !workerReadyObserved
                        && "test-disabled".equals(event.attributes().get("reason"));
        return abort();
    }

    private CompletionStage<Boolean> onReady(MatchEvent event) {
        synchronized (this) {
            if (lifecycle.state() != MatchState.PREPARING
                    && lifecycle.state() != MatchState.READY) {
                return CompletableFuture.completedFuture(false);
            }
            String hash = event.attributes().get("configHash");
            if (!manifest.configHash().equals(hash)) {
                throw new IllegalStateException("Worker prepared a different Bingo config hash");
            }
            if (lifecycle.state() == MatchState.PREPARING) {
                lifecycle.transitionTo(MatchState.READY);
                instance.markReady();
            }
        }

        long expires = System.currentTimeMillis() + 120_000L;
        List<ink.ziip.championshipscore.protocol.PlayerSnapshot> routedParticipants =
                manifest.participants().stream()
                        .filter(
                                player ->
                                        player.requiredAtStart()
                                                || plugin.getServer().getPlayer(player.uuid())
                                                        != null)
                        .toList();
        List<PlayerRoute> routes =
                routedParticipants.stream()
                        .map(
                                player ->
                                        new PlayerRoute(
                                                player.uuid(),
                                                manifest.matchId(),
                                                manifest.epoch(),
                                                workerServer,
                                                player.role(),
                                                expires))
                        .toList();
        CompletionStage<List<RouteReceipt>> routing =
                manifest.runMode().test()
                        ? routeParticipantsInBatches(routes)
                        : routeParticipantsAtOnce(routes);
        return routing.thenApply(
                receipts -> {
                    for (int index = 0; index < receipts.size(); index++) {
                        RouteReceipt receipt = receipts.get(index);
                        if (!receipt.accepted()
                                && routedParticipants.get(index).role() == ParticipantRole.PLAYER) {
                            return false;
                        }
                    }
                    synchronized (this) {
                        if (lifecycle.state() == MatchState.READY)
                            lifecycle.transitionTo(MatchState.ROUTING);
                    }
                    return true;
                });
    }

    private CompletionStage<List<RouteReceipt>> routeParticipantsInBatches(
            List<PlayerRoute> routes) {
        CompletableFuture<List<RouteReceipt>> result = new CompletableFuture<>();
        routeParticipantBatch(routes, 0, new ArrayList<>(), result);
        return result;
    }

    /** Preserve the established formal GAME/EVENT/DAILY routing timing. */
    private CompletionStage<List<RouteReceipt>> routeParticipantsAtOnce(List<PlayerRoute> routes) {
        List<CompletionStage<RouteReceipt>> pending = routes.stream().map(router::route).toList();
        CompletableFuture<?>[] futures =
                pending.stream()
                        .map(CompletionStage::toCompletableFuture)
                        .toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(futures)
                .thenApply(
                        ignored ->
                                pending.stream()
                                        .map(stage -> stage.toCompletableFuture().join())
                                        .toList());
    }

    private void routeParticipantBatch(
            List<PlayerRoute> routes,
            int offset,
            List<RouteReceipt> receipts,
            CompletableFuture<List<RouteReceipt>> result) {
        if (result.isDone()) return;
        if (offset >= routes.size()) {
            result.complete(List.copyOf(receipts));
            return;
        }
        int end = Math.min(routes.size(), offset + ROUTE_BATCH_SIZE);
        List<CompletableFuture<RouteReceipt>> batch = new ArrayList<>(end - offset);
        for (int index = offset; index < end; index++) {
            PlayerRoute route = routes.get(index);
            int sequence = index + 1;
            logTestRoute("dispatch", route, sequence, routes.size(), "");
            try {
                CompletableFuture<RouteReceipt> routed = router.route(route).toCompletableFuture();
                routed.whenComplete(
                        (receipt, failure) -> {
                            if (failure != null) {
                                logTestRoute(
                                        "route-failed",
                                        route,
                                        sequence,
                                        routes.size(),
                                        failure.getClass().getSimpleName());
                            } else {
                                logTestRoute(
                                        "route-receipt",
                                        route,
                                        sequence,
                                        routes.size(),
                                        "accepted="
                                                + receipt.accepted()
                                                + " detail="
                                                + receipt.detail());
                            }
                        });
                batch.add(routed);
            } catch (RuntimeException failure) {
                result.completeExceptionally(failure);
                return;
            }
        }
        CompletableFuture.allOf(batch.toArray(CompletableFuture[]::new))
                .whenComplete(
                        (ignored, failure) -> {
                            if (failure != null) {
                                result.completeExceptionally(failure);
                                return;
                            }
                            batch.forEach(future -> receipts.add(future.join()));
                            if (end >= routes.size()) {
                                result.complete(List.copyOf(receipts));
                            } else {
                                if (manifest.runMode().test()) {
                                    plugin.getLogger()
                                            .info(
                                                    "TEST_ROUTE match="
                                                            + manifest.matchId()
                                                            + " epoch="
                                                            + manifest.epoch()
                                                            + " stage=next-batch-wait nextSeq="
                                                            + (end + 1)
                                                            + "/"
                                                            + routes.size()
                                                            + " delayTicks="
                                                            + ROUTE_BATCH_DELAY_TICKS
                                                            + " atMs="
                                                            + System.currentTimeMillis());
                                }
                                scheduler.runGlobalLater(
                                        () -> routeParticipantBatch(routes, end, receipts, result),
                                        ROUTE_BATCH_DELAY_TICKS);
                            }
                        });
    }

    private CompletionStage<Boolean> onPlayerArrived(MatchEvent event) {
        UUID playerId;
        try {
            playerId = UUID.fromString(required(event, "playerId"));
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("Invalid PLAYER_ARRIVED event", invalid);
        }
        if (manifest.runMode().test()) {
            plugin.getLogger()
                    .info(
                            "TEST_ROUTE match="
                                    + manifest.matchId()
                                    + " epoch="
                                    + manifest.epoch()
                                    + " player="
                                    + playerId
                                    + " role="
                                    + event.attributes().getOrDefault("role", "unknown")
                                    + " stage=worker-arrived-event eventSeq="
                                    + event.seq()
                                    + " atMs="
                                    + System.currentTimeMillis());
        }
        boolean shouldCommit;
        synchronized (this) {
            if (lifecycle.state() != MatchState.ROUTING) {
                ParticipantRole role = roleOf(playerId);
                boolean liveLateArrival =
                        role != null
                                && role.name().equals(event.attributes().get("role"))
                                && (lifecycle.state() == MatchState.COUNTDOWN
                                        || lifecycle.state() == MatchState.RUNNING);
                return CompletableFuture.completedFuture(liveLateArrival);
            }
            boolean isPlayer =
                    manifest.participants().stream()
                            .anyMatch(
                                    player ->
                                            player.uuid().equals(playerId)
                                                    && player.role() == ParticipantRole.PLAYER);
            if (isPlayer) arrivedPlayers.add(playerId);
            shouldCommit =
                    manifest.participants().stream()
                            .filter(
                                    player ->
                                            player.role() == ParticipantRole.PLAYER
                                                    && player.requiredAtStart())
                            .allMatch(player -> arrivedPlayers.contains(player.uuid()));
        }
        if (!shouldCommit) return CompletableFuture.completedFuture(true);
        MatchCommand start =
                MatchMessages.command(
                        manifest.matchId(), manifest.epoch(), MatchCommandType.START_COMMIT);
        return commands.publishCommand(start)
                .thenApply(
                        ignored -> {
                            synchronized (this) {
                                if (lifecycle.state() == MatchState.ROUTING) {
                                    lifecycle.transitionTo(MatchState.COUNTDOWN);
                                    instance.markCountdown();
                                }
                            }
                            return true;
                        });
    }

    private void logTestRoute(
            String stage, PlayerRoute route, int sequence, int total, String detail) {
        if (!manifest.runMode().test()) return;
        plugin.getLogger()
                .info(
                        "TEST_ROUTE match="
                                + manifest.matchId()
                                + " epoch="
                                + manifest.epoch()
                                + " seq="
                                + sequence
                                + "/"
                                + total
                                + " player="
                                + route.playerId()
                                + " role="
                                + route.role()
                                + " target="
                                + route.serverName()
                                + " stage="
                                + stage
                                + (detail.isBlank() ? "" : " " + detail)
                                + " atMs="
                                + System.currentTimeMillis());
    }

    private CompletionStage<Boolean> onPlayerLeft(MatchEvent event) {
        if (!"voluntary".equals(event.attributes().get("intent")))
            return CompletableFuture.completedFuture(true);
        UUID playerId = UUID.fromString(required(event, "playerId"));
        Set<UUID> leaving = plugin.getDailyManager().leaveActiveFromRemote(instance, playerId);
        if (leaving.isEmpty()) return CompletableFuture.completedFuture(true);
        return removeParticipants(leaving);
    }

    private boolean markStarted() {
        synchronized (this) {
            if (lifecycle.state() != MatchState.COUNTDOWN) return false;
            lifecycle.transitionTo(MatchState.RUNNING);
            instance.markStarted();
            return true;
        }
    }

    private boolean applyCompletion(MatchEvent event) {
        synchronized (this) {
            if (lifecycle.state() != MatchState.RUNNING) return false;
        }
        ScoringDecision decision = scoring.apply(MatchMessages.completionObservation(event));
        if (!decision.accepted()) return true;
        synchronized (this) {
            if (claimedCells.add(decision.observation().cellIndex())) {
                firstCompletionsByTeam.merge(decision.observation().teamId(), 1, Integer::sum);
            }
        }
        for (PlayerAward award : decision.awards()) {
            instance.applyAward(award.playerId(), award.points());
            pendingAwards.add(new PendingAward(decision.observation().seq(), award));
        }
        if (instance.getRunMode() == GameRunMode.DAILY) {
            List<Integer> progressedTeams =
                    manifest.scoring().variant().remix()
                                    == ink.ziip.championshipscore.protocol.BingoRemix.COOP
                            ? manifest.teams().stream()
                                    .map(ink.ziip.championshipscore.protocol.TeamSnapshot::id)
                                    .toList()
                            : List.of(decision.observation().teamId());
            for (int progressedTeamId : progressedTeams) {
                ChampionshipTeam team =
                        instance.getGameTeams().stream()
                                .filter(
                                        candidate ->
                                                candidate
                                                        .getMembers()
                                                        .equals(
                                                                Set.copyOf(
                                                                        manifest.teamsById()
                                                                                .get(
                                                                                        progressedTeamId)
                                                                                .members())))
                                .findFirst()
                                .orElse(null);
                if (team == null) continue;
                long durationMillis = decision.observation().observedGameTick() * 50L;
                int completed = scoring.result().completedCells().getOrDefault(progressedTeamId, 0);
                int firsts = firstCompletionsByTeam.getOrDefault(progressedTeamId, 0);
                plugin.getDailyManager()
                        .statsManager()
                        .recordBingoProgress(
                                instance, team, decision.completedLines(), completed, firsts);
                if (decision.completedLines() > 0) {
                    plugin.getDailyManager()
                            .statsManager()
                            .recordTeamMilestone(
                                    instance,
                                    team,
                                    DailyRecordType.BINGO_FIRST_LINE,
                                    durationMillis,
                                    decision.observation().playerId());
                }
                if (completed >= manifest.tasks().size()) {
                    plugin.getDailyManager()
                            .statsManager()
                            .recordTeamMilestone(
                                    instance,
                                    team,
                                    DailyRecordType.BINGO_FULL_CARD,
                                    durationMillis,
                                    decision.observation().playerId());
                }
            }
        }
        return true;
    }

    private CompletionStage<Boolean> finish(MatchEvent event) {
        BingoResult local = scoring.result();
        if (!local.resultHash().equals(required(event, "resultHash"))) {
            throw new IllegalStateException(
                    "Worker/Core Bingo result hash mismatch for " + manifest.matchId());
        }
        List<ink.ziip.championshipscore.api.rank.RankManager.PointSubmission> submissions =
                new ArrayList<>();
        if (instance.isFormalEventRun() || manifest.runMode().test()) {
            for (PendingAward pending : pendingAwards) {
                PlayerAward award = pending.award();
                UUID transactionId =
                        DeterministicIds.scoreTransaction(
                                manifest.matchId(),
                                manifest.epoch(),
                                pending.completionSeq(),
                                award.playerId(),
                                award.kind());
                submissions.add(
                        new ink.ziip.championshipscore.api.rank.RankManager.PointSubmission(
                                transactionId,
                                award.playerId(),
                                null,
                                GameTypeEnum.Bingo,
                                instance.getGameConfig().getAreaName(),
                                manifest.matchId() + ":" + manifest.epoch(),
                                award.points()));
            }
        }
        return plugin.getRankManager()
                .addPlayerPointsBatch(submissions, manifest.runMode().test())
                .thenCompose(
                        staged ->
                                staged
                                        ? scheduler.supplyGlobal(this::finishAccepted)
                                        : CompletableFuture.completedFuture(false));
    }

    private boolean finishAccepted() {
        synchronized (this) {
            if (lifecycle.state() != MatchState.RUNNING) return false;
            lifecycle.transitionTo(MatchState.SETTLING);
            lifecycle.transitionTo(MatchState.FINISHED);
            completeSpectatorAcks(false);
        }
        if (instance.isFormalEventRun()) plugin.getRankManager().refreshAfterPendingPointWrites();
        instance.completeFromRemote();
        terminal.complete(null);
        return true;
    }

    private boolean abort() {
        synchronized (this) {
            if (!lifecycle.state().terminal()) lifecycle.transitionTo(MatchState.ABORTED);
            completeSpectatorAcks(false);
        }
        terminal.complete(null);
        return true;
    }

    void abortLocally() {
        abort();
    }

    private void completeSpectatorAcks(boolean value) {
        spectatorAddAcks.values().forEach(future -> future.complete(value));
        spectatorRemoveAcks.values().forEach(future -> future.complete(value));
        spectatorAddAcks.clear();
        spectatorRemoveAcks.clear();
    }

    MatchCommand abortCommand(String reason) {
        var attributes = new java.util.HashMap<String, String>();
        attributes.put("reason", reason);
        if (manifest.runMode().test()) {
            String bots =
                    manifest.participants().stream()
                            .filter(
                                    player ->
                                            plugin.getPlayerManager()
                                                            .isEphemeralTestIdentity(player.uuid())
                                                    && (plugin.getTestSessionManager() == null
                                                            || !plugin.getTestSessionManager()
                                                                    .hasUsableReconnectLease(
                                                                            player.username(),
                                                                            player.uuid())))
                            .map(player -> player.uuid().toString())
                            .collect(java.util.stream.Collectors.joining(","));
            attributes.put("disconnectPlayers", bots);
        }
        return MatchMessages.command(
                manifest.matchId(),
                manifest.epoch(),
                MatchCommandType.ABORT,
                attributes,
                Clock.systemUTC());
    }

    MatchCommand forceEndCommand(String reason) {
        return MatchMessages.command(
                manifest.matchId(),
                manifest.epoch(),
                MatchCommandType.FORCE_END,
                Map.of("reason", reason),
                Clock.systemUTC());
    }

    CompletionStage<Boolean> removeParticipants(Set<UUID> players) {
        if (players.isEmpty()) return CompletableFuture.completedFuture(true);
        String joined =
                players.stream()
                        .map(UUID::toString)
                        .sorted()
                        .collect(java.util.stream.Collectors.joining(","));
        MatchCommand command =
                MatchMessages.command(
                        manifest.matchId(),
                        manifest.epoch(),
                        MatchCommandType.REMOVE_PARTICIPANTS,
                        Map.of("players", joined),
                        Clock.systemUTC());
        return commands.publishCommand(command).thenApply(ignored -> true);
    }

    CompletionStage<Boolean> addSpectator(UUID playerId, String username, double points) {
        synchronized (this) {
            if (lifecycle.state().terminal()) return CompletableFuture.completedFuture(false);
            removedSpectators.remove(playerId);
            addedSpectators.add(playerId);
        }
        MatchCommand add =
                MatchMessages.command(
                        manifest.matchId(),
                        manifest.epoch(),
                        MatchCommandType.ADD_SPECTATOR,
                        Map.of(
                                "playerId",
                                playerId.toString(),
                                "username",
                                username,
                                "points",
                                Double.toString(points)),
                        Clock.systemUTC());
        CompletableFuture<Boolean> acknowledged = new CompletableFuture<>();
        spectatorAddAcks.put(playerId, acknowledged);
        return commands.publishCommand(add)
                .thenCompose(ignored -> acknowledged.orTimeout(10, TimeUnit.SECONDS))
                .thenCompose(
                        accepted -> {
                            if (!accepted) return CompletableFuture.completedFuture(false);
                            return router.route(
                                            new PlayerRoute(
                                                    playerId,
                                                    manifest.matchId(),
                                                    manifest.epoch(),
                                                    workerServer,
                                                    ParticipantRole.SPECTATOR,
                                                    System.currentTimeMillis() + 120_000L))
                                    .thenApply(RouteReceipt::accepted);
                        })
                .whenComplete(
                        (ignored, failure) -> spectatorAddAcks.remove(playerId, acknowledged));
    }

    CompletionStage<Boolean> removeSpectator(UUID playerId) {
        synchronized (this) {
            boolean frozenSpectator =
                    manifest.participants().stream()
                            .anyMatch(
                                    player ->
                                            player.uuid().equals(playerId)
                                                    && player.role() == ParticipantRole.SPECTATOR);
            if (!frozenSpectator && !addedSpectators.contains(playerId)) {
                return CompletableFuture.completedFuture(true);
            }
            removedSpectators.add(playerId);
            addedSpectators.remove(playerId);
            if (lifecycle.state().terminal()) return CompletableFuture.completedFuture(true);
        }
        MatchCommand remove =
                MatchMessages.command(
                        manifest.matchId(),
                        manifest.epoch(),
                        MatchCommandType.REMOVE_SPECTATOR,
                        Map.of("playerId", playerId.toString()),
                        Clock.systemUTC());
        CompletableFuture<Boolean> acknowledged = new CompletableFuture<>();
        spectatorRemoveAcks.put(playerId, acknowledged);
        return commands.publishCommand(remove)
                .thenCompose(ignored -> acknowledged.orTimeout(10, TimeUnit.SECONDS))
                .whenComplete(
                        (ignored, failure) -> spectatorRemoveAcks.remove(playerId, acknowledged));
    }

    private boolean acknowledgeSpectator(
            MatchEvent event, Map<UUID, CompletableFuture<Boolean>> acknowledgements) {
        UUID playerId = UUID.fromString(required(event, "playerId"));
        CompletableFuture<Boolean> acknowledgement = acknowledgements.remove(playerId);
        if (acknowledgement != null) acknowledgement.complete(true);
        return true;
    }

    ParticipantRole roleOf(UUID playerId) {
        synchronized (this) {
            if (removedSpectators.contains(playerId)) return null;
        }
        ParticipantRole frozen =
                manifest.participants().stream()
                        .filter(player -> player.uuid().equals(playerId))
                        .map(player -> player.role())
                        .findFirst()
                        .orElse(null);
        if (frozen != null) return frozen;
        synchronized (this) {
            return addedSpectators.contains(playerId) ? ParticipantRole.SPECTATOR : null;
        }
    }

    synchronized boolean heartbeatExpired(long nowMillis, long timeoutMillis) {
        return (lifecycle.state() == MatchState.COUNTDOWN
                        || lifecycle.state() == MatchState.RUNNING)
                && nowMillis - lastActivityMillis > timeoutMillis;
    }

    synchronized boolean allRemoteParticipantsOffline() {
        return remoteOnlineParticipantCount == 0;
    }

    private synchronized boolean applyHeartbeat(MatchEvent event) {
        String online = event.attributes().get("online");
        if (online == null) return true;
        try {
            int count = Integer.parseInt(online);
            if (count >= 0) remoteOnlineParticipantCount = count;
        } catch (NumberFormatException ignored) {
            // Keep accepting the heartbeat for liveness even if an older worker omits a valid
            // count.
        }
        return true;
    }

    private static CompletionStage<Boolean> completed(java.util.function.BooleanSupplier action) {
        return CompletableFuture.completedFuture(action.getAsBoolean());
    }

    private static String required(MatchEvent event, String key) {
        String value = event.attributes().get(key);
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("Missing event attribute " + key);
        return value;
    }

    private record PendingAward(long completionSeq, PlayerAward award) {}
}
