package ink.ziip.championshipscore.api.test;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseManager;
import ink.ziip.championshipscore.api.game.instance.BaseGameInstance;
import ink.ziip.championshipscore.api.game.model.GameRunMode;
import ink.ziip.championshipscore.api.game.model.GameTypeEnum;
import ink.ziip.championshipscore.api.game.start.ArenaSelection;
import ink.ziip.championshipscore.api.game.start.GameExecutionContext;
import ink.ziip.championshipscore.api.game.start.GameFlow;
import ink.ziip.championshipscore.api.game.start.GameStartArguments;
import ink.ziip.championshipscore.api.game.start.GameStartService;
import ink.ziip.championshipscore.api.game.start.SettlementPolicy;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.api.test.auth.TestAuthGateway;
import ink.ziip.championshipscore.api.test.auth.TestBotIdentityClient;
import ink.ziip.championshipscore.api.test.auth.TestIdentityReservations;
import ink.ziip.championshipscore.api.test.auth.TestLeaseNonceStore;
import ink.ziip.championshipscore.auth.TestLeaseToken;
import ink.ziip.championshipscore.configuration.config.CCConfig;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.BiPredicate;
import java.util.function.Supplier;

/**
 * Core-owned lifecycle boundary for unattended tests.
 *
 * <p>It owns the authentication boundary, Core-local single/Event orchestration, and guarantees
 * that shutdown invalidates all test generations.
 */
public final class TestSessionManager extends BaseManager {
    private static final int WEB_LEASE_TTL_SECONDS = 86_400;
    private static final long WEB_LEASE_RENEWAL_PERIOD_TICKS = 20L * 60L * 60L * 6L;
    private static final String[] TEAM_COLORS = {
        "RED", "GREEN", "BLUE", "YELLOW", "CYAN", "PURPLE", "ORANGE", "WHITE",
        "LIME", "PINK", "LIGHT_BLUE", "MAGENTA", "GRAY", "BLACK", "BROWN", "LIGHT_GRAY"
    };
    private static final String[] TEAM_CODES = {
        "#ff5555", "#55ff55", "#5555ff", "#ffff55", "#55ffff", "#aa00aa", "#ffaa00", "#ffffff",
        "#00aa00", "#ff55ff", "#00aaaa", "#aa0000", "#aaaaaa", "#000000", "#555555", "#aaaaaa"
    };
    private TestSession active;
    private List<ChampionshipTeam> transientTeams = List.of();

    /**
     * Runtime instances started by the current test session, retained even if roster indexes
     * change.
     */
    private List<BaseGameInstance> ownedInstances = List.of();

    private TestEventOrchestrator eventOrchestrator;
    private Object materializationRequest;
    private final Set<UUID> admittedBotUuids = new HashSet<>();

    private final TestIdentityReservations reservations;
    private final BiPredicate<String, UUID> formalIdentityConflict;
    private final TestDeveloperStateStore developerStates;
    private final TestLeaseNonceStore leaseNonces;

    private TestSession cleanupOwner;
    private boolean cleanupCloseRequested;
    private CompletableFuture<Void> cleanupFuture = CompletableFuture.completedFuture(null);
    private org.bukkit.scheduler.BukkitTask webLeaseRenewalTask;
    private CompletionStage<GameStartService.Result> pendingStart;
    private boolean loaded;
    private volatile boolean unloading;

    public TestSessionManager(ChampionshipsCore championshipsCore) {
        this(
                championshipsCore,
                championshipsCore == null
                        ? TestIdentityReservations.inMemory()
                        : new TestIdentityReservations(
                                championshipsCore
                                        .getDataFolder()
                                        .toPath()
                                        .resolve("test/identity-reservations.tsv")));
    }

    TestSessionManager(ChampionshipsCore championshipsCore, TestIdentityReservations reservations) {
        this(
                championshipsCore,
                reservations,
                championshipsCore == null
                        ? (username, uuid) -> false
                        : (username, uuid) ->
                                requireFormalIdentityStore(championshipsCore)
                                        && (championshipsCore
                                                        .getPlayerManager()
                                                        .conflictsWithFormalIdentity(username, uuid)
                                                || championshipsCore
                                                                .getTeamManager()
                                                                .getFormalTeamByPlayer(uuid)
                                                        != null));
    }

    TestSessionManager(
            ChampionshipsCore championshipsCore,
            TestIdentityReservations reservations,
            BiPredicate<String, UUID> formalIdentityConflict) {
        this(
                championshipsCore,
                reservations,
                formalIdentityConflict,
                developerStateDirectory(championshipsCore));
    }

    TestSessionManager(
            ChampionshipsCore championshipsCore,
            TestIdentityReservations reservations,
            BiPredicate<String, UUID> formalIdentityConflict,
            java.nio.file.Path developerStateDirectory) {
        super(championshipsCore);
        this.reservations = Objects.requireNonNull(reservations, "reservations");
        this.formalIdentityConflict =
                Objects.requireNonNull(formalIdentityConflict, "formalIdentityConflict");
        this.developerStates = new TestDeveloperStateStore(developerStateDirectory);
        this.leaseNonces =
                new TestLeaseNonceStore(
                        championshipsCore == null || championshipsCore.getDataFolder() == null
                                ? null
                                : championshipsCore
                                        .getDataFolder()
                                        .toPath()
                                        .resolve("test/lease-nonces.tsv"));
    }

    private static java.nio.file.Path developerStateDirectory(ChampionshipsCore core) {
        if (core == null) return null;
        java.io.File dataFolder = core.getDataFolder();
        return dataFolder == null ? null : dataFolder.toPath().resolve("test/developers");
    }

    /**
     * Test lease admission must stop when the durable identity store has not finished booting.
     * Returning false here would turn an unavailable database into an approval and persist a
     * reservation without proving that the name and UUID are outside the formal identity domain.
     */
    private static boolean requireFormalIdentityStore(@NotNull ChampionshipsCore core) {
        if (core.getDatabaseManager() == null)
            throw new IllegalStateException("formal identity store is unavailable");
        return true;
    }

    @Override
    public synchronized void load() {
        // The first load completes after Core's asynchronous database bootstrap. Only a genuine
        // second load is a reload; treating the first load as a reload would revoke a session that
        // an early dependent plugin created during its own enable callback.
        if (loaded) closeActiveSession();
        unloading = false;
        loaded = true;
    }

    @Override
    public synchronized void unload() {
        unloading = true;
        cancelWebLeaseRenewal();
        closeActiveSession();
        loaded = false;
    }

    public boolean isEnabled() {
        return Boolean.TRUE.equals(CCConfig.TEST_ENABLED);
    }

    private void requireEnabled() {
        if (!isEnabled())
            throw new IllegalStateException(
                    "Game tests are disabled; set test.enabled on this server to allow them");
    }

