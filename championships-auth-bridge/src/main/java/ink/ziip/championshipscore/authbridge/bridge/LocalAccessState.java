package ink.ziip.championshipscore.authbridge.bridge;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;
import java.util.LinkedHashMap;

public final class LocalAccessState {
    private final File file;
    private final Map<String, Identity> identities = new ConcurrentHashMap<>();
    private final Map<String, Integer> authVersions = new ConcurrentHashMap<>();
    private volatile String cursor = "0";
    private volatile boolean synchronizedOnce;
    private volatile boolean bindingSnapshotApplied;
    private volatile String maintenanceJobId;
    private volatile String pendingAckCursor;
    private volatile ControlCompletion pendingControlCompletion;

    public LocalAccessState(File file) {
        this.file = file;
        load();
    }

    public UUID expectedUuid(String username) {
        Identity identity = identities.get(normalize(username));
        if (identity == null || identity.minecraftUuid() == null || identity.minecraftUuid().isBlank()) return null;
        try {
            return UUID.fromString(identity.minecraftUuid());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public void recordIdentity(String username, String accountId, String minecraftUuid) {
        identities.put(normalize(username), new Identity(accountId, minecraftUuid));
    }

    /** Test convenience where the account id is also the effective game UUID. */
    public void recordIdentity(String username, String accountId) {
        recordIdentity(username, accountId, accountId);
    }

    public void revoke(String username) {
        identities.remove(normalize(username));
    }

    public void rename(String oldUsername, String newUsername, String accountId, String minecraftUuid) {
        String oldKey = normalize(oldUsername);
        String newKey = normalize(newUsername);
        Identity existing = identities.remove(oldKey);
        if (existing != null) identities.put(newKey, new Identity(
                accountId == null || accountId.isBlank() ? existing.accountId() : accountId,
                minecraftUuid == null || minecraftUuid.isBlank() ? existing.minecraftUuid() : minecraftUuid));
        Integer version = authVersions.remove(oldKey);
        if (version != null) authVersions.merge(newKey, version, Math::max);
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

    public boolean bindingSnapshotApplied() {
        return bindingSnapshotApplied;
    }

    public synchronized void markBindingSnapshotApplied() throws IOException {
        bindingSnapshotApplied = true;
        save();
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
        return new PendingAcknowledgement(pendingAckCursor);
    }

    public synchronized void advance(String cursor) throws IOException {
        this.cursor = cursor;
        this.synchronizedOnce = true;
        this.pendingAckCursor = cursor;
        save();
    }

    public synchronized void confirmAcknowledged(String cursor) throws IOException {
        if (pendingAckCursor == null || !pendingAckCursor.equals(cursor)) return;
        pendingAckCursor = null;
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

    public synchronized void migrateIdentityUuids(Map<String, String> uuidsByAccountId) throws IOException {
        identities.replaceAll((name, identity) -> {
            String uuid = uuidsByAccountId.get(identity.accountId());
            return uuid == null ? identity : new Identity(identity.accountId(), uuid);
        });
        save();
    }

    private void load() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        cursor = yaml.getString("cursor", "0");
        synchronizedOnce = yaml.getBoolean("synchronized-once", false);
        bindingSnapshotApplied = yaml.getBoolean("binding-snapshot-applied", false);
        maintenanceJobId = yaml.getString("maintenance-job-id", yaml.getString("migration-job-id"));
        pendingAckCursor = yaml.getString("pending-ack.cursor");
        var identitySectionRoot = yaml.getConfigurationSection("identities");
        if (identitySectionRoot == null) identitySectionRoot = yaml.getConfigurationSection("whitelist");
        if (identitySectionRoot != null) for (String key : identitySectionRoot.getKeys(false)) {
            var identitySection = identitySectionRoot.getConfigurationSection(key);
            if (identitySection == null) {
                String legacy = identitySectionRoot.getString(key, "");
                identities.put(key, new Identity(legacy, legacy));
            } else {
                String accountId = identitySection.getString("account-id", "");
                identities.put(key, new Identity(accountId,
                        identitySection.getString("minecraft-uuid", accountId)));
            }
        }
        String pendingControlId = yaml.getString("pending-control-completion.job-id");
        if (pendingControlId != null && !pendingControlId.isBlank()) {
            Map<String, Object> result = new LinkedHashMap<>();
            var section = yaml.getConfigurationSection("pending-control-completion.result");
            if (section != null) for (String key : section.getKeys(false)) result.put(key, section.get(key));
            pendingControlCompletion = new ControlCompletion(pendingControlId, Map.copyOf(result));
        }
        var versionSection = yaml.getConfigurationSection("auth-versions");
        if (versionSection != null) for (String key : versionSection.getKeys(false)) authVersions.put(key, versionSection.getInt(key));
    }

    private void save() throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("cursor", cursor);
        yaml.set("synchronized-once", synchronizedOnce);
        yaml.set("binding-snapshot-applied", bindingSnapshotApplied);
        yaml.set("maintenance-job-id", maintenanceJobId);
        yaml.set("pending-ack.cursor", pendingAckCursor);
        yaml.set("pending-control-completion.job-id",
                pendingControlCompletion == null ? null : pendingControlCompletion.jobId());
        yaml.set("pending-control-completion.result",
                pendingControlCompletion == null ? null : pendingControlCompletion.result());
        identities.forEach((name, identity) -> {
            yaml.set("identities." + name + ".account-id", identity.accountId());
            yaml.set("identities." + name + ".minecraft-uuid", identity.minecraftUuid());
        });
        authVersions.forEach((name, version) -> yaml.set("auth-versions." + name, version));
        yaml.save(file);
    }

    private static String normalize(String username) {
        return username.toLowerCase(Locale.ROOT);
    }

    public record Identity(String accountId, String minecraftUuid) {
    }

    public record PendingAcknowledgement(String cursor) {
    }

    public record ControlCompletion(String jobId, Map<String, Object> result) {
    }
}
