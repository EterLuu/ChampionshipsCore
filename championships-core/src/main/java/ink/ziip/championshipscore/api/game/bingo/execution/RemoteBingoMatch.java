package ink.ziip.championshipscore.api.game.bingo.execution;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.bingo.engine.BingoResult;
import ink.ziip.championshipscore.bingo.engine.BingoScoringEngine;
import ink.ziip.championshipscore.bingo.engine.PlayerAward;
import ink.ziip.championshipscore.bingo.engine.ScoringDecision;
import ink.ziip.championshipscore.api.daily.DailyRecordType;
import ink.ziip.championshipscore.api.object.game.GameRunMode;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.protocol.DeterministicIds;
import ink.ziip.championshipscore.protocol.MatchCommand;
import ink.ziip.championshipscore.protocol.MatchCommandType;
import ink.ziip.championshipscore.protocol.MatchEvent;
import ink.ziip.championshipscore.protocol.MatchEventType;
import ink.ziip.championshipscore.protocol.MatchManifest;
import ink.ziip.championshipscore.protocol.MatchMessages;
import ink.ziip.championshipscore.protocol.MatchState;
import ink.ziip.championshipscore.protocol.MatchStateMachine;
import ink.ziip.championshipscore.protocol.ParticipantRole;
import ink.ziip.championshipscore.protocol.PlayerRoute;
import ink.ziip.championshipscore.protocol.transport.MatchCommandPublisher;
import ink.ziip.championshipscore.protocol.transport.PlayerRoutingGateway;
import ink.ziip.championshipscore.protocol.transport.RouteReceipt;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;

import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;

/** Core-side authoritative replay and lifecycle for one remote execution. */
final class RemoteBingoMatch {
    private final ChampionshipsCore plugin;
    private final MatchManifest manifest;
    private final RemoteBingoInstance instance;
    private final MatchCommandPublisher commands;
    private final PlayerRoutingGateway router;
    private final String workerServer;
    private final BingoScoringEngine scoring;
    private final MatchStateMachine lifecycle = new MatchStateMachine();
    private final Set<UUID> arrivedPlayers = new HashSet<>();
    private final Set<UUID> addedSpectators = new HashSet<>();
    private final Set<UUID> removedSpectators = new HashSet<>();
    private final List<PendingAward> pendingAwards = new ArrayList<>();
    private final Map<UUID, CompletableFuture<Boolean>> spectatorAddAcks = new ConcurrentHashMap<>();
    private final Map<UUID, CompletableFuture<Boolean>> spectatorRemoveAcks = new ConcurrentHashMap<>();
    private long lastEventSeq;
    private long lastActivityMillis = System.currentTimeMillis();