    /** Creates an authentication-pending session; only one session may exist at a time. */
    public synchronized TestSession createSession(TestSessionMode mode, TestRoster roster) {
        return createSession(mode, roster, null);
    }

    /** Creates a session and freezes its target metadata before any Bot is admitted. */
    public synchronized TestSession createSession(
            TestSessionMode mode, TestRoster roster, TestSessionPlan plan) {
        return createSession(mode, roster, plan, null);
    }

    public synchronized TestSession createEventSession(
            TestRoster roster, TestSessionPlan plan, TestEventPlan eventPlan) {
        return createSession(TestSessionMode.EVENT, roster, plan, eventPlan);
    }

    private synchronized TestSession createSession(
            TestSessionMode mode,
            TestRoster roster,
            TestSessionPlan plan,
            TestEventPlan eventPlan) {
        requireEnabled();
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(roster, "roster");
        if (plugin != null && !loaded)
            throw new IllegalStateException("test session manager is not ready");
        if (active != null && !active.state().terminal())
            throw new IllegalStateException("a test session is already active");
        if (cleanupOwner != null || !transientTeams.isEmpty())
            throw new IllegalStateException("previous test game is still cleaning up");
        TestSession session =
                new TestSession(
                        UUID.randomUUID(),
                        mode,
                        roster,
                        new TestIdentityRegistry(),
                        plan,
                        eventPlan);
        session.beginAuthentication();
        active = session;
        admittedBotUuids.clear();
        pendingStart = null;
        return session;
    }

    /** Registers the expected profile UUID supplied by the trusted test identity service. */
    public synchronized void registerLease(
            String username, UUID expectedProfileUuid, Instant expiresAt, Instant now) {
        requireEnabled();
        if (active == null) throw new IllegalStateException("no test session");
        if (active.state() != TestSessionState.AUTHENTICATING)
            throw new IllegalStateException(
                    "test leases can only be registered during authentication");
        if (!active.roster().isBotUsername(username))
            throw new IllegalArgumentException("username is not in the test roster: " + username);
        if (formalIdentityConflict.test(username, expectedProfileUuid))
            throw new IllegalArgumentException(
                    "test identity conflicts with a formal Core identity: " + username);
        active.identities()
                .register(
                        new TestIdentityRegistry.Lease(
                                active.sessionId(),
                                active.generation(),
                                username,
                                expectedProfileUuid,
                                expiresAt),
                        now);
        try {
            reservations.reserve(username, expectedProfileUuid);
        } catch (RuntimeException failure) {
            active.identities().revokeUsername(username);
            throw failure;
        }
    }

    /** Verifies and consumes a signed identity-service lease for this exact session generation. */
    public synchronized void registerSignedLease(String token, Instant now) {
        requireEnabled();
        if (active == null) throw new IllegalStateException("no test session");
        String serverId = CCConfig.TEST_AUTH_SERVER_ID;
        String secret = CCConfig.TEST_AUTH_HMAC_SECRET;
        if (serverId == null
                || serverId.isBlank()
                || secret == null
                || secret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32)
            throw new IllegalStateException(
                    "test.auth server-id and 32+ byte hmac-secret are required");
        TestLeaseToken.Claims claims =
                TestLeaseToken.verify(
                        token,
                        secret,
                        serverId,
                        active.sessionId(),
                        active.generation(),
                        now.getEpochSecond());
        if (!active.roster().isBotUsername(claims.username()))
            throw new IllegalArgumentException(
                    "username is not in the test roster: " + claims.username());
        leaseNonces.consume(claims.nonce());
        registerLease(
                claims.username(),
                claims.profileUuid(),
                Instant.ofEpochSecond(claims.expiresAtEpochSecond()),
                now);
    }

    /** Verifies and renews an existing web lease without resetting its connection state. */
    private synchronized void renewSignedLease(String token, Instant now) {
        requireEnabled();
        if (active == null) throw new IllegalStateException("no test session");
        String serverId = CCConfig.TEST_AUTH_SERVER_ID;
        String secret = CCConfig.TEST_AUTH_HMAC_SECRET;
        if (serverId == null
                || serverId.isBlank()
                || secret == null
                || secret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32)
            throw new IllegalStateException(
                    "test.auth server-id and 32+ byte hmac-secret are required");
        TestLeaseToken.Claims claims =
                TestLeaseToken.verify(
                        token,
                        secret,
                        serverId,
                        active.sessionId(),
                        active.generation(),
                        now.getEpochSecond());
        if (!active.roster().isBotUsername(claims.username()))
            throw new IllegalArgumentException(
                    "username is not in the test roster: " + claims.username());
        leaseNonces.consume(claims.nonce());
        active.identities()
                .renew(
                        active.sessionId(),
                        active.generation(),
                        claims.username(),
                        claims.profileUuid(),
                        Instant.ofEpochSecond(claims.expiresAtEpochSecond()),
                        now);
    }

    /** Obtains the current Bot UUIDs and signed leases from cc-web for this session. */
    public CompletionStage<Integer> registerWebLeases() {
        final TestSession session;
        final List<String> usernames;
        synchronized (this) {
            requireEnabled();
            if (active == null) throw new IllegalStateException("no test session");
            if (active.state() != TestSessionState.AUTHENTICATING)
                throw new IllegalStateException(
                        "web leases can only be registered during authentication");
            if (CCConfig.TEST_IDENTITY_BASE_URL == null
                    || CCConfig.TEST_IDENTITY_BASE_URL.isBlank()
                    || CCConfig.TEST_IDENTITY_KEY_ID == null
                    || CCConfig.TEST_IDENTITY_KEY_ID.isBlank()
                    || CCConfig.TEST_IDENTITY_HMAC_SECRET == null
                    || CCConfig.TEST_IDENTITY_HMAC_SECRET.getBytes(
                                            java.nio.charset.StandardCharsets.UTF_8)
                                    .length
                            < 32)
                throw new IllegalStateException(
                        "test.identity Web API configuration is incomplete");
            session = active;
            usernames =
                    session.roster().snapshot().stream()
                            .filter(seat -> seat.occupantType() == TestRoster.OccupantType.BOT)
                            .map(TestRoster.SeatSnapshot::botUsername)
                            .toList();
        }
        TestBotIdentityClient client =
                new TestBotIdentityClient(
                        CCConfig.TEST_IDENTITY_BASE_URL,
                        CCConfig.TEST_IDENTITY_KEY_ID,
                        CCConfig.TEST_IDENTITY_HMAC_SECRET,
                        Boolean.TRUE.equals(CCConfig.TEST_IDENTITY_ALLOW_INSECURE_PRIVATE_HTTP),
                        java.time.Duration.ofSeconds(
                                Math.max(1, CCConfig.TEST_IDENTITY_CONNECT_TIMEOUT_SECONDS)),
                        java.time.Duration.ofSeconds(
                                Math.max(1, CCConfig.TEST_IDENTITY_REQUEST_TIMEOUT_SECONDS)));
        return java.util.concurrent.CompletableFuture.supplyAsync(
                        () -> {
                            try {
                                return client.leases(
                                        CCConfig.TEST_AUTH_SERVER_ID,
                                        session.sessionId(),
                                        session.generation(),
                                        usernames,
                                        WEB_LEASE_TTL_SECONDS);
                            } catch (Exception failure) {
                                throw new java.util.concurrent.CompletionException(failure);
                            }
                        })
                .thenApply(
                        leases -> {
                            synchronized (this) {
                                if (active != session
                                        || session.state() != TestSessionState.AUTHENTICATING)
                                    throw new IllegalStateException(
                                            "test session changed while obtaining Bot leases");
                                leases.forEach(
                                        lease -> registerSignedLease(lease.token(), Instant.now()));
                                scheduleWebLeaseRenewal(session);
                                return leases.size();
                            }
                        });
    }

