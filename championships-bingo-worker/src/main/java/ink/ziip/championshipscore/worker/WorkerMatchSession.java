package ink.ziip.championshipscore.worker;

import ink.ziip.championshipscore.bingo.engine.BingoResult;
import ink.ziip.championshipscore.bingo.engine.BingoScoringEngine;
import ink.ziip.championshipscore.bingo.engine.ScoringDecision;
import ink.ziip.championshipscore.platform.bukkit.bingo.BingoStarterKitService;
import ink.ziip.championshipscore.platform.bukkit.bingo.BingoPermanentEffectService;
import ink.ziip.championshipscore.platform.bukkit.bingo.BingoSpectatorService;
import ink.ziip.championshipscore.platform.bukkit.bingo.map.TaskImageAtlas;
import ink.ziip.championshipscore.platform.bukkit.scheduler.PlatformScheduler;
import ink.ziip.championshipscore.platform.bukkit.scoreboard.SharedSidebar;
import ink.ziip.championshipscore.platform.bukkit.world.SafeScatterService;
import ink.ziip.championshipscore.protocol.CompletionObservation;
import ink.ziip.championshipscore.protocol.BingoManifestHasher;
import ink.ziip.championshipscore.protocol.BingoIntroductionMode;
import ink.ziip.championshipscore.protocol.BingoLocationSnapshot;
import ink.ziip.championshipscore.protocol.BingoPresentation;
import ink.ziip.championshipscore.protocol.BingoMode;
import ink.ziip.championshipscore.protocol.BingoRemix;
import ink.ziip.championshipscore.protocol.BingoTaskSpec;
import ink.ziip.championshipscore.protocol.MatchEvent;
import ink.ziip.championshipscore.protocol.MatchEventType;
import ink.ziip.championshipscore.protocol.MatchManifest;
import ink.ziip.championshipscore.protocol.MatchMessages;
import ink.ziip.championshipscore.protocol.MatchRunMode;
import ink.ziip.championshipscore.protocol.MatchState;
import ink.ziip.championshipscore.protocol.MatchStateMachine;
import ink.ziip.championshipscore.protocol.ParticipantRole;
import ink.ziip.championshipscore.protocol.PlayerSnapshot;
import ink.ziip.championshipscore.protocol.TeamSnapshot;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.Instrument;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Note;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/** One isolated match coordinator. It never reads Bukkit state while holding its state lock. */
final class WorkerMatchSession {
    private final Plugin plugin;
    private final WorkerConfig config;
    private final MatchManifest manifest;
    private final DurableEventOutbox events;
    private final WorkerReturnRouter returnRouter;
    private final WorkerWorldController worlds;
    private final Runnable worldReset;
    private final PlatformScheduler scheduler;
    private final NamespacedKey cardKey;
    private final NamespacedKey spectatorControlKey;
    private final SafeScatterService scatter;
    private final MatchStateMachine lifecycle = new MatchStateMachine();
    private final BingoScoringEngine scoring;
    private final WorkerObjectives objectives;
    private volatile List<BingoTaskSpec> activeTasks;
    private int variationGeneration;
    private final Map<UUID, WorkerObjectives> differentialObjectives = new HashMap<>();
    private final Map<UUID, List<BingoTaskSpec>> differentialTasks = new HashMap<>();
    private final SharedSidebar sidebar;
    private final List<PotionEffect> permanentEffects;
    private final Map<UUID, PlayerSnapshot> participants = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<Integer, TeamSnapshot> teams;
    private final Set<UUID> arrived = new HashSet<>();
    private final Set<UUID> preparedPlayers = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final Set<UUID> bulkScatterPlayers = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final Set<TeamCell> completedTeamCells = ConcurrentHashMap.newKeySet();
    private final Map<Integer, List<Integer>> completionTeamsByCell = new HashMap<>();
    private final Map<Integer, MapView> cardViews = new HashMap<>();
    private final Map<UUID, MapView> playerCardViews = new HashMap<>();
    private MapView spectatorCardView;
    private final Set<Integer> hiddenCells = ConcurrentHashMap.newKeySet();
    private final Set<Integer> lockedCells = ConcurrentHashMap.newKeySet();
    private final Map<Integer, int[]> parallaxOrders = new HashMap<>();
    private long eventSeq;
    private long completionSeq;
    private long startedAtMillis;
    private volatile boolean introductionActive;
    private volatile boolean roundPrepared;
    private ScheduledTask endTask;
    private ScheduledTask timerTask;
    private ScheduledTask heartbeatTask;
    private ScheduledTask spectatorSyncTask;
    private ScheduledTask pvpTask;
    private ScheduledTask remixTask;
    private volatile BossBar timerBossBar;
    private volatile boolean pvpEnabled;
    private volatile Integer winnerTeamId;
    private final AtomicBoolean sidebarRefreshPending = new AtomicBoolean();
    private static final float MIN_SPECTATOR_SPEED = 0.05F;
    private static final float MAX_SPECTATOR_SPEED = 1.0F;
    private static final int SPECTATOR_CARD_TEAM = Integer.MIN_VALUE;

    WorkerMatchSession(Plugin plugin, WorkerConfig config, MatchManifest manifest,
                       DurableEventOutbox events, WorkerReturnRouter returnRouter,
                       WorkerWorldController worlds, Runnable worldReset) {
        this.plugin = plugin;
        this.config = config;
        this.manifest = manifest;
        this.events = events;
        this.returnRouter = returnRouter;
        this.worlds = worlds;
        this.worldReset = worldReset;
        this.scheduler = new PlatformScheduler(plugin);
        this.cardKey = new NamespacedKey(plugin, "bingo_card");
        this.spectatorControlKey = new NamespacedKey(plugin, "spectator_control");
        this.scatter = new SafeScatterService(plugin);
        this.scoring = new BingoScoringEngine(manifest);
        this.objectives = new WorkerObjectives(manifest.tasks());
        this.activeTasks = List.copyOf(manifest.tasks());
        if (manifest.scoring().variant().remix() == BingoRemix.DIFFERENTIAL)
            initDifferentialCards();
        this.sidebar = new SharedSidebar("cc_bingo",
                WorkerPresentationService.component(sidebarField("sidebar.title", "board.title")),
                warning -> plugin.getLogger().warning(warning));
        this.permanentEffects = BingoPermanentEffectService.parse(manifest.runtimeRules().permanentEffects(),
                warning -> plugin.getLogger().warning("Bingo manifest: " + warning));
        this.teams = manifest.teamsById();
        manifest.participants().forEach(player -> participants.put(player.uuid(), player));
        if (manifest.scoring().variant().remix() == BingoRemix.BLIND)
            manifest.tasks().forEach(task -> hiddenCells.add(task.cellIndex()));
        if (manifest.scoring().variant().remix() == BingoRemix.PARALLAX) initParallaxOrders();
    }

    UUID matchId() {
        return manifest.matchId();
    }

    long epoch() {
        return manifest.epoch();
    }

    synchronized MatchState state() {
        return lifecycle.state();
    }

    /** True for championship (EVENT) matches, where voluntary mid-match leaving is disabled. */
    boolean eventMode() {
        return manifest.runMode() == MatchRunMode.EVENT;
    }

    synchronized boolean owns(UUID playerId) {
        return participants.containsKey(playerId) && !lifecycle.state().terminal();
    }

    synchronized boolean isPlaying(UUID playerId) {
        PlayerSnapshot player = participants.get(playerId);
        return player != null && player.role() == ParticipantRole.PLAYER
                && (lifecycle.state() == MatchState.ROUTING || lifecycle.state() == MatchState.COUNTDOWN
                || lifecycle.state() == MatchState.RUNNING);
    }

    synchronized boolean isRunningPlayer(UUID playerId) {
        PlayerSnapshot player = participants.get(playerId);
        return lifecycle.state() == MatchState.RUNNING && player != null
                && player.role() == ParticipantRole.PLAYER;
    }

    String resolveChampionshipPlaceholder(UUID playerId, String params) {
        PlayerSnapshot participant = participants.get(playerId);
        TeamSnapshot team = participant == null || participant.teamId() == null
                ? null : teams.get(participant.teamId());
        return WorkerChampionshipPlaceholderValues.resolve(participant, team,
                manifest.runtimeRules().presentation(), params,
                manifest.runMode() == ink.ziip.championshipscore.protocol.MatchRunMode.DAILY);
    }

    WorkerPlayerPresentation playerPresentation(UUID playerId) {
        PlayerSnapshot participant = participants.get(playerId);
        TeamSnapshot team = participant == null || participant.teamId() == null
                ? null : teams.get(participant.teamId());
        String spectator = manifest.runtimeRules().presentation().messages()
                .getOrDefault("papi.spectator", "旁观");
        String label = team == null
                ? manifest.runMode() == MatchRunMode.DAILY ? "&6" + gameName() : spectator
                : team.colorCode() + team.name();
        boolean activePlayer = participant != null && participant.role() == ParticipantRole.PLAYER && team != null;
        return new WorkerPlayerPresentation(label, team == null ? null : team.colorCode(), activePlayer);
    }

    synchronized boolean canPickupCard(UUID playerId, int teamId) {
        PlayerSnapshot player = participants.get(playerId);
        return player != null && player.role() == ParticipantRole.PLAYER
                && Integer.valueOf(teamId).equals(player.teamId());
    }

    synchronized boolean canPickupSpectatorCard(UUID playerId) {
        PlayerSnapshot player = participants.get(playerId);
        return player != null && player.role() == ParticipantRole.SPECTATOR;
    }

    synchronized boolean canUseBingoUi(UUID playerId) {
        return participants.containsKey(playerId) && !lifecycle.state().terminal();
    }

