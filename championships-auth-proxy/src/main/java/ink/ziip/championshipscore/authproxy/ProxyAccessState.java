package ink.ziip.championshipscore.authproxy;

import ink.ziip.championshipscore.auth.AuthIdentity;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/** Durable admission snapshot used when cc-web is temporarily unreachable. */
final class ProxyAccessState {
    private static final String CURSOR_KEY = "ban-event-cursor";
    private static final String MAINTENANCE_KEY = "maintenance";
    private static final String SNAPSHOT_INITIALIZED_KEY = "access-snapshot-initialized";
    private static final String PROFILE_PREFIX = "profile.";
    private static final String BAN_PREFIX = "ban.";
    private static final Set<String> CACHEABLE_PROFILE_STATUSES = Set.of("ALLOWED", "UNBOUND", "REVOKED");

    private final File file;
    private final Map<String, CachedProfile> profiles = new HashMap<>();
    private final Map<String, CachedBan> bans = new HashMap<>();
    private String cursor;
    private boolean maintenance;
    private boolean snapshotInitialized;

    ProxyAccessState(File file) {
        this.file = file;
        load();
    }

    synchronized boolean initialized() {
        return snapshotInitialized && cursor != null;
    }

    synchronized String cursor() {
        if (cursor == null) throw new IllegalStateException("Proxy access state has not been initialized");
        return cursor;
    }

    synchronized ProxyIdentityClient.LoginProfile cachedProfile(
            String username, Duration maxStale, Instant now) {
        String key = AuthIdentity.normalizeUsername(username);
        if (maintenance) return profile("MAINTENANCE", null, null, null);
        CachedBan ban = bans.get(key);
        if (ban != null && isActive(ban.expiresAt(), now)) {
            return profile("BANNED", null, ban.reason(), ban.expiresAt());
        }
        CachedProfile cached = profiles.get(key);
        if (cached == null || isStale(cached.updatedAt(), maxStale, now)) return null;
        return profile(cached.status(), cached.uuid(), null, null);
    }

    synchronized void recordLiveProfile(String username, ProxyIdentityClient.LoginProfile live, Instant now)
            throws IOException {
        String key = AuthIdentity.normalizeUsername(username);
        String status = requireStatus(live.status);
        if ("MAINTENANCE".equals(status)) {
            maintenance = true;
        } else {
            maintenance = false;
            applyProfile(profiles, bans, key, status, live.uuid, live.reason, live.expiresAt, now.toEpochMilli());
        }
        save();
    }

    synchronized void replaceSnapshot(ProxyIdentityClient.ProxyBanSnapshot snapshot, Instant now) throws IOException {
        if (snapshot == null || snapshot.maintenance == null || snapshot.bans == null) {
            throw new IllegalStateException("Bridge proxy access snapshot is incomplete");
        }
        String nextCursor = requireCursor(snapshot.nextCursor);
        Map<String, CachedProfile> nextProfiles = snapshot.profiles == null
                ? new HashMap<>(profiles)
                : parseProfiles(snapshot.profiles, now.toEpochMilli());
        Map<String, CachedBan> nextBans = parseBans(snapshot.bans);
        profiles.clear();
        profiles.putAll(nextProfiles);
        bans.clear();
        bans.putAll(nextBans);
        maintenance = snapshot.maintenance;
        cursor = nextCursor;
        snapshotInitialized = true;
        save();
    }

    synchronized void applyChanges(ProxyIdentityClient.ProxyChangeBatch batch, Instant now) throws IOException {
        if (batch == null || batch.maintenance == null || batch.changes == null) {
            throw new IllegalStateException("Bridge proxy change batch is incomplete");
        }
        String nextCursor = requireCursor(batch.nextCursor);
        Map<String, CachedProfile> nextProfiles = new HashMap<>(profiles);
        Map<String, CachedBan> nextBans = new HashMap<>(bans);
        for (ProxyIdentityClient.ProxyChange change : batch.changes) {
            if (change == null) continue;
            String key = AuthIdentity.normalizeUsername(change.authmeUsername);
            if (change.previousUsername != null && !change.previousUsername.isBlank()) {
                String previous = AuthIdentity.normalizeUsername(change.previousUsername);
                nextProfiles.remove(previous);
                nextBans.remove(previous);
            }
            if (change.status == null) {
                applyLegacyBanChange(nextBans, key, change);
            } else {
                applyProfile(nextProfiles, nextBans, key, requireStatus(change.status), change.uuid,
                        change.reason, change.expiresAt, now.toEpochMilli());
            }
        }
        profiles.clear();
        profiles.putAll(nextProfiles);
        bans.clear();
        bans.putAll(nextBans);
        maintenance = batch.maintenance;
        cursor = nextCursor;
        save();
    }