    private synchronized void scheduleWebLeaseRenewal(TestSession session) {
        cancelWebLeaseRenewal();
        if (plugin == null || !plugin.isEnabled()) return;
        webLeaseRenewalTask =
                plugin.getServer()
                        .getScheduler()
                        .runTaskTimerAsynchronously(
                                plugin,
                                () -> renewWebLeases(session),
                                WEB_LEASE_RENEWAL_PERIOD_TICKS,
                                WEB_LEASE_RENEWAL_PERIOD_TICKS);
    }

    private void renewWebLeases(TestSession session) {
        final List<String> usernames;
        final long generation;
        synchronized (this) {
            if (active != session || session.state().terminal() || unloading) return;
            generation = session.generation();
            usernames =
                    session.roster().snapshot().stream()
                            .filter(seat -> seat.occupantType() == TestRoster.OccupantType.BOT)
                            .map(TestRoster.SeatSnapshot::botUsername)
                            .toList();
        }
        TestBotIdentityClient client;
        try {
            client = webIdentityClient();
        } catch (RuntimeException failure) {
            logLeaseRenewalFailure(failure);
            return;
        }
        CompletableFuture.supplyAsync(
                        () -> {
                            try {
                                return client.leases(
                                        CCConfig.TEST_AUTH_SERVER_ID,
                                        session.sessionId(),
                                        generation,
                                        usernames,
                                        WEB_LEASE_TTL_SECONDS);
                            } catch (Exception failure) {
                                throw new CompletionException(failure);
                            }
                        })
                .thenAccept(
                        leases -> {
                            synchronized (this) {
                                if (active != session
                                        || session.generation() != generation
                                        || session.state().terminal()) return;
                                Instant now = Instant.now();
                                leases.forEach(lease -> renewSignedLease(lease.token(), now));
                            }
                        })
                .exceptionally(
                        failure -> {
                            logLeaseRenewalFailure(failure);
                            return null;
                        });
    }

    private TestBotIdentityClient webIdentityClient() {
        if (CCConfig.TEST_IDENTITY_BASE_URL == null
                || CCConfig.TEST_IDENTITY_BASE_URL.isBlank()
                || CCConfig.TEST_IDENTITY_KEY_ID == null
                || CCConfig.TEST_IDENTITY_KEY_ID.isBlank()
                || CCConfig.TEST_IDENTITY_HMAC_SECRET == null
                || CCConfig.TEST_IDENTITY_HMAC_SECRET.getBytes(
                                        java.nio.charset.StandardCharsets.UTF_8)
                                .length
                        < 32)
            throw new IllegalStateException("test.identity Web API configuration is incomplete");
        return new TestBotIdentityClient(
                CCConfig.TEST_IDENTITY_BASE_URL,
                CCConfig.TEST_IDENTITY_KEY_ID,
                CCConfig.TEST_IDENTITY_HMAC_SECRET,
                Boolean.TRUE.equals(CCConfig.TEST_IDENTITY_ALLOW_INSECURE_PRIVATE_HTTP),
                java.time.Duration.ofSeconds(
                        Math.max(1, CCConfig.TEST_IDENTITY_CONNECT_TIMEOUT_SECONDS)),
                java.time.Duration.ofSeconds(
                        Math.max(1, CCConfig.TEST_IDENTITY_REQUEST_TIMEOUT_SECONDS)));
    }

    private void logLeaseRenewalFailure(Throwable failure) {
        Throwable cause =
                failure instanceof CompletionException && failure.getCause() != null
                        ? failure.getCause()
                        : failure;
        if (plugin != null)
            plugin.getLogger()
                    .warning(
                            "Test Bot web lease renewal failed; existing leases remain unchanged: "
                                    + (cause.getMessage() == null
                                            ? cause.getClass().getSimpleName()
                                            : cause.getMessage()));
    }

    private synchronized void cancelWebLeaseRenewal() {
        if (webLeaseRenewalTask != null) {
            webLeaseRenewalTask.cancel();
            webLeaseRenewalTask = null;
        }
    }

    public synchronized TestSession activeSession() {
        return active;
    }

    /** Status remains reviewable while asynchronous cleanup owns a stopped session. */
    public synchronized TestSession sessionForStatus() {
        return active != null ? active : cleanupOwner;
    }

    /**
     * Reserves roster members across preparation, result windows and asynchronous cleanup. This
     * does not grant gameplay or authentication; other lobbies use it only to avoid taking over a
     * connection which belongs to this test.
     */
    public synchronized boolean ownsRosterMember(UUID uuid) {
        Objects.requireNonNull(uuid, "uuid");
        TestSession owner = cleanupOwner != null ? cleanupOwner : active;
        return owner != null
                && owner.state() != TestSessionState.CLOSED
                && (owner.state() != TestSessionState.FAILED || cleanupOwner == owner)
                && owner.roster().allMembers().contains(uuid);
    }

    /** Called by the Core pre-login listener with the UUID actually observed by Paper. */
    public synchronized TestAuthGateway.PreLoginResult observePreLogin(
            String username, UUID observedUuid, Instant now) {
        if (!isEnabled())
            return new TestAuthGateway.PreLoginResult(
                    reservations.contains(username, observedUuid)
                                    || (active != null
                                            && active.identities().snapshot(username) != null)
                            ? TestAuthGateway.PreLoginStatus.REJECTED
                            : TestAuthGateway.PreLoginStatus.NOT_TEST_IDENTITY,
                    null,
                    "game tests are disabled");
        if ((active == null || active.identities().snapshot(username) == null)
                && reservations.contains(username, observedUuid))
            return new TestAuthGateway.PreLoginResult(
                    TestAuthGateway.PreLoginStatus.REJECTED,
                    null,
                    "test identity lease was revoked");
        if (active == null)
            return new TestAuthGateway.PreLoginResult(
                    TestAuthGateway.PreLoginStatus.NOT_TEST_IDENTITY, null, "no test session");
        TestAuthGateway.PreLoginResult result =
                new TestAuthGateway(active).observePreLogin(username, observedUuid, now);
        if (result.accepted()) {
            try {
                active.roster().bindBot(username, observedUuid);
            } catch (RuntimeException failure) {
                return new TestAuthGateway.PreLoginResult(
                        TestAuthGateway.PreLoginStatus.REJECTED,
                        result.binding(),
                        failure.getMessage());
            }
        }
        return result;
    }