    RemoteBingoMatch(ChampionshipsCore plugin, MatchManifest manifest, RemoteBingoInstance instance,
                     MatchCommandPublisher commands, PlayerRoutingGateway router, String workerServer) {
        this.plugin = plugin;
        this.manifest = manifest;
        this.instance = instance;
        this.commands = commands;
        this.router = router;
        this.workerServer = workerServer;
        this.scoring = new BingoScoringEngine(manifest);
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

    synchronized void markPreparing() {
        if (lifecycle.state() == MatchState.CREATED) lifecycle.transitionTo(MatchState.PREPARING);
    }

    CompletionStage<Boolean> apply(MatchEvent event) {
        synchronized (this) {
            if (event.seq() <= lastEventSeq) return CompletableFuture.completedFuture(true);
            if (event.seq() != lastEventSeq + 1) return CompletableFuture.completedFuture(false);
        }

        CompletionStage<Boolean> result = switch (event.type()) {
            case READY -> onReady(event);
            case PLAYER_ARRIVED -> onPlayerArrived(event);
            case SPECTATOR_ADDED -> completed(() -> acknowledgeSpectator(event, spectatorAddAcks));
            case SPECTATOR_REMOVED -> completed(() -> acknowledgeSpectator(event, spectatorRemoveAcks));
            case STARTED -> completed(this::markStarted);
            case TASK_COMPLETED -> completed(() -> applyCompletion(event));
            case FINISHED -> completed(() -> finish(event));
            case PREPARE_FAILED, FAILED, ABORTED -> completed(this::abort);
            case PLAYER_LEFT -> onPlayerLeft(event);
            case HEARTBEAT -> CompletableFuture.completedFuture(true);
        };
        return result.thenApply(success -> {
            if (success) {
                synchronized (this) {
                    lastEventSeq = event.seq();
                    lastActivityMillis = System.currentTimeMillis();
                }
            }
            return success;
        });
    }

    private CompletionStage<Boolean> onReady(MatchEvent event) {
        synchronized (this) {
            if (lifecycle.state() != MatchState.PREPARING && lifecycle.state() != MatchState.READY) {
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
        List<ink.ziip.championshipscore.protocol.PlayerSnapshot> routedParticipants = manifest.participants().stream()
                .filter(player -> player.requiredAtStart()
                        || plugin.getServer().getPlayer(player.uuid()) != null)
                .toList();
        List<CompletionStage<RouteReceipt>> routes = routedParticipants.stream()
                .map(player -> router.route(new PlayerRoute(player.uuid(), manifest.matchId(), manifest.epoch(),
                        workerServer, player.role(), expires)))
                .toList();
        CompletableFuture<?>[] futures = routes.stream().map(CompletionStage::toCompletableFuture)
                .toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(futures).thenApply(ignored -> {
            for (int index = 0; index < routes.size(); index++) {
                RouteReceipt receipt = routes.get(index).toCompletableFuture().join();
                if (!receipt.accepted()
                        && routedParticipants.get(index).role() == ParticipantRole.PLAYER) {
                    return false;
                }
            }
            synchronized (this) {
                if (lifecycle.state() == MatchState.READY) lifecycle.transitionTo(MatchState.ROUTING);
            }
            return true;
        });
    }

    private CompletionStage<Boolean> onPlayerArrived(MatchEvent event) {
        UUID playerId;
        try {
            playerId = UUID.fromString(required(event, "playerId"));
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("Invalid PLAYER_ARRIVED event", invalid);
        }
        boolean shouldCommit;
        synchronized (this) {
            if (lifecycle.state() != MatchState.ROUTING) {
                ParticipantRole role = roleOf(playerId);
                boolean liveLateArrival = role != null && role.name().equals(event.attributes().get("role"))
                        && (lifecycle.state() == MatchState.COUNTDOWN || lifecycle.state() == MatchState.RUNNING);
                return CompletableFuture.completedFuture(liveLateArrival);
            }
            boolean isPlayer = manifest.participants().stream().anyMatch(player ->
                    player.uuid().equals(playerId) && player.role() == ParticipantRole.PLAYER);
            if (isPlayer) arrivedPlayers.add(playerId);
            shouldCommit = manifest.participants().stream()
                    .filter(player -> player.role() == ParticipantRole.PLAYER && player.requiredAtStart())
                    .allMatch(player -> arrivedPlayers.contains(player.uuid()));
        }
        if (!shouldCommit) return CompletableFuture.completedFuture(true);
        MatchCommand start = MatchMessages.command(manifest.matchId(), manifest.epoch(),
                MatchCommandType.START_COMMIT);
        return commands.publishCommand(start).thenApply(ignored -> {
            synchronized (this) {
                if (lifecycle.state() == MatchState.ROUTING) {
                    lifecycle.transitionTo(MatchState.COUNTDOWN);
                    instance.markCountdown();
                }
            }
            return true;
        });
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
        for (PlayerAward award : decision.awards()) {
            instance.applyAward(award.playerId(), award.points());
            pendingAwards.add(new PendingAward(decision.observation().seq(), award));
        }
        if (instance.getRunMode() == GameRunMode.DAILY) {
            ChampionshipTeam team = instance.getGameTeams().stream()
                    .filter(candidate -> candidate.getMembers().equals(
                            Set.copyOf(manifest.teamsById().get(decision.observation().teamId()).members())))
                    .findFirst().orElse(null);
            if (team != null) {
                long durationMillis = decision.observation().observedGameTick() * 50L;
                if (decision.completedLines() > 0) {
                    plugin.getDailyManager().statsManager().recordTeamMilestone(instance, team,
                            DailyRecordType.BINGO_FIRST_LINE, durationMillis,
                            decision.observation().playerId());
                }
                int completed = scoring.result().completedCells()
                        .getOrDefault(decision.observation().teamId(), 0);
                if (completed >= manifest.tasks().size()) {
                    plugin.getDailyManager().statsManager().recordTeamMilestone(instance, team,
                            DailyRecordType.BINGO_FULL_CARD, durationMillis,
                            decision.observation().playerId());
                }
            }
        }
        return true;
    }

    private boolean finish(MatchEvent event) {
        BingoResult local = scoring.result();
        if (!local.resultHash().equals(required(event, "resultHash"))) {
            throw new IllegalStateException("Worker/Core Bingo result hash mismatch for " + manifest.matchId());
        }
        if (instance.isEventRun()) {
            for (PendingAward pending : pendingAwards) {
                PlayerAward award = pending.award();
                UUID transactionId = DeterministicIds.scoreTransaction(manifest.matchId(), manifest.epoch(),
                        pending.completionSeq(), award.playerId(), award.kind());
                boolean staged = plugin.getRankManager().addPlayerPointsWithTransaction(transactionId,
                        award.playerId(), null, GameTypeEnum.Bingo, instance.getGameConfig().getAreaName(),
                        manifest.matchId() + ":" + manifest.epoch(), award.points());
                if (!staged) return false;
            }
        }
        synchronized (this) {
            if (lifecycle.state() != MatchState.RUNNING) return false;
            lifecycle.transitionTo(MatchState.SETTLING);
            lifecycle.transitionTo(MatchState.FINISHED);
            completeSpectatorAcks(false);
        }
        if (instance.isEventRun()) plugin.getRankManager().refreshAfterPendingPointWrites();
        instance.completeFromRemote();
        return true;
    }

    private boolean abort() {
        synchronized (this) {
            if (!lifecycle.state().terminal()) lifecycle.transitionTo(MatchState.ABORTED);
            completeSpectatorAcks(false);
        }
        return true;
    }

    private void completeSpectatorAcks(boolean value) {
        spectatorAddAcks.values().forEach(future -> future.complete(value));
        spectatorRemoveAcks.values().forEach(future -> future.complete(value));
        spectatorAddAcks.clear();
        spectatorRemoveAcks.clear();
    }

    MatchCommand abortCommand(String reason) {
        return MatchMessages.command(manifest.matchId(), manifest.epoch(), MatchCommandType.ABORT,
                Map.of("reason", reason), Clock.systemUTC());
    }

    MatchCommand forceEndCommand(String reason) {
        return MatchMessages.command(manifest.matchId(), manifest.epoch(), MatchCommandType.FORCE_END,
                Map.of("reason", reason), Clock.systemUTC());
    }

    CompletionStage<Boolean> removeParticipants(Set<UUID> players) {
        if (players.isEmpty()) return CompletableFuture.completedFuture(true);
        String joined = players.stream().map(UUID::toString).sorted()
                .collect(java.util.stream.Collectors.joining(","));
        MatchCommand command = MatchMessages.command(manifest.matchId(), manifest.epoch(),
                MatchCommandType.REMOVE_PARTICIPANTS, Map.of("players", joined), Clock.systemUTC());
        return commands.publishCommand(command).thenApply(ignored -> true);
    }

    CompletionStage<Boolean> addSpectator(UUID playerId, String username) {
        synchronized (this) {
            if (lifecycle.state().terminal()) return CompletableFuture.completedFuture(false);
            removedSpectators.remove(playerId);
            addedSpectators.add(playerId);
        }
        MatchCommand add = MatchMessages.command(manifest.matchId(), manifest.epoch(),
                MatchCommandType.ADD_SPECTATOR,
                Map.of("playerId", playerId.toString(), "username", username), Clock.systemUTC());
        CompletableFuture<Boolean> acknowledged = new CompletableFuture<>();
        spectatorAddAcks.put(playerId, acknowledged);
        return commands.publishCommand(add)
                .thenCompose(ignored -> acknowledged.orTimeout(10, TimeUnit.SECONDS))
                .thenCompose(accepted -> {
                    if (!accepted) return CompletableFuture.completedFuture(false);
                    return router.route(new PlayerRoute(playerId, manifest.matchId(), manifest.epoch(),
                                    workerServer, ParticipantRole.SPECTATOR,
                                    System.currentTimeMillis() + 120_000L))
                            .thenApply(RouteReceipt::accepted);
                })
                .whenComplete((ignored, failure) -> spectatorAddAcks.remove(playerId, acknowledged));
    }

    CompletionStage<Boolean> removeSpectator(UUID playerId) {
        synchronized (this) {
            boolean frozenSpectator = manifest.participants().stream().anyMatch(player ->
                    player.uuid().equals(playerId) && player.role() == ParticipantRole.SPECTATOR);
            if (!frozenSpectator && !addedSpectators.contains(playerId)) {
                return CompletableFuture.completedFuture(true);
            }
            removedSpectators.add(playerId);
            addedSpectators.remove(playerId);
            if (lifecycle.state().terminal()) return CompletableFuture.completedFuture(true);
        }
        MatchCommand remove = MatchMessages.command(manifest.matchId(), manifest.epoch(),
                MatchCommandType.REMOVE_SPECTATOR, Map.of("playerId", playerId.toString()), Clock.systemUTC());
        CompletableFuture<Boolean> acknowledged = new CompletableFuture<>();
        spectatorRemoveAcks.put(playerId, acknowledged);
        return commands.publishCommand(remove)
                .thenCompose(ignored -> acknowledged.orTimeout(10, TimeUnit.SECONDS))
                .whenComplete((ignored, failure) -> spectatorRemoveAcks.remove(playerId, acknowledged));
    }

    private boolean acknowledgeSpectator(MatchEvent event,
                                          Map<UUID, CompletableFuture<Boolean>> acknowledgements) {
        UUID playerId = UUID.fromString(required(event, "playerId"));
        CompletableFuture<Boolean> acknowledgement = acknowledgements.remove(playerId);
        if (acknowledgement != null) acknowledgement.complete(true);
        return true;
    }

    ParticipantRole roleOf(UUID playerId) {
        synchronized (this) {
            if (removedSpectators.contains(playerId)) return null;
        }
        ParticipantRole frozen = manifest.participants().stream()
                .filter(player -> player.uuid().equals(playerId)).map(player -> player.role()).findFirst().orElse(null);
        if (frozen != null) return frozen;
        synchronized (this) {
            return addedSpectators.contains(playerId) ? ParticipantRole.SPECTATOR : null;
        }
    }

    synchronized boolean heartbeatExpired(long nowMillis, long timeoutMillis) {
        return (lifecycle.state() == MatchState.COUNTDOWN || lifecycle.state() == MatchState.RUNNING)
                && nowMillis - lastActivityMillis > timeoutMillis;
    }

    private static CompletionStage<Boolean> completed(java.util.function.BooleanSupplier action) {
        return CompletableFuture.completedFuture(action.getAsBoolean());
    }

    private static String required(MatchEvent event, String key) {
        String value = event.attributes().get(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing event attribute " + key);
        return value;
    }

    private record PendingAward(long completionSeq, PlayerAward award) {
    }
}