    private static Map<String, CachedProfile> parseProfiles(
            List<ProxyIdentityClient.LoginProfile> source, long updatedAt) {
        Map<String, CachedProfile> parsed = new HashMap<>();
        for (ProxyIdentityClient.LoginProfile profile : source) {
            if (profile == null) throw new IllegalArgumentException("Null profile in bridge snapshot");
            String key = AuthIdentity.normalizeUsername(profile.username);
            String status = requireStatus(profile.status);
            if (!CACHEABLE_PROFILE_STATUSES.contains(status)) {
                throw new IllegalArgumentException("Unsupported profile status in bridge snapshot");
            }
            String uuid = validateUuid(status, profile.uuid);
            if (parsed.put(key, new CachedProfile(status, uuid, updatedAt)) != null) {
                throw new IllegalArgumentException("Duplicate profile in bridge snapshot");
            }
        }
        return parsed;
    }

    private static Map<String, CachedBan> parseBans(List<ProxyIdentityClient.ProxyBan> source) {
        Map<String, CachedBan> parsed = new HashMap<>();
        for (ProxyIdentityClient.ProxyBan ban : source) {
            if (ban == null) throw new IllegalArgumentException("Null ban in bridge snapshot");
            String key = AuthIdentity.normalizeUsername(ban.username);
            validateExpiry(ban.expiresAt);
            if (parsed.put(key, new CachedBan(valueOrEmpty(ban.reason), blankToNull(ban.expiresAt))) != null) {
                throw new IllegalArgumentException("Duplicate ban in bridge snapshot");
            }
        }
        return parsed;
    }

    private static void applyProfile(Map<String, CachedProfile> profiles, Map<String, CachedBan> bans,
                                     String key, String status, String uuid, String reason,
                                     String expiresAt, long updatedAt) {
        switch (status) {
            case "ALLOWED", "UNBOUND", "REVOKED" -> {
                profiles.put(key, new CachedProfile(status, validateUuid(status, uuid), updatedAt));
                bans.remove(key);
            }
            case "BANNED" -> {
                validateExpiry(expiresAt);
                bans.put(key, new CachedBan(valueOrEmpty(reason), blankToNull(expiresAt)));
            }
            case "MAINTENANCE" -> throw new IllegalArgumentException("Maintenance is a global proxy state");
            default -> throw new IllegalArgumentException("Unsupported cached login status");
        }
    }

    private static void applyLegacyBanChange(Map<String, CachedBan> bans, String key,
                                             ProxyIdentityClient.ProxyChange change) {
        if ("BANNED".equals(change.operation)) {
            validateExpiry(change.expiresAt);
            bans.put(key, new CachedBan(valueOrEmpty(change.reason), blankToNull(change.expiresAt)));
        } else if ("UNBANNED".equals(change.operation)) {
            bans.remove(key);
        }
    }

