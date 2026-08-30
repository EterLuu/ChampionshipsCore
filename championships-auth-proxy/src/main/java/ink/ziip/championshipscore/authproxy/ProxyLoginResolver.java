package ink.ziip.championshipscore.authproxy;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Resolves live admission first and uses durable state only for transient cc-web outages. */
final class ProxyLoginResolver {
    private final ProxyIdentityClient client;
    private final ProxyAccessState state;
    private final boolean cacheEnabled;
    private final Duration maxStale;
    private final Logger logger;
    private final AtomicLong lastFallbackLogAt = new AtomicLong();
    private final AtomicLong lastWriteFailureLogAt = new AtomicLong();

    ProxyLoginResolver(ProxyIdentityClient client, ProxyAccessState state, boolean cacheEnabled,
                       Duration maxStale, Logger logger) {
        if (maxStale.isNegative()) throw new IllegalArgumentException("Offline cache maximum age must not be negative");
        this.client = client;
        this.state = state;
        this.cacheEnabled = cacheEnabled;
        this.maxStale = maxStale;
        this.logger = logger;
    }

    ProxyIdentityClient.LoginProfile lookup(String username) throws Exception {
        try {
            ProxyIdentityClient.LoginProfile live = client.lookup(username);
            if (cacheEnabled) persistLiveProfile(username, live);
            return live;
        } catch (Exception failure) {
            if (!cacheEnabled || !ProxyIdentityClient.isServiceUnavailable(failure)) throw failure;
            ProxyIdentityClient.LoginProfile cached = state.cachedProfile(username, maxStale, Instant.now());
            if (cached == null) throw failure;
            logFallback();
            return cached;
        }
    }

    private void persistLiveProfile(String username, ProxyIdentityClient.LoginProfile live) {
        try {
            state.recordLiveProfile(username, live, Instant.now());
        } catch (Exception failure) {
            if (claimLogWindow(lastWriteFailureLogAt)) {
                logger.log(Level.WARNING, "Could not persist auth proxy login cache", failure);
            }
        }
    }

    private void logFallback() {
        if (claimLogWindow(lastFallbackLogAt)) {
            logger.warning("cc-web is unavailable; using the persisted auth proxy login cache");
        }
    }

    private static boolean claimLogWindow(AtomicLong lastLogAt) {
        long now = System.currentTimeMillis();
        while (true) {
            long previous = lastLogAt.get();
            if (now - previous < 60_000L) return false;
            if (lastLogAt.compareAndSet(previous, now)) return true;
        }
    }
}
