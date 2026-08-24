package ink.ziip.championshipscore.authbridge.bridge;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;

public final class LocalAccessState {
    private final File file;
    private final Map<String, Identity> whitelist = new ConcurrentHashMap<>();
    private final Map<String, Ban> bans = new ConcurrentHashMap<>();
    private final Map<String, Integer> authVersions = new ConcurrentHashMap<>();
    private volatile String cursor = "0";
    private volatile boolean synchronizedOnce;
    private volatile String maintenanceJobId;
    private volatile String pendingAckCursor;
    private volatile List<ServerUuidReport> pendingAckServerUuids = List.of();
    private volatile ControlCompletion pendingControlCompletion;

    public LocalAccessState(File file) {
        this.file = file;
        load();
    }

    public boolean isWhitelisted(String username) {
        return whitelist.containsKey(normalize(username));
    }

    public UUID expectedUuid(String username) {
        Identity identity = whitelist.get(normalize(username));
        if (identity == null || identity.minecraftUuid() == null || identity.minecraftUuid().isBlank()) return null;
        try {
            return UUID.fromString(identity.minecraftUuid());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public Ban activeBan(String username) {
        Ban ban = bans.get(normalize(username));
        if (ban != null && ban.expiresAt() != null && !ban.expiresAt().isAfter(Instant.now())) {
            bans.remove(normalize(username));
            return null;
        }
        return ban;
    }

    public void whitelist(String username, String accountId, String minecraftUuid) {
        whitelist.put(normalize(username), new Identity(accountId, minecraftUuid));
    }

    /** Legacy/test convenience where the account id is also the effective game UUID. */
    public void whitelist(String username, String accountId) {
        whitelist(username, accountId, accountId);
    }

    public void revoke(String username) {
        whitelist.remove(normalize(username));
    }

    public void rename(String oldUsername, String newUsername, String accountId, String minecraftUuid) {
        String oldKey = normalize(oldUsername);
        String newKey = normalize(newUsername);
        Identity existing = whitelist.remove(oldKey);
        if (existing != null) whitelist.put(newKey, new Identity(
                accountId == null || accountId.isBlank() ? existing.accountId() : accountId,
                minecraftUuid == null || minecraftUuid.isBlank() ? existing.minecraftUuid() : minecraftUuid));
        Ban ban = bans.remove(oldKey);
        if (ban != null) bans.put(newKey, ban);
        Integer version = authVersions.remove(oldKey);
        if (version != null) authVersions.merge(newKey, version, Math::max);
    }

    public void ban(String username, String reason, String expiresAt) {
        bans.put(normalize(username), new Ban(reason == null ? "" : reason, parseInstant(expiresAt)));
    }

    public void unban(String username) {
        bans.remove(normalize(username));
    }

    public int authVersion(String username) {
        return authVersions.getOrDefault(normalize(username), 0);
    }

    public void setAuthVersion(String username, int version) {
        authVersions.merge(normalize(username), version, Math::max);
    }

    public String cursor() {
        return cursor;
    }

    public boolean synchronizedOnce() {
        return synchronizedOnce;
    }

    public boolean maintenanceInProgress() {
        return maintenanceJobId != null && !maintenanceJobId.isBlank();
    }

    public synchronized void beginMaintenance(String jobId) throws IOException {
        if (maintenanceJobId != null && !maintenanceJobId.isBlank() && !maintenanceJobId.equals(jobId)) {
            throw new IllegalStateException("A different maintenance job is still incomplete");
        }
        maintenanceJobId = jobId;
        save();
    }

    public synchronized void clearMaintenance(String jobId) throws IOException {
        if (jobId == null || maintenanceJobId == null || maintenanceJobId.equals(jobId)) {
            maintenanceJobId = null;
            save();
        }
    }

    public synchronized PendingAcknowledgement pendingAcknowledgement() {
        if (pendingAckCursor == null || pendingAckCursor.isBlank()) return null;
        return new PendingAcknowledgement(pendingAckCursor, pendingAckServerUuids);
    }

    public synchronized void advance(String cursor, List<ServerUuidReport> serverUuids) throws IOException {
        this.cursor = cursor;
        this.synchronizedOnce = true;
        this.pendingAckCursor = cursor;
        this.pendingAckServerUuids = List.copyOf(serverUuids);
        save();
    }

    public synchronized void advance(String cursor) throws IOException {
        advance(cursor, List.of());
    }

    public synchronized void confirmAcknowledged(String cursor) throws IOException {
        if (pendingAckCursor == null || !pendingAckCursor.equals(cursor)) return;
        pendingAckCursor = null;
        pendingAckServerUuids = List.of();
        save();
    }

    public synchronized ControlCompletion pendingControlCompletion() {
        return pendingControlCompletion;
    }

    public synchronized void stageControlCompletion(String jobId, Map<String, Object> result) throws IOException {
        pendingControlCompletion = new ControlCompletion(jobId, Map.copyOf(result));
        save();
    }

    public synchronized void finishControlCompletion(String jobId) throws IOException {
        if (pendingControlCompletion != null && pendingControlCompletion.jobId().equals(jobId)) {
            pendingControlCompletion = null;
        }
        if (maintenanceJobId != null && maintenanceJobId.equals(jobId)) maintenanceJobId = null;
        save();
    }

    public synchronized void migrateWhitelistUuids(Map<String, String> uuidsByAccountId) throws IOException {
        whitelist.replaceAll((name, identity) -> {
            String uuid = uuidsByAccountId.get(identity.accountId());
            return uuid == null ? identity : new Identity(identity.accountId(), uuid);
        });
        save();
    }

    private void load() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        cursor = yaml.getString("cursor", "0");
        synchronizedOnce = yaml.getBoolean("synchronized-once", false);
        maintenanceJobId = yaml.getString("maintenance-job-id", yaml.getString("migration-job-id"));
        pendingAckCursor = yaml.getString("pending-ack.cursor");
        var whitelistSection = yaml.getConfigurationSection("whitelist");
        if (whitelistSection != null) for (String key : whitelistSection.getKeys(false)) {
            var identitySection = whitelistSection.getConfigurationSection(key);
            if (identitySection == null) {
                String legacy = whitelistSection.getString(key, "");
                whitelist.put(key, new Identity(legacy, legacy));
            } else {
                String accountId = identitySection.getString("account-id", "");
                whitelist.put(key, new Identity(accountId,
                        identitySection.getString("minecraft-uuid", accountId)));
            }
        }
        pendingAckServerUuids = readServerUuidReports(yaml.getMapList("pending-ack.server-uuids"));
        String pendingControlId = yaml.getString("pending-control-completion.job-id");
        if (pendingControlId != null && !pendingControlId.isBlank()) {
            Map<String, Object> result = new LinkedHashMap<>();
            var section = yaml.getConfigurationSection("pending-control-completion.result");
            if (section != null) for (String key : section.getKeys(false)) result.put(key, section.get(key));
            pendingControlCompletion = new ControlCompletion(pendingControlId, Map.copyOf(result));
        }
        var banSection = yaml.getConfigurationSection("bans");
        if (banSection != null) for (String key : banSection.getKeys(false)) bans.put(key, new Ban(banSection.getString(key + ".reason", ""), parseInstant(banSection.getString(key + ".expires-at"))));
        var versionSection = yaml.getConfigurationSection("auth-versions");
        if (versionSection != null) for (String key : versionSection.getKeys(false)) authVersions.put(key, versionSection.getInt(key));
    }

    private void save() throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("cursor", cursor);
        yaml.set("synchronized-once", synchronizedOnce);
        yaml.set("maintenance-job-id", maintenanceJobId);
        yaml.set("pending-ack.cursor", pendingAckCursor);
        yaml.set("pending-ack.server-uuids", pendingAckServerUuids.stream()
                .map(report -> Map.of("account-id", report.accountId(), "server-uuid", report.serverUuid()))
                .toList());
        yaml.set("pending-control-completion.job-id",
                pendingControlCompletion == null ? null : pendingControlCompletion.jobId());
        yaml.set("pending-control-completion.result",
                pendingControlCompletion == null ? null : pendingControlCompletion.result());
        whitelist.forEach((name, identity) -> {
            yaml.set("whitelist." + name + ".account-id", identity.accountId());
            yaml.set("whitelist." + name + ".minecraft-uuid", identity.minecraftUuid());
        });
        bans.forEach((name, ban) -> {
            yaml.set("bans." + name + ".reason", ban.reason());
            yaml.set("bans." + name + ".expires-at", ban.expiresAt() == null ? null : ban.expiresAt().toString());
        });
        authVersions.forEach((name, version) -> yaml.set("auth-versions." + name, version));
        yaml.save(file);
    }

    private static String normalize(String username) {
        return username.toLowerCase(Locale.ROOT);
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private static List<ServerUuidReport> readServerUuidReports(List<Map<?, ?>> raw) {
        List<ServerUuidReport> reports = new ArrayList<>();
        for (Map<?, ?> entry : raw) {
            Object accountId = entry.get("account-id");
            Object serverUuid = entry.get("server-uuid");
            if (accountId != null && serverUuid != null) {
                reports.add(new ServerUuidReport(accountId.toString(), serverUuid.toString()));
            }
        }
        return List.copyOf(reports);
    }

    public record Ban(String reason, Instant expiresAt) {
    }

    public record Identity(String accountId, String minecraftUuid) {
    }

    public record ServerUuidReport(String accountId, String serverUuid) {
    }

    public record PendingAcknowledgement(String cursor, List<ServerUuidReport> serverUuids) {
    }

    public record ControlCompletion(String jobId, Map<String, Object> result) {
    }
}
