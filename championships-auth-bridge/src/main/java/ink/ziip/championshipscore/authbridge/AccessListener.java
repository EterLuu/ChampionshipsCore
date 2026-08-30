package ink.ziip.championshipscore.authbridge;

import fr.xephi.authme.events.LoginEvent;
import ink.ziip.championshipscore.auth.AuthAdmissionOwner;
import ink.ziip.championshipscore.authbridge.bridge.LocalAccessState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

public final class AccessListener implements Listener {
    private final LocalAccessState state;
    private final boolean failClosed;
    private final String maintenanceMessage;
    private final String unavailableMessage;
    private final String uuidMismatchMessage;
    private final String unboundMessage;
    private final String bannedMessage;
    private final AuthAdmissionOwner admissionOwner;
    private final boolean legacySynchronizationOnly;

    public AccessListener(LocalAccessState state, boolean failClosed, String maintenanceMessage,
                          String unavailableMessage,
                          String uuidMismatchMessage) {
        this(state, failClosed, maintenanceMessage, unavailableMessage, uuidMismatchMessage,
                "&#ff6b26你还没有绑定 Minecraft 账号。", "&#ff6b26你已被服务器封禁。",
                null);
    }

    public AccessListener(LocalAccessState state, boolean failClosed, String maintenanceMessage,
                          String unavailableMessage, String uuidMismatchMessage,
                          String unboundMessage, String bannedMessage,
                          AuthAdmissionOwner admissionOwner) {
        this.state = state;
        this.failClosed = failClosed;
        this.maintenanceMessage = maintenanceMessage;
        this.unavailableMessage = unavailableMessage;
        this.uuidMismatchMessage = uuidMismatchMessage;
        this.unboundMessage = unboundMessage;
        this.bannedMessage = bannedMessage;
        this.legacySynchronizationOnly = admissionOwner == null;
        this.admissionOwner = admissionOwner == null ? AuthAdmissionOwner.PROXY : admissionOwner;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) return;
        AccessDecision decision = accessDecision(event.getName(), event.getUniqueId());
        if (decision.result() != AccessResult.ALLOWED) {
            event.disallow(
                    AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    BridgeText.component(decision.message())
            );
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAuthMeLogin(LoginEvent event) {
        AccessDecision decision = accessDecision(event.getPlayer().getName(), event.getPlayer().getUniqueId());
        if (decision.result() != AccessResult.ALLOWED)
            event.getPlayer().kick(BridgeText.component(decision.message()));
    }

    AccessDecision accessDecision(String username) {
        if (state.maintenanceInProgress()) return new AccessDecision(AccessResult.MAINTENANCE, maintenanceMessage);
        if (admissionOwner == AuthAdmissionOwner.BRIDGE) {
            LocalAccessState.Ban ban = state.activeBan(username, java.time.Instant.now());
            if (ban != null) return new AccessDecision(AccessResult.BANNED, replaceBanPlaceholders(bannedMessage, ban));
        }
        if (legacySynchronizationOnly || admissionOwner == AuthAdmissionOwner.BRIDGE) {
            if (!state.synchronizedOnce()) {
                if (failClosed) return new AccessDecision(AccessResult.BRIDGE_UNAVAILABLE, unavailableMessage);
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

    record AccessDecision(AccessResult result, String message) {
    }

    private static String replaceBanPlaceholders(String template, LocalAccessState.Ban ban) {
        return (template == null ? "" : template)
                .replace("%reason%", ban.reason() == null || ban.reason().isBlank() ? "违反服务器规则" : ban.reason())
                .replace("%expires%", ban.expiresAt() == null || ban.expiresAt().isBlank()
                        ? "请查看账号页面" : ban.expiresAt());
    }
}