    /** Called by AuthBridge only after AuthMe has accepted the same server UUID. */
    public synchronized TestIdentityRegistry.BindingSnapshot confirmAuthMeLogin(
            String username, UUID observedUuid, Instant now) {
        requireEnabled();
        if (active == null) throw new IllegalStateException("no test session");
        TestIdentityRegistry.BindingSnapshot binding =
                new TestAuthGateway(active).markAuthMeLogin(username, observedUuid, now);
        admittedBotUuids.add(observedUuid);
        maybeReadyAndMaterialize(active);
        return binding;
    }

    public synchronized boolean hasTestLease(String username) {
        return (active != null && active.identities().snapshot(username) != null)
                || reservations.containsName(username);
    }

    public synchronized boolean isReservedTestIdentity(String username, UUID observedUuid) {
        return active != null && active.identities().isReserved(username, observedUuid);
    }

    public synchronized boolean isAuthenticatedTestIdentity(String username, UUID observedUuid) {
        return isEnabled()
                && active != null
                && active.identities().isAuthenticated(username, observedUuid);
    }

    /** Whether a returning Bot may still use this session's trusted lease for a fresh login. */
    public synchronized boolean hasUsableReconnectLease(String username, UUID uuid) {
        if (!isEnabled() || active == null) return false;
        var binding = active.identities().snapshot(username);
        return binding != null
                && active.sessionId().equals(binding.sessionId())
                && active.isCurrent(binding.generation())
                && uuid.equals(binding.expectedProfileUuid())
                && binding.state() != TestIdentityRegistry.BindingState.REVOKED
                && binding.expiresAt().isAfter(Instant.now());
    }

    public synchronized boolean isReady() {
        return active != null && active.state() == TestSessionState.READY;
    }

    public synchronized List<ChampionshipTeam> transientTeams() {
        return List.copyOf(transientTeams);
    }

    public synchronized int eventRound() {
        return eventOrchestrator == null || active == null || active.mode() != TestSessionMode.EVENT
                ? -1
                : eventOrchestrator.currentRound();
    }

    public synchronized java.util.Map<UUID, Double> eventStandings() {
        return eventOrchestrator == null ? java.util.Map.of() : eventOrchestrator.standings();
    }

