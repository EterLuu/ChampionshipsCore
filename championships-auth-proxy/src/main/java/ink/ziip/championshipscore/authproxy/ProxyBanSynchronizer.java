package ink.ziip.championshipscore.authproxy;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Keeps Bungee responsible for disconnecting already connected banned players. */
final class ProxyBanSynchronizer implements Runnable {
    private final ProxyIdentityClient client;
    private final ProxyBanState state;
    private final BiConsumer<String, String> kickBannedPlayer;
    private final Logger logger;
    private final AtomicBoolean running = new AtomicBoolean();

    ProxyBanSynchronizer(ProxyIdentityClient client, ProxyBanState state,
                         BiConsumer<String, String> kickBannedPlayer, Logger logger) {
        this.client = client;
        this.state = state;
        this.kickBannedPlayer = kickBannedPlayer;
        this.logger = logger;
    }

    @Override
    public void run() {
        if (!running.compareAndSet(false, true)) return;
        try {
            if (!state.initialized()) bootstrap();
            else applyChanges();
        } catch (Exception exception) {
            logger.log(Level.WARNING, "Could not synchronize proxy ban state", exception);
        } finally {
            running.set(false);
        }
    }

    private void bootstrap() throws Exception {
        ProxyIdentityClient.ProxyBanSnapshot snapshot = client.banSnapshot();
        if (snapshot == null || snapshot.bans == null) throw new IllegalStateException("Bridge proxy ban snapshot is incomplete");
        for (ProxyIdentityClient.ProxyBan ban : snapshot.bans) {
            if (ban == null || !isActive(ban.expiresAt)) continue;
            kick(requireUsername(ban.username), ban.reason);
        }
        state.advance(requireCursor(snapshot.nextCursor));
    }

    private void applyChanges() throws Exception {
        ProxyIdentityClient.ProxyChangeBatch batch = client.changesAfter(state.cursor());
        if (batch == null || batch.changes == null) throw new IllegalStateException("Bridge change batch is incomplete");
        for (ProxyIdentityClient.ProxyChange change : batch.changes) {
            if (change != null && "BANNED".equals(change.operation) && isActive(change.expiresAt)
                    && isMinecraftUsername(change.authmeUsername)) {
                kick(change.authmeUsername, change.reason);
            }
        }
        state.advance(requireCursor(batch.nextCursor));
    }

    private void kick(String username, String reason) {
        kickBannedPlayer.accept(username, reason == null ? "" : reason);
    }

    private static boolean isActive(String expiresAt) {
        if (expiresAt == null || expiresAt.isBlank()) return true;
        try {
            return Instant.parse(expiresAt).isAfter(Instant.now());
        } catch (DateTimeParseException exception) {
            return false;
        }
    }

    private static String requireUsername(String username) {
        if (!isMinecraftUsername(username)) {
            throw new IllegalArgumentException("Invalid Minecraft username in bridge response");
        }
        return username;
    }

    private static boolean isMinecraftUsername(String username) {
        return username != null && username.matches("^[A-Za-z0-9_]{3,16}$");
    }

    private static String requireCursor(String cursor) {
        if (cursor == null || !cursor.matches("^\\d{1,19}$")) {
            throw new IllegalArgumentException("Invalid bridge cursor in response");
        }
        return cursor;
    }
}