    private void load() {
        if (!file.isFile()) return;
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(file.toPath())) {
            properties.load(input);
            String storedCursor = properties.getProperty(CURSOR_KEY);
            if (storedCursor != null && storedCursor.matches("^\\d{1,19}$")) cursor = storedCursor;
            maintenance = Boolean.parseBoolean(properties.getProperty(MAINTENANCE_KEY, "false"));
            snapshotInitialized = Boolean.parseBoolean(properties.getProperty(SNAPSHOT_INITIALIZED_KEY, "false"));
            for (String key : properties.stringPropertyNames()) {
                if (key.startsWith(PROFILE_PREFIX) && key.endsWith(".status")) loadProfile(properties, key);
                if (key.startsWith(BAN_PREFIX) && key.endsWith(".reason")) loadBan(properties, key);
            }
        } catch (Exception ignored) {
            cursor = null;
            maintenance = false;
            snapshotInitialized = false;
            profiles.clear();
            bans.clear();
        }
    }

    private void loadProfile(Properties properties, String statusKey) {
        String username = statusKey.substring(PROFILE_PREFIX.length(), statusKey.length() - ".status".length());
        String normalized = AuthIdentity.normalizeUsername(username);
        String status = properties.getProperty(statusKey);
        if (!CACHEABLE_PROFILE_STATUSES.contains(status)) return;
        String uuid = validateUuid(status, properties.getProperty(PROFILE_PREFIX + normalized + ".uuid"));
        long updatedAt = Long.parseLong(properties.getProperty(PROFILE_PREFIX + normalized + ".updated-at", "0"));
        profiles.put(normalized, new CachedProfile(status, uuid, updatedAt));
    }

    private void loadBan(Properties properties, String reasonKey) {
        String username = reasonKey.substring(BAN_PREFIX.length(), reasonKey.length() - ".reason".length());
        String normalized = AuthIdentity.normalizeUsername(username);
        String expiresAt = blankToNull(properties.getProperty(BAN_PREFIX + normalized + ".expires-at"));
        validateExpiry(expiresAt);
        bans.put(normalized, new CachedBan(properties.getProperty(reasonKey, ""), expiresAt));
    }

    private void save() throws IOException {
        Properties properties = new Properties();
        if (cursor != null) properties.setProperty(CURSOR_KEY, cursor);
        properties.setProperty(MAINTENANCE_KEY, Boolean.toString(maintenance));
        properties.setProperty(SNAPSHOT_INITIALIZED_KEY, Boolean.toString(snapshotInitialized));
        profiles.forEach((username, profile) -> {
            String prefix = PROFILE_PREFIX + username + ".";
            properties.setProperty(prefix + "status", profile.status());
            if (profile.uuid() != null) properties.setProperty(prefix + "uuid", profile.uuid());
            properties.setProperty(prefix + "updated-at", Long.toString(profile.updatedAt()));
        });
        bans.forEach((username, ban) -> {
            String prefix = BAN_PREFIX + username + ".";
            properties.setProperty(prefix + "reason", ban.reason());
            if (ban.expiresAt() != null) properties.setProperty(prefix + "expires-at", ban.expiresAt());
        });

        File parent = file.getParentFile();
        if (parent != null) Files.createDirectories(parent.toPath());
        var target = file.toPath();
        var temporary = target.resolveSibling(target.getFileName() + ".tmp");
        try (OutputStream output = Files.newOutputStream(temporary)) {
            properties.store(output, "ChampionshipsAuthProxy durable access cache");
        }
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static ProxyIdentityClient.LoginProfile profile(
            String status, String uuid, String reason, String expiresAt) {
        ProxyIdentityClient.LoginProfile profile = new ProxyIdentityClient.LoginProfile();
        profile.status = status;
        profile.uuid = uuid;
        profile.reason = reason;
        profile.expiresAt = expiresAt;
        return profile;
    }

    private static String requireStatus(String status) {
        if (status == null || !Set.of("ALLOWED", "UNBOUND", "BANNED", "REVOKED", "MAINTENANCE").contains(status)) {
            throw new IllegalArgumentException("Invalid bridge login status");
        }
        return status;
    }

    private static String validateUuid(String status, String uuid) {
        if (!"ALLOWED".equals(status)) return null;
        if (uuid == null) throw new IllegalArgumentException("Allowed cached profile omitted UUID");
        return AuthIdentity.parseUuid(uuid, "cached profile UUID").toString();
    }

    private static boolean isStale(long updatedAt, Duration maxStale, Instant now) {
        return maxStale != null && !maxStale.isZero()
                && updatedAt < now.minus(maxStale).toEpochMilli();
    }

    private static boolean isActive(String expiresAt, Instant now) {
        if (expiresAt == null || expiresAt.isBlank()) return true;
        try {
            return Instant.parse(expiresAt).isAfter(now);
        } catch (DateTimeParseException exception) {
            return false;
        }
    }

    private static void validateExpiry(String expiresAt) {
        if (expiresAt == null || expiresAt.isBlank()) return;
        Instant.parse(expiresAt);
    }

    private static String requireCursor(String cursor) {
        if (cursor == null || !cursor.matches("^\\d{1,19}$")) {
            throw new IllegalArgumentException("Invalid bridge cursor");
        }
        return cursor;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private record CachedProfile(String status, String uuid, long updatedAt) {
    }

    private record CachedBan(String reason, String expiresAt) {
    }
}