    /** Replaces one Bot seat with an already authenticated developer before a game starts. */
    public synchronized void joinDeveloper(int teamNumber, Player developer) {
        requireEnabled();
        Objects.requireNonNull(developer, "developer");
        requirePreparation();
        if (plugin != null
                && (plugin.getGameManager().getBasePlayerArea(developer.getUniqueId()) != null
                        || plugin.getGameManager().getPlayerSpectatorStatus(developer.getUniqueId())
                                != null
                        || (plugin.getDailyManager() != null
                                && (plugin.getDailyManager().isQueued(developer.getUniqueId())
                                        || plugin.getDailyManager().session(developer.getUniqueId())
                                                != null))))
            throw new IllegalStateException(
                    "leave the current game, queue or spectator area before joining a test team");
        if (active.roster().snapshot().stream()
                .anyMatch(
                        seat ->
                                seat.teamNumber() == teamNumber
                                        && seat.occupantType() == TestRoster.OccupantType.DEVELOPER
                                        && developer.getUniqueId().equals(seat.occupantUuid())))
            return;
        TestRoster.SeatSnapshot target =
                active.roster().snapshot().stream()
                        .filter(seat -> seat.teamNumber() == teamNumber)
                        .filter(seat -> seat.occupantType() == TestRoster.OccupantType.BOT)
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("team has no Bot seat"));
        TestRoster.SeatSnapshot previous =
                active.roster().snapshot().stream()
                        .filter(seat -> seat.occupantType() == TestRoster.OccupantType.DEVELOPER)
                        .filter(seat -> developer.getUniqueId().equals(seat.occupantUuid()))
                        .findFirst()
                        .orElse(null);
        if (previous != null && previous.teamNumber() == teamNumber) return;
        developerStates.captureIfAbsent(developer);
        if (previous != null)
            active.roster().removeDeveloper(previous.teamNumber(), developer.getUniqueId());
        UUID replacedUuid = target.occupantUuid();
        String replacedBot =
                active.roster()
                        .replaceWithDeveloper(
                                teamNumber, developer.getUniqueId(), developer.getName());
        active.identities().revokeUsername(replacedBot);
        if (replacedUuid != null) {
            Player bot = Bukkit.getPlayer(replacedUuid);
            if (bot != null)
                bot.kick(
                        net.kyori.adventure.text.Component.text(
                                "Test seat replaced by developer."));
            if (plugin != null && plugin.getPlayerManager() != null)
                plugin.getPlayerManager().removeEphemeralTestIdentity(replacedUuid);
            admittedBotUuids.remove(replacedUuid);
        }
        refreshPreparationRoster();
        refreshDailyLobby(developer);
    }

    public synchronized String leaveDeveloper(int teamNumber, Player developer) {
        Objects.requireNonNull(developer, "developer");
        requirePreparation();
        String restoredBot = active.roster().removeDeveloper(teamNumber, developer.getUniqueId());
        refreshPreparationRoster();
        refreshDailyLobby(developer);
        restoreDeveloperState(developer.getUniqueId());
        return restoredBot;
    }

    public void recoverDeveloperStateOnJoin(Player player) {
        if (player == null) return;
        if (plugin == null) {
            restoreDeveloperState(player.getUniqueId());
            return;
        }
        // Join handlers and deferred lobby initialization can replace developer state. Keep the
        // durable snapshot until a second restore has run after those callbacks.
        plugin.getServer()
                .getScheduler()
                .runTaskLater(plugin, () -> beginDeveloperRecovery(player, 0), 2L);
    }

    private void beginDeveloperRecovery(Player player, int attempt) {
        if (!player.isOnline() || Bukkit.getPlayer(player.getUniqueId()) != player) return;
        UUID uuid = player.getUniqueId();
        if (!developerStates.contains(uuid)) return;
        synchronized (this) {
            if (active != null
                    && active.roster().snapshot().stream()
                            .anyMatch(
                                    seat ->
                                            seat.occupantType() == TestRoster.OccupantType.DEVELOPER
                                                    && uuid.equals(seat.occupantUuid()))) return;
            if (!applyDeveloperState(uuid)) {
                retryDeveloperRecovery(player, attempt);
                return;
            }
        }
        if (plugin == null || !plugin.isEnabled()) return;
        plugin.getServer()
                .getScheduler()
                .runTaskLater(plugin, () -> finishDeveloperRecovery(player, attempt), 5L);
    }

    private void finishDeveloperRecovery(Player player, int attempt) {
        if (!player.isOnline() || Bukkit.getPlayer(player.getUniqueId()) != player) return;
        UUID uuid = player.getUniqueId();
        synchronized (this) {
            if (active != null
                    && active.roster().snapshot().stream()
                            .anyMatch(
                                    seat ->
                                            seat.occupantType() == TestRoster.OccupantType.DEVELOPER
                                                    && uuid.equals(seat.occupantUuid()))) return;
            if (developerStates.contains(uuid)) restoreDeveloperState(uuid);
        }
        if (developerStates.contains(uuid)) retryDeveloperRecovery(player, attempt);
    }

    private void retryDeveloperRecovery(Player player, int attempt) {
        if (!player.isOnline() || Bukkit.getPlayer(player.getUniqueId()) != player) return;
        UUID uuid = player.getUniqueId();
        if (attempt >= 300
                || !developerStates.contains(uuid)
                || plugin == null
                || !plugin.isEnabled()) return;
        plugin.getServer()
                .getScheduler()
                .runTaskLater(plugin, () -> beginDeveloperRecovery(player, attempt + 1), 20L);
    }

    public synchronized void recoverOnlineDeveloperStates() {
        recoverOnlineDeveloperStates(true);
    }

    private synchronized void recoverOnlineDeveloperStates(boolean skipActiveDevelopers) {
        for (UUID uuid : developerStates.uuids()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) continue;
            if (skipActiveDevelopers
                    && active != null
                    && active.roster().snapshot().stream()
                            .anyMatch(
                                    seat ->
                                            seat.occupantType() == TestRoster.OccupantType.DEVELOPER
                                                    && uuid.equals(seat.occupantUuid()))) continue;
            recoverDeveloperStateOnJoin(player);
        }
    }

    private boolean restoreDeveloperState(UUID uuid) {
        try {
            developerStates.restoreIfOnline(uuid);
            return !developerStates.contains(uuid);
        } catch (RuntimeException failure) {
            if (plugin != null)
                plugin.getLogger()
                        .log(
                                java.util.logging.Level.WARNING,
                                "Developer test state remains pending for " + uuid,
                                failure);
            return false;
        }
    }

    private boolean applyDeveloperState(UUID uuid) {
        try {
            return developerStates.applyIfOnline(uuid);
        } catch (RuntimeException failure) {
            if (plugin != null)
                plugin.getLogger()
                        .log(
                                java.util.logging.Level.WARNING,
                                "Developer test state remains pending for " + uuid,
                                failure);
            return false;
        }
    }

    private void refreshDailyLobby(Player player) {
        if (plugin != null && plugin.getDailyManager() != null)
            plugin.getDailyManager().syncLobbyItem(player);
    }

    private void requirePreparation() {
        if (active == null) throw new IllegalStateException("no test session");
        if (active.state() != TestSessionState.AUTHENTICATING
                && active.state() != TestSessionState.READY)
            throw new IllegalStateException(
                    "developer changes are only allowed during preparation");
        if (plugin != null && !Bukkit.isPrimaryThread())
            throw new IllegalStateException("test roster must be changed on the server thread");
    }

    /** Running games keep their frozen roster; only preparation releases disconnected seats. */
    public synchronized void onPlayerDisconnect(Player player) {
        if (active == null
                || (active.state() != TestSessionState.AUTHENTICATING
                        && active.state() != TestSessionState.READY)) return;
        requirePreparation();
        UUID uuid = player.getUniqueId();
        Player current = Bukkit.getPlayer(uuid);
        if (current != null && current != player) return;
        TestRoster.SeatSnapshot seat =
                active.roster().snapshot().stream()
                        .filter(s -> uuid.equals(s.occupantUuid()))
                        .findFirst()
                        .orElse(null);
        if (seat == null) return;
        if (seat.occupantType() == TestRoster.OccupantType.DEVELOPER) {
            active.roster().removeDeveloper(seat.teamNumber(), uuid);
        } else {
            active.identities().markDisconnected(seat.botUsername(), uuid);
            active.roster().unbindBot(seat.botUsername(), uuid);
            if (plugin != null && plugin.getPlayerManager() != null)
                plugin.getPlayerManager().removeEphemeralTestIdentity(uuid);
            // Keep admission ownership until stop in case an older join callback cached identity.
        }
        refreshPreparationRoster();
    }

    private void refreshPreparationRoster() {
        active.resumeAuthentication();
        materializationRequest = null;
        if (plugin != null) plugin.getTeamManager().removeTransientTeams(transientTeams);
        transientTeams = List.of();
        maybeReadyAndMaterialize(active);
    }

    /**
     * Enters a developer who did not replace a roster seat through the shared spectator lifecycle.
     * The test session only selects its owned runtime instance; mode, protection, teleport and
     * visibility remain owned by GameManager/SpectatorManager.
     */
    public synchronized boolean spectateDeveloper(Player developer) {
        requireEnabled();
        Objects.requireNonNull(developer, "developer");
        if (active == null) throw new IllegalStateException("no test session");
        if (active.state() != TestSessionState.RUNNING)
            throw new IllegalStateException("test game is not running");
        if (active.roster().snapshot().stream()
                .anyMatch(
                        seat ->
                                seat.occupantType() == TestRoster.OccupantType.DEVELOPER
                                        && developer.getUniqueId().equals(seat.occupantUuid())))
            throw new IllegalStateException("a developer occupying a team seat cannot spectate");
        for (BaseGameInstance instance : ownedInstances) {
            if (instance != null && plugin.getGameManager().spectateArea(developer, instance))
                return true;
        }
        return false;
    }

    /** Leaves through the normal GameManager/SpectatorManager release path. */
    public synchronized boolean leaveDeveloperSpectator(Player developer) {
        Objects.requireNonNull(developer, "developer");
        if (!ownedInstances.contains(
                plugin.getGameManager().getPlayerSpectatorStatus(developer.getUniqueId())))
            return false;
        return plugin.getGameManager().leaveSpectating(developer);
    }

    /** Materializes the 16 scoreboard-only teams after all Bot seats are authenticated. */
    public synchronized List<ChampionshipTeam> materializeTransientTeams() {
        requireEnabled();
        if (active == null) throw new IllegalStateException("no test session");
        if (!Bukkit.isPrimaryThread())
            throw new IllegalStateException("test teams must be materialized on the server thread");
        maybeMarkReady(active);
        if (active.state() != TestSessionState.READY)
            throw new IllegalStateException("test roster is not authenticated and ready");
        if (!transientTeams.isEmpty()) return List.copyOf(transientTeams);
        List<ChampionshipTeam> created = new ArrayList<>();
        try {
            for (int index = 0; index < TestRoster.TEAM_COUNT; index++) {
                int teamNumber = index + 1;
                created.add(
                        plugin.getTeamManager()
                                .createTransientTeam(
                                        "cct"
                                                + active.sessionId().toString().substring(0, 8)
                                                + teamNumber,
                                        "Test Team " + teamNumber,
                                        TEAM_COLORS[index],
                                        TEAM_CODES[index],
                                        Set.copyOf(active.roster().teamMembers(teamNumber))));
            }
            transientTeams = List.copyOf(created);
            return List.copyOf(transientTeams);
        } catch (RuntimeException failure) {
            plugin.getTeamManager().removeTransientTeams(created);
            throw failure;
        }
    }

    /** Starts the frozen single-game plan through GameManager with TEST settlement isolation. */
    public synchronized CompletionStage<GameStartService.Result> startPlannedGame() {
        requireEnabled();
        if (plugin == null) throw new IllegalStateException("test session requires a Core plugin");
        if (active == null) throw new IllegalStateException("no test session");
        if (active.state() != TestSessionState.READY)
            throw new IllegalStateException("test session is not ready");
        if (active.plan() == null) throw new IllegalStateException("test session has no game plan");
        requireOnlineRoster();
        if (active.mode() == TestSessionMode.EVENT) {
            if (active.eventPlan() == null)
                throw new IllegalStateException("test session has no event plan");
            TestSession session = active;
            materializeTransientTeams();
            session.start();
            eventOrchestrator = new TestEventOrchestrator(this, session.eventPlan());
            return startEventRound(eventOrchestrator, session, 0);
        }
        TestSession session = active;
        materializeTransientTeams();
        GameTypeEnum game = GameTypeEnum.fromCommand(session.plan().target());
        if (game == null)
            throw new IllegalArgumentException("unknown test game: " + session.plan().target());
        GameStartArguments arguments =
                new GameStartArguments(game, session.plan().map(), List.of(), ArenaSelection.all());
        session.start();
        eventOrchestrator =
                new TestEventOrchestrator(
                        this,
                        new TestEventPlan(
                                "single",
                                List.of(
                                        new TestEventPlan.Round(
                                                game.commandName(), session.plan().map()))));
        TestEventOrchestrator orchestrator = eventOrchestrator;
        CompletionStage<GameStartService.Result> result =
                new GameStartService(plugin)
                        .startManual(
                                arguments,
                                GameRunMode.TEST,
                                transientTeams,
                                session.plan().variant());
        pendingStart = result;
        result.whenComplete(
                (outcome, failure) ->
                        finishStart(
                                session, orchestrator, 0, GameFlow.STANDALONE, outcome, failure));
        return result;
    }

    private void requireOnlineRoster() {
        for (TestRoster.SeatSnapshot seat : active.roster().snapshot()) {
            Player player =
                    seat.occupantUuid() == null ? null : Bukkit.getPlayer(seat.occupantUuid());
            if (player == null || !player.isOnline())
                throw new IllegalStateException(
                        "test roster member is offline: " + seat.occupantName());
        }
    }

    private synchronized CompletionStage<GameStartService.Result> startEventRound(
            TestEventOrchestrator orchestrator, TestSession session, int index) {
        if (active != session || eventOrchestrator != orchestrator)
            throw new IllegalStateException("test event session is no longer active");
        if (session.state() != TestSessionState.RUNNING)
            throw new IllegalStateException("test event is not running");
        TestEventPlan.Round round = orchestrator.plan().rounds().get(index);
        GameTypeEnum game = GameTypeEnum.fromCommand(round.game());
        GameStartArguments arguments =
                new GameStartArguments(game, round.map(), List.of(), ArenaSelection.all());
        CompletionStage<GameStartService.Result> result =
                new GameStartService(plugin)
                        .startManual(
                                arguments, GameRunMode.TEST_EVENT, transientTeams, round.variant());
        pendingStart = result;
        result.whenComplete(
                (outcome, failure) ->
                        finishStart(
                                session,
                                orchestrator,
                                index,
                                GameFlow.TEST_EVENT,
                                outcome,
                                failure));
        return result;
    }

    /** A late or malformed start result cannot revive a stopped round or strand RUNNING state. */
    synchronized void finishStart(
            TestSession session,
            TestEventOrchestrator orchestrator,
            int index,
            GameFlow flow,
            GameStartService.Result outcome,
            Throwable failure) {
        if (active != session
                || eventOrchestrator != orchestrator
                || session.state() != TestSessionState.RUNNING) return;
        try {
            if (failure != null || outcome == null || !outcome.started()) {
                session.fail();
                cleanupTransientTeamsAfterFailedStart();
                return;
            }
            List<BaseGameInstance> instances = applyOwnerContext(session, flow);
            orchestrator.markRoundStarted(index, instances);
            instances.forEach(
                    instance ->
                            instance.setTestCompletionListener(
                                    finished -> orchestrator.onRoundComplete(index, finished)));
        } catch (RuntimeException callbackFailure) {
            session.fail();
            cleanupTransientTeamsAfterFailedStart();
            plugin.getLogger()
                    .log(
                            java.util.logging.Level.WARNING,
                            "Test start ownership could not be established",
                            callbackFailure);
        }
    }

    CompletionStage<GameStartService.Result> startNextEventRound(
            TestEventOrchestrator orchestrator, int index) {
        synchronized (this) {
            requireEnabled();
            if (active == null) throw new IllegalStateException("no test session");
            return startEventRound(orchestrator, active, index);
        }
    }

    void scheduleNextEventRound(TestEventOrchestrator orchestrator, int index) {
        if (plugin == null || !plugin.isEnabled()) return;
        CompletionStage<Void> released =
                plugin.getRemoteBingoManager() == null
                        ? CompletableFuture.completedFuture(null)
                        : plugin.getRemoteBingoManager().awaitTestResources();
        released.whenComplete(
                (ignored, releaseFailure) -> {
                    if (releaseFailure != null) {
                        synchronized (TestSessionManager.this) {
                            if (eventOrchestrator == orchestrator && active != null) {
                                active.fail();
                                cleanupTransientTeamsAfterFailedStart();
                            }
                        }
                        return;
                    }
                    if (!plugin.isEnabled()) return;
                    try {
                        plugin.getServer()
                                .getScheduler()
                                .runTaskLater(
                                        plugin,
                                        () -> {
                                            try {
                                                awaitNextRoundPlayers(
                                                        orchestrator,
                                                        index,
                                                        System.nanoTime()
                                                                + java.util.concurrent.TimeUnit
                                                                        .SECONDS
                                                                        .toNanos(
                                                                                Math.max(
                                                                                        60,
                                                                                        ink.ziip
                                                                                                .championshipscore
                                                                                                .configuration
                                                                                                .config
                                                                                                .CCConfig
                                                                                                .BINGO_ARRIVAL_TIMEOUT_SECONDS)));
                                            } catch (RuntimeException failure) {
                                                synchronized (TestSessionManager.this) {
                                                    if (eventOrchestrator == orchestrator
                                                            && active != null) {
                                                        active.fail();
                                                        cleanupTransientTeamsAfterFailedStart();
                                                    }
                                                }
                                            }
                                        },
                                        220L);
                    } catch (RuntimeException schedulingFailure) {
                        synchronized (TestSessionManager.this) {
                            if (eventOrchestrator == orchestrator && active != null) {
                                active.fail();
                                cleanupTransientTeamsAfterFailedStart();
                            }
                        }
                    }
                });
    }

    /** A full roster is required on every test round, including asynchronous proxy returns. */
    private synchronized void awaitNextRoundPlayers(
            TestEventOrchestrator orchestrator, int index, long deadline) {
        if (active == null
                || eventOrchestrator != orchestrator
                || active.state() != TestSessionState.RUNNING) return;
        if (plugin == null || !plugin.isEnabled()) return;
        try {
            if (isRosterReadyForNextRound()) {
                startNextEventRound(orchestrator, index);
                return;
            }
            if (System.nanoTime() >= deadline) {
                active.fail();
                cleanupTransientTeamsAfterFailedStart();
                plugin.getLogger()
                        .warning("Test round did not regain all 64 authenticated connections");
                return;
            }
            plugin.getServer()
                    .getScheduler()
                    .runTaskLater(
                            plugin,
                            () -> awaitNextRoundPlayers(orchestrator, index, deadline),
                            20L);
        } catch (RuntimeException failure) {
            active.fail();
            cleanupTransientTeamsAfterFailedStart();
            plugin.getLogger()
                    .log(java.util.logging.Level.WARNING, "Test round admission failed", failure);
        }
    }

    synchronized boolean isRosterReadyForNextRound() {
        if (active == null) return false;
        for (var seat : active.roster().snapshot()) {
            Player player =
                    seat.occupantUuid() == null ? null : Bukkit.getPlayer(seat.occupantUuid());
            if (player == null || !player.isOnline()) return false;
            if (seat.occupantType() == TestRoster.OccupantType.BOT
                    && (!hasUsableReconnectLease(seat.botUsername(), seat.occupantUuid())
                            || !isAuthenticatedTestIdentity(
                                    seat.botUsername(), seat.occupantUuid()))) return false;
        }
        return true;
    }

    void completeEvent(TestEventOrchestrator orchestrator) {
        synchronized (this) {
            if (active == null
                    || active.state() != TestSessionState.RUNNING
                    || eventOrchestrator != orchestrator) return;
            active.complete();
        }
    }

    /** Marks a running test failed after its owned runtime instance is aborted remotely. */
    public synchronized void failOwnedSession(GameExecutionContext context) {
        if (context == null || context.ownerSessionId() == null || active == null) return;
        if (!active.sessionId().equals(context.ownerSessionId())
                || active.generation() != context.generation()
                || active.state() != TestSessionState.RUNNING) return;
        active.fail();
        cleanupTransientTeamsAfterFailedStart();
    }

    private List<BaseGameInstance> applyOwnerContext(TestSession session, GameFlow flow) {
        GameExecutionContext context =
                new GameExecutionContext(
                        session.mode() == TestSessionMode.EVENT
                                ? GameRunMode.TEST_EVENT
                                : GameRunMode.TEST,
                        flow,
                        SettlementPolicy.TEST,
                        session.sessionId(),
                        session.generation());
        ownedInstances =
                transientTeams.stream()
                        .map(plugin.getGameManager()::getTeamCurrenArea)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList();
        ownedInstances.forEach(instance -> instance.applyExecutionContext(context));
        return ownedInstances;
    }

    private void cleanupTransientTeamsAfterFailedStart() {
        if (active != null) beginCleanup(active, false);
    }

    /** Revoke admission immediately; retain ownership until all restores have finished. */
    public synchronized void closeActiveSession() {
        closeActiveSessionAsync();
    }

    public synchronized CompletionStage<Void> closeActiveSessionAsync() {
        cancelWebLeaseRenewal();
        if (active == null) {
            if (cleanupOwner != null && cleanupFuture.isCompletedExceptionally()) {
                TestSession retry = cleanupOwner;
                cleanupOwner = null;
                return beginCleanup(retry, true);
            }
            return cleanupFuture;
        }
        TestSession session = active;
        if (!session.state().terminal()) session.requestStop();
        active = null;
        if (cleanupOwner == session && cleanupFuture.isCompletedExceptionally())
            cleanupOwner = null;
        return beginCleanup(session, true);
    }

    public synchronized boolean isCleaningUp() {
        return cleanupOwner != null;
    }

    private CompletionStage<Void> beginCleanup(TestSession session, boolean close) {
        if (cleanupOwner != null) {
            if (cleanupOwner == session && close) cleanupCloseRequested = true;
            return cleanupFuture;
        }
        cleanupOwner = session;
        cleanupCloseRequested = close;
        cleanupFuture = new CompletableFuture<>();
        CompletableFuture<Void> result = cleanupFuture;
        List<ChampionshipTeam> teams = List.copyOf(transientTeams);
        Set<UUID> identities = Set.copyOf(admittedBotUuids);
        if (eventOrchestrator != null) eventOrchestrator.cancel();
        eventOrchestrator = null;
        materializationRequest = null;
        session.identities().revokeSession(session.sessionId());
        // During Bukkit shutdown the plugin is already disabled before onDisable() runs.  An
        // unfinished start cannot be scheduled or completed through this plugin anymore; its
        // ownership is still revoked below and the Worker release remains the cleanup gate.
        CompletionStage<?> start =
                unloading || (plugin != null && !plugin.isEnabled())
                        ? CompletableFuture.completedFuture(null)
                        : (pendingStart == null
                                ? CompletableFuture.completedFuture(null)
                                : pendingStart);
        start.handle((ignored, failure) -> null)
                .thenCompose(
                        ignored ->
                                onServerThread(
                                        () -> {
                                            if (plugin != null)
                                                identities.forEach(
                                                        uuid -> {
                                                            Player bot = Bukkit.getPlayer(uuid);
                                                            if (bot != null)
                                                                bot.kick(
                                                                        net.kyori.adventure.text
                                                                                .Component.text(
                                                                                "Test session"
                                                                                    + " stopped."));
                                                        });
                                            Set<BaseGameInstance> instances =
                                                    new LinkedHashSet<>(ownedInstances);
                                            if (plugin != null)
                                                teams.stream()
                                                        .map(
                                                                plugin.getGameManager()
                                                                        ::getTeamCurrenArea)
                                                        .filter(Objects::nonNull)
                                                        .forEach(instances::add);
                                            List<CompletableFuture<Boolean>> restores =
                                                    new ArrayList<>();
                                            for (BaseGameInstance instance : instances) {
                                                instance.setTestCompletionListener(null);
                                                try {
                                                    if (!(instance
                                                                    instanceof
                                                                    ink.ziip.championshipscore.api
                                                                            .game.bingo.execution
                                                                            .RemoteBingoInstance)
                                                            && !instance.isDisposed())
                                                        restores.add(instance.abortAndReset());
                                                } catch (RuntimeException failure) {
                                                    restores.add(
                                                            CompletableFuture.failedFuture(
                                                                    failure));
                                                }
                                            }
                                            if (plugin != null
                                                    && plugin.getRemoteBingoManager() != null)
                                                restores.add(
                                                        plugin.getRemoteBingoManager()
                                                                .stopTestMatches()
                                                                .toCompletableFuture());
                                            return CompletableFuture.allOf(
                                                            restores.toArray(
                                                                    CompletableFuture[]::new))
                                                    .thenApply(
                                                            done -> {
                                                                if (restores.stream()
                                                                        .anyMatch(
                                                                                future ->
                                                                                        !Boolean
                                                                                                .TRUE
                                                                                                .equals(
                                                                                                        future
                                                                                                                .join())))
                                                                    throw new CompletionException(
                                                                            new IllegalStateException(
                                                                                    "test arena"
                                                                                        + " restore"
                                                                                        + " failed"));
                                                                return null;
                                                            });
                                        }))
                .thenCompose(
                        ignored -> {
                            if (unloading || (plugin != null && !plugin.isEnabled())) {
                                synchronized (TestSessionManager.this) {
                                    if (cleanupOwner == session) {
                                        transientTeams = List.of();
                                        ownedInstances = List.of();
                                        admittedBotUuids.removeAll(identities);
                                        pendingStart = null;
                                        cleanupOwner = null;
                                        if (cleanupCloseRequested
                                                && session.state() != TestSessionState.CLOSED)
                                            session.close();
                                    }
                                }
                                return CompletableFuture.completedFuture(null);
                            }
                            return onServerThread(
                                    () -> {
                                        synchronized (TestSessionManager.this) {
                                            if (cleanupOwner != session)
                                                return CompletableFuture.completedFuture(null);
                                            if (plugin != null) {
                                                plugin.getTeamManager().removeTransientTeams(teams);
                                                if (teams.stream()
                                                        .anyMatch(
                                                                plugin.getTeamManager()
                                                                        ::isTransientTeam))
                                                    throw new IllegalStateException(
                                                            "test teams were not completely"
                                                                    + " released");
                                                if (plugin.getPlayerManager() != null)
                                                    identities.forEach(
                                                            plugin.getPlayerManager()
                                                                    ::removeEphemeralTestIdentity);
                                            }
                                            if (plugin != null) recoverOnlineDeveloperStates(false);
                                            transientTeams = List.of();
                                            ownedInstances = List.of();
                                            admittedBotUuids.removeAll(identities);
                                            pendingStart = null;
                                            cleanupOwner = null;
                                            if (cleanupCloseRequested
                                                    && session.state() != TestSessionState.CLOSED)
                                                session.close();
                                            if (plugin != null)
                                                session.roster().snapshot().stream()
                                                        .filter(
                                                                seat ->
                                                                        seat.occupantType()
                                                                                == TestRoster
                                                                                        .OccupantType
                                                                                        .DEVELOPER)
                                                        .map(TestRoster.SeatSnapshot::occupantUuid)
                                                        .map(Bukkit::getPlayer)
                                                        .filter(Objects::nonNull)
                                                        .forEach(this::refreshDailyLobby);
                                        }
                                        return CompletableFuture.completedFuture(null);
                                    });
                        })
                .whenComplete(
                        (ignored, failure) -> {
                            if (failure == null) result.complete(null);
                            else {
                                // Fail closed: a failed arena restore must not admit a new session.
                                result.completeExceptionally(failure);
                                if (plugin != null)
                                    plugin.getLogger()
                                            .log(
                                                    java.util.logging.Level.WARNING,
                                                    "Test session cleanup incomplete; new sessions"
                                                            + " remain blocked",
                                                    failure);
                            }
                        });
        return result;
    }

    private <T> CompletionStage<T> onServerThread(Supplier<CompletionStage<T>> operation) {
        if (plugin == null || Bukkit.isPrimaryThread()) {
            try {
                return operation.get();
            } catch (RuntimeException failure) {
                return CompletableFuture.failedFuture(failure);
            }
        }
        CompletableFuture<T> result = new CompletableFuture<>();
        try {
            plugin.getServer()
                    .getScheduler()
                    .runTask(
                            plugin,
                            () -> {
                                try {
                                    operation
                                            .get()
                                            .whenComplete(
                                                    (value, failure) -> {
                                                        if (failure == null) result.complete(value);
                                                        else result.completeExceptionally(failure);
                                                    });
                                } catch (RuntimeException failure) {
                                    result.completeExceptionally(failure);
                                }
                            });
        } catch (RuntimeException failure) {
            result.completeExceptionally(failure);
        }
        return result;
    }

    private void maybeReadyAndMaterialize(TestSession session) {
        if (!isEnabled() || session != active) return;
        maybeMarkReady(session);
        if (session.state() != TestSessionState.READY
                || !transientTeams.isEmpty()
                || plugin == null
                || materializationRequest != null) return;
        Object request = new Object();
        materializationRequest = request;
        long generation = session.generation();
        try {
            plugin.getServer()
                    .getScheduler()
                    .runTaskLater(
                            plugin,
                            () -> {
                                synchronized (TestSessionManager.this) {
                                    if (materializationRequest != request) return;
                                    materializationRequest = null;
                                    if (isEnabled()
                                            && active == session
                                            && session.isCurrent(generation)
                                            && session.state() == TestSessionState.READY)
                                        materializeTransientTeams();
                                }
                            },
                            2L);
        } catch (RuntimeException schedulingFailure) {
            if (materializationRequest == request) materializationRequest = null;
            if (active == session && session.state() == TestSessionState.READY) {
                session.fail();
                cleanupTransientTeamsAfterFailedStart();
            }
            if (plugin != null)
                plugin.getLogger()
                        .log(
                                java.util.logging.Level.WARNING,
                                "Test session materialization could not be scheduled",
                                schedulingFailure);
        }
    }

    private void maybeMarkReady(TestSession session) {
        if (session.state() != TestSessionState.AUTHENTICATING || !session.roster().isComplete())
            return;
        for (TestRoster.SeatSnapshot seat : session.roster().snapshot()) {
            if (seat.occupantType() == TestRoster.OccupantType.BOT
                    && !session.identities()
                            .isAuthenticated(seat.botUsername(), seat.occupantUuid())) return;
        }
        session.markReady();
    }
}
