package ink.ziip.championshipscore.api.test;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * In-memory lease registry for test identities.
 *
 * <p>The expected UUID is a check supplied by the trusted identity service. The observed UUID
 * supplied to {@link #bindObserved} is still the server-side value and is the only value that the
 * Core roster may use after a successful binding.
 */
public final class TestIdentityRegistry {
    public enum BindingState {
        LEASED,
        OBSERVED,
        AUTHENTICATED,
        REVOKED
    }

    public record Lease(
            UUID sessionId,
            long generation,
            String username,
            UUID expectedProfileUuid,
            Instant expiresAt) {
        public Lease {
            Objects.requireNonNull(sessionId, "sessionId");
            if (generation < 1) throw new IllegalArgumentException("generation must be positive");
            username = normalizeUsername(username);
            Objects.requireNonNull(expectedProfileUuid, "expectedProfileUuid");
            Objects.requireNonNull(expiresAt, "expiresAt");
        }
    }

    public record BindingSnapshot(
            UUID sessionId,
            long generation,
            String username,
            UUID expectedProfileUuid,
            UUID observedUuid,
            Instant expiresAt,
            BindingState state) {}

    private final Map<String, Entry> byUsername = new HashMap<>();
    private final Map<UUID, Entry> byExpectedUuid = new HashMap<>();
    private final Map<UUID, Entry> byObservedUuid = new HashMap<>();

    public synchronized void register(Lease lease, Instant now) {
        Objects.requireNonNull(lease, "lease");
        Objects.requireNonNull(now, "now");
        if (!lease.expiresAt().isAfter(now)) throw new IllegalArgumentException("lease is expired");
        if (byUsername.containsKey(lease.username()))
            throw new IllegalStateException("username already leased: " + lease.username());
        if (byExpectedUuid.containsKey(lease.expectedProfileUuid()))
            throw new IllegalStateException(
                    "profile UUID already leased: " + lease.expectedProfileUuid());
        Entry entry = new Entry(lease);
        byUsername.put(lease.username(), entry);
        byExpectedUuid.put(lease.expectedProfileUuid(), entry);
    }

    /**
     * Binds the UUID observed by Paper/AuthBridge. A client-provided UUID must never call this
     * method in the production integration.
     */
    public synchronized BindingSnapshot bindObserved(
            UUID sessionId, long generation, String username, UUID observedUuid, Instant now) {
        Objects.requireNonNull(sessionId, "sessionId");
        if (generation < 1) throw new IllegalArgumentException("generation must be positive");
        String normalized = normalizeUsername(username);
        Objects.requireNonNull(observedUuid, "observedUuid");
        Objects.requireNonNull(now, "now");
        Entry entry = byUsername.get(normalized);
        if (entry == null) throw new IllegalStateException("no test lease for " + normalized);
        if (!entry.lease.sessionId().equals(sessionId) || entry.lease.generation() != generation)
            throw new IllegalStateException("test lease generation mismatch for " + normalized);
        if (!entry.lease.expiresAt().isAfter(now)) {
            entry.state = BindingState.REVOKED;
            throw new IllegalStateException("test lease expired for " + normalized);
        }
        // AuthMe can complete again after a reconnect. Paper will reject a duplicate UUID while
        // the old connection is alive, so accepting the same UUID here is safe and keeps the
        // session lease reusable without ever accepting a different identity.
        if (entry.state != BindingState.LEASED
                && !((entry.state == BindingState.OBSERVED
                                || entry.state == BindingState.AUTHENTICATED)
                        && observedUuid.equals(entry.observedUuid)))
            throw new IllegalStateException("test lease is not available for " + normalized);
        if (!entry.lease.expectedProfileUuid().equals(observedUuid))
            throw new IllegalStateException("server UUID mismatch for " + normalized);
        Entry existing = byObservedUuid.get(observedUuid);
        if (existing != null && existing != entry)
            throw new IllegalStateException("observed UUID already bound: " + observedUuid);
        entry.observedUuid = observedUuid;
        entry.state = BindingState.OBSERVED;
        byObservedUuid.put(observedUuid, entry);
        return entry.snapshot();
    }

    /** Confirms the same server UUID after AuthMe's successful LoginEvent. */
    public synchronized BindingSnapshot markAuthenticated(
            UUID sessionId, long generation, String username, UUID observedUuid, Instant now) {
        Objects.requireNonNull(sessionId, "sessionId");
        if (generation < 1) throw new IllegalArgumentException("generation must be positive");
        String normalized = normalizeUsername(username);
        Objects.requireNonNull(observedUuid, "observedUuid");
        Objects.requireNonNull(now, "now");
        Entry entry = byUsername.get(normalized);
        if (entry == null) throw new IllegalStateException("no test lease for " + normalized);
        if (!entry.lease.sessionId().equals(sessionId) || entry.lease.generation() != generation)
            throw new IllegalStateException("test lease generation mismatch for " + normalized);
        if (!entry.lease.expiresAt().isAfter(now))
            throw new IllegalStateException("test lease expired for " + normalized);
        if (entry.state != BindingState.OBSERVED
                || !observedUuid.equals(entry.observedUuid)
                || !observedUuid.equals(entry.lease.expectedProfileUuid()))
            throw new IllegalStateException("AuthMe UUID binding mismatch for " + normalized);
        entry.state = BindingState.AUTHENTICATED;
        return entry.snapshot();
    }

    /** Extends an existing lease without changing its trusted UUID or observed connection. */
    public synchronized BindingSnapshot renew(
            UUID sessionId,
            long generation,
            String username,
            UUID expectedProfileUuid,
            Instant expiresAt,
            Instant now) {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(expectedProfileUuid, "expectedProfileUuid");
        Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(now, "now");
        if (generation < 1) throw new IllegalArgumentException("generation must be positive");
        if (!expiresAt.isAfter(now)) throw new IllegalArgumentException("lease is expired");
        Entry entry = byUsername.get(normalizeUsername(username));
        if (entry == null) throw new IllegalStateException("no test lease for " + username);
        if (!entry.lease.sessionId().equals(sessionId)
                || entry.lease.generation() != generation
                || !entry.lease.expectedProfileUuid().equals(expectedProfileUuid))
            throw new IllegalStateException("test lease renewal identity mismatch for " + username);
        if (entry.state == BindingState.REVOKED)
            throw new IllegalStateException("test lease is revoked for " + username);
        entry.lease =
                new Lease(
                        sessionId,
                        generation,
                        entry.lease.username(),
                        expectedProfileUuid,
                        expiresAt);
        return entry.snapshot();
    }

    public synchronized BindingSnapshot snapshot(String username) {
        Entry entry = lookup(username);
        return entry == null ? null : entry.snapshot();
    }

    public synchronized BindingSnapshot snapshot(UUID observedUuid) {
        Entry entry = byObservedUuid.get(observedUuid);
        return entry == null ? null : entry.snapshot();
    }

    /**
     * Returns true only for a UUID reserved by this session and observed during this connection.
     */
    public synchronized boolean isReserved(String username, UUID observedUuid) {
        Entry entry = lookup(username);
        return entry != null
                && entry.lease.expiresAt().isAfter(Instant.now())
                && (entry.state == BindingState.OBSERVED
                        || entry.state == BindingState.AUTHENTICATED)
                && observedUuid.equals(entry.observedUuid);
    }

    public synchronized boolean isAuthenticated(String username, UUID observedUuid) {
        Entry entry = lookup(username);
        return entry != null
                && entry.lease.expiresAt().isAfter(Instant.now())
                && entry.state == BindingState.AUTHENTICATED
                && observedUuid.equals(entry.observedUuid);
    }

    public synchronized void revokeSession(UUID sessionId, long generation) {
        Objects.requireNonNull(sessionId, "sessionId");
        for (Entry entry : Set.copyOf(byUsername.values())) {
            if (entry.lease.sessionId().equals(sessionId)
                    && entry.lease.generation() == generation) {
                entry.state = BindingState.REVOKED;
                byUsername.remove(entry.lease.username(), entry);
                byExpectedUuid.remove(entry.lease.expectedProfileUuid(), entry);
                if (entry.observedUuid != null) byObservedUuid.remove(entry.observedUuid, entry);
            }
        }
    }

    /** Revokes every current generation for a session during stop/reload cleanup. */
    public synchronized void revokeSession(UUID sessionId) {
        Objects.requireNonNull(sessionId, "sessionId");
        for (Entry entry : Set.copyOf(byUsername.values())) {
            if (!entry.lease.sessionId().equals(sessionId)) continue;
            entry.state = BindingState.REVOKED;
            byUsername.remove(entry.lease.username(), entry);
            byExpectedUuid.remove(entry.lease.expectedProfileUuid(), entry);
            if (entry.observedUuid != null) byObservedUuid.remove(entry.observedUuid, entry);
        }
    }

    /** Revokes one Bot lease when its seat is replaced by a developer during preparation. */
    public synchronized void revokeUsername(String username) {
        Entry entry = byUsername.remove(normalizeUsername(username));
        if (entry == null) return;
        entry.state = BindingState.REVOKED;
        byExpectedUuid.remove(entry.lease.expectedProfileUuid(), entry);
        if (entry.observedUuid != null) byObservedUuid.remove(entry.observedUuid, entry);
    }

    public synchronized int size() {
        return byUsername.size();
    }

    /** Preparation disconnects require a fresh observation and AuthMe confirmation. */
    public synchronized boolean markDisconnected(String username, UUID observedUuid) {
        Entry entry = lookup(username);
        if (entry == null
                || !Objects.equals(observedUuid, entry.observedUuid)
                || (entry.state != BindingState.OBSERVED
                        && entry.state != BindingState.AUTHENTICATED)) return false;
        byObservedUuid.remove(observedUuid, entry);
        entry.observedUuid = null;
        entry.state = BindingState.LEASED;
        return true;
    }

    public synchronized Set<UUID> authenticatedUuids() {
        Set<UUID> result = new HashSet<>();
        for (Entry entry : byUsername.values()) {
            if (entry.state == BindingState.AUTHENTICATED) result.add(entry.observedUuid);
        }
        return Set.copyOf(result);
    }

    /** Ordinary logins must not be validated against the stricter test Bot naming policy. */
    private Entry lookup(String username) {
        return username == null
                ? null
                : byUsername.get(username.trim().toLowerCase(java.util.Locale.ROOT));
    }

    private static String normalizeUsername(String username) {
        Objects.requireNonNull(username, "username");
        String normalized = username.trim().toLowerCase(java.util.Locale.ROOT);
        if (!normalized.matches("[a-z0-9_]{3,16}"))
            throw new IllegalArgumentException("invalid Minecraft username: " + username);
        return normalized;
    }

    private static final class Entry {
        private Lease lease;
        private UUID observedUuid;
        private BindingState state = BindingState.LEASED;

        private Entry(Lease lease) {
            this.lease = lease;
        }

        private BindingSnapshot snapshot() {
            return new BindingSnapshot(
                    lease.sessionId(),
                    lease.generation(),
                    lease.username(),
                    lease.expectedProfileUuid(),
                    observedUuid,
                    lease.expiresAt(),
                    state);
        }
    }
}
