package ink.ziip.championshipscore.authproxy;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Keeps Bungee responsible for disconnecting already connected banned players. */
final class ProxyBanSynchronizer implements Runnable {
    @FunctionalInterface
    interface BanKick {
        void kick(String username, String reason, String expiresAt);
    }

    private final ProxyIdentityClient client;
    private final ProxyAccessState state;
    private final BanKick kickBannedPlayer;
    private final Logger logger;
    private final AtomicBoolean running = new AtomicBoolean();
    private int consecutiveFailures;
    private long lastUnavailableLogAt;

    ProxyBanSynchronizer(ProxyIdentityClient client, ProxyAccessState state,
                         BanKick kickBannedPlayer, Logger logger) {
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
            consecutiveFailures = 0;
        } catch (Exception exception) {
            logFailure(exception);
        } finally {
            running.set(false);
        }
    }

    private void logFailure(Exception failure) {
        consecutiveFailures++;
        if (!ProxyIdentityClient.isServiceUnavailable(failure)) {
            logger.log(Level.WARNING, "Could not synchronize proxy ban state", failure);
            return;
        }
        long now = System.currentTimeMillis();
        if (consecutiveFailures == 1 || now - lastUnavailableLogAt >= 60_000L) {
            lastUnavailableLogAt = now;
            logger.warning("Auth proxy web service unavailable; retrying (attempt "
                    + consecutiveFailures + ")");
        }
    }

    private void bootstrap() throws Exception {
        ProxyIdentityClient.ProxyBanSnapshot snapshot = client.banSnapshot();
        if (snapshot == null || snapshot.maintenance == null || snapshot.bans == null) {
            throw new IllegalStateException("Bridge proxy access snapshot is incomplete");
        }
        state.replaceSnapshot(snapshot, Instant.now());
        for (ProxyIdentityClient.ProxyBan ban : snapshot.bans) {
            if (ban == null || !isActive(ban.expiresAt)) continue;
            kick(requireUsername(ban.username), ban.reason, ban.expiresAt);
        }
    }

    private void applyChanges() throws Exception {
        ProxyIdentityClient.ProxyChangeBatch batch = client.changesAfter(state.cursor());
        if (batch == null || batch.maintenance == null || batch.changes == null) {
            throw new IllegalStateException("Bridge proxy change batch is incomplete");
        }
        state.applyChanges(batch, Instant.now());
        for (ProxyIdentityClient.ProxyChange change : batch.changes) {
            boolean banned = change != null && (change.status == null
                    ? "BANNED".equals(change.operation)
                    : "BANNED".equals(change.status));
            if (banned && isActive(change.expiresAt)
                    && isMinecraftUsername(change.authmeUsername)) {
                kick(change.authmeUsername, change.reason, change.expiresAt);
            }
        }
    }

    private void kick(String username, String reason, String expiresAt) {
        kickBannedPlayer.kick(username, reason == null ? "" : reason, expiresAt);
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

}
