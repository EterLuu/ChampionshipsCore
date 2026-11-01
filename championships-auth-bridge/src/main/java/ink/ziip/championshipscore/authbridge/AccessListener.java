package ink.ziip.championshipscore.authbridge;

import fr.xephi.authme.events.LoginEvent;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.test.TestSessionManager;
import ink.ziip.championshipscore.auth.AuthAdmissionOwner;
import ink.ziip.championshipscore.auth.BanPresentation;
import ink.ziip.championshipscore.auth.BotLoginPolicy;
import ink.ziip.championshipscore.authbridge.bridge.LocalAccessState;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

public final class AccessListener implements Listener {
    private static final int TEST_AUTH_CONFIRM_RETRY_LIMIT = 600;
    private static final long TEST_AUTH_CONFIRM_RETRY_TICKS = 5L;
    private static final long TEST_AUTH_CONFIRM_TIMEOUT_NANOS = 120_000_000_000L;
    private final LocalAccessState state;
    private final boolean failClosed;
    private final String maintenanceMessage;
    private final String unavailableMessage;
    private final String uuidMismatchMessage;
    private final String unboundMessage;
    private final BanPresentation banPresentation;
    private final AuthAdmissionOwner admissionOwner;
    private final BotLoginPolicy botLoginPolicy;

    /** Compatibility constructor for non-Bot bridge tests and older embedders. */
    public AccessListener(
            LocalAccessState state,
            boolean failClosed,
            String maintenanceMessage,
            String unavailableMessage,
            String uuidMismatchMessage,
            String unboundMessage,
            BanPresentation banPresentation,
            AuthAdmissionOwner admissionOwner) {
        this(
                state,
                failClosed,
                maintenanceMessage,
                unavailableMessage,
                uuidMismatchMessage,
                unboundMessage,
                banPresentation,
                admissionOwner,
                BotLoginPolicy.of("__bot__", java.util.List.of()));
    }

    public AccessListener(
            LocalAccessState state,
            boolean failClosed,
            String maintenanceMessage,
            String unavailableMessage,
            String uuidMismatchMessage,
            String unboundMessage,
            BanPresentation banPresentation,
            AuthAdmissionOwner admissionOwner,
            BotLoginPolicy botLoginPolicy) {
        this.state = state;
        this.failClosed = failClosed;
        this.maintenanceMessage = maintenanceMessage;
        this.unavailableMessage = unavailableMessage;
        this.uuidMismatchMessage = uuidMismatchMessage;
        this.unboundMessage = unboundMessage;
        this.banPresentation = java.util.Objects.requireNonNull(banPresentation, "banPresentation");
        this.admissionOwner = java.util.Objects.requireNonNull(admissionOwner, "admissionOwner");
        this.botLoginPolicy = java.util.Objects.requireNonNull(botLoginPolicy, "botLoginPolicy");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) return;
        if (!allowsBotLogin(event.getName(), event.getAddress())) {
            event.disallow(
                    AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    BridgeText.component("Bot source address is not allowed."));
            return;
        }
        AccessDecision decision = accessDecision(event.getName(), event.getUniqueId());
        if (decision.result() != AccessResult.ALLOWED) {
            event.disallow(
                    AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    BridgeText.component(decision.message()));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAuthMeLogin(LoginEvent event) {
        if (!allowsBotLogin(
                event.getPlayer().getName(),
                event.getPlayer().getAddress() == null
                        ? null
                        : event.getPlayer().getAddress().getAddress())) {
            event.getPlayer().kick(BridgeText.component("Bot source address is not allowed."));
            return;
        }
        AccessDecision decision =
                accessDecision(event.getPlayer().getName(), event.getPlayer().getUniqueId());
        if (decision.result() != AccessResult.ALLOWED)
            event.getPlayer().kick(BridgeText.component(decision.message()));
        else confirmCoreTestIdentity(event);
    }

    private boolean allowsBotLogin(String username, java.net.InetAddress address) {
        LocalAccessState.Identity identity = state.identity(username);
        return identity == null
                ? botLoginPolicy.allows(username, address)
                : botLoginPolicy.allows(username, address, identity.bot(), identity.allowedCidrs());
    }

    private void confirmCoreTestIdentity(LoginEvent event) {
        ChampionshipsCore core = ChampionshipsCore.getInstance();
        TestSessionManager tests = core == null ? null : core.getTestSessionManager();
        if (core == null
                || !core.isEnabled()
                || tests == null
                || !tests.hasTestLease(event.getPlayer().getName())) return;
        confirmCoreTestIdentity(
                core,
                tests,
                event.getPlayer(),
                0,
                System.nanoTime() + TEST_AUTH_CONFIRM_TIMEOUT_NANOS);
    }

    private void confirmCoreTestIdentity(
            ChampionshipsCore core,
            TestSessionManager tests,
            org.bukkit.entity.Player player,
            int attempt,
            long deadlineNanos) {
        if (!core.isEnabled() || !player.isOnline()) return;
        try {
            tests.confirmAuthMeLogin(
                    player.getName(), player.getUniqueId(), java.time.Instant.now());
            return;
        } catch (RuntimeException failure) {
            if (attempt == 0)
                core.getLogger()
                        .warning(
                                "Test AuthMe confirmation deferred for "
                                        + player.getName()
                                        + ": "
                                        + failure.getMessage());
            if (attempt >= TEST_AUTH_CONFIRM_RETRY_LIMIT || System.nanoTime() >= deadlineNanos) {
                player.kick(BridgeText.component("Test identity verification failed."));
                return;
            }
        }
        core.getServer()
                .getScheduler()
                .runTaskLater(
                        core,
                        () ->
                                confirmCoreTestIdentity(
                                        core, tests, player, attempt + 1, deadlineNanos),
                        TEST_AUTH_CONFIRM_RETRY_TICKS);
    }

    AccessDecision accessDecision(String username) {
        if (state.maintenanceInProgress())
            return new AccessDecision(AccessResult.MAINTENANCE, maintenanceMessage);
        if (admissionOwner == AuthAdmissionOwner.BRIDGE) {
            LocalAccessState.Ban ban = state.activeBan(username, java.time.Instant.now());
            if (ban != null)
                return new AccessDecision(
                        AccessResult.BANNED, banPresentation.render(ban.reason(), ban.expiresAt()));
        }
        if (admissionOwner == AuthAdmissionOwner.BRIDGE) {
            if (!state.synchronizedOnce()) {
                if (failClosed)
                    return new AccessDecision(AccessResult.BRIDGE_UNAVAILABLE, unavailableMessage);
                return new AccessDecision(AccessResult.ALLOWED, "");
            }
            if (admissionOwner == AuthAdmissionOwner.BRIDGE && !state.hasIdentity(username)) {
                return new AccessDecision(AccessResult.UNBOUND, unboundMessage);
            }
        }
        return new AccessDecision(AccessResult.ALLOWED, "");
    }

    AccessDecision accessDecision(String username, java.util.UUID actualUuid) {
        AccessDecision decision = accessDecision(username);
        if (decision.result() != AccessResult.ALLOWED) return decision;
        java.util.UUID expectedUuid = state.expectedUuid(username);
        if (expectedUuid != null && !expectedUuid.equals(actualUuid)) {
            return new AccessDecision(AccessResult.UUID_MISMATCH, uuidMismatchMessage);
        }
        return decision;
    }

    enum AccessResult {
        ALLOWED,
        UNBOUND,
        BANNED,
        MAINTENANCE,
        UUID_MISMATCH,
        BRIDGE_UNAVAILABLE
    }

    record AccessDecision(AccessResult result, String message) {}
}