    synchronized boolean isProtectedParticipant(UUID playerId) {
        PlayerSnapshot player = participants.get(playerId);
        if (player == null || lifecycle.state().terminal()) return false;
        return player.role() == ParticipantRole.SPECTATOR || lifecycle.state() != MatchState.RUNNING;
    }

    synchronized boolean isFinalCountdownPlayer(UUID playerId) {
        PlayerSnapshot player = participants.get(playerId);
        return roundPrepared && lifecycle.state() == MatchState.COUNTDOWN && player != null
                && player.role() == ParticipantRole.PLAYER;
    }

    Location respawnLocation(UUID playerId) {
        PlayerSnapshot snapshot;
        synchronized (this) {
            snapshot = participants.get(playerId);
            if (snapshot == null || lifecycle.state().terminal()) return null;
        }
        if (snapshot.role() == ParticipantRole.SPECTATOR) return spectatorLocation();
        if (!isPlaying(playerId)) return null;
        String respawnWorld = manifest.scoring().variant().remix() == BingoRemix.NETHER
                ? config.nether() : config.overworld();
        World world = plugin.getServer().getWorld(respawnWorld);
        return world == null ? null : world.getSpawnLocation();
    }

    void restoreAfterRespawn(Player player) {
        PlayerSnapshot snapshot;
        synchronized (this) {
            snapshot = participants.get(player.getUniqueId());
            if (snapshot == null || lifecycle.state().terminal()) return;
        }
        restoreParticipantState(player, snapshot);
    }

    CompletionStage<Boolean> prepare() {
        synchronized (this) {
            if (lifecycle.state() == MatchState.READY || lifecycle.state() == MatchState.ROUTING) {
                return CompletableFuture.completedFuture(true);
            }
            if (lifecycle.state() != MatchState.CREATED) return CompletableFuture.completedFuture(false);
            lifecycle.transitionTo(MatchState.PREPARING);
        }
        String scatterWorld = manifest.scoring().variant().remix() == BingoRemix.NETHER
                ? config.nether() : config.overworld();
        World world = plugin.getServer().getWorld(scatterWorld);
        if (world == null || plugin.getServer().getWorld(config.nether()) == null
                || plugin.getServer().getWorld(config.end()) == null) {
            return failPreparation("required-world-not-loaded");
        }
        if (!worlds.freeze()) return failPreparation("unable-to-freeze-worlds");
        if (!manifest.configHash().equals(BingoManifestHasher.hash(manifest))) {
            return failPreparation("manifest-config-hash-mismatch");
        }
        createCardViews(world);
        updateSidebar();
        setPvp(false);
        synchronized (this) {
            lifecycle.transitionTo(MatchState.READY);
        }
        return emit(MatchEventType.READY, Map.of(
                "workerId", config.workerId(),
                "configHash", manifest.configHash())).thenApply(ignored -> true);
    }

    CompletionStage<Boolean> playerArrived(Player player) {
        PlayerSnapshot snapshot;
        MatchState currentState;
        boolean returning;
        synchronized (this) {
            snapshot = participants.get(player.getUniqueId());
            if (snapshot == null || lifecycle.state().terminal()) {
                return CompletableFuture.completedFuture(false);
            }
            if (lifecycle.state() == MatchState.READY) lifecycle.transitionTo(MatchState.ROUTING);
            if (lifecycle.state() != MatchState.ROUTING && lifecycle.state() != MatchState.COUNTDOWN
                    && lifecycle.state() != MatchState.RUNNING) {
                return CompletableFuture.completedFuture(false);
            }
            returning = arrived.contains(player.getUniqueId());
            currentState = lifecycle.state();
            if (returning) {
                scheduler.runEntity(player, () -> restoreParticipantState(player, snapshot));
                return CompletableFuture.completedFuture(true);
            }
        }

        CompletableFuture<Boolean> result = new CompletableFuture<>();
        scheduler.runEntity(player, () -> {
            resetVitals(player);
            sidebar.show(player);
            refreshSidebar(player, snapshot);
            if (roundPrepared) {
                if (snapshot.role() == ParticipantRole.SPECTATOR) {
                    player.getInventory().clear();
                    applySpectatorState(player);
                    teleportAndRecord(player, snapshot, spectatorLocation(), result);
                    return;
                }
                prepareParticipantForRound(player, snapshot, false);
                World world = plugin.getServer().getWorld(config.overworld());
                if (world == null) {
                    result.complete(false);
                    return;
                }
                scatter.performScatterAsync(world, List.of(player), manifest.runtimeRules().scatterRadius(),
                        manifest.runtimeRules().scatterJitter(),
                        manifest.runtimeRules().scatterMaxTries(),
                        () -> recordArrival(snapshot, result, null));
                return;
            }
            player.getInventory().clear();
            if (snapshot.role() == ParticipantRole.SPECTATOR) applySpectatorState(player);
            else applyWaitingParticipantMode(player, currentState == MatchState.COUNTDOWN && introductionActive);
            Location destination = currentState == MatchState.COUNTDOWN && introductionActive
                    ? introductionLocation() : spectatorLocation();
            teleportAndRecord(player, snapshot, destination, result);
        });
        return result;
    }

    private void teleportAndRecord(Player player, PlayerSnapshot snapshot, Location destination,
                                   CompletableFuture<Boolean> result) {
        if (destination == null) {
            result.complete(false);
            return;
        }
        player.teleportAsync(destination).whenComplete((success, error) -> {
            Throwable failure = error;
            if (failure == null && !Boolean.TRUE.equals(success)) {
                failure = new IllegalStateException("Bingo arrival teleport was rejected");
            }
            recordArrival(snapshot, result, failure);
        });
    }

    private void recordArrival(PlayerSnapshot snapshot, CompletableFuture<Boolean> result, Throwable error) {
        if (error != null) {
            result.completeExceptionally(error);
            return;
        }
        synchronized (this) {
            arrived.add(snapshot.uuid());
        }
        // A late spectator or reconnect gets a new Player entity. Reconcile both directions after
        // admission so active players cannot briefly see the spectator's body.
        scheduler.runGlobal(this::reconcileVisibility);
        emit(MatchEventType.PLAYER_ARRIVED, Map.of(
                "playerId", snapshot.uuid().toString(), "role", snapshot.role().name()))
                .whenComplete((ignored, publishError) -> {
                    if (publishError == null) result.complete(true);
                    else result.completeExceptionally(publishError);
                });
    }

    CompletionStage<Boolean> startCommit() {
        synchronized (this) {
            if (lifecycle.state() == MatchState.COUNTDOWN || lifecycle.state() == MatchState.RUNNING) {
                return CompletableFuture.completedFuture(true);
            }
            if (lifecycle.state() != MatchState.ROUTING || !allPlayersArrived()) {
                return CompletableFuture.completedFuture(false);
            }
            lifecycle.transitionTo(MatchState.COUNTDOWN);
        }
        startHeartbeat();
        if (manifest.runtimeRules().showIntroduction()
                && !manifest.runtimeRules().introductionRules().isEmpty()) {
            introductionActive = true;
            forEachOnlineParticipant((player, snapshot) -> {
                if (snapshot.role() == ParticipantRole.PLAYER) applyIntroductionMode(player);
                else applySpectatorState(player);
                Location destination = introductionLocation();
                if (destination != null) player.teleportAsync(destination);
                showTitle(player, message("game.introduction-title", "%game%", gameName()),
                        Component.empty(), 1, 100, 1);
            });
            introductionTick(0);
        } else {
            startFormalPreparation();
        }
        return CompletableFuture.completedFuture(true);
    }

    private synchronized boolean allPlayersArrived() {
        return manifest.participants().stream()
                .filter(player -> player.role() == ParticipantRole.PLAYER && player.requiredAtStart())
                .allMatch(player -> arrived.contains(player.uuid()));
    }

    private void finalCountdown(int remaining) {
        if (remaining <= 0) {
            beginRunning();
            return;
        }
        Component title = message("game.start-countdown-title", "%time%", Integer.toString(remaining));
        Component subtitle = message("game.start-countdown-subtitle", "%game%", gameName());
        forEachOnlinePlayer(player -> {
            showTitle(player, title, subtitle, 1, 20, 1);
            player.playNote(player.getLocation(), Instrument.BIT, Note.natural(0, Note.Tone.C));
        });
        scheduler.runGlobalLater(() -> finalCountdown(remaining - 1), 20L);
    }

