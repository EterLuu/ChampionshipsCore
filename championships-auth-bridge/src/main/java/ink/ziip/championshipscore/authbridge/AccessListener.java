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

    public AccessListener(LocalAccessState state, boolean failClosed, String notWhitelistedMessage, String bannedMessage) {
        this.state = state;
        this.failClosed = failClosed;
        this.notWhitelistedMessage = notWhitelistedMessage;
        this.bannedMessage = bannedMessage;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        var ban = state.activeBan(event.getName());
        if (ban != null) event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, Component.text(messageForBan(ban)));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAuthMeLogin(LoginEvent event) {
        String username = event.getPlayer().getName();
        var ban = state.activeBan(username);
        if (ban != null) {
            event.getPlayer().kick(Component.text(messageForBan(ban)));
            return;
        }
        if ((!state.synchronizedOnce() && failClosed) || !state.isWhitelisted(username)) {
            event.getPlayer().kick(Component.text(notWhitelistedMessage));
        }
    }

    private String messageForBan(LocalAccessState.Ban ban) {
        return ban.reason().isBlank() ? bannedMessage : bannedMessage + "\n" + ban.reason();
    }
}
