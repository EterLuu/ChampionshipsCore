package ink.ziip.championshipscore.authbridge;

import fr.xephi.authme.events.LoginEvent;
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

    public AccessListener(LocalAccessState state, boolean failClosed, String maintenanceMessage,
                          String unavailableMessage,
                          String uuidMismatchMessage) {
        this.state = state;
        this.failClosed = failClosed;
        this.maintenanceMessage = maintenanceMessage;
        this.unavailableMessage = unavailableMessage;
        this.uuidMismatchMessage = uuidMismatchMessage;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) return;
        AccessDecision decision = accessDecision(event.getName(), event.getUniqueId());
        if (decision.result() == AccessResult.MAINTENANCE) {
            event.disallow(
                    AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    BridgeText.component(decision.message())
            );
        } else if (decision.result() == AccessResult.UUID_MISMATCH
                || decision.result() == AccessResult.BRIDGE_UNAVAILABLE) {
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
        if (!state.synchronizedOnce()) {
            if (!failClosed) return new AccessDecision(AccessResult.ALLOWED, "");
            return new AccessDecision(AccessResult.BRIDGE_UNAVAILABLE, unavailableMessage);
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
        MAINTENANCE,
        UUID_MISMATCH,
        BRIDGE_UNAVAILABLE
    }

    record AccessDecision(AccessResult result, String message) {
    }
}