    private void introductionTick(int elapsedSeconds) {
        synchronized (this) {
            if (lifecycle.state() != MatchState.COUNTDOWN) return;
        }
        int duration = manifest.runtimeRules().introductionSeconds();
        int section = WorkerPresentationService.sectionAt(elapsedSeconds, duration,
                manifest.runtimeRules().introductionRules().size());
        if (section >= 0) {
            List<String> lines = manifest.runtimeRules().introductionRules().get(section);
            forEachOnlinePlayer(player -> {
                WorkerPresentationService.sendSection(player, lines);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1F, 1F);
            });
        }
        if (elapsedSeconds >= duration) {
            introductionActive = false;
            hideTimerBossBar();
            startFormalPreparation();
            return;
        }
        int remaining = duration - elapsedSeconds;
        Component preparation = message("game.preparation-countdown", "%game%", gameName(),
                "%time%", Integer.toString(remaining));
        updateTimerBossBar(preparation, remaining, duration);
        scheduler.runGlobalLater(() -> introductionTick(elapsedSeconds + 1), 20L);
    }

    private void startFormalPreparation() {
        Component preparationMessage = message("bingo.start-preparation");
        Component preparationTitle = message("bingo.start-preparation-title");
        Component preparationSubtitle = message("bingo.start-preparation-subtitle");
        Location destination = spectatorLocation();
        forEachOnlineParticipant((player, snapshot) -> {
            if (snapshot.role() == ParticipantRole.SPECTATOR) {
                applySpectatorState(player);
            } else {
                player.getInventory().clear();
                clearEffects(player);
                player.setGameMode(GameMode.ADVENTURE);
                player.setInvulnerable(false);
                player.setFlying(false);
                player.setAllowFlight(false);
            }
            resetVitals(player);
            resetExperience(player);
            if (destination != null) player.teleportAsync(destination);
            player.sendMessage(preparationMessage);
            showTitle(player, preparationTitle, preparationSubtitle, 1, 20, 1);
        });
        preparationTick(manifest.runtimeRules().preparationSeconds());
    }

    private void preparationTick(int remaining) {
        synchronized (this) {
            if (lifecycle.state() != MatchState.COUNTDOWN) return;
        }
        if (remaining <= 0) {
            hideTimerBossBar();
            prepareRoundAndScatter();
            return;
        }
        Component preparation = message("game.preparation-countdown", "%game%", gameName(),
                "%time%", Integer.toString(remaining));
        updateTimerBossBar(preparation, remaining, manifest.runtimeRules().preparationSeconds());
        scheduler.runGlobalLater(() -> preparationTick(remaining - 1), 20L);
    }

    private void prepareRoundAndScatter() {
        String scatterWorld = manifest.scoring().variant().remix() == BingoRemix.NETHER
                ? config.nether() : config.overworld();
        World world = plugin.getServer().getWorld(scatterWorld);
        if (world == null) {
            abort("overworld-unavailable-before-scatter");
            return;
        }
        world.setTime(WorkerWorldController.START_TIME);
        roundPrepared = true;
        updateSidebar();
        List<Player> players = new ArrayList<>();
        List<CompletableFuture<Void>> preparations = new ArrayList<>();
        for (PlayerSnapshot snapshot : participants.values()) {
            Player player = plugin.getServer().getPlayer(snapshot.uuid());
            if (player == null) continue;
            if (snapshot.role() == ParticipantRole.SPECTATOR) {
                preparations.add(scheduler.runEntityFuture(player, () -> applySpectatorState(player)));
            } else {
                players.add(player);
                bulkScatterPlayers.add(snapshot.uuid());
                preparations.add(scheduler.runEntityFuture(player,
                        () -> {
                            if (!preparedPlayers.contains(snapshot.uuid())) {
                                prepareParticipantForRound(player, snapshot, false);
                            }
                        }));
            }
        }
        CompletableFuture.allOf(preparations.toArray(CompletableFuture[]::new))
                .whenComplete((ignored, failure) -> scheduler.runGlobal(() -> {
                    if (failure != null) {
                        abort("participant-prepare-failed");
                        return;
                    }
                    scatter.performScatterAsync(world, players, manifest.runtimeRules().scatterRadius(),
                            manifest.runtimeRules().scatterJitter(),
                            manifest.runtimeRules().scatterMaxTries(),
                            () -> scheduler.runGlobal(() -> {
                                bulkScatterPlayers.clear();
                                finalCountdown(manifest.runtimeRules().finalCountdownSeconds());
                            }));
                }));
    }

    private void startHeartbeat() {
        if (heartbeatTask != null) return;
        heartbeatTask = scheduler.runGlobalTimer(() -> emit(MatchEventType.HEARTBEAT,
                Map.of("state", state().name(), "online", Integer.toString(onlineParticipantCount()))), 100L, 100L);
        spectatorSyncTask = scheduler.runGlobalTimer(this::syncSpectators, 20L, 20L);
    }

    /** Re-issues the read-only card set after reconnects or inventory reconciliation by another plugin. */
    private void syncSpectators() {
        if (state().terminal()) return;
        reconcileVisibility();
        forEachOnlineParticipant((player, snapshot) -> {
            if (snapshot.role() == ParticipantRole.SPECTATOR) {
                applySpectatorState(player);
            }
        });
    }

    private void beginRunning() {
        synchronized (this) {
            if (lifecycle.state() != MatchState.COUNTDOWN) return;
        }
        completeBeginRunning();
    }

    private void completeBeginRunning() {
        synchronized (this) {
            if (lifecycle.state() != MatchState.COUNTDOWN) return;
        }
        if (!worlds.startMatch()) {
            abort("worlds-unavailable-before-start");
            return;
        }
        applyVariantWorldRules();
        boolean transitioned;
        synchronized (this) {
            transitioned = lifecycle.state() == MatchState.COUNTDOWN;
            if (transitioned) {
                lifecycle.transitionTo(MatchState.RUNNING);
                startedAtMillis = System.currentTimeMillis();
            }
        }
        if (!transitioned) {
            worlds.freeze();
            return;
        }
        updateSidebar();
        Component startTitle = message("bingo.game-start-title");
        Component startSubtitle = message("bingo.game-start-subtitle");
        forEachOnlinePlayer(player -> {
            player.sendActionBar(message("game.start-action-bar", "%game%", gameName()));
            showTitle(player, startTitle, startSubtitle, 1, 20, 1);
            player.playNote(player.getLocation(), Instrument.BIT, Note.natural(1, Note.Tone.C));
        });
        emit(MatchEventType.STARTED, Map.of());
        endTask = scheduler.runGlobalLater(() -> finish("timer"), manifest.durationSeconds() * 20L);
        timerTask = scheduler.runGlobalTimer(this::updateRunningTimer, 1L, 20L);
        startHeartbeat();
        long pvpDelay = Math.max(1L, manifest.runtimeRules().pvpGraceSeconds() * 20L);
        pvpTask = scheduler.runGlobalLater(() -> {
            setPvp(true);
            pvpEnabled = true;
            forEachOnlinePlayer(player -> player.sendActionBar(message("bingo.pvp-started")));
        }, pvpDelay);
        startTimedRemix();
    }

    void observe(Player player) {
        if (state() != MatchState.RUNNING) return;
        PlayerSnapshot participant = participants.get(player.getUniqueId());
        if (participant == null || participant.role() != ParticipantRole.PLAYER) return;
        BingoPermanentEffectService.ensure(player, permanentEffects);
        int teamId = participant.teamId();
        for (int cellIndex : objectivesFor(player.getUniqueId()).matching(player,
                cellIndex -> canAttempt(teamId, cellIndex))) {
            acceptCompletion(participant, cellIndex);
        }
    }

    void observeAdvancement(Player player, org.bukkit.advancement.Advancement advancement) {
        if (state() != MatchState.RUNNING) return;
        PlayerSnapshot participant = participants.get(player.getUniqueId());
        if (participant == null || participant.role() != ParticipantRole.PLAYER) return;
        for (int cellIndex : objectivesFor(player.getUniqueId()).matchingAdvancement(advancement,
                cellIndex -> canAttempt(participant.teamId(), cellIndex)))
            acceptCompletion(participant, cellIndex);
        objectivesFor(player.getUniqueId()).recordCount(player, "advancement_count");
        observe(player);
    }

    void observeEventSignal(Player player, String trigger, String param) {
        if (state() != MatchState.RUNNING) return;
        PlayerSnapshot participant = participants.get(player.getUniqueId());
        if (participant == null || participant.role() != ParticipantRole.PLAYER) return;
        int teamId = participant.teamId();
        for (int cellIndex : objectivesFor(player.getUniqueId()).matchingEventSignal(player, trigger, param,
                cellIndex -> canAttempt(teamId, cellIndex))) {
            acceptCompletion(participant, cellIndex);
        }
    }

    void recordEventDistinct(Player player, String bucket, String value) {
        if (!isRunningPlayer(player.getUniqueId())) return;
        objectivesFor(player.getUniqueId()).recordDistinct(player, bucket, value);
        observe(player);
    }

    void recordEventCount(Player player, String bucket) {
        if (!isRunningPlayer(player.getUniqueId())) return;
        objectivesFor(player.getUniqueId()).recordCount(player, bucket);
        observe(player);
    }

    private void acceptCompletion(PlayerSnapshot player, int cellIndex) {
        CompletionObservation observation;
        ScoringDecision decision;
        long outgoingEventSeq;
        synchronized (this) {
            if (lifecycle.state() != MatchState.RUNNING) return;
            if (!canAttempt(player.teamId(), cellIndex)) return;
            TeamCell completionKey = new TeamCell(player.teamId(), cellIndex);
            if (!completedTeamCells.add(completionKey)) return;
            observation = new CompletionObservation(manifest.matchId(), manifest.epoch(), ++completionSeq,
                    player.teamId(), player.uuid(), cellIndex, elapsedTicks());
            decision = scoring.apply(observation);
            if (!decision.accepted()) {
                completedTeamCells.remove(completionKey);
                throw new IllegalStateException("Locally validated Bingo completion was rejected: "
                        + decision.rejectionReason());
            }
            completionTeamsByCell.computeIfAbsent(cellIndex, ignored -> new ArrayList<>()).add(player.teamId());
            hiddenCells.remove(cellIndex);
            settleParallax(player.teamId(), cellIndex);
            if (manifest.scoring().variant().remix() == BingoRemix.COOP) {
                for (int teamId : teams.keySet()) {
                    completedTeamCells.add(new TeamCell(teamId, cellIndex));
                    List<Integer> completedTeams = completionTeamsByCell.get(cellIndex);
                    if (!completedTeams.contains(teamId)) completedTeams.add(teamId);
                }
            }
            outgoingEventSeq = ++eventSeq;
        }
        MatchEvent event = MatchMessages.taskCompleted(observation, outgoingEventSeq, Clock.systemUTC());
        events.publishEvent(event).exceptionally(error -> {
            plugin.getLogger().log(Level.SEVERE, "Unable to publish Bingo completion " + event.messageId(), error);
            return null;
        });
        Component completion = message("bingo.task-completed", "%points%",
                Integer.toString(decision.cellPoints() + decision.linePointsPerMember()));
        TeamSnapshot team = teams.get(player.teamId());
        Component playerName = Component.text(player.username(), teamColor(team));
        Component taskName = WorkerMenuService.displayName(taskAt(cellIndex, player.uuid()));
        completion = completion.replaceText(builder -> builder.matchLiteral("%player%").replacement(playerName))
                .replaceText(builder -> builder.matchLiteral("%task%").replacement(taskName));
        Component finalCompletion = completion;
        forEachOnlinePlayer(audience -> audience.sendMessage(finalCompletion));
        requestSidebarUpdate();
        if (scoring.hasWon(player.teamId())) {
            winnerTeamId = player.teamId();
            scheduler.runGlobal(() -> finish("win-condition"));
        } else {
            BingoMode mode = manifest.scoring().variant().mode();
            if ((mode == BingoMode.POINTS || mode == BingoMode.DOMINATION)
                    && scoring.result().boardFullyClaimed())
                scheduler.runGlobal(() -> finish("board-claimed"));
        }
    }

    private synchronized boolean canAttempt(int teamId, int cellIndex) {
        if (lockedCells.contains(cellIndex)) return false;
        if (completedTeamCells.contains(new TeamCell(teamId, cellIndex))) return false;
        if (manifest.scoring().variant().mode().locksCells()
                && completionTeamsByCell.containsKey(cellIndex)) return false;
        if (manifest.scoring().variant().remix() != BingoRemix.CHAIN) return true;
        boolean hasCompletion = completedTeamCells.stream().anyMatch(cell -> cell.teamId() == teamId);
        if (!hasCompletion) return true;
        int width = manifest.scoring().cardWidth();
        int x = cellIndex % width;
        int y = cellIndex / width;
        return (x > 0 && completedTeamCells.contains(new TeamCell(teamId, cellIndex - 1)))
                || (x + 1 < width && completedTeamCells.contains(new TeamCell(teamId, cellIndex + 1)))
                || (y > 0 && completedTeamCells.contains(new TeamCell(teamId, cellIndex - width)))
                || (y + 1 < width && completedTeamCells.contains(new TeamCell(teamId, cellIndex + width)));
    }

    private void applyVariantWorldRules() {
        BingoRemix remix = manifest.scoring().variant().remix();
        boolean night = remix == BingoRemix.ETERNAL_NIGHT;
        boolean day = remix == BingoRemix.POLAR_DAY;
        for (String name : List.of(config.overworld(), config.nether(), config.end())) {
            World world = plugin.getServer().getWorld(name);
            if (world == null) continue;
            world.setGameRule(GameRules.KEEP_INVENTORY,
                    !manifest.scoring().variant().difficulty().clearsInventoryOnDeath());
            if (night || day) {
                world.setDifficulty(night ? org.bukkit.Difficulty.HARD : org.bukkit.Difficulty.EASY);
                world.setGameRule(GameRules.ADVANCE_TIME, false);
                world.setTime(night ? 18000L : 0L);
            } else {
                world.setDifficulty(org.bukkit.Difficulty.NORMAL);
                world.setGameRule(GameRules.ADVANCE_TIME, true);
            }
        }
    }

    private void startTimedRemix() {
        switch (manifest.scoring().variant().remix()) {
            case BLIND -> remixTask = scheduler.runGlobalLater(this::revealBlindCell, 3L * 60L * 20L);
            case FINALE -> remixTask = scheduler.runGlobalLater(this::lockFinaleCell, 5L * 60L * 20L);
            case VARIATION -> remixTask = scheduler.runGlobalLater(this::varyOpenCells, 4L * 60L * 20L);
            default -> { }
        }
    }

    private void revealBlindCell() {
        if (state() != MatchState.RUNNING || hiddenCells.isEmpty()) return;
        List<Integer> candidates = new ArrayList<>(hiddenCells);
        int cell = candidates.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(candidates.size()));
        hiddenCells.remove(cell);
        forEachOnlinePlayer(player -> player.sendMessage(Component.text("视障奇遇揭示了一项任务。",
                NamedTextColor.LIGHT_PURPLE)));
        remixTask = scheduler.runGlobalLater(this::revealBlindCell, 60L * 20L);
    }

    private void lockFinaleCell() {
        if (state() != MatchState.RUNNING) return;
        List<Integer> candidates = manifest.tasks().stream().map(BingoTaskSpec::cellIndex)
                .filter(cell -> !lockedCells.contains(cell))
                .filter(cell -> teams.keySet().stream().noneMatch(team ->
                        completedTeamCells.contains(new TeamCell(team, cell))))
                .toList();
        if (candidates.isEmpty()) {
            scheduler.runGlobal(() -> finish("finale-exhausted"));
            return;
        }
        int cell = candidates.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(candidates.size()));
        lockedCells.add(cell);
        forEachOnlinePlayer(player -> player.sendMessage(Component.text("终曲奇遇封锁了一项任务。",
                NamedTextColor.LIGHT_PURPLE)));
        remixTask = scheduler.runGlobalLater(this::lockFinaleCell, 60L * 20L);
    }

    private void varyOpenCells() {
        if (state() != MatchState.RUNNING) return;
        List<Integer> open = activeTasks.stream().map(BingoTaskSpec::cellIndex)
                .filter(cell -> teams.keySet().stream().noneMatch(team ->
                        completedTeamCells.contains(new TeamCell(team, cell))))
                .sorted().toList();
        if (open.size() > 1) {
            Map<Integer, BingoTaskSpec> byCell = new HashMap<>();
            activeTasks.forEach(task -> byCell.put(task.cellIndex(), task));
            Map<Integer, BingoTaskSpec> changed = new HashMap<>(byCell);
            variationGeneration++;
            for (int index = 0; index < open.size(); index++) {
                int targetCell = open.get(index);
                BingoTaskSpec source = byCell.get(open.get((index + 1) % open.size()));
                changed.put(targetCell, new BingoTaskSpec(targetCell,
                        source.taskId() + "#v" + variationGeneration,
                        source.taskType(), source.attributes()));
            }
            activeTasks = changed.values().stream()
                    .sorted(java.util.Comparator.comparingInt(BingoTaskSpec::cellIndex)).toList();
            objectives.replace(activeTasks);
            forEachOnlineParticipant((player, snapshot) -> {
                if (snapshot.role() == ParticipantRole.PLAYER) objectives.captureBaselines(player);
            });
            forEachOnlinePlayer(player -> player.sendMessage(Component.text(
                    "变奏奇遇刷新了所有未完成任务。", NamedTextColor.LIGHT_PURPLE)));
        }
        remixTask = scheduler.runGlobalLater(this::varyOpenCells, 4L * 60L * 20L);
    }

    private void initParallaxOrders() {
        int cells = manifest.tasks().size();
        for (int teamId : teams.keySet()) {
            int[] order = new int[cells];
            for (int index = 0; index < cells; index++) order[index] = index;
            java.util.Random random = new java.util.Random(manifest.cardSeed() + teamId * 7919L);
            for (int index = cells - 1; index > 0; index--) {
                int swap = random.nextInt(index + 1);
                int value = order[index]; order[index] = order[swap]; order[swap] = value;
            }
            parallaxOrders.put(teamId, order);
        }
    }

    private void initDifferentialCards() {
        for (PlayerSnapshot participant : manifest.participants()) {
            if (participant.role() != ParticipantRole.PLAYER) continue;
            List<BingoTaskSpec> shuffled = new ArrayList<>(manifest.tasks());
            java.util.Collections.shuffle(shuffled,
                    new java.util.Random(manifest.cardSeed() + participant.uuid().hashCode() * 1_000_003L));
            List<BingoTaskSpec> playerTasks = new ArrayList<>(shuffled.size());
            for (int cell = 0; cell < shuffled.size(); cell++) {
                BingoTaskSpec source = shuffled.get(cell);
                playerTasks.add(new BingoTaskSpec(cell, source.taskId() + "@" + cell,
                        source.taskType(), source.attributes()));
            }
            List<BingoTaskSpec> frozen = List.copyOf(playerTasks);
            differentialTasks.put(participant.uuid(), frozen);
            differentialObjectives.put(participant.uuid(), new WorkerObjectives(frozen));
        }
    }

    private WorkerObjectives objectivesFor(UUID playerId) {
        return differentialObjectives.getOrDefault(playerId, objectives);
    }

    private void settleParallax(int teamId, int trueIndex) {
        int[] order = parallaxOrders.get(teamId);
        if (order == null) return;
        int displayedAt = -1;
        for (int index = 0; index < order.length; index++)
            if (order[index] == trueIndex) { displayedAt = index; break; }
        if (displayedAt < 0 || displayedAt == trueIndex) return;
        int displaced = order[trueIndex];
        order[trueIndex] = trueIndex;
        order[displayedAt] = displaced;
    }

    List<BingoTaskSpec> tasksSnapshot() { return activeTasks; }
    List<BingoTaskSpec> tasksSnapshot(UUID playerId) {
        return differentialTasks.getOrDefault(playerId, activeTasks);
    }
    boolean cellHidden(int cell) { return hiddenCells.contains(cell); }
    boolean cellLocked(int cell) { return lockedCells.contains(cell); }
    Set<Integer> hiddenSnapshot() { return Set.copyOf(hiddenCells); }
    Set<Integer> lockedSnapshot() { return Set.copyOf(lockedCells); }
    int[] displayOrder(int teamId) {
        int[] order = parallaxOrders.get(teamId);
        return order == null ? null : order.clone();
    }

    boolean clearsInventoryOnDeath(UUID playerId) {
        return isRunningPlayer(playerId)
                && manifest.scoring().variant().difficulty().clearsInventoryOnDeath();
    }

    CompletionStage<Boolean> finish(String reason) {
        BingoResult result;
        synchronized (this) {
            if (lifecycle.state() == MatchState.FINISHED || lifecycle.state() == MatchState.ABORTED) {
                return CompletableFuture.completedFuture(true);
            }
            if (lifecycle.state() != MatchState.RUNNING) return CompletableFuture.completedFuture(false);
            lifecycle.transitionTo(MatchState.SETTLING);
            result = scoring.result();
        }
        cancelTasks();
        worlds.freeze();
        setPvp(true);
        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put("reason", reason);
        attributes.put("resultHash", result.resultHash());
        attributes.put("lastCompletionSeq", Long.toString(result.finalSeq()));
        attributes.put("teamScores", result.teamScores().toString());
        CompletionStage<MatchEvent> published = emit(MatchEventType.FINISHED, attributes);
        synchronized (this) {
            lifecycle.transitionTo(MatchState.FINISHED);
        }
        TeamSnapshot winner = winnerTeamId == null ? resolveWinner(result) : teams.get(winnerTeamId);
        winnerTeamId = winner == null ? null : winner.id();
        updateSidebar();
        Component winnerMessage = winner == null ? null
                : message("bingo.game-winner", "%points%",
                Integer.toString(result.teamScores().getOrDefault(winner.id(), 0)))
                .replaceText(builder -> builder.matchLiteral("%team%")
                        .replacement(Component.text(winner.name(), teamColor(winner))));
        Component endTitle = message("game.completion-title");
        Component endSubtitle = message("bingo.game-end-subtitle");
        // Spectators are part of the frozen participant roster, so they receive the same authoritative
        // winner/chat/title result as players before the return route starts.
        forEachOnlineParticipant((player, snapshot) -> {
            if (winnerMessage != null) player.sendMessage(winnerMessage);
            player.sendActionBar(message("game.end-action-bar", "%game%", gameName()));
            showTitle(player, endTitle, endSubtitle, 1, 20, 1);
        });
        // Match Core's post-game result display window; otherwise the proxy transfer hides the
        // winner/end title in the same tick it is sent.
        scheduler.runGlobalLater(this::routeEveryoneBack, 200L);
        return published.thenApply(ignored -> true);
    }

    CompletionStage<Boolean> abort(String reason) {
        synchronized (this) {
            if (lifecycle.state().terminal()) return CompletableFuture.completedFuture(true);
            lifecycle.transitionTo(MatchState.ABORTED);
        }
        cancelTasks();
        worlds.freeze();
        setPvp(true);
        CompletionStage<MatchEvent> published = emit(MatchEventType.ABORTED, Map.of("reason", reason));
        routeEveryoneBack();
        return published.thenApply(ignored -> true);
    }

    void playerLeft(Player player) {
        if (!owns(player.getUniqueId())) return;
        sidebar.hide(player);
        scheduler.runGlobal(this::reconcileVisibility);
        emit(MatchEventType.PLAYER_LEFT, Map.of("playerId", player.getUniqueId().toString()));
    }

    void requestVoluntaryLeave(Player player) {
        if (!isPlaying(player.getUniqueId())) return;
        player.sendMessage(Component.text("正在离开自由游玩…", NamedTextColor.YELLOW));
        emit(MatchEventType.PLAYER_LEFT, Map.of(
                "playerId", player.getUniqueId().toString(), "intent", "voluntary"));
    }

    CompletionStage<Boolean> removeParticipants(Set<UUID> playerIds) {
        if (playerIds.isEmpty()) return CompletableFuture.completedFuture(true);
        synchronized (this) {
            for (UUID playerId : playerIds) {
                PlayerSnapshot participant = participants.get(playerId);
                if (participant == null || participant.role() != ParticipantRole.PLAYER) continue;
                participants.remove(playerId);
                arrived.remove(playerId);
                preparedPlayers.remove(playerId);
                bulkScatterPlayers.remove(playerId);
            }
        }
        playerIds.forEach(this::releaseVisibilityForTarget);
        for (UUID playerId : playerIds) {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player == null) continue;
            scheduler.runEntity(player, () -> {
                sidebar.hide(player);
                resetVitals(player);
                player.getInventory().clear();
                returnRouter.request(player);
            });
        }
        boolean empty;
        MatchState current;
        synchronized (this) {
            empty = participants.values().stream().noneMatch(
                    participant -> participant.role() == ParticipantRole.PLAYER);
            current = lifecycle.state();
        }
        if (!empty) {
            reconcileVisibility();
            updateSidebar();
            return CompletableFuture.completedFuture(true);
        }
        return current == MatchState.RUNNING
                ? finish("all-players-left")
                : abort("all-players-left");
    }

    CompletionStage<Boolean> addSpectator(UUID playerId, String username, double points) {
        synchronized (this) {
            if (lifecycle.state().terminal()) return CompletableFuture.completedFuture(false);
            PlayerSnapshot existing = participants.get(playerId);
            if (existing != null && existing.role() == ParticipantRole.PLAYER) {
                return CompletableFuture.completedFuture(false);
            }
            participants.put(playerId, new PlayerSnapshot(playerId, username,
                    ParticipantRole.SPECTATOR, null, false, points));
        }
        updateSidebar();
        reconcileVisibility();
        return emit(MatchEventType.SPECTATOR_ADDED, Map.of("playerId", playerId.toString()))
                .thenApply(ignored -> true);
    }

    void openCard(Player player, Integer selectedTeamId) {
        PlayerSnapshot snapshot;
        Set<Integer> own = new HashSet<>();
        Map<Integer, List<Integer>> all = new HashMap<>();
        synchronized (this) {
            snapshot = participants.get(player.getUniqueId());
            if (snapshot == null || lifecycle.state().terminal()) return;
            boolean aggregate = snapshot.role() == ParticipantRole.SPECTATOR
                    && Integer.valueOf(SPECTATOR_CARD_TEAM).equals(selectedTeamId);
            int viewedTeam = snapshot.role() == ParticipantRole.PLAYER
                    ? snapshot.teamId() : selectedTeamId == null ? firstTeamId() : selectedTeamId;
            if (!aggregate && !teams.containsKey(viewedTeam)) return;
            if (!aggregate && viewedTeam >= 0) {
                for (TeamCell completed : completedTeamCells) {
                    if (completed.teamId() == viewedTeam) own.add(completed.cellIndex());
                }
            }
            completionTeamsByCell.forEach((cell, teams) -> all.put(cell, List.copyOf(teams)));
        }
        boolean aggregate = snapshot.role() == ParticipantRole.SPECTATOR
                && Integer.valueOf(SPECTATOR_CARD_TEAM).equals(selectedTeamId);
        int viewedTeam = snapshot.role() == ParticipantRole.PLAYER
                ? snapshot.teamId() : selectedTeamId == null ? firstTeamId() : selectedTeamId;
        List<BingoTaskSpec> viewedTasks = snapshot.role() == ParticipantRole.PLAYER || !aggregate
                ? tasksSnapshot(snapshot.uuid()) : tasksSnapshot();
        WorkerMenuService.openCard(player, manifest, viewedTasks, Set.copyOf(own), Map.copyOf(all),
                hiddenSnapshot(), lockedSnapshot(), aggregate ? null : displayOrder(viewedTeam), aggregate);
    }

    boolean isSpectatorCard(ItemStack item) {
        if (item == null || item.getType() != Material.FILLED_MAP || item.getItemMeta() == null) return false;
        String value = item.getItemMeta().getPersistentDataContainer().get(cardKey, PersistentDataType.STRING);
        return (manifest.matchId() + ":spectator").equals(value);
    }

    Integer boundCardTeam(ItemStack item) {
        if (item == null || item.getType() != Material.FILLED_MAP) return null;
        String value = item.getItemMeta().getPersistentDataContainer().get(
                cardKey, PersistentDataType.STRING);
        if (value == null) return null;
        String prefix = manifest.matchId() + ":";
        if (!value.startsWith(prefix)) return null;
        if (value.equals(prefix + "spectator")) return SPECTATOR_CARD_TEAM;
        try {
            int teamId = Integer.parseInt(value.substring(prefix.length()));
            return teams.containsKey(teamId) ? teamId : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    void openTeammates(Player player) {
        PlayerSnapshot snapshot;
        synchronized (this) {
            snapshot = participants.get(player.getUniqueId());
            if (snapshot == null || snapshot.teamId() == null || lifecycle.state().terminal()) return;
        }
        TeamSnapshot team = teams.get(snapshot.teamId());
        if (team != null) WorkerMenuService.openTeammates(player, manifest, team, manifest.participants());
    }

    void openSpectatorTargets(Player player) {
        PlayerSnapshot snapshot = participants.get(player.getUniqueId());
        if (snapshot == null || snapshot.role() != ParticipantRole.SPECTATOR || lifecycle.state().terminal()) return;
        WorkerMenuService.openSpectatorTargets(player, manifest, manifest.participants());
    }

    void teleportToSpectatorTarget(Player player, UUID targetId) {
        PlayerSnapshot source = participants.get(player.getUniqueId());
        if (source == null || source.role() != ParticipantRole.SPECTATOR || lifecycle.state().terminal()) return;
        PlayerSnapshot snapshot = participants.get(targetId);
        if (snapshot == null || snapshot.role() != ParticipantRole.PLAYER) return;
        Player target = Bukkit.getPlayer(targetId);
        if (target == null || !target.isOnline()) return;
        scheduler.runEntity(player, () -> player.teleportAsync(target.getLocation()));
    }

    void teleportToTeammate(Player player, UUID targetId) {
        PlayerSnapshot source;
        PlayerSnapshot target;
        synchronized (this) {
            if (lifecycle.state() != MatchState.RUNNING) return;
            source = participants.get(player.getUniqueId());
            target = participants.get(targetId);
            if (source == null || target == null || source.teamId() == null
                    || !source.teamId().equals(target.teamId())) return;
        }
        Player onlineTarget = plugin.getServer().getPlayer(targetId);
        if (onlineTarget == null) {
            player.sendMessage(message("compass.target_offline"));
            return;
        }
        scheduler.supplyEntity(onlineTarget, onlineTarget::getLocation).thenAccept(location -> {
            if (location == null) return;
            scheduler.runEntity(player, () -> player.teleportAsync(location).thenAccept(success -> {
                if (!success) return;
                Component confirmation = message("compass.teleport_success")
                        .replaceText(builder -> builder.matchLiteral("{0}")
                                .replacement(Component.text(target.username(), teamColor(teams.get(target.teamId())))));
                scheduler.runEntity(player, () -> player.sendMessage(confirmation));
            }));
        });
    }

    synchronized Map<Integer, List<Integer>> completionSnapshot() {
        Map<Integer, List<Integer>> snapshot = new HashMap<>();
        completionTeamsByCell.forEach((cell, completedTeams) ->
                snapshot.put(cell, List.copyOf(completedTeams)));
        return Map.copyOf(snapshot);
    }

    Integer winnerTeamId() {
        return winnerTeamId;
    }

    private void createCardViews(World world) {
        if (!cardViews.isEmpty()) return;
        TaskImageAtlas.ensureLoaded();
        for (TeamSnapshot team : manifest.teams()) {
            MapView view = Bukkit.createMap(world);
            view.setScale(MapView.Scale.NORMAL);
            view.setTrackingPosition(false);
            view.setUnlimitedTracking(false);
            for (MapRenderer renderer : new ArrayList<>(view.getRenderers())) view.removeRenderer(renderer);
            view.addRenderer(new WorkerCardMapRenderer(manifest, team.id(), this));
            cardViews.put(team.id(), view);
        }
        spectatorCardView = Bukkit.createMap(world);
        spectatorCardView.setScale(MapView.Scale.NORMAL);
        spectatorCardView.setTrackingPosition(false);
        spectatorCardView.setUnlimitedTracking(false);
        for (MapRenderer renderer : new ArrayList<>(spectatorCardView.getRenderers())) spectatorCardView.removeRenderer(renderer);
        spectatorCardView.addRenderer(new WorkerCardMapRenderer(manifest, this));
        if (!differentialTasks.isEmpty()) {
            for (PlayerSnapshot participant : manifest.participants()) {
                if (participant.role() != ParticipantRole.PLAYER) continue;
                MapView view = Bukkit.createMap(world);
                view.setScale(MapView.Scale.NORMAL);
                view.setTrackingPosition(false);
                view.setUnlimitedTracking(false);
                for (MapRenderer renderer : new ArrayList<>(view.getRenderers())) view.removeRenderer(renderer);
                view.addRenderer(new WorkerCardMapRenderer(manifest, participant.teamId(),
                        participant.uuid(), this));
                playerCardViews.put(participant.uuid(), view);
            }
        }
    }

    private int firstTeamId() {
        return manifest.teams().isEmpty() ? -1 : manifest.teams().getFirst().id();
    }

    private void ensureCard(Player player, int teamId) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (Integer.valueOf(teamId).equals(boundCardTeam(item))) return;
        }
        ItemStack card = cardItem(teamId, player.getUniqueId());
        if (card == null) return;
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (offhand == null || offhand.getType().isAir()) player.getInventory().setItemInOffHand(card);
        else if (!player.getInventory().addItem(card).isEmpty())
            player.getWorld().dropItemNaturally(player.getLocation(), card);
    }

    private ItemStack cardItem(int teamId) {
        return cardItem(teamId, null);
    }

    private ItemStack cardItem(int teamId, UUID playerId) {
        MapView view = playerId == null ? cardViews.get(teamId)
                : playerCardViews.getOrDefault(playerId, cardViews.get(teamId));
        if (view == null) return null;
        ItemStack card = new ItemStack(Material.FILLED_MAP);
        card.editMeta(MapMeta.class, meta -> {
            meta.setMapView(view);
            TeamSnapshot team = teams.get(teamId);
            meta.displayName(message("card.map_name", "{0}", team == null ? "" : team.name())
                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
            meta.lore(List.of(message("card.map_hint")
                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)));
            meta.getPersistentDataContainer().set(cardKey, PersistentDataType.STRING,
                    manifest.matchId() + ":" + teamId);
        });
        return card;
    }

    private ItemStack spectatorCardItem() {
        if (spectatorCardView == null) return null;
        ItemStack card = new ItemStack(Material.FILLED_MAP);
        card.editMeta(MapMeta.class, meta -> {
            meta.setMapView(spectatorCardView);
            meta.displayName(message("card.spectator_map_name")
                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
            meta.lore(List.of(message("card.map_hint")
                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)));
            meta.getPersistentDataContainer().set(cardKey, PersistentDataType.STRING,
                    manifest.matchId() + ":spectator");
        });
        return card;
    }

    private void ensureSpectatorCards(Player player) {
        removeBoundCards(player);
        ItemStack card = spectatorCardItem();
        if (card != null && !player.getInventory().addItem(card).isEmpty())
            player.getWorld().dropItemNaturally(player.getLocation(), card);
        player.updateInventory();
    }

    private void removeBoundCards(Player player) {
        if (boundCardTeam(player.getInventory().getItemInOffHand()) != null) {
            player.getInventory().setItemInOffHand(null);
        }
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            if (boundCardTeam(player.getInventory().getItem(slot)) != null) {
                player.getInventory().setItem(slot, null);
            }
        }
    }

    private void restoreParticipantState(Player player, PlayerSnapshot snapshot) {
        sidebar.show(player);
        refreshSidebar(player, snapshot);
        if (timerBossBar != null) player.showBossBar(timerBossBar);
        if (introductionActive) {
            if (snapshot.role() == ParticipantRole.SPECTATOR) applySpectatorState(player);
            else applyIntroductionMode(player);
            return;
        }
        if (snapshot.role() == ParticipantRole.SPECTATOR) {
            applySpectatorState(player);
            return;
        }
        if (!roundPrepared) {
            applyWaitingParticipantMode(player, false);
            return;
        }
        if (preparedPlayers.contains(snapshot.uuid())) {
            prepareParticipantForRound(player, snapshot, true);
            return;
        }
        prepareParticipantForRound(player, snapshot, false);
        if (bulkScatterPlayers.contains(snapshot.uuid())) return;
        World world = plugin.getServer().getWorld(config.overworld());
        if (world != null) {
            scatter.performScatterAsync(world, List.of(player), manifest.runtimeRules().scatterRadius(),
                    manifest.runtimeRules().scatterJitter(),
                    manifest.runtimeRules().scatterMaxTries(), null);
        }
    }

    private void prepareParticipantForRound(Player player, PlayerSnapshot snapshot, boolean preserveInventory) {
        TeamSnapshot team = teams.get(snapshot.teamId());
        if (team == null) return;
        BingoSpectatorService.clear(player);
        if (!preserveInventory) {
            player.getInventory().clear();
            clearEffects(player);
            resetExperience(player);
        }
        player.setGameMode(GameMode.SURVIVAL);
        player.setInvulnerable(false);
        player.setAllowFlight(false);
        player.setFlying(false);
        resetVitals(player);
        if (!BingoStarterKitService.hasKit(player)) {
            WorkerStarterKit.give(player, team, manifest.runtimeRules().presentation(),
                    manifest.scoring().variant().remix());
        }
        ensureCard(player, team.id());
        BingoPermanentEffectService.ensure(player, permanentEffects);
        if (!preserveInventory) {
            objectivesFor(snapshot.uuid()).prepareParticipant(player);
            preparedPlayers.add(snapshot.uuid());
        }
    }

    private void applyWaitingParticipantMode(Player player, boolean introduction) {
        BingoSpectatorService.clear(player);
        if (introduction) {
            applyIntroductionMode(player);
            return;
        }
        player.setGameMode(GameMode.ADVENTURE);
        player.setInvulnerable(false);
        player.setFlying(false);
        player.setAllowFlight(false);
    }

    private void applyIntroductionMode(Player player) {
        BingoSpectatorService.clear(player);
        GameMode mode = manifest.runtimeRules().introductionMode() == BingoIntroductionMode.SPECTATOR
                ? GameMode.SPECTATOR : GameMode.ADVENTURE;
        player.setGameMode(mode);
        player.setInvulnerable(false);
        if (mode == GameMode.ADVENTURE) {
            player.setFlying(false);
            player.setAllowFlight(false);
        }
    }

    private void applySpectatorState(Player player) {
        PlayerSnapshot snapshot = participants.get(player.getUniqueId());
        if (snapshot != null) {
            // Spectators may be admitted after the regular arrival pass. Always create and render
            // their private sidebar here, even when the periodic match refresh has not run yet.
            sidebar.show(player);
            refreshSidebar(player, snapshot);
        }
        BingoSpectatorService.apply(player);
        applySpectatorControls(player);
        if (roundPrepared) ensureSpectatorCards(player);
    }

    boolean handleSpectatorControl(Player player, ItemStack control, boolean rightClick) {
        PlayerSnapshot snapshot = participants.get(player.getUniqueId());
        if (snapshot == null || snapshot.role() != ParticipantRole.SPECTATOR || control == null) return false;
        ItemMeta meta = control.getItemMeta();
        if (meta == null) return false;
        String action = meta.getPersistentDataContainer().get(
                spectatorControlKey, PersistentDataType.STRING);
        if (action == null) return false;
        if (action.equals("teleport")) {
            if (rightClick) openSpectatorTargets(player);
            return true;
        }
        if (action.equals("speed")) {
            float delta = rightClick ? -.05F : .05F;
            player.setFlySpeed(Math.max(MIN_SPECTATOR_SPEED,
                    Math.min(MAX_SPECTATOR_SPEED, player.getFlySpeed() + delta)));
            applySpectatorControls(player);
            spectatorFeedback(player, message("spectator.speed.feedback",
                            "%speed%", spectatorSpeed(player)), NamedTextColor.YELLOW,
                    delta > 0 ? 1.25F : 0.8F);
            return true;
        }
        return false;
    }

    private void applySpectatorControls(Player player) {
        // Slot 1 is reserved for the Bingo card. Keep all spectator controls elsewhere.
        player.getInventory().setItem(0, spectatorControl(Material.COMPASS, "teleport",
                message("spectator.teleport.name").color(NamedTextColor.GREEN)
                        .decorate(TextDecoration.BOLD),
                List.of(message("spectator.teleport.hint").color(NamedTextColor.GRAY))));
        player.getInventory().setItem(2, null);
        player.getInventory().setItem(3, null);
        player.getInventory().setItem(7, spectatorControl(Material.FEATHER, "speed",
                message("spectator.speed.name", "%speed%", spectatorSpeed(player))
                        .color(NamedTextColor.YELLOW)
                        .decorate(TextDecoration.BOLD),
                List.of(message("spectator.speed.faster").color(NamedTextColor.GREEN),
                        message("spectator.speed.slower").color(NamedTextColor.RED))));
    }

    private ItemStack spectatorControl(Material material, String action, Component name,
                                       List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        meta.lore(lore.stream().map(line -> line.decoration(TextDecoration.ITALIC, false)).toList());
        meta.getPersistentDataContainer().set(spectatorControlKey,
                PersistentDataType.STRING, action);
        item.setItemMeta(meta);
        return item;
    }

    private static String spectatorSpeed(Player player) {
        return Math.round(player.getFlySpeed() * 100F) + "%";
    }

    private static void spectatorFeedback(Player player, Component message, NamedTextColor color, float pitch) {
        player.sendActionBar(message.color(color).decorate(TextDecoration.BOLD));
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7F, pitch);
    }

    private void resetVitals(Player player) {
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        player.setHealth(Math.min(20.0, maxHealth == null ? player.getHealth() : maxHealth.getValue()));
        player.setFoodLevel(20);
        player.setFireTicks(0);
        player.setFallDistance(0F);
    }

    private static void resetExperience(Player player) {
        player.setExp(0F);
        player.setLevel(0);
        player.setTotalExperience(0);
    }

    private static void clearEffects(Player player) {
        for (PotionEffect effect : player.getActivePotionEffects()) player.removePotionEffect(effect.getType());
    }

    CompletionStage<Boolean> removeSpectator(UUID playerId) {
        synchronized (this) {
            PlayerSnapshot existing = participants.get(playerId);
            if (existing == null || existing.role() != ParticipantRole.SPECTATOR) {
                return CompletableFuture.completedFuture(true);
            }
            participants.remove(playerId);
            arrived.remove(playerId);
        }
        releaseVisibilityForTarget(playerId);
        updateSidebar();
        Player player = plugin.getServer().getPlayer(playerId);
        if (player != null) {
            scheduler.runEntity(player, () -> {
                sidebar.hide(player);
                player.getInventory().clear();
                BingoSpectatorService.clear(player);
                clearEffects(player);
                player.setGameMode(GameMode.ADVENTURE);
            });
            returnRouter.request(player);
        }
        return emit(MatchEventType.SPECTATOR_REMOVED, Map.of("playerId", playerId.toString()))
                .thenApply(ignored -> true);
    }

    private CompletionStage<Boolean> failPreparation(String reason) {
        synchronized (this) {
            lifecycle.transitionTo(MatchState.ABORTED);
        }
        clearVisibility();
        worlds.freeze();
        CompletionStage<MatchEvent> published = emit(MatchEventType.PREPARE_FAILED, Map.of("reason", reason));
        worldReset.run();
        return published.thenApply(ignored -> false);
    }

    CompletionStage<Boolean> rejectPreparation(String reason) {
        synchronized (this) {
            if (lifecycle.state() != MatchState.CREATED) return CompletableFuture.completedFuture(false);
            lifecycle.transitionTo(MatchState.PREPARING);
        }
        return failPreparation(reason);
    }

    private CompletionStage<MatchEvent> emit(MatchEventType type, Map<String, String> attributes) {
        MatchEvent event;
        synchronized (this) {
            event = MatchMessages.event(manifest.matchId(), manifest.epoch(), ++eventSeq,
                    type, attributes, Clock.systemUTC());
        }
        return events.publishEvent(event).thenApply(ignored -> event);
    }

    private void routeEveryoneBack() {
        clearVisibility();
        List<PlayerSnapshot> routingParticipants;
        synchronized (this) {
            routingParticipants = List.copyOf(participants.values());
        }
        for (PlayerSnapshot participant : routingParticipants) {
            Player player = plugin.getServer().getPlayer(participant.uuid());
            if (player != null) {
                scheduler.runEntity(player, () -> {
                    sidebar.hide(player);
                    player.getInventory().clear();
                    BingoSpectatorService.clear(player);
                    clearEffects(player);
                    player.setGameMode(GameMode.ADVENTURE);
                });
            }
            returnRouter.request(participant.uuid());
        }
        worldReset.run();
    }

    private void cancelTasks() {
        if (endTask != null) endTask.cancel();
        if (timerTask != null) timerTask.cancel();
        if (heartbeatTask != null) heartbeatTask.cancel();
        if (pvpTask != null) pvpTask.cancel();
        if (remixTask != null) remixTask.cancel();
        if (spectatorSyncTask != null) spectatorSyncTask.cancel();
        spectatorSyncTask = null;
        remixTask = null;
        hideTimerBossBar();
    }

    /**
     * Mirrors Core's visibility contract on the worker connection. Participants see one another,
     * active participants do not see spectators, and spectators see every participant.
     *
     * <p>Folia requires the viewer's entity scheduler for these packet-backed operations, so the
     * global match coordinator only resolves the roster and schedules the actual calls.</p>
     */
    private void reconcileVisibility() {
        if (state().terminal()) return;
        List<Map.Entry<Player, PlayerSnapshot>> online = new ArrayList<>();
        for (PlayerSnapshot snapshot : participants.values()) {
            Player player = plugin.getServer().getPlayer(snapshot.uuid());
            if (player != null && player.isOnline()) online.add(Map.entry(player, snapshot));
        }
        for (Map.Entry<Player, PlayerSnapshot> viewerEntry : online) {
            Player viewer = viewerEntry.getKey();
            boolean viewerIsSpectator = viewerEntry.getValue().role() == ParticipantRole.SPECTATOR;
            for (Map.Entry<Player, PlayerSnapshot> targetEntry : online) {
                Player target = targetEntry.getKey();
                if (viewer.equals(target)) continue;
                boolean targetIsSpectator = targetEntry.getValue().role() == ParticipantRole.SPECTATOR;
                boolean visible = viewerIsSpectator || !targetIsSpectator;
                scheduler.runEntity(viewer, () -> {
                    if (visible) viewer.showEntity(plugin, target);
                    else viewer.hideEntity(plugin, target);
                });
            }
        }
    }

    /** Releases a removed participant's target from every remaining viewer before routing it away. */
    private void releaseVisibilityForTarget(UUID targetId) {
        Player target = plugin.getServer().getPlayer(targetId);
        if (target == null) return;
        for (UUID viewerId : participants.keySet()) {
            if (viewerId.equals(targetId)) continue;
            Player viewer = plugin.getServer().getPlayer(viewerId);
            if (viewer != null) scheduler.runEntity(viewer, () -> viewer.showEntity(plugin, target));
        }
    }

    /** Removes this match's hides before players are handed back to Core. */
    private void clearVisibility() {
        List<Player> online = new ArrayList<>();
        for (PlayerSnapshot snapshot : participants.values()) {
            Player player = plugin.getServer().getPlayer(snapshot.uuid());
            if (player != null && player.isOnline()) online.add(player);
        }
        for (Player viewer : online) {
            for (Player target : online) {
                if (!viewer.equals(target)) scheduler.runEntity(viewer, () -> viewer.showEntity(plugin, target));
            }
        }
    }

    private void updateRunningTimer() {
        int remaining = Math.max(0, manifest.durationSeconds()
                - (int) ((System.currentTimeMillis() - startedAtMillis) / 1000L));
        int graceRemaining = Math.max(0, manifest.runtimeRules().pvpGraceSeconds()
                - (manifest.durationSeconds() - remaining));
        Component title = message("bingo.timer", "%time%", Integer.toString(remaining)).append(
                WorkerPresentationService.component(" &#bababa• ")).append(pvpEnabled
                ? message("bingo.pvp-active")
                : message("bingo.pvp-protection", "%time%", Integer.toString(graceRemaining)));
        updateTimerBossBar(title, remaining, manifest.durationSeconds());
        if (!pvpEnabled && graceRemaining >= 1 && graceRemaining <= 10) {
            Component warning = message("bingo.pvp-countdown", "%time%", Integer.toString(graceRemaining));
            forEachOnlinePlayer(player -> player.sendActionBar(warning));
        }
    }

    private void updateTimerBossBar(Component title, int remaining, int duration) {
        float progress = duration <= 0 ? 0F : Math.clamp(remaining / (float) duration, 0F, 1F);
        if (timerBossBar == null) {
            BossBar bar = BossBar.bossBar(title, progress, BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS);
            timerBossBar = bar;
            forEachOnlinePlayer(player -> player.showBossBar(bar));
        } else {
            timerBossBar.name(title);
            timerBossBar.progress(progress);
        }
    }

    private void hideTimerBossBar() {
        BossBar bar = timerBossBar;
        timerBossBar = null;
        if (bar != null) forEachOnlinePlayer(player -> player.hideBossBar(bar));
    }

    private void updateSidebar() {
        BingoResult result = scoring.result();
        MatchState currentState = state();
        forEachOnlineParticipant((player, snapshot) ->
                renderSidebar(player, snapshot, result, currentState));
    }

    private void requestSidebarUpdate() {
        if (!sidebarRefreshPending.compareAndSet(false, true)) return;
        scheduler.runGlobalLater(() -> {
            sidebarRefreshPending.set(false);
            updateSidebar();
        }, 1L);
    }

    private void refreshSidebar(Player player, PlayerSnapshot viewer) {
        MatchState currentState = state();
        renderSidebar(player, viewer, scoring.result(), currentState);
    }

    private void renderSidebar(Player player, PlayerSnapshot viewer, BingoResult result,
                               MatchState currentState) {
        if (manifest.runtimeRules().presentation().messages().containsKey("sidebar.line-count")) {
            renderConfiguredSidebar(player, viewer, result, currentState);
            return;
        }
        List<Component> lines = new ArrayList<>();
        lines.add(message("board.separator"));
        lines.add(message("board.current_game", "{0}", gameName()));
        lines.add(WorkerPresentationService.component("#1da4ad场地状态: #f6ffa8"
                + WorkerPresentationService.sidebarStatus(manifest.runtimeRules().presentation(), currentState)));
        lines.add(message("board.teams_header"));
        Integer viewerTeamId = viewer.role() == ParticipantRole.PLAYER ? viewer.teamId() : null;
        for (WorkerSidebarRanking.Entry entry : WorkerSidebarRanking.select(result, teams, viewerTeamId)) {
            TeamSnapshot team = entry.team();
            String key = entry.viewerTeam() ? "board.own_team_score" : "board.team_score";
            Component teamName = Component.text(team.name(), teamColor(team));
            if (entry.viewerTeam()) teamName = teamName.decorate(TextDecoration.BOLD);
            Component row = message(key,
                    "{0}", Integer.toString(entry.rank()),
                    "{2}", Integer.toString(result.teamScores().getOrDefault(team.id(), 0)),
                    "{3}", Integer.toString(result.completedCells().getOrDefault(team.id(), 0)),
                    "{4}", Integer.toString(manifest.tasks().size()));
            Component finalTeamName = teamName;
            lines.add(row.replaceText(builder -> builder.matchLiteral("{1}").replacement(finalTeamName)));
        }
        lines.add(Component.empty());
        lines.add(message("board.footer"));
        sidebar.render(player, message("board.title"), lines);
    }

    private void renderConfiguredSidebar(Player player, PlayerSnapshot viewer, BingoResult result,
                                         MatchState currentState) {
        BingoPresentation presentation = manifest.runtimeRules().presentation();
        int count;
        try {
            count = Integer.parseInt(presentation.messages().getOrDefault("sidebar.line-count", "0"));
        } catch (NumberFormatException ignored) {
            count = 0;
        }
        String status = WorkerPresentationService.sidebarStatus(presentation, currentState);
        Integer viewerTeamId = viewer.role() == ParticipantRole.PLAYER ? viewer.teamId() : null;
        int viewerTasks = viewerTeamId == null ? 0 : result.completedCells().getOrDefault(viewerTeamId, 0);
        List<Component> lines = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            String raw = presentation.messages().getOrDefault("sidebar.line." + index, "");
            if ("{ranking}".equals(raw)) {
                appendConfiguredRanking(lines, result, viewerTeamId, presentation);
                continue;
            }
            raw = WorkerPresentationService.sidebarLine(raw, gameName(), status, viewerTasks);
            lines.add(WorkerPresentationService.component(raw));
        }
        if (lines.size() > 15) lines = new ArrayList<>(lines.subList(0, 15));
        sidebar.render(player, WorkerPresentationService.component(
                presentation.messages().getOrDefault("sidebar.title", plainMessage("board.title"))), lines);
    }

    private void appendConfiguredRanking(List<Component> lines, BingoResult result, Integer viewerTeamId,
                                         BingoPresentation presentation) {
        String normal = presentation.messages().getOrDefault("sidebar.ranking-line",
                "{rank.team-color}{rank.position}. {rank.team} &f{rank.score}");
        String own = presentation.messages().getOrDefault("sidebar.own-ranking-line", normal);
        for (WorkerSidebarRanking.Entry entry : WorkerSidebarRanking.select(result, teams, viewerTeamId)) {
            TeamSnapshot team = entry.team();
            String raw = (entry.viewerTeam() ? own : normal)
                    .replace("{rank.team-color}", team.colorCode())
                    .replace("{rank.position}", Integer.toString(entry.rank()))
                    .replace("{rank.team}", team.name())
                    .replace("{rank.score}", Integer.toString(result.teamScores().getOrDefault(team.id(), 0)))
                    .replace("{rank.tasks}", Integer.toString(result.completedCells().getOrDefault(team.id(), 0)));
            lines.add(WorkerPresentationService.component(raw));
        }
    }

    private String sidebarField(String preferred, String fallback) {
        BingoPresentation presentation = manifest.runtimeRules().presentation();
        return presentation.messages().getOrDefault(preferred, plainMessage(fallback));
    }

    private String plainMessage(String key) {
        return manifest.runtimeRules().presentation().messages().getOrDefault(key, "");
    }

    private Component message(String key, String... replacements) {
        return WorkerPresentationService.message(manifest.runtimeRules().presentation(), key, replacements);
    }

    private BingoTaskSpec taskAt(int cellIndex) {
        return taskAt(cellIndex, null);
    }

    private BingoTaskSpec taskAt(int cellIndex, UUID playerId) {
        return tasksSnapshot(playerId).stream()
                .filter(task -> task.cellIndex() == cellIndex)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing Bingo task for cell " + cellIndex));
    }

    private TeamSnapshot resolveWinner(BingoResult result) {
        Integer winnerId = result.winnerTeamId();
        return winnerId == null ? null : teams.get(winnerId);
    }

    private String gameName() {
        return manifest.runtimeRules().presentation().message("game.name");
    }

    private static TextColor teamColor(TeamSnapshot team) {
        TextColor color = team == null ? null : TextColor.fromHexString(team.colorCode());
        return color == null ? NamedTextColor.WHITE : color;
    }

    private static void showTitle(Player player, Component title, Component subtitle,
                                  int fadeInTicks, int stayTicks, int fadeOutTicks) {
        player.showTitle(Title.title(title, subtitle, Title.Times.times(
                Duration.ofMillis(fadeInTicks * 50L), Duration.ofMillis(stayTicks * 50L),
                Duration.ofMillis(fadeOutTicks * 50L))));
    }

    private void setPvp(boolean enabled) {
        for (String name : List.of(config.overworld(), config.nether(), config.end())) {
            World world = plugin.getServer().getWorld(name);
            if (world != null) world.setGameRule(GameRules.PVP, enabled);
        }
    }

    private Location introductionLocation() {
        BingoLocationSnapshot configured = manifest.runtimeRules().introductionSpawn();
        return configured == null ? spectatorLocation() : location(configured);
    }

    private Location spectatorLocation() {
        BingoLocationSnapshot configured = manifest.runtimeRules().spectatorSpawn();
        if (configured != null) return location(configured);
        World world = plugin.getServer().getWorld(config.overworld());
        return world == null ? null : world.getSpawnLocation();
    }

    private Location location(BingoLocationSnapshot snapshot) {
        String worldName = switch (snapshot.dimension()) {
            case OVERWORLD -> config.overworld();
            case NETHER -> config.nether();
            case THE_END -> config.end();
        };
        World world = plugin.getServer().getWorld(worldName);
        return world == null ? null : new Location(world, snapshot.x(), snapshot.y(), snapshot.z(),
                snapshot.yaw(), snapshot.pitch());
    }

    private long elapsedTicks() {
        return Math.max(0L, (System.currentTimeMillis() - startedAtMillis) / 50L);
    }

    private int onlineParticipantCount() {
        int online = 0;
        for (UUID playerId : participants.keySet()) {
            if (plugin.getServer().getPlayer(playerId) != null) online++;
        }
        return online;
    }

    private void forEachOnlinePlayer(java.util.function.Consumer<Player> action) {
        List<Player> online = new ArrayList<>();
        for (UUID playerId : participants.keySet()) {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player != null) online.add(player);
        }
        for (Player player : online) scheduler.runEntity(player, () -> action.accept(player));
    }

    private void forEachOnlineParticipant(java.util.function.BiConsumer<Player, PlayerSnapshot> action) {
        List<Map.Entry<Player, PlayerSnapshot>> online = new ArrayList<>();
        for (PlayerSnapshot snapshot : participants.values()) {
            Player player = plugin.getServer().getPlayer(snapshot.uuid());
            if (player != null) online.add(Map.entry(player, snapshot));
        }
        for (Map.Entry<Player, PlayerSnapshot> entry : online) {
            scheduler.runEntity(entry.getKey(), () -> action.accept(entry.getKey(), entry.getValue()));
        }
    }

    private record TeamCell(int teamId, int cellIndex) {
    }
}
