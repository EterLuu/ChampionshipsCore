package ink.ziip.championshipscore.authbridge;

import fr.xephi.authme.events.LoginEvent;
import ink.ziip.championshipscore.authbridge.bridge.LocalAccessState;
import net.kyori.adventure.text.Component;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

public final class AccessListener implements Listener {
    private final LocalAccessState state;
    private final boolean failClosed;
    private final String notWhitelistedMessage;
    private final String bannedMessage;
    private final String maintenanceMessage;
    private final String unavailableMessage;
    private final String uuidMismatchMessage;

    public AccessListener(LocalAccessState state, boolean failClosed, String notWhitelistedMessage,
                          String bannedMessage, String maintenanceMessage, String unavailableMessage,
                          String uuidMismatchMessage) {
        this.state = state;
        this.failClosed = failClosed;
        this.notWhitelistedMessage = notWhitelistedMessage;
        this.bannedMessage = bannedMessage;
        this.maintenanceMessage = maintenanceMessage;
        this.unavailableMessage = unavailableMessage;
        this.uuidMismatchMessage = uuidMismatchMessage;
    }

    public AccessListener(LocalAccessState state, boolean failClosed, String notWhitelistedMessage,
                          String bannedMessage, String maintenanceMessage, String uuidMismatchMessage) {
        this(state, failClosed, notWhitelistedMessage, bannedMessage, maintenanceMessage,
                "账号服务暂时不可用，请稍后重新连接。", uuidMismatchMessage);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) return;
        AccessDecision decision = accessDecision(event.getName(), event.getUniqueId());
        if (decision.result() == AccessResult.NOT_WHITELISTED || decision.result() == AccessResult.MAINTENANCE) {
            event.disallow(
                    AsyncPlayerPreLoginEvent.Result.KICK_WHITELIST,
                    Component.text(decision.message())
            );
        } else if (decision.result() == AccessResult.BANNED) {
            event.disallow(
                    AsyncPlayerPreLoginEvent.Result.KICK_BANNED,
                    Component.text(decision.message())
            );
        } else if (decision.result() == AccessResult.UUID_MISMATCH
                || decision.result() == AccessResult.BRIDGE_UNAVAILABLE) {
            event.disallow(
                    AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    Component.text(decision.message())
            );
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAuthMeLogin(LoginEvent event) {
        AccessDecision decision = accessDecision(event.getPlayer().getName(), event.getPlayer().getUniqueId());
        if (decision.result() != AccessResult.ALLOWED)
            event.getPlayer().kick(Component.text(decision.message()));
    }

    AccessDecision accessDecision(String username) {
        var ban = state.activeBan(username);
        if (ban != null) return new AccessDecision(AccessResult.BANNED, messageForBan(ban));
        if (state.maintenanceInProgress()) return new AccessDecision(AccessResult.MAINTENANCE, maintenanceMessage);
        if (!state.synchronizedOnce()) {
            if (!failClosed) return new AccessDecision(AccessResult.ALLOWED, "");
            return new AccessDecision(AccessResult.BRIDGE_UNAVAILABLE, unavailableMessage);
        }
        if (!state.isWhitelisted(username))
            return new AccessDecision(AccessResult.NOT_WHITELISTED, notWhitelistedMessage);
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

    private String messageForBan(LocalAccessState.Ban ban) {
        return ban.reason().isBlank() ? bannedMessage : bannedMessage + "\n" + ban.reason();
    }

    enum AccessResult {
        ALLOWED,
        NOT_WHITELISTED,
        MAINTENANCE,
        BANNED,
        UUID_MISMATCH,
        BRIDGE_UNAVAILABLE
    }

    record AccessDecision(AccessResult result, String message) {
    }
}
